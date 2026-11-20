plugins {
    id("mini.spring-boot-app")
}

dependencies {
    implementation(project(":pedido-service:adapters"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Resource server: valida o JWT do Keycloak; o sub do token é o dono do pedido.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    // Transação dos use cases (PedidoConfig) e agendamento do relay (OutboxAgendador).
    implementation("org.springframework:spring-tx")
    // Trava do job de expiração entre instâncias (ShedLock, na tabela shedlock do MySQL) e o JdbcTemplate dela.
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.16.0")
    implementation("org.springframework:spring-jdbc")

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
