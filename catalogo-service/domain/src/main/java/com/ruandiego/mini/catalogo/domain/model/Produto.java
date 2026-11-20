package com.ruandiego.mini.catalogo.domain.model;

import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import com.ruandiego.mini.catalogo.domain.event.PrecoAlterado;
import com.ruandiego.mini.kernel.AggregateRoot;
import com.ruandiego.mini.kernel.Dinheiro;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Produto da loja: raiz de agregado do catálogo.
 *
 * Regras que vivem aqui (e não no use case nem no banco):
 *  - preço sempre maior que zero;
 *  - produto inativo fica fora da vitrine;
 *  - toda mudança de preço registra PrecoAlterado.
 *
 * O estado só muda por métodos com nome de negócio; não há setters.
 */
public class Produto extends AggregateRoot {

    private static final int NOME_MAXIMO = 120;
    private static final int DESCRICAO_MAXIMA = 2000;
    /** Quantas imagens um produto pode ter (cadastro, atualização ou upload). */
    public static final int IMAGENS_MAXIMO = 5;

    private final ProdutoId id;
    private final Sku sku;
    private String nome;
    private Slug slug;
    private String descricao;
    private Dinheiro preco;
    private List<Imagem> imagens;
    private CategoriaId categoriaId;
    private boolean ativo;

    private Produto(ProdutoId id, Sku sku, String nome, Slug slug, String descricao, Dinheiro preco,
                    List<Imagem> imagens, CategoriaId categoriaId, boolean ativo) {
        this.id = Objects.requireNonNull(id, "id");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.nome = validarNome(nome);
        this.slug = Objects.requireNonNull(slug, "slug");
        this.descricao = validarDescricao(descricao);
        this.preco = validarPreco(preco);
        this.imagens = validarImagens(imagens);
        this.categoriaId = Objects.requireNonNull(categoriaId, "categoriaId");
        this.ativo = ativo;
    }

    /**
     * Cadastra um produto novo, já ativo na vitrine. Sem slug, ele é gerado a partir do nome.
     * Não registra PrecoAlterado: não existe preço anterior.
     */
    public static Produto criar(Sku sku, String nome, Slug slug, String descricao, Dinheiro preco,
                                List<Imagem> imagens, CategoriaId categoriaId) {
        return new Produto(ProdutoId.novo(), sku, nome, slug != null ? slug : Slug.gerarDe(nome), descricao, preco,
                imagens, categoriaId, true);
    }

    /** Recria um produto já existente (usado pelo adapter de persistência). Não registra eventos. */
    public static Produto reconstituir(ProdutoId id, Sku sku, String nome, Slug slug, String descricao,
                                       Dinheiro preco, List<Imagem> imagens, CategoriaId categoriaId, boolean ativo) {
        return new Produto(id, sku, nome, slug, descricao, preco, imagens, categoriaId, ativo);
    }

    /**
     * Muda o preço e registra PrecoAlterado. O instante vem de fora (o use case passa o relógio),
     * para o domínio não depender da hora do sistema e os testes serem determinísticos.
     * Mesmo preço não é mudança: nada é registrado.
     */
    public void alterarPreco(Dinheiro novoPreco, Instant agora) {
        Objects.requireNonNull(agora, "agora");
        Dinheiro validado = validarPreco(novoPreco);
        if (validado.equals(preco)) {
            return;
        }
        Dinheiro anterior = preco;
        this.preco = validado;
        registrar(new PrecoAlterado(id, sku, anterior, validado, agora));
    }

    /** Atualiza os dados descritivos. Preço tem método próprio porque gera evento. */
    public void atualizarDados(String novoNome, String novaDescricao, List<Imagem> novasImagens,
                               CategoriaId novaCategoriaId) {
        this.nome = validarNome(novoNome);
        this.descricao = validarDescricao(novaDescricao);
        this.imagens = validarImagens(novasImagens);
        this.categoriaId = Objects.requireNonNull(novaCategoriaId, "novaCategoriaId");
    }

    /** Acrescenta uma imagem no fim da galeria, respeitando o limite de {@link #IMAGENS_MAXIMO}. */
    public void adicionarImagem(Imagem imagem) {
        Objects.requireNonNull(imagem, "imagem");
        if (!aceitaMaisImagens()) {
            throw new RegraDeNegocioException("produto aceita até " + IMAGENS_MAXIMO + " imagens");
        }
        List<Imagem> novas = new java.util.ArrayList<>(imagens);
        novas.add(imagem);
        this.imagens = List.copyOf(novas);
    }

    public boolean aceitaMaisImagens() {
        return imagens.size() < IMAGENS_MAXIMO;
    }

    public void alterarSlug(Slug novoSlug) {
        this.slug = Objects.requireNonNull(novoSlug, "novoSlug");
    }

    public void ativar() {
        this.ativo = true;
    }

    public void desativar() {
        this.ativo = false;
    }

    /** Regra da vitrine: só produto ativo aparece para o cliente. */
    public boolean visivelNaVitrine() {
        return ativo;
    }

    private static String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("nome do produto é obrigatório");
        }
        String limpo = nome.trim();
        if (limpo.length() > NOME_MAXIMO) {
            throw new RegraDeNegocioException("nome do produto deve ter até " + NOME_MAXIMO + " caracteres");
        }
        return limpo;
    }

    private static String validarDescricao(String descricao) {
        String limpa = descricao == null ? "" : descricao.trim();
        if (limpa.length() > DESCRICAO_MAXIMA) {
            throw new RegraDeNegocioException("descrição deve ter até " + DESCRICAO_MAXIMA + " caracteres");
        }
        return limpa;
    }

    private static Dinheiro validarPreco(Dinheiro preco) {
        if (preco == null || !preco.ehPositivo()) {
            throw new RegraDeNegocioException("preço deve ser maior que zero: " + preco);
        }
        return preco;
    }

    private static List<Imagem> validarImagens(List<Imagem> imagens) {
        List<Imagem> copia = imagens == null ? List.of() : List.copyOf(imagens);
        if (copia.size() > IMAGENS_MAXIMO) {
            throw new RegraDeNegocioException("produto aceita até " + IMAGENS_MAXIMO + " imagens");
        }
        return copia;
    }

    public ProdutoId id() {
        return id;
    }

    public Sku sku() {
        return sku;
    }

    public String nome() {
        return nome;
    }

    public Slug slug() {
        return slug;
    }

    public String descricao() {
        return descricao;
    }

    public Dinheiro preco() {
        return preco;
    }

    /** Lista imutável. */
    public List<Imagem> imagens() {
        return imagens;
    }

    public CategoriaId categoriaId() {
        return categoriaId;
    }

    public boolean ativo() {
        return ativo;
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Produto p && id.equals(p.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
