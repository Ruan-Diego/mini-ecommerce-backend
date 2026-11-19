plugins {
    id("mini.spring-adapter")
}

dependencies {
    api(project(":carrinho-service:application"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // OpenAPI gerado a partir dos controllers, com Swagger UI em /swagger-ui/index.html.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
    // Carrinho do cliente logado: MySQL.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    // Carrinho de visitante: Redis (com expiração).
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Testes de integração: MySQL e Redis de verdade em containers, rodando os mesmos contratos do adapter em memória.
    testImplementation(testFixtures(project(":carrinho-service:application")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
}
