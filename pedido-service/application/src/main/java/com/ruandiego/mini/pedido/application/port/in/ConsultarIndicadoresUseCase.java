package com.ruandiego.mini.pedido.application.port.in;

import com.ruandiego.mini.kernel.Dinheiro;

import java.time.LocalDate;

/**
 * Números do painel do admin que dependem só de pedidos. "Estoque abaixo de 5" é do estoque-service
 * (cada serviço só lê o próprio schema), que expõe o próprio indicador.
 */
public interface ConsultarIndicadoresUseCase {

    Indicadores indicadores();

    /**
     * @param dia                 o "hoje" no fuso da loja
     * @param pedidosHoje         pedidos criados hoje, em qualquer status
     * @param faturamentoDoDia    soma dos totais dos pedidos criados hoje que foram pagos (PAGO em diante, sem os cancelados)
     * @param aguardandoPagamento pedidos em AGUARDANDO_PAGAMENTO agora, de qualquer dia
     */
    record Indicadores(LocalDate dia, long pedidosHoje, Dinheiro faturamentoDoDia, long aguardandoPagamento) {
    }
}
