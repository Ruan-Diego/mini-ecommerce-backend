package com.ruandiego.mini.catalogo.application.port.in;

import com.ruandiego.mini.catalogo.application.Pagina;
import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;

/** Vitrine: busca produtos ativos com filtros, ordem e paginação. */
public interface BuscarProdutosUseCase {

    Pagina<Produto> buscar(Filtro filtro);

    enum Ordem { NOME, MENOR_PRECO, MAIOR_PRECO }

    /**
     * Todos os campos são opcionais (nulo = sem filtro). Categoria inclui as subcategorias.
     * Disponibilidade em estoque entra quando existir o estoque-service (Fase 4).
     */
    record Filtro(String texto, Slug categoria, Dinheiro precoMin, Dinheiro precoMax, Ordem ordem,
                  Integer pagina, Integer tamanho) {

        public static final int TAMANHO_PADRAO = 24;
        public static final int TAMANHO_MAXIMO = 100;

        public Filtro {
            texto = texto == null || texto.isBlank() ? null : texto.trim();
            ordem = ordem == null ? Ordem.NOME : ordem;
            pagina = pagina == null ? 0 : pagina;
            tamanho = tamanho == null ? TAMANHO_PADRAO : tamanho;
            if (pagina < 0) {
                throw new RegraDeNegocioException("página começa em 0");
            }
            if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
                throw new RegraDeNegocioException("tamanho da página deve ser de 1 a " + TAMANHO_MAXIMO);
            }
            if (precoMin != null && precoMax != null && precoMin.compareTo(precoMax) > 0) {
                throw new RegraDeNegocioException("preço mínimo maior que o máximo");
            }
        }

        public static Filtro vazio() {
            return new Filtro(null, null, null, null, null, null, null);
        }
    }
}
