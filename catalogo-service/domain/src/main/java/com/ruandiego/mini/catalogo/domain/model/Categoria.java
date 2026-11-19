package com.ruandiego.mini.catalogo.domain.model;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import com.ruandiego.mini.kernel.AggregateRoot;

import java.util.Objects;
import java.util.Optional;

/**
 * Categoria da vitrine (ex.: Esferas, Anéis). Pode ter uma categoria pai, formando uma árvore.
 *
 * Guarda só o CategoriaId do pai, não o objeto: cada categoria é um agregado próprio. Por isso o
 * domínio só impede que a categoria seja pai de si mesma; ciclos mais longos (A → B → A) exigem
 * olhar outras categorias e são checados no use case, que tem acesso ao repositório.
 */
public class Categoria extends AggregateRoot {

    private static final int NOME_MAXIMO = 60;

    private final CategoriaId id;
    private String nome;
    private Slug slug;
    private CategoriaId paiId;

    private Categoria(CategoriaId id, String nome, Slug slug, CategoriaId paiId) {
        this.id = Objects.requireNonNull(id, "id");
        this.nome = validarNome(nome);
        this.slug = Objects.requireNonNull(slug, "slug");
        this.paiId = paiId;
        garantirQueNaoEhPaiDeSiMesma(paiId);
    }

    /** Nova categoria. Sem slug, ele é gerado a partir do nome. Pai nulo = categoria raiz. */
    public static Categoria criar(String nome, Slug slug, CategoriaId paiId) {
        return new Categoria(CategoriaId.novo(), nome, slug != null ? slug : Slug.gerarDe(nome), paiId);
    }

    /** Recria uma categoria já existente (usado pelo adapter de persistência). */
    public static Categoria reconstituir(CategoriaId id, String nome, Slug slug, CategoriaId paiId) {
        return new Categoria(id, nome, slug, paiId);
    }

    public void renomear(String novoNome) {
        this.nome = validarNome(novoNome);
    }

    public void alterarSlug(Slug novoSlug) {
        this.slug = Objects.requireNonNull(novoSlug, "novoSlug");
    }

    /** Muda o pai. Nulo transforma em categoria raiz. */
    public void moverPara(CategoriaId novoPaiId) {
        garantirQueNaoEhPaiDeSiMesma(novoPaiId);
        this.paiId = novoPaiId;
    }

    public boolean ehRaiz() {
        return paiId == null;
    }

    private void garantirQueNaoEhPaiDeSiMesma(CategoriaId candidatoAPai) {
        if (id.equals(candidatoAPai)) {
            throw new RegraDeNegocioException("uma categoria não pode ser pai de si mesma");
        }
    }

    private static String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("nome da categoria é obrigatório");
        }
        String limpo = nome.trim();
        if (limpo.length() > NOME_MAXIMO) {
            throw new RegraDeNegocioException("nome da categoria deve ter até " + NOME_MAXIMO + " caracteres");
        }
        return limpo;
    }

    public CategoriaId id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public Slug slug() {
        return slug;
    }

    public Optional<CategoriaId> paiId() {
        return Optional.ofNullable(paiId);
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Categoria c && id.equals(c.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
