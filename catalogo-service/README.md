# catalogo-service

Dono do schema `catalogo`. Camadas (dependências sempre apontam para dentro):

| Módulo | Contém | Depende de |
| --- | --- | --- |
| `domain` | Produto, Categoria, value objects, eventos | shared-kernel (só JDK) |
| `application` | ports de entrada (use cases), ports de saída, serviços | domain |
| `adapters` | REST (entrada), JPA + Flyway (saída), publicador de eventos | application |
| `bootstrap` | `main`, configuração Spring, testes de arquitetura | todos |

O trabalho desta fase está nas issues da milestone **Fase 1 — Catálogo**.

## Persistência e testes

- Schema criado pelo Flyway em `adapters/src/main/resources/db/migration` (`V1__catalogo.sql`). O Hibernate só valida (`ddl-auto: validate`).
- Entidades JPA (`adapters/.../out/persistencia/*Jpa`) são separadas das de domínio; o `CatalogoMapper` converte.
- Contratos dos ports de saída em `application/src/testFixtures/.../contrato`: o adapter em memória e o JPA (com MySQL 8 via Testcontainers) rodam os mesmos cenários.
- A transação de cada use case é amarrada no `CatalogoConfig` (bootstrap), porque os services não conhecem Spring. O `CatalogoIntegracaoTest` sobe o serviço inteiro e prova o rollback.
- Os testes de `adapters` e `bootstrap` precisam do Docker rodando.
