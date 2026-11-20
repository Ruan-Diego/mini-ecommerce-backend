package com.ruandiego.mini.pagamento.application.port.in;

/** Expira os Pix pendentes que passaram dos 30 minutos. Chamado por um agendador. */
public interface ExpirarPixVencidosUseCase {

    /** Quantos pagamentos foram expirados nesta rodada. */
    int expirarVencidos();
}
