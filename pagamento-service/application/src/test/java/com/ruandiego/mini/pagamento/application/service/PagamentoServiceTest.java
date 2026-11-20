package com.ruandiego.mini.pagamento.application.service;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.ConflitoException;
import com.ruandiego.mini.pagamento.application.NaoEncontradoException;
import com.ruandiego.mini.pagamento.application.ServicoIndisponivelException;
import com.ruandiego.mini.pagamento.application.memoria.EventosDoGatewayProcessadosEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.GatewayPagamentoEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.PagamentoRepositoryEmMemoria;
import com.ruandiego.mini.pagamento.application.port.in.IniciarPagamentoUseCase.Comando;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase.Evento;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase.Resultado;
import com.ruandiego.mini.pagamento.domain.RegraDeNegocioException;
import com.ruandiego.mini.pagamento.domain.TransicaoDeStatusInvalida;
import com.ruandiego.mini.pagamento.domain.model.ClienteId;
import com.ruandiego.mini.pagamento.domain.model.MetodoPagamento;
import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PagamentoId;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
import com.ruandiego.mini.pagamento.domain.model.StatusPagamento;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Use cases do pagamento sobre os adapters em memória: sem Spring, sem banco, sem mocks. */
class PagamentoServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");
    private static final ClienteId ANA = new ClienteId("ana");
    private static final ClienteId BETO = new ClienteId("beto");
    private static final String CARTAO_QUALQUER = "4111111111111112";

    /** Relógio que o teste pode adiantar, para a expiração do Pix. */
    private static class RelogioDeTeste extends Clock {
        private Instant agora = T0;

        void avancarSegundos(long segundos) {
            agora = agora.plusSeconds(segundos);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final PagamentoRepositoryEmMemoria pagamentos = new PagamentoRepositoryEmMemoria();
    private final GatewayPagamentoEmMemoria gateway = new GatewayPagamentoEmMemoria();
    private final EventosDoGatewayProcessadosEmMemoria processados = new EventosDoGatewayProcessadosEmMemoria();
    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final PagamentoService service = new PagamentoService(pagamentos, gateway, processados, outbox, relogio);

    private final PedidoId pedido = new PedidoId(UUID.randomUUID());

    private Comando cartao(String numero) {
        return new Comando(ANA, pedido, Dinheiro.de("219.90"), MetodoPagamento.CARTAO, numero);
    }

    private Comando pix() {
        return new Comando(ANA, pedido, Dinheiro.de("219.90"), MetodoPagamento.PIX, null);
    }

    // ---- cartão ----

    @Test
    void cartaoAprovadoGravaPagamentoAprovadoNaOutbox() {
        Pagamento pagamento = service.iniciar(cartao(CARTAO_QUALQUER));

        assertThat(pagamento.status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamento.transacaoId()).isEqualTo("tx-1");
        assertThat(pagamentos.buscarPorId(pagamento.id()).orElseThrow().status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(outbox.tiposGravados()).containsExactly("pagamento.aprovado");
        assertThat(pagamento.eventosPendentes()).isEmpty();
    }

    @Test
    void cartaoRecusadoGuardaOMotivoEGravaPagamentoRecusado() {
        gateway.aprovarCartoes(false);

        Pagamento pagamento = service.iniciar(cartao(CARTAO_QUALQUER));

        assertThat(pagamento.status()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(pagamento.motivoRecusa()).isEqualTo("cartão recusado");
        assertThat(outbox.tiposGravados()).containsExactly("pagamento.recusado");
    }

    @Test
    void numeroDeCartaoInvalidoNaoChegaAoGatewayENaoApareceNaMensagem() {
        assertThatThrownBy(() -> service.iniciar(cartao("1234")))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("1234"));
        assertThatThrownBy(() -> service.iniciar(cartao(null))).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.iniciar(cartao("4111-1111-1111-1112"))).isInstanceOf(RegraDeNegocioException.class);

        assertThat(gateway.cartoesCobrados()).isEmpty();
        assertThat(pagamentos.listarPorPedido(pedido)).isEmpty();
    }

    @Test
    void gatewayForaDoArNaoGravaNada() {
        gateway.ficarForaDoAr(true);

        assertThatThrownBy(() -> service.iniciar(cartao(CARTAO_QUALQUER))).isInstanceOf(ServicoIndisponivelException.class);
        assertThatThrownBy(() -> service.iniciar(pix())).isInstanceOf(ServicoIndisponivelException.class);

        assertThat(pagamentos.listarPorPedido(pedido)).isEmpty();
        assertThat(outbox.tiposGravados()).isEmpty();
    }

    // ---- Pix ----

    @Test
    void pixNasceCodigoPendenteEValidoPorTrintaMinutosSemEvento() {
        Pagamento pagamento = service.iniciar(pix());

        assertThat(pagamento.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.pixCodigo()).isEqualTo("PIX-tx-1");
        assertThat(pagamento.pixExpiraEm()).isEqualTo(T0.plusSeconds(1800));
        assertThat(outbox.tiposGravados()).isEmpty();
    }

    @Test
    void naoDeixaPagarDuasVezesOMesmoPedido() {
        service.iniciar(pix());

        assertThatThrownBy(() -> service.iniciar(cartao(CARTAO_QUALQUER))).isInstanceOf(ConflitoException.class);
    }

    @Test
    void depoisDeAprovadoOPedidoNaoAceitaOutroPagamento() {
        service.iniciar(cartao(CARTAO_QUALQUER));

        assertThatThrownBy(() -> service.iniciar(pix())).isInstanceOf(ConflitoException.class);
    }

    @Test
    void depoisDeRecusadoOClienteTentaDeNovo() {
        gateway.aprovarCartoes(false);
        service.iniciar(cartao(CARTAO_QUALQUER));
        gateway.aprovarCartoes(true);

        Pagamento segundo = service.iniciar(cartao(CARTAO_QUALQUER));

        assertThat(segundo.status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamentos.listarPorPedido(pedido)).hasSize(2);
    }

    @Test
    void pixVencidoNaoImpedeUmNovoPagamento() {
        service.iniciar(pix());
        relogio.avancarSegundos(1800);

        assertThat(service.iniciar(cartao(CARTAO_QUALQUER)).status()).isEqualTo(StatusPagamento.APROVADO);
    }

    // ---- webhook (critério de pronto: repetido não gera evento duplicado) ----

    @Test
    void webhookAprovaOPixEGravaUmEvento() {
        Pagamento pix = service.iniciar(pix());

        Resultado resultado = service.processar(pix.transacaoId(), Evento.APROVADO);

        assertThat(resultado).isEqualTo(Resultado.PROCESSADO);
        assertThat(pagamentos.buscarPorId(pix.id()).orElseThrow().status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(outbox.tiposGravados()).containsExactly("pagamento.aprovado");
    }

    @Test
    void webhookRepetidoNaoGeraEventoDuplicado() {
        Pagamento pix = service.iniciar(pix());

        assertThat(service.processar(pix.transacaoId(), Evento.APROVADO)).isEqualTo(Resultado.PROCESSADO);
        assertThat(service.processar(pix.transacaoId(), Evento.APROVADO)).isEqualTo(Resultado.JA_PROCESSADO);
        assertThat(service.processar(pix.transacaoId(), Evento.APROVADO)).isEqualTo(Resultado.JA_PROCESSADO);

        assertThat(outbox.tiposGravados()).containsExactly("pagamento.aprovado");
    }

    @Test
    void entregaSimultaneaQueJaFoiRegistradaPorOutraNaoGeraEvento() {
        Pagamento pix = service.iniciar(pix());
        // Outra entrega do mesmo webhook registrou primeiro (e ainda não commitou a mudança de status).
        processados.registrar(pix.transacaoId(), "APROVADO");

        assertThat(service.processar(pix.transacaoId(), Evento.APROVADO)).isEqualTo(Resultado.JA_PROCESSADO);

        assertThat(outbox.tiposGravados()).isEmpty();
    }

    @Test
    void webhookDeAprovadoParaCartaoJaAprovadoNaoFazNada() {
        Pagamento cartao = service.iniciar(cartao(CARTAO_QUALQUER));

        assertThat(service.processar(cartao.transacaoId(), Evento.APROVADO)).isEqualTo(Resultado.JA_PROCESSADO);

        assertThat(outbox.tiposGravados()).containsExactly("pagamento.aprovado");
    }

    @Test
    void webhookRecusaOPixEEstornaOAprovado() {
        Pagamento pixRecusado = service.iniciar(new Comando(ANA, new PedidoId(UUID.randomUUID()), Dinheiro.de("10.00"),
                MetodoPagamento.PIX, null));
        Pagamento aprovado = service.iniciar(cartao(CARTAO_QUALQUER));

        service.processar(pixRecusado.transacaoId(), Evento.RECUSADO);
        service.processar(aprovado.transacaoId(), Evento.ESTORNADO);

        assertThat(pagamentos.buscarPorId(pixRecusado.id()).orElseThrow().status()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(pagamentos.buscarPorId(aprovado.id()).orElseThrow().status()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(outbox.tiposGravados()).containsExactly("pagamento.aprovado", "pagamento.recusado", "pagamento.estornado");
    }

    @Test
    void webhookDeTransacaoDesconhecidaEhNaoEncontrado() {
        assertThatThrownBy(() -> service.processar("tx-que-nao-existe", Evento.APROVADO))
                .isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void webhookQueContrariaOFluxoEhRecusado() {
        Pagamento aprovado = service.iniciar(cartao(CARTAO_QUALQUER));

        assertThatThrownBy(() -> service.processar(aprovado.transacaoId(), Evento.RECUSADO))
                .isInstanceOf(TransicaoDeStatusInvalida.class);
        assertThat(pagamentos.buscarPorId(aprovado.id()).orElseThrow().status()).isEqualTo(StatusPagamento.APROVADO);
    }

    // ---- consultas ----

    @Test
    void soODonoEnxergaOPagamento() {
        Pagamento pagamento = service.iniciar(cartao(CARTAO_QUALQUER));

        assertThat(service.obter(ANA, pagamento.id()).id()).isEqualTo(pagamento.id());
        assertThatThrownBy(() -> service.obter(BETO, pagamento.id())).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.obter(ANA, PagamentoId.novo())).isInstanceOf(NaoEncontradoException.class);
        assertThat(service.doPedido(ANA, pedido)).hasSize(1);
        assertThat(service.doPedido(BETO, pedido)).isEmpty();
    }

    // ---- estorno ----

    @Test
    void estornaOPagamentoAprovadoNoGatewayEGravaOEvento() {
        Pagamento pagamento = service.iniciar(cartao(CARTAO_QUALQUER));

        Pagamento estornado = service.estornar(pagamento.id());

        assertThat(estornado.status()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(gateway.estornos()).containsExactly(pagamento.transacaoId());
        assertThat(outbox.tiposGravados()).containsExactly("pagamento.aprovado", "pagamento.estornado");
    }

    @Test
    void naoEstornaOQueNaoFoiAprovadoENemChamaOGateway() {
        gateway.aprovarCartoes(false);
        Pagamento recusado = service.iniciar(cartao(CARTAO_QUALQUER));

        assertThatThrownBy(() -> service.estornar(recusado.id())).isInstanceOf(TransicaoDeStatusInvalida.class);
        assertThatThrownBy(() -> service.estornar(PagamentoId.novo())).isInstanceOf(NaoEncontradoException.class);

        assertThat(gateway.estornos()).isEmpty();
    }

    @Test
    void gatewayForaDoArNaoEstornaENaoGravaEvento() {
        Pagamento pagamento = service.iniciar(cartao(CARTAO_QUALQUER));
        gateway.ficarForaDoAr(true);

        assertThatThrownBy(() -> service.estornar(pagamento.id())).isInstanceOf(ServicoIndisponivelException.class);

        assertThat(pagamentos.buscarPorId(pagamento.id()).orElseThrow().status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(outbox.tiposGravados()).containsExactly("pagamento.aprovado");
    }

    // ---- expiração ----

    @Test
    void expiraSoOsPixVencidosEGravaPagamentoRecusadoComMotivo() {
        Pagamento pix = service.iniciar(pix());
        relogio.avancarSegundos(1799);
        assertThat(service.expirarVencidos()).isZero();

        relogio.avancarSegundos(1);
        assertThat(service.expirarVencidos()).isEqualTo(1);

        Pagamento lido = pagamentos.buscarPorId(pix.id()).orElseThrow();
        assertThat(lido.status()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(lido.motivoRecusa()).isEqualTo("Pix expirado");
        assertThat(outbox.tiposGravados()).containsExactly("pagamento.recusado");
        assertThat(service.expirarVencidos()).isZero();
    }
}
