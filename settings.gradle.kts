rootProject.name = "mini-ecommerce-backend"

include("shared-kernel")
include("gateway")

// catalogo-service: um módulo Gradle por camada da arquitetura hexagonal
include("catalogo-service:domain")
include("catalogo-service:application")
include("catalogo-service:adapters")
include("catalogo-service:bootstrap")

// carrinho, pedido, pagamento e estoque entram nas fases 2 a 4 com a mesma estrutura
