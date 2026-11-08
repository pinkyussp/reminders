@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package pink.yussp.dates

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DatesRoot() }
    }
}

data class Palette(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val highlight: Color,
    val line: Color,
    val today: Color
)

private fun c(v: Long): Color = Color(0xFF000000L or v)

val THEME_KEYS = listOf("cream", "wine", "midnight", "forest", "lavender", "noir")
val THEME_LABELS = listOf("Cream", "Wine", "Midnight", "Forest", "Lavender", "Noir")

fun paletteFor(key: String): Palette = when (key) {
    "wine" -> Palette(c(0x1a1214), c(0x2a1f22), c(0xffffff), c(0xb59ea3), c(0x8b1e33), c(0xe8a2ab), c(0x3a2d30), c(0x4a1d26))
    "midnight" -> Palette(c(0x0f1626), c(0x1a2438), c(0xeef2ff), c(0x8fa0c2), c(0x3d5fd9), c(0x9bb8ff), c(0x2a3654), c(0x22304d))
    "forest" -> Palette(c(0xecf2ea), c(0xf8fbf6), c(0x203025), c(0x6d7f70), c(0x3f7d57), c(0x3f6b4a), c(0xd6e2d3), c(0xdcebd9))
    "lavender" -> Palette(c(0xf4f0fa), c(0xfdfbff), c(0x2e2640), c(0x7d7291), c(0x8a5bd1), c(0x7b4fb3), c(0xe2d9f0), c(0xebe1f8))
    "noir" -> Palette(c(0x121212), c(0x1e1e1e), c(0xf2f2f2), c(0x9a9a9a), c(0x3a3a3a), c(0xd4d4d4), c(0x2e2e2e), c(0x2a2a2a))
    else -> Palette(c(0xf7f1e8), c(0xfffaf3), c(0x3a2e28), c(0x8c7b70), c(0xc2614f), c(0xa84b44), c(0xeadfd2), c(0xf6e2d2))
}

fun schemeFor(p: Palette): ColorScheme {
    val brightness = 0.2126f * p.bg.red + 0.7152f * p.bg.green + 0.0722f * p.bg.blue
    val base = if (brightness < 0.5f) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accent,
        onPrimary = Color.White,
        background = p.bg,
        onBackground = p.ink,
        surface = p.card,
        onSurface = p.ink,
        surfaceVariant = p.card,
        onSurfaceVariant = p.muted,
        outline = p.line,
        secondaryContainer = p.today
    )
}

fun countdown(days: Long): String = when (days) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> "in $days days"
}

fun remindLabel(days: Int): String = when (days) {
    0 -> "On the day"
    1 -> "1 day"
    else -> "$days days"
}

fun typeLabel(e: Entry): String = if (e.type == "anniversary") "Anniversary" else "Birthday"

fun ageText(e: Entry, today: LocalDate): String {
    val years = e.yearsAt(today)
    return if (e.type == "anniversary") {
        "$years yr${if (years == 1) "" else "s"}"
    } else {
        "turns $years"
    }
}

fun metaLine(e: Entry, today: LocalDate): String =
    listOf(e.rel.takeIf { it.isNotBlank() }, typeLabel(e), ageText(e, today)).filterNotNull().joinToString(" · ")

fun prettyDate(iso: String): String =
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

fun matchesDay(e: Entry, day: LocalDate): Boolean {
    val base = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return false
    return base.monthValue == day.monthValue && base.dayOfMonth == day.dayOfMonth
}

@Composable
fun DatesRoot() {
    val ctx = LocalContext.current
    val prefs = remember { Store.prefs(ctx) }
    val scope = rememberCoroutineScope()
    var themeKey by remember { mutableStateOf(prefs.getString("theme", "cream") ?: "cream") }
    var defaultRemind by remember { mutableStateOf(prefs.getInt("default_remind", 3)) }
    var defaultView by remember { mutableStateOf(prefs.getInt("default_view", 0)) }
    var entries by remember { mutableStateOf<List<Entry>>(Store.load(ctx)) }
    val pagerState = rememberPagerState(initialPage = defaultView, pageCount = { 3 })
    var settingsOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var whatsNewOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<List<Entry>?>(null) }
    val whatsNew = remember {
        runCatching { ctx.assets.open("whats-new.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    val p = paletteFor(themeKey)
    val today = LocalDate.now()

    fun persist(list: List<Entry>) {
        entries = list
        Store.save(ctx, list)
    }

    fun toast(message: String) {
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    fun closeWhatsNew() {
        whatsNewOpen = false
        prefs.edit().putInt("last_seen", BuildConfig.VERSION_CODE).apply()
    }

    fun openNew() {
        editing = null
        sheetOpen = true
    }

    fun openEdit(e: Entry) {
        editing = e
        sheetOpen = true
    }

    LaunchedEffect(Unit) {
        Reminders.channel(ctx)
        Reminders.schedule(ctx)
        val lastSeen = prefs.getInt("last_seen", 0)
        if (lastSeen == 0) {
            prefs.edit().putInt("last_seen", BuildConfig.VERSION_CODE).apply()
        } else if (lastSeen < BuildConfig.VERSION_CODE) {
            whatsNewOpen = true
        }
    }

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast(if (granted) "Notifications are on" else "Notifications are blocked. Allow them in phone settings.")
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            toast(if (Store.exportTo(ctx, uri, entries)) "Backup saved" else "Couldn't save the backup")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val list = Store.importFrom(ctx, uri)
            if (list == null) toast("That file isn't a Dates backup") else pendingImport = list
        }
    }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    MaterialTheme(colorScheme = schemeFor(p)) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> OverviewPage(entries, p, today, onAdd = { openNew() }, onOpen = { openEdit(it) })
                    1 -> DatesPage(entries, p, today, onAdd = { openNew() }, onOpen = { openEdit(it) })
                    else -> CalendarPage(entries, p, today, onAdd = { openNew() }, onOpen = { openEdit(it) })
                }
            }

            TabBar(
                p = p,
                selected = pagerState.currentPage,
                onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
                onSettings = { settingsOpen = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (settingsOpen) {
                Surface(modifier = Modifier.fillMaxSize(), color = p.bg) {
                    SettingsPanel(
                        p = p,
                        defaultView = defaultView,
                        defaultRemind = defaultRemind,
                        themeKey = themeKey,
                        versionText = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        onBack = { settingsOpen = false },
                        onView = {
                            defaultView = it
                            prefs.edit().putInt("default_view", it).apply()
                        },
                        onRemind = {
                            defaultRemind = it
                            prefs.edit().putInt("default_remind", it).apply()
                        },
                        onTheme = {
                            themeKey = it
                            prefs.edit().putString("theme", it).apply()
                        },
                        onAllowNotifications = { notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onTestNotification = {
                            if (Reminders.canNotify(ctx)) {
                                Reminders.notify(ctx, "Dates", "Notifications are working.", 1)
                            } else {
                                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onExport = { exportLauncher.launch("dates-backup.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        onWhatsNew = { whatsNewOpen = true }
                    )
                }
            }

            if (sheetOpen) {
                EditSheet(
                    existing = editing,
                    p = p,
                    defaultRemind = defaultRemind,
                    onDismiss = { sheetOpen = false },
                    onSave = { e ->
                        val current = entries
                        persist(if (editing == null) current + e else current.map { if (it.id == e.id) e else it })
                        sheetOpen = false
                    },
                    onDelete = {
                        val id = editing?.id
                        if (id != null) persist(entries.filter { it.id != id })
                        sheetOpen = false
                    }
                )
            }

            pendingImport?.let { list ->
                AlertDialog(
                    onDismissRequest = { pendingImport = null },
                    title = { Text("Replace your dates?") },
                    text = { Text("This replaces your ${entries.size} current dates with ${list.size} from the backup.") },
                    confirmButton = {
                        TextButton(onClick = {
                            persist(list)
                            pendingImport = null
                            toast("Restored ${list.size} dates")
                        }) { Text("Replace") }
                    },
                    dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } }
                )
            }

            if (whatsNewOpen) {
                AlertDialog(
                    onDismissRequest = { closeWhatsNew() },
                    title = { Text("What's new in ${BuildConfig.VERSION_NAME}") },
                    text = { Text(whatsNew) },
                    confirmButton = { TextButton(onClick = { closeWhatsNew() }) { Text("OK") } }
                )
            }
        }
    }
}

@Composable
fun TabBar(p: Palette, selected: Int, onSelect: (Int) -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    val labels = listOf("Overview", "Dates", "Calendar")
    Row(
        modifier
            .padding(bottom = 22.dp)
            .fillMaxWidth(0.92f)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(50))
            .background(p.card)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) p.accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) Color.White else p.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .clickable { onSettings() },
            contentAlignment = Alignment.Center
        ) {
            Text("≡", color = p.ink, fontSize = 24.sp)
        }
    }
}

@Composable
fun Header(p: Palette, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Dates", color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(p.accent)
                .clickable { onAdd() },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontSize = 24.sp)
        }
    }
}

@Composable
fun SectionLabel(text: String, p: Palette) {
    Text(
        text.uppercase(),
        color = p.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun EmptyState(p: Palette) {
    Text(
        "No dates yet. Tap + to add a birthday or anniversary.",
        color = p.muted,
        fontSize = 15.sp,
        modifier = Modifier.padding(vertical = 60.dp, horizontal = 20.dp)
    )
}

@Composable
fun Hero(e: Entry, p: Palette, today: LocalDate, onClick: () -> Unit) {
    val days = e.daysUntil(today)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.accent)
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Text("Next up", color = Color(0xFFFFF9F4), fontSize = 13.sp)
        Text(countdown(days), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("${e.name} · ${typeLabel(e)}", color = Color(0xFFFFF1EA), fontSize = 14.sp)
    }
}

@Composable
fun EntryRow(e: Entry, p: Palette, today: LocalDate, onClick: () -> Unit) {
    val next = e.next(today)
    val days = e.daysUntil(today)
    val isToday = days == 0L
    val shape = RoundedCornerShape(20.dp)
    val base = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp)
        .clip(shape)
        .background(if (isToday) p.today else p.card)
    val framed = if (isToday) base.border(1.5.dp, p.accent, shape) else base
    Row(
        framed.clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(p.accent),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                next.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(),
                color = Color(0xFFFFF9F4),
                fontSize = 11.sp
            )
            Text(next.dayOfMonth.toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(
                e.name,
                color = p.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(metaLine(e, today), color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            countdown(days),
            color = if (isToday) p.accent else p.highlight,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun OverviewPage(all: List<Entry>, p: Palette, today: LocalDate, onAdd: () -> Unit, onOpen: (Entry) -> Unit) {
    val sorted = all.sortedBy { it.daysUntil(today) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
    ) {
        item { Header(p, onAdd) }
        if (sorted.isEmpty()) {
            item { EmptyState(p) }
        } else {
            val next = sorted.first()
            item {
                Hero(next, p, today) { onOpen(next) }
                Spacer(Modifier.height(14.dp))
            }
            val soon = sorted.filter { it.id != next.id && it.daysUntil(today) <= 30 }
            val later = sorted.filter { it.daysUntil(today) > 30 }
            if (soon.isNotEmpty()) {
                item { SectionLabel("Coming up", p) }
                items(soon, key = { it.id }) { e -> EntryRow(e, p, today) { onOpen(e) } }
            }
            if (later.isNotEmpty()) {
                item { SectionLabel("Later", p) }
                items(later, key = { it.id }) { e -> EntryRow(e, p, today) { onOpen(e) } }
            }
        }
    }
}

@Composable
fun DatesPage(all: List<Entry>, p: Palette, today: LocalDate, onAdd: () -> Unit, onOpen: (Entry) -> Unit) {
    val sorted = all.sortedBy { it.daysUntil(today) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
    ) {
        item { Header(p, onAdd) }
        if (sorted.isEmpty()) {
            item { EmptyState(p) }
        } else {
            item { SectionLabel("All dates", p) }
            items(sorted, key = { it.id }) { e -> EntryRow(e, p, today) { onOpen(e) } }
        }
    }
}

@Composable
fun CalendarPage(all: List<Entry>, p: Palette, today: LocalDate, onAdd: () -> Unit, onOpen: (Entry) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val firstWeekday = month.atDay(1).dayOfWeek.value % 7
    val weeks = (firstWeekday + month.lengthOfMonth() + 6) / 7
    val weekdayNames = listOf("S", "M", "T", "W", "T", "F", "S")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
    ) {
        item { Header(p, onAdd) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { month = month.minusMonths(1); selected = null }) { Text("‹", color = p.accent, fontSize = 22.sp) }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    color = p.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { month = month.plusMonths(1); selected = null }) { Text("›", color = p.accent, fontSize = 22.sp) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                weekdayNames.forEach { d ->
                    Text(d, color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for (w in 0 until weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (d in 0 until 7) {
                            val dayNumber = w * 7 + d - firstWeekday + 1
                            if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                                Spacer(Modifier.weight(1f).height(44.dp))
                            } else {
                                val date = month.atDay(dayNumber)
                                val isSel = selected == date
                                val hasEvent = all.any { matchesDay(it, date) }
                                val isToday = date == today
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) p.accent else if (isToday) p.today else Color.Transparent)
                                        .clickable { selected = if (isSel) null else date },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        dayNumber.toString(),
                                        color = if (isSel) Color.White else p.ink,
                                        fontSize = 15.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (hasEvent) {
                                        Box(
                                            Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isSel) Color.White else p.highlight)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            val list = if (selected != null) {
                val day = selected!!
                all.filter { matchesDay(it, day) }
            } else {
                all.filter { runCatching { LocalDate.parse(it.date).monthValue == month.monthValue }.getOrDefault(false) }
                    .sortedBy { runCatching { LocalDate.parse(it.date).dayOfMonth }.getOrDefault(0) }
            }
            Column {
                SectionLabel(if (selected != null) "Selected day" else "This month", p)
                if (list.isEmpty()) {
                    Text("Nothing on this day.", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                list.forEach { e -> EntryRow(e, p, today) { onOpen(e) } }
            }
        }
    }
}

@Composable
fun EditSheet(
    existing: Entry?,
    p: Palette,
    defaultRemind: Int,
    onDismiss: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.type ?: "birthday") }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var remind by remember { mutableStateOf(existing?.remind ?: defaultRemind) }
    var rel by remember { mutableStateOf(existing?.rel ?: "") }
    var showPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.bg
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (existing == null) "New date" else "Edit date", color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Text("Name", color = p.muted, fontSize = 13.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Type", color = p.muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == "birthday", onClick = { kind = "birthday" }, label = { Text("Birthday") })
                FilterChip(selected = kind == "anniversary", onClick = { kind = "anniversary" }, label = { Text("Anniversary") })
            }

            Text("Date", color = p.muted, fontSize = 13.sp)
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(prettyDate(date))
            }

            Text("Remind me before", color = p.muted, fontSize = 13.sp)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 1, 3, 7, 14).forEach { d ->
                    FilterChip(selected = remind == d, onClick = { remind = d }, label = { Text(remindLabel(d)) })
                }
            }

            Text("Relationship (optional)", color = p.muted, fontSize = 13.sp)
            OutlinedTextField(
                value = rel,
                onValueChange = { rel = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSave(
                        Entry(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            type = kind,
                            date = date,
                            remind = remind,
                            rel = rel.trim()
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            if (existing != null) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
            }
        }
    }

    if (showPicker) {
        DatePickDialog(
            initial = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
            onPick = { date = it.toString() },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
fun DatePickDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun SettingsSection(title: String, p: Palette, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = p.highlight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun SettingsPanel(
    p: Palette,
    defaultView: Int,
    defaultRemind: Int,
    themeKey: String,
    versionText: String,
    onBack: () -> Unit,
    onView: (Int) -> Unit,
    onRemind: (Int) -> Unit,
    onTheme: (String) -> Unit,
    onAllowNotifications: () -> Unit,
    onTestNotification: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onWhatsNew: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = p.accent, fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            Text("Settings", color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(64.dp))
        }

        SettingsSection("General", p) {
            Text("Default view", color = p.muted, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Overview", "Dates", "Calendar").forEachIndexed { i, label ->
                    FilterChip(selected = defaultView == i, onClick = { onView(i) }, label = { Text(label) })
                }
            }
            Text("Default reminder for new dates", color = p.muted, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 3, 7, 14).forEach { d ->
                    FilterChip(selected = defaultRemind == d, onClick = { onRemind(d) }, label = { Text(remindLabel(d)) })
                }
            }
        }

        SettingsSection("Theme", p) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                THEME_KEYS.forEach { key ->
                    val swatch = paletteFor(key)
                    val selected = key == themeKey
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatch.bg)
                            .border(if (selected) 3.dp else 1.dp, if (selected) p.accent else p.line, CircleShape)
                            .clickable { onTheme(key) }
                    )
                }
            }
            Text(THEME_LABELS[THEME_KEYS.indexOf(themeKey).coerceAtLeast(0)], color = p.ink, fontSize = 14.sp)
        }

        SettingsSection("Notifications", p) {
            Text("Reminders are checked once a day. Allow notifications so they can show.", color = p.muted, fontSize = 13.sp)
            Button(onClick = onAllowNotifications, modifier = Modifier.fillMaxWidth()) { Text("Allow notifications") }
            OutlinedButton(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) { Text("Send a test notification") }
        }

        SettingsSection("Data", p) {
            Text("Backups use the same file format as the web app.", color = p.muted, fontSize = 13.sp)
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export backup") }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import backup") }
        }

        SettingsSection("Info", p) {
            Text(versionText, color = p.ink, fontSize = 14.sp)
            OutlinedButton(onClick = onWhatsNew, modifier = Modifier.fillMaxWidth()) { Text("What's new") }
        }
    }
}
