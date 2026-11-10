plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    kotlin("plugin.allopen") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.foodrescue"
version = "0.0.1-SNAPSHOT"

repositories { mavenCentral() }

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }
tasks.bootJar { archiveFileName.set("foodrescue-backend.jar") }

// Package the single reviewed API contract for dev/test documentation.
tasks.processResources { from("docs/api") { into("api") } }

// SW-01,05,08: build-only asset distribution; no WebJar on runtimeClasspath.
val swaggerUiVersion = "5.34.1"
val swaggerUiAssets = configurations.create("swaggerUiAssets") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    add(swaggerUiAssets.name, "org.webjars:swagger-ui:$swaggerUiVersion")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}
tasks.processResources {
    from({ swaggerUiAssets.map { zipTree(it) } }) {
        include("META-INF/resources/webjars/swagger-ui/$swaggerUiVersion/swagger-ui.css")
        include("META-INF/resources/webjars/swagger-ui/$swaggerUiVersion/swagger-ui-bundle.js")
        include("META-INF/resources/webjars/swagger-ui/$swaggerUiVersion/swagger-ui-bundle.js.LICENSE.txt")
        eachFile { path = "api-docs/vendor/$name" }
        includeEmptyDirs = false
    }
}
