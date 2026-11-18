plugins {
    id("io.spring.dependency-management")
}

the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
    imports { mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES) }
}

dependencies {
    "implementation"(project(":shared-kernel"))
    "implementation"(project(":catalogo-service:domain"))
    "implementation"(project(":catalogo-service:application"))
    "implementation"("org.springframework.boot:spring-boot-starter-web")
    "implementation"("org.springframework.boot:spring-boot-starter-validation")
    "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
    "implementation"("org.flywaydb:flyway-core")
    "implementation"("org.flywaydb:flyway-mysql")
    "runtimeOnly"("com.mysql:mysql-connector-j")
}
