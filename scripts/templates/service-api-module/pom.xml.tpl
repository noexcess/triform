<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.triform</groupId>
        <artifactId>service-api</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>service-__NAME__-api</artifactId>
    <description>__NAME__ 服务跨服务契约(纯接口+DTO, 零实现)</description>
    <dependencies>
        <dependency>
            <groupId>io.triform</groupId>
            <artifactId>triform-rpc</artifactId>
            <version>${project.version}</version>
        </dependency>
        <!-- E3: 编译期自动生成 META-INF/triform-facades 索引 -->
        <dependency>
            <groupId>io.triform</groupId>
            <artifactId>triform-indexer</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
