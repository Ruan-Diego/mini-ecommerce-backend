package com.ruandiego.mini.carrinho.adapters.in.mensageria;

import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.application.memoria.CarrinhoClienteRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CarrinhoVisitanteRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CatalogoProdutosEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CupomRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.PrecosJaProcessadosEmMemoria;
import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.application.service.CarrinhoService;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O listener só traduz mensagem em chamada de use case: aqui com o service de verdade sobre adapters em memória. */
class PrecoAlteradoListenerTest {

    private static final Instant AGORA = Instant.parse("2026-11-19T12:00:00Z");
    private static final ProdutoId ESFERA = new ProdutoId(UUID.randomUUID());
    private static final ClienteId ANA = new ClienteId("ana");

    private final CarrinhoClienteRepositoryEmMemoria clientes = new CarrinhoClienteRepositoryEmMemoria();
    private CarrinhoService service;
    private PrecoAlteradoListener listener;

    @BeforeEach
    void preparar() {
        CatalogoProdutosEmMemoria catalogo = new CatalogoProdutosEmMemoria();
        catalogo.cadastrar(new CatalogoProdutos.Produto(ESFERA, "Esfera Chrome", Dinheiro.de("100.00")));
        service = new CarrinhoService(new CarrinhoVisitanteRepositoryEmMemoria(), clientes,
                new CupomRepositoryEmMemoria(), catalogo, new PrecosJaProcessadosEmMemoria(),
                Clock.fixed(AGORA, ZoneOffset.UTC));
        listener = new PrecoAlteradoListener(service);
        service.adicionar(Identidade.cliente(ANA), ESFERA, 2);
    }

    private static PrecoAlteradoMensagem mensagem(String produtoId, String precoNovo, String ocorridoEm) {
        return new PrecoAlteradoMensagem(produtoId, "ESF-01", new BigDecimal("100.00"),
                precoNovo == null ? null : new BigDecimal(precoNovo), ocorridoEm);
    }

    @Test
    void mensagemValidaAtualizaOPrecoComAvisoDoValorAntigo() {
        listener.aoReceber(mensagem(ESFERA.toString(), "120.00", "2026-11-19T12:00:00.123456Z"), null);

        var item = service.obter(Identidade.cliente(ANA)).itens().get(0);
        assertThat(item.precoUnitario()).isEqualTo(Dinheiro.de("120.00"));
        assertThat(item.precoAnterior()).isEqualTo(Dinheiro.de("100.00"));
    }

    @Test
    void mensagemRepetidaNaoMudaNada() {
        PrecoAlteradoMensagem m = mensagem(ESFERA.toString(), "120.00", "2026-11-19T12:00:00Z");

        listener.aoReceber(m, null);
        listener.aoReceber(m, null);

        var item = service.obter(Identidade.cliente(ANA)).itens().get(0);
        assertThat(item.precoUnitario()).isEqualTo(Dinheiro.de("120.00"));
        assertThat(item.precoAnterior()).isEqualTo(Dinheiro.de("100.00"));
    }

    @Test
    void camposDesconhecidosNaoImportamMasCamposInvalidosSaoRejeitadosSemReentrega() {
        assertThatThrownBy(() -> listener.aoReceber(mensagem("nao-e-uuid", "120.00", "2026-11-19T12:00:00Z"), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.aoReceber(mensagem(ESFERA.toString(), null, "2026-11-19T12:00:00Z"), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.aoReceber(mensagem(ESFERA.toString(), "120.00", "ontem"), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.aoReceber(mensagem(ESFERA.toString(), "120.00", null), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.aoReceber(mensagem(null, "120.00", "2026-11-19T12:00:00Z"), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(service.obter(Identidade.cliente(ANA)).itens().get(0).precoAlterado()).isFalse();
    }

    @Test
    void precoZeroOuNegativoEhRejeitadoSemReentrega() {
        assertThatThrownBy(() -> listener.aoReceber(mensagem(ESFERA.toString(), "0.00", "2026-11-19T12:00:00Z"), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.aoReceber(mensagem(ESFERA.toString(), "-5.00", "2026-11-19T12:00:00Z"), null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(service.obter(Identidade.cliente(ANA)).itens().get(0).precoUnitario())
                .isEqualTo(Dinheiro.de("100.00"));
    }
}
