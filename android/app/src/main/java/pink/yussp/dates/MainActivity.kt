@file:OptIn(ExperimentalMaterial3Api::class)

package pink.yussp.dates

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
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

sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    data class Edit(val id: String?) : Screen()
}

data class Palette(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val highlight: Color,
    val line: Color
)

val THEME_KEYS = listOf("cream", "wine", "midnight", "forest", "lavender", "noir")

fun paletteFor(key: String): Palette = when (key) {
    "wine" -> Palette(Color(0xFF1A1214), Color(0xFF2A1F22), Color(0xFFFFFFFF), Color(0xFFB59EA3), Color(0xFF8B1E33), Color(0xFFE8A2AB), Color(0xFF3A2D30))
    "midnight" -> Palette(Color(0xFF0F1626), Color(0xFF1A2438), Color(0xFFEEF2FF), Color(0xFF8FA0C2), Color(0xFF3D5FD9), Color(0xFF9BB8FF), Color(0xFF2A3654))
    "forest" -> Palette(Color(0xFFECF2EA), Color(0xFFF8FBF6), Color(0xFF203025), Color(0xFF6D7F70), Color(0xFF3F7D57), Color(0xFF3F6B4A), Color(0xFFD6E2D3))
    "lavender" -> Palette(Color(0xFFF4F0FA), Color(0xFFFDFBFF), Color(0xFF2E2640), Color(0xFF7D7291), Color(0xFF8A5BD1), Color(0xFF7B4FB3), Color(0xFFE2D9F0))
    "noir" -> Palette(Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFF2F2F2), Color(0xFF9A9A9A), Color(0xFF3A3A3A), Color(0xFFD4D4D4), Color(0xFF2E2E2E))
    else -> Palette(Color(0xFFF7F1E8), Color(0xFFFFFAF3), Color(0xFF3A2E28), Color(0xFF8C7B70), Color(0xFFC2614F), Color(0xFFA84B44), Color(0xFFEADFD2))
}

fun schemeFor(p: Palette): ColorScheme {
    val base = if (p.bg.luminance() < 0.5f) darkColorScheme() else lightColorScheme()
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
        secondaryContainer = p.card
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

fun prettyDate(iso: String): String =
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

@Composable
fun DatesRoot() {
    val ctx = LocalContext.current
    val prefs = remember { Store.prefs(ctx) }
    var themeKey by remember { mutableStateOf(prefs.getString("theme", "cream") ?: "cream") }
    var defaultRemind by remember { mutableStateOf(prefs.getInt("default_remind", 3)) }
    var items by remember { mutableStateOf<List<Entry>>(Store.load(ctx)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var whatsNewOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<List<Entry>?>(null) }
    val whatsNew = remember {
        runCatching { ctx.assets.open("whats-new.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    val p = paletteFor(themeKey)

    fun persist(list: List<Entry>) {
        items = list
        Store.save(ctx, list)
    }

    fun toast(message: String) {
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    fun closeWhatsNew() {
        whatsNewOpen = false
        prefs.edit().putInt("last_seen", BuildConfig.VERSION_CODE).apply()
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
            toast(if (Store.exportTo(ctx, uri, items)) "Backup saved" else "Couldn't save the backup")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val list = Store.importFrom(ctx, uri)
            if (list == null) toast("That file isn't a Dates backup") else pendingImport = list
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    MaterialTheme(colorScheme = schemeFor(p)) {
        Surface(modifier = Modifier.fillMaxSize(), color = p.bg) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    items = items,
                    p = p,
                    onAdd = { screen = Screen.Edit(null) },
                    onOpen = { screen = Screen.Edit(it.id) },
                    onSettings = { screen = Screen.Settings }
                )
                is Screen.Edit -> {
                    val existing = items.find { it.id == s.id }
                    val onDelete: (() -> Unit)? = existing?.let { ex ->
                        { persist(items.filter { it.id != ex.id }); screen = Screen.Home }
                    }
                    EditScreen(
                        existing = existing,
                        p = p,
                        defaultRemind = defaultRemind,
                        onCancel = { screen = Screen.Home },
                        onSave = { e ->
                            persist(if (existing == null) items + e else items.map { if (it.id == e.id) e else it })
                            screen = Screen.Home
                        },
                        onDelete = onDelete
                    )
                }
                Screen.Settings -> SettingsScreen(
                    p = p,
                    defaultRemind = defaultRemind,
                    themeKey = themeKey,
                    versionText = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    onBack = { screen = Screen.Home },
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

            pendingImport?.let { list ->
                AlertDialog(
                    onDismissRequest = { pendingImport = null },
                    title = { Text("Replace your dates?") },
                    text = { Text("This replaces your ${items.size} current dates with ${list.size} from the backup.") },
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
fun HomeScreen(
    items: List<Entry>,
    p: Palette,
    onAdd: () -> Unit,
    onOpen: (Entry) -> Unit,
    onSettings: () -> Unit
) {
    val today = LocalDate.now()
    val sorted = items.sortedBy { it.daysUntil(today) }
    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                title = { Text("Dates", color = p.highlight, fontWeight = FontWeight.Bold) },
                actions = { TextButton(onClick = onSettings) { Text("Settings", color = p.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = p.accent, contentColor = Color.White) {
                Text("+", fontSize = 26.sp)
            }
        }
    ) { padding ->
        if (sorted.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No dates yet. Tap + to add a birthday or anniversary.", color = p.muted)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sorted, key = { it.id }) { e ->
                    EntryCard(e, p, today) { onOpen(e) }
                }
            }
        }
    }
}

@Composable
fun EntryCard(e: Entry, p: Palette, today: LocalDate, onClick: () -> Unit) {
    val days = e.daysUntil(today)
    val years = e.yearsAt(today)
    val isAnniversary = e.type == "anniversary"
    val kind = if (isAnniversary) "Anniversary" else "Birthday"
    val detail = if (isAnniversary) "$years year${if (years == 1) "" else "s"}" else "turns $years"
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = p.card)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.name, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("$kind · $detail", color = p.muted, fontSize = 13.sp)
            }
            Text(
                countdown(days),
                color = if (days == 0L) p.accent else p.highlight,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EditScreen(
    existing: Entry?,
    p: Palette,
    defaultRemind: Int,
    onCancel: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "birthday") }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var remind by remember { mutableStateOf(existing?.remind ?: defaultRemind) }
    var rel by remember { mutableStateOf(existing?.rel ?: "") }
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing == null) "New date" else "Edit date",
                        color = p.highlight,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Back", color = p.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Type", color = p.muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == "birthday",
                    onClick = { type = "birthday" },
                    label = { Text("Birthday") }
                )
                FilterChip(
                    selected = type == "anniversary",
                    onClick = { type = "anniversary" },
                    label = { Text("Anniversary") }
                )
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
                    FilterChip(
                        selected = remind == d,
                        onClick = { remind = d },
                        label = { Text(remindLabel(d)) }
                    )
                }
            }

            OutlinedTextField(
                value = rel,
                onValueChange = { rel = it },
                label = { Text("Relationship (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSave(
                        Entry(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            type = type,
                            date = date,
                            remind = remind,
                            rel = rel.trim()
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            if (onDelete != null) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
            }
        }
    }

    if (showPicker) {
        DatePickDialog(
            initial = LocalDate.parse(date),
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
fun SettingsScreen(
    p: Palette,
    defaultRemind: Int,
    themeKey: String,
    versionText: String,
    onBack: () -> Unit,
    onRemind: (Int) -> Unit,
    onTheme: (String) -> Unit,
    onAllowNotifications: () -> Unit,
    onTestNotification: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onWhatsNew: () -> Unit
) {
    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = p.highlight, fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = p.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Section("Reminders", p) {
                Text("Default reminder for new dates", color = p.muted, fontSize = 13.sp)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 1, 3, 7, 14).forEach { d ->
                        FilterChip(
                            selected = defaultRemind == d,
                            onClick = { onRemind(d) },
                            label = { Text(remindLabel(d)) }
                        )
                    }
                }
            }

            Section("Theme", p) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    THEME_KEYS.forEach { key ->
                        val swatch = paletteFor(key)
                        val selected = key == themeKey
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(swatch.bg)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) p.accent else p.line,
                                    shape = CircleShape
                                )
                                .clickable { onTheme(key) }
                        )
                    }
                }
            }

            Section("Notifications", p) {
                Text(
                    "Reminders are checked once a day. Allow notifications so they can show.",
                    color = p.muted,
                    fontSize = 13.sp
                )
                Button(onClick = onAllowNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow notifications")
                }
                OutlinedButton(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) {
                    Text("Send a test notification")
                }
            }

            Section("Backup", p) {
                Text("Backups use the same file format as the web app.", color = p.muted, fontSize = 13.sp)
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export backup") }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import backup") }
            }

            Section("About", p) {
                Text(versionText, color = p.ink)
                OutlinedButton(onClick = onWhatsNew, modifier = Modifier.fillMaxWidth()) { Text("What's new") }
            }
        }
    }
}

@Composable
fun Section(title: String, p: Palette, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = p.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = p.highlight, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
