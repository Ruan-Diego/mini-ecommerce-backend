plugins {
    `kotlin-dsl`
}

// Os plugins do Spring entram como dependência aqui para os convention plugins poderem aplicá-los.
// A versão do Spring Boot do projeto inteiro é definida só neste arquivo.
dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.6")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
}
