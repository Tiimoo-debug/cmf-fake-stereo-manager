package com.tiimoo.cmfstereo

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Minimal root shell. The module is driven entirely by `stereoctl` and two
 * text files, so a one-shot `su -c` per command is enough - no persistent
 * shell library, and one less dependency that CI can fail on.
 */
object Shell {

    data class Result(val ok: Boolean, val out: String)

    fun run(command: String): Result = try {
        val p = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val text = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
        Result(p.waitFor() == 0, text.trimEnd())
    } catch (e: Exception) {
        Result(false, e.message ?: "failed to start su")
    }

    fun hasRoot(): Boolean = run("id -u").let { it.ok && it.out.trim() == "0" }
}
