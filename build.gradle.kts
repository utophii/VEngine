plugins {
    kotlin("jvm") version "2.4.20" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}

subprojects {
    group = "com.utophii"
    version = "0.1.1"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven {
          name = "faststatsReleases"
          url = uri("https://repo.faststats.dev/releases")
        }
        maven {
          name = "placeholderapi"
          url = uri("https://repo.extendedclip.com/releases/")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
