import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

// vengine-api: stable public contracts that external plugins compile against
// (ParticleEffect, EffectOptions, modifiers, EffectProvider SPI, EffectHandle, no engine internals)
plugins {
    kotlin("jvm")
    signing
    id("org.jetbrains.dokka") version "2.2.0"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "io.github.utophii"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Paper API: compiled against the oldest supported version (1.21.6), runs up to 26.x
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("VEngine API")
        description.set("Public contracts for integrating with the VEngine Minecraft particle effect engine.")
        url.set("https://utophii.github.io/VEngine/")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit/")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("utophii")
                name.set("utophii")
                url.set("https://github.com/utophii")
            }
        }
        scm {
            url.set("https://github.com/utophii/VEngine")
            connection.set("scm:git:https://github.com/utophii/VEngine.git")
            developerConnection.set("scm:git:ssh://git@github.com/utophii/VEngine.git")
        }
    }
}

signing {
    // Local publication and ordinary builds work without a signing key.
    setRequired {
        gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
    }
}
