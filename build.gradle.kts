plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("com.gradleup.shadow") version "9.4.1" apply false
}

subprojects {
    group = "com.utophii"
    version = "01-a"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
