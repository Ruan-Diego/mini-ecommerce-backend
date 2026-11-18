package com.ruandiego.mini.catalogo.domain.event;

import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.kernel.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * O preço de um produto mudou. O carrinho ouve este evento para atualizar os itens (Fase 2).
 * Fica registrado no agregado e só é publicado depois do commit, via outbox.
 */
public record PrecoAlterado(ProdutoId produtoId, Sku sku, Dinheiro precoAnterior, Dinheiro precoNovo, Instant ocorridoEm)
        implements DomainEvent {

    public static final String TIPO = "catalogo.preco-alterado";

    public PrecoAlterado {
        Objects.requireNonNull(produtoId, "produtoId");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(precoAnterior, "precoAnterior");
        Objects.requireNonNull(precoNovo, "precoNovo");
        Objects.requireNonNull(ocorridoEm, "ocorridoEm");
    }

    @Override
    public String tipo() {
        return TIPO;
    }
}
