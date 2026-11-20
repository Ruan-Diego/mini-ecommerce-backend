// Teste de integração da saga: sobe os jars reais do pedido, do estoque e do pagamento juntos, contra MySQL e
// RabbitMQ em containers. Não tem código de produção; só testes.
//
// Cada serviço roda como um PROCESSO próprio (java -jar), e não como um contexto Spring dentro da JVM do teste,
// porque os três têm `application.yml` e `db/migration` no mesmo caminho do classpath e um sobrescreveria o outro.
// Rodar os jars também é o que acontece de verdade em produção.
plugins {
    id("mini.spring-adapter")
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-jdbc")
    testRuntimeOnly("com.mysql:mysql-connector-j")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    // Para assinar os JWTs do teste (o mesmo recurso que o teste de segurança do gateway usa).
    testImplementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:rabbitmq")
}

val servicos = listOf("pedido", "estoque", "pagamento")

tasks.test {
    // Os jars executáveis precisam existir antes dos testes; o caminho de cada um vai por propriedade de sistema.
    servicos.forEach { servico ->
        dependsOn(":$servico-service:bootstrap:bootJar")
        val jar = project(":$servico-service:bootstrap").layout.buildDirectory
            .file("libs/bootstrap-${project.version}.jar").get().asFile
        // O jar é ENTRADA da tarefa: sem isso o Gradle vê o teste como atualizado quando só o código dos serviços muda.
        inputs.file(jar).withPropertyName("jar-$servico").withPathSensitivity(PathSensitivity.NONE)
        systemProperty("saga.jar.$servico", jar.absolutePath)
    }
    systemProperty("saga.logs", layout.buildDirectory.dir("logs").get().asFile.absolutePath)
    testLogging { events("failed") }
}
