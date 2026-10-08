package io.triform.rpc.support;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.triform.core.BizException;
import io.triform.core.ResultCode;
import io.triform.rpc.context.HttpRpcOutboundInterceptor;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.Retryer;
import feign.jackson.JacksonEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * HTTP(备选)传输的远程代理工厂 Bean —— 由 {@code triform.rpc.transport=http} 时注册,
 * 与 {@link DubboRefFactoryBean} 并列且互斥(Dubbo 路径保持不变,零回归风险)。
 *
 * <p>实现要点:
 * <ol>
 *   <li><b>Feign 只当传输</b>:Feign 客户端接口是本模块自己的 {@link HttpRpcClient},业务契约零传输注解;</li>
 *   <li><b>动态代理 + 泛型还原</b>:调用被转成 {@code (facadeFqn, methodName, args)} 发给通用端点,
 *       响应体原文是 JSON,再由本类按业务方法的**泛型返回类型**反序列化;</li>
 *   <li><b>超时/重试</b>:{@code Retryer.NEVER_RETRY} 与 Dubbo 侧 {@code retries=0} 对齐(避免写操作被重复执行);
 *       连接/读超时由 {@code triform.rpc.connect-timeout-ms}(默认 3000)、{@code triform.rpc.read-timeout-ms}(默认 5000)配置;</li>
 *   <li><b>实例解析</b>:交给 {@link HttpRpcOutboundInterceptor}(按服务名经 Nacos 轮询,不经网关)。</li>
 * </ol>
 */
public class HttpRefFactoryBean implements FactoryBean<Object>, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(HttpRefFactoryBean.class);

    private final String facadeFqn;

    private BeanFactory beanFactory;
    private volatile Object proxy;

    public HttpRefFactoryBean(String facadeFqn) {
        this.facadeFqn = facadeFqn;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object getObject() {
        if (proxy == null) {
            synchronized (this) {
                if (proxy == null) {
                    proxy = createProxy();
                }
            }
        }
        return proxy;
    }

    @Override
    public Class<?> getObjectType() {
        return ClassUtils.resolveClassName(facadeFqn, getClass().getClassLoader());
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    private Object createProxy() {
        Class<?> facade = ClassUtils.resolveClassName(facadeFqn, getClass().getClassLoader());
        Environment environment = resolveEnvironment();
        ObjectMapper objectMapper = beanFactory.getBean(ObjectMapper.class);
        DiscoveryClient discoveryClient = discoveryClientOrNull();
        HttpServiceNameResolver resolver = new HttpServiceNameResolver(environment, discoveryClient);
        HttpRpcClient httpRpcClient = buildClient(facadeFqn, resolver, discoveryClient, environment, objectMapper);
        log.info("[rpc] 以 HTTP(Feign) 传输创建远程代理: {} (目标服务名三级解析: 配置>注册中心元数据>包名约定)",
                facadeFqn);

        InvocationHandler handler = (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(p, method, args);
            }
            String json;
            try {
                json = httpRpcClient.invoke(facadeFqn, method.getName(), args == null ? new Object[0] : args);
            } catch (FeignException e) {
                throw mapTransportFailure(facade, method, e);
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class || returnType == Void.class || json == null || json.isEmpty()) {
                return null;
            }
            try {
                JavaType type = objectMapper.getTypeFactory().constructType(method.getGenericReturnType());
                return objectMapper.readValue(json, type);
            } catch (Exception e) {
                throw new BizException(ResultCode.SYSTEM_ERROR,
                        "远程返回体反序列化失败(" + facade.getSimpleName() + "#" + method.getName() + "): " + e.getMessage());
            }
        };
        // T10 治理 SPI: 容器存在装饰器(triform-rpc-resilience4j 激活)时包装调用链; 默认零治理
        org.springframework.beans.factory.ObjectProvider<RpcResilienceDecorator> decorator =
                beanFactory.getBeanProvider(RpcResilienceDecorator.class);
        RpcResilienceDecorator d = decorator.getIfAvailable();
        if (d != null) {
            handler = d.wrap(facadeFqn, handler);
            log.info("[rpc] 治理装饰已启用({}): {}", d.getClass().getSimpleName(), facadeFqn);
        }
        return Proxy.newProxyInstance(facade.getClassLoader(), new Class<?>[]{facade}, handler);
    }

    private HttpRpcClient buildClient(String facadeFqn, HttpServiceNameResolver resolver,
                                      DiscoveryClient discoveryClient, Environment environment,
                                      ObjectMapper objectMapper) {
        int connect = environment != null
                ? environment.getProperty("triform.rpc.connect-timeout-ms", Integer.class, 3000) : 3000;
        int read = environment != null
                ? environment.getProperty("triform.rpc.read-timeout-ms", Integer.class, 5000) : 5000;
        String internalToken = environment != null
                ? environment.getProperty("triform.rpc.internal-token", "") : "";
        if (internalToken == null || internalToken.isEmpty()) {
            log.warn("[rpc-http] 未配置 triform.rpc.internal-token —— 若提供侧开启了 http-expose，调用会被 403 拒绝");
        }
        // 档1 止血(基线/42 §7.4): OkHttp 连接池替代默认 HttpURLConnection(每请求新建连接);
        // 池参数跟随超时可配(连接数按 read 超时内并发的保守估计), keepAlive 5min 复用长连接
        okhttp3.ConnectionPool pool = new okhttp3.ConnectionPool(
                Math.max(8, read / 500), 5, java.util.concurrent.TimeUnit.MINUTES);
        okhttp3.OkHttpClient okClient = new okhttp3.OkHttpClient.Builder()
                .connectionPool(pool)
                .connectTimeout(connect, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(read, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        return Feign.builder()
                .client(new feign.okhttp.OkHttpClient(okClient))
                // 请求编码与响应解码共用宿主 ObjectMapper(日期/命名策略/自定义模块口径一致)
                .encoder(new JacksonEncoder(objectMapper))
                // 不配 JacksonDecoder:本客户端方法返回 String,要的是**响应体原文**;
                // 用 JacksonDecoder 会把 JSON 对象往 String 里塞而报错(实测:返回 boolean 的接口能过、返回对象的接口全挂)
                .retryer(Retryer.NEVER_RETRY)
                .options(new Request.Options(connect, read))
                .requestInterceptor(new HttpRpcOutboundInterceptor(discoveryClient, facadeFqn, resolver, internalToken))
                // target 仅为占位 URL: 真实目标由拦截器每次请求经三级解析后改写
                .target(HttpRpcClient.class, "http://" + resolver.resolveStatic(facadeFqn));
    }

    /**
     * 取 Environment:优先从容器取(Spring 会把 Environment 注册为单例 bean),
     * 再退回 {@link EnvironmentCapable} —— 注意 {@code BeanFactory} 本身**不是** EnvironmentCapable
     * (实测踩到:只看 instanceof 会拿到 null,导致读不到 internal-token / 超时配置)。
     */
    private Environment resolveEnvironment() {
        try {
            return beanFactory.getBean(Environment.class);
        } catch (Exception ignored) {
            return beanFactory instanceof EnvironmentCapable
                    ? ((EnvironmentCapable) beanFactory).getEnvironment() : null;
        }
    }

    private BizException mapTransportFailure(Class<?> facade, Method method, FeignException e) {
        String where = facade.getSimpleName() + "#" + method.getName();
        String body = null;
        try {
            body = e.contentUTF8();
        } catch (Exception ignored) {
            // 无响应体
        }
        return TransportFailureMapper.map(where, e.status(), body, beanFactory.getBean(ObjectMapper.class));
    }

    /** 服务发现可选:缺失时保持 Feign 目标原样(仅测试/特殊环境会出现)。 */
    private DiscoveryClient discoveryClientOrNull() {
        try {
            return beanFactory.getBean(DiscoveryClient.class);
        } catch (Exception e) {
            log.warn("[rpc-http] 未找到 DiscoveryClient,HTTP 传输将不做实例负载均衡: {}", e.getMessage());
            return null;
        }
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "toString":
                return "HttpRefProxy(" + facadeFqn + ")";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return args != null && args.length == 1 && proxy == args[0];
            default:
                return null;
        }
    }
}
