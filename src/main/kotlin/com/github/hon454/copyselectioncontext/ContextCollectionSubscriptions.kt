package com.github.hon454.copyselectioncontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList

/** No initial event: subscribe on EDT, then read the current snapshot before returning to the event loop. */
internal class ContextCollectionSubscriptions<T> : Disposable {
    private val listeners = CopyOnWriteArrayList<Subscription>()

    fun subscribe(parent: Disposable, listener: (T) -> Unit) {
        val subscription = Subscription(listener)
        listeners.add(subscription)
        Disposer.register(parent, subscription)
    }

    fun publish(snapshot: T) {
        listeners.forEach { subscription ->
            try {
                subscription.listener?.invoke(snapshot)
            } catch (failure: RuntimeException) {
                // Callback errors cannot roll back a committed collection or expose captured content in logs.
                Logger.getInstance(ContextCollectionSubscriptions::class.java)
                    .warn("Collection subscriber failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    override fun dispose() {
        listeners.toList().forEach(Disposer::dispose)
    }

    private inner class Subscription(@Volatile var listener: ((T) -> Unit)?) : Disposable {
        override fun dispose() {
            listener = null
            listeners.remove(this)
        }
    }
}
