package com.ruandiego.mini.catalogo.domain.model;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import com.ruandiego.mini.catalogo.domain.event.PrecoAlterado;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdutoTest {

    private static final Instant AGORA = Instant.parse("2026-11-18T15:00:00Z");
    private static final CategoriaId ESFERAS = CategoriaId.novo();
    private static final Imagem FOTO = new Imagem("https://cdn.mini.dev/esfera.webp", "Esfera cromada");

    private static Produto esferaChrome() {
        return Produto.criar(new Sku("ESF-CHROME-01"), "Esfera Chrome", null, "Esfera de aço polido.",
                Dinheiro.de("289.00"), List.of(FOTO), ESFERAS);
    }

    @Nested
    class Criacao {

        @Test
        void criaProdutoAtivoComSlugGeradoESemEventos() {
            Produto produto = esferaChrome();

            assertThat(produto.id()).isNotNull();
            assertThat(produto.slug()).isEqualTo(new Slug("esfera-chrome"));
            assertThat(produto.preco()).isEqualTo(Dinheiro.de("289.00"));
            assertThat(produto.ativo()).isTrue();
            assertThat(produto.visivelNaVitrine()).isTrue();
            assertThat(produto.eventosPendentes()).isEmpty();
        }

        @Test
        void usaSlugInformadoELimpaTextos() {
            Produto produto = Produto.criar(new Sku("anel-iris"), "  Anel Íris  ", new Slug("anel-iris-edicao"),
                    null, Dinheiro.de("219.00"), null, ESFERAS);

            assertThat(produto.slug().valor()).isEqualTo("anel-iris-edicao");
            assertThat(produto.nome()).isEqualTo("Anel Íris");
            assertThat(produto.descricao()).isEmpty();
            assertThat(produto.imagens()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.00", "-1.00", "0.004"})
        void precoPrecisaSerMaiorQueZero(String preco) {
            assertThatThrownBy(() -> Produto.criar(new Sku("ESF-01"), "Esfera", null, "", Dinheiro.de(preco),
                    List.of(), ESFERAS))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("maior que zero");
        }

        @Test
        void validaNomeDescricaoImagensECategoria() {
            Sku sku = new Sku("ESF-01");
            Dinheiro preco = Dinheiro.de("10.00");
            assertThatThrownBy(() -> Produto.criar(sku, " ", new Slug("x"), "", preco, List.of(), ESFERAS))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> Produto.criar(sku, "x".repeat(121), new Slug("x"), "", preco, List.of(), ESFERAS))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> Produto.criar(sku, "Esfera", null, "d".repeat(2001), preco, List.of(), ESFERAS))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> Produto.criar(sku, "Esfera", null, "", preco, Collections.nCopies(11, FOTO), ESFERAS))
                    .isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> Produto.criar(sku, "Esfera", null, "", preco, List.of(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void imagensSaoUmaCopiaImutavel() {
            List<Imagem> imagens = new ArrayList<>(List.of(FOTO));
            Produto produto = Produto.criar(new Sku("ESF-01"), "Esfera", null, "", Dinheiro.de("10.00"), imagens, ESFERAS);

            imagens.clear();

            assertThat(produto.imagens()).containsExactly(FOTO);
            assertThatThrownBy(() -> produto.imagens().add(FOTO)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class AlteracaoDePreco {

        @Test
        void registraPrecoAlteradoComPrecoAnteriorENovo() {
            Produto produto = esferaChrome();

            produto.alterarPreco(Dinheiro.de("259.90"), AGORA);

            assertThat(produto.preco()).isEqualTo(Dinheiro.de("259.90"));
            assertThat(produto.eventosPendentes()).containsExactly(new PrecoAlterado(
                    produto.id(), new Sku("ESF-CHROME-01"), Dinheiro.de("289.00"), Dinheiro.de("259.90"), AGORA));
            assertThat(produto.eventosPendentes().getFirst().tipo()).isEqualTo("catalogo.preco-alterado");
        }

        @Test
        void cadaAlteracaoRegistraUmEvento() {
            Produto produto = esferaChrome();

            produto.alterarPreco(Dinheiro.de("259.90"), AGORA);
            produto.alterarPreco(Dinheiro.de("299.00"), AGORA.plusSeconds(60));

            assertThat(produto.eventosPendentes()).hasSize(2);
            PrecoAlterado segundo = (PrecoAlterado) produto.eventosPendentes().get(1);
            assertThat(segundo.precoAnterior()).isEqualTo(Dinheiro.de("259.90"));
            assertThat(segundo.precoNovo()).isEqualTo(Dinheiro.de("299.00"));
        }

        @Test
        void mesmoPrecoNaoEhMudanca() {
            Produto produto = esferaChrome();

            produto.alterarPreco(Dinheiro.de("289"), AGORA);

            assertThat(produto.eventosPendentes()).isEmpty();
        }

        @Test
        void precoNovoInvalidoNaoMudaNadaNemRegistraEvento() {
            Produto produto = esferaChrome();

            assertThatThrownBy(() -> produto.alterarPreco(Dinheiro.ZERO, AGORA)).isInstanceOf(RegraDeNegocioException.class);

            assertThat(produto.preco()).isEqualTo(Dinheiro.de("289.00"));
            assertThat(produto.eventosPendentes()).isEmpty();
        }

        @Test
        void eventosSaemDepoisDeLimpos() {
            Produto produto = esferaChrome();
            produto.alterarPreco(Dinheiro.de("199.00"), AGORA);

            produto.limparEventos();

            assertThat(produto.eventosPendentes()).isEmpty();
        }
    }

    @Nested
    class Vitrine {

        @Test
        void produtoInativoFicaForaDaVitrine() {
            Produto produto = esferaChrome();

            produto.desativar();
            assertThat(produto.visivelNaVitrine()).isFalse();

            produto.ativar();
            assertThat(produto.visivelNaVitrine()).isTrue();
        }

        @Test
        void reconstituirRespeitaOEstadoSalvoSemRegistrarEventos() {
            Produto original = esferaChrome();

            Produto salvo = Produto.reconstituir(original.id(), original.sku(), original.nome(), original.slug(),
                    original.descricao(), original.preco(), original.imagens(), original.categoriaId(), false);

            assertThat(salvo).isEqualTo(original);
            assertThat(salvo.visivelNaVitrine()).isFalse();
            assertThat(salvo.eventosPendentes()).isEmpty();
        }
    }

    @Test
    void atualizaDadosDescritivosSemMexerNoPreco() {
        Produto produto = esferaChrome();
        CategoriaId aneis = CategoriaId.novo();

        produto.atualizarDados("Esfera Chrome XL", "Maior.", List.of(), aneis);

        assertThat(produto.nome()).isEqualTo("Esfera Chrome XL");
        assertThat(produto.categoriaId()).isEqualTo(aneis);
        assertThat(produto.imagens()).isEmpty();
        assertThat(produto.preco()).isEqualTo(Dinheiro.de("289.00"));
        assertThat(produto.eventosPendentes()).isEmpty();
    }
}
