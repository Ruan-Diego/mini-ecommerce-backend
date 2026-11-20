package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import com.ruandiego.mini.pagamento.application.port.out.PedidosCancelados;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Adapter real do registro de pedidos cancelados. `INSERT IGNORE` na tabela `pedido_cancelado` (chave primária
 * = pedido): registrar duas vezes, mesmo ao mesmo tempo, deixa uma linha só. A transação é a do use case.
 */
@Component
public class PedidosCanceladosJpa implements PedidosCancelados {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void registrar(PedidoId pedido, Instant quando) {
        em.createNativeQuery("INSERT IGNORE INTO pedido_cancelado (pedido_id, cancelado_em) VALUES (?1, ?2)")
                .setParameter(1, pedido.toString())
                .setParameter(2, quando)
                .executeUpdate();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estaCancelado(PedidoId pedido) {
        Number linhas = (Number) em.createNativeQuery("SELECT COUNT(*) FROM pedido_cancelado WHERE pedido_id = ?1")
                .setParameter(1, pedido.toString())
                .getSingleResult();
        return linhas.longValue() > 0;
    }
}
