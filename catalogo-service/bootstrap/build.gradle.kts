plugins {
    id("mini.spring-boot-app")
}

dependencies {
    implementation(project(":catalogo-service:adapters"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // /actuator/prometheus: métricas HTTP, JVM e de negócio (mini_*) para o Prometheus.
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Resource server: valida o JWT do Keycloak e exige o papel admin em /admin/**.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    testImplementation("org.springframework.security:spring-security-test")
    // Transação dos use cases, amarrada no CatalogoConfig.
    implementation("org.springframework:spring-tx")

    // Teste de integração do serviço inteiro contra MySQL em container.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
}
