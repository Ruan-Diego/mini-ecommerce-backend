package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;

import java.time.Instant;

/**
 * Reação ao evento PrecoAlterado do catálogo: aplica o preço novo em todos os carrinhos (de visitante e
 * de cliente) que têm o produto, marcando nos itens o valor antigo para o aviso na tela.
 *
 * O consumo é idempotente e tolera mensagens fora de ordem: o service lembra o instante do último evento
 * processado de cada produto e ignora o que for igual ou mais antigo (reentrega, duplicata, atraso).
 */
public interface AtualizarPrecoNosCarrinhosUseCase {

    /**
     * @param ocorridoEm instante em que o catálogo mudou o preço (vem no evento; é a chave de ordem e de duplicata)
     */
    void atualizar(ProdutoId produtoId, Dinheiro precoNovo, Instant ocorridoEm);
}
