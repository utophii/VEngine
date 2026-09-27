// vengine-core: the engine itself - registry/scheduler, effects, math, modifiers, YAML loader, DSL
plugins {
    kotlin("jvm")
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // api dependency: EffectOptions and friends appear in core's public signatures (FXEngine.play, DSL)
    api(project(":vengine-api"))

    // Paper API: compiled against the oldest supported version (1.21.6), runs up to 26.x
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")

    // Kotlin runtime
    implementation(kotlin("stdlib"))

    // Safe math expression evaluator for user-defined parametric effects (no arbitrary code execution)
    implementation("net.objecthunter:exp4j:0.4.8")

    // Tests: JUnit 5 + kotlin-test, plus Paper API on the runtime classpath for Bukkit types
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
}
