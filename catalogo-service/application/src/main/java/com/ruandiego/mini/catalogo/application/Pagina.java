package com.ruandiego.mini.catalogo.application;

import java.util.List;
import java.util.function.Function;

/** Uma página de resultados. `pagina` começa em 0. */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total) {

    public Pagina {
        itens = List.copyOf(itens);
    }

    public long totalPaginas() {
        return tamanho == 0 ? 0 : (total + tamanho - 1) / tamanho;
    }

    public <R> Pagina<R> mapear(Function<T, R> conversor) {
        return new Pagina<>(itens.stream().map(conversor).toList(), pagina, tamanho, total);
    }
}
