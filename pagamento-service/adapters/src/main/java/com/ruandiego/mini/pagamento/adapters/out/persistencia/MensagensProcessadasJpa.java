package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import com.ruandiego.mini.pagamento.application.port.out.MensagensProcessadas;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Adapter real do registro de mensagens tratadas. Usa `INSERT IGNORE` na tabela cuja chave primária é
 * (consumidor, mensagem): o banco decide quem foi o primeiro. Se duas entregas da mesma mensagem chegam
 * juntas, a segunda espera a primeira terminar a transação; se a primeira confirma, a segunda insere 0 linhas
 * (e vira "já processada"); se a primeira desfaz, a segunda insere e segue em frente.
 */
@Component
public class MensagensProcessadasJpa implements MensagensProcessadas {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public boolean registrar(String consumidor, UUID mensagemId) {
        int linhas = em.createNativeQuery("""
                        INSERT IGNORE INTO mensagens_processadas (consumidor, mensagem_id, processada_em)
                        VALUES (?1, ?2, ?3)""")
                .setParameter(1, consumidor)
                .setParameter(2, mensagemId.toString())
                .setParameter(3, Instant.now())
                .executeUpdate();
        return linhas > 0;
    }
}
