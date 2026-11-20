plugins {
    id("mini.application")
}

dependencies {
    api(project(":estoque-service:domain"))

    // Os testes de contrato dos ports (src/testFixtures/.../contrato) usam JUnit e AssertJ.
    testFixturesApi(platform("org.junit:junit-bom:5.12.2"))
    testFixturesApi("org.junit.jupiter:junit-jupiter-api")
    testFixturesApi("org.assertj:assertj-core:3.27.3")
}
