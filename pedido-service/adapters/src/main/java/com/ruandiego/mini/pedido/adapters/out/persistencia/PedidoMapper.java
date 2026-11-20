package com.ruandiego.mini.pedido.adapters.out.persistencia;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.Frete;
import com.ruandiego.mini.pedido.domain.model.ItemPedido;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;

import java.util.List;

/**
 * Traduz entre o Pedido do domínio e a linha JPA. Entidade de domínio e entidade JPA são coisas
 * diferentes: o domínio não tem anotação nem construtor vazio, e a tabela não conhece regra de negócio.
 */
final class PedidoMapper {

    private PedidoMapper() {
    }

    /** Grava o estado do domínio na linha JPA (a de um pedido novo ou a carregada do banco). */
    static void paraJpa(Pedido pedido, PedidoJpa jpa) {
        EnderecoEntrega e = pedido.endereco();
        List<ItemPedidoJpa> itens = pedido.itens().stream()
                .map(i -> new ItemPedidoJpa(i.produtoId().toString(), i.nome(), i.precoUnitario().valor(), i.quantidade()))
                .toList();
        jpa.preencher(pedido.clienteId().valor(), pedido.status(), pedido.frete().codigo(), pedido.frete().nome(),
                pedido.frete().valor().valor(), pedido.frete().prazoDias(),
                new String[]{e.destinatario(), e.cep(), e.logradouro(), e.numero(), e.complemento(), e.bairro(),
                        e.cidade(), e.uf()},
                pedido.criadoEm(), pedido.atualizadoEm(), itens);
    }

    static Pedido paraDominio(PedidoJpa jpa) {
        List<ItemPedido> itens = jpa.getItens().stream()
                .map(i -> new ItemPedido(ProdutoId.de(i.getProdutoId()), i.getNome(),
                        new Dinheiro(i.getPrecoUnitario()), i.getQuantidade()))
                .toList();
        EnderecoEntrega endereco = new EnderecoEntrega(jpa.getEndDestinatario(), jpa.getEndCep(),
                jpa.getEndLogradouro(), jpa.getEndNumero(), jpa.getEndComplemento(), jpa.getEndBairro(),
                jpa.getEndCidade(), jpa.getEndUf());
        Frete frete = new Frete(jpa.getFreteCodigo(), jpa.getFreteNome(), new Dinheiro(jpa.getFreteValor()),
                jpa.getFretePrazoDias());
        return Pedido.reconstituir(PedidoId.de(jpa.getId()), new ClienteId(jpa.getClienteId()), itens, endereco,
                frete, jpa.getStatus(), jpa.getCriadoEm(), jpa.getAtualizadoEm());
    }
}
