package com.hietech.andromeda

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

@Composable
fun FocusScreen(vm: LauncherViewModel, back: () -> Unit) {
    val pr by vm.profiles.collectAsState()
    val am by vm.activeManual.collectAsState()
    val apps by vm.visible.collectAsState()
    var open by remember { mutableIntStateOf(-1) }
    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
        item {
            Text("Focus profiles", color = HieWhite, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = HieFont)
            Text(
                "While a profile is active, Andromeda refuses to launch its selected apps. This is a launcher restriction only: " +
                    "it does not freeze or disable apps, and they can still be opened from notifications, recents or other launchers.",
                color = HieWhite.copy(0.6f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        item { UsageCard(vm) }
        itemsIndexed(pr) { i, p ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).glass(false).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, color = HieWhite, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Choice(if (am == i) "Active" else "Activate", am == i) { vm.setActive(if (am == i) -1 else i) }
                    Choice(if (open == i) "Close" else "Edit", false) { open = if (open == i) -1 else i }
                }
                val sched = if (p.start >= 0) "  •  ${fmt(p.start)}–${fmt(p.end)}" else ""
                Text("${p.pkgs.size} apps blocked$sched", color = HieWhite.copy(0.6f), fontSize = 12.sp)
                if (open == i) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice("No schedule", p.start < 0) { vm.updateProfile(i, p.copy(start = -1, end = -1)) }
                        Choice("Scheduled", p.start >= 0) { if (p.start < 0) vm.updateProfile(i, p.copy(start = 22 * 60, end = 7 * 60)) }
                    }
                    if (p.start >= 0) {
                        TimeStepper("Start", p.start) { vm.updateProfile(i, p.copy(start = it)) }
                        TimeStepper("End", p.end) { vm.updateProfile(i, p.copy(end = it)) }
                    }
                    Text("Blocked apps", color = Look.accent, modifier = Modifier.padding(top = 8.dp))
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        apps.forEach { a ->
                            val on = a.pkg in p.pkgs
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(on, { c -> vm.updateProfile(i, p.copy(pkgs = if (c) p.pkgs + a.pkg else p.pkgs - a.pkg)) })
                                Image(a.icon, null, Modifier.size(28.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(a.label, color = HieWhite, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        item { TextButton(back) { Text("Back", color = HieBlue) } }
    }
}

@Composable
private fun TimeStepper(label: String, v: Int, set: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${fmt(v)}", color = HieWhite, modifier = Modifier.weight(1f))
        TextButton({ set((v - 30 + 1440) % 1440) }) { Text("-30m") }
        TextButton({ set((v + 30) % 1440) }) { Text("+30m") }
    }
}

@Suppress("DEPRECATION")
private fun hasUsageAccess(ctx: Context): Boolean {
    val ops = ctx.getSystemService(AppOpsManager::class.java)
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
}

@Composable
private fun UsageCard(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val apps by vm.visible.collectAsState()
    var granted by remember { mutableStateOf(hasUsageAccess(ctx)) }
    var stats by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasUsageAccess(ctx) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(granted) {
        if (granted) stats = withContext(Dispatchers.IO) {
            val usm = ctx.getSystemService(UsageStatsManager::class.java)
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            usm.queryAndAggregateUsageStats(start, System.currentTimeMillis()).values
                .filter { it.totalTimeInForeground > 0 }
                .map { it.packageName to it.totalTimeInForeground }
                .sortedByDescending { it.second }.take(8)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).glass(false).padding(14.dp)) {
        Text("Today's usage", color = HieWhite, fontWeight = FontWeight.Medium)
        if (!granted) {
            Text("Needs Usage access, which Android only lets you grant in system settings. Read once when this screen opens; no background tracking.",
                color = HieWhite.copy(0.6f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Button({ ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                colors = ButtonDefaults.buttonColors(containerColor = Look.accent)) { Text("Grant usage access") }
        } else if (stats.isEmpty()) {
            Text("No usage recorded yet today.", color = HieWhite.copy(0.6f), fontSize = 12.sp)
        } else {
            stats.forEach { (pkg, ms) ->
                val label = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
                val min = ms / 60_000
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(label, color = HieWhite, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m", color = HieBlue, fontSize = 13.sp)
                }
            }
        }
    }
}
