// Gera um dashboard do Grafana por serviço em ./dashboards, todos com o mesmo esqueleto (HTTP, JVM, logs)
// e com os painéis de negócio de cada serviço. Rode depois de mudar este arquivo:
//   node infra/observabilidade/grafana/gerar-dashboards.mjs
// Os JSON gerados são versionados: o Compose só monta a pasta, não roda este script.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const PROM = { type: 'prometheus', uid: 'prometheus' };
const LOKI = { type: 'loki', uid: 'loki' };

/** Contador de eventos publicados pelo relay da outbox, por tipo (é a métrica de negócio comum). */
const eventos = (servico, tipo, janela = '1h') =>
  `sum(increase(mini_eventos_publicados_total{servico="${servico}", tipo="${tipo}"}[${janela}])) or vector(0)`;

const SERVICOS = [
  { id: 'gateway', nome: 'Gateway', negocio: [] },
  {
    id: 'catalogo-service',
    nome: 'Catálogo',
    negocio: [{ titulo: 'Preços alterados (1h)', expr: eventos('catalogo-service', 'catalogo.preco-alterado') }],
  },
  { id: 'carrinho-service', nome: 'Carrinho', negocio: [] },
  {
    id: 'pedido-service',
    nome: 'Pedido',
    negocio: [
      { titulo: 'Pedidos criados (1h)', expr: eventos('pedido-service', 'pedido.criado') },
      { titulo: 'Pedidos pagos (1h)', expr: eventos('pedido-service', 'pedido.pago') },
      { titulo: 'Pedidos cancelados (1h)', expr: eventos('pedido-service', 'pedido.cancelado') },
      {
        titulo: 'Reservas expiradas (1h)',
        expr: 'sum(increase(mini_reservas_expiradas_total{servico="pedido-service"}[1h])) or vector(0)',
      },
    ],
  },
  {
    id: 'pagamento-service',
    nome: 'Pagamento',
    negocio: [
      { titulo: 'Pagamentos aprovados (1h)', expr: eventos('pagamento-service', 'pagamento.aprovado') },
      { titulo: 'Pagamentos recusados (1h)', expr: eventos('pagamento-service', 'pagamento.recusado') },
      { titulo: 'Estornos (1h)', expr: eventos('pagamento-service', 'pagamento.estornado') },
    ],
  },
  {
    id: 'estoque-service',
    nome: 'Estoque',
    negocio: [
      { titulo: 'Reservas (1h)', expr: eventos('estoque-service', 'estoque.reservado') },
      { titulo: 'Sem estoque (1h)', expr: eventos('estoque-service', 'estoque.indisponivel') },
      { titulo: 'Baixas (1h)', expr: eventos('estoque-service', 'estoque.baixado') },
      { titulo: 'Liberações (1h)', expr: eventos('estoque-service', 'estoque.liberado') },
    ],
  },
];

let proximoId = 1;

function serie(titulo, expr, legenda, grid, unidade = 'short') {
  return {
    id: proximoId++,
    type: 'timeseries',
    title: titulo,
    datasource: PROM,
    gridPos: grid,
    fieldConfig: { defaults: { unit: unidade }, overrides: [] },
    options: { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'multi' } },
    targets: [{ refId: 'A', datasource: PROM, expr, legendFormat: legenda }],
  };
}

function numero(titulo, expr, grid) {
  return {
    id: proximoId++,
    type: 'stat',
    title: titulo,
    datasource: PROM,
    gridPos: grid,
    fieldConfig: { defaults: { unit: 'short', decimals: 0 }, overrides: [] },
    options: { reduceOptions: { calcs: ['lastNotNull'] }, colorMode: 'value', graphMode: 'area' },
    targets: [{ refId: 'A', datasource: PROM, expr, instant: true }],
  };
}

function dashboard(servico) {
  proximoId = 1;
  const s = servico.id;
  const painéis = [];
  let y = 0;

  if (servico.negocio.length > 0) {
    const largura = Math.floor(24 / servico.negocio.length);
    servico.negocio.forEach((p, i) => painéis.push(numero(p.titulo, p.expr, { x: i * largura, y, w: largura, h: 5 })));
    y += 5;
  }

  painéis.push(
    serie('Requisições por segundo (por status)',
      `sum by (status) (rate(http_server_requests_seconds_count{servico="${s}"}[1m]))`, '{{status}}',
      { x: 0, y, w: 12, h: 8 }, 'reqps'),
    serie('Latência p95 por rota',
      `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{servico="${s}"}[5m])))`,
      '{{uri}}', { x: 12, y, w: 12, h: 8 }, 's'),
  );
  y += 8;
  painéis.push(
    serie('Erros 5xx por segundo',
      `sum by (uri) (rate(http_server_requests_seconds_count{servico="${s}", status=~"5.."}[1m]))`, '{{uri}}',
      { x: 0, y, w: 12, h: 8 }, 'reqps'),
    serie('Memória heap da JVM', `sum(jvm_memory_used_bytes{servico="${s}", area="heap"})`, 'heap usada',
      { x: 12, y, w: 12, h: 8 }, 'bytes'),
  );
  y += 8;
  painéis.push({
    id: proximoId++,
    type: 'logs',
    title: 'Logs (clique no trace_id para abrir o trace no Tempo)',
    datasource: LOKI,
    gridPos: { x: 0, y, w: 24, h: 12 },
    options: { showTime: true, wrapLogMessage: true, sortOrder: 'Descending', enableLogDetails: true },
    targets: [{ refId: 'A', datasource: LOKI, expr: `{service_name="${s}"}` }],
  });

  return {
    uid: `mini-${s}`,
    title: `Mini E-commerce · ${servico.nome}`,
    tags: ['mini-ecommerce'],
    timezone: 'browser',
    schemaVersion: 39,
    version: 1,
    refresh: '10s',
    time: { from: 'now-1h', to: 'now' },
    panels: painéis,
  };
}

const pasta = join(dirname(fileURLToPath(import.meta.url)), 'dashboards');
mkdirSync(pasta, { recursive: true });
for (const servico of SERVICOS) {
  writeFileSync(join(pasta, `${servico.id}.json`), JSON.stringify(dashboard(servico), null, 2) + '\n');
}
console.log(`${SERVICOS.length} dashboards em ${pasta}`);
