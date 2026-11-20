# ADR-0005: Contratos Pact versionados no repositório do backend

- Status: aceito
- Data: 2026-11-20
- Vale para: o backend e os 5 microfrontends (itens 52 a 55 e 59 do roadmap)

## Contexto

Cada microfrontend chama a API de um ou mais serviços pelo gateway. Queremos que uma mudança incompatível nessa API quebre o CI do backend antes de chegar à `main`, e não só no navegador. O teste de contrato com Pact resolve isso: o consumidor (MFE) descreve as interações que usa e gera um arquivo de contrato; o provider (serviço) prova que atende a cada uma.

Falta decidir como o contrato gerado num repositório chega ao outro. O caminho usual é um Pact Broker (por exemplo, o PactFlow), mas isso exige uma conta externa e credenciais no CI de cada repositório. Um broker local (no Compose) não é alcançável pelo GitHub Actions.

## Decisão

1. **Consumidor**: cada MFE tem testes Pact (pact-js, `PactV3`) que rodam no `vitest` e geram `pacts/<mfe>-<servico>.json`. O script `pnpm pact:copiar` copia esses arquivos para `mini-ecommerce-backend/contratos/pacts/` (os repositórios ficam lado a lado no workspace).
2. **Os contratos são versionados no backend**, em `contratos/pacts/`. Mudar um contrato é um PR no backend, revisado como qualquer mudança de API.
3. **Provider**: o `bootstrap` de cada serviço tem um teste de verificação (pact-jvm `junit5spring`) que sobe o serviço inteiro (Testcontainers) e verifica os contratos daquele provider em `contratos/pacts/`. O teste roda no `./gradlew build`, ou seja, no CI do PR para a `main`. A pasta é entrada declarada da tarefa de teste, então mudar um contrato faz o teste rodar de novo (o cache do Gradle não o pula).
4. **Estados do provider** (`given`) são preparados pelo próprio teste de verificação, gravando direto pelos ports de saída (como os outros testes de integração).

## Consequências

- Sem conta nem segredo externo; o contrato fica ao lado do código que o cumpre.
- Mudança incompatível no backend quebra o `./gradlew build` local e o CI. Mudança no consumidor só chega ao backend quando o `pact:copiar` é rodado e o PR do backend é mesclado: se alguém esquecer de copiar, o backend verifica um contrato antigo. Esse risco é aceito, porque os dois PRs andam juntos no mesmo item do roadmap.
- Sem broker, não há `can-i-deploy` nem matriz de versões. Se o projeto crescer, basta trocar a pasta por um Pact Broker; os testes dos dois lados continuam os mesmos.
