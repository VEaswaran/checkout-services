plugins {
    java
    id("org.springframework.boot") version "4.0.8"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.company"
version = "0.0.1-SNAPSHOT"
description = "Checkout domain API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Web layer (Spring MVC on virtual threads) ---
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // --- Outbound HTTP: WebClient + Reactor Netty (timeouts, connection pool) ---
    implementation("org.springframework.boot:spring-boot-starter-webclient")

    // --- Observability: health, metrics, Micrometer Tracing (W3C traceparent) + OTLP export ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    // Lets Reactor carry the current trace across threads (retries, Netty callbacks) -> no broken traces
    implementation("io.micrometer:context-propagation")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // --- Tests ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // -parameters keeps @PathVariable/@RequestParam names without repeating them
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    // Exposes build version/time on /actuator/info -> useful when correlating incidents with releases
    buildInfo()
}
