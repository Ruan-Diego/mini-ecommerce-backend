package com.ruandiego.mini.pagamento.adapters.in.web;

/** A rota exige um cliente logado e a requisição chegou sem identidade. O tratador de erros traduz para 401. */
class NaoAutenticadoException extends RuntimeException {

    NaoAutenticadoException() {
        super("é preciso estar autenticado");
    }
}
