package com.ruandiego.mini.pagamento.application.contrato;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.port.out.PagamentoRepository;
import com.ruandiego.mini.pagamento.domain.model.ClienteId;
import com.ruandiego.mini.pagamento.domain.model.MetodoPagamento;
import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PagamentoId;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
import com.ruandiego.mini.pagamento.domain.model.StatusPagamento;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do port PagamentoRepository. Quem estende devolve um repositório vazio a cada teste. */
public abstract class PagamentoRepositoryContrato {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

    protected abstract PagamentoRepository pagamentos();

    private static Pagamento cartao(PedidoId pedido, Instant criadoEm, String transacao) {
        Pagamento pagamento = Pagamento.cartao(pedido, new ClienteId("ana"), Dinheiro.de("219.90"), criadoEm);
        pagamento.vincularTransacao(transacao);
        return pagamento;
    }

    private static Pagamento pix(PedidoId pedido, Instant criadoEm, String transacao) {
        Pagamento pagamento = Pagamento.pix(pedido, new ClienteId("ana"), Dinheiro.de("50.00"), criadoEm);
        pagamento.vincularTransacao(transacao);
        pagamento.definirCodigoPix("00020126FAKE" + transacao);
        return pagamento;
    }

    private static PedidoId pedido() {
        return new PedidoId(UUID.randomUUID());
    }

    @Test
    void salvaEDevolveOPagamentoCompletoDoCartao() {
        Pagamento original = cartao(pedido(), T0, "tx-1");
        original.recusar("cartão recusado", T0.plusSeconds(1));
        pagamentos().salvar(original);

        Pagamento lido = pagamentos().buscarPorId(original.id()).orElseThrow();

        assertThat(lido.id()).isEqualTo(original.id());
        assertThat(lido.pedidoId()).isEqualTo(original.pedidoId());
        assertThat(lido.clienteId()).isEqualTo(new ClienteId("ana"));
        assertThat(lido.metodo()).isEqualTo(MetodoPagamento.CARTAO);
        assertThat(lido.valor()).isEqualTo(Dinheiro.de("219.90"));
        assertThat(lido.status()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(lido.motivoRecusa()).isEqualTo("cartão recusado");
        assertThat(lido.transacaoId()).isEqualTo("tx-1");
        assertThat(lido.pixCodigo()).isNull();
        assertThat(lido.pixExpiraEm()).isNull();
        assertThat(lido.criadoEm()).isEqualTo(T0);
        assertThat(lido.atualizadoEm()).isEqualTo(T0.plusSeconds(1));
        assertThat(lido.eventosPendentes()).isEmpty();
    }

    @Test
    void guardaOCodigoEAExpiracaoDoPix() {
        Pagamento original = pix(pedido(), T0, "tx-pix");
        pagamentos().salvar(original);

        Pagamento lido = pagamentos().buscarPorId(original.id()).orElseThrow();

        assertThat(lido.metodo()).isEqualTo(MetodoPagamento.PIX);
        assertThat(lido.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(lido.pixCodigo()).isEqualTo("00020126FAKEtx-pix");
        assertThat(lido.pixExpiraEm()).isEqualTo(T0.plusSeconds(1800));
    }

    @Test
    void salvarDeNovoAtualizaOStatus() {
        Pagamento pagamento = cartao(pedido(), T0, "tx-1");
        pagamentos().salvar(pagamento);

        pagamento.aprovar(T0.plusSeconds(5));
        pagamentos().salvar(pagamento);

        Pagamento lido = pagamentos().buscarPorId(pagamento.id()).orElseThrow();
        assertThat(lido.status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(lido.atualizadoEm()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void buscaPorTransacaoEIdDesconhecidoViraVazio() {
        Pagamento pagamento = cartao(pedido(), T0, "tx-abc");
        pagamentos().salvar(pagamento);

        assertThat(pagamentos().buscarPorTransacao("tx-abc")).map(Pagamento::id).contains(pagamento.id());
        assertThat(pagamentos().buscarPorTransacao("tx-nao-existe")).isEmpty();
        assertThat(pagamentos().buscarPorId(PagamentoId.novo())).isEmpty();
    }

    @Test
    void listaOsPagamentosDoPedidoDoMaisRecenteParaOMaisAntigo() {
        PedidoId pedido = pedido();
        Pagamento antigo = cartao(pedido, T0, "tx-1");
        Pagamento recente = cartao(pedido, T0.plusSeconds(60), "tx-2");
        Pagamento deOutroPedido = cartao(pedido(), T0.plusSeconds(120), "tx-3");
        pagamentos().salvar(antigo);
        pagamentos().salvar(recente);
        pagamentos().salvar(deOutroPedido);

        assertThat(pagamentos().listarPorPedido(pedido)).extracting(Pagamento::id)
                .containsExactly(recente.id(), antigo.id());
        assertThat(pagamentos().listarPorPedido(pedido())).isEmpty();
    }

    @Test
    void listaSoOsPixPendentesJaVencidosDoMaisAntigoAoMaisNovo() {
        Pagamento vencidoAntigo = pix(pedido(), T0, "tx-1");
        Pagamento vencidoNovo = pix(pedido(), T0.plusSeconds(60), "tx-2");
        Pagamento aindaValido = pix(pedido(), T0.plusSeconds(3600), "tx-3");
        Pagamento pagoEVencido = pix(pedido(), T0, "tx-4");
        pagoEVencido.aprovar(T0.plusSeconds(10));
        Pagamento cartaoPendente = cartao(pedido(), T0, "tx-5");
        for (Pagamento p : new Pagamento[]{vencidoNovo, aindaValido, vencidoAntigo, pagoEVencido, cartaoPendente}) {
            pagamentos().salvar(p);
        }
        Instant agora = T0.plusSeconds(1800 + 3600);

        // vencidoAntigo vence em T0+1800 e vencidoNovo em T0+1860: no instante T0+1800 só o primeiro venceu.
        assertThat(pagamentos().listarPixVencidos(T0.plusSeconds(1800), 10)).extracting(Pagamento::id)
                .containsExactly(vencidoAntigo.id());
        assertThat(pagamentos().listarPixVencidos(T0.plusSeconds(1860), 10)).extracting(Pagamento::id)
                .containsExactly(vencidoAntigo.id(), vencidoNovo.id());
        assertThat(pagamentos().listarPixVencidos(agora, 10)).extracting(Pagamento::id)
                .containsExactly(vencidoAntigo.id(), vencidoNovo.id(), aindaValido.id());
        assertThat(pagamentos().listarPixVencidos(agora, 1)).hasSize(1);
        assertThat(pagamentos().listarPixVencidos(T0, 10)).isEmpty();
    }
}
