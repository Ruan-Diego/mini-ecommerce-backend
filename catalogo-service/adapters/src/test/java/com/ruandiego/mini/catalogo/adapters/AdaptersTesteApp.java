package com.ruandiego.mini.catalogo.adapters;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * O módulo adapters não é uma aplicação: esta classe só existe nos testes, para o @DataJpaTest
 * ter uma configuração raiz de onde achar as entidades e os repositórios JPA.
 */
@SpringBootApplication
class AdaptersTesteApp {
}
