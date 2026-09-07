package com.tiimoo.cmfstereo

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Minimal root shell.
 *
 * Commands are written to su's stdin rather than passed as `su -c <string>`.
 * That matters: with -c the string goes through argv and is re-joined and
 * re-parsed before a shell sees it, which quietly mangles anything with
 * quoting in it. A sed like
 *
 *     sed -i 's#^ctl|Handset Volume|.*#ctl|Handset Volume|5#' file
 *
 * worked when typed into a terminal and silently failed from the app, so the
 * gain slider appeared to write and never did. Feeding stdin means the root
 * shell parses exactly the text we wrote, the same as typing it.
 */
object Shell {

    data class Result(val ok: Boolean, val out: String)

    fun run(command: String, timeoutSeconds: Long = 180): Result = try {
        val p = ProcessBuilder("su")
            .redirectErrorStream(true)
            .start()

        p.outputStream.bufferedWriter().use { w ->
            w.write(command)
            w.write("\nexit\n")
            w.flush()
        }

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

    fun hasRoot(): Boolean = run("id -u").let { it.ok && it.out.trim().endsWith("0") }
}
