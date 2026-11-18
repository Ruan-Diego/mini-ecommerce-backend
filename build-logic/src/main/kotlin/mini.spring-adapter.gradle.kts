// Módulo adapters: implementa os ports com Spring (REST, JPA, RabbitMQ, HTTP).
// Usa o BOM do Spring Boot para as versões, mas não é uma aplicação executável.
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("mini.java-conventions")
    `java-library`
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports { mavenBom(SpringBootPlugin.BOM_COORDINATES) }
}
