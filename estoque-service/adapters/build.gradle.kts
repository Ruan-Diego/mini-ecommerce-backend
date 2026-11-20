plugins {
    id("mini.spring-adapter")
}

dependencies {
    api(project(":estoque-service:application"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // OpenAPI gerado a partir dos controllers, com Swagger UI em /swagger-ui/index.html.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
    // Saldos, reservas, mensagens tratadas e outbox: MySQL.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    // Relay da outbox publica no RabbitMQ; os listeners consomem os eventos do pedido.
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    // Só a API do OpenTelemetry (o Java agent, quando presente, fornece a implementação) e o Micrometer das métricas.
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.micrometer:micrometer-core")

    // Testes de integração: MySQL (e RabbitMQ) de verdade em containers, rodando os mesmos contratos dos adapters em memória.
    testImplementation(testFixtures(project(":estoque-service:application")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:rabbitmq")
}
