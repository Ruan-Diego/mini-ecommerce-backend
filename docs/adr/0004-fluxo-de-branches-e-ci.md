# ADR-0004: Fluxo de branches com dev e CI só na entrada da main

- Status: aceito
- Data: 2026-11-18
- Vale para: os 8 repositórios do projeto

## Contexto

O projeto tem 63 issues em 8 repositórios. A cota de minutos do GitHub Actions é limitada: se o CI rodar a cada push de feature, ela acaba muito antes do fim do roadmap. Ao mesmo tempo, queremos que a `main` só receba trabalho validado e que cada fase do `ROADMAP.md` seja um marco claro no histórico.

## Decisão

1. **Branches**
   - `main`: só recebe código no fim de cada fase completa do roadmap. Fase 0 a Fase 6 = 7 merges na `main` por repositório (no máximo; um repositório sem issue na fase não recebe merge).
   - `dev`: branch de integração. Todo trabalho termina nela.
   - `feat/<numero>-<slug>`: uma por issue, criada a partir da `dev` e mesclada de volta na `dev` por PR. Para ajustes sem issue: `fix/<slug>` ou `chore/<slug>`.
2. **Fluxo de uma issue**: `dev` → `feat/<n>-<slug>` → testes locais (`.\gradlew.bat build` ou `pnpm typecheck; pnpm test; pnpm build`) → PR para `dev` → merge.
3. **Fluxo de uma fase**: quando o critério de pronto da fase passa, abre-se um PR `dev` → `main` com título `Fase N — <nome>` e um `Closes #n` para cada issue da fase. O merge é feito com merge commit (sem squash), para preservar o histórico das features.
4. **CI**: os workflows rodam apenas em `pull_request` para `main` e por `workflow_dispatch` (manual). Não há gatilho em `push`, nem em PRs para `dev`. Workflows futuros (por exemplo, publicação de pacotes do `ui`) seguem a mesma regra: só disparam a partir da `main`.

## Consequências

- O CI roda uma vez por fase em cada repositório (mais as reexecuções se o PR da fase precisar de correção). A cota de Actions dura o projeto inteiro.
- Os testes nas features e na `dev` são responsabilidade local: nenhuma feature é mesclada na `dev` sem os testes do repositório passando na máquina.
- Um erro que só aparece no runner do Actions (Testcontainers, cache, sistema operacional) é descoberto só no fim da fase. Se precisar, dá para disparar o CI manualmente na `dev` pelo `workflow_dispatch`.
- O cache do Actions criado num PR só vale para aquele PR: as reexecuções do PR de uma fase aproveitam o cache, mas o PR da fase seguinte começa sem cache. O backend usa `cache-read-only: false` no `setup-gradle` para conseguir gravar cache fora da branch padrão.
- Issues só fecham automaticamente quando a fase chega na `main` (o GitHub só aplica `Closes #n` na branch padrão). Até lá, o progresso fica no `ROADMAP.md` e nos PRs para `dev`.
