deployment:
  mode: micro
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:114.132.52.228:8848}
  autoconfigure:
    exclude: []
dubbo:
  application:
    name: ticket-__NAME__
    register-mode: interface
  registry:
    address: nacos://${NACOS_SERVER_ADDR:114.132.52.228:8848}
  protocol:
    name: dubbo
    port: __DUBBO_PORT__
