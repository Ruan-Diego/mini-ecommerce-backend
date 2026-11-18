plugins {
    id("mini.spring-boot-app")
}

dependencies {
    implementation(project(":pedido-service:adapters"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
