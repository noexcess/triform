package io.triform.testing.rpc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * RPC 接线守卫（构建期）—— 把"靠人记"的三条静默陷阱改成构建失败。
 *
 * <ol>
 *   <li><b>provider 导出缺位</b>：实现类不是 Spring Bean（FacadeAutoExporter 扫不到），或退回
 *       {@code @DubboService}/{@code @EnableDubbo} 注解路径（双路径）→ 静默不导出，消费方 5s 超时后才失败；</li>
 *   <li><b>api 模块缺 {@code triform-rpc} 依赖</b> → {@code @RemoteFacade} 注解不可用 / Registrar 扫不到；</li>
 *   <li><b>非聚合模块依赖 service 模块</b> → 对方实现类进入本进程 classpath，被 {@code DubboConsumerRegistrar}
 *       的自动兜底误判为"本地实现"（行为错、但不报错，只在启动日志里能看出）；</li>
 *   <li><b>服务端口重复</b> → mono 与 micro 互相抢占（历史事故：mono 占 20881 导致 verify micro 启动 BindException）。</li>
 * </ol>
 *
 * 实现取舍：只按 pom / 源码 / yml 的**文本**判读，不启动 Spring 上下文、不引入额外依赖 —— 守卫要简单直白、
 * 失败信息直接告诉你改哪里；代价是极端情形（例如注释里出现同名注解）可能误判，这类守卫误报时改注释即可。
 *
 * <p>⚠️ 本守卫只在**测试阶段**执行：用 {@code -DskipTests} 构建时不会跑。提交前请执行
 * {@code mvn -s .mvn-settings.xml test}，或交给 CI。
 *
 * <p>背景见《10-新增微服务与跨服务调用指南》。
 */
class RpcWiringGuardTest {

    private static final Pattern DUBBO_SERVICE = Pattern.compile("@DubboService\\b");
    private static final Pattern ENABLE_DUBBO = Pattern.compile("@EnableDubbo\\b");
    private static final Pattern REMOTE_FACADE = Pattern.compile("@RemoteFacade\\b");
    private static final Pattern SPRING_BOOT_APPLICATION = Pattern.compile("@SpringBootApplication\\b");
    private static final Pattern POM_DEPENDENCY = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL);

    /** 已知且有意为之的端口重叠：mono(all-in-one) 与 iam micro 都绑 8080/20880，故两者互斥、不可同跑。 */
    private static final List<Set<String>> KNOWN_PORT_SHARING = new java.util.ArrayList<>();

    // ===================== 1. 启动类零传输注解(provider 导出走编程式) =====================

    @Test
    void noBootApplicationCarriesDubboScan() throws IOException {
        Path root = repoRoot();
        List<String> problems = new ArrayList<>();
        for (Path serviceDir : serviceDirs(root)) {
            List<Path> javaFiles = javaFiles(serviceDir.resolve("src/main/java"));
            List<Path> boots = javaFiles.stream()
                    .filter(p -> SPRING_BOOT_APPLICATION.matcher(stripComments(read(p))).find())
                    .collect(Collectors.toList());
            assertTrue(boots.size() == 1,
                    serviceDir.getFileName() + " 应有且仅有一个 @SpringBootApplication 启动类, 实际 " + boots.size() + " 个");
            if (ENABLE_DUBBO.matcher(stripComments(read(boots.get(0)))).find()) {
                problems.add(serviceDir.getFileName() + " 的启动类带 @EnableDubbo"
                        + " —— provider 导出已由 FacadeAutoExporter 编程式承担(基线/42 §8.4),"
                        + " 注解扫描是历史路径, 出现即双路径风险, 请删除");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\\n", problems));
    }

    // ===================== 2. Facade 契约模块必须能被扫描 =====================

    @Test
    void everyApiModuleWithRemoteFacadeDependsOnRpcSupport() throws IOException {
        Path root = repoRoot();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root.resolve("service-api"))) {
            for (Path apiDir : dirs.filter(Files::isDirectory).collect(Collectors.toList())) {
                List<Path> javaFiles = javaFiles(apiDir.resolve("src/main/java"));
                boolean hasFacade = javaFiles.stream().anyMatch(p -> REMOTE_FACADE.matcher(stripComments(read(p))).find());
                if (!hasFacade) {
                    continue;
                }
                Path pom = apiDir.resolve("pom.xml");
                if (!Files.exists(pom) || !read(pom).contains("triform-rpc")) {
                    problems.add(apiDir.getFileName() + " 含 @RemoteFacade 接口, 但 pom 未依赖 triform-rpc"
                            + " —— 注解不可用且 Registrar 扫不到该接口");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ===================== 3. 只有聚合模块可以依赖 service 模块 =====================

    @Test
    void onlyAggregatorMayDependOnServiceModules() throws IOException {
        Path root = repoRoot();
        Set<String> serviceArtifacts = new LinkedHashSet<>();
        for (Path serviceDir : serviceDirs(root)) {
            serviceArtifacts.add(artifactId(serviceDir.resolve("pom.xml")));
        }
        List<String> problems = new ArrayList<>();
        for (Path pom : allPoms(root)) {
            String module = root.relativize(pom.getParent()).toString().replace('\\', '/');
            if ("all-in-one".equals(module)) {
                continue; // 聚合模块：mono 就是要把所有服务装进一个进程
            }
            for (String dependency : dependencyBlocks(read(pom))) {
                for (String serviceArtifact : serviceArtifacts) {
                    if (dependency.contains("<artifactId>" + serviceArtifact + "</artifactId>")) {
                        problems.add(module + " 依赖了服务模块 " + serviceArtifact
                                + " —— 对方实现类会进入本进程 classpath, 被自动兜底误判为本地实现"
                                + "(服务之间只应依赖对方的 *-api 契约模块)");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ===================== 5. 实现类禁止 @DubboService(编程式导出已接管) =====================

    @Test
    void noFacadeImplCarriesDubboServiceAnnotation() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path serviceDir : serviceDirs(repoRoot())) {
            for (Path javaFile : javaFiles(serviceDir.resolve("src/main/java"))) {
                String text = stripComments(read(javaFile));
                if (DUBBO_SERVICE.matcher(text).find()) {
                    problems.add(serviceDir.getFileName() + " 的 " + javaFile.getFileName()
                            + " 带 @DubboService —— provider 导出已由 FacadeAutoExporter 编程式承担(基线/42 §8.4),"
                            + " 注解会让提供方出现两条导出路径, 请删除");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\\n", problems));
    }

    // ===================== 6. 本服务实现了的 Facade 必须导出 =====================

    @Test
    void everyLocallyImplementedFacadeIsExported() throws IOException {
        Path root = repoRoot();
        Map<String, String> facades = new LinkedHashMap<>();   // simpleName -> 定义它的模块
        try (Stream<Path> dirs = Files.list(root.resolve("service-api"))) {
            for (Path apiDir : dirs.filter(Files::isDirectory).collect(Collectors.toList())) {
                for (Path javaFile : javaFiles(apiDir.resolve("src/main/java"))) {
                    if (REMOTE_FACADE.matcher(stripComments(read(javaFile))).find()) {
                        facades.put(javaFile.getFileName().toString().replace(".java", ""),
                                apiDir.getFileName().toString());
                    }
                }
            }
        }
        List<String> problems = new ArrayList<>();
        for (Path serviceDir : serviceDirs(root)) {
            for (Path javaFile : javaFiles(serviceDir.resolve("src/main/java"))) {
                String text = stripComments(read(javaFile));
                String fileName = javaFile.getFileName().toString();
                for (Map.Entry<String, String> facade : facades.entrySet()) {
                    String simple = facade.getKey();
                    boolean isImpl = fileName.equals(simple + "Impl.java")
                            || Pattern.compile("implements[^{]*\\b" + Pattern.quote(simple) + "\\b").matcher(text).find();
                    boolean springBean = text.contains("org.springframework.stereotype.Service")
                            || text.contains("org.springframework.stereotype.Component");
                    if (isImpl && !springBean) {
                        problems.add(serviceDir.getFileName() + " 的 " + fileName + " 实现了 Facade " + simple
                                + "(" + facade.getValue() + " 的契约) 但不是 Spring Bean(缺 @Service/@Component)"
                                + " —— micro 下 FacadeAutoExporter 只扫描 Spring Bean 编程式导出, 非 Bean 不会被导出,"
                                + " 其他服务消费该接口时找不到 provider(若确为本地专用实现, 请去掉接口上的 @RemoteFacade)");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\\n", problems));
    }

    // ===================== 7. 我方模块之间不得有同 FQN 的类 =====================

    /**
     * 我方模块之间不得出现同 FQN 的类(mono 下加载到哪一个不确定)。
     *
     * <p>范围说明:这里只守**本项目源码**之间的重复(典型场景:跨服务复制粘贴、或在聚合模块里又写了一份)。
     * 依赖 jar 之间的重复类不在本守卫范围 —— 曾用 maven-enforcer 的 banDuplicateClasses 尝试覆盖,
     * 但实测它只比对依赖之间(注入同 FQN 的本地源文件不触发),且排查厂商打包噪声的成本高于收益,已评估放弃
     * (见《需求20260915-类查重评估与RPC接线守卫-任务》)。依赖版本错位建议在升级依赖时人工核对。
     */
    @Test
    void noDuplicateClassNamesAcrossOurOwnModules() throws IOException {
        Path root = repoRoot();
        List<Path> modules = new ArrayList<>();
        try (Stream<Path> top = Files.list(root)) {
            for (Path dir : top.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve("pom.xml")))
                    .collect(Collectors.toList())) {
                if (Files.exists(dir.resolve("src/main/java"))) {
                    modules.add(dir);
                }
                try (Stream<Path> sub = Files.list(dir)) {
                    for (Path child : sub.filter(Files::isDirectory)
                            .filter(d -> Files.exists(d.resolve("pom.xml")))
                            .collect(Collectors.toList())) {
                        if (Files.exists(child.resolve("src/main/java"))) {
                            modules.add(child);
                        }
                    }
                }
            }
        }
        Map<String, List<String>> owners = new LinkedHashMap<>();
        for (Path module : modules) {
            Path srcRoot = module.resolve("src/main/java");
            String moduleName = root.relativize(module).toString().replace('\\', '/');
            for (Path javaFile : javaFiles(srcRoot)) {
                String rel = srcRoot.relativize(javaFile).toString().replace('\\', '/');
                String fqn = rel.substring(0, rel.length() - ".java".length()).replace('/', '.');
                owners.computeIfAbsent(fqn, k -> new ArrayList<>()).add(moduleName);
            }
        }
        List<String> problems = new ArrayList<>();
        owners.forEach((fqn, modulesOwning) -> {
            if (modulesOwning.size() > 1) {
                problems.add("同 FQN 类出现在多个模块: " + fqn + " -> " + modulesOwning
                        + " (mono 下加载到哪一个不确定)");
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ===================== 4. 服务端口唯一 =====================

    @Test
    void servicePortsAreUnique() throws IOException {
        Path root = repoRoot();
        Map<String, int[]> table = new LinkedHashMap<>();   // module -> {http, dubbo}
        for (Path dir : projectDirsWithApplicationYml(root)) {
            // 以 artifactId 为键: 与 KNOWN_PORT_SHARING 的写法一致(用相对路径会带 service/ 前缀, 匹配不上)
            String module = artifactId(dir.resolve("pom.xml"));
            Integer http = firstNonNull(portOf(dir.resolve("src/main/resources/application.yml"), "server", "port"),
                    portOf(dir.resolve("src/main/resources/application-micro.yml"), "server", "port"));
            Integer dubbo = firstNonNull(portOf(dir.resolve("src/main/resources/application-micro.yml"), "dubbo", "protocol", "port"),
                    portOf(dir.resolve("src/main/resources/application.yml"), "dubbo", "protocol", "port"));
            // all-in-one 是 mono 的 Web 入口: 其 starter-web 是经 service 模块传递进来的, pom 文本里看不到
            boolean webApp = isWebApp(dir) || module.endsWith("all-in-one");
            if (http == null && webApp) {
                http = 8080;    // Spring Boot 默认
            }
            // Dubbo 默认端口只对"真的引了 Dubbo 的模块 + mono 聚合"成立;网关是纯 WebFlux, 不参与
            if (dubbo == null && webApp && (hasDubboDependency(dir) || module.endsWith("all-in-one"))) {
                dubbo = 20880;  // Dubbo 默认
            }
            table.put(module, new int[]{http == null ? -1 : http, dubbo == null ? -1 : dubbo});
        }

        List<String> problems = new ArrayList<>();
        List<String> entries = new ArrayList<>(table.keySet());
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                String a = entries.get(i);
                String b = entries.get(j);
                if (knownSharing(a, b)) {
                    continue;
                }
                int[] pa = table.get(a);
                int[] pb = table.get(b);
                if (pa[0] > 0 && pa[0] == pb[0]) {
                    problems.add(a + " 与 " + b + " 的 HTTP 端口重复: " + pa[0]);
                }
                if (pa[1] > 0 && pa[1] == pb[1]) {
                    problems.add(a + " 与 " + b + " 的 Dubbo 端口重复: " + pa[1]);
                }
            }
            int[] p = table.get(entries.get(i));
            if (p[0] > 0 && p[0] == p[1]) {
                problems.add(entries.get(i) + " 的 HTTP 与 Dubbo 端口相同: " + p[0]);
            }
        }
        // 端口表打出来,便于评审与容量基线核对
        StringBuilder view = new StringBuilder("端口登记表(HTTP/Dubbo, -1=不适用):\n");
        table.forEach((m, p) -> view.append(String.format("  %-28s %5d / %5d%n", m, p[0], p[1])));
        view.append("  已知有意重叠(互斥,不可同跑): ").append(KNOWN_PORT_SHARING);
        System.out.println(view);
        assertTrue(problems.isEmpty(), String.join("\n", problems) + "\n" + view);
    }

    private static boolean knownSharing(String a, String b) {
        for (Set<String> pair : KNOWN_PORT_SHARING) {
            if (pair.contains(a) && pair.contains(b)) {
                return true;
            }
        }
        return false;
    }

    // ===================== 8. SQL 注释里不得出现半角分号 =====================

    /**
     * schema / 迁移脚本的 `-- 注释` 与 `COMMENT '...'` 里不得出现半角分号：
     * App 用 ResourceDatabasePopulator 按分号切语句，注释里的分号会把 CREATE/ALTER 拦腰截断。
     * 实测踩到两次（一次 iam 的 schema、一次 verify 的迁移），症状是启动后报 SQL 语法错或"未知列"，
     * 且根因被启动失败监听器吞掉、很难定位，故加此守卫。
     */
    @Test
    void noSemicolonInsideSqlComments() throws IOException {
        Path root = repoRoot();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> sqlFiles = walk.filter(p -> p.toString().endsWith(".sql"))
                    .filter(p -> !p.toString().contains("target"))
                    .collect(Collectors.toList());
            for (Path sql : sqlFiles) {
                int lineNo = 0;
                for (String line : read(sql).split("\\r?\\n")) {
                    lineNo++;
                    String trimmed = line.trim();
                    boolean badLineComment = trimmed.startsWith("--") && trimmed.indexOf(';') >= 0;
                    boolean badInlineComment = false;
                    int idx = line.indexOf("COMMENT '");
                    if (idx >= 0) {
                        String after = line.substring(idx + "COMMENT '".length());
                        int end = after.indexOf('\'');
                        badInlineComment = end > 0 && after.substring(0, end).indexOf(';') >= 0;
                    }
                    if (badLineComment || badInlineComment) {
                        problems.add(root.relativize(sql) + ":" + lineNo + " 注释里出现半角分号, 会截断 SQL 语句");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ===================== 工具方法 =====================

    /**
     * 仓根：从当前模块目录往上找。
     * 注意不能只看 pom 里是否有 triform —— 中间层聚合模块等的 &lt;parent&gt; 里也有它，
     * 故同时要求存在 service/ 与 service-api/ 两个聚合目录。
     */
    private static Path repoRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null) {
            Path pom = p.resolve("pom.xml");
            if (Files.exists(pom)
                    && read(pom).contains("<artifactId>triform</artifactId>")
                    && Files.exists(p.resolve("service/pom.xml"))
                    && Files.exists(p.resolve("service-api/pom.xml"))) {
                return p;
            }
            p = p.getParent();
        }
        fail("找不到仓根: 应从模块目录往上找到同时含 triform pom 与 service/、service-api/ 的目录");
        return null;
    }

    private static List<Path> serviceDirs(Path root) throws IOException {
        try (Stream<Path> dirs = Files.list(root.resolve("service"))) {
            return dirs.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve("pom.xml")))
                    .collect(Collectors.toList());
        }
    }

    private static List<Path> projectDirsWithApplicationYml(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        result.addAll(serviceDirs(root));
        result.add(root.resolve("gateway"));
        result.add(root.resolve("all-in-one"));
        return result.stream()
                .filter(d -> Files.exists(d.resolve("src/main/resources/application.yml")))
                .collect(Collectors.toList());
    }

    private static boolean isWebApp(Path moduleDir) {
        Path pom = moduleDir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return false;
        }
        String text = read(pom);
        return text.contains("spring-boot-starter-web") || text.contains("spring-cloud-starter-gateway");
    }

    /** 只认依赖块里的 Dubbo(注释里出现 "dubbo" 不算,例如网关 pom 的说明性注释)。 */
    private static boolean hasDubboDependency(Path moduleDir) {
        Path pom = moduleDir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return false;
        }
        for (String dependency : dependencyBlocks(read(pom))) {
            if (dependency.contains("dubbo") || dependency.contains("triform-rpc")) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> allPoms(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> !p.toString().contains("node_modules"))
                    .collect(Collectors.toList());
        }
    }

    private static List<Path> javaFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static List<String> dependencyBlocks(String pomText) {
        List<String> blocks = new ArrayList<>();
        Matcher m = POM_DEPENDENCY.matcher(pomText);
        while (m.find()) {
            blocks.add(m.group(1));
        }
        return blocks;
    }

    private static String artifactId(Path pom) {
        Matcher m = Pattern.compile("<artifactId>([^<]+)</artifactId>").matcher(read(pom));
        // 第一个 artifactId 是 parent(common-module/parent), 模块自身的是第二个
        m.find();
        return m.find() ? m.group(1) : "";
    }

    /** 极简 YAML 取值：按缩进栈找 server.port / dubbo.protocol.port（不引入 yaml 依赖）。 */
    private static Integer portOf(Path yml, String... path) {
        if (!Files.exists(yml)) {
            return null;
        }
        List<String> stack = new ArrayList<>();
        String[] lines = read(yml).split("\r?\n");
        for (String raw : lines) {
            String line = raw.replaceAll("#.*$", "");
            if (line.trim().isEmpty()) {
                continue;
            }
            int indent = line.length() - line.replaceAll("^\\s+", "").length();
            Matcher kv = Pattern.compile("^\\s*([\\w.-]+):\\s*(.*)$").matcher(line);
            if (!kv.find()) {
                continue;
            }
            int depth = indent / 2;
            while (stack.size() > depth) {
                stack.remove(stack.size() - 1);
            }
            String key = kv.group(1);
            String value = kv.group(2).trim();
            if (!value.isEmpty()) {
                if (stack.size() == path.length - 1 && matchesPath(stack, path, key)) {
                    Matcher num = Pattern.compile("^(\\d+)$").matcher(value);
                    if (num.find()) {
                        return Integer.valueOf(num.group(1));
                    }
                    Matcher env = Pattern.compile("\\$\\{[^:}]+:(-?\\d+)}").matcher(value);
                    if (env.find() && Integer.parseInt(env.group(1)) > 0) {
                        return Integer.valueOf(env.group(1));
                    }
                }
                continue;
            }
            stack.add(key);
        }
        return null;
    }

    private static boolean matchesPath(List<String> stack, String[] path, String lastKey) {
        for (int i = 0; i < path.length - 1; i++) {
            if (i >= stack.size() || !path[i].equals(stack.get(i))) {
                return false;
            }
        }
        return path[path.length - 1].equals(lastKey);
    }

    private static Integer firstNonNull(Integer... values) {
        for (Integer v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * 剥掉 // 与 / * * / 注释后再做注解/关键字判读 —— 否则 javadoc 里提到 @DubboService 之类也会被当成"真的声明了"
     * (2026-09-15 实测:ServiceIamApplication 的注释提到 @DubboService, 导致守卫 5 假阳性)。
     */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取失败: " + p, e);
        }
    }
}
