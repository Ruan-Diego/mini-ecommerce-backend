package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import com.ruandiego.mini.catalogo.application.contrato.OutboxContrato;
import com.ruandiego.mini.catalogo.application.port.out.Outbox;
import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A tabela outbox passa no mesmo contrato da outbox em memória, com o JSON dos eventos gravado de verdade. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, OutboxJpaAdapter.class, JacksonAutoConfiguration.class})
class OutboxJpaAdapterTest extends OutboxContrato {

    private static final java.util.UUID PRODUTO = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private OutboxJpaAdapter outbox;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }

    /**
     * O JSON de catalogo.preco-alterado é o contrato que o carrinho consome (PrecoAlteradoMensagem): texto e número
     * planos, não os value objects do domínio aninhados.
     */
    @org.junit.jupiter.api.Test
    void precoAlteradoSaiComoJsonPlanoDoContratoDoCarrinho() throws Exception {
        outbox.publicar(java.util.List.of(new com.ruandiego.mini.catalogo.domain.event.PrecoAlterado(
                new com.ruandiego.mini.catalogo.domain.model.ProdutoId(PRODUTO),
                new com.ruandiego.mini.catalogo.domain.model.Sku("ESF-01"),
                com.ruandiego.mini.kernel.Dinheiro.de("289.90"), com.ruandiego.mini.kernel.Dinheiro.de("249.90"),
                java.time.Instant.parse("2026-11-19T12:00:00.123456Z"))));

        String payload = outbox.pendentes(1).get(0).payload();
        com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);

        org.assertj.core.api.Assertions.assertThat(json.get("produtoId").asText()).isEqualTo(PRODUTO.toString());
        org.assertj.core.api.Assertions.assertThat(json.get("sku").asText()).isEqualTo("ESF-01");
        org.assertj.core.api.Assertions.assertThat(json.get("precoAnterior").decimalValue()).isEqualByComparingTo("289.90");
        org.assertj.core.api.Assertions.assertThat(json.get("precoNovo").decimalValue()).isEqualByComparingTo("249.90");
        org.assertj.core.api.Assertions.assertThat(json.get("ocorridoEm").asText()).isEqualTo("2026-11-19T12:00:00.123456Z");
        org.assertj.core.api.Assertions.assertThat(json.size()).isEqualTo(5);
    }

    @Override
    protected PublicadorEventos publicador() {
        return outbox;
    }

    @Override
    protected Outbox outbox() {
        return outbox;
    }
}
