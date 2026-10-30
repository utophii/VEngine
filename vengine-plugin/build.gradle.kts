// vengine-plugin: Bukkit entry point, commands and the distributable shadow jar
plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":vengine-core"))
    implementation(kotlin("stdlib"))
    implementation("dev.faststats.metrics:bukkit:0.30.2")

    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("io.lumine:Mythic-Dist:5.13.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version,
                "name" to rootProject.name,
            )
        }
    }

    jar {
        // the shadow jar is the only published artifact
        enabled = false
    }

    shadowJar {
        archiveBaseName.set("VEngine")
        archiveClassifier.set("")
        // libraries are relocated so other plugins that shade the same ones (exp4j, faststats) cannot clash
        relocate("net.objecthunter.exp4j", "com.utophii.libs.exp4j")
        relocate("dev.faststats", "com.utophii.libs.faststats")
        relocate("com.google.gson", "com.utophii.libs.gson")
        mergeServiceFiles()
        // kotlin-stdlib stays unrelocated on purpose: Paper ships its own Kotlin runtime and relocating
        // kotlin.* breaks every other Kotlin plugin sharing the server
    }

    build {
        dependsOn(shadowJar)
    }
}
