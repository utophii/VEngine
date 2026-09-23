package com.utophii.api

// public service-provider contract: lets any plugin add its own effects to the VEngine registry.
// Implement this interface and register it via FXEngine.registerProvider(...) during onEnable to
// expose custom effects to /vengine, the DSL, and scripted YAML layers.
// The returned effects are registered as primitives, so they can also be used as layer bases.
fun interface EffectProvider {
    // the effects this provider contributes; returning an empty list is allowed
    fun provide(): List<ParticleEffect>
}
