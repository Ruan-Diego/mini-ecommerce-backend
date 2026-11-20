package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruandiego.mini.kernel.DomainEvent;
import com.ruandiego.mini.pagamento.application.port.out.Outbox;
import com.ruandiego.mini.pagamento.application.port.out.PublicadorEventos;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Adapter real da outbox, nos dois sentidos:
 * - {@link PublicadorEventos}: grava o evento (JSON) na tabela outbox, na transação do use case. Não fala com o broker.
 * - {@link Outbox}: o relay lê o que está pendente e dá baixa. Só linhas já commitadas aparecem na leitura.
 */
@Component
public class OutboxJpaAdapter implements PublicadorEventos, Outbox {

    private final OutboxJpaRepository jpa;
    private final ObjectMapper json;

    OutboxJpaAdapter(OutboxJpaRepository jpa, ObjectMapper json) {
        this.jpa = jpa;
        this.json = json;
    }

    @Override
    @Transactional
    public void publicar(List<? extends DomainEvent> eventos) {
        for (DomainEvent evento : eventos) {
            try {
                jpa.save(new OutboxJpa(UUID.randomUUID().toString(), evento.tipo(), json.writeValueAsString(evento),
                        evento.ocorridoEm()));
            } catch (JsonProcessingException e) {
                // Se não dá para serializar, desfaz o use case inteiro: evento perdido em silêncio é pior que erro.
                throw new IllegalStateException("não foi possível serializar o evento " + evento.tipo(), e);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Mensagem> pendentes(int limite) {
        return jpa.findByPublicadaEmIsNullOrderBySeqAsc(PageRequest.of(0, limite)).stream()
                .map(o -> new Mensagem(UUID.fromString(o.getId()), o.getTipo(), o.getPayload(), o.getCriadaEm()))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarComoPublicada(UUID id, Instant publicadaEm) {
        jpa.findOneById(id.toString()).ifPresent(o -> o.marcarPublicada(publicadaEm));
    }
}
