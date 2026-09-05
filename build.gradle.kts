plugins {
    kotlin("jvm") version "2.3.0"
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "com.utophii"
version = "01-a"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")

    // Kotlin runtime
    implementation(kotlin("stdlib"))

    // SnakeYAML for config
    implementation("org.yaml:snakeyaml:2.4")

    // Adventure text minimessage
    implementation("net.kyori:adventure-text-minimessage:4.24.0")

    // Apache Commons Lang
    implementation("org.apache.commons:commons-lang3:3.18.0")

    // Safe math expression evaluator for user-defined parametric effects
    implementation("net.objecthunter:exp4j:0.4.8")

    // Tests: JUnit 5 + kotlin-test, plus Paper API on the runtime classpath for Bukkit types
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
}

tasks {
    test {
        useJUnitPlatform()
    }
    processResources {
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version,
                "name" to project.name
            )
        }
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}
