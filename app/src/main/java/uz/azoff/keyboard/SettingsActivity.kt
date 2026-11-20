@file:OptIn(ExperimentalMaterial3Api::class)

package uz.azoff.keyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.FileOutputStream
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        EmojiData.load(this)
        val open = intent?.getStringExtra("open")
        setContent {
            val s by Store.settings.collectAsState()
            MaterialTheme(colorScheme = kbScheme(s)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(open)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Dict.saveAll(this)
    }
}

private fun imeId(ctx: Context) = "${ctx.packageName}/${AzoffIme::class.java.name}"

private fun isEnabled(ctx: Context): Boolean {
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
}

private fun isDefault(ctx: Context): Boolean {
    val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
    return cur.startsWith(ctx.packageName + "/")
}

private fun hasMic(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
fun SettingsScreen(open: String?) {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsState()
    val update: ((KbSettings) -> KbSettings) -> Unit = { Store.update(it) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val enabled = remember(tick) { isEnabled(ctx) }
    val default = remember(tick) { isDefault(ctx) }
    var mic by remember { mutableStateOf(hasMic(ctx)) }
    LaunchedEffect(tick) { mic = hasMic(ctx) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("no image")
                val scale = (1080f / bmp.width).coerceAtMost(1f)
                val out = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
                FileOutputStream(BgImage.file(ctx)).use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }.isSuccess
            if (ok) {
                Store.update { it.copy(bgImage = true) }
                BgImage.version.value = BgImage.version.value + 1
            } else Toast.makeText(ctx, "Rasmni ochib boʻlmadi", Toast.LENGTH_SHORT).show()
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(open) {
        when (open) {
            "mic" -> if (!hasMic(ctx)) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            "templates" -> dialog = "templates"
            "gif" -> dialog = "giphy"
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Text("⌨", fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Azoff Klaviatura", style = MaterialTheme.typography.titleLarge)
                Text("Yangi oʻzbek alifbosi bilan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 48.dp)) {
            // ---------- O'rnatish ----------
            if (!enabled || !default || !mic) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Oʻrnatish", style = MaterialTheme.typography.titleMedium)
                            SetupStep(1, "Klaviaturani yoqish", enabled) {
                                ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            SetupStep(2, "Asosiy klaviatura qilib tanlash", default) {
                                (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                            }
                            SetupStep(3, "Ovozli yozish uchun mikrofon (ixtiyoriy)", mic) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                }
            }
            item {
                var test by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = test, onValueChange = { test = it },
                    label = { Text("Shu yerda sinab koʻring") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ---------- Tartiblar ----------
            item { Section("Tartiblar va tillar") }
            LayoutIds.all.forEach { id ->
                item {
                    CheckRow(LayoutIds.name(id), id in s.layouts) { on ->
                        update { st ->
                            val list = if (on) LayoutIds.all.filter { it in st.layouts || it == id } else st.layouts - id
                            if (list.isEmpty()) st else st.copy(layouts = list, currentLayout = if (st.currentLayout in list) st.currentLayout else list.first())
                        }
                    }
                }
            }
            item { SwitchRow("Raqamlar qatori", "Harflar ustida 1–0 tugmalari", s.numberRow) { v -> update { it.copy(numberRow = v) } } }

            // ---------- Yozish ----------
            item { Section("Yozish") }
            item { SwitchRow("Soʻz takliflari", "Yozayotganda soʻzlarni taklif qiladi", s.suggestions) { v -> update { it.copy(suggestions = v) } } }
            item { SwitchRow("Avtotuzatish", "Boʻsh joy bosilganda xatoni tuzatadi (⌫ bilan bekor qilinadi)", s.autocorrect) { v -> update { it.copy(autocorrect = v) } } }
            item { SwitchRow("Keyingi soʻz taxmini", "Oʻzingiz yozgan iboralardan oʻrganadi", s.nextWord) { v -> update { it.copy(nextWord = v) } } }
            item { SwitchRow("Glayd yozish", "Barmoqni harflar ustidan sudrab yozish", s.glide) { v -> update { it.copy(glide = v) } } }
            item { SwitchRow("Glayd izini koʻrsatish", null, s.glideTrail) { v -> update { it.copy(glideTrail = v) } } }
            item { SwitchRow("Gap boshida katta harf", null, s.autoCaps) { v -> update { it.copy(autoCaps = v) } } }
            item { SwitchRow("Ikki boʻsh joy = nuqta", null, s.doubleSpacePeriod) { v -> update { it.copy(doubleSpacePeriod = v) } } }
            item { SwitchRow("Yangi harflarga avtomatik oʻtkazish", "Yangi alifboda: sh→ş, ch→ç, o'→ö, g'→ğ", s.autoNewLetters) { v -> update { it.copy(autoNewLetters = v) } } }
            item { SwitchRow("Toʻgʻri tutuq belgisi", "' oʻrniga ʻ (oʻ, gʻ) va ʼ qoʻyadi", s.fixApostrophe) { v -> update { it.copy(fixApostrophe = v) } } }
            item { SwitchRow("Yangi alifbo maslahatlari", "Takliflar qatorida oʻ→ö kabi eslatmalar", s.alphabetTips) { v -> update { it.copy(alphabetTips = v) } } }
            item { SwitchRow("Son va pul formati", "100000 → 100 000 soʻm", s.numberFormat) { v -> update { it.copy(numberFormat = v) } } }

            // ---------- Shablonlar ----------
            item { Section("Tezkor iboralar (shablonlar)") }
            item { SwitchRow("Avtomatik ochish", "Qisqartma + boʻsh joy → ibora", s.templateAutoExpand) { v -> update { it.copy(templateAutoExpand = v) } } }
            item { ClickRow("Iboralarni tahrirlash", "${s.templates.size} ta ibora") { dialog = "templates" } }

            // ---------- Ko'rinish ----------
            item { Section("Koʻrinish") }
            item { ClickRow("Mavzu", listOf("Tizim boʻyicha", "Yorugʻ", "Qorongʻi")[s.theme.coerceIn(0, 2)]) { dialog = "theme" } }
            if (Build.VERSION.SDK_INT >= 31) {
                item { SwitchRow("Material You ranglari", "Ranglar fon rasmiga moslashadi", s.dynamicColor) { v -> update { it.copy(dynamicColor = v) } } }
            }
            if (!s.dynamicColor || Build.VERSION.SDK_INT < 31) {
                item { AccentRow(s.accent) { i -> update { it.copy(accent = i) } } }
            }
            item { SliderRow("Tugma balandligi", s.keyHeight, 40, 64, "dp") { v -> update { it.copy(keyHeight = v) } } }
            item { SliderRow("Tugmalar orasidagi masofa", s.keyGap, 0, 10, "dp") { v -> update { it.copy(keyGap = v) } } }
            item { SliderRow("Tugma burchaklari", s.keyRadius, 0, 22, "dp") { v -> update { it.copy(keyRadius = v) } } }
            item { SwitchRow("Tugma chegaralari", null, s.keyBorders) { v -> update { it.copy(keyBorders = v) } } }
            item { SwitchRow("Bosilgan harfni kattalashtirib koʻrsatish", null, s.showPreview) { v -> update { it.copy(showPreview = v) } } }
            item { SwitchRow("Gorizontal holatda boʻlingan klaviatura", null, s.splitLandscape) { v -> update { it.copy(splitLandscape = v) } } }
            item { ClickRow("Bir qoʻlda yozish", listOf("Oʻchiq", "Chap qoʻl", "Oʻng qoʻl")[s.oneHand.coerceIn(0, 2)]) { dialog = "onehand" } }
            item { ClickRow("Fon rasmi", if (s.bgImage) "Oʻrnatilgan — oʻzgartirish uchun bosing" else "Galereyadan tanlash") { bgLauncher.launch(arrayOf("image/*")) } }
            if (s.bgImage) {
                item { SliderRow("Fonni xiralashtirish", (s.bgDim * 100).roundToInt(), 0, 80, "%") { v -> update { it.copy(bgDim = v / 100f) } } }
                item {
                    ClickRow("Fon rasmini olib tashlash", null) {
                        BgImage.file(ctx).delete(); update { it.copy(bgImage = false) }; BgImage.version.value++
                    }
                }
            }

            // ---------- Ovoz ----------
            item { Section("Ovoz va tebranish") }
            item { ClickRow("Tugma ovozi", soundName(s.sound)) { dialog = "sound" } }
            if (s.sound > 0) item { SliderRow("Ovoz balandligi", (s.volume * 100).roundToInt(), 5, 100, "%") { v -> update { it.copy(volume = v / 100f) } } }
            item { SwitchRow("Tebranish", null, s.vibrate) { v -> update { it.copy(vibrate = v) } } }
            if (s.vibrate) item { SliderRow("Tebranish kuchi", s.vibrateMs, 3, 50, "ms") { v -> update { it.copy(vibrateMs = v) } } }

            // ---------- Bufer ----------
            item { Section("Bufer") }
            item { SwitchRow("Bufer tarixi", "Nusxalangan matnlarni saqlaydi (inkognitoda emas)", s.clipboardHistory) { v -> update { it.copy(clipboardHistory = v) } } }
            item { ClickRow("Avtomatik tozalash", clearName(s.clipboardClearHours)) { dialog = "clear" } }

            // ---------- Maxfiylik ----------
            item { Section("Maxfiylik va internet") }
            item {
                SwitchRow(
                    "Avtomatik inkognito",
                    "Parol va maxfiy maydonlarda hech narsa eslab qolinmaydi",
                    s.incognitoAuto,
                ) { v -> update { it.copy(incognitoAuto = v) } }
            }
            item {
                SwitchRow(
                    "Internet funksiyalari",
                    "Faqat tarjima va GIF uchun. Oʻchirilsa klaviatura internetga umuman chiqmaydi.",
                    s.internet,
                ) { v -> update { it.copy(internet = v) } }
            }
            item { ClickRow("GIPHY kaliti (GIF uchun)", if (s.giphyKey.isBlank()) "Kiritilmagan" else "Kiritilgan ✓") { dialog = "giphy" } }
            item { ClickRow("Oʻrganilgan soʻzlarni oʻchirish", "Lugʻatga qoʻshilgan soʻzlar va iboralar") { dialog = "forget" } }

            // ---------- Statistika ----------
            item { Section("Statistika") }
            item { StatsCard() }
            item { ClickRow("Statistikani tozalash", null) { Stats.reset() } }

            // ---------- Haqida ----------
            item { Section("Haqida") }
            item {
                Text(
                    "Azoff Klaviatura v" + (runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "") +
                        "\n\nYangi oʻzbek lotin alifbosi (ö, ğ, ş, ç) qonun asosida 2027-yil bahoridan kuchga kiradi. " +
                        "Klaviatura joriy lotin, yangi alifbo va kirill yozuvlarini qoʻllab-quvvatlaydi.\n\n" +
                        "Oʻzbek lugʻati: kodchi/uzbek-words (GNU GPL v2). Yozganlaringiz faqat telefoningizda saqlanadi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }

    val dismiss = { dialog = null }
    when (dialog) {
        "theme" -> Choice("Mavzu", listOf("Tizim boʻyicha", "Yorugʻ", "Qorongʻi"), s.theme, dismiss) { i -> update { it.copy(theme = i) } }
        "onehand" -> Choice("Bir qoʻlda yozish", listOf("Oʻchiq", "Chap qoʻl", "Oʻng qoʻl"), s.oneHand, dismiss) { i -> update { it.copy(oneHand = i) } }
        "sound" -> Choice("Tugma ovozi", (0..4).map { soundName(it) }, s.sound, dismiss) { i -> update { it.copy(sound = i) }; Sounds.prepare(ctx, i) }
        "clear" -> {
            val opts = listOf(0, 1, 6, 24, 168)
            Choice("Bufer tarixini tozalash", opts.map { clearName(it) }, opts.indexOf(s.clipboardClearHours).coerceAtLeast(0), dismiss) { i ->
                update { it.copy(clipboardClearHours = opts[i]) }
            }
        }
        "giphy" -> {
            var key by remember { mutableStateOf(s.giphyKey) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("GIPHY kaliti") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "GIF qidirish uchun bepul kalit kerak:\n1) developers.giphy.com saytida roʻyxatdan oʻting\n2) \"Create an App\" → API\n3) Kalitni shu yerga qoʻying",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(value = key, onValueChange = { key = it.trim() }, singleLine = true, label = { Text("API key") })
                    }
                },
                confirmButton = { Button(onClick = { update { it.copy(giphyKey = key) }; dismiss() }) { Text("Saqlash") } },
                dismissButton = { TextButton(onClick = dismiss) { Text("Bekor qilish") } },
            )
        }
        "forget" -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Oʻrganilgan soʻzlar oʻchirilsinmi?") },
            text = { Text("Klaviatura siz yozgan soʻzlar va iboralarni unutadi. Asosiy lugʻat saqlanib qoladi.") },
            confirmButton = { Button(onClick = { Dict.clearUser(ctx); dismiss(); Toast.makeText(ctx, "Oʻchirildi", Toast.LENGTH_SHORT).show() }) { Text("Oʻchirish") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Bekor qilish") } },
        )
        "templates" -> TemplatesDialog(s.templates, dismiss) { list -> update { it.copy(templates = list) } }
    }
}

private fun soundName(i: Int) = listOf("Oʻchiq", "Tizim ovozi", "Mexanik", "Yumshoq", "Yozuv mashinkasi").getOrElse(i) { "?" }
private fun clearName(h: Int) = when (h) { 0 -> "Hech qachon"; 1 -> "1 soatdan keyin"; 6 -> "6 soatdan keyin"; 24 -> "1 kundan keyin"; 168 -> "1 haftadan keyin"; else -> "$h soat" }

@Composable
private fun SetupStep(n: Int, title: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = !done, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (done) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else "$n", color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f))
        if (!done) Text("›", fontSize = 22.sp)
    }
}

@Composable
private fun StatsCard() {
    val days by Stats.days.collectAsState()
    val total by Stats.totalWords.collectAsState()
    val chars by Stats.totalChars.collectAsState()
    val today = days.entries.maxByOrNull { it.key }?.value
    val last7 = days.entries.sortedByDescending { it.key }.take(7).reversed()
    val maxW = (last7.maxOfOrNull { it.value.words } ?: 1).coerceAtLeast(1)
    val top = remember(total) { Dict.topWords(8) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox("Jami soʻz", total.toString(), Modifier.weight(1f))
                StatBox("Bugun", (today?.words ?: 0).toString(), Modifier.weight(1f))
                StatBox("Tezlik", "${Stats.wpm()} s/daq", Modifier.weight(1f))
            }
            Text("Jami belgilar: $chars", style = MaterialTheme.typography.bodySmall)
            if (last7.isNotEmpty()) {
                Text("Soʻnggi kunlar", style = MaterialTheme.typography.labelLarge)
                last7.forEach { (day, d) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day.substring(5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp))
                        Box(
                            Modifier
                                .height(12.dp)
                                .fillMaxWidth(d.words.toFloat() / maxW * 0.8f + 0.01f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${d.words}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (top.isNotEmpty()) {
                Text("Eng koʻp yozilgan soʻzlar", style = MaterialTheme.typography.labelLarge)
                Text(top.joinToString(" · ") { "${it.first} (${it.second})" }, style = MaterialTheme.typography.bodySmall)
            }
            Text("Statistika faqat telefoningizda saqlanadi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatBox(title: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TemplatesDialog(initial: List<Template>, onDismiss: () -> Unit, onSave: (List<Template>) -> Unit) {
    var list by remember { mutableStateOf(initial) }
    var shortcut by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tezkor iboralar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    itemsIndexed(list) { i, t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.shortcut, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(56.dp))
                            Text(t.text, modifier = Modifier.weight(1f), maxLines = 2)
                            TextButton(onClick = { list = list.filterIndexed { j, _ -> j != i } }) { Text("✕") }
                        }
                        HorizontalDivider()
                    }
                }
                OutlinedTextField(value = shortcut, onValueChange = { shortcut = it.trim() }, singleLine = true, label = { Text("Qisqartma (masalan: asl)") })
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Toʻliq ibora") })
                Button(
                    onClick = {
                        list = list.filter { !it.shortcut.equals(shortcut, true) } + Template(shortcut, text)
                        shortcut = ""; text = ""
                    },
                    enabled = shortcut.isNotBlank() && text.isNotBlank(),
                ) { Text("+ Qoʻshish") }
            }
        },
        confirmButton = { Button(onClick = { onSave(list); onDismiss() }) { Text("Saqlash") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } },
    )
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp),
    )
}

@Composable
private fun ClickRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SliderRow(title: String, value: Int, min: Int, max: Int, suffix: String, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${local.roundToInt()} $suffix", color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}

@Composable
private fun AccentRow(selected: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text("Asosiy rang", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(accentColors) { i, c ->
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(if (i == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) { if (i == selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = { onPick(i); onDismiss() })
                        Text(o)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Yopish") } },
    )
}
