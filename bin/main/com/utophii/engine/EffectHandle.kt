package com.utophii.api

import java.util.concurrent.atomic.AtomicBoolean

// a handle to a running effect that is authorized to control its execution and stop it early
interface EffectHandle {
    val id: String
    val effectName: String
    val isCancelled: Boolean

    fun cancel()
}

// default implementation of an EffectHandle wrapping an on-cancel callback
class SimpleEffectHandle(
    override val id: String,
    override val effectName: String,
    private val onCancel: () -> Unit,
) : EffectHandle {
    private val cancelled = AtomicBoolean(false)

    override val isCancelled: Boolean
        get() = cancelled.get()

    override fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            onCancel()
        }
    }
}

// composite handle grouping multiple layered effect tasks
class CompositeEffectHandle(
    override val id: String,
    override val effectName: String,
    private val children: List<EffectHandle>,
) : EffectHandle {
    private val cancelled = AtomicBoolean(false)

    override val isCancelled: Boolean
        get() = cancelled.get()

    override fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            children.forEach(EffectHandle::cancel)
        }
    }
}