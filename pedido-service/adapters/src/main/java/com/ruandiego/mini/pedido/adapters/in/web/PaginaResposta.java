package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.pedido.application.Pagina;

import java.util.List;
import java.util.function.Function;

/** Página como o REST devolve (o mesmo formato da vitrine do catálogo). */
record PaginaResposta<T>(List<T> itens, int pagina, int tamanho, long total, long totalPaginas) {

    static <D, T> PaginaResposta<T> de(Pagina<D> pagina, Function<D, T> conversor) {
        Pagina<T> convertida = pagina.mapear(conversor);
        return new PaginaResposta<>(convertida.itens(), convertida.pagina(), convertida.tamanho(),
                convertida.total(), convertida.totalPaginas());
    }
}
