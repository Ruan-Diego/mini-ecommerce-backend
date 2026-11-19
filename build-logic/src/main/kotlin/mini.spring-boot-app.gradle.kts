// Módulo executável (bootstrap de cada serviço e o gateway): Spring Boot, wiring e testes de arquitetura.
plugins {
    id("mini.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// O BOM do Spring Boot 3.5.6 traz Testcontainers 1.21.3, cujo docker-java usa a API 1.32 do Docker.
// O Docker do runner do GitHub Actions exige no mínimo 1.40 e recusa a conexão ("client version 1.32 is too old").
extra["testcontainers.version"] = "1.21.4"

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}
