@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package pink.yussp.dates

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.animateItemPlacement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

val LOCK_AUTHENTICATORS: Int =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
val LANG_TAGS = listOf("en", "fr", "es", "de", "pt")
val LANG_NAMES = listOf("English", "Français", "Español", "Deutsch", "Português")
const val WEBSITE_URL = "https://dates.yussp.pink"

object LockState {
    var started = false
    var locked by mutableStateOf(false)
    var ignoreNextStop = false
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!LockState.started) {
            LockState.started = true
            if (Store.prefs(this).getBoolean("lock_enabled", false)) LockState.locked = true
        }
        setContent { DatesRoot(this) }
    }

    override fun onStop() {
        super.onStop()
        if (LockState.ignoreNextStop) {
            LockState.ignoreNextStop = false
        } else if (Store.prefs(this).getBoolean("lock_enabled", false)) {
            LockState.locked = true
        }
    }
}

data class Palette(
    val bg: UiColor,
    val card: UiColor,
    val ink: UiColor,
    val muted: UiColor,
    val accent: UiColor,
    val highlight: UiColor,
    val line: UiColor,
    val today: UiColor
)

private fun c(v: Long): UiColor = UiColor(0xFF000000L or v)

val THEME_KEYS = listOf("cream", "wine", "midnight", "forest", "lavender", "noir")

fun themeNameRes(key: String): Int = when (key) {
    "wine" -> R.string.theme_wine
    "midnight" -> R.string.theme_midnight
    "forest" -> R.string.theme_forest
    "lavender" -> R.string.theme_lavender
    "noir" -> R.string.theme_noir
    else -> R.string.theme_cream
}

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
        onPrimary = UiColor.White,
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

fun countdown(days: Long, ctx: Context): String = when (days) {
    0L -> ctx.getString(R.string.countdown_today)
    1L -> ctx.getString(R.string.countdown_tomorrow)
    else -> ctx.getString(R.string.countdown_in, days)
}

fun remindLabel(days: Int, ctx: Context): String =
    if (days == 0) ctx.getString(R.string.on_day)
    else ctx.resources.getQuantityString(R.plurals.remind_days, days, days)

fun typeLabel(e: Entry, ctx: Context): String =
    ctx.getString(if (e.type == "anniversary") R.string.type_anniversary else R.string.type_birthday)

fun ageText(e: Entry, today: LocalDate, ctx: Context): String {
    val years = e.yearsAt(today)
    return if (e.type == "anniversary") {
        ctx.resources.getQuantityString(R.plurals.age_years, years, years)
    } else {
        ctx.getString(R.string.age_turns, years)
    }
}

fun metaLine(e: Entry, today: LocalDate, ctx: Context): String =
    listOfNotNull(e.rel.takeIf { it.isNotBlank() }, typeLabel(e, ctx), ageText(e, today, ctx)).joinToString(" · ")

fun prettyDate(iso: String): String =
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

fun matchesDay(e: Entry, day: LocalDate): Boolean {
    val base = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return false
    return base.monthValue == day.monthValue && base.dayOfMonth == day.dayOfMonth
}

fun canUseLock(ctx: Context): Boolean =
    BiometricManager.from(ctx).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

fun promptUnlock(activity: AppCompatActivity) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                LockState.locked = false
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setSubtitle(activity.getString(R.string.lock_prompt_sub))
            .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
            .build()
    )
}

enum class SettingsPage { Main, People }

@Composable
fun DatesRoot(activity: AppCompatActivity) {
    val ctx = LocalContext.current
    val prefs = remember { Store.prefs(ctx) }
    val scope = rememberCoroutineScope()
    var themeKey by remember { mutableStateOf(prefs.getString("theme", "cream") ?: "cream") }
    var defaultRemind by remember { mutableStateOf(prefs.getInt("default_remind", 3)) }
    var defaultView by remember { mutableStateOf(prefs.getInt("default_view", 0)) }
    var lockOn by remember { mutableStateOf(prefs.getBoolean("lock_enabled", false)) }
    var entries by remember { mutableStateOf<List<Entry>>(Store.load(ctx)) }
    val pagerState = rememberPagerState(initialPage = defaultView, pageCount = { 3 })
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(SettingsPage.Main) }
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var whatsNewOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<List<Entry>?>(null) }
    var pendingDeletePerson by remember { mutableStateOf<String?>(null) }
    val whatsNew = remember {
        runCatching { ctx.assets.open("whats-new.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    val p = paletteFor(themeKey)
    val today = LocalDate.now()
    val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
        .ifEmpty { Locale.getDefault().language }
        .let { if (it in LANG_TAGS) it else "en" }

    fun persist(list: List<Entry>) {
        entries = list
        Store.save(ctx, list)
    }

    fun say(text: String) {
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
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

    fun setLock(on: Boolean) {
        if (on && !canUseLock(ctx)) {
            say(ctx.getString(R.string.lock_need_device))
            return
        }
        lockOn = on
        prefs.edit().putBoolean("lock_enabled", on).apply()
    }

    fun setLanguage(tag: String) {
        if (tag == currentLang) return
        LockState.ignoreNextStop = true
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
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

    LaunchedEffect(LockState.locked) {
        if (LockState.locked) promptUnlock(activity)
    }

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        say(ctx.getString(if (granted) R.string.notif_on else R.string.notif_blocked))
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            say(ctx.getString(if (Store.exportTo(ctx, uri, entries)) R.string.backup_saved else R.string.backup_failed))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val list = Store.importFrom(ctx, uri)
            if (list == null) say(ctx.getString(R.string.not_backup_file)) else pendingImport = list
        }
    }

    BackHandler(enabled = settingsOpen) {
        if (settingsPage != SettingsPage.Main) settingsPage = SettingsPage.Main else settingsOpen = false
    }

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
                onSettings = {
                    settingsPage = SettingsPage.Main
                    settingsOpen = true
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            AnimatedVisibility(
                visible = settingsOpen,
                modifier = Modifier.fillMaxSize(),
                enter = slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut()
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = p.bg) {
                    AnimatedContent(
                        targetState = settingsPage,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            (slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn()) togetherWith
                                (slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut())
                        },
                        label = "settings"
                    ) { page ->
                        when (page) {
                            SettingsPage.Main -> SettingsMain(
                                p = p,
                                defaultView = defaultView,
                                defaultRemind = defaultRemind,
                                themeKey = themeKey,
                                lockOn = lockOn,
                                lang = currentLang,
                                versionText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
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
                                onLock = { setLock(it) },
                                onLang = { setLanguage(it) },
                                onAllowNotifications = { notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                onTestNotification = {
                                    if (Reminders.canNotify(ctx)) {
                                        Reminders.notify(ctx, "Dates", ctx.getString(R.string.notif_test_text), 1)
                                    } else {
                                        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onExport = {
                                    LockState.ignoreNextStop = true
                                    exportLauncher.launch("dates-backup.json")
                                },
                                onImport = {
                                    LockState.ignoreNextStop = true
                                    importLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                onPeople = { settingsPage = SettingsPage.People },
                                onAbout = { aboutOpen = true },
                                onWhatsNew = { whatsNewOpen = true }
                            )
                            SettingsPage.People -> PeoplePage(
                                all = entries,
                                p = p,
                                today = today,
                                onBack = { settingsPage = SettingsPage.Main },
                                onOpen = { openEdit(it) },
                                onDelete = { pendingDeletePerson = it }
                            )
                        }
                    }
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
                    title = { Text(stringResource(R.string.replace_title)) },
                    text = { Text(stringResource(R.string.replace_text)) },
                    confirmButton = {
                        TextButton(onClick = {
                            persist(list)
                            pendingImport = null
                            say(ctx.getString(R.string.restored))
                        }) { Text(stringResource(R.string.replace)) }
                    },
                    dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } }
                )
            }

            pendingDeletePerson?.let { name ->
                AlertDialog(
                    onDismissRequest = { pendingDeletePerson = null },
                    title = { Text(stringResource(R.string.delete_person_title, name)) },
                    text = { Text(stringResource(R.string.delete_person_text)) },
                    confirmButton = {
                        TextButton(onClick = {
                            persist(entries.filter { it.name != name })
                            pendingDeletePerson = null
                        }) { Text(stringResource(R.string.delete)) }
                    },
                    dismissButton = { TextButton(onClick = { pendingDeletePerson = null }) { Text(stringResource(R.string.cancel)) } }
                )
            }

            if (aboutOpen) {
                AlertDialog(
                    onDismissRequest = { aboutOpen = false },
                    title = { Text(stringResource(R.string.about_title)) },
                    text = {
                        Column {
                            NavRow(stringResource(R.string.privacy_policy), p) { privacyOpen = true }
                            NavRow(stringResource(R.string.website), p) {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL)))
                            }
                            Text(
                                stringResource(R.string.version_text, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                                color = p.muted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    },
                    confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text(stringResource(R.string.ok)) } }
                )
            }

            if (privacyOpen) {
                AlertDialog(
                    onDismissRequest = { privacyOpen = false },
                    title = { Text(stringResource(R.string.privacy_policy)) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.privacy_text))
                        }
                    },
                    confirmButton = { TextButton(onClick = { privacyOpen = false }) { Text(stringResource(R.string.ok)) } }
                )
            }

            if (whatsNewOpen) {
                AlertDialog(
                    onDismissRequest = { closeWhatsNew() },
                    title = { Text(stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME)) },
                    text = { Text(whatsNew) },
                    confirmButton = { TextButton(onClick = { closeWhatsNew() }) { Text(stringResource(R.string.ok)) } }
                )
            }

            AnimatedVisibility(
                visible = LockState.locked,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LockScreen(p) { promptUnlock(activity) }
            }
        }
    }
}

@Composable
fun LockScreen(p: Palette, onUnlock: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔒", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.lock_title), color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.unlock)) }
    }
}

@Composable
fun TabBar(p: Palette, selected: Int, onSelect: (Int) -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    val labels = listOf(R.string.tab_overview, R.string.tab_dates, R.string.tab_calendar)
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
        labels.forEachIndexed { i, id ->
            TabItem(
                label = stringResource(id),
                active = i == selected,
                p = p,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(i) }
            )
        }
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .clickable { onSettings() },
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", color = p.ink, fontSize = 22.sp)
        }
    }
}

@Composable
fun TabItem(label: String, active: Boolean, p: Palette, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) p.accent else UiColor.Transparent, animationSpec = tween(250), label = "tabbg")
    val fg by animateColorAsState(if (active) UiColor.White else p.ink, animationSpec = tween(250), label = "tabfg")
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
        Text(stringResource(R.string.app_name), color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(p.accent)
                .clickable { onAdd() },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = UiColor.White, fontSize = 24.sp)
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
        stringResource(R.string.empty_state),
        color = p.muted,
        fontSize = 15.sp,
        modifier = Modifier.padding(vertical = 60.dp, horizontal = 20.dp)
    )
}

@Composable
fun Hero(e: Entry, p: Palette, today: LocalDate, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val days = e.daysUntil(today)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "hero")
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .background(p.accent)
            .clickable(interactionSource = src, indication = null) { onClick() }
            .padding(20.dp)
    ) {
        Text(stringResource(R.string.next_up), color = UiColor(0xFFFFF9F4), fontSize = 13.sp)
        AnimatedContent(targetState = countdown(days, ctx), label = "countdown") { text ->
            Text(text, color = UiColor.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Text("${e.name} · ${typeLabel(e, ctx)}", color = UiColor(0xFFFFF1EA), fontSize = 14.sp)
    }
}

@Composable
fun EntryRow(e: Entry, p: Palette, today: LocalDate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val next = e.next(today)
    val days = e.daysUntil(today)
    val isToday = days == 0L
    val shape = RoundedCornerShape(20.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "row")
    val bg by animateColorAsState(if (isToday) p.today else p.card, animationSpec = tween(300), label = "rowbg")
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .then(if (isToday) Modifier.border(1.5.dp, p.accent, shape) else Modifier)
            .clickable(interactionSource = src, indication = null) { onClick() }
            .padding(14.dp),
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
                color = UiColor(0xFFFFF9F4),
                fontSize = 11.sp
            )
            Text(next.dayOfMonth.toString(), color = UiColor.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
            Text(
                metaLine(e, today, ctx),
                color = p.muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            countdown(days, ctx),
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
                Column {
                    Crossfade(targetState = next, label = "hero") { e ->
                        Hero(e, p, today) { onOpen(e) }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            val soon = sorted.filter { it.id != next.id && it.daysUntil(today) <= 30 }
            val later = sorted.filter { it.daysUntil(today) > 30 }
            if (soon.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.section_coming_up), p) }
                items(soon, key = { it.id }) { e ->
                    EntryRow(e, p, today, Modifier.animateItemPlacement()) { onOpen(e) }
                }
            }
            if (later.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.section_later), p) }
                items(later, key = { it.id }) { e ->
                    EntryRow(e, p, today, Modifier.animateItemPlacement()) { onOpen(e) }
                }
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
            item { SectionLabel(stringResource(R.string.section_all), p) }
            items(sorted, key = { it.id }) { e ->
                EntryRow(e, p, today, Modifier.animateItemPlacement()) { onOpen(e) }
            }
        }
    }
}

@Composable
fun CalendarPage(all: List<Entry>, p: Palette, today: LocalDate, onAdd: () -> Unit, onOpen: (Entry) -> Unit) {
    val ctx = LocalContext.current
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val firstWeekday = month.atDay(1).dayOfWeek.value % 7
    val weeks = (firstWeekday + month.lengthOfMonth() + 6) / 7
    val weekdayNames = (0L..6L).map { DayOfWeek.SUNDAY.plus(it).getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    val sel = selected

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
                    Text(d, color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
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
                                val isSel = sel == date
                                val hasEvent = all.any { matchesDay(it, date) }
                                val isToday = date == today
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) p.accent else if (isToday) p.today else UiColor.Transparent)
                                        .clickable { selected = if (isSel) null else date },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        dayNumber.toString(),
                                        color = if (isSel) UiColor.White else p.ink,
                                        fontSize = 15.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (hasEvent) {
                                        Box(
                                            Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isSel) UiColor.White else p.highlight)
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
            val list = if (sel != null) {
                all.filter { matchesDay(it, sel) }
            } else {
                all.filter { runCatching { LocalDate.parse(it.date).monthValue == month.monthValue }.getOrDefault(false) }
                    .sortedBy { runCatching { LocalDate.parse(it.date).dayOfMonth }.getOrDefault(0) }
            }
            Column {
                SectionLabel(
                    stringResource(if (sel != null) R.string.section_selected_day else R.string.section_this_month),
                    p
                )
                if (list.isEmpty()) {
                    Text(stringResource(R.string.nothing_on_day), color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
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
    val ctx = LocalContext.current
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
            Text(
                stringResource(if (existing == null) R.string.new_date else R.string.edit_date),
                color = p.ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(stringResource(R.string.field_name), color = p.muted, fontSize = 13.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.field_type), color = p.muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == "birthday",
                    onClick = { kind = "birthday" },
                    label = { Text(stringResource(R.string.type_birthday)) }
                )
                FilterChip(
                    selected = kind == "anniversary",
                    onClick = { kind = "anniversary" },
                    label = { Text(stringResource(R.string.type_anniversary)) }
                )
            }

            Text(stringResource(R.string.field_date), color = p.muted, fontSize = 13.sp)
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(prettyDate(date))
            }

            Text(stringResource(R.string.field_remind), color = p.muted, fontSize = 13.sp)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 1, 3, 7, 14).forEach { d ->
                    FilterChip(selected = remind == d, onClick = { remind = d }, label = { Text(remindLabel(d, ctx)) })
                }
            }

            Text(stringResource(R.string.field_rel), color = p.muted, fontSize = 13.sp)
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
            ) { Text(stringResource(R.string.save)) }

            if (existing != null) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete)) }
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
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun SettingsHeader(title: String, p: Palette, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) {
            Text("‹ " + stringResource(R.string.back), color = p.accent, fontSize = 16.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(96.dp))
    }
}

@Composable
fun SettingsCard(p: Palette, title: String, content: @Composable ColumnScope.() -> Unit) {
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
fun NavRow(title: String, p: Palette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("›", color = p.muted, fontSize = 20.sp)
    }
}

@Composable
fun SettingsMain(
    p: Palette,
    defaultView: Int,
    defaultRemind: Int,
    themeKey: String,
    lockOn: Boolean,
    lang: String,
    versionText: String,
    onBack: () -> Unit,
    onView: (Int) -> Unit,
    onRemind: (Int) -> Unit,
    onTheme: (String) -> Unit,
    onLock: (Boolean) -> Unit,
    onLang: (String) -> Unit,
    onAllowNotifications: () -> Unit,
    onTestNotification: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPeople: () -> Unit,
    onAbout: () -> Unit,
    onWhatsNew: () -> Unit
) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsHeader(stringResource(R.string.settings_title), p, onBack)

        SettingsCard(p, stringResource(R.string.settings_general)) {
            Text(stringResource(R.string.default_view), color = p.muted, fontSize = 13.sp)
            ChipRow {
                listOf(R.string.tab_overview, R.string.tab_dates, R.string.tab_calendar).forEachIndexed { i, id ->
                    FilterChip(selected = defaultView == i, onClick = { onView(i) }, label = { Text(stringResource(id)) })
                }
            }
            Text(stringResource(R.string.default_remind), color = p.muted, fontSize = 13.sp)
            ChipRow {
                listOf(0, 1, 3, 7, 14).forEach { d ->
                    FilterChip(selected = defaultRemind == d, onClick = { onRemind(d) }, label = { Text(remindLabel(d, ctx)) })
                }
            }
            Text(stringResource(R.string.language), color = p.muted, fontSize = 13.sp)
            ChipRow {
                LANG_TAGS.forEachIndexed { i, tag ->
                    FilterChip(selected = lang == tag, onClick = { onLang(tag) }, label = { Text(LANG_NAMES[i]) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_lock), color = p.ink, fontSize = 15.sp)
                    Text(stringResource(R.string.app_lock_desc), color = p.muted, fontSize = 12.sp)
                }
                Switch(checked = lockOn, onCheckedChange = onLock)
            }
        }

        SettingsCard(p, stringResource(R.string.theme)) {
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
            Text(stringResource(themeNameRes(themeKey)), color = p.ink, fontSize = 14.sp)
        }

        SettingsCard(p, stringResource(R.string.notifications)) {
            Text(stringResource(R.string.notif_desc), color = p.muted, fontSize = 13.sp)
            Button(onClick = onAllowNotifications, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.allow_notifications))
            }
            OutlinedButton(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.test_notification))
            }
        }

        SettingsCard(p, stringResource(R.string.data)) {
            Text(stringResource(R.string.backup_desc), color = p.muted, fontSize = 13.sp)
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_backup)) }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_backup)) }
        }

        SettingsCard(p, stringResource(R.string.people)) {
            NavRow(stringResource(R.string.people_desc), p, onPeople)
        }

        SettingsCard(p, stringResource(R.string.info)) {
            NavRow(stringResource(R.string.about), p, onAbout)
            NavRow(stringResource(R.string.whats_new), p, onWhatsNew)
            Text(versionText, color = p.muted, fontSize = 13.sp)
        }

        Text(
            stringResource(R.string.built_with),
            color = p.muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

@Composable
fun PeoplePage(
    all: List<Entry>,
    p: Palette,
    today: LocalDate,
    onBack: () -> Unit,
    onOpen: (Entry) -> Unit,
    onDelete: (String) -> Unit
) {
    val ctx = LocalContext.current
    val groups = all.groupBy { it.name }.toList().sortedBy { it.first.lowercase() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsHeader(stringResource(R.string.people_title), p, onBack)
        Text(stringResource(R.string.people_desc), color = p.muted, fontSize = 13.sp)
        if (groups.isEmpty()) {
            Text(stringResource(R.string.people_empty), color = p.muted, fontSize = 14.sp)
        }
        groups.forEach { (name, list) ->
            SettingsCard(p, name) {
                Text(
                    ctx.resources.getQuantityString(R.plurals.person_dates, list.size, list.size),
                    color = p.muted,
                    fontSize = 13.sp
                )
                list.sortedBy { it.daysUntil(today) }.forEach { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(e) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${prettyDate(e.date)} · ${typeLabel(e, ctx)}",
                            color = p.ink,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(countdown(e.daysUntil(today), ctx), color = p.highlight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = { onDelete(name) }) {
                    Text(stringResource(R.string.delete_person), color = p.accent)
                }
            }
        }
    }
}
