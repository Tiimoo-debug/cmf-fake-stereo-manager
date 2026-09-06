package com.tiimoo.cmfstereo

/**
 * Everything the UI needs from the module, expressed as shell calls.
 *
 * Config lives in /data/adb/cmf-stereo and is read by the daemon every cycle,
 * so writes here take effect without restarting anything.
 */
object StereoCtl {

    const val MODULE_DIR = "/data/adb/modules/cmf_stereo"
    const val DATA_DIR = "/data/adb/cmf-stereo"
    private const val CTL = "$MODULE_DIR/scripts/stereoctl"
    private const val ACTIONS = "$DATA_DIR/actions.conf"
    private const val CONF = "$DATA_DIR/stereo.conf"

    /** Highest value the receiver's 5-bit gain field accepts. Above this it wraps. */
    const val MAX_GAIN = 31

    data class Status(
        val installed: Boolean = false,
        val rooted: Boolean = false,
        val armed: Boolean = false,
        val daemonRunning: Boolean = false,
        val playing: Boolean = false,
        val mode: String = "playback",
        /** null means "could not be read" - which is NOT the same as zero. */
        val gain: Int? = null,
        val actionCount: Int = 0,
        val moduleVersion: String = "",
        val supportsDoctor: Boolean = false,
        val raw: String = ""
    )

    private fun ctl(args: String) = Shell.run("sh $CTL $args")

    fun installed(): Boolean = Shell.run("test -f $CTL && echo yes").out.trim() == "yes"

    fun status(): Status {
        if (!Shell.hasRoot()) return Status(rooted = false)
        if (!installed()) return Status(rooted = true, installed = false)

        val raw = ctl("status").out
        return Status(
            installed = true,
            rooted = true,
            armed = raw.contains(Regex("""armed:\s+yes""")),
            daemonRunning = raw.contains(Regex("""daemon:\s+running""")),
            playing = raw.contains(Regex("""playback:\s+active""")),
            mode = Regex("""mode:\s+(\w+)""").find(raw)?.groupValues?.get(1) ?: "playback",
            gain = readGain(),
            actionCount = Regex("""\((\d+) configured\)""").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            moduleVersion = readModuleVersion(),
            supportsDoctor = supportsCommand("doctor"),
            raw = raw
        )
    }

    fun setArmed(on: Boolean) = ctl(if (on) "on" else "off").out

    fun solo(on: Boolean) = ctl(if (on) "solo" else "unsolo").out

    fun doctor() = ctl("doctor").out

    fun log(lines: Int = 60) = ctl("log $lines").out

    fun probe() = ctl("probe").out

    fun report() = ctl("report").out

    /**
     * Returns null when the value cannot be read, so the UI can say so
     * instead of showing 0 - a displayed 0 that then gets written back is how
     * this silently zeroed a working config.
     *
     * No `grep -m1`: Android's grep is toybox and does not take the value
     * jammed onto the flag. `head -n 1` is portable and cannot fail quietly.
     */
    fun readGain(): Int? {
        val r = Shell.run("grep '^ctl|Handset Volume|' $ACTIONS 2>/dev/null | head -n 1 | cut -d'|' -f3")
        if (!r.ok) return null
        return r.out.trim().toIntOrNull()
    }

    fun readModuleVersion(): String =
        Shell.run("grep '^version=' $MODULE_DIR/module.prop 2>/dev/null | cut -d= -f2")
            .out.trim()

    /** Older modules lack the newer subcommands; asking is cheaper than guessing. */
    fun supportsCommand(name: String): Boolean =
        Shell.run("sh $CTL --help 2>&1 | grep -c '^  $name'").out.trim().toIntOrNull()?.let { it > 0 } ?: false

    /**
     * Rewrite the gain in actions.conf. Clamped to the hardware ceiling: the
     * register field is 5 bits, so 40 wraps to 8 and gets quieter, not louder.
     */
    /**
     * Writes the gain to actions.conf, then asks the daemon to apply it now.
     *
     * Without the apply, the new value only reaches the mixer on the daemon's
     * next cycle - and in MODE=playback that is never, unless audio happens to
     * be running. The slider then looks broken: the file changes, the loudness
     * does not.
     */
    fun setGain(value: Int): String {
        val v = value.coerceIn(0, MAX_GAIN)
        // '#' as the sed delimiter: the pattern itself contains '|'.
        val w = Shell.run("sed -i 's#^ctl|Handset Volume|.*#ctl|Handset Volume|$v#' $ACTIONS")
        if (!w.ok) return w.out
        // Push it straight at the mixer too, so the change is audible now
        // rather than at some later route change.
        val live = Shell.run("sh $CTL ctl 'Handset Volume' $v")
        return "gain -> $v\n${live.out}"
    }

    fun setMode(mode: String): String {
        val m = if (mode == "always") "always" else "playback"
        return Shell.run("sed -i 's/^MODE=.*/MODE=$m/' $CONF").out
    }

    /** Live mixer value, as opposed to what actions.conf says it should be. */
    fun liveGain(): String = Shell.run("sh $CTL ctl 'Handset Volume'").out

    fun readConf(): String = Shell.run("cat $CONF 2>/dev/null").out

    fun readActions(): String = Shell.run("cat $ACTIONS 2>/dev/null").out
}
