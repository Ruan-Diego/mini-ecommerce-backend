package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.pedido.application.port.in.AdministrarPedidosUseCase;
import com.ruandiego.mini.pedido.domain.model.Pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Pedido como o admin vê: o mesmo do cliente mais o dono e as transições que a tela pode oferecer. Com
 * `transicoesPermitidas` a tela não precisa repetir a máquina de estados (nem oferecer transição inválida).
 */
record PedidoAdminResposta(String id, String clienteId, String status, List<String> transicoesPermitidas,
                           List<PedidoResposta.Item> itens, PedidoResposta.Endereco endereco, FreteResposta frete,
                           BigDecimal subtotal, BigDecimal total, String codigoRastreio, Instant criadoEm,
                           Instant atualizadoEm) {

    static PedidoAdminResposta de(Pedido pedido) {
        List<String> transicoes = AdministrarPedidosUseCase.transicoesManuais(pedido.status()).stream()
                .map(Enum::name).toList(); // EnumSet: na ordem do fluxo
        return new PedidoAdminResposta(pedido.id().toString(), pedido.clienteId().valor(), pedido.status().name(),
                transicoes, pedido.itens().stream().map(PedidoResposta.Item::de).toList(),
                PedidoResposta.Endereco.de(pedido.endereco()), FreteResposta.de(pedido.frete()),
                pedido.subtotal().valor(), pedido.total().valor(), pedido.codigoRastreio(), pedido.criadoEm(),
                pedido.atualizadoEm());
    }
}
