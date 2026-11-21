plugins {
    id("mini.spring-boot-app")
}

dependencies {
    implementation(project(":pagamento-service:adapters"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // /actuator/prometheus: métricas HTTP, JVM e de negócio (mini_*) para o Prometheus.
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Resource server: valida o JWT do Keycloak; o sub do token é o dono do pagamento.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    // Transação dos use cases (PagamentoConfig) e agendamento do relay (OutboxAgendador).
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

// Contratos Pact dos microfrontends (ADR-0005): o ContratoPactTest verifica os arquivos de contratos/pacts.
// A pasta é entrada da tarefa de teste: mudar um contrato faz o teste rodar de novo (o cache não o pula).
dependencies {
    testImplementation("au.com.dius.pact.provider:junit5spring:4.7.6")
}

tasks.test {
    val pacts = rootProject.layout.projectDirectory.dir("contratos/pacts")
    inputs.dir(pacts).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("contratosPact")
}
