package com.ruandiego.mini.pagamento.application.port.in;

import com.ruandiego.mini.pagamento.domain.model.Pagamento;
import com.ruandiego.mini.pagamento.domain.model.PagamentoId;

/** Estorna um pagamento aprovado (operação de administrador). */
public interface EstornarPagamentoUseCase {

    Pagamento estornar(PagamentoId id);
}
