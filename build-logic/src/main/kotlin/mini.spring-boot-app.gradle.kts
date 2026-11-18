// Módulo executável (bootstrap de cada serviço e o gateway): Spring Boot, wiring e testes de arquitetura.
plugins {
    id("mini.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}
