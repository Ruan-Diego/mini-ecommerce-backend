package com.ruandiego.mini.catalogo.application.service;

import com.ruandiego.mini.catalogo.application.ConflitoException;
import com.ruandiego.mini.catalogo.application.NaoEncontradoException;
import com.ruandiego.mini.catalogo.application.port.in.AtualizarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.CadastrarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.DesativarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;
import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Produto;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.DomainEvent;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Casos de uso de administração de produtos.
 *
 * O service orquestra: valida o que depende de outros dados (SKU e slug únicos, categoria existe),
 * delega as regras do produto ao agregado e, depois de salvar, publica os eventos que ele registrou.
 * Não conhece Spring: a transação é amarrada no bootstrap.
 */
public class AdministracaoProdutosService
        implements CadastrarProdutoUseCase, AtualizarProdutoUseCase, DesativarProdutoUseCase {

    private final ProdutoRepository produtos;
    private final CategoriaRepository categorias;
    private final PublicadorEventos publicador;
    private final Clock relogio;

    public AdministracaoProdutosService(ProdutoRepository produtos, CategoriaRepository categorias,
                                        PublicadorEventos publicador, Clock relogio) {
        this.produtos = Objects.requireNonNull(produtos, "produtos");
        this.categorias = Objects.requireNonNull(categorias, "categorias");
        this.publicador = Objects.requireNonNull(publicador, "publicador");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
    }

    @Override
    public ProdutoId cadastrar(CadastrarProdutoUseCase.Comando comando) {
        garantirQueCategoriaExiste(comando.categoriaId());
        // O agregado valida as próprias regras (preço, nome, imagens) e gera o slug se faltar.
        Produto produto = Produto.criar(comando.sku(), comando.nome(), comando.slug(), comando.descricao(),
                comando.preco(), comando.imagens(), comando.categoriaId());

        // Unicidade depende de outros produtos: só o repositório sabe.
        if (produtos.existeComSku(produto.sku())) {
            throw new ConflitoException("já existe produto com o SKU " + produto.sku());
        }
        if (produtos.existeComSlug(produto.slug())) {
            throw new ConflitoException("já existe produto com o slug " + produto.slug());
        }

        salvarEPublicar(produto);
        return produto.id();
    }

    @Override
    public void atualizar(AtualizarProdutoUseCase.Comando comando) {
        Produto produto = carregar(comando.id());
        garantirQueCategoriaExiste(comando.categoriaId());

        produto.atualizarDados(comando.nome(), comando.descricao(), comando.imagens(), comando.categoriaId());
        produto.alterarPreco(comando.preco(), relogio.instant());

        salvarEPublicar(produto);
    }

    @Override
    public void desativar(ProdutoId id) {
        Produto produto = carregar(id);
        produto.desativar();
        salvarEPublicar(produto);
    }

    private Produto carregar(ProdutoId id) {
        return produtos.buscarPorId(id).orElseThrow(() -> new NaoEncontradoException("produto não encontrado: " + id));
    }

    private void garantirQueCategoriaExiste(CategoriaId id) {
        if (id == null || categorias.buscarPorId(id).isEmpty()) {
            throw new NaoEncontradoException("categoria não encontrada: " + id);
        }
    }

    /** Salva primeiro e só então entrega os eventos: nunca sai evento de algo que não foi salvo. */
    private void salvarEPublicar(Produto produto) {
        produtos.salvar(produto);
        List<DomainEvent> eventos = List.copyOf(produto.eventosPendentes());
        if (!eventos.isEmpty()) {
            publicador.publicar(eventos);
        }
        produto.limparEventos();
    }
}
