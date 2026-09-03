package com.github.hon454.copyselectioncontext;

import com.intellij.openapi.wm.CustomStatusBarWidget;

/**
 * Prevents Kotlin from generating compatibility bridges for deprecated
 * {@code StatusBarWidget} default methods when implementing the public custom-widget API.
 */
public abstract class CustomStatusBarWidgetAdapter implements CustomStatusBarWidget {
}
