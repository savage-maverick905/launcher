package com.hietech.andromeda

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val HieOrange = Color(0xFFED6627)
val HieBlue = Color(0xFF00ADEF)
val HieWhite = Color(0xFFF2F2F2)
val HieDark = Color(0xFF1E1E1E)
val SoftOrange = Color(0xFFFFAD80)
// Poppins is preferred; bundle it in res/font later. SansSerif is the fallback.
val HieFont = FontFamily.SansSerif

val Accents = listOf(HieOrange, HieBlue, SoftOrange, Color(0xFF7C4DFF), Color(0xFF34C759))
object Look {
    var accent by mutableStateOf(HieOrange)
    var glass by mutableFloatStateOf(0.08f)
}

enum class Screen { Home, Library, Settings, Widgets, Focus }

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private var screen by mutableStateOf(Screen.Home)
    private var pendingId = -1
    private var pendingProvider: AppWidgetProviderInfo? = null
    private lateinit var bindLauncher: ActivityResultLauncher<Intent>
    private lateinit var configLauncher: ActivityResultLauncher<Intent>

    private fun startAdd(p: AppWidgetProviderInfo) {
        val id = vm.host.allocateAppWidgetId()
        pendingId = id; pendingProvider = p
        if (vm.mgr.bindAppWidgetIdIfAllowed(id, p.provider)) afterBind()
        else bindLauncher.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider)
        )
    }
    private fun afterBind() {
        val p = pendingProvider ?: return
        val cfg = p.configure
        if (cfg != null) {
            try {
                configLauncher.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(cfg)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                )
            } catch (_: Exception) { finishAdd() }
        } else finishAdd()
    }
    private fun finishAdd() {
        if (pendingId != -1) vm.addWidget(pendingId)
        pendingId = -1; pendingProvider = null; screen = Screen.Home
    }
    private fun cancelAdd() {
        if (pendingId != -1) try { vm.host.deleteAppWidgetId(pendingId) } catch (_: Exception) {}
        pendingId = -1; pendingProvider = null
    }

    override fun onStart() { super.onStart(); try { vm.host.startListening() } catch (_: Exception) {} }
    override fun onStop() { super.onStop(); try { vm.host.stopListening() } catch (_: Exception) {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) afterBind() else cancelAdd()
        }
        configLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) finishAdd() else cancelAdd()
        }
        setContent {
            val ai by vm.accentIdx.collectAsState()
            val g by vm.glass.collectAsState()
            LaunchedEffect(ai, g) { Look.accent = Accents[ai.coerceIn(0, Accents.size - 1)]; Look.glass = g }
            MaterialTheme(colorScheme = darkColorScheme(primary = HieOrange, secondary = HieBlue, background = HieDark, surface = HieDark)) {
                val bl by vm.blocked.collectAsState()
                bl?.let { (a, name) ->
                    AlertDialog(
                        onDismissRequest = { vm.blocked.value = null },
                        confirmButton = { TextButton({ vm.emergencyOverride() }) { Text("Emergency override (5 min)", color = Look.accent) } },
                        dismissButton = { TextButton({ vm.blocked.value = null }) { Text("Stay focused") } },
                        title = { Text("${a.label} is blocked") },
                        text = { Text("$name focus is active. An override pauses all focus blocking for 5 minutes; then tap the app again.") }
                    )
                }
                BackHandler(screen != Screen.Home) { screen = Screen.Home }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1620), HieDark, Color(0xFF2A1509))))) {
                    when (screen) {
                        Screen.Home -> HomeScreen(vm, { screen = Screen.Library }, { screen = Screen.Settings }, { screen = Screen.Widgets })
                        Screen.Library -> LibraryScreen(vm, { screen = Screen.Settings }) { screen = Screen.Home }
                        Screen.Widgets -> WidgetPicker(vm, { startAdd(it) }) { screen = Screen.Home }
                        Screen.Settings -> SettingsScreen(vm, { screen = Screen.Focus }) { screen = Screen.Home }
                        Screen.Focus -> FocusScreen(vm) { screen = Screen.Home }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) screen = Screen.Home
    }
}

@Composable
fun Modifier.glass(light: Boolean, radius: Int = 24): Modifier =
    this.clip(RoundedCornerShape(radius.dp))
        .background(Color.White.copy(alpha = Look.glass + if (light) 0.04f else 0f))
        .border(1.dp, Brush.verticalGradient(listOf(Look.accent.copy(0.6f), Color.White.copy(0.1f))), RoundedCornerShape(radius.dp))

@Composable
fun LibraryScreen(vm: LauncherViewModel, openSettings: () -> Unit, close: () -> Unit) {
    val ctx = LocalContext.current
    val apps by vm.visible.collectAsState()
    val favs by vm.favKeys.collectAsState()
    val light by vm.lightMode.collectAsState()
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    fun letter(a: AppItem): Char = a.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
    val rows: List<Any> = remember(filtered, query) {
        if (query.isNotBlank()) filtered
        else buildList {
            var cur: Char? = null
            filtered.forEach { a -> val l = letter(a); if (l != cur) { cur = l; add(l.toString()) }; add(a) }
        }
    }
    val letters = remember(rows) { rows.filterIsInstance<String>() }
    val letterIndex = remember(rows) { rows.withIndex().filter { it.value is String }.associate { it.value as String to it.index } }
    var h by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    fun jump(y: Float) {
        if (letters.isEmpty()) return
        val i = ((y / h) * letters.size).toInt().coerceIn(0, letters.size - 1)
        scope.launch { listState.scrollToItem(letterIndex[letters[i]] ?: 0) }
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search apps") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HieBlue, unfocusedBorderColor = Color.White.copy(0.25f))
            )
            TextButton(openSettings) { Text("⚙", color = HieWhite, fontSize = 22.sp) }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(end = 28.dp)) {
                items(rows) { r ->
                    if (r is String) Text(r, color = Look.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = HieFont, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    else AppRow(r as AppItem, r.key in favs, vm) { query = ""; close() }
                }
            }
        }
        if (query.isBlank()) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).fillMaxHeight(0.8f).width(28.dp)
                    .glass(light, 14)
                    .onSizeChanged { h = it.height.coerceAtLeast(1) }
                    .pointerInput(letters) { detectTapGestures { jump(it.y) } }
                    .pointerInput(letters) { detectVerticalDragGestures(onDragStart = { jump(it.y) }) { c, _ -> jump(c.position.y) } },
                verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally
            ) { letters.forEach { Text(it, color = HieBlue, fontSize = 10.sp, fontWeight = FontWeight.Medium) } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(a: AppItem, fav: Boolean, vm: LauncherViewModel, onLaunched: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val layout by vm.layout.collectAsState()
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { vm.launch(a); onLaunched() }, onLongClick = { menu = true })
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(a.icon, null, Modifier.size(44.dp))
            Spacer(Modifier.width(16.dp))
            Text(a.label, color = HieWhite, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = HieFont)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text(if (fav) "Remove from dock" else "Add to dock") }, { vm.toggleFav(a); menu = false })
            layout.indices.forEach { i ->
                DropdownMenuItem({ Text("Add to home page ${i + 1}") }, { vm.addToPage(a, i); menu = false })
            }
            DropdownMenuItem({ Text("Hide") }, { vm.hide(a); menu = false })
            DropdownMenuItem({ Text("App info") }, {
                menu = false
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            DropdownMenuItem({ Text("Uninstall") }, {
                menu = false
                ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
    }
}

@Composable
fun SettingsScreen(vm: LauncherViewModel, openFocus: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val light by vm.lightMode.collectAsState()
    val hidden by vm.hiddenApps.collectAsState()
    val pinStored by vm.pin.collectAsState()
    var unlocked by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val showHidden = pinStored.isEmpty() || unlocked
    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
        item {
            Text("Andromeda Settings", color = HieWhite, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = HieFont)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().glass(light).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lightweight mode", color = HieWhite)
                    Text("Slightly stronger glass fill, no heavy effects", color = HieWhite.copy(0.6f), fontSize = 12.sp)
                }
                Switch(light, { vm.setLight(it) })
            }
            Spacer(Modifier.height(16.dp))
            val ai by vm.accentIdx.collectAsState()
            Text("Accent colour", color = HieWhite)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accents.forEachIndexed { i, c ->
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(c)
                        .border(if (i == ai) 3.dp else 0.dp, Color.White, RoundedCornerShape(18.dp))
                        .clickable { vm.setAccent(i) })
                }
            }
            var g by remember { mutableFloatStateOf(Look.glass) }
            Text("Glass intensity", color = HieWhite)
            Slider(g, { g = it; Look.glass = it }, valueRange = 0.03f..0.3f, onValueChangeFinished = { vm.setGlass(g) })
            val la by vm.longAction.collectAsState()
            val da by vm.downAction.collectAsState()
            Text("Long-press on home", color = HieWhite)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Settings", la == 0) { vm.setLongAction(0) }
                Choice("Add widget", la == 1) { vm.setLongAction(1) }
            }
            Text("Swipe down on home", color = HieWhite)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("None", da == 0) { vm.setDownAction(0) }
                Choice("Settings", da == 1) { vm.setDownAction(1) }
                Choice("Add widget", da == 2) { vm.setDownAction(2) }
            }
            Button(openFocus, colors = ButtonDefaults.buttonColors(containerColor = Look.accent)) { Text("Focus profiles") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ vm.addPage() }) { Text("Add home page", color = HieWhite) }
            Spacer(Modifier.height(12.dp))
            Button({ ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                colors = ButtonDefaults.buttonColors(containerColor = Look.accent)) { Text("Set as default launcher") }
            Spacer(Modifier.height(24.dp))
            Text("Universe (optional)", color = HieBlue, fontWeight = FontWeight.Bold)
            Text("Andromeda works without Universe. Enter its package name and URI scheme to enable shortcuts; nothing is assumed.", color = HieWhite.copy(0.6f), fontSize = 12.sp)
            var upkg by remember { mutableStateOf(vm.universePkg.value) }
            var usch by remember { mutableStateOf(vm.universeScheme.value) }
            var upath by remember { mutableStateOf("") }
            var ustatus by remember { mutableStateOf("") }
            OutlinedTextField(upkg, { upkg = it }, singleLine = true, label = { Text("Package name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(usch, { usch = it }, singleLine = true, label = { Text("URI scheme (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(upath, { upath = it }, singleLine = true, label = { Text("Deep-link path to open (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton({ vm.saveUniverse(upkg, usch); ustatus = "Saved" }) { Text("Save", color = HieWhite) }
                Button({
                    val b = vm.universe(upkg, usch)
                    ustatus = when {
                        upkg.isBlank() -> "Not configured"
                        !b.isAvailable() -> "Not detected on this device"
                        b.open(upath) -> "Opened"
                        else -> "Could not open"
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Look.accent)) { Text("Open Universe") }
            }
            if (ustatus.isNotEmpty()) Text(ustatus, color = HieWhite.copy(0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            Text("Hidden apps", color = HieBlue, fontWeight = FontWeight.Bold)
            Text("Hiding only removes apps from the launcher list and search. It does not freeze or disable them.", color = HieWhite.copy(0.6f), fontSize = 12.sp)
            if (!showHidden) {
                OutlinedTextField(
                    entry, { entry = it.filter { c -> c.isDigit() }.take(8); err = false }, singleLine = true,
                    label = { Text("PIN") }, isError = err,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button({ if (vm.checkPin(entry)) { unlocked = true; entry = "" } else err = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Look.accent)) { Text("Unlock") }
            } else {
                if (hidden.isEmpty()) Text("None", color = HieWhite.copy(0.5f), modifier = Modifier.padding(top = 8.dp))
                var newPin by remember { mutableStateOf("") }
                OutlinedTextField(
                    newPin, { newPin = it.filter { c -> c.isDigit() }.take(8) }, singleLine = true,
                    label = { Text(if (pinStored.isEmpty()) "Set PIN (4-8 digits)" else "Change PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ if (newPin.length >= 4) { vm.setPin(newPin); newPin = ""; unlocked = true } },
                        colors = ButtonDefaults.buttonColors(containerColor = Look.accent)) { Text("Save PIN") }
                    if (pinStored.isNotEmpty()) TextButton({ vm.clearPin() }) { Text("Remove PIN") }
                }
                Text("The PIN only guards this list inside Andromeda; it is not device-level security.", color = HieWhite.copy(0.5f), fontSize = 11.sp)
            }
        }
        items(if (showHidden) hidden else emptyList()) { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(a.icon, null, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(a.label, color = HieWhite, modifier = Modifier.weight(1f))
                TextButton({ vm.restore(a) }) { Text("Restore", color = Look.accent) }
            }
        }
        item { Spacer(Modifier.height(16.dp)); TextButton(back) { Text("Back", color = HieBlue) } }
    }
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) Look.accent.copy(0.35f) else Color.White.copy(0.06f))
            .border(1.dp, if (selected) Look.accent else Color.White.copy(0.2f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = HieWhite, fontSize = 13.sp) }
}

@Composable
fun WidgetPicker(vm: LauncherViewModel, pick: (AppWidgetProviderInfo) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val providers = remember {
        vm.mgr.installedProviders
            .filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
            .map { it to it.loadLabel(ctx.packageManager).toString() }
            .sortedBy { it.second.lowercase() }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
        Text("Add widget", color = HieWhite, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = HieFont)
        Text("Added to the home page you were viewing.", color = HieWhite.copy(0.6f), fontSize = 12.sp)
        if (providers.isEmpty()) Text("No widgets available on this device.", color = HieWhite.copy(0.6f), modifier = Modifier.padding(top = 12.dp))
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp)) {
            items(providers) { (p, label) ->
                Text("$label  •  ${p.provider.packageName}", color = HieWhite, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable { pick(p) }.padding(vertical = 12.dp))
            }
        }
        TextButton(back) { Text("Back", color = HieBlue) }
    }
}
