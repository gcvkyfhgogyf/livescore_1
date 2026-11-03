@file:OptIn(ExperimentalMaterial3Api::class)

package com.livescore.floating

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var pendingStart = false

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        val prefs = Prefs(this)
        setContent {
            var theme by remember { mutableIntStateOf(prefs.theme) }
            AppTheme(theme) {
                val running by OverlayService.running.collectAsState()
                App(
                    prefs = prefs,
                    running = running,
                    onToggle = { setOverlay(it) },
                    theme = theme,
                    onTheme = { theme = it; prefs.theme = it }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MatchStore.start(this)
    }

    override fun onStop() {
        MatchStore.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (pendingStart && Settings.canDrawOverlays(this)) OverlayService.start(this)
        pendingStart = false
    }

    private fun setOverlay(on: Boolean) {
        if (!on) {
            OverlayService.stop(this)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true
            Toast.makeText(this, "Allow “Display over other apps” for this app", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        OverlayService.start(this)
    }
}

// ------------------------------------------------------------------ theme

@Composable
fun AppTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF00E676), onPrimary = Color.Black,
        background = Color(0xFF0B1117), surface = Color(0xFF111A22),
        surfaceVariant = Color(0xFF1B2833)
    ) else lightColorScheme(
        primary = Color(0xFF00A152), background = Color(0xFFF4F7F6),
        surface = Color.White, surfaceVariant = Color(0xFFE3EBE8)
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

// ------------------------------------------------------------------ app

@Composable
fun App(
    prefs: Prefs,
    running: Boolean,
    onToggle: (Boolean) -> Unit,
    theme: Int,
    onTheme: (Int) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    val loading by MatchStore.loading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "Live Scores" else "Settings", fontWeight = FontWeight.Bold) },
                actions = {
                    if (tab == 0) IconButton(onClick = { MatchStore.refresh() }) {
                        Icon(Icons.Filled.Refresh, "Refresh", modifier = Modifier.alpha(if (loading) 0.4f else 1f))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Live") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (tab == 0) LiveScreen(running, onToggle, apiKey.isNotBlank())
            else SettingsScreen(prefs, theme, onTheme, apiKey) { apiKey = it }
        }
    }
}

@Composable
fun LiveScreen(running: Boolean, onToggle: (Boolean) -> Unit, hasKey: Boolean) {
    val matches by MatchStore.matches.collectAsState()
    val pinned by MatchStore.pinnedId.collectAsState()
    val err by MatchStore.error.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Floating score widget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (running) "Showing above other apps. Tap ★ on a match to pin it."
                            else "Turn on to keep the score above all apps.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = running, onCheckedChange = onToggle)
                }
            }
        }
        if (!hasKey) item {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Demo mode: simulated matches. Add your API key in Settings for real live scores.",
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
                )
            }
        }
        err?.let { msg ->
            item { Text("⚠ $msg", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        if (matches.isEmpty()) item {
            Text(
                "No live matches right now.",
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(matches, key = { it.id }) { m ->
            MatchCard(m, m.id == pinned) { MatchStore.togglePin(m.id) }
        }
    }
}

@Composable
fun MatchCard(m: Match, pinned: Boolean, onPin: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.league, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ClockBadge(m)
                IconButton(onClick = onPin) {
                    Icon(
                        Icons.Filled.Star, "Pin to widget",
                        tint = if (pinned) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.home, modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                    fontWeight = FontWeight.SemiBold, maxLines = 2
                )
                Text(
                    "${m.homeScore} - ${m.awayScore}",
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold
                )
                Text(m.away, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(top = 12.dp, end = 12.dp)) {
                    if (m.events.isEmpty()) Text("No events yet", style = MaterialTheme.typography.bodySmall)
                    m.events.reversed().forEach {
                        Text(
                            "${it.icon}  ${it.minute}'  ${it.player} · ${it.team}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClockBadge(m: Match) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        0.25f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha"
    )
    Row(
        Modifier
            .background(
                if (m.isLive) Color(0x33D32F2F) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (m.isLive) {
            Box(Modifier.size(7.dp).alpha(a).background(Color(0xFFE53935), CircleShape))
            Spacer(Modifier.size(5.dp))
        }
        Text(m.clock, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsScreen(prefs: Prefs, theme: Int, onTheme: (Int) -> Unit, apiKey: String, onKey: (String) -> Unit) {
    var keyText by remember { mutableStateOf(apiKey) }
    var opacity by remember { mutableStateOf(prefs.opacity.toFloat()) }
    var poll by remember { mutableIntStateOf(prefs.pollSec) }
    var notify by remember { mutableStateOf(prefs.notifyGoals) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Live data (API-Football)", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = keyText, onValueChange = { keyText = it },
            label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Free key: dashboard.api-football.com. Leave empty for demo mode.") }
        )
        androidx.compose.material3.Button(onClick = {
            prefs.apiKey = keyText; onKey(keyText.trim()); MatchStore.refresh()
        }) { Text("Save & refresh") }

        Spacer(Modifier.height(12.dp))
        Text("Theme", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("System", "Light", "Dark").forEachIndexed { i, s ->
                FilterChip(selected = theme == i, onClick = { onTheme(i) }, label = { Text(s) })
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Widget opacity: ${opacity.toInt()}%", fontWeight = FontWeight.Bold)
        Slider(value = opacity, onValueChange = { opacity = it; prefs.opacity = it.toInt() }, valueRange = 30f..100f)

        Spacer(Modifier.height(12.dp))
        Text("Update every (real API)", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60).forEach { s ->
                FilterChip(selected = poll == s, onClick = { poll = s; prefs.pollSec = s }, label = { Text("${s}s") })
            }
        }
        Text(
            "Longer interval = less battery and fewer API calls (free plan: 100 requests/day).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Goal notifications", fontWeight = FontWeight.Bold)
                Text("Get an alert with scorer and minute.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = notify, onCheckedChange = { notify = it; prefs.notifyGoals = it })
        }
    }
}
