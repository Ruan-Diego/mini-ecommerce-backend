plugins {
    id("mini.spring-boot-app")
}

dependencies {
    implementation(project(":carrinho-service:adapters"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // /actuator/prometheus: métricas HTTP, JVM e de negócio (mini_*) para o Prometheus.
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Teste de integração do serviço inteiro contra MySQL, Redis e RabbitMQ em containers.
    // O teste confere o estado direto no Redis e no MySQL (os adapters guardam essas dependências para si).
    testImplementation("org.springframework.boot:spring-boot-starter-amqp")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:rabbitmq")
}
