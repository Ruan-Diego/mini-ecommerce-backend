package com.ruandiego.mini.catalogo.application.contrato;

import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato do port CategoriaRepository: todo adapter (em memória, JPA) estende esta classe e
 * precisa passar nos mesmos cenários.
 */
public abstract class CategoriaRepositoryContrato {

    protected abstract CategoriaRepository categorias();

    @Test
    void salvaEBuscaPorIdESlug() {
        Categoria objetos = salvar(Categoria.criar("Objetos", null, null));
        Categoria aneis = salvar(Categoria.criar("Anéis", null, objetos.id()));

        Categoria lida = categorias().buscarPorId(aneis.id()).orElseThrow();
        assertThat(lida.nome()).isEqualTo("Anéis");
        assertThat(lida.slug()).isEqualTo(new Slug("aneis"));
        assertThat(lida.paiId()).contains(objetos.id());
        assertThat(categorias().buscarPorSlug(new Slug("objetos")).orElseThrow().ehRaiz()).isTrue();
    }

    @Test
    void naoEncontradoViraOptionalVazio() {
        assertThat(categorias().buscarPorId(CategoriaId.novo())).isEmpty();
        assertThat(categorias().buscarPorSlug(new Slug("nao-existe"))).isEmpty();
    }

    @Test
    void salvarDeNovoAtualiza() {
        Categoria objetos = salvar(Categoria.criar("Objetos", null, null));
        Categoria esferas = salvar(Categoria.criar("Esferas", null, objetos.id()));

        esferas.renomear("Esferas de vidro");
        esferas.moverPara(null);
        salvar(esferas);

        Categoria lida = categorias().buscarPorId(esferas.id()).orElseThrow();
        assertThat(lida.nome()).isEqualTo("Esferas de vidro");
        assertThat(lida.ehRaiz()).isTrue();
        assertThat(categorias().listarTodas()).hasSize(2);
    }

    @Test
    void listaTodas() {
        salvar(Categoria.criar("Objetos", null, null));
        salvar(Categoria.criar("Esferas", null, null));

        assertThat(categorias().listarTodas()).extracting(Categoria::nome)
                .containsExactlyInAnyOrder("Objetos", "Esferas");
    }

    private Categoria salvar(Categoria categoria) {
        categorias().salvar(categoria);
        return categoria;
    }
}
