package com.ruandiego.mini.carrinho.domain.model;

import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.kernel.AggregateRoot;
import com.ruandiego.mini.kernel.Dinheiro;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Carrinho de compras: raiz de agregado.
 *
 * Regras que vivem aqui:
 *  - quantidade de 1 a 10 por item (somando adições do mesmo produto);
 *  - no máximo um cupom por carrinho (trocar exige remover o atual);
 *  - subtotal recalculado a cada alteração, nunca digitado por fora;
 *  - cupom só entra se válido e se o subtotal alcança o mínimo; se depois o subtotal cair
 *    abaixo do mínimo, o cupom fica no carrinho mas o desconto vira zero até voltar a valer.
 */
public class Carrinho extends AggregateRoot {

    private final CarrinhoId id;
    private final Map<ProdutoId, ItemCarrinho> itens = new LinkedHashMap<>();
    private Cupom cupom;
    private Dinheiro subtotal = Dinheiro.ZERO;

    private Carrinho(CarrinhoId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static Carrinho criar() {
        return new Carrinho(CarrinhoId.novo());
    }

    /** Recria um carrinho existente (adapter de persistência). O subtotal é recalculado, não lido. */
    public static Carrinho reconstituir(CarrinhoId id, List<ItemCarrinho> itens, Cupom cupom) {
        Carrinho carrinho = new Carrinho(id);
        itens.forEach(i -> carrinho.itens.put(i.produtoId(), i));
        carrinho.cupom = cupom;
        carrinho.recalcular();
        return carrinho;
    }

    /** Adiciona o item; se o produto já está no carrinho, soma as quantidades (o limite de 10 vale para a soma). */
    public void adicionarItem(ProdutoId produtoId, String nome, Dinheiro precoUnitario, int quantidade) {
        ItemCarrinho existente = itens.get(produtoId);
        if (existente == null) {
            itens.put(produtoId, new ItemCarrinho(produtoId, nome, precoUnitario, quantidade));
        } else {
            itens.put(produtoId, existente.comQuantidade(existente.quantidade() + quantidade));
        }
        recalcular();
    }

    public void alterarQuantidade(ProdutoId produtoId, int quantidade) {
        itens.put(produtoId, itemOuErro(produtoId).comQuantidade(quantidade));
        recalcular();
    }

    public void removerItem(ProdutoId produtoId) {
        itemOuErro(produtoId);
        itens.remove(produtoId);
        recalcular();
    }

    /** Usado ao consumir PrecoAlterado do catálogo. Produto fora do carrinho é ignorado (não é erro). */
    public void atualizarPreco(ProdutoId produtoId, Dinheiro novoPreco) {
        ItemCarrinho item = itens.get(produtoId);
        if (item == null) {
            return;
        }
        itens.put(produtoId, item.comPreco(novoPreco));
        recalcular();
    }

    public void aplicarCupom(Cupom novo, Instant agora) {
        Objects.requireNonNull(novo, "cupom");
        Objects.requireNonNull(agora, "agora");
        if (cupom != null) {
            throw new RegraDeNegocioException(
                    "o carrinho já tem o cupom " + cupom.codigo() + "; remova-o antes de aplicar outro");
        }
        if (novo.vencido(agora)) {
            throw new RegraDeNegocioException("cupom " + novo.codigo() + " vencido");
        }
        if (subtotal.compareTo(novo.valorMinimo()) < 0) {
            throw new RegraDeNegocioException(
                    "cupom " + novo.codigo() + " exige subtotal mínimo de " + novo.valorMinimo());
        }
        this.cupom = novo;
    }

    public void removerCupom() {
        this.cupom = null;
    }

    /**
     * Junta o carrinho de visitante a este (o do cliente logado). Regras:
     *  - produto que só existe no visitante entra como está;
     *  - produto nos dois soma as quantidades, limitado a 10 (juntar não deve falhar: o cliente
     *    não fez nada de errado, então a quantidade é cortada no teto);
     *  - preço do item que já estava no carrinho do cliente é mantido;
     *  - o cupom do cliente prevalece; sem cupom, adota o do visitante se ainda for aplicável.
     */
    public void unirCom(Carrinho visitante, Instant agora) {
        Objects.requireNonNull(visitante, "visitante");
        Objects.requireNonNull(agora, "agora");
        if (visitante == this) {
            return;
        }
        for (ItemCarrinho item : visitante.itens()) {
            ItemCarrinho existente = itens.get(item.produtoId());
            if (existente == null) {
                itens.put(item.produtoId(), item);
            } else {
                int soma = Math.min(ItemCarrinho.QUANTIDADE_MAXIMA, existente.quantidade() + item.quantidade());
                itens.put(item.produtoId(), existente.comQuantidade(soma));
            }
        }
        recalcular();
        Cupom doVisitante = visitante.cupom;
        if (cupom == null && doVisitante != null && doVisitante.aplicavelA(subtotal, agora)) {
            cupom = doVisitante;
        }
    }

    /** Esvazia itens e cupom (evento carrinho:limpo, após a compra). */
    public void esvaziar() {
        itens.clear();
        cupom = null;
        recalcular();
    }

    /** Desconto do cupom em {@code agora}; zero sem cupom, com cupom vencido ou subtotal abaixo do mínimo. */
    public Dinheiro desconto(Instant agora) {
        if (cupom == null || !cupom.aplicavelA(subtotal, agora)) {
            return Dinheiro.ZERO;
        }
        return cupom.descontoSobre(subtotal);
    }

    public Dinheiro total(Instant agora) {
        return subtotal.subtrair(desconto(agora));
    }

    private ItemCarrinho itemOuErro(ProdutoId produtoId) {
        ItemCarrinho item = itens.get(produtoId);
        if (item == null) {
            throw new RegraDeNegocioException("produto " + produtoId + " não está no carrinho");
        }
        return item;
    }

    private void recalcular() {
        subtotal = itens.values().stream().map(ItemCarrinho::subtotal).reduce(Dinheiro.ZERO, Dinheiro::somar);
    }

    public CarrinhoId id() {
        return id;
    }

    /** Lista imutável, na ordem em que os produtos entraram. */
    public List<ItemCarrinho> itens() {
        return Collections.unmodifiableList(new ArrayList<>(itens.values()));
    }

    public int quantidadeTotal() {
        return itens.values().stream().mapToInt(ItemCarrinho::quantidade).sum();
    }

    public boolean vazio() {
        return itens.isEmpty();
    }

    public Dinheiro subtotal() {
        return subtotal;
    }

    public Cupom cupom() {
        return cupom;
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Carrinho c && id.equals(c.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
