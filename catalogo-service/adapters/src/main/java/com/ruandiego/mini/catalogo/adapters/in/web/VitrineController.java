package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.application.port.in.BuscarProdutosUseCase;
import com.ruandiego.mini.catalogo.application.port.in.BuscarProdutosUseCase.Filtro;
import com.ruandiego.mini.catalogo.application.port.in.BuscarProdutosUseCase.Ordem;
import com.ruandiego.mini.catalogo.application.port.in.ListarCategoriasUseCase;
import com.ruandiego.mini.catalogo.application.port.in.ObterProdutoUseCase;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Endpoints públicos da vitrine. O controller só traduz HTTP ↔ port de entrada: lê os parâmetros,
 * monta o Filtro e converte o resultado para JSON. Nenhuma regra mora aqui.
 */
@RestController
@Tag(name = "Vitrine", description = "Consulta pública de produtos e categorias")
class VitrineController {

    private final BuscarProdutosUseCase buscarProdutos;
    private final ObterProdutoUseCase obterProduto;
    private final ListarCategoriasUseCase listarCategorias;

    VitrineController(BuscarProdutosUseCase buscarProdutos, ObterProdutoUseCase obterProduto,
                      ListarCategoriasUseCase listarCategorias) {
        this.buscarProdutos = buscarProdutos;
        this.obterProduto = obterProduto;
        this.listarCategorias = listarCategorias;
    }

    @GetMapping("/produtos")
    @Operation(summary = "Busca produtos ativos com filtros, ordem e paginação")
    PaginaResposta<ProdutoResposta> buscar(
            @RequestParam(required = false) @Parameter(description = "Texto no nome ou na descrição") String busca,
            @RequestParam(required = false) @Parameter(description = "Slug da categoria (inclui subcategorias)")
            String categoria,
            @RequestParam(required = false) BigDecimal precoMin,
            @RequestParam(required = false) BigDecimal precoMax,
            @RequestParam(required = false)
            @Parameter(description = "Só em estoque. Aceito e ignorado até existir o estoque-service (Fase 4)")
            Boolean disponivel,
            @RequestParam(required = false) @Parameter(description = "nome, menor-preco ou maior-preco")
            String ordem,
            @RequestParam(required = false) @Parameter(description = "Começa em 0") Integer pagina,
            @RequestParam(required = false) @Parameter(description = "De 1 a 100, padrão 24") Integer tamanho) {
        Filtro filtro = new Filtro(busca, categoria == null ? null : new Slug(categoria), dinheiro(precoMin),
                dinheiro(precoMax), ordem(ordem), pagina, tamanho);
        return PaginaResposta.de(buscarProdutos.buscar(filtro), ProdutoResposta::de);
    }

    @GetMapping("/produtos/{slug}")
    @Operation(summary = "Detalhe de um produto ativo")
    ProdutoResposta obter(@PathVariable String slug) {
        return ProdutoResposta.de(obterProduto.obterPorSlug(new Slug(slug)));
    }

    @GetMapping("/produtos/por-id/{id}")
    @Operation(summary = "Detalhe de um produto ativo pelo id (usado pelo carrinho)")
    ProdutoResposta obterPorId(@PathVariable String id) {
        return ProdutoResposta.de(obterProduto.obterPorId(
                RequisicaoInvalidaException.converter("id", id, ProdutoId::de)));
    }

    @GetMapping("/categorias")
    @Operation(summary = "Categorias em ordem alfabética")
    List<CategoriaResposta> categorias() {
        return listarCategorias.listar().stream().map(CategoriaResposta::de).toList();
    }

    private static Dinheiro dinheiro(BigDecimal valor) {
        return valor == null ? null : new Dinheiro(valor);
    }

    /** "menor-preco" → MENOR_PRECO. Na URL fica o formato amigável; no port, o enum. */
    private static Ordem ordem(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return RequisicaoInvalidaException.converter("ordem", valor,
                v -> Ordem.valueOf(v.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
    }
}
