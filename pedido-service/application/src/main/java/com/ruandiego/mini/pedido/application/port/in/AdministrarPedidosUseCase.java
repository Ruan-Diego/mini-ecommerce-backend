package com.ruandiego.mini.pedido.application.port.in;

import com.ruandiego.mini.pedido.application.Pagina;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * O que o admin faz com pedidos de qualquer cliente: listar com filtro, ver um e mudar o status à mão.
 * Quem garante que só admin chega aqui é a segurança do adapter REST, não este port.
 */
public interface AdministrarPedidosUseCase {

    /**
     * Status que o admin pode escolher a partir do atual. PAGO fica de fora de propósito: só o evento do
     * pagamento marca um pedido como pago. O resto vem da máquina de estados do domínio.
     */
    static Set<StatusPedido> transicoesManuais(StatusPedido atual) {
        Set<StatusPedido> permitidos = EnumSet.noneOf(StatusPedido.class);
        permitidos.addAll(atual.proximosPermitidos());
        permitidos.remove(StatusPedido.PAGO);
        return permitidos;
    }

    Pagina<Pedido> listar(Filtro filtro, int pagina, int tamanho);

    Pedido obter(PedidoId id);

    /** `codigoRastreio` só é usado (e é obrigatório) quando o destino é ENVIADO. */
    Pedido alterarStatus(PedidoId id, StatusPedido destino, String codigoRastreio);

    /**
     * Filtro em datas do calendário da loja (fuso configurado no service), inclusivas dos dois lados.
     * `status` vazio = todos; datas nulas = sem limite.
     */
    record Filtro(Set<StatusPedido> status, LocalDate de, LocalDate ate) {

        public Filtro {
            status = status == null ? Set.of() : Set.copyOf(status);
        }
    }
}
