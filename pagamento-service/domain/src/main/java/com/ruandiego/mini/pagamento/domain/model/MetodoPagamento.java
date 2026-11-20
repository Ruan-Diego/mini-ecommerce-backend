package com.ruandiego.mini.pagamento.domain.model;

/** Forma de pagamento. O cartão responde na hora; o Pix fica pendente até o webhook do gateway ou a expiração. */
public enum MetodoPagamento {
    CARTAO,
    PIX
}
