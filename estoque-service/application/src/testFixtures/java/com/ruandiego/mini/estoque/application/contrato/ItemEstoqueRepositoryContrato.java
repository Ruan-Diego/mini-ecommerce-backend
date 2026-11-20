package com.ruandiego.mini.estoque.application.contrato;

import com.ruandiego.mini.estoque.application.port.out.ItemEstoqueRepository;
import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do port ItemEstoqueRepository. Quem estende devolve um repositório vazio a cada teste. */
public abstract class ItemEstoqueRepositoryContrato {

    protected abstract ItemEstoqueRepository itens();

    private static ProdutoId produto() {
        return new ProdutoId(UUID.randomUUID());
    }

    @Test
    void salvaEBuscaOSaldo() {
        ProdutoId id = produto();

        itens().salvar(ItemEstoque.reconstituir(id, 7, 3));

        assertThat(itens().buscar(id)).hasValueSatisfying(i -> {
            assertThat(i.disponivel()).isEqualTo(7);
            assertThat(i.reservado()).isEqualTo(3);
        });
    }

    @Test
    void produtoSemItemNaoApareceNaBusca() {
        assertThat(itens().buscar(produto())).isEmpty();
        assertThat(itens().buscarComTrava(List.of(produto()))).isEmpty();
    }

    @Test
    void salvarDeNovoAtualizaOSaldo() {
        ProdutoId id = produto();
        itens().salvar(ItemEstoque.novo(id, 5));

        ItemEstoque lido = itens().buscar(id).orElseThrow();
        lido.reservar(2);
        itens().salvar(lido);

        assertThat(itens().buscar(id)).hasValueSatisfying(i -> {
            assertThat(i.disponivel()).isEqualTo(3);
            assertThat(i.reservado()).isEqualTo(2);
        });
    }

    @Test
    void buscaComTravaDevolveSoOsExistentesEmOrdemEstavel() {
        ProdutoId a = produto();
        ProdutoId b = produto();
        itens().salvar(ItemEstoque.novo(a, 1));
        itens().salvar(ItemEstoque.novo(b, 2));

        List<ItemEstoque> lidos = itens().buscarComTrava(List.of(b, produto(), a));

        assertThat(lidos).extracting(ItemEstoque::produtoId)
                .containsExactlyInAnyOrder(a, b)
                .isSorted();
    }
}
