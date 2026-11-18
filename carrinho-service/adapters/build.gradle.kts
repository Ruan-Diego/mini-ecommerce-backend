// As dependências de cada adapter (web, JPA, Redis, RabbitMQ) entram junto com a issue que cria o adapter.
plugins {
    id("mini.spring-adapter")
}

dependencies {
    api(project(":carrinho-service:application"))
}
