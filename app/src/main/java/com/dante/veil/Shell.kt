package com.dante.veil

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class ShellResult(val code: Int, val out: String, val err: String) {
    val ok: Boolean get() = code == 0
}

/** Single-quote a string for `sh -c`. */
fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

object ShizukuShell {

    // Shizuku 13+ keeps newProcess() private; reflection is the standard workaround.
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun run(cmd: String): ShellResult = try {
        val p = newProcess.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        ShellResult(p.waitFor(), out.trim(), err.trim())
    } catch (t: Throwable) {
        ShellResult(-1, "", t.cause?.message ?: t.message ?: t.javaClass.simpleName)
    }
}
