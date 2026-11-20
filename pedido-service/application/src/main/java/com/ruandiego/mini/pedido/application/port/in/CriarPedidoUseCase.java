package com.ruandiego.mini.pedido.application.port.in;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;

import java.util.List;

/** Cria o pedido a partir do que o cliente viu no carrinho, conferindo preço atual e frete. */
public interface CriarPedidoUseCase {

    /** `precoVisto` é o preço que o cliente viu; se o catálogo já mudou, o pedido é recusado (conflito). */
    record ItemSolicitado(ProdutoId produtoId, int quantidade, Dinheiro precoVisto) {
    }

    record Comando(ClienteId cliente, List<ItemSolicitado> itens, EnderecoEntrega endereco, String freteCodigo) {
    }

    Pedido criar(Comando comando);
}
