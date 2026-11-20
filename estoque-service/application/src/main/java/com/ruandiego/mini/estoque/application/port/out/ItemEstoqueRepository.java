package com.ruandiego.mini.estoque.application.port.out;

import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Port de saída: persistência do saldo por produto. */
public interface ItemEstoqueRepository {

    Optional<ItemEstoque> buscar(ProdutoId produto);

    /**
     * Busca os itens existentes e os TRAVA até o fim da transação atual: quem pedir os mesmos itens espera.
     * É isso que impede duas reservas simultâneas de venderem o mesmo último item. Devolve em ordem estável
     * (por id do produto), a mesma ordem em que o adapter trava, para duas transações não se bloquearem em ciclo.
     * Produtos sem item cadastrado simplesmente não aparecem.
     */
    List<ItemEstoque> buscarComTrava(Collection<ProdutoId> produtos);

    void salvar(ItemEstoque item);

    /**
     * Itens com `disponivel` estritamente abaixo de `limite` (nulo = todos), do menor saldo para o maior e,
     * no empate, pelo id do produto.
     */
    List<ItemEstoque> listarComDisponivelAbaixoDe(Integer limite);

    long contarComDisponivelAbaixoDe(int limite);
}
