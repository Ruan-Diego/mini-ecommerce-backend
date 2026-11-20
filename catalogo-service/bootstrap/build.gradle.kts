plugins {
    id("mini.spring-boot-app")
}

dependencies {
    implementation(project(":catalogo-service:adapters"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Resource server: valida o JWT do Keycloak e exige o papel admin em /admin/**.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    testImplementation("org.springframework.security:spring-security-test")
    // Transação dos use cases, amarrada no CatalogoConfig.
    implementation("org.springframework:spring-tx")

    // Teste de integração do serviço inteiro contra MySQL em container.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
}
