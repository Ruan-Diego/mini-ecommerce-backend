package com.ruandiego.mini.estoque.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador do produto (o mesmo UUID do catálogo; este serviço só o referencia, sem chave estrangeira). */
public record ProdutoId(UUID valor) implements Comparable<ProdutoId> {

    public ProdutoId {
        Objects.requireNonNull(valor, "valor");
    }

    public static ProdutoId de(String valor) {
        return new ProdutoId(UUID.fromString(valor));
    }

    /** Ordem estável (a mesma do texto do UUID, que é a do banco): quem trava vários itens os trava sempre nesta ordem, para duas reservas não se bloquearem em ciclo. */
    @Override
    public int compareTo(ProdutoId outro) {
        return valor.toString().compareTo(outro.valor.toString());
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
