package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.pedido.domain.model.EnderecoEntrega;
import com.ruandiego.mini.pedido.domain.model.ItemPedido;
import com.ruandiego.mini.pedido.domain.model.Pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Pedido como o REST o devolve. Valores em reais como número; datas em ISO-8601 (UTC). */
record PedidoResposta(String id, String status, List<Item> itens, Endereco endereco, FreteResposta frete,
                      BigDecimal subtotal, BigDecimal total, String codigoRastreio, Instant criadoEm,
                      Instant atualizadoEm) {

    record Item(String produtoId, String nome, BigDecimal precoUnitario, int quantidade, BigDecimal subtotal) {

        static Item de(ItemPedido item) {
            return new Item(item.produtoId().toString(), item.nome(), item.precoUnitario().valor(), item.quantidade(),
                    item.subtotal().valor());
        }
    }

    record Endereco(String destinatario, String cep, String logradouro, String numero, String complemento,
                    String bairro, String cidade, String uf) {

        static Endereco de(EnderecoEntrega e) {
            return new Endereco(e.destinatario(), e.cep(), e.logradouro(), e.numero(), e.complemento(), e.bairro(),
                    e.cidade(), e.uf());
        }
    }

    static PedidoResposta de(Pedido pedido) {
        return new PedidoResposta(pedido.id().toString(), pedido.status().name(),
                pedido.itens().stream().map(Item::de).toList(), Endereco.de(pedido.endereco()),
                FreteResposta.de(pedido.frete()), pedido.subtotal().valor(), pedido.total().valor(),
                pedido.codigoRastreio(), pedido.criadoEm(), pedido.atualizadoEm());
    }
}
