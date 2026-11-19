package com.ruandiego.mini.carrinho.application.contrato;

import com.ruandiego.mini.carrinho.application.port.out.CarrinhoVisitanteRepository;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Test;

import static com.ruandiego.mini.carrinho.application.contrato.CarrinhoRepositoryContratoBase.CUBO;
import static com.ruandiego.mini.carrinho.application.contrato.CarrinhoRepositoryContratoBase.assertIgual;
import static com.ruandiego.mini.carrinho.application.contrato.CarrinhoRepositoryContratoBase.ESFERA;
import static com.ruandiego.mini.carrinho.application.contrato.CarrinhoRepositoryContratoBase.carrinhoCompleto;
import static com.ruandiego.mini.carrinho.application.contrato.CarrinhoRepositoryContratoBase.carrinhoSo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato do port CarrinhoVisitanteRepository: todo adapter (em memória, Redis) estende esta classe e
 * precisa passar nos mesmos cenários. A expiração de 7 dias é testada à parte, no adapter Redis.
 */
public abstract class CarrinhoVisitanteRepositoryContrato {

    private static final SessaoId SESSAO_A = new SessaoId("sessao-a");
    private static final SessaoId SESSAO_B = new SessaoId("sessao-b");

    protected abstract CarrinhoVisitanteRepository visitantes();

    @Test
    void sessaoSemCarrinhoViraOptionalVazio() {
        assertThat(visitantes().buscar(SESSAO_A)).isEmpty();
    }

    @Test
    void guardaEDevolveItensECupomNaMesmaOrdem() {
        Carrinho original = carrinhoCompleto();

        visitantes().salvar(SESSAO_A, original);

        assertIgual(visitantes().buscar(SESSAO_A).orElseThrow(), original);
    }

    @Test
    void salvarDeNovoSubstituiOCarrinho() {
        Carrinho carrinho = carrinhoCompleto();
        visitantes().salvar(SESSAO_A, carrinho);

        carrinho.removerCupom();
        carrinho.removerItem(CUBO);
        visitantes().salvar(SESSAO_A, carrinho);

        Carrinho lido = visitantes().buscar(SESSAO_A).orElseThrow();
        assertThat(lido.itens()).hasSize(1);
        assertThat(lido.cupom()).isNull();
        assertThat(lido.subtotal()).isEqualTo(Dinheiro.de("200.00"));
    }

    @Test
    void removerApagaSoOCarrinhoDaquelaSessao() {
        visitantes().salvar(SESSAO_A, carrinhoCompleto());
        visitantes().salvar(SESSAO_B, carrinhoCompleto());

        visitantes().remover(SESSAO_A);

        assertThat(visitantes().buscar(SESSAO_A)).isEmpty();
        assertThat(visitantes().buscar(SESSAO_B)).isPresent();
    }

    @Test
    void guardaOAvisoDeMudancaDePrecoDosItens() {
        visitantes().salvar(SESSAO_A, carrinhoCompleto());

        Carrinho lido = visitantes().buscar(SESSAO_A).orElseThrow();

        assertThat(lido.itens().get(0).precoAlterado()).isFalse();
        assertThat(lido.itens().get(1).precoAnterior()).isEqualTo(Dinheiro.de("49.90"));
        assertThat(lido.itens().get(1).precoUnitario()).isEqualTo(Dinheiro.de("59.90"));
    }

    @Test
    void listaAsSessoesQueTemOProdutoNoCarrinho() {
        visitantes().salvar(SESSAO_A, carrinhoSo(ESFERA, "Esfera Chrome"));
        visitantes().salvar(SESSAO_B, carrinhoSo(CUBO, "Cubo Preto"));

        assertThat(visitantes().sessoesComProduto(ESFERA)).containsExactly(SESSAO_A);
        assertThat(visitantes().sessoesComProduto(CUBO)).containsExactly(SESSAO_B);
        assertThat(visitantes().sessoesComProduto(new ProdutoId(java.util.UUID.randomUUID()))).isEmpty();
    }

    @Test
    void sessaoRemovidaNaoApareceMaisNaLista() {
        visitantes().salvar(SESSAO_A, carrinhoSo(ESFERA, "Esfera Chrome"));

        visitantes().remover(SESSAO_A);

        // Pode continuar na lista (índice sem expiração própria), mas nunca com carrinho para devolver.
        assertThat(visitantes().sessoesComProduto(ESFERA)).allSatisfy(s -> assertThat(visitantes().buscar(s)).isEmpty());
    }

    @Test
    void removerSessaoSemCarrinhoNaoEhErro() {
        visitantes().remover(SESSAO_A);

        assertThat(visitantes().buscar(SESSAO_A)).isEmpty();
    }
}
