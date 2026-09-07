package com.tiimoo.cmfstereo

/**
 * In-app reference. Everything here is what was actually measured on the
 * CMF Phone 1 (A015 / Tetris, MT6878) rather than general Android lore -
 * where a claim is device-specific it says so.
 */
data class HelpEntry(val title: String, val body: String, val tag: String)

object Help {

    val sections: List<Pair<String, List<HelpEntry>>> = listOf(

        "Start here" to listOf(
            HelpEntry(
                "What this module does",
                "It plays media through the earpiece as well as the bottom speaker, " +
                    "so both drivers are producing sound at once. Louder, fuller and " +
                    "taller than the single bottom-firing speaker.",
                "overview intro what"
            ),
            HelpEntry(
                "What it is NOT",
                "It is not left/right stereo. Both transducers are fed from the same " +
                    "mixer channel (DL_24CH_CH1), and no arrangement of the available " +
                    "controls puts different channels on them. That was measured, not " +
                    "assumed - see 'Why no true stereo'.",
                "stereo fake pseudo channels"
            ),
            HelpEntry(
                "Everyday use",
                "There isn't any. Flash the module, reboot, play something. The " +
                    "routing applies while audio plays and releases when it stops. " +
                    "The app is optional - the module never needs it.",
                "usage daily normal"
            ),
            HelpEntry(
                "If the earpiece goes silent",
                "Speaker tab > Doctor. It walks the six things that can cause " +
                    "silence - kill switch, dead daemon, unusable tinymix, idle " +
                    "playback, a blocking guard, a control that will not take its " +
                    "value - and names the one responsible.",
                "silent broken troubleshoot doctor"
            )
        ),

        "Commands" to listOf(
            HelpEntry("status", "Module, daemon and live control state. 'want=' is what the config asks for, 'now=' is what the mixer actually holds; a mismatch is the fault.", "status"),
            HelpEntry("doctor", "Diagnoses silence in six ordered steps and names the culprit. Needs module v0.7.1+. It deliberately does nothing when playback is idle - a diagnostic that changes what it measures is worse than none.", "doctor diagnose"),
            HelpEntry("on / off", "Arm or disarm. 'off' stops the daemon and restores every control it touched. It survives a reboot, so a disarmed module stays disarmed until you arm it again.", "on off arm disarm enable"),
            HelpEntry("solo / unsolo", "Silences the speaker amp so you can hear the earpiece alone. It bypasses the guard first, because disabling the amp is otherwise read as 'headphones took over' and would silence the earpiece too.", "solo audition listen"),
            HelpEntry("guard on / off", "The guard applies the routing only while the speaker amp is enabled, so the earpiece follows the speaker and stays quiet under headphones or Bluetooth. Bypass it when you want the routing regardless.", "guard headphones bluetooth"),
            HelpEntry("apply / revert", "One-shot apply of the configured actions, or restore of the values saved before the module touched them.", "apply revert restore"),
            HelpEntry("restart", "Restarts the watcher daemon. Needed if you edited config by hand and want it picked up immediately.", "restart daemon"),
            HelpEntry("log [n]", "Tail the daemon log. Set LOG_LEVEL=debug in stereo.conf for more.", "log logs debug"),
            HelpEntry("reset", "Clears saved originals, guard bypass, solo state and the kill switch, then restarts. The saved originals survive reinstalls, so a bad one makes every revert restore a wrong value - this is the way out. Needs v0.7.1+.", "reset clear stuck"),
            HelpEntry("ctl NAME [VALUE]", "Read or write one mixer control. With no value it reads. This is the lowest-level tool and the one most worth understanding.", "ctl mixer control"),
            HelpEntry("scan [pattern]", "Search the mixer for controls matching a pattern. Use it to find candidates on unfamiliar hardware.", "scan search find"),
            HelpEntry("dump", "Every mixer control and its current value. On this device that is about 1400 lines.", "dump all controls"),
            HelpEntry("diff [seconds]", "Snapshots the mixer, waits, then shows what changed. Start or stop playback during the wait: this is how the media path on this device was found in the first place.", "diff snapshot compare"),
            HelpEntry("probe [dir]", "Full hardware dump - ALSA topology, all mixer controls, amplifier drivers, HAL configs, MediaTek's AudioParam tree. Only needed on hardware the module has not been mapped on. It is slow (a minute or two).", "probe dump hardware"),
            HelpEntry("report", "Folds the newest probe into one shareable text file at /sdcard/cmf-stereo-report.txt.", "report share"),
            HelpEntry("xml-patch / xml-revert", "Overlays an audio policy that widens the speaker port to stereo. DO NOT use it on the CMF Phone 1: the mono speaker port is what makes the framework sum L+R, so widening it would drop the right channel entirely. It exists for other hardware.", "xml policy danger")
        ),

        "Settings (stereo.conf)" to listOf(
            HelpEntry("MODE", "'playback' applies the routing only while audio plays - the default, and kinder to the earpiece. 'always' keeps it applied permanently.", "mode playback always"),
            HelpEntry("PLAYBACK_CTL / PLAYBACK_VALUE", "How 'audio is playing' is detected. Empty means scan /proc/asound for a RUNNING substream, which misses DSP-offload playback entirely. On this device media IS offloaded, so it is set to dsp_music_runtime_en - without which MODE=playback never engages while MODE=always works.", "playback detect offload"),
            HelpEntry("GUARD_CTL / GUARD_VALUE", "Apply the routing only while this control holds this value. Set to aw_dev_0_switch = Enable here, so the earpiece follows the speaker amp.", "guard"),
            HelpEntry("WATCH_INTERVAL", "How often the routing is re-asserted, in seconds. The HAL resets mixer controls on every route change, so this cannot be large. 2 is the default.", "interval poll"),
            HelpEntry("LOG_LEVEL", "debug, info or warn.", "log level"),
            HelpEntry("actions.conf syntax", "One action per line, applied in order:\n\n  ctl|<mixer control>|<value>\n  sysfs|<path>|<value>\n  prop|<property>|<value>\n\nThe original value of everything touched is saved and restored on stop.", "actions config syntax")
        ),

        "This device" to listOf(
            HelpEntry(
                "How the earpiece is driven",
                "Media never touches the internal codec on this phone. It runs:\n\n" +
                    "  DL_24CH_CH1/CH2 -> I2SOUT4 -> Awinic aw_dev_0 -> speaker\n\n" +
                    "The earpiece hangs off the internal codec's DAC (ADDA_DL), whose " +
                    "input switches are all off - which is why pointing RCV Mux at the " +
                    "receiver alone produces silence. The switch that matters is " +
                    "ADDA_DL_CH1 DL_24CH_CH1: it feeds that DAC from the same stream " +
                    "already going to the speaker amp. Then RCV Mux = Voice Playback " +
                    "routes it to the earpiece.",
                "routing how works adda rcv"
            ),
            HelpEntry(
                "Why no true stereo",
                "Four independent findings:\n\n" +
                    "1. Speaker takes I2SOUT4_CH1 <- DL_24CH_CH1 only, and I2SOUT4_CH1 " +
                    "has no DL_24CH_CH2 source available.\n" +
                    "2. Earpiece takes RCV <- ADDA_DL_CH1 <- DL_24CH_CH1 only. Feeding " +
                    "ADDA_DL_CH2 instead leaves the path unpowered.\n" +
                    "3. Only one Awinic device (aw_dev_0) - no second amp channel.\n" +
                    "4. MediaTek's 2nd-loudspeaker parameters are present but entirely " +
                    "zeroed, and 2nd-ACF is a filter feature, not a routing one.\n\n" +
                    "True stereo would need a custom kernel adding a DAPM route.",
                "stereo why not channels split"
            ),
            HelpEntry(
                "Earpiece gain",
                "'Handset Volume' advertises range 0-18, and that is where loudness " +
                    "rises predictably. Values 19-31 still write - the field is 5 bits " +
                    "and the vendor HAL parks these controls at 31 - but the mapping " +
                    "there is NOT monotonic: some higher values are quieter than lower " +
                    "ones. 40 wraps to 8, measured. Use Developer > Gain sweep to find " +
                    "the loudest by ear.",
                "gain volume loud 31 18"
            ),
            HelpEntry(
                "Safety",
                "The earpiece is a receiver: no protection circuit, no excursion limit, " +
                    "no thermal cutoff. Distortion, buzz or rattle means back off " +
                    "immediately - that damage is permanent, and it also costs you the " +
                    "earpiece for phone calls. If you run it loud for hours, check " +
                    "whether the top of the phone gets warm.",
                "safety damage warning heat"
            ),
            HelpEntry(
                "Recovery",
                "If a boot ever fails, the module disarms itself after two failed " +
                    "boots. Otherwise: adb shell, then\n\n" +
                    "  touch /data/adb/modules/cmf_stereo/disable\n\n" +
                    "and reboot. Magisk and KernelSU both honour that file.",
                "recovery bootloop disable"
            )
        )
    )
}
