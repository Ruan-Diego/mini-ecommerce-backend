// Módulo executável (bootstrap de cada serviço e o gateway): Spring Boot, wiring e testes de arquitetura.
plugins {
    id("mini.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// O BOM do Spring Boot 3.5.6 traz Testcontainers 1.21.3, cujo docker-java usa a API 1.32 do Docker.
// O Docker do runner do GitHub Actions exige no mínimo 1.40 e recusa a conexão ("client version 1.32 is too old").
extra["testcontainers.version"] = "1.21.4"

// OpenTelemetry Java agent: instrumenta HTTP, JDBC e RabbitMQ sem código e exporta traces e logs por OTLP.
// Fica fora do classpath da aplicação (configuração própria) e só entra no `bootRun` e na imagem Docker;
// os testes rodam sem ele.
val agenteOtel: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    agenteOtel("io.opentelemetry.javaagent:opentelemetry-javaagent:2.33.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}

/** Copia o agent para build/otel/opentelemetry-javaagent.jar (nome fixo, usado pelo bootRun e pelo Dockerfile). */
val copiarAgenteOtel by tasks.registering(Copy::class) {
    from(agenteOtel)
    into(layout.buildDirectory.dir("otel"))
    rename { "opentelemetry-javaagent.jar" }
}

tasks.named("assemble") {
    dependsOn(copiarAgenteOtel)
}

// `bootRun` sobe com o agent. Sem o collector no ar o agent só avisa no log que não conseguiu exportar.
// Métricas saem pelo Actuator (/actuator/prometheus), então o exportador de métricas do agent fica desligado.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn(copiarAgenteOtel)
    val agente = layout.buildDirectory.file("otel/opentelemetry-javaagent.jar")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-javaagent:${agente.get().asFile.absolutePath}", "-Dotel.metrics.exporter=none")
    })
}
