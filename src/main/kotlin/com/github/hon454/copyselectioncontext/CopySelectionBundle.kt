package com.github.hon454.copyselectioncontext

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_NAME = "messages.CopySelectionBundle"

object CopySelectionBundle {
    private val bundle = DynamicBundle(CopySelectionBundle::class.java, BUNDLE_NAME)

    fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): String =
        bundle.getMessage(key, *params)
}
