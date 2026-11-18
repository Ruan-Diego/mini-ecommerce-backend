# ADR-0003: MySQL com um schema por serviço

- Status: aceito
- Data: 2026-11-18

## Contexto

Cada serviço precisa ser dono dos próprios dados, mas subir cinco instâncias de banco pesa no ambiente local.

## Decisão

Uma instância MySQL 8 no Docker Compose, com um schema e um usuário por serviço. Cada usuário só tem permissão no próprio schema. Migrações com Flyway, por serviço.

## Consequências

- Nenhum serviço consegue fazer join ou ler tabela de outro.
- Separar em instâncias diferentes depois é só configuração.
- Consistência entre serviços é eventual, via eventos e outbox.
