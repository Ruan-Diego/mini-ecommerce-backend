plugins {
    id("mini.spring-adapter")
}

dependencies {
    api(project(":pedido-service:application"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // OpenAPI gerado a partir dos controllers, com Swagger UI em /swagger-ui/index.html.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
    // Pedidos e outbox: MySQL.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    // Retry do frete (FreteComFallback). Só o módulo de retry, sem o starter do Spring.
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    // Relay da outbox: publica os eventos no RabbitMQ.
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // Testes de integração: MySQL (e RabbitMQ) de verdade em containers, rodando os mesmos contratos dos adapters em memória.
    testImplementation(testFixtures(project(":pedido-service:application")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:rabbitmq")
}
