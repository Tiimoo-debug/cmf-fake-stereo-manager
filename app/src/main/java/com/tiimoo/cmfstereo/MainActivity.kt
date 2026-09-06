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
import androidx.lifecycle.lifecycleScope
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
    var console by remember { mutableStateOf("") }
    var gain by remember { mutableStateOf(0f) }
    var gainReadable by remember { mutableStateOf(false) }
    var soloOn by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            busy = true
            val s = withContext(Dispatchers.IO) { StereoCtl.status() }
            status = s
            gainReadable = s.gain != null
            gain = (s.gain ?: 0).toFloat()
            busy = false
        }
    }

    fun action(label: String, block: () -> String) {
        scope.launch {
            busy = true
            val out = withContext(Dispatchers.IO) { block() }
            console = if (out.isBlank()) "$label: done" else out
            val s = withContext(Dispatchers.IO) { StereoCtl.status() }
            status = s
            gainReadable = s.gain != null
            gain = (s.gain ?: 0).toFloat()
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CMF Stereo") },
                actions = {
                    TextButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                !status.rooted -> Problem(
                    "No root access",
                    "Grant this app superuser permission in Magisk, then Refresh."
                )
                !status.installed -> Problem(
                    "Module not installed",
                    "Flash cmf-fake-stereo in Magisk and reboot."
                )
                else -> {
                    StatusCard(status)

                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Earpiece output", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (status.armed) "Armed" else "Disabled",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = status.armed,
                                    enabled = !busy,
                                    onCheckedChange = { on ->
                                        action("power") { StereoCtl.setArmed(on) }
                                    }
                                )
                            }

                            HorizontalDivider()

                            Text(
                                if (gainReadable) "Earpiece gain: ${gain.toInt()} / ${StereoCtl.MAX_GAIN}"
                                else "Earpiece gain: unavailable"
                            )
                            Slider(
                                value = gain,
                                onValueChange = { gain = it },
                                onValueChangeFinished = {
                                    action("gain") { StereoCtl.setGain(gain.toInt()) }
                                },
                                valueRange = 0f..StereoCtl.MAX_GAIN.toFloat(),
                                steps = StereoCtl.MAX_GAIN - 1,
                                // Disabled when the value could not be read. Writing a
                                // displayed-but-unread 0 back is how this zeroed a working
                                // config once; the slider stays inert rather than guess.
                                enabled = !busy && gainReadable
                            )
                            if (!gainReadable) {
                                Text(
                                    "Could not read the gain from actions.conf, so the slider is " +
                                        "disabled rather than risk writing a wrong value over it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    "31 is the hardware ceiling - the register field is 5 bits, so " +
                                        "higher values wrap and get quieter. The earpiece has no " +
                                        "protection circuit; back off at the first buzz.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            HorizontalDivider()

                            Text("Mode", style = MaterialTheme.typography.titleSmall)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf("playback" to "While playing", "always" to "Always on")
                                    .forEachIndexed { i, (value, label) ->
                                        SegmentedButton(
                                            selected = status.mode == value,
                                            onClick = { action("mode") { StereoCtl.setMode(value) } },
                                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                                            enabled = !busy
                                        ) { Text(label) }
                                    }
                            }
                        }
                    }

                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Tools", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        soloOn = !soloOn
                                        action("solo") { StereoCtl.solo(soloOn) }
                                    },
                                    enabled = !busy
                                ) { Text(if (soloOn) "Un-solo" else "Solo earpiece") }
                                FilledTonalButton(
                                    onClick = { action("doctor") { StereoCtl.doctor() } },
                                    enabled = !busy && status.supportsDoctor
                                ) { Text("Doctor") }
                            }
                            if (!status.supportsDoctor) {
                                Text(
                                    "Doctor needs module v0.7.1 or newer - this device has " +
                                        "${status.moduleVersion.ifBlank { "an older build" }}.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { action("log") { StereoCtl.log() } },
                                    enabled = !busy
                                ) { Text("Log") }
                                OutlinedButton(
                                    onClick = { action("probe") { StereoCtl.probe() } },
                                    enabled = !busy
                                ) { Text("Probe") }
                                OutlinedButton(
                                    onClick = { action("report") { StereoCtl.report() } },
                                    enabled = !busy
                                ) { Text("Report") }
                            }
                        }
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
                                Text(
                                    console,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Text(
                        "Both drivers play the same content. This is not left/right stereo - " +
                            "the hardware cannot do that.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(s: StereoCtl.Status) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            StatusRow("Module", if (s.installed) "installed" else "missing", s.installed)
            StatusRow("Armed", if (s.armed) "yes" else "no", s.armed)
            StatusRow("Daemon", if (s.daemonRunning) "running" else "stopped", s.daemonRunning)
            StatusRow("Playback", if (s.playing) "active" else "idle", true)
            StatusRow("Routing", "${s.actionCount} actions", s.actionCount > 0)
            StatusRow("Gain", s.gain?.let { "$it / ${StereoCtl.MAX_GAIN}" } ?: "unreadable", s.gain != null)
            if (s.moduleVersion.isNotBlank()) {
                StatusRow("Version", s.moduleVersion, true)
            }
        }
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
