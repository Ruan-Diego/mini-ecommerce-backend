package com.ruandiego.mini.carrinho.application.service;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.application.NaoEncontradoException;
import com.ruandiego.mini.carrinho.application.memoria.CarrinhoClienteRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CarrinhoVisitanteRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CatalogoProdutosEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CupomRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.PrecosJaProcessadosEmMemoria;
import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Use cases do carrinho sobre os adapters em memória: sem Spring e sem mocks. */
class CarrinhoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-19T12:00:00Z");
    private static final ProdutoId ESFERA = new ProdutoId(UUID.randomUUID());
    private static final ProdutoId CUBO = new ProdutoId(UUID.randomUUID());
    private static final ProdutoId FANTASMA = new ProdutoId(UUID.randomUUID());
    private static final Identidade VISITANTE = Identidade.visitante(new SessaoId("sessao-1"));
    private static final ClienteId ANA = new ClienteId("ana");
    private static final Identidade CLIENTE = Identidade.cliente(ANA);

    private final CarrinhoVisitanteRepositoryEmMemoria visitantes = new CarrinhoVisitanteRepositoryEmMemoria();
    private final CarrinhoClienteRepositoryEmMemoria clientes = new CarrinhoClienteRepositoryEmMemoria();
    private final CupomRepositoryEmMemoria cupons = new CupomRepositoryEmMemoria();
    private final CatalogoProdutosEmMemoria catalogo = new CatalogoProdutosEmMemoria();
    private final PrecosJaProcessadosEmMemoria processados = new PrecosJaProcessadosEmMemoria();
    private CarrinhoService service;

    @BeforeEach
    void preparar() {
        catalogo.cadastrar(new CatalogoProdutos.Produto(ESFERA, "Esfera Chrome", Dinheiro.de("100.00")));
        catalogo.cadastrar(new CatalogoProdutos.Produto(CUBO, "Cubo Preto", Dinheiro.de("50.00")));
        cupons.adicionar(Cupom.percentual("DEZ", 10, AGORA.plusSeconds(3600), null));
        cupons.adicionar(Cupom.percentual("VENCIDO", 10, AGORA.minusSeconds(1), null));
        cupons.adicionar(Cupom.percentual("GRANDE", 10, AGORA.plusSeconds(3600), Dinheiro.de("1000.00")));
        service = new CarrinhoService(visitantes, clientes, cupons, catalogo, processados,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void quemNaoTemCarrinhoRecebeUmVazioENadaEhGravado() {
        CarrinhoResumo resumo = service.obter(VISITANTE);

        assertThat(resumo.itens()).isEmpty();
        assertThat(resumo.total()).isEqualTo(Dinheiro.ZERO);
        assertThat(visitantes.buscar(new SessaoId("sessao-1"))).isEmpty();
    }

    @Test
    void adicionarUsaNomeEPrecoDoCatalogoEGravaOCarrinhoDoVisitante() {
        CarrinhoResumo resumo = service.adicionar(VISITANTE, ESFERA, 2);

        assertThat(resumo.itens()).singleElement().satisfies(item -> {
            assertThat(item.nome()).isEqualTo("Esfera Chrome");
            assertThat(item.precoUnitario()).isEqualTo(Dinheiro.de("100.00"));
        });
        assertThat(resumo.subtotal()).isEqualTo(Dinheiro.de("200.00"));
        assertThat(service.obter(VISITANTE).quantidadeTotal()).isEqualTo(2);
        assertThat(clientes.buscar(ANA)).isEmpty();
    }

    @Test
    void produtoForaDoCatalogoNaoEntraNoCarrinho() {
        assertThatThrownBy(() -> service.adicionar(VISITANTE, FANTASMA, 1))
                .isInstanceOf(NaoEncontradoException.class);
        assertThat(visitantes.buscar(new SessaoId("sessao-1"))).isEmpty();
    }

    @Test
    void regraDoDominioNaoGravaNada() {
        service.adicionar(VISITANTE, ESFERA, 8);

        assertThatThrownBy(() -> service.adicionar(VISITANTE, ESFERA, 3))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(service.obter(VISITANTE).quantidadeTotal()).isEqualTo(8);
    }

    @Test
    void alterarEQuantidadeERemoverItem() {
        service.adicionar(VISITANTE, ESFERA, 2);
        service.adicionar(VISITANTE, CUBO, 1);

        assertThat(service.alterar(VISITANTE, ESFERA, 4).subtotal()).isEqualTo(Dinheiro.de("450.00"));
        assertThat(service.remover(VISITANTE, CUBO).subtotal()).isEqualTo(Dinheiro.de("400.00"));
    }

    @Test
    void clienteLogadoGravaNoSeuProprioCarrinho() {
        service.adicionar(CLIENTE, CUBO, 3);

        assertThat(clientes.buscar(ANA).orElseThrow().quantidadeTotal()).isEqualTo(3);
    }

    @Test
    void cupomAplicadoPorCodigoDescontaESaiPorRemoverCupom() {
        service.adicionar(VISITANTE, ESFERA, 2);

        CarrinhoResumo com = service.aplicar(VISITANTE, "dez");
        assertThat(com.desconto()).isEqualTo(Dinheiro.de("20.00"));
        assertThat(com.total()).isEqualTo(Dinheiro.de("180.00"));

        CarrinhoResumo sem = service.removerCupom(VISITANTE);
        assertThat(sem.cupom()).isNull();
        assertThat(sem.total()).isEqualTo(Dinheiro.de("200.00"));
    }

    @Test
    void cupomInexistenteVencidoOuAbaixoDoMinimoSaoRecusados() {
        service.adicionar(VISITANTE, ESFERA, 2);

        assertThatThrownBy(() -> service.aplicar(VISITANTE, "NAO-EXISTE")).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.aplicar(VISITANTE, "VENCIDO")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.aplicar(VISITANTE, "GRANDE")).isInstanceOf(RegraDeNegocioException.class);
        assertThat(service.obter(VISITANTE).cupom()).isNull();
    }

    @Test
    void removerCupomSemCupomNaoGravaNada() {
        service.removerCupom(VISITANTE);

        assertThat(visitantes.buscar(new SessaoId("sessao-1"))).isEmpty();
    }

    @Test
    void itensDeVisitanteContinuamNoCarrinhoDepoisDoLogin() {
        service.adicionar(VISITANTE, ESFERA, 2);
        service.aplicar(VISITANTE, "DEZ");

        Identidade logou = new Identidade(new SessaoId("sessao-1"), ANA);
        CarrinhoResumo resumo = service.obter(logou);

        assertThat(resumo.itens()).singleElement().satisfies(i -> assertThat(i.produtoId()).isEqualTo(ESFERA));
        assertThat(resumo.cupom().codigo()).isEqualTo("DEZ");
        assertThat(clientes.buscar(ANA)).isPresent();
        assertThat(visitantes.buscar(new SessaoId("sessao-1"))).isEmpty();
        assertThat(service.obter(CLIENTE).quantidadeTotal()).isEqualTo(2);
    }

    @Test
    void uniaoSomaComOCarrinhoQueOClienteJaTinha() {
        service.adicionar(CLIENTE, ESFERA, 1);
        service.adicionar(VISITANTE, ESFERA, 2);
        service.adicionar(VISITANTE, CUBO, 1);

        service.unir(new SessaoId("sessao-1"), ANA);

        CarrinhoResumo resumo = service.obter(CLIENTE);
        assertThat(resumo.quantidadeTotal()).isEqualTo(4);
        assertThat(resumo.subtotal()).isEqualTo(Dinheiro.de("350.00"));
    }

    @Test
    void unirSemCarrinhoDeVisitanteNaoFazNada() {
        service.unir(new SessaoId("sessao-1"), ANA);

        assertThat(clientes.buscar(ANA)).isEmpty();
    }

    @Test
    void naoUneDeNovoDepoisDaPrimeiraVez() {
        service.adicionar(VISITANTE, ESFERA, 2);
        Identidade logou = new Identidade(new SessaoId("sessao-1"), ANA);

        service.obter(logou);
        service.obter(logou);

        assertThat(service.obter(CLIENTE).quantidadeTotal()).isEqualTo(2);
    }

    @Test
    void checkoutDevolveAFotoFinalDoCarrinhoDoCliente() {
        service.adicionar(CLIENTE, ESFERA, 2);
        service.aplicar(CLIENTE, "DEZ");

        CarrinhoResumo resumo = service.iniciar(ANA);

        assertThat(resumo.total()).isEqualTo(Dinheiro.de("180.00"));
    }

    @Test
    void checkoutComCarrinhoVazioEhRecusado() {
        assertThatThrownBy(() -> service.iniciar(ANA))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    void checkoutRecusaCupomQueDeixouDeValer() {
        service.adicionar(CLIENTE, ESFERA, 2);
        // O cupom venceu depois de aplicado: grava direto, como se tivesse sido aplicado antes.
        var carrinho = clientes.buscar(ANA).orElseThrow();
        carrinho.aplicarCupom(Cupom.percentual("CURTO", 10, AGORA, null), AGORA.minusSeconds(60));
        clientes.salvar(ANA, carrinho);
        Clock depois = Clock.fixed(AGORA.plusSeconds(1), ZoneOffset.UTC);
        CarrinhoService tardio = new CarrinhoService(visitantes, clientes, cupons, catalogo, processados, depois);

        assertThatThrownBy(() -> tardio.iniciar(ANA))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("CURTO");
    }

    @Test
    void mudancaDePrecoChegaAosCarrinhosDeClienteEDeVisitanteComAvisoDoValorAntigo() {
        service.adicionar(CLIENTE, ESFERA, 2);
        service.adicionar(VISITANTE, ESFERA, 1);
        service.adicionar(VISITANTE, CUBO, 1);

        service.atualizar(ESFERA, Dinheiro.de("120.00"), AGORA);

        var doCliente = service.obter(CLIENTE).itens().get(0);
        assertThat(doCliente.precoUnitario()).isEqualTo(Dinheiro.de("120.00"));
        assertThat(doCliente.precoAnterior()).isEqualTo(Dinheiro.de("100.00"));
        var doVisitante = service.obter(VISITANTE);
        assertThat(doVisitante.itens().get(0).precoAnterior()).isEqualTo(Dinheiro.de("100.00"));
        assertThat(doVisitante.itens().get(1).precoAlterado()).isFalse();
        assertThat(doVisitante.subtotal()).isEqualTo(Dinheiro.de("170.00"));
    }

    @Test
    void mesmaMensagemDuasVezesTemOEfeitoDeUma() {
        service.adicionar(CLIENTE, ESFERA, 1);

        service.atualizar(ESFERA, Dinheiro.de("120.00"), AGORA);
        service.atualizar(ESFERA, Dinheiro.de("120.00"), AGORA);

        var item = service.obter(CLIENTE).itens().get(0);
        assertThat(item.precoUnitario()).isEqualTo(Dinheiro.de("120.00"));
        assertThat(item.precoAnterior()).isEqualTo(Dinheiro.de("100.00"));
    }

    @Test
    void mensagemAntigaQueChegaDepoisDeUmaNovaEhDescartada() {
        service.adicionar(CLIENTE, ESFERA, 1);

        service.atualizar(ESFERA, Dinheiro.de("130.00"), AGORA.plusSeconds(60));
        service.atualizar(ESFERA, Dinheiro.de("110.00"), AGORA);

        assertThat(service.obter(CLIENTE).itens().get(0).precoUnitario()).isEqualTo(Dinheiro.de("130.00"));
    }

    @Test
    void duasMudancasEmOrdemMantemOPrimeiroValorVistoNoAviso() {
        service.adicionar(CLIENTE, ESFERA, 1);

        service.atualizar(ESFERA, Dinheiro.de("110.00"), AGORA);
        service.atualizar(ESFERA, Dinheiro.de("120.00"), AGORA.plusSeconds(1));

        var item = service.obter(CLIENTE).itens().get(0);
        assertThat(item.precoAnterior()).isEqualTo(Dinheiro.de("100.00"));
        assertThat(item.precoUnitario()).isEqualTo(Dinheiro.de("120.00"));
    }

    @Test
    void produtoForaDeTodosOsCarrinhosSoRegistraOEvento() {
        service.atualizar(CUBO, Dinheiro.de("70.00"), AGORA);

        assertThat(processados.jaProcessado(CUBO, AGORA)).isTrue();
        assertThat(clientes.clientesComProduto(CUBO)).isEmpty();
    }

    @Test
    void carrinhoSemOProdutoNaoEhRegravado() {
        service.adicionar(VISITANTE, ESFERA, 1);
        service.remover(VISITANTE, ESFERA);
        // O índice do Redis pode listar sessão que já tirou o produto: o service não deve criar nada por isso.

        service.atualizar(ESFERA, Dinheiro.de("120.00"), AGORA);

        assertThat(service.obter(VISITANTE).itens()).isEmpty();
    }

    @Test
    void pedidoCriadoEsvaziaOCarrinhoDoClienteComItensECupom() {
        service.adicionar(CLIENTE, ESFERA, 2);
        service.aplicar(CLIENTE, "DEZ");

        service.esvaziar(ANA);

        CarrinhoResumo depois = service.obter(CLIENTE);
        assertThat(depois.itens()).isEmpty();
        assertThat(depois.cupom()).isNull();
    }

    @Test
    void esvaziarEhIdempotenteEIgnoraQuemNaoTemCarrinho() {
        service.esvaziar(ANA); // não existe: nada acontece
        service.adicionar(CLIENTE, ESFERA, 1);
        service.esvaziar(ANA);
        service.esvaziar(ANA); // reentrega da mesma mensagem

        assertThat(service.obter(CLIENTE).itens()).isEmpty();
    }

    @Test
    void esvaziarOCarrinhoDoClienteNaoMexeNoDeOutroNemNoDeVisitante() {
        service.adicionar(CLIENTE, ESFERA, 1);
        ClienteId bia = new ClienteId("bia");
        service.adicionar(Identidade.cliente(bia), CUBO, 1);
        service.adicionar(VISITANTE, CUBO, 3);

        service.esvaziar(ANA);

        assertThat(service.obter(Identidade.cliente(bia)).itens()).hasSize(1);
        assertThat(service.obter(VISITANTE).quantidadeTotal()).isEqualTo(3);
    }
}
