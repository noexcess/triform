server:
  port: __HTTP_PORT__
spring:
  config:
    import:
      - classpath:triform-shared.yml
  application:
    name: ticket-__NAME__
  autoconfigure:
    exclude:
      - org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration
deployment:
  mode: mono
