package com.tiimoo.cmfstereo

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Minimal root shell. The module is driven entirely by `stereoctl` and two
 * text files, so a one-shot `su -c` per command is enough - no persistent
 * shell library, and one less dependency that CI can fail on.
 */
object Shell {

    data class Result(val ok: Boolean, val out: String)

    /**
     * Runs a command as root, with a hard ceiling on how long it may take.
     *
     * The timeout is not belt-and-braces: a script that reads /proc/kmsg, or
     * any other stream, never returns, and without this the UI sits greyed out
     * forever with no way to tell a hang from a slow command.
     */
    fun run(command: String, timeoutSeconds: Long = 180): Result = try {
        val p = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val text = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
        if (p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            Result(p.exitValue() == 0, text.trimEnd())
        } else {
            p.destroyForcibly()
            Result(
                false,
                (text.trimEnd() + "\n\n[timed out after ${timeoutSeconds}s and was killed]").trim()
            )
        }
    } catch (e: Exception) {
        Result(false, e.message ?: "failed to start su")
    }

    fun hasRoot(): Boolean = run("id -u").let { it.ok && it.out.trim() == "0" }
}
