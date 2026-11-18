package com.ruandiego.mini.catalogo.application.memoria;

import com.ruandiego.mini.catalogo.application.Pagina;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Adapter em memória do ProdutoRepository, para testes. Mesmo contrato do adapter JPA.
 * Guarda cópias (como um banco): mudar o objeto depois de salvar não muda o que está guardado.
 */
public class ProdutoRepositoryEmMemoria implements ProdutoRepository {

    private static final Collator ORDEM_ALFABETICA = Collator.getInstance(Locale.of("pt", "BR"));

    private final Map<ProdutoId, Produto> dados = new LinkedHashMap<>();

    @Override
    public void salvar(Produto produto) {
        dados.put(produto.id(), copiar(produto));
    }

    @Override
    public Optional<Produto> buscarPorId(ProdutoId id) {
        return Optional.ofNullable(dados.get(id)).map(ProdutoRepositoryEmMemoria::copiar);
    }

    @Override
    public Optional<Produto> buscarPorSlug(Slug slug) {
        return dados.values().stream().filter(p -> p.slug().equals(slug)).findFirst().map(ProdutoRepositoryEmMemoria::copiar);
    }

    @Override
    public boolean existeComSku(Sku sku) {
        return dados.values().stream().anyMatch(p -> p.sku().equals(sku));
    }

    @Override
    public boolean existeComSlug(Slug slug) {
        return dados.values().stream().anyMatch(p -> p.slug().equals(slug));
    }

    @Override
    public Pagina<Produto> buscar(Criterios c) {
        Predicate<Produto> filtro = p -> (!c.somenteAtivos() || p.visivelNaVitrine())
                && (c.categorias().isEmpty() || c.categorias().contains(p.categoriaId()))
                && (c.precoMin() == null || p.preco().compareTo(c.precoMin()) >= 0)
                && (c.precoMax() == null || p.preco().compareTo(c.precoMax()) <= 0)
                && (c.texto() == null || contemTexto(p, c.texto()));

        List<Produto> encontrados = dados.values().stream().filter(filtro).sorted(ordenacao(c)).toList();
        List<Produto> pagina = encontrados.stream()
                .skip((long) c.pagina() * c.tamanho())
                .limit(c.tamanho())
                .map(ProdutoRepositoryEmMemoria::copiar)
                .toList();
        return new Pagina<>(pagina, c.pagina(), c.tamanho(), encontrados.size());
    }

    public int quantidade() {
        return dados.size();
    }

    private static Comparator<Produto> ordenacao(Criterios c) {
        Comparator<Produto> porNome = Comparator.comparing(Produto::nome, ORDEM_ALFABETICA);
        return switch (c.ordem()) {
            case NOME -> porNome;
            case MENOR_PRECO -> Comparator.comparing(Produto::preco).thenComparing(porNome);
            case MAIOR_PRECO -> Comparator.comparing(Produto::preco).reversed().thenComparing(porNome);
        };
    }

    /** Busca sem diferenciar maiúsculas nem acentos, em nome, descrição e SKU. */
    private static boolean contemTexto(Produto p, String texto) {
        String procurado = normalizar(texto);
        return normalizar(p.nome()).contains(procurado)
                || normalizar(p.descricao()).contains(procurado)
                || normalizar(p.sku().valor()).contains(procurado);
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static Produto copiar(Produto p) {
        return Produto.reconstituir(p.id(), p.sku(), p.nome(), p.slug(), p.descricao(), p.preco(), p.imagens(),
                p.categoriaId(), p.ativo());
    }
}
