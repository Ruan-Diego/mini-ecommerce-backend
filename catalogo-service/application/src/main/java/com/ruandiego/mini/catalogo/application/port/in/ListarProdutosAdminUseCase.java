package com.ruandiego.mini.catalogo.application.port.in;

import com.ruandiego.mini.catalogo.application.Pagina;
import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import com.ruandiego.mini.catalogo.domain.model.Produto;

/**
 * Lista de produtos do admin: diferente da vitrine, inclui os desativados (para reativar ou conferir) e a busca
 * por texto também acha pelo SKU. Ordem fixa por nome.
 */
public interface ListarProdutosAdminUseCase {

    Pagina<Produto> listarParaAdmin(Filtro filtro);

    /** Texto no nome, na descrição ou no SKU (nulo ou em branco = sem filtro). Página começa em 0. */
    record Filtro(String texto, Integer pagina, Integer tamanho) {

        public static final int TAMANHO_PADRAO = 20;
        public static final int TAMANHO_MAXIMO = 100;

        public Filtro {
            texto = texto == null || texto.isBlank() ? null : texto.trim();
            pagina = pagina == null ? 0 : pagina;
            tamanho = tamanho == null ? TAMANHO_PADRAO : tamanho;
            if (pagina < 0) {
                throw new RegraDeNegocioException("página começa em 0");
            }
            if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
                throw new RegraDeNegocioException("tamanho da página deve ser de 1 a " + TAMANHO_MAXIMO);
            }
        }
    }
}
