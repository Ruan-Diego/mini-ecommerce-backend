package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;

/**
 * Junta o carrinho do visitante (por sessão) ao do cliente que acabou de logar. O carrinho de visitante
 * é apagado depois. Sem carrinho de visitante, não faz nada. Chamado no primeiro acesso autenticado
 * (o service já o dispara quando a identidade traz cliente e sessão).
 */
public interface UnirCarrinhosUseCase {

    void unir(SessaoId sessao, ClienteId cliente);
}
