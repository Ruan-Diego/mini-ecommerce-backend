package com.ruandiego.mini.pedido.application.contrato;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Frete;
import com.ruandiego.mini.pedido.domain.model.ItemPedido;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do port PedidoRepository. Quem estende devolve um repositório vazio a cada teste. */
public abstract class PedidoRepositoryContrato {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

    protected abstract PedidoRepository pedidos();

    private static Pedido pedido(String cliente, Instant criadoEm) {
        return Pedido.criar(new ClienteId(cliente),
                List.of(new ItemPedido(new ProdutoId(UUID.randomUUID()), "Peça A", Dinheiro.de("100.00"), 2),
                        new ItemPedido(new ProdutoId(UUID.randomUUID()), "Peça B", Dinheiro.de("50.50"), 1)),
                new EnderecoEntrega("Ana Souza", "01310100", "Av. Paulista", "1000", "ap 12", "Bela Vista", "São Paulo", "SP"),
                new Frete("padrao", "Padrão", Dinheiro.de("19.90"), 7), criadoEm);
    }

    @Test
    void salvaEDevolveOPedidoCompleto() {
        Pedido original = pedido("cliente-1", T0);
        pedidos().salvar(original);

        Pedido lido = pedidos().buscarPorId(original.id()).orElseThrow();

        assertThat(lido.id()).isEqualTo(original.id());
        assertThat(lido.clienteId()).isEqualTo(original.clienteId());
        assertThat(lido.status()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(lido.itens()).containsExactlyElementsOf(original.itens());
        assertThat(lido.endereco()).isEqualTo(original.endereco());
        assertThat(lido.frete()).isEqualTo(original.frete());
        assertThat(lido.total()).isEqualTo(Dinheiro.de("270.40"));
        assertThat(lido.criadoEm()).isEqualTo(T0);
        assertThat(lido.eventosPendentes()).isEmpty();
    }

    @Test
    void salvarDeNovoAtualizaOStatus() {
        Pedido pedido = pedido("cliente-1", T0);
        pedidos().salvar(pedido);

        pedido.cancelar(T0.plusSeconds(60));
        pedidos().salvar(pedido);

        Pedido lido = pedidos().buscarPorId(pedido.id()).orElseThrow();
        assertThat(lido.status()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(lido.atualizadoEm()).isEqualTo(T0.plusSeconds(60));
        assertThat(lido.itens()).hasSize(2);
    }

    @Test
    void idDesconhecidoViraOptionalVazio() {
        assertThat(pedidos().buscarPorId(PedidoId.novo())).isEmpty();
    }

    @Test
    void listaSoOsPedidosDoClienteDoMaisRecenteParaOMaisAntigo() {
        Pedido antigo = pedido("cliente-1", T0);
        Pedido recente = pedido("cliente-1", T0.plusSeconds(3600));
        Pedido deOutro = pedido("cliente-2", T0.plusSeconds(7200));
        pedidos().salvar(antigo);
        pedidos().salvar(recente);
        pedidos().salvar(deOutro);

        assertThat(pedidos().listarPorCliente(new ClienteId("cliente-1")))
                .extracting(Pedido::id).containsExactly(recente.id(), antigo.id());
        assertThat(pedidos().listarPorCliente(new ClienteId("ninguem"))).isEmpty();
    }

    @Test
    void listaOsAguardandoPagamentoCriadosAteOLimiteInclusivoDoMaisAntigoParaOMaisNovo() {
        Pedido maisAntigo = pedido("cliente-1", T0);
        Pedido noLimite = pedido("cliente-2", T0.plusSeconds(1800));
        Pedido depoisDoLimite = pedido("cliente-1", T0.plusSeconds(1801));
        pedidos().salvar(noLimite);
        pedidos().salvar(depoisDoLimite);
        pedidos().salvar(maisAntigo);

        assertThat(pedidos().listarAguardandoPagamentoCriadosAte(T0.plusSeconds(1800), 10))
                .extracting(Pedido::id).containsExactly(maisAntigo.id(), noLimite.id());
    }

    @Test
    void naoListaOsQueJaSairamDeAguardandoPagamento() {
        Pedido cancelado = pedido("cliente-1", T0);
        Pedido pago = pedido("cliente-1", T0);
        Pedido aguardando = pedido("cliente-1", T0);
        cancelado.cancelar(T0.plusSeconds(10));
        pago.marcarComoPago(T0.plusSeconds(10));
        pedidos().salvar(cancelado);
        pedidos().salvar(pago);
        pedidos().salvar(aguardando);

        assertThat(pedidos().listarAguardandoPagamentoCriadosAte(T0.plusSeconds(3600), 10))
                .extracting(Pedido::id).containsExactly(aguardando.id());
    }

    @Test
    void respeitaOLimiteDeQuantidade() {
        for (int i = 0; i < 5; i++) {
            pedidos().salvar(pedido("cliente-1", T0.plusSeconds(i)));
        }

        assertThat(pedidos().listarAguardandoPagamentoCriadosAte(T0.plusSeconds(3600), 3)).hasSize(3);
    }
}
