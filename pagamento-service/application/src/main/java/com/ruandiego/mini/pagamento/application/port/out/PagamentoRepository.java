package com.ruandiego.mini.pagamento.application.port.out;

import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PagamentoId;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistência do agregado Pagamento. Salvar cria ou atualiza (o status muda com o tempo). */
public interface PagamentoRepository {

    void salvar(Pagamento pagamento);

    Optional<Pagamento> buscarPorId(PagamentoId id);

    /** Pelo id da transação no gateway: é o que o webhook traz. */
    Optional<Pagamento> buscarPorTransacao(String transacaoId);

    /** Do mais recente para o mais antigo. */
    List<Pagamento> listarPorPedido(PedidoId pedido);

    /** Pix ainda pendente cujo prazo já passou em `agora`, do mais antigo para o mais novo. */
    List<Pagamento> listarPixVencidos(Instant agora, int limite);
}
