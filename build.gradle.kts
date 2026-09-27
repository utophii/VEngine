plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}

subprojects {
    group = "com.utophii"
    version = "0.1.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
