plugins {
    id("mini.spring-boot-app")
}

dependencies {
    implementation(project(":estoque-service:adapters"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Resource server: valida o JWT do Keycloak (o ajuste de estoque é só do papel admin).
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    // Transação dos use cases (EstoqueConfig) e agendamento do relay (OutboxAgendador).
    implementation("org.springframework:spring-tx")

    // Teste de integração do serviço inteiro contra MySQL e RabbitMQ em containers.
    // O teste confere o estado direto no MySQL e no RabbitMQ (os adapters guardam essas dependências para si).
    testImplementation("org.springframework.boot:spring-boot-starter-amqp")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:rabbitmq")
}
