// Módulo adapters: implementa os ports com Spring (REST, JPA, RabbitMQ, HTTP).
// Usa o BOM do Spring Boot para as versões, mas não é uma aplicação executável.
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("mini.java-conventions")
    `java-library`
    id("io.spring.dependency-management")
}

// O BOM do Spring Boot 3.5.6 traz Testcontainers 1.21.3, cujo docker-java usa a API 1.32 do Docker.
// O Docker do runner do GitHub Actions exige no mínimo 1.40 e recusa a conexão ("client version 1.32 is too old").
extra["testcontainers.version"] = "1.21.4"

dependencyManagement {
    imports { mavenBom(SpringBootPlugin.BOM_COORDINATES) }
}
