package com.ruandiego.mini.pedido.application.port.out;

import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistência do agregado Pedido. Salvar cria ou atualiza (o status muda com o tempo). */
public interface PedidoRepository {

    void salvar(Pedido pedido);

    Optional<Pedido> buscarPorId(PedidoId id);

    /** Do mais recente para o mais antigo. */
    List<Pedido> listarPorCliente(ClienteId cliente);

    /**
     * Pedidos ainda AGUARDANDO_PAGAMENTO criados em `criadosAte` ou antes (limite inclusivo), do mais antigo para
     * o mais novo, no máximo `quantidade`. É o que o job de expiração cancela.
     */
    List<Pedido> listarAguardandoPagamentoCriadosAte(Instant criadosAte, int quantidade);
}
