package com.ruandiego.mini.catalogo.application.service;

import com.ruandiego.mini.catalogo.application.NaoEncontradoException;
import com.ruandiego.mini.catalogo.application.Pagina;
import com.ruandiego.mini.catalogo.application.memoria.CategoriaRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.ProdutoRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.application.port.in.BuscarProdutosUseCase.Filtro;
import com.ruandiego.mini.catalogo.application.port.in.BuscarProdutosUseCase.Ordem;
import com.ruandiego.mini.catalogo.application.port.in.ListarProdutosAdminUseCase;
import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultaCatalogoServiceTest {

    private final ProdutoRepositoryEmMemoria produtos = new ProdutoRepositoryEmMemoria();
    private final CategoriaRepositoryEmMemoria categorias = new CategoriaRepositoryEmMemoria();
    private final ConsultaCatalogoService service = new ConsultaCatalogoService(produtos, categorias);

    private Categoria objetos;
    private Categoria aneis;
    private Categoria blocos;
    private Categoria esferas;

    @BeforeEach
    void catalogo() {
        // Objetos > Anéis, Objetos > Blocos; Esferas é raiz.
        objetos = salvar(Categoria.criar("Objetos", null, null));
        aneis = salvar(Categoria.criar("Anéis", null, objetos.id()));
        blocos = salvar(Categoria.criar("Blocos", null, objetos.id()));
        esferas = salvar(Categoria.criar("Esferas", null, null));

        produto("ESF-CHROME", "Esfera Chrome", "289.00", esferas.id(), true);
        produto("ESF-LIME", "Esfera Lime", "199.00", esferas.id(), true);
        produto("ANL-IRIS", "Anel Íris", "219.00", aneis.id(), true);
        produto("BLC-INK", "Bloco Ink", "149.00", blocos.id(), true);
        produto("ANL-CLAY", "Anel Clay", "99.00", aneis.id(), false); // inativo
    }

    @Nested
    class ListarParaAdmin {

        @Test
        void trazTambemOsDesativadosEmOrdemAlfabetica() {
            Pagina<Produto> pagina = service.listarParaAdmin(new ListarProdutosAdminUseCase.Filtro(null, null, null));

            assertThat(nomes(pagina)).containsExactly("Anel Clay", "Anel Íris", "Bloco Ink", "Esfera Chrome", "Esfera Lime");
            assertThat(pagina.itens()).filteredOn(p -> !p.ativo()).extracting(Produto::nome).containsExactly("Anel Clay");
        }

        @Test
        void buscaPeloSku() {
            Pagina<Produto> pagina = service.listarParaAdmin(new ListarProdutosAdminUseCase.Filtro("anl-", null, null));

            assertThat(nomes(pagina)).containsExactly("Anel Clay", "Anel Íris");
        }

        @Test
        void paginaComOTamanhoPedido() {
            Pagina<Produto> segunda = service.listarParaAdmin(new ListarProdutosAdminUseCase.Filtro(null, 1, 2));

            assertThat(nomes(segunda)).containsExactly("Bloco Ink", "Esfera Chrome");
            assertThat(segunda.total()).isEqualTo(5);
        }

        @Test
        void tamanhoForaDoLimiteEhRecusado() {
            assertThatThrownBy(() -> new ListarProdutosAdminUseCase.Filtro(null, 0, 101))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> new ListarProdutosAdminUseCase.Filtro(null, -1, 10))
                    .isInstanceOf(RegraDeNegocioException.class);
        }
    }

    @Nested
    class Buscar {

        @Test
        void semFiltroTrazSoAtivosEmOrdemAlfabetica() {
            Pagina<Produto> pagina = service.buscar(Filtro.vazio());

            assertThat(nomes(pagina)).containsExactly("Anel Íris", "Bloco Ink", "Esfera Chrome", "Esfera Lime");
            assertThat(pagina.total()).isEqualTo(4);
        }

        @Test
        void filtroPorCategoriaIncluiSubcategorias() {
            assertThat(nomes(service.buscar(filtroCategoria("objetos")))).containsExactly("Anel Íris", "Bloco Ink");
            assertThat(nomes(service.buscar(filtroCategoria("aneis")))).containsExactly("Anel Íris");
        }

        @Test
        void categoriaInexistenteDevolveVitrineVazia() {
            Pagina<Produto> pagina = service.buscar(filtroCategoria("nao-existe"));

            assertThat(pagina.itens()).isEmpty();
            assertThat(pagina.total()).isZero();
        }

        @Test
        void textoIgnoraMaiusculasEAcentos() {
            Filtro filtro = new Filtro("  IRIS ", null, null, null, null, null, null);

            assertThat(nomes(service.buscar(filtro))).containsExactly("Anel Íris");
        }

        @Test
        void faixaDePrecoEOrdemPorPreco() {
            Filtro filtro = new Filtro(null, null, Dinheiro.de("150.00"), Dinheiro.de("250.00"), Ordem.MAIOR_PRECO,
                    null, null);

            assertThat(nomes(service.buscar(filtro))).containsExactly("Anel Íris", "Esfera Lime");
        }

        @Test
        void paginacao() {
            Filtro segunda = new Filtro(null, null, null, null, Ordem.MENOR_PRECO, 1, 3);

            Pagina<Produto> pagina = service.buscar(segunda);

            assertThat(nomes(pagina)).containsExactly("Esfera Chrome");
            assertThat(pagina.total()).isEqualTo(4);
            assertThat(pagina.totalPaginas()).isEqualTo(2);
        }

        @Test
        void filtroInvalidoEhRecusado() {
            assertThatThrownBy(() -> new Filtro(null, null, null, null, null, -1, null))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> new Filtro(null, null, null, null, null, null, 101))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> new Filtro(null, null, Dinheiro.de("10"), Dinheiro.de("5"), null, null, null))
                    .isInstanceOf(RegraDeNegocioException.class);
        }
    }

    @Nested
    class Obter {

        @Test
        void obtemProdutoAtivoPeloSlug() {
            assertThat(service.obterPorSlug(new Slug("esfera-chrome")).nome()).isEqualTo("Esfera Chrome");
        }

        @Test
        void obtemProdutoAtivoPeloId() {
            Produto chrome = service.obterPorSlug(new Slug("esfera-chrome"));

            assertThat(service.obterPorId(chrome.id()).nome()).isEqualTo("Esfera Chrome");
        }

        @Test
        void produtoInativoOuDesconhecidoNaoEhEncontradoPeloId() {
            Produto clay = produtos.buscarPorSlug(new Slug("anel-clay")).orElseThrow();

            assertThatThrownBy(() -> service.obterPorId(clay.id())).isInstanceOf(NaoEncontradoException.class);
            assertThatThrownBy(() -> service.obterPorId(ProdutoId.novo())).isInstanceOf(NaoEncontradoException.class);
        }

        @Test
        void produtoInativoOuInexistenteNaoEhEncontrado() {
            assertThatThrownBy(() -> service.obterPorSlug(new Slug("anel-clay"))).isInstanceOf(NaoEncontradoException.class);
            assertThatThrownBy(() -> service.obterPorSlug(new Slug("nada"))).isInstanceOf(NaoEncontradoException.class);
        }
    }

    @Test
    void listaCategoriasEmOrdemAlfabeticaComAcento() {
        assertThat(service.listar()).extracting(Categoria::nome).containsExactly("Anéis", "Blocos", "Esferas", "Objetos");
    }

    private Filtro filtroCategoria(String slug) {
        return new Filtro(null, new Slug(slug), null, null, null, null, null);
    }

    private static List<String> nomes(Pagina<Produto> pagina) {
        return pagina.itens().stream().map(Produto::nome).toList();
    }

    private Categoria salvar(Categoria categoria) {
        categorias.salvar(categoria);
        return categoria;
    }

    private void produto(String sku, String nome, String preco, CategoriaId categoria, boolean ativo) {
        Produto produto = Produto.criar(new Sku(sku), nome, null, "", Dinheiro.de(preco), List.of(), categoria);
        if (!ativo) {
            produto.desativar();
        }
        produtos.salvar(produto);
    }
}
