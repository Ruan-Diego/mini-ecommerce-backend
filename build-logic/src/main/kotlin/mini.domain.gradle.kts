// Módulo de domínio (e o shared-kernel): só JDK.
// Pode depender de outros módulos do projeto (ex.: shared-kernel), nunca de biblioteca externa.
// Dependências de teste (JUnit, AssertJ) não entram na regra.
plugins {
    id("mini.java-conventions")
    `java-library`
}

val configuracoesDeProducao = listOf("api", "implementation", "compileOnly", "runtimeOnly")

val dependenciasExternas = provider {
    configuracoesDeProducao
        .flatMap { configurations.getByName(it).dependencies }
        .filterNot { it is ProjectDependency }
        .map { "${it.group}:${it.name}" }
}

val verificarSoJdk by tasks.registering {
    group = "verification"
    description = "Falha se o módulo de domínio declarar dependência externa."
    val externas = dependenciasExternas
    val modulo = path
    doLast {
        val lista = externas.get()
        if (lista.isNotEmpty()) {
            throw GradleException(
                "$modulo é um módulo de domínio e só pode depender do JDK e de outros módulos do projeto. " +
                    "Remova: ${lista.joinToString()}"
            )
        }
    }
}

tasks.named("check") { dependsOn(verificarSoJdk) }
