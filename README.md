# mini-ecommerce-backend

Backend do **Mini E-commerce**, um projeto de estudo de **arquitetura hexagonal** e **microfrontends**.

Java 21 · Spring Boot 3.5 · Gradle multi-module · MySQL 8 · RabbitMQ · Redis · Keycloak · Spring Cloud Gateway

## Repositórios do projeto

| Repositório | Papel |
| --- | --- |
| **mini-ecommerce-backend** (este) | Gateway, serviços hexagonais e infra local |
| [mini-ecommerce-ui](https://github.com/Ruan-Diego/mini-ecommerce-ui) | Design-system e contratos de eventos |
| [mini-ecommerce-shell](https://github.com/Ruan-Diego/mini-ecommerce-shell) | Host dos microfrontends |
| [mini-ecommerce-mfe-catalogo](https://github.com/Ruan-Diego/mini-ecommerce-mfe-catalogo) | Home, listagem e detalhe do produto |
| [mini-ecommerce-mfe-carrinho](https://github.com/Ruan-Diego/mini-ecommerce-mfe-carrinho) | Mini-carrinho e carrinho |
| [mini-ecommerce-mfe-checkout](https://github.com/Ruan-Diego/mini-ecommerce-mfe-checkout) | Checkout, Pix e confirmação |
| [mini-ecommerce-mfe-conta](https://github.com/Ruan-Diego/mini-ecommerce-mfe-conta) | Conta e pedidos do cliente |
| [mini-ecommerce-mfe-admin](https://github.com/Ruan-Diego/mini-ecommerce-mfe-admin) | Painel administrativo |

## Estrutura

```
build-logic/            Convention plugins: mini.domain, mini.application, mini.spring-adapter, mini.spring-boot-app
shared-kernel/          Dinheiro, Id, DomainEvent, AggregateRoot (só JDK)
gateway/                Spring Cloud Gateway (porta 8080)
catalogo-service/       domain · application · adapters · bootstrap (porta 8081)
carrinho-service/       idem (porta 8082)
pedido-service/         idem (porta 8083)
pagamento-service/      idem (porta 8084)
estoque-service/        idem (porta 8085)
infra/                  Docker Compose, MySQL init, realm do Keycloak
docs/adr/               Decisões de arquitetura
```

Cada módulo declara o próprio papel com um convention plugin. Módulos `mini.domain` (o `shared-kernel` e o `domain` de cada serviço) falham no `check` se declararem qualquer dependência externa. carrinho, pedido, pagamento e estoque já existem como esqueleto; o código de cada um entra nas fases 2 a 4.

## Rodando localmente

```bash
cp infra/.env.example infra/.env
docker compose -f infra/docker-compose.yml up -d --wait   # espera todos os healthchecks
./gradlew build
./gradlew :catalogo-service:bootstrap:bootRun
./gradlew :gateway:bootRun
```

O gateway também pode rodar em container, com healthcheck, apontando para os serviços no host:

```bash
./gradlew :gateway:assemble   # jar + OpenTelemetry Java agent (a imagem leva os dois)
docker compose -f infra/docker-compose.yml --profile apps up -d --build --wait gateway
curl http://localhost:8080/api/catalogo/produtos   # o gateway tira o /api/catalogo e repassa para :8081
```

Swagger UI do catálogo: http://localhost:8081/swagger-ui/index.html

| Serviço | Endereço | Acesso |
| --- | --- | --- |
| Gateway | http://localhost:8080 | — |
| RabbitMQ | http://localhost:15672 | guest / guest |
| Keycloak | http://localhost:8180 | admin / admin |
| Usuário cliente | realm `mini` | cliente@mini.dev / cliente |
| Usuário admin | realm `mini` | admin@mini.dev / admin |
| MySQL | localhost:3306 | um usuário por schema: `catalogo`/`catalogo`, `carrinho`/`carrinho`, … |
| Redis | localhost:6379 | — |

### Observabilidade (traces, logs e métricas)

```powershell
docker compose -f infra/docker-compose.yml --profile observabilidade up -d
```

| Ferramenta | Endereço | Para quê |
| --- | --- | --- |
| Grafana | http://localhost:3000 | um dashboard por serviço (pasta "Mini E-commerce"), Explore com Tempo e Loki |
| Prometheus | http://localhost:9090 | lê o `/actuator/prometheus` de cada serviço |
| Tempo | http://localhost:3200 | traces |
| Loki | http://localhost:3100 | logs com `trace_id` |
| OTel Collector | localhost:4317 (gRPC) / 4318 (HTTP) | recebe traces e logs do agent e repassa ao Tempo e ao Loki |

- O `bootRun` de cada serviço já sobe com o OpenTelemetry Java agent (`build/otel/opentelemetry-javaagent.jar`). Para rodar o jar direto, use `java -javaagent:<serviço>/bootstrap/build/otel/opentelemetry-javaagent.jar -jar ...`.
- O `X-Trace-Id` da resposta é o trace id: cole no Explore do Grafana (Tempo) para ver o caminho inteiro. O trace atravessa a outbox e o RabbitMQ: o clique em "pagar" chega até a baixa no estoque.
- Métricas de negócio: `mini_eventos_publicados_total{tipo}` (pedidos criados, pagamentos aprovados e recusados...) e `mini_reservas_expiradas_total`.
- Os dashboards saem de `infra/observabilidade/grafana/gerar-dashboards.mjs`. Mudou o gerador? Rode `node infra/observabilidade/grafana/gerar-dashboards.mjs` e reinicie o Grafana.

Para conferir que o usuário de um serviço não lê o schema de outro (ADR-0003), com a infra no ar:

```powershell
cd infra; .\verificar-isolamento.ps1
```

## Regras da arquitetura

- `domain` só depende do JDK e do `shared-kernel`.
- `application` depende do `domain`, nunca de `adapters`.
- Cada port de saída tem um adapter real e um em memória para testes.
- Eventos são publicados depois do commit, via transactional outbox.

Essas regras são verificadas por ArchUnit em `*/bootstrap/src/test`.

## Decisões

- [ADR-0001 Arquitetura hexagonal](docs/adr/0001-arquitetura-hexagonal.md)
- [ADR-0002 Microfrontends em polyrepo](docs/adr/0002-microfrontends-polyrepo.md)
- [ADR-0003 MySQL com um schema por serviço](docs/adr/0003-mysql-schema-por-servico.md)
- [ADR-0004 Fluxo de branches com dev e CI só na entrada da main](docs/adr/0004-fluxo-de-branches-e-ci.md)

## Roadmap

O trabalho está organizado em milestones (Fase 0 a Fase 6) e issues neste repositório e nos repositórios de frontend.
