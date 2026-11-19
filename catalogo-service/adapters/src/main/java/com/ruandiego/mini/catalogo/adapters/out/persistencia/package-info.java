/**
 * Adapter de saída de persistência: implementa ProdutoRepository e CategoriaRepository com JPA + MySQL.
 *
 * As classes *Jpa são entidades JPA, separadas das entidades de domínio: o domínio não tem anotação nenhuma
 * e o formato das tabelas pode mudar sem tocar nas regras de negócio. O CatalogoMapper converte entre os dois.
 */
package com.ruandiego.mini.catalogo.adapters.out.persistencia;
