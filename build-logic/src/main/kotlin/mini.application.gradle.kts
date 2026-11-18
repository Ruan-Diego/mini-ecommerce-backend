// Módulo application: ports (in/out) e services dos use cases.
// Depende do domínio; sem web, JPA ou mensageria (o ArquiteturaHexagonalTest confere os imports).
// Os adapters em memória dos ports de saída ficam em src/testFixtures, para os testes de outros
// módulos (bootstrap, adapters) poderem usar via testImplementation(testFixtures(project(...))).
plugins {
    id("mini.java-conventions")
    `java-library`
    `java-test-fixtures`
}
