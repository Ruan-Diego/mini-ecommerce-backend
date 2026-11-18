# ADR-0002: Microfrontends com Module Federation em polyrepo

- Status: aceito
- Data: 2026-11-18

## Contexto

Queremos estudar microfrontends com isolamento real: build, versão e deploy independentes por contexto de negócio.

## Decisão

Um repositório para o shell (host), um por microfrontend (catálogo, carrinho, checkout, conta, admin) e um para os pacotes compartilhados (design-system e event-contracts), publicados no GitHub Packages. Os MFEs são carregados em runtime com Module Federation e se comunicam só por eventos tipados.

## Consequências

- Nenhum MFE importa código de outro; tudo que é comum passa por pacote versionado.
- Mudança incompatível num evento exige novo nome de evento.
- Mais repositórios para manter e um fluxo de release para os pacotes compartilhados.
