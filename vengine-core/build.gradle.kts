// vengine-core: the engine itself - registry/scheduler, effects, math, modifiers, YAML loader, DSL
plugins {
    kotlin("jvm")
    `java-library`
    id("me.champeau.jmh")
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

    // optional: with the PacketEvents plugin installed, followed display models are client-side passengers
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")

    // Kotlin runtime
    implementation(kotlin("stdlib"))

    // Safe math expression evaluator for user-defined parametric effects (no arbitrary code execution)
    implementation("net.objecthunter:exp4j:0.4.8")

    // Tests: JUnit 5 + kotlin-test, plus Paper API on the runtime classpath for Bukkit types
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")

    // Benchmarks: Bukkit types (Location, Vector) on the runtime classpath
    jmhImplementation("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
}

// ./gradlew :vengine-core:jmh, or -Pjmh.includes=Transform to run one class
jmh {
    jmhVersion.set("1.37")
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    // gc profiler reports allocation per operation next to the timing
    profilers.add("gc")
    (findProperty("jmh.includes") as String?)?.let { includes.add(it) }
}
