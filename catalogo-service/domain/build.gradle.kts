// Domínio puro: nada de Spring, JPA ou Jackson aqui (verificado por verificarSoJdk e pelo ArchUnit).
plugins {
    id("mini.domain")
}

dependencies {
    api(project(":shared-kernel"))
}
