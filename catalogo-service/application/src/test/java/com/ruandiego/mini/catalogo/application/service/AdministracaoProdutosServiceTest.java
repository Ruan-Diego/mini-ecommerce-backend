package com.ruandiego.mini.catalogo.application.service;

import com.ruandiego.mini.catalogo.application.ConflitoException;
import com.ruandiego.mini.catalogo.application.NaoEncontradoException;
import com.ruandiego.mini.catalogo.application.memoria.CategoriaRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.ProdutoRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.PublicadorEventosEmMemoria;
import com.ruandiego.mini.catalogo.application.port.in.AtualizarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.CadastrarProdutoUseCase;
import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import com.ruandiego.mini.catalogo.domain.event.PrecoAlterado;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdministracaoProdutosServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-18T15:00:00Z");
    private static final Imagem FOTO = new Imagem("https://cdn.mini.dev/esfera.webp", "Esfera cromada");

    private final ProdutoRepositoryEmMemoria produtos = new ProdutoRepositoryEmMemoria();
    private final CategoriaRepositoryEmMemoria categorias = new CategoriaRepositoryEmMemoria();
    private final PublicadorEventosEmMemoria publicador = new PublicadorEventosEmMemoria();
    private final AdministracaoProdutosService service = new AdministracaoProdutosService(
            produtos, categorias, publicador, Clock.fixed(AGORA, ZoneOffset.UTC));

    private final Categoria esferas = salvar(Categoria.criar("Esferas", null, null));

    private CadastrarProdutoUseCase.Comando esferaChrome() {
        return new CadastrarProdutoUseCase.Comando(new Sku("ESF-CHROME"), "Esfera Chrome", null, "Aço polido.",
                Dinheiro.de("289.00"), List.of(FOTO), esferas.id());
    }

    @Nested
    class Cadastrar {

        @Test
        void cadastraESalvaProdutoAtivoComSlugGerado() {
            ProdutoId id = service.cadastrar(esferaChrome());

            Produto salvo = produtos.buscarPorId(id).orElseThrow();
            assertThat(salvo.nome()).isEqualTo("Esfera Chrome");
            assertThat(salvo.slug()).isEqualTo(new Slug("esfera-chrome"));
            assertThat(salvo.visivelNaVitrine()).isTrue();
            assertThat(publicador.publicados()).isEmpty();
        }

        @Test
        void skuDuplicadoEhRecusado() {
            service.cadastrar(esferaChrome());
            CadastrarProdutoUseCase.Comando mesmoSku = new CadastrarProdutoUseCase.Comando(new Sku("esf-chrome"),
                    "Outra esfera", null, "", Dinheiro.de("10.00"), List.of(), esferas.id());

            assertThatThrownBy(() -> service.cadastrar(mesmoSku))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessageContaining("SKU ESF-CHROME")
                    .hasFieldOrPropertyWithValue("campo", "sku");
            assertThat(produtos.quantidade()).isEqualTo(1);
        }

        @Test
        void slugDuplicadoEhRecusado() {
            service.cadastrar(esferaChrome());
            CadastrarProdutoUseCase.Comando mesmoNome = new CadastrarProdutoUseCase.Comando(new Sku("ESF-CHROME-2"),
                    "Esfera Chrome", null, "", Dinheiro.de("10.00"), List.of(), esferas.id());

            assertThatThrownBy(() -> service.cadastrar(mesmoNome))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessageContaining("slug esfera-chrome")
                    .hasFieldOrPropertyWithValue("campo", "slug");
        }

        @Test
        void categoriaPrecisaExistir() {
            CadastrarProdutoUseCase.Comando semCategoria = new CadastrarProdutoUseCase.Comando(new Sku("ESF-01"),
                    "Esfera", null, "", Dinheiro.de("10.00"), List.of(), CategoriaId.novo());

            assertThatThrownBy(() -> service.cadastrar(semCategoria)).isInstanceOf(NaoEncontradoException.class);
            assertThat(produtos.quantidade()).isZero();
        }

        @Test
        void regraDoDominioContinuaValendo() {
            CadastrarProdutoUseCase.Comando precoZero = new CadastrarProdutoUseCase.Comando(new Sku("ESF-01"),
                    "Esfera", null, "", Dinheiro.ZERO, List.of(), esferas.id());

            assertThatThrownBy(() -> service.cadastrar(precoZero)).isInstanceOf(RegraDeNegocioException.class);
            assertThat(produtos.quantidade()).isZero();
        }
    }

    @Nested
    class Atualizar {

        @Test
        void mudancaDePrecoSalvaEPublicaPrecoAlteradoComOInstanteDoRelogio() {
            ProdutoId id = service.cadastrar(esferaChrome());

            service.atualizar(new AtualizarProdutoUseCase.Comando(id, "Esfera Chrome", "Aço polido.",
                    Dinheiro.de("259.90"), List.of(FOTO), esferas.id()));

            assertThat(produtos.buscarPorId(id).orElseThrow().preco()).isEqualTo(Dinheiro.de("259.90"));
            assertThat(publicador.publicados()).containsExactly(new PrecoAlterado(
                    id, new Sku("ESF-CHROME"), Dinheiro.de("289.00"), Dinheiro.de("259.90"), AGORA));
        }

        @Test
        void semMudancaDePrecoNaoPublicaNada() {
            ProdutoId id = service.cadastrar(esferaChrome());
            Categoria aneis = salvar(Categoria.criar("Anéis", null, null));

            service.atualizar(new AtualizarProdutoUseCase.Comando(id, "Esfera Chrome XL", "Maior.",
                    Dinheiro.de("289.00"), List.of(), aneis.id()));

            Produto salvo = produtos.buscarPorId(id).orElseThrow();
            assertThat(salvo.nome()).isEqualTo("Esfera Chrome XL");
            assertThat(salvo.categoriaId()).isEqualTo(aneis.id());
            assertThat(publicador.publicados()).isEmpty();
        }

        @Test
        void produtoOuCategoriaInexistentes() {
            ProdutoId id = service.cadastrar(esferaChrome());

            assertThatThrownBy(() -> service.atualizar(new AtualizarProdutoUseCase.Comando(ProdutoId.novo(), "X", "",
                    Dinheiro.de("1.00"), List.of(), esferas.id()))).isInstanceOf(NaoEncontradoException.class);
            assertThatThrownBy(() -> service.atualizar(new AtualizarProdutoUseCase.Comando(id, "X", "",
                    Dinheiro.de("1.00"), List.of(), CategoriaId.novo()))).isInstanceOf(NaoEncontradoException.class);
        }

        @Test
        void precoInvalidoNaoSalvaNemPublica() {
            ProdutoId id = service.cadastrar(esferaChrome());

            assertThatThrownBy(() -> service.atualizar(new AtualizarProdutoUseCase.Comando(id, "Esfera Chrome", "",
                    Dinheiro.ZERO, List.of(), esferas.id()))).isInstanceOf(RegraDeNegocioException.class);

            assertThat(produtos.buscarPorId(id).orElseThrow().preco()).isEqualTo(Dinheiro.de("289.00"));
            assertThat(publicador.publicados()).isEmpty();
        }
    }

    @Nested
    class Desativar {

        @Test
        void tiraDaVitrineSemApagar() {
            ProdutoId id = service.cadastrar(esferaChrome());

            service.desativar(id);
            service.desativar(id); // idempotente

            assertThat(produtos.buscarPorId(id).orElseThrow().visivelNaVitrine()).isFalse();
            assertThat(produtos.quantidade()).isEqualTo(1);
        }

        @Test
        void produtoInexistente() {
            assertThatThrownBy(() -> service.desativar(ProdutoId.novo())).isInstanceOf(NaoEncontradoException.class);
        }
    }

    private Categoria salvar(Categoria categoria) {
        categorias.salvar(categoria);
        return categoria;
    }
}
