package com.ruandiego.mini.pagamento.application.service;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.ServicoIndisponivelException;
import com.ruandiego.mini.pagamento.application.memoria.EventosDoGatewayProcessadosEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.GatewayPagamentoEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.MensagensProcessadasEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.PagamentoRepositoryEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.PedidosCanceladosEmMemoria;
import com.ruandiego.mini.pagamento.application.port.in.IniciarPagamentoUseCase.Comando;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDePedidoUseCase.PedidoCancelado;
import com.ruandiego.mini.pagamento.application.port.out.Outbox;
import com.ruandiego.mini.pagamento.domain.model.ClienteId;
import com.ruandiego.mini.pagamento.domain.model.MetodoPagamento;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
import com.ruandiego.mini.pagamento.domain.model.StatusPagamento;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reação do pagamento ao pedido cancelado, sobre os adapters em memória: sem Spring, sem banco, sem mocks. */
class PedidoEventosServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-20T10:00:00Z");
    private static final ClienteId ANA = new ClienteId("ana");
    private static final String CARTAO_APROVADO = "4111111111111112";
    private static final String CARTAO_RECUSADO = "4111111111111113";

    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final PagamentoRepositoryEmMemoria pagamentos = new PagamentoRepositoryEmMemoria();
    private final GatewayPagamentoEmMemoria gateway = new GatewayPagamentoEmMemoria();
    private final MensagensProcessadasEmMemoria processadas = new MensagensProcessadasEmMemoria();
    private final OutboxEmMemoria outbox = new OutboxEmMemoria();
    private final PedidosCanceladosEmMemoria cancelados = new PedidosCanceladosEmMemoria();
    private final PagamentoService pagamento = new PagamentoService(pagamentos, gateway,
            new EventosDoGatewayProcessadosEmMemoria(), cancelados, outbox, relogio);
    private final PedidoEventosService service = new PedidoEventosService(pagamentos, gateway, processadas, cancelados, outbox, relogio);

    private final PedidoId pedido = new PedidoId(UUID.randomUUID());

    private void pagarComCartao(String numero) {
        pagamento.iniciar(new Comando(ANA, pedido, Dinheiro.de("219.90"), MetodoPagamento.CARTAO, numero));
    }

    private PedidoCancelado cancelado() {
        return new PedidoCancelado(UUID.randomUUID(), pedido);
    }

    private StatusPagamento status() {
        return pagamentos.listarPorPedido(pedido).get(0).status();
    }

    private java.util.List<String> tipos() {
        return outbox.pendentes(100).stream().map(Outbox.Mensagem::tipo).toList();
    }

    @Test
    void pedidoCanceladoEstornaOPagamentoAprovadoNoDominioENoGateway() {
        gateway.aprovarCartoes(true);
        pagarComCartao(CARTAO_APROVADO);

        service.cancelado(cancelado());

        assertThat(status()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(gateway.estornos()).hasSize(1);
        assertThat(tipos()).containsExactly("pagamento.aprovado", "pagamento.estornado");
    }

    @Test
    void mensagemRepetidaNaoEstornaDeNovo() {
        gateway.aprovarCartoes(true);
        pagarComCartao(CARTAO_APROVADO);
        PedidoCancelado evento = cancelado();

        service.cancelado(evento);
        service.cancelado(evento);

        assertThat(gateway.estornos()).hasSize(1);
        assertThat(tipos()).containsExactly("pagamento.aprovado", "pagamento.estornado");
    }

    @Test
    void outraMensagemDoMesmoPedidoTambemNaoEstornaDeNovo() {
        gateway.aprovarCartoes(true);
        pagarComCartao(CARTAO_APROVADO);

        service.cancelado(cancelado());
        service.cancelado(cancelado());

        assertThat(gateway.estornos()).hasSize(1);
    }

    @Test
    void pagamentoRecusadoNaoTemOQueEstornar() {
        gateway.aprovarCartoes(false);
        pagarComCartao(CARTAO_RECUSADO);

        service.cancelado(cancelado());

        assertThat(status()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(gateway.estornos()).isEmpty();
        assertThat(tipos()).containsExactly("pagamento.recusado");
    }

    @Test
    void pedidoCanceladoFicaRegistradoParaNaoAceitarPagamentoDepois() {
        service.cancelado(cancelado());

        assertThat(cancelados.estaCancelado(pedido)).isTrue();
    }

    @Test
    void pedidoSemPagamentoNaoFazNada() {
        service.cancelado(cancelado());

        assertThat(gateway.estornos()).isEmpty();
        assertThat(tipos()).isEmpty();
    }

    @Test
    void gatewayForaDoArLancaParaOBrokerTentarDeNovo() {
        gateway.aprovarCartoes(true);
        pagarComCartao(CARTAO_APROVADO);
        gateway.ficarForaDoAr(true);

        assertThatThrownBy(() -> service.cancelado(cancelado())).isInstanceOf(ServicoIndisponivelException.class);
    }
}
