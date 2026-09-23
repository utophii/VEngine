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

    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
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
    }

    build {
        dependsOn(shadowJar)
    }
}
