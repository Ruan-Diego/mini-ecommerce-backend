plugins {
    id("mini.spring-adapter")
}

dependencies {
    api(project(":catalogo-service:application"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // OpenAPI gerado a partir dos controllers, com Swagger UI em /swagger-ui.html.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    // Relay da outbox: publica os eventos no RabbitMQ.
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Testes de integração: MySQL de verdade num container, rodando os mesmos contratos do adapter em memória.
    testImplementation(testFixtures(project(":catalogo-service:application")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
}
