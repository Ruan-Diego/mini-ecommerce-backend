package com.ruandiego.mini.catalogo.application.contrato;

import com.ruandiego.mini.catalogo.application.Pagina;
import com.ruandiego.mini.catalogo.application.port.in.BuscarProdutosUseCase.Ordem;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository.Criterios;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato do port ProdutoRepository: o adapter em memória e o JPA estendem esta classe e precisam
 * passar nos mesmos cenários. É isso que permite testar os use cases com o adapter em memória e
 * confiar que em produção, com MySQL, o comportamento é o mesmo.
 */
public abstract class ProdutoRepositoryContrato {

    protected abstract ProdutoRepository produtos();

    /** As categorias existem antes dos produtos (no banco há chave estrangeira). */
    protected abstract CategoriaRepository categorias();

    private CategoriaId esferas;
    private CategoriaId aneis;

    @BeforeEach
    void categoriasBase() {
        esferas = categoria("Esferas");
        aneis = categoria("Anéis");
    }

    @Test
    void salvaEReconstituiTodosOsCampos() {
        Produto p = Produto.criar(new Sku("ESF-CHROME"), "Esfera Chrome", null, "Esfera de aço polido",
                Dinheiro.de("289.90"),
                List.of(new Imagem("https://img.exemplo/1.jpg", "Frente"), new Imagem("https://img.exemplo/2.jpg", "Verso")),
                esferas);
        salvar(p);

        Produto lido = produtos().buscarPorId(p.id()).orElseThrow();
        assertThat(lido).isNotSameAs(p);
        assertThat(lido.sku()).isEqualTo(new Sku("ESF-CHROME"));
        assertThat(lido.nome()).isEqualTo("Esfera Chrome");
        assertThat(lido.slug()).isEqualTo(new Slug("esfera-chrome"));
        assertThat(lido.descricao()).isEqualTo("Esfera de aço polido");
        assertThat(lido.preco()).isEqualTo(Dinheiro.de("289.90"));
        assertThat(lido.imagens()).extracting(Imagem::textoAlternativo).containsExactly("Frente", "Verso");
        assertThat(lido.categoriaId()).isEqualTo(esferas);
        assertThat(lido.ativo()).isTrue();
        assertThat(lido.eventosPendentes()).isEmpty();
    }

    @Test
    void salvarDeNovoAtualiza() {
        Produto p = produto("ANL-IRIS", "Anel Íris", "219.00", aneis, true);

        p.alterarPreco(Dinheiro.de("199.00"), Instant.parse("2026-11-19T12:00:00Z"));
        p.atualizarDados("Anel Íris Duplo", "Novo", List.of(new Imagem("https://img.exemplo/a.jpg", "Anel")), esferas);
        p.desativar();
        salvar(p);

        Produto lido = produtos().buscarPorId(p.id()).orElseThrow();
        assertThat(lido.nome()).isEqualTo("Anel Íris Duplo");
        assertThat(lido.preco()).isEqualTo(Dinheiro.de("199.00"));
        assertThat(lido.imagens()).hasSize(1);
        assertThat(lido.categoriaId()).isEqualTo(esferas);
        assertThat(lido.ativo()).isFalse();
    }

    @Test
    void buscaPorSlugEVerificaSkuESlug() {
        Produto p = produto("ESF-LIME", "Esfera Lime", "199.00", esferas, true);

        assertThat(produtos().buscarPorSlug(new Slug("esfera-lime"))).map(Produto::id).contains(p.id());
        assertThat(produtos().buscarPorSlug(new Slug("outra"))).isEmpty();
        assertThat(produtos().buscarPorId(ProdutoId.novo())).isEmpty();
        assertThat(produtos().existeComSku(new Sku("esf-lime"))).isTrue();
        assertThat(produtos().existeComSku(new Sku("ESF-XXX"))).isFalse();
        assertThat(produtos().existeComSlug(new Slug("esfera-lime"))).isTrue();
        assertThat(produtos().existeComSlug(new Slug("outra"))).isFalse();
    }

    @Test
    void buscaSoAtivosOuTodos() {
        vitrine();

        assertThat(nomes(produtos().buscar(criterios(null, Set.of(), null, null, true, Ordem.NOME))))
                .containsExactly("Anel Íris", "Esfera Chrome", "Esfera Lime");
        assertThat(nomes(produtos().buscar(criterios(null, Set.of(), null, null, false, Ordem.NOME))))
                .containsExactly("Anel Clay", "Anel Íris", "Esfera Chrome", "Esfera Lime");
    }

    @Test
    void filtraPorCategoria() {
        vitrine();

        assertThat(nomes(produtos().buscar(criterios(null, Set.of(aneis), null, null, true, Ordem.NOME))))
                .containsExactly("Anel Íris");
        assertThat(nomes(produtos().buscar(criterios(null, Set.of(aneis, esferas), null, null, true, Ordem.NOME))))
                .hasSize(3);
    }

    @Test
    void filtraPorFaixaDePrecoComLimitesInclusivos() {
        vitrine();

        assertThat(nomes(produtos().buscar(criterios(null, Set.of(), Dinheiro.de("199.00"), Dinheiro.de("219.00"),
                true, Ordem.NOME)))).containsExactly("Anel Íris", "Esfera Lime");
        assertThat(nomes(produtos().buscar(criterios(null, Set.of(), Dinheiro.de("250"), null, true, Ordem.NOME))))
                .containsExactly("Esfera Chrome");
    }

    @Test
    void buscaTextoSemDiferenciarMaiusculasNemAcentosEmNomeDescricaoESku() {
        vitrine();

        assertThat(nomes(produtos().buscar(criterios("IRIS", Set.of(), null, null, true, Ordem.NOME))))
                .containsExactly("Anel Íris");
        assertThat(nomes(produtos().buscar(criterios("aço", Set.of(), null, null, true, Ordem.NOME))))
                .containsExactly("Esfera Chrome");
        assertThat(nomes(produtos().buscar(criterios("esf-l", Set.of(), null, null, true, Ordem.NOME))))
                .containsExactly("Esfera Lime");
    }

    @Test
    void curingasDoTextoSaoLiterais() {
        vitrine();

        assertThat(produtos().buscar(criterios("%", Set.of(), null, null, true, Ordem.NOME)).total()).isZero();
        assertThat(produtos().buscar(criterios("_", Set.of(), null, null, true, Ordem.NOME)).total()).isZero();
    }

    @Test
    void ordenaPorPreco() {
        vitrine();

        assertThat(nomes(produtos().buscar(criterios(null, Set.of(), null, null, true, Ordem.MENOR_PRECO))))
                .containsExactly("Esfera Lime", "Anel Íris", "Esfera Chrome");
        assertThat(nomes(produtos().buscar(criterios(null, Set.of(), null, null, true, Ordem.MAIOR_PRECO))))
                .containsExactly("Esfera Chrome", "Anel Íris", "Esfera Lime");
    }

    @Test
    void pagina() {
        vitrine();

        Pagina<Produto> segunda = produtos().buscar(new Criterios(null, Set.of(), null, null, true, Ordem.NOME, 1, 2));

        assertThat(nomes(segunda)).containsExactly("Esfera Lime");
        assertThat(segunda.total()).isEqualTo(3);
        assertThat(segunda.totalPaginas()).isEqualTo(2);
        assertThat(segunda.pagina()).isEqualTo(1);
    }

    /** Três ativos e um inativo. "aço" só aparece na descrição da Esfera Chrome. */
    private void vitrine() {
        produto("ESF-CHROME", "Esfera Chrome", "289.00", esferas, true, "Esfera de aço polido");
        produto("ESF-LIME", "Esfera Lime", "199.00", esferas, true);
        produto("ANL-IRIS", "Anel Íris", "219.00", aneis, true);
        produto("ANL-CLAY", "Anel Clay", "99.00", aneis, false);
    }

    private Produto produto(String sku, String nome, String preco, CategoriaId categoria, boolean ativo) {
        return produto(sku, nome, preco, categoria, ativo, "");
    }

    private Produto produto(String sku, String nome, String preco, CategoriaId categoria, boolean ativo,
                            String descricao) {
        Produto p = Produto.criar(new Sku(sku), nome, null, descricao, Dinheiro.de(preco), List.of(), categoria);
        if (!ativo) {
            p.desativar();
        }
        return salvar(p);
    }

    private Produto salvar(Produto p) {
        produtos().salvar(p);
        return p;
    }

    private CategoriaId categoria(String nome) {
        Categoria c = Categoria.criar(nome, null, null);
        categorias().salvar(c);
        return c.id();
    }

    private static Criterios criterios(String texto, Set<CategoriaId> categorias, Dinheiro min, Dinheiro max,
                                       boolean somenteAtivos, Ordem ordem) {
        return new Criterios(texto, categorias, min, max, somenteAtivos, ordem, 0, 24);
    }

    private static List<String> nomes(Pagina<Produto> pagina) {
        return pagina.itens().stream().map(Produto::nome).toList();
    }
}
