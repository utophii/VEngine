// vengine-api: stable public contracts that external plugins compile against
// (ParticleEffect, EffectOptions, modifiers, EffectProvider SPI, EffectHandle, no engine internals)
plugins {
    kotlin("jvm")
}

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
