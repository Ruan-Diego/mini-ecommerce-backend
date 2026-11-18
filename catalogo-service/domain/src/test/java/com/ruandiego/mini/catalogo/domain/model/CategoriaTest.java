package com.ruandiego.mini.catalogo.domain.model;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoriaTest {

    @Test
    void criaCategoriaRaizComSlugGeradoDoNome() {
        Categoria esferas = Categoria.criar(" Esferas Cromadas ", null, null);

        assertThat(esferas.nome()).isEqualTo("Esferas Cromadas");
        assertThat(esferas.slug()).isEqualTo(new Slug("esferas-cromadas"));
        assertThat(esferas.ehRaiz()).isTrue();
        assertThat(esferas.paiId()).isEmpty();
    }

    @Test
    void criaSubcategoriaComSlugInformado() {
        Categoria objetos = Categoria.criar("Objetos", null, null);
        Categoria aneis = Categoria.criar("Anéis", new Slug("aneis"), objetos.id());

        assertThat(aneis.paiId()).contains(objetos.id());
        assertThat(aneis.ehRaiz()).isFalse();
        assertThat(aneis.slug().valor()).isEqualTo("aneis");
    }

    @Test
    void naoPodeSerPaiDeSiMesma() {
        Categoria esferas = Categoria.criar("Esferas", null, null);

        assertThatThrownBy(() -> esferas.moverPara(esferas.id()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("pai de si mesma");
        assertThatThrownBy(() -> Categoria.reconstituir(esferas.id(), "Esferas", esferas.slug(), esferas.id()))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void moveParaOutroPaiEVoltaParaRaiz() {
        Categoria objetos = Categoria.criar("Objetos", null, null);
        Categoria blocos = Categoria.criar("Blocos", null, null);

        blocos.moverPara(objetos.id());
        assertThat(blocos.paiId()).contains(objetos.id());

        blocos.moverPara(null);
        assertThat(blocos.ehRaiz()).isTrue();
    }

    @Test
    void validaNome() {
        assertThatThrownBy(() -> Categoria.criar(" ", new Slug("vazia"), null)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> Categoria.criar("x".repeat(61), new Slug("longa"), null))
                .isInstanceOf(RegraDeNegocioException.class);

        Categoria capsulas = Categoria.criar("Cápsulas", null, null);
        capsulas.renomear("  Cápsulas de vidro ");
        assertThat(capsulas.nome()).isEqualTo("Cápsulas de vidro");
    }

    @Test
    void igualdadePelaIdentidade() {
        Categoria original = Categoria.criar("Esferas", null, null);
        Categoria mesmaIdOutroNome = Categoria.reconstituir(original.id(), "Outro nome", new Slug("outro"), null);

        assertThat(mesmaIdOutroNome).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(Categoria.criar("Esferas", null, null)).isNotEqualTo(original);
    }
}
