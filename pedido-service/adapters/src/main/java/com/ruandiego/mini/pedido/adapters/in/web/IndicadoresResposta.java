package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.pedido.application.port.in.ConsultarIndicadoresUseCase.Indicadores;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Indicadores do painel que vêm dos pedidos. `dia` é o "hoje" da loja, para a tela dizer de qual dia fala. */
record IndicadoresResposta(LocalDate dia, long pedidosHoje, BigDecimal faturamentoDoDia, long aguardandoPagamento) {

    static IndicadoresResposta de(Indicadores i) {
        return new IndicadoresResposta(i.dia(), i.pedidosHoje(), i.faturamentoDoDia().valor(), i.aguardandoPagamento());
    }
}
