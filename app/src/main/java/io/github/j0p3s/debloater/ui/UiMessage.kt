package io.github.j0p3s.debloater.ui

import androidx.annotation.StringRes

/** Resolve in the current activity so messages use the selected app language. */
data class UiMessage(@param:StringRes @get:StringRes val resource: Int, val args: List<Any> = emptyList())
