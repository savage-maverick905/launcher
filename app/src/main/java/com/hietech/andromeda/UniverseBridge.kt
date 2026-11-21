package com.hietech.andromeda

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Optional link to the Universe ecosystem. Andromeda never requires it and never hardcodes Universe
 * identifiers: the package name and URI scheme are user-supplied settings. See docs/UNIVERSE.md.
 */
interface UniverseBridge {
    fun isAvailable(): Boolean
    /** Opens Universe, or a deep link "<scheme>://<path>" when path is given. Returns false if it could not. */
    fun open(path: String? = null): Boolean
}

class IntentUniverseBridge(
    private val ctx: Context,
    private val pkg: String,
    private val scheme: String,
    private val installed: (String) -> Boolean,
    private val launchPackage: (String) -> Boolean
) : UniverseBridge {
    override fun isAvailable() = pkg.isNotBlank() && installed(pkg)

    override fun open(path: String?): Boolean {
        if (!isAvailable()) return false
        if (!path.isNullOrBlank() && scheme.isNotBlank()) {
            return try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://${path.trimStart('/')}"))
                        .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) { false }
        }
        return launchPackage(pkg)
    }
}
