package com.ruandiego.mini.pedido.domain.model;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;
import com.ruandiego.mini.pedido.domain.TransicaoDeStatusInvalida;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidoTest {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-20T11:00:00Z");

    private static ItemPedido item(String preco, int quantidade) {
        return new ItemPedido(new ProdutoId(UUID.randomUUID()), "Peça", Dinheiro.de(preco), quantidade);
    }

    private static EnderecoEntrega endereco() {
        return new EnderecoEntrega("Ana Souza", "01310-100", "Av. Paulista", "1000", null, "Bela Vista", "São Paulo", "sp");
    }

    private static Frete frete() {
        return new Frete("padrao", "Padrão", Dinheiro.de("19.90"), 7);
    }

    private static Pedido novo() {
        return Pedido.criar(new ClienteId("cliente-1"), List.of(item("100.00", 2), item("50.50", 1)), endereco(), frete(), T0);
    }

    private static Pedido noStatus(StatusPedido status) {
        return Pedido.reconstituir(PedidoId.novo(), new ClienteId("cliente-1"), List.of(item("10.00", 1)), endereco(),
                frete(), status, T0, T0);
    }

    // ---- criação ----

    @Test
    void nasceAguardandoPagamentoComTotalSomandoItensEFrete() {
        Pedido pedido = novo();

        assertThat(pedido.status()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(pedido.subtotal()).isEqualTo(Dinheiro.de("250.50"));
        assertThat(pedido.total()).isEqualTo(Dinheiro.de("270.40"));
        assertThat(pedido.endereco().uf()).isEqualTo("SP");
        assertThat(pedido.endereco().cep()).isEqualTo("01310100");
    }

    @Test
    void registraPedidoCriadoAoNascer() {
        Pedido pedido = novo();

        assertThat(pedido.eventosPendentes()).singleElement().isInstanceOfSatisfying(PedidoCriado.class, evento -> {
            assertThat(evento.tipo()).isEqualTo("pedido.criado");
            assertThat(evento.pedidoId()).isEqualTo(pedido.id().toString());
            assertThat(evento.total()).isEqualByComparingTo("270.40");
            assertThat(evento.itens()).hasSize(pedido.itens().size())
                    .allSatisfy(item -> assertThat(item.quantidade()).isPositive());
        });
    }

    @Test
    void reconstituirNaoRegistraEvento() {
        assertThat(noStatus(StatusPedido.PAGO).eventosPendentes()).isEmpty();
    }

    @Test
    void recusaPedidoSemItens() {
        assertThatThrownBy(() -> Pedido.criar(new ClienteId("c"), List.of(), endereco(), frete(), T0))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void recusaItemComQuantidadeForaDoLimiteEPrecoNaoPositivo() {
        assertThatThrownBy(() -> item("10.00", 0)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> item("10.00", 11)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> item("0.00", 1)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void recusaEnderecoComCepInvalidoEUfInvalida() {
        assertThatThrownBy(() -> new EnderecoEntrega("A", "123", "R", "1", null, "B", "C", "SP"))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("CEP");
        assertThatThrownBy(() -> new EnderecoEntrega("A", "01310100", "R", "1", null, "B", "C", "SAO"))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("UF");
    }

    // ---- máquina de estados: todas as combinações (de, para) ----

    /** Cada destino tem o seu método; o teste exercita todos pelo mesmo caminho do domínio. */
    private static final Map<StatusPedido, BiConsumer<Pedido, Instant>> ACOES = Map.of(
            StatusPedido.PAGO, Pedido::marcarComoPago,
            StatusPedido.EM_SEPARACAO, Pedido::iniciarSeparacao,
            StatusPedido.ENVIADO, Pedido::enviar,
            StatusPedido.ENTREGUE, Pedido::entregar,
            StatusPedido.CANCELADO, Pedido::cancelar);

    /** O fluxo esperado, escrito à mão e independente do enum, para o teste não repetir a implementação. */
    private static boolean esperadoPermitido(StatusPedido de, StatusPedido para) {
        return switch (de) {
            case AGUARDANDO_PAGAMENTO -> para == StatusPedido.PAGO || para == StatusPedido.CANCELADO;
            case PAGO -> para == StatusPedido.EM_SEPARACAO || para == StatusPedido.CANCELADO;
            case EM_SEPARACAO -> para == StatusPedido.ENVIADO || para == StatusPedido.CANCELADO;
            case ENVIADO -> para == StatusPedido.ENTREGUE;
            case ENTREGUE, CANCELADO -> false;
        };
    }

    static Stream<Arguments> transicoesValidas() {
        return combinacoes(true);
    }

    static Stream<Arguments> transicoesInvalidas() {
        return combinacoes(false);
    }

    /** AGUARDANDO_PAGAMENTO não é destino de ninguém (não existe método para ele); as demais combinações entram. */
    private static Stream<Arguments> combinacoes(boolean permitidas) {
        List<Arguments> todas = new ArrayList<>();
        for (StatusPedido de : StatusPedido.values()) {
            for (StatusPedido para : ACOES.keySet()) {
                if (esperadoPermitido(de, para) == permitidas) {
                    todas.add(Arguments.of(de, para));
                }
            }
        }
        return todas.stream();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("transicoesValidas")
    void aplicaTransicaoValidaERegistraEvento(StatusPedido de, StatusPedido para) {
        Pedido pedido = noStatus(de);

        ACOES.get(para).accept(pedido, T1);

        assertThat(pedido.status()).isEqualTo(para);
        assertThat(pedido.atualizadoEm()).isEqualTo(T1);
        assertThat(pedido.eventosPendentes()).singleElement().isInstanceOfSatisfying(PedidoStatusAlterado.class, evento -> {
            assertThat(evento.de()).isEqualTo(de.name());
            assertThat(evento.para()).isEqualTo(para.name());
            assertThat(evento.ocorridoEm()).isEqualTo(T1);
        });
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("transicoesInvalidas")
    void recusaTransicaoInvalidaSemMudarNada(StatusPedido de, StatusPedido para) {
        Pedido pedido = noStatus(de);

        assertThatThrownBy(() -> ACOES.get(para).accept(pedido, T1))
                .isInstanceOfSatisfying(TransicaoDeStatusInvalida.class, e -> {
                    assertThat(e.de()).isEqualTo(de);
                    assertThat(e.para()).isEqualTo(para);
                });

        assertThat(pedido.status()).isEqualTo(de);
        assertThat(pedido.atualizadoEm()).isEqualTo(T0);
        assertThat(pedido.eventosPendentes()).isEmpty();
    }

    @Test
    void oEnumConcordaComOFluxoEscritoNoTeste() {
        for (StatusPedido de : StatusPedido.values()) {
            for (StatusPedido para : StatusPedido.values()) {
                boolean esperado = para != StatusPedido.AGUARDANDO_PAGAMENTO && esperadoPermitido(de, para);
                assertThat(de.podeIrPara(para)).as(de + " → " + para).isEqualTo(esperado);
            }
        }
    }

    @Test
    void statusFinaisNaoTemSaida() {
        assertThat(StatusPedido.ENTREGUE.terminal()).isTrue();
        assertThat(StatusPedido.CANCELADO.terminal()).isTrue();
        assertThat(StatusPedido.ENVIADO.terminal()).isFalse();
    }

    @Test
    void percorreOFluxoFelizAteEntregue() {
        Pedido pedido = novo();
        pedido.limparEventos();

        pedido.marcarComoPago(T1);
        pedido.iniciarSeparacao(T1);
        pedido.enviar(T1);
        pedido.entregar(T1);

        assertThat(pedido.status()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(pedido.eventosPendentes()).extracting(e -> e.tipo())
                .containsExactly("pedido.pago", "pedido.em-separacao", "pedido.enviado", "pedido.entregue");
    }
}
