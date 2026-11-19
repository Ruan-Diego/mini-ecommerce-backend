plugins {
    id("mini.spring-boot-app")
}

dependencyManagement {
    imports { mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0") }
}

// Nome fixo do jar, para o Dockerfile não depender da versão.
tasks.bootJar {
    archiveFileName.set("gateway.jar")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
