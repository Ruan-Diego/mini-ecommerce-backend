package com.ruandiego.mini.pagamento.domain.model;

import com.ruandiego.mini.kernel.DomainEvent;
import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.domain.RegraDeNegocioException;
import com.ruandiego.mini.pagamento.domain.TransicaoDeStatusInvalida;
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

class PagamentoTest {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-20T10:10:00Z");
    private static final Instant DEPOIS_DO_PRAZO = T0.plusSeconds(30 * 60);

    private static Pagamento cartao() {
        return Pagamento.cartao(new PedidoId(UUID.randomUUID()), new ClienteId("ana"), Dinheiro.de("219.90"), T0);
    }

    private static Pagamento pix() {
        return Pagamento.pix(new PedidoId(UUID.randomUUID()), new ClienteId("ana"), Dinheiro.de("219.90"), T0);
    }

    private static Pagamento noStatus(StatusPagamento status) {
        return Pagamento.reconstituir(PagamentoId.novo(), new PedidoId(UUID.randomUUID()), new ClienteId("ana"),
                MetodoPagamento.PIX, Dinheiro.de("10.00"), status, "tx-1", "codigo", T0.plusSeconds(1800), null, T0, T0);
    }

    // ---- criação ----

    @Test
    void cartaoNasceBemPendenteSemPixENemTransacao() {
        Pagamento pagamento = cartao();

        assertThat(pagamento.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.metodo()).isEqualTo(MetodoPagamento.CARTAO);
        assertThat(pagamento.pixExpiraEm()).isNull();
        assertThat(pagamento.transacaoId()).isNull();
        assertThat(pagamento.eventosPendentes()).isEmpty();
    }

    @Test
    void pixExpiraEmTrintaMinutos() {
        assertThat(pix().pixExpiraEm()).isEqualTo(T0.plusSeconds(1800));
    }

    @Test
    void recusaValorNaoPositivo() {
        assertThatThrownBy(() -> Pagamento.cartao(new PedidoId(UUID.randomUUID()), new ClienteId("a"), Dinheiro.ZERO, T0))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void vinculaATransacaoUmaVezSo() {
        Pagamento pagamento = cartao();
        pagamento.vincularTransacao("tx-1");

        assertThat(pagamento.transacaoId()).isEqualTo("tx-1");
        assertThatThrownBy(() -> pagamento.vincularTransacao("tx-2")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> cartao().vincularTransacao(" ")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void soPixTemCodigoESoPixExpira() {
        assertThatThrownBy(() -> cartao().definirCodigoPix("x")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> cartao().expirar(DEPOIS_DO_PRAZO)).isInstanceOf(RegraDeNegocioException.class);

        Pagamento pix = pix();
        pix.definirCodigoPix("00020126FAKE");
        assertThat(pix.pixCodigo()).isEqualTo("00020126FAKE");
    }

    // ---- eventos ----

    @Test
    void aprovarRegistraPagamentoAprovado() {
        Pagamento pagamento = cartao();
        pagamento.vincularTransacao("tx-1");

        pagamento.aprovar(T1);

        assertThat(pagamento.status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamento.eventosPendentes()).singleElement().isInstanceOfSatisfying(PagamentoAprovado.class, e -> {
            assertThat(e.tipo()).isEqualTo("pagamento.aprovado");
            assertThat(e.pagamentoId()).isEqualTo(pagamento.id().toString());
            assertThat(e.pedidoId()).isEqualTo(pagamento.pedidoId().toString());
            assertThat(e.valor()).isEqualByComparingTo("219.90");
            assertThat(e.transacaoId()).isEqualTo("tx-1");
        });
    }

    @Test
    void recusarGuardaOMotivoERegistraPagamentoRecusado() {
        Pagamento pagamento = cartao();

        pagamento.recusar("cartão recusado", T1);

        assertThat(pagamento.motivoRecusa()).isEqualTo("cartão recusado");
        assertThat(pagamento.eventosPendentes()).singleElement().isInstanceOfSatisfying(PagamentoRecusado.class, e -> {
            assertThat(e.tipo()).isEqualTo("pagamento.recusado");
            assertThat(e.motivo()).isEqualTo("cartão recusado");
        });
    }

    @Test
    void estornarRegistraPagamentoEstornado() {
        Pagamento pagamento = noStatus(StatusPagamento.APROVADO);

        pagamento.estornar(T1);

        assertThat(pagamento.status()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(pagamento.eventosPendentes()).extracting(DomainEvent::tipo).containsExactly("pagamento.estornado");
    }

    // ---- expiração do Pix ----

    @Test
    void pixNaoExpiraDentroDoPrazoMasExpiraNoPrazo() {
        Pagamento pagamento = pix();

        assertThat(pagamento.pixVencido(DEPOIS_DO_PRAZO.minusSeconds(1))).isFalse();
        assertThatThrownBy(() -> pagamento.expirar(DEPOIS_DO_PRAZO.minusSeconds(1)))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(pagamento.status()).isEqualTo(StatusPagamento.PENDENTE);

        assertThat(pagamento.pixVencido(DEPOIS_DO_PRAZO)).isTrue();
        pagamento.expirar(DEPOIS_DO_PRAZO);

        assertThat(pagamento.status()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(pagamento.eventosPendentes()).singleElement().isInstanceOfSatisfying(PagamentoRecusado.class,
                e -> assertThat(e.motivo()).isEqualTo("Pix expirado"));
    }

    @Test
    void pixJaPagoNaoEhVencidoNemExpira() {
        Pagamento pago = noStatus(StatusPagamento.APROVADO);

        assertThat(pago.pixVencido(DEPOIS_DO_PRAZO.plusSeconds(3600))).isFalse();
        assertThatThrownBy(() -> pago.expirar(DEPOIS_DO_PRAZO.plusSeconds(3600)))
                .isInstanceOf(TransicaoDeStatusInvalida.class);
    }

    // ---- máquina de estados: todas as combinações (de, para) ----

    private static final Map<StatusPagamento, BiConsumer<Pagamento, Instant>> ACOES = Map.of(
            StatusPagamento.APROVADO, Pagamento::aprovar,
            StatusPagamento.RECUSADO, (p, t) -> p.recusar("motivo", t),
            StatusPagamento.EXPIRADO, Pagamento::expirar,
            StatusPagamento.ESTORNADO, Pagamento::estornar);

    /** O fluxo esperado, escrito à mão e independente do enum. */
    private static boolean esperadoPermitido(StatusPagamento de, StatusPagamento para) {
        return switch (de) {
            case PENDENTE -> para == StatusPagamento.APROVADO || para == StatusPagamento.RECUSADO
                    || para == StatusPagamento.EXPIRADO;
            case APROVADO -> para == StatusPagamento.ESTORNADO;
            case RECUSADO, EXPIRADO, ESTORNADO -> false;
        };
    }

    static Stream<Arguments> transicoesValidas() {
        return combinacoes(true);
    }

    static Stream<Arguments> transicoesInvalidas() {
        return combinacoes(false);
    }

    /** PENDENTE não é destino de ninguém (não existe método para ele). */
    private static Stream<Arguments> combinacoes(boolean permitidas) {
        List<Arguments> todas = new ArrayList<>();
        for (StatusPagamento de : StatusPagamento.values()) {
            for (StatusPagamento para : ACOES.keySet()) {
                if (esperadoPermitido(de, para) == permitidas) {
                    todas.add(Arguments.of(de, para));
                }
            }
        }
        return todas.stream();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("transicoesValidas")
    void aplicaTransicaoValida(StatusPagamento de, StatusPagamento para) {
        Pagamento pagamento = noStatus(de);

        ACOES.get(para).accept(pagamento, DEPOIS_DO_PRAZO);

        assertThat(pagamento.status()).isEqualTo(para);
        assertThat(pagamento.atualizadoEm()).isEqualTo(DEPOIS_DO_PRAZO);
        assertThat(pagamento.eventosPendentes()).hasSize(1);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("transicoesInvalidas")
    void recusaTransicaoInvalidaSemMudarNada(StatusPagamento de, StatusPagamento para) {
        Pagamento pagamento = noStatus(de);

        assertThatThrownBy(() -> ACOES.get(para).accept(pagamento, DEPOIS_DO_PRAZO))
                .isInstanceOfSatisfying(TransicaoDeStatusInvalida.class, e -> {
                    assertThat(e.de()).isEqualTo(de);
                    assertThat(e.para()).isEqualTo(para);
                });

        assertThat(pagamento.status()).isEqualTo(de);
        assertThat(pagamento.atualizadoEm()).isEqualTo(T0);
        assertThat(pagamento.eventosPendentes()).isEmpty();
    }

    @Test
    void oEnumConcordaComOFluxoEscritoNoTeste() {
        for (StatusPagamento de : StatusPagamento.values()) {
            for (StatusPagamento para : StatusPagamento.values()) {
                boolean esperado = para != StatusPagamento.PENDENTE && esperadoPermitido(de, para);
                assertThat(de.podeIrPara(para)).as(de + " → " + para).isEqualTo(esperado);
            }
        }
    }
}
