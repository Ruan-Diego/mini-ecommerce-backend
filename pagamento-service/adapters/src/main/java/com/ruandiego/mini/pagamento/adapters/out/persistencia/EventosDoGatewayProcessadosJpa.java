package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import com.ruandiego.mini.pagamento.application.port.out.EventosDoGatewayProcessados;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Adapter real do registro de eventos do gateway. Usa `INSERT IGNORE` na tabela cuja chave primária é
 * (transação, evento): o banco decide quem foi o primeiro. Se duas entregas do mesmo webhook chegam juntas,
 * a segunda espera a primeira terminar a transação; se a primeira confirma, a segunda insere 0 linhas
 * (e vira "já processado"); se a primeira desfaz, a segunda insere e segue em frente.
 */
@Component
public class EventosDoGatewayProcessadosJpa implements EventosDoGatewayProcessados {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public boolean registrar(String transacaoId, String evento) {
        int linhas = em.createNativeQuery("""
                        INSERT IGNORE INTO evento_gateway_processado (transacao_id, evento, recebido_em)
                        VALUES (?1, ?2, ?3)""")
                .setParameter(1, transacaoId)
                .setParameter(2, evento)
                .setParameter(3, Instant.now())
                .executeUpdate();
        return linhas > 0;
    }
}
