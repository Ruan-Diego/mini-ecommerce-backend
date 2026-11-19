package com.ruandiego.mini.carrinho.application.contrato;

import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cenários e asserções compartilhados pelos contratos dos dois repositórios de carrinho. */
final class CarrinhoRepositoryContratoBase {

    static final Instant VALIDADE = Instant.parse("2030-01-01T00:00:00Z");
    static final ProdutoId ESFERA = new ProdutoId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    static final ProdutoId CUBO = new ProdutoId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    private CarrinhoRepositoryContratoBase() {
    }

    /** Dois itens e um cupom percentual com mínimo: exercita todos os campos que o adapter precisa guardar. */
    static Carrinho carrinhoCompleto() {
        Carrinho c = Carrinho.criar();
        c.adicionarItem(ESFERA, "Esfera Chrome", Dinheiro.de("100.00"), 2);
        c.adicionarItem(CUBO, "Cubo Preto", Dinheiro.de("49.90"), 1);
        c.aplicarCupom(Cupom.percentual("BEMVINDO", 10, VALIDADE, Dinheiro.de("100.00")),
                Instant.parse("2026-11-19T12:00:00Z"));
        return c;
    }

    static void assertIgual(Carrinho lido, Carrinho esperado) {
        assertThat(lido.id()).isEqualTo(esperado.id());
        assertThat(lido.itens()).containsExactlyElementsOf(esperado.itens());
        assertThat(lido.subtotal()).isEqualTo(esperado.subtotal());
        assertThat(lido.cupom()).isEqualTo(esperado.cupom());
    }
}
