package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruandiego.mini.catalogo.domain.event.PrecoAlterado;
import com.ruandiego.mini.kernel.DomainEvent;
import com.ruandiego.mini.catalogo.application.port.out.Outbox;
import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;
import com.ruandiego.mini.catalogo.adapters.out.mensageria.ContextoDeTrace;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                jpa.save(new OutboxJpa(UUID.randomUUID().toString(), evento.tipo(), paraJson(evento),
                        evento.ocorridoEm(), ContextoDeTrace.capturar()));
            } catch (JsonProcessingException e) {
                // Se não dá para serializar, desfaz o use case inteiro: evento perdido em silêncio é pior que erro.
                throw new IllegalStateException("não foi possível serializar o evento " + evento.tipo(), e);
            }
        }
    }

    /**
     * JSON plano (só texto e número), que é o contrato do consumidor, e não o record do domínio, cujos value
     * objects (ProdutoId, Sku, Dinheiro) sairiam como objetos aninhados. Evento novo precisa ganhar o seu
     * mapeamento aqui: sem ele, falha (e desfaz o use case) em vez de publicar um formato qualquer.
     * Contrato de `catalogo.preco-alterado`: veja PrecoAlteradoMensagem, no carrinho-service.
     */
    private String paraJson(DomainEvent evento) throws JsonProcessingException {
        if (evento instanceof PrecoAlterado e) {
            Map<String, Object> mensagem = new LinkedHashMap<>();
            mensagem.put("produtoId", e.produtoId().toString());
            mensagem.put("sku", e.sku().valor());
            mensagem.put("precoAnterior", e.precoAnterior().valor());
            mensagem.put("precoNovo", e.precoNovo().valor());
            mensagem.put("ocorridoEm", e.ocorridoEm().toString());
            return json.writeValueAsString(mensagem);
        }
        throw new IllegalArgumentException("evento sem serialização definida: " + evento.tipo());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Mensagem> pendentes(int limite) {
        return jpa.findByPublicadaEmIsNullOrderBySeqAsc(PageRequest.of(0, limite)).stream()
                .map(o -> new Mensagem(UUID.fromString(o.getId()), o.getTipo(), o.getPayload(), o.getCriadaEm(), o.getTraceId()))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarComoPublicada(UUID id, Instant publicadaEm) {
        jpa.findOneById(id.toString()).ifPresent(o -> o.marcarPublicada(publicadaEm));
    }
}
