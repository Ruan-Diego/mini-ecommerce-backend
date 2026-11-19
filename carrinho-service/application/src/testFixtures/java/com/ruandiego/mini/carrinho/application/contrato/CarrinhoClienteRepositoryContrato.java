package com.ruandiego.mini.carrinho.application.contrato;

import com.ruandiego.mini.carrinho.application.port.out.CarrinhoClienteRepository;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Test;

import static com.ruandiego.mini.carrinho.application.contrato.CarrinhoRepositoryContratoBase.CUBO;
import static com.ruandiego.mini.carrinho.application.contrato.CarrinhoRepositoryContratoBase.ESFERA;
import static com.ruandiego.mini.carrinho.application.contrato.CarrinhoRepositoryContratoBase.assertIgual;
import static com.ruandiego.mini.carrinho.application.contrato.CarrinhoRepositoryContratoBase.carrinhoCompleto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato do port CarrinhoClienteRepository: todo adapter (em memória, JPA) estende esta classe e
 * precisa passar nos mesmos cenários.
 */
public abstract class CarrinhoClienteRepositoryContrato {

    private static final ClienteId ANA = new ClienteId("cliente-ana");
    private static final ClienteId BRUNO = new ClienteId("cliente-bruno");

    protected abstract CarrinhoClienteRepository clientes();

    @Test
    void clienteSemCarrinhoViraOptionalVazio() {
        assertThat(clientes().buscar(ANA)).isEmpty();
    }

    @Test
    void guardaEDevolveItensECupomNaMesmaOrdem() {
        Carrinho original = carrinhoCompleto();

        clientes().salvar(ANA, original);

        assertIgual(clientes().buscar(ANA).orElseThrow(), original);
    }

    @Test
    void salvarDeNovoSubstituiItensECupom() {
        Carrinho carrinho = carrinhoCompleto();
        clientes().salvar(ANA, carrinho);

        carrinho.removerItem(CUBO);
        carrinho.alterarQuantidade(ESFERA, 5);
        carrinho.removerCupom();
        clientes().salvar(ANA, carrinho);

        Carrinho lido = clientes().buscar(ANA).orElseThrow();
        assertThat(lido.itens()).hasSize(1);
        assertThat(lido.itens().get(0).quantidade()).isEqualTo(5);
        assertThat(lido.cupom()).isNull();
        assertThat(lido.subtotal()).isEqualTo(Dinheiro.de("500.00"));
    }

    @Test
    void carrinhoVazioTambemEhGuardado() {
        Carrinho carrinho = carrinhoCompleto();
        clientes().salvar(ANA, carrinho);

        carrinho.esvaziar();
        clientes().salvar(ANA, carrinho);

        assertThat(clientes().buscar(ANA).orElseThrow().vazio()).isTrue();
    }

    @Test
    void cadaClienteTemOSeuCarrinho() {
        Carrinho daAna = carrinhoCompleto();
        Carrinho doBruno = Carrinho.criar();
        doBruno.adicionarItem(CUBO, "Cubo Preto", Dinheiro.de("49.90"), 4);

        clientes().salvar(ANA, daAna);
        clientes().salvar(BRUNO, doBruno);

        assertIgual(clientes().buscar(ANA).orElseThrow(), daAna);
        assertIgual(clientes().buscar(BRUNO).orElseThrow(), doBruno);
    }
}
