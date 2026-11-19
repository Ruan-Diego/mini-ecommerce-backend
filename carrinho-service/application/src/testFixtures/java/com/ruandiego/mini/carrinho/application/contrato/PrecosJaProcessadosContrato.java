package com.ruandiego.mini.carrinho.application.contrato;

import com.ruandiego.mini.carrinho.application.port.out.PrecosJaProcessados;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do port PrecosJaProcessados: todo adapter (em memória, JPA) estende esta classe. */
public abstract class PrecosJaProcessadosContrato {

    private static final Instant T1 = Instant.parse("2026-11-19T12:00:00Z");
    private static final Instant T2 = T1.plusSeconds(60);

    protected abstract PrecosJaProcessados processados();

    private static ProdutoId novoProduto() {
        return new ProdutoId(UUID.randomUUID());
    }

    @Test
    void produtoSemEventoNaoEstaProcessado() {
        assertThat(processados().jaProcessado(novoProduto(), T1)).isFalse();
    }

    @Test
    void depoisDeRegistrarOMesmoInstanteEOsMaisAntigosContamComoProcessados() {
        ProdutoId produto = novoProduto();

        processados().registrar(produto, T2);

        assertThat(processados().jaProcessado(produto, T2)).isTrue();
        assertThat(processados().jaProcessado(produto, T1)).isTrue();
        assertThat(processados().jaProcessado(produto, T2.plusMillis(1))).isFalse();
    }

    @Test
    void registrarUmInstanteMaisAntigoNaoFazVoltarNoTempo() {
        ProdutoId produto = novoProduto();
        processados().registrar(produto, T2);

        processados().registrar(produto, T1);

        assertThat(processados().jaProcessado(produto, T2)).isTrue();
    }

    @Test
    void cadaProdutoTemOSeuProprioMarco() {
        ProdutoId a = novoProduto();
        ProdutoId b = novoProduto();

        processados().registrar(a, T2);

        assertThat(processados().jaProcessado(b, T1)).isFalse();
    }

    @Test
    void guardaOInstanteComPrecisaoDeMicrossegundos() {
        ProdutoId produto = novoProduto();
        Instant preciso = Instant.parse("2026-11-19T12:00:00.123456Z");

        processados().registrar(produto, preciso);

        assertThat(processados().jaProcessado(produto, preciso)).isTrue();
        assertThat(processados().jaProcessado(produto, preciso.plusNanos(1_000))).isFalse();
    }
}
