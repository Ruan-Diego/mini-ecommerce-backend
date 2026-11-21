package com.ruandiego.mini.estoque.application.port.in;

import com.ruandiego.mini.estoque.domain.model.ItemEstoque;

import java.util.List;

/** O que o admin consulta no estoque: a lista (com filtro de estoque baixo) e o indicador do painel. */
public interface ConsultarEstoqueAdminUseCase {

    /** `abaixoDe` nulo = todos os itens; senão só os com disponível abaixo do valor. Menor saldo primeiro. */
    List<ItemEstoque> listar(Integer abaixoDe);

    Indicadores indicadores();

    /** @param estoqueBaixo itens com disponível abaixo de `limite` ({@link ItemEstoque#LIMITE_ESTOQUE_BAIXO}) */
    record Indicadores(long estoqueBaixo, int limite) {
    }
}
