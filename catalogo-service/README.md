# catalogo-service

Dono do schema `catalogo`. Camadas (dependências sempre apontam para dentro):

| Módulo | Contém | Depende de |
| --- | --- | --- |
| `domain` | Produto, Categoria, value objects, eventos | shared-kernel (só JDK) |
| `application` | ports de entrada (use cases), ports de saída, serviços | domain |
| `adapters` | REST (entrada), JPA + Flyway (saída), publicador de eventos | application |
| `bootstrap` | `main`, configuração Spring, testes de arquitetura | todos |

O trabalho desta fase está nas issues da milestone **Fase 1 — Catálogo**.
