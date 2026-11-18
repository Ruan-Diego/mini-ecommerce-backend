# ADR-0001: Arquitetura hexagonal nos serviços

- Status: aceito
- Data: 2026-11-18

## Contexto

O projeto existe para estudar arquitetura hexagonal. Queremos trocar banco, mensageria e integrações externas (pagamento, frete) sem mexer nas regras de negócio.

## Decisão

Cada serviço tem quatro módulos Gradle: `domain` (só JDK), `application` (ports e use cases), `adapters` (REST, JPA, RabbitMQ, clientes HTTP) e `bootstrap` (Spring Boot e wiring). As dependências apontam sempre para dentro, e um teste ArchUnit por serviço quebra o build se essa regra for violada.

## Consequências

- O domínio é testado sem Spring, sem banco e sem mocks.
- Entidades de domínio e entidades JPA são classes diferentes, com mapper no adapter.
- Há mais classes e mais mapeamento do que num CRUD em camadas.
- Cada port de saída deve ter pelo menos dois adapters: o real e um em memória para testes.
