package com.tiimoo.cmfstereo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(StereoCtl.Status()) }
    var busy by remember { mutableStateOf(true) }
    var runningLabel by remember { mutableStateOf("") }
    var console by remember { mutableStateOf("") }
    var gain by remember { mutableStateOf(0f) }
    var gainReadable by remember { mutableStateOf(false) }
    var extendedRange by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var sweeping by remember { mutableStateOf(false) }
    var sweepAt by remember { mutableStateOf(-1) }

    fun sync(s: StereoCtl.Status) {
        status = s
        gainReadable = s.gain != null
        gain = (s.gain ?: 0).toFloat()
        if ((s.gain ?: 0) > StereoCtl.MAX_GAIN) extendedRange = true
    }

    fun refresh() = scope.launch {
        busy = true
        sync(withContext(Dispatchers.IO) { StereoCtl.status() })
        busy = false
    }

    fun action(label: String, block: () -> String) {
        scope.launch {
            busy = true
            runningLabel = label
            val out = withContext(Dispatchers.IO) { block() }
            runningLabel = ""
            console = if (out.isBlank()) "$label: done" else out
            sync(withContext(Dispatchers.IO) { StereoCtl.status() })
            busy = false
        }
    }

    // Steps the gain across its range, holding each value long enough to judge
    // by ear. The mapping is not monotonic on this hardware, so listening to
    // every value is the only way to find the loudest.
    //
    // Each step writes actions.conf as well as the mixer. Writing only the
    // mixer does not work: the daemon re-asserts the configured gain every
    // WATCH_INTERVAL seconds, so every swept value snapped back to the
    // configured one within about two seconds and the sweep measured nothing
    // but the config. The starting value is restored at the end.
    fun sweep(from: Int, to: Int, dwellMs: Long) {
        scope.launch {
            sweeping = true
            val original = status.gain
            val heard = StringBuilder(
                "gain sweep $from..$to, ${dwellMs / 1000}s each\n" +
                    "(writing config + mixer so the daemon does not overwrite each step)\n"
            )
            for (v in from..to) {
                if (!sweeping) break
                sweepAt = v
                withContext(Dispatchers.IO) { StereoCtl.setGain(v, allowExtended = true) }
                heard.append("  $v\n")
                kotlinx.coroutines.delay(dwellMs)
            }
            sweepAt = -1
            sweeping = false
            if (original != null) {
                withContext(Dispatchers.IO) { StereoCtl.setGain(original, allowExtended = true) }
                heard.append("\nrestored $original\n")
            }
            console = heard.toString() +
                "\nSweep done. Set whichever value sounded best on the Speaker tab."
            sync(withContext(Dispatchers.IO) { StereoCtl.status() })
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CMF Stereo") },
                actions = { TextButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (runningLabel.isNotBlank()) {
                    // Some commands take a minute or more. A bare spinner with
                    // every control greyed out is indistinguishable from a hang.
                    Text(
                        "Running: $runningLabel" + when (runningLabel) {
                            "probe" -> "  -  dumping every mixer control, /sys, dmesg and " +
                                "the param XMLs. This takes a minute or two."
                            "report" -> "  -  folding the newest probe into one file."
                            "diff" -> "  -  snapshotting, waiting, then comparing. " +
                                "Start or stop playback now."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            when {
                !status.rooted -> Padded { Problem("No root access", "Grant superuser permission, then Refresh.") }
                !status.installed -> Padded { Problem("Module not installed", "Flash cmf-fake-stereo and reboot.") }
                else -> {
                    TabRow(selectedTabIndex = tab) {
                        listOf("Speaker", "Settings", "Developer", "Guide").forEachIndexed { i, t ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (tab) {
                            0 -> ControlTab(status, busy, gain, gainReadable, extendedRange,
                                onGain = { gain = it },
                                onExtended = { extendedRange = it },
                                // Committing here rather than inside ControlTab
                                // matters: `gain` there is a parameter, captured
                                // when the lambda was composed, and Slider holds
                                // that lambda for the whole gesture - so it
                                // committed the value from before the drag. Read
                                // through the state delegate instead, which is
                                // live at invocation.
                                onGainCommit = {
                                    action("gain") { StereoCtl.setGain(gain.toInt(), extendedRange) }
                                },
                                action = ::action)
                            1 -> AdvancedTab(status, busy, ::action)
                            2 -> ToolsTab(status, busy, ::action, sweeping, sweepAt,
                                onSweep = ::sweep, onStopSweep = { sweeping = false })
                            3 -> GuideTab()
                        }

                        if (console.isNotBlank()) {
                            Card {
                                Column(Modifier.padding(16.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Output", style = MaterialTheme.typography.titleMedium)
                                        TextButton(onClick = { console = "" }) { Text("Clear") }
                                    }
                                    Text(console, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Padded(content: @Composable () -> Unit) =
    Column(Modifier.padding(16.dp)) { content() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlTab(
    s: StereoCtl.Status,
    busy: Boolean,
    gain: Float,
    gainReadable: Boolean,
    extended: Boolean,
    onGain: (Float) -> Unit,
    onExtended: (Boolean) -> Unit,
    onGainCommit: () -> Unit,
    action: (String, () -> String) -> Unit
) {
    val max = if (extended) StereoCtl.MAX_GAIN_EXTENDED else StereoCtl.MAX_GAIN

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            StatusRow("Armed", if (s.armed) "yes" else "no", s.armed)
            StatusRow("Daemon", if (s.daemonRunning) "running" else "stopped", s.daemonRunning)
            StatusRow("Playback", if (s.playing) "active" else "idle", true)
            StatusRow("Mode", s.mode, true)
            StatusRow("Routing", "${s.actionCount} actions", s.actionCount > 0)
            StatusRow("Gain", s.gain?.toString() ?: "unreadable", s.gain != null)
            StatusRow("Guard", if (s.guardBypassed) "bypassed" else "active", true)
            if (s.moduleVersion.isNotBlank()) StatusRow("Module", s.moduleVersion, true)
            StatusRow("App", BuildConfig.VERSION_NAME, true)
        }
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Earpiece output", style = MaterialTheme.typography.titleMedium)
                    Text(if (s.armed) "Armed" else "Disabled", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = s.armed, enabled = !busy,
                    onCheckedChange = { on -> action("power") { StereoCtl.setArmed(on) } })
            }

            HorizontalDivider()

            Text(if (gainReadable) "Earpiece gain: ${gain.toInt()} / $max" else "Earpiece gain: unavailable")
            Slider(
                value = gain.coerceAtMost(max.toFloat()),
                onValueChange = onGain,
                onValueChangeFinished = onGainCommit,
                valueRange = 0f..max.toFloat(),
                steps = max - 1,
                enabled = !busy && gainReadable
            )
            Text(
                "The driver declares 0-18, and that is where loudness rises " +
                    "predictably. Higher values still write, but the mapping is not " +
                    "monotonic there - some larger numbers are quieter than smaller ones.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = extended, onCheckedChange = onExtended, enabled = !busy)
                Text("Allow 19-31 (undefined, non-monotonic)", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "The earpiece has no protection circuit. Back off at the first buzz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            HorizontalDivider()

            Text("Mode", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("playback" to "While playing", "always" to "Always on").forEachIndexed { i, (v, l) ->
                    SegmentedButton(
                        selected = s.mode == v,
                        onClick = { action("mode") { StereoCtl.setMode(v) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        enabled = !busy
                    ) { Text(l) }
                }
            }
            HorizontalDivider()

            Text("Everyday", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { action("solo") { StereoCtl.solo(true) } }, enabled = !busy) { Text("Solo") }
                FilledTonalButton(onClick = { action("unsolo") { StereoCtl.solo(false) } }, enabled = !busy) { Text("Un-solo") }
                FilledTonalButton(
                    onClick = { action("doctor") { StereoCtl.doctor() } },
                    enabled = !busy && s.supportsDoctor
                ) { Text("Doctor") }
            }
            Text(
                "Solo silences the speaker so you can hear the earpiece alone. " +
                    "Doctor explains why it is silent, if it ever is.",
                style = MaterialTheme.typography.bodySmall
            )

            if (s.mode == "playback" && !s.playing) {
                Text(
                    "Nothing is applied in this mode until audio is playing. If the " +
                        "earpiece never engages while music plays, the module cannot " +
                        "see your playback - use Always on, and send me the output of " +
                        "Tools > Playback probe.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AdvancedTab(s: StereoCtl.Status, busy: Boolean, action: (String, () -> String) -> Unit) {
    var interval by remember(s.interval) { mutableStateOf(s.interval) }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Daemon", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { action("apply") { StereoCtl.apply() } }, enabled = !busy) { Text("Apply") }
                FilledTonalButton(onClick = { action("revert") { StereoCtl.revert() } }, enabled = !busy) { Text("Revert") }
                FilledTonalButton(onClick = { action("restart") { StereoCtl.restart() } }, enabled = !busy) { Text("Restart") }
            }

            HorizontalDivider()

            Text("Poll interval (seconds)", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.width(110.dp),
                    singleLine = true,
                    enabled = !busy
                )
                Button(
                    onClick = { action("interval") { StereoCtl.setSetting("WATCH_INTERVAL", interval) } },
                    enabled = !busy && interval.isNotBlank()
                ) { Text("Set") }
            }
            Text(
                "How often the routing is re-asserted. The HAL resets mixer controls " +
                    "on every route change, so this cannot be very large.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            Text("Guard", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.guardBypassed) "Bypassed" else "Active")
                Switch(
                    checked = !s.guardBypassed,
                    enabled = !busy,
                    onCheckedChange = { on -> action("guard") { StereoCtl.guard(on) } }
                )
            }
            Text(
                "When active, the earpiece follows the speaker amp and stays quiet " +
                    "under headphones or Bluetooth.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            Text("Log level", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("debug", "info", "warn").forEachIndexed { i, lvl ->
                    SegmentedButton(
                        selected = s.logLevel == lvl,
                        onClick = { action("log level") { StereoCtl.setSetting("LOG_LEVEL", lvl) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        enabled = !busy
                    ) { Text(lvl) }
                }
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Danger zone", style = MaterialTheme.typography.titleMedium)
            Text(
                "The audio policy overlay must NOT be used on the CMF Phone 1: the mono " +
                    "speaker port is what makes the framework sum L+R, and widening it " +
                    "would drop the right channel entirely. Needs a reboot either way.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { action("xml-patch") { StereoCtl.xmlPatch() } }, enabled = !busy) { Text("XML patch") }
                OutlinedButton(onClick = { action("xml-revert") { StereoCtl.xmlRevert() } }, enabled = !busy) { Text("XML revert") }
                if (s.supportsReset) {
                    OutlinedButton(onClick = { action("reset") { StereoCtl.reset() } }, enabled = !busy) { Text("Reset state") }
                }
            }
        }
    }
}

@Composable
private fun ToolsTab(
    s: StereoCtl.Status,
    busy: Boolean,
    action: (String, () -> String) -> Unit,
    sweeping: Boolean,
    sweepAt: Int,
    onSweep: (Int, Int, Long) -> Unit,
    onStopSweep: () -> Unit
) {
    var ctlName by remember { mutableStateOf("Handset Volume") }
    var ctlValue by remember { mutableStateOf("") }
    var scanPattern by remember { mutableStateOf("rcv|handset|spk") }
    var rawArgs by remember { mutableStateOf("") }
    var diffSeconds by remember { mutableStateOf("20") }
    var sweepFrom by remember { mutableStateOf("0") }
    var sweepTo by remember { mutableStateOf("31") }
    var dwell by remember { mutableStateOf("4") }

    Text(
        "Developer tools. Nothing here is needed for normal use - the Speaker " +
            "tab covers that. These exist for mapping unknown hardware and for " +
            "diagnosing the module itself.",
        style = MaterialTheme.typography.bodySmall
    )

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gain sweep", style = MaterialTheme.typography.titleMedium)
            Text(
                "Steps the earpiece gain across a range, holding each value so you " +
                    "can judge it by ear. The mapping is not monotonic on this " +
                    "hardware, so listening is the only way to find the loudest " +
                    "setting. Play music and use Solo first.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sweepFrom, { sweepFrom = it.filter { c -> c.isDigit() } },
                    label = { Text("From") }, singleLine = true,
                    modifier = Modifier.width(90.dp), enabled = !busy && !sweeping)
                OutlinedTextField(sweepTo, { sweepTo = it.filter { c -> c.isDigit() } },
                    label = { Text("To") }, singleLine = true,
                    modifier = Modifier.width(90.dp), enabled = !busy && !sweeping)
                OutlinedTextField(dwell, { dwell = it.filter { c -> c.isDigit() } },
                    label = { Text("Sec") }, singleLine = true,
                    modifier = Modifier.width(90.dp), enabled = !busy && !sweeping)
            }
            if (sweeping) {
                Text("Now playing gain: $sweepAt", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onStopSweep) { Text("Stop") }
            } else {
                Button(
                    onClick = {
                        onSweep(
                            sweepFrom.toIntOrNull() ?: 0,
                            (sweepTo.toIntOrNull() ?: 31).coerceAtMost(StereoCtl.MAX_GAIN_EXTENDED),
                            ((dwell.toLongOrNull() ?: 4L) * 1000)
                        )
                    },
                    enabled = !busy
                ) { Text("Start sweep") }
            }
            Text(
                "Each step writes the config as well as the mixer, otherwise the " +
                    "daemon re-asserts the configured gain within a couple of seconds " +
                    "and every step snaps back. Your starting value is restored at " +
                    "the end.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mixer control", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(ctlName, { ctlName = it }, label = { Text("Control name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(ctlValue, { ctlValue = it }, label = { Text("Value (blank to read)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        action("ctl") {
                            if (ctlValue.isBlank()) StereoCtl.ctlGet(ctlName)
                            else StereoCtl.ctlSet(ctlName, ctlValue)
                        }
                    },
                    enabled = !busy && ctlName.isNotBlank()
                ) { Text(if (ctlValue.isBlank()) "Read" else "Write") }
                OutlinedButton(onClick = { action("dump") { StereoCtl.dump() } }, enabled = !busy) { Text("Dump all") }
                OutlinedButton(onClick = { action("live gain") { StereoCtl.liveGain() } }, enabled = !busy) { Text("Live gain") }
            }

            HorizontalDivider()

            OutlinedTextField(scanPattern, { scanPattern = it }, label = { Text("Scan pattern") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { action("scan") { StereoCtl.scan(scanPattern) } }, enabled = !busy) { Text("Scan") }
                OutlinedTextField(diffSeconds, { diffSeconds = it.filter { c -> c.isDigit() } },
                    label = { Text("Diff s") }, singleLine = true, modifier = Modifier.width(100.dp), enabled = !busy)
                Button(
                    onClick = { action("diff") { StereoCtl.diff(diffSeconds.toIntOrNull() ?: 20) } },
                    enabled = !busy
                ) { Text("Diff") }
            }
            Text(
                "Diff snapshots the mixer, waits, then shows what changed - start or " +
                    "stop playback during the wait to find which controls carry media.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hardware dump", style = MaterialTheme.typography.titleMedium)
            Text(
                "Only needed on hardware this module has not been mapped on. The " +
                    "CMF Phone 1 routing is already known and shipped.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { action("probe") { StereoCtl.probe() } }, enabled = !busy) { Text("Probe (slow)") }
                OutlinedButton(onClick = { action("report") { StereoCtl.report() } }, enabled = !busy) { Text("Report") }
                OutlinedButton(
                    onClick = { action("playback probe") { StereoCtl.playbackProbe() } },
                    enabled = !busy
                ) { Text("Playback probe") }
            }
        }
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Run any stereoctl command", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(rawArgs, { rawArgs = it }, label = { Text("arguments") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { action("stereoctl $rawArgs") { StereoCtl.raw(rawArgs) } },
                    enabled = !busy && rawArgs.isNotBlank()) { Text("Run") }
                OutlinedButton(onClick = { action("help") { StereoCtl.help() } }, enabled = !busy) { Text("Help") }
            }
        }
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Config files", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { action("stereo.conf") { StereoCtl.readConf() } }, enabled = !busy) { Text("stereo.conf") }
                OutlinedButton(onClick = { action("actions.conf") { StereoCtl.readActions() } }, enabled = !busy) { Text("actions.conf") }
                OutlinedButton(onClick = { action("log") { StereoCtl.log() } }, enabled = !busy) { Text("Log") }
            }
        }
    }
}

@Composable
private fun GuideTab() {
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search the guide") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    val q = query.trim().lowercase()
    Help.sections.forEach { (section, entries) ->
        val matching = entries.filter {
            q.isBlank() || it.title.lowercase().contains(q) ||
                it.body.lowercase().contains(q) || it.tag.contains(q)
        }
        if (matching.isNotEmpty()) {
            Text(section, style = MaterialTheme.typography.titleMedium)
            matching.forEach { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { open = if (open == e.title) null else e.title }) {
                                Text(if (open == e.title) "Hide" else "Show")
                            }
                        }
                        // Searching implies you want the answer, not another tap.
                        if (open == e.title || q.isNotBlank()) {
                            Text(
                                e.body,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (q.isNotBlank() && Help.sections.none { (_, es) ->
            es.any {
                it.title.lowercase().contains(q) || it.body.lowercase().contains(q) ||
                    it.tag.contains(q)
            }
        }) {
        Text("Nothing matches \"$query\".", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusRow(label: String, value: String, good: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun Problem(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
