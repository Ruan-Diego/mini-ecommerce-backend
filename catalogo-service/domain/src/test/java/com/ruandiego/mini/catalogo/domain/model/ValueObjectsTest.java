package com.ruandiego.mini.catalogo.domain.model;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @Nested
    class SkuTest {

        @Test
        void normalizaParaMaiusculasSemEspacos() {
            assertThat(new Sku("  esf-chrome-01 ").valor()).isEqualTo("ESF-CHROME-01");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "AB", "ESF CHROME", "ESF--01", "-ESF", "ESF-", "ESF_01", "ÇÃO-1",
                "A23456789012345678901234567890123"})
        void rejeitaFormatoInvalido(String valor) {
            assertThatThrownBy(() -> new Sku(valor)).isInstanceOf(RegraDeNegocioException.class);
        }

        @Test
        void doisSkusComOMesmoValorSaoIguais() {
            assertThat(new Sku("anel-01")).isEqualTo(new Sku("ANEL-01"));
        }
    }

    @Nested
    class SlugTest {

        @Test
        void aceitaSlugValido() {
            assertThat(new Slug("esfera-chrome-2").valor()).isEqualTo("esfera-chrome-2");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"Esfera", "esfera chrome", "esfera--chrome", "-esfera", "esfera-", "cápsula"})
        void rejeitaFormatoInvalido(String valor) {
            assertThatThrownBy(() -> new Slug(valor)).isInstanceOf(RegraDeNegocioException.class);
        }

        @Test
        void geraDeTextoLivreRemovendoAcentosESimbolos() {
            assertThat(Slug.gerarDe("  Anel Íris — Duplo! ").valor()).isEqualTo("anel-iris-duplo");
            assertThat(Slug.gerarDe("Cápsula Âmbar 2.0").valor()).isEqualTo("capsula-ambar-2-0");
        }

        @Test
        void cortaEm80CaracteresSemTerminarEmHifen() {
            String longo = "a".repeat(79) + " bbbbb";
            Slug slug = Slug.gerarDe(longo);
            assertThat(slug.valor()).hasSizeLessThanOrEqualTo(80).doesNotEndWith("-");
        }

        @Test
        void naoGeraDeTextoSemLetrasNemNumeros() {
            assertThatThrownBy(() -> Slug.gerarDe("!!!")).isInstanceOf(RegraDeNegocioException.class);
            assertThatThrownBy(() -> Slug.gerarDe(" ")).isInstanceOf(RegraDeNegocioException.class);
        }
    }

    @Nested
    class ImagemTest {

        @Test
        void exigeUrlHttpEAltText() {
            Imagem imagem = new Imagem(" https://cdn.mini.dev/esfera.webp ", " Esfera cromada sobre fundo escuro ");
            assertThat(imagem.url()).isEqualTo("https://cdn.mini.dev/esfera.webp");
            assertThat(imagem.textoAlternativo()).isEqualTo("Esfera cromada sobre fundo escuro");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"cdn.mini.dev/esfera.webp", "ftp://cdn.mini.dev/a.webp", "javascript:alert(1)", "http://"})
        void rejeitaUrlInvalida(String url) {
            assertThatThrownBy(() -> new Imagem(url, "Esfera")).isInstanceOf(RegraDeNegocioException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void rejeitaImagemSemTextoAlternativo(String alt) {
            assertThatThrownBy(() -> new Imagem("https://cdn.mini.dev/esfera.webp", alt))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("texto alternativo");
        }
    }
}
