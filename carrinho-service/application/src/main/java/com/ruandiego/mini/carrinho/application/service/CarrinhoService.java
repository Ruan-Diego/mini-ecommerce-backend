package com.ruandiego.mini.carrinho.application.service;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.application.NaoEncontradoException;
import com.ruandiego.mini.carrinho.application.port.in.AdicionarItemUseCase;
import com.ruandiego.mini.carrinho.application.port.in.AlterarQuantidadeUseCase;
import com.ruandiego.mini.carrinho.application.port.in.AplicarCupomUseCase;
import com.ruandiego.mini.carrinho.application.port.in.IniciarCheckoutUseCase;
import com.ruandiego.mini.carrinho.application.port.in.ObterCarrinhoUseCase;
import com.ruandiego.mini.carrinho.application.port.in.RemoverCupomUseCase;
import com.ruandiego.mini.carrinho.application.port.in.RemoverItemUseCase;
import com.ruandiego.mini.carrinho.application.port.in.UnirCarrinhosUseCase;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoClienteRepository;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoVisitanteRepository;
import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.application.port.out.CupomRepository;
import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;

import java.time.Clock;
import java.util.Optional;

/**
 * Casos de uso do carrinho. Só orquestra: carrega o carrinho certo (visitante ou cliente), chama o
 * agregado, que aplica as regras, e grava de volta. Não conhece Redis, MySQL nem HTTP.
 *
 * Sem transação em volta do serviço: o carrinho do visitante vive no Redis e o do cliente no MySQL, e
 * cada adapter já grava de forma atômica (uma chave, uma transação). A união grava primeiro o carrinho
 * do cliente e só depois apaga o do visitante: se algo falhar no meio, o pior caso é repetir a união
 * (quantidades somadas de novo, limitadas a 10), e nunca perder itens.
 */
public class CarrinhoService implements ObterCarrinhoUseCase, AdicionarItemUseCase, AlterarQuantidadeUseCase,
        RemoverItemUseCase, AplicarCupomUseCase, RemoverCupomUseCase, UnirCarrinhosUseCase, IniciarCheckoutUseCase {

    private final CarrinhoVisitanteRepository visitantes;
    private final CarrinhoClienteRepository clientes;
    private final CupomRepository cupons;
    private final CatalogoProdutos catalogo;
    private final Clock relogio;

    public CarrinhoService(CarrinhoVisitanteRepository visitantes, CarrinhoClienteRepository clientes,
                           CupomRepository cupons, CatalogoProdutos catalogo, Clock relogio) {
        this.visitantes = visitantes;
        this.clientes = clientes;
        this.cupons = cupons;
        this.catalogo = catalogo;
        this.relogio = relogio;
    }

    @Override
    public CarrinhoResumo obter(Identidade identidade) {
        return resumo(carregar(identidade));
    }

    @Override
    public CarrinhoResumo adicionar(Identidade identidade, ProdutoId produtoId, int quantidade) {
        Carrinho carrinho = carregar(identidade);
        CatalogoProdutos.Produto produto = catalogo.buscar(produtoId)
                .orElseThrow(() -> new NaoEncontradoException("produto " + produtoId + " não existe ou está inativo"));
        carrinho.adicionarItem(produto.id(), produto.nome(), produto.preco(), quantidade);
        return salvar(identidade, carrinho);
    }

    @Override
    public CarrinhoResumo alterar(Identidade identidade, ProdutoId produtoId, int quantidade) {
        Carrinho carrinho = carregar(identidade);
        carrinho.alterarQuantidade(produtoId, quantidade);
        return salvar(identidade, carrinho);
    }

    @Override
    public CarrinhoResumo remover(Identidade identidade, ProdutoId produtoId) {
        Carrinho carrinho = carregar(identidade);
        carrinho.removerItem(produtoId);
        return salvar(identidade, carrinho);
    }

    @Override
    public CarrinhoResumo aplicar(Identidade identidade, String codigo) {
        Cupom cupom = cupons.buscarPorCodigo(codigo)
                .orElseThrow(() -> new NaoEncontradoException("cupom " + codigo + " não existe"));
        Carrinho carrinho = carregar(identidade);
        carrinho.aplicarCupom(cupom, relogio.instant());
        return salvar(identidade, carrinho);
    }

    @Override
    public CarrinhoResumo removerCupom(Identidade identidade) {
        Carrinho carrinho = carregar(identidade);
        if (carrinho.cupom() == null) {
            return resumo(carrinho);
        }
        carrinho.removerCupom();
        return salvar(identidade, carrinho);
    }

    @Override
    public void unir(SessaoId sessao, ClienteId cliente) {
        Optional<Carrinho> doVisitante = visitantes.buscar(sessao);
        if (doVisitante.isEmpty()) {
            return;
        }
        Carrinho doCliente = clientes.buscar(cliente).orElseGet(Carrinho::criar);
        doCliente.unirCom(doVisitante.get(), relogio.instant());
        clientes.salvar(cliente, doCliente);
        visitantes.remover(sessao);
    }

    @Override
    public CarrinhoResumo iniciar(ClienteId cliente) {
        Carrinho carrinho = clientes.buscar(cliente).orElseGet(Carrinho::criar);
        if (carrinho.vazio()) {
            throw new RegraDeNegocioException("o carrinho está vazio");
        }
        Cupom cupom = carrinho.cupom();
        if (cupom != null && !cupom.aplicavelA(carrinho.subtotal(), relogio.instant())) {
            throw new RegraDeNegocioException(
                    "o cupom " + cupom.codigo() + " não vale mais para este carrinho; remova-o para continuar");
        }
        return resumo(carrinho);
    }

    /** Carrinho de quem pede. Para cliente que ainda traz a sessão, une o carrinho de visitante antes. */
    private Carrinho carregar(Identidade identidade) {
        if (identidade.autenticada()) {
            if (identidade.sessao() != null) {
                unir(identidade.sessao(), identidade.cliente());
            }
            return clientes.buscar(identidade.cliente()).orElseGet(Carrinho::criar);
        }
        return visitantes.buscar(identidade.sessao()).orElseGet(Carrinho::criar);
    }

    private CarrinhoResumo salvar(Identidade identidade, Carrinho carrinho) {
        if (identidade.autenticada()) {
            clientes.salvar(identidade.cliente(), carrinho);
        } else {
            visitantes.salvar(identidade.sessao(), carrinho);
        }
        return resumo(carrinho);
    }

    private CarrinhoResumo resumo(Carrinho carrinho) {
        return CarrinhoResumo.de(carrinho, relogio.instant());
    }
}
