package com.ruandiego.mini.carrinho.application.service;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.application.NaoEncontradoException;
import com.ruandiego.mini.carrinho.application.port.in.AdicionarItemUseCase;
import com.ruandiego.mini.carrinho.application.port.in.AlterarQuantidadeUseCase;
import com.ruandiego.mini.carrinho.application.port.in.AplicarCupomUseCase;
import com.ruandiego.mini.carrinho.application.port.in.AtualizarPrecoNosCarrinhosUseCase;
import com.ruandiego.mini.carrinho.application.port.in.EsvaziarCarrinhoDoClienteUseCase;
import com.ruandiego.mini.carrinho.application.port.in.IniciarCheckoutUseCase;
import com.ruandiego.mini.carrinho.application.port.in.ObterCarrinhoUseCase;
import com.ruandiego.mini.carrinho.application.port.in.RemoverCupomUseCase;
import com.ruandiego.mini.carrinho.application.port.in.RemoverItemUseCase;
import com.ruandiego.mini.carrinho.application.port.in.UnirCarrinhosUseCase;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoClienteRepository;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoVisitanteRepository;
import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.application.port.out.CupomRepository;
import com.ruandiego.mini.carrinho.application.port.out.PrecosJaProcessados;
import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;

import com.ruandiego.mini.kernel.Dinheiro;

import java.time.Clock;
import java.time.Instant;
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
        RemoverItemUseCase, AplicarCupomUseCase, RemoverCupomUseCase, UnirCarrinhosUseCase, IniciarCheckoutUseCase,
        AtualizarPrecoNosCarrinhosUseCase, EsvaziarCarrinhoDoClienteUseCase {

    private final CarrinhoVisitanteRepository visitantes;
    private final CarrinhoClienteRepository clientes;
    private final CupomRepository cupons;
    private final CatalogoProdutos catalogo;
    private final PrecosJaProcessados processados;
    private final Clock relogio;

    public CarrinhoService(CarrinhoVisitanteRepository visitantes, CarrinhoClienteRepository clientes,
                           CupomRepository cupons, CatalogoProdutos catalogo, PrecosJaProcessados processados,
                           Clock relogio) {
        this.visitantes = visitantes;
        this.clientes = clientes;
        this.cupons = cupons;
        this.catalogo = catalogo;
        this.processados = processados;
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

    /**
     * Esvazia o carrinho do cliente depois que virou pedido. Limite conhecido: um item adicionado no intervalo
     * entre criar o pedido e consumir o evento (milissegundos) também é descartado.
     */
    @Override
    public void esvaziar(ClienteId cliente) {
        clientes.buscar(cliente).ifPresent(carrinho -> {
            carrinho.esvaziar();
            clientes.salvar(cliente, carrinho);
        });
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

    /**
     * Ordem pensada para falhar sem perder nada: primeiro confere se o evento já foi aplicado, depois aplica
     * nos carrinhos e só no fim registra o instante. Se o serviço cair no meio, a mensagem é reentregue e
     * reaplicada, e reaplicar o mesmo preço é inofensivo (o domínio ignora preço igual).
     * Limites conhecidos: um cliente que mexe no carrinho no instante exato da atualização pode ter a
     * própria gravação sobrescrita (lê, muda, grava); e gravar o carrinho de visitante renova o prazo de 7 dias.
     */
    @Override
    public void atualizar(ProdutoId produtoId, Dinheiro precoNovo, Instant ocorridoEm) {
        if (processados.jaProcessado(produtoId, ocorridoEm)) {
            return;
        }
        for (ClienteId cliente : clientes.clientesComProduto(produtoId)) {
            clientes.buscar(cliente).ifPresent(carrinho -> {
                if (aplicarPreco(carrinho, produtoId, precoNovo)) {
                    clientes.salvar(cliente, carrinho);
                }
            });
        }
        for (SessaoId sessao : visitantes.sessoesComProduto(produtoId)) {
            visitantes.buscar(sessao).ifPresent(carrinho -> {
                if (aplicarPreco(carrinho, produtoId, precoNovo)) {
                    visitantes.salvar(sessao, carrinho);
                }
            });
        }
        processados.registrar(produtoId, ocorridoEm);
    }

    /** Aplica o preço e diz se algo mudou, para só regravar (e renovar prazo) o que mudou de fato. */
    private static boolean aplicarPreco(Carrinho carrinho, ProdutoId produtoId, Dinheiro precoNovo) {
        var antes = carrinho.itens();
        carrinho.atualizarPreco(produtoId, precoNovo);
        return !antes.equals(carrinho.itens());
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
