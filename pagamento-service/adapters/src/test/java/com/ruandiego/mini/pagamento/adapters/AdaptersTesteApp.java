package com.ruandiego.mini.pagamento.adapters;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * O módulo adapters não é uma aplicação: esta classe só existe nos testes, para o @DataJpaTest e o
 * @WebMvcTest terem uma configuração raiz de onde achar entidades, repositórios e controllers.
 */
@SpringBootApplication
class AdaptersTesteApp {
}
