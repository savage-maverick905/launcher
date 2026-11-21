package com.hietech.andromeda

import androidx.compose.foundation.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private fun folderName(item: String) = item.split("~").getOrElse(1) { "Folder" }

@Composable
fun HomeScreen(vm: LauncherViewModel, openLibrary: () -> Unit, openSettings: () -> Unit, openWidgets: () -> Unit) {
    val dock by vm.dock.collectAsState()
    val layout by vm.layout.collectAsState()
    val apps by vm.visible.collectAsState()
    val light by vm.lightMode.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(30_000) } }
    val longAct by vm.longAction.collectAsState()
    val downAct by vm.downAction.collectAsState()
    val pr by vm.profiles.collectAsState()
    val am by vm.activeManual.collectAsState()
    val ou by vm.overrideUntil.collectAsState()
    val ap = remember(now, pr, am, ou) { vm.activeProfile() }
    var drag by remember { mutableFloatStateOf(0f) }
    val pager = rememberPagerState { layout.size }
    LaunchedEffect(pager.currentPage) { vm.currentPage = pager.currentPage }
    var folderOpen by remember { mutableStateOf<String?>(null) }
    var folderFor by remember { mutableStateOf<AppItem?>(null) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag < -120f) openLibrary()
                        else if (drag > 160f) when (downAct) { 1 -> openSettings(); 2 -> openWidgets(); else -> {} }
                    },
                    onVerticalDrag = { _, d -> drag += d })
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { if (longAct == 1) openWidgets() else openSettings() }) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = HieWhite, fontSize = 64.sp, fontWeight = FontWeight.Light, fontFamily = HieFont)
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = SoftOrange, fontSize = 16.sp, fontFamily = HieFont)
        if (ap != null) Text("● Focus: ${ap.name}", color = HieWhite, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp).glass(light, 16).padding(horizontal = 12.dp, vertical = 4.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { p ->
            Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val items = layout.getOrElse(p) { emptyList() }
                val blocks = remember(items) {
                    val out = mutableListOf<List<String>>()
                    var cur = mutableListOf<String>()
                    items.forEach { x ->
                        if (x.startsWith("W~")) {
                            if (cur.isNotEmpty()) { out.add(cur); cur = mutableListOf() }
                            out.add(listOf(x))
                        } else cur.add(x)
                    }
                    if (cur.isNotEmpty()) out.add(cur)
                    out.toList()
                }
                blocks.forEach { blk ->
                    if (blk[0].startsWith("W~")) WidgetCell(blk[0], vm)
                    else blk.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { item ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    HomeCell(item, byKey, vm, light, { folderOpen = it }, { folderFor = it })
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(layout.size) { i ->
                Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (i == pager.currentPage) Look.accent else HieWhite.copy(0.3f)))
            }
        }
        Row(Modifier.fillMaxWidth().glass(light).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (dock.isEmpty()) Text("Long-press an app in the library to add it to the dock or a home page", color = HieWhite.copy(0.6f), fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            dock.forEach { a -> Image(a.icon, a.label, Modifier.size(52.dp).clickable { vm.launch(a) }) }
        }
    }

    folderOpen?.let { f ->
        AlertDialog(
            onDismissRequest = { folderOpen = null },
            confirmButton = { TextButton({ folderOpen = null }) { Text("Close", color = Look.accent) } },
            title = { Text(folderName(f)) },
            text = {
                Column {
                    folderKeys(f).mapNotNull { byKey[it] }.forEach { a ->
                        Row(Modifier.fillMaxWidth().clickable { vm.launch(a); folderOpen = null }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(a.icon, null, Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(a.label)
                        }
                    }
                }
            }
        )
    }
    folderFor?.let { a ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { folderFor = null },
            confirmButton = { TextButton({ vm.intoFolder(a, name); folderFor = null }) { Text("Move", color = Look.accent) } },
            dismissButton = { TextButton({ folderFor = null }) { Text("Cancel") } },
            title = { Text("Folder for ${a.label}") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Folder name") }) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeCell(
    item: String, byKey: Map<String, AppItem>, vm: LauncherViewModel, light: Boolean,
    openFolder: (String) -> Unit, moveToFolder: (AppItem) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val isFolder = item.startsWith("F~")
    val app = if (isFolder) null else byKey[item]
    if (!isFolder && app == null) return
    Box {
        Column(
            Modifier.width(72.dp).clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { if (isFolder) openFolder(item) else vm.launch(app!!) }, onLongClick = { menu = true })
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isFolder) {
                val icons = folderKeys(item).mapNotNull { byKey[it] }.take(4)
                Column(Modifier.size(52.dp).glass(light, 14).padding(5.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    icons.chunked(2).forEach { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            r.forEach { Image(it.icon, null, Modifier.size(18.dp)) }
                        }
                    }
                }
                Text(folderName(item), color = HieWhite, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = HieFont)
            } else {
                Image(app!!.icon, null, Modifier.size(52.dp))
                Text(app.label, color = HieWhite, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = HieFont)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Remove from home") }, { vm.removeItem(item); menu = false })
            if (isFolder) DropdownMenuItem({ Text("Dissolve folder") }, { vm.dissolveFolder(item); menu = false })
            else DropdownMenuItem({ Text("Move to folder…") }, { moveToFolder(app!!); menu = false })
        }
    }
}

@Composable
fun WidgetCell(item: String, vm: LauncherViewModel) {
    val id = item.removePrefix("W~").toIntOrNull() ?: return
    val info = vm.mgr.getAppWidgetInfo(id)
    if (info == null) { LaunchedEffect(id) { vm.removeWidget(id) }; return }
    val hDp = with(LocalDensity.current) { info.minHeight.coerceIn(100, 900).toDp() }
    Box(Modifier.fillMaxWidth().height(hDp)) {
        AndroidView(factory = { c -> vm.host.createView(c, id, info) }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.TopEnd).size(28.dp).clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(0.5f)).clickable { vm.removeWidget(id) },
            contentAlignment = Alignment.Center
        ) { Text("✕", color = HieWhite, fontSize = 12.sp) }
    }
}
