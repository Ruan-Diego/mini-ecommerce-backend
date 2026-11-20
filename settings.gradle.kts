pluginManagement {
    // Convention plugins do projeto (mini.domain, mini.application, mini.spring-adapter, mini.spring-boot-app)
    includeBuild("build-logic")
}

// Repositórios declarados uma vez para todos os módulos; um módulo não pode adicionar os seus.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "mini-ecommerce-backend"

include("shared-kernel")
include("gateway")
include("saga-tests") // testes de integração que sobem pedido, estoque e pagamento juntos

// Cada serviço tem um módulo Gradle por camada da arquitetura hexagonal.
val servicos = listOf("catalogo", "carrinho", "pedido", "pagamento", "estoque")
val camadas = listOf("domain", "application", "adapters", "bootstrap")

for (servico in servicos) {
    for (camada in camadas) {
        include("$servico-service:$camada")
    }
}
