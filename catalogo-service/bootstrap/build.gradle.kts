plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    "implementation"(project(":shared-kernel"))
    "implementation"(project(":catalogo-service:domain"))
    "implementation"(project(":catalogo-service:application"))
    "implementation"(project(":catalogo-service:adapters"))
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    "implementation"("org.springframework.boot:spring-boot-starter-web")

    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("com.tngtech.archunit:archunit-junit5:1.4.1")
}
