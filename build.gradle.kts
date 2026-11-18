plugins {
    id("org.springframework.boot") version "3.5.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.ruandiego.mini"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.12.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:3.27.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> { useJUnitPlatform() }
    tasks.withType<JavaCompile> { options.encoding = "UTF-8"; options.compilerArgs.add("-parameters") }
}
