@file:OptIn(ExperimentalFoundationApi::class)

package uz.azoff.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

// ======================= Yuqori qator =======================

@Composable
fun TopStrip(kb: KbState, s: KbSettings, colors: KbColors) {
    Box(Modifier.fillMaxWidth().height(46.dp)) {
        when {
            kb.internal != Internal.NONE -> InternalField(kb, colors)
            kb.suggestions.isNotEmpty() || kb.tip != null -> SuggestionBar(kb, colors)
            else -> Toolbar(kb, s, colors)
        }
    }
}

@Composable
private fun InternalField(kb: KbState, colors: KbColors) {
    val placeholder = when (kb.internal) {
        Internal.EMOJI_SEARCH -> "Emoji qidirish: yurak, kulish…"
        Internal.GIF_SEARCH -> "GIF qidirish…"
        Internal.TRANSLATE -> "Tarjima uchun matn yozing…"
        Internal.NONE -> ""
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("✕", colors) {
            if (kb.internal == Internal.EMOJI_SEARCH) { kb.internal = Internal.NONE; kb.internalText = "" } else kb.closePanel()
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.key)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (kb.internalText.isEmpty()) Text(placeholder, color = colors.sub, fontSize = 14.sp, maxLines = 1)
            else Text(kb.internalText + "▏", color = colors.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (kb.internal == Internal.GIF_SEARCH) {
            Spacer(Modifier.width(6.dp))
            Chip("🔍", colors) { kb.searchGifs() }
        }
    }
}

@Composable
private fun SuggestionBar(kb: KbState, colors: KbColors) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(kb.suggestions.size) { i ->
                val sug = kb.suggestions[i]
                val hl = i == kb.highlight
                Box(
                    Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hl) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { kb.pickSuggestion(sug) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (sug.length > 28) sug.take(26) + "…" else sug,
                        color = if (hl) colors.accent else colors.text,
                        fontSize = 16.sp,
                        fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
        kb.tip?.let { t ->
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accent)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) { Text("💡 $t", color = colors.onAccent, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun Toolbar(kb: KbState, s: KbSettings, colors: KbColors) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIcon("😊", colors) { kb.openPanel(Panel.EMOJI) }
            ToolIcon("📋", colors) { kb.openPanel(Panel.CLIPBOARD) }
            ToolIcon("🎤", colors) { kb.openPanel(Panel.VOICE) }
            ToolIcon("文A", colors) { kb.openPanel(Panel.TRANSLATE) }
            ToolIcon("GIF", colors) { kb.openPanel(Panel.GIF) }
            ToolIcon("📝", colors) { kb.openPanel(Panel.TEMPLATES) }
            ToolIcon("🧮", colors) { kb.openPanel(Panel.CALC) }
            ToolIcon("Aa", colors) { kb.openPanel(Panel.TOOLS) }
            ToolIcon("✋", colors) { Store.update { it.copy(oneHand = if (it.oneHand == 0) 2 else 0) } }
            ToolIcon(if (kb.incognito) "🕶️" else "👓", colors, active = kb.incognito) { kb.toggleIncognito() }
            ToolIcon("⚙️", colors) { kb.openSettings() }
        }
        Box(
            Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.special)
                .clickable { kb.nextLayout() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) { Text(LayoutIds.short(s.currentLayout), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ToolIcon(t: String, colors: KbColors, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (active) colors.accent.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = colors.text, fontSize = if (t.length > 2) 13.sp else 19.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Chip(t: String, colors: KbColors, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.accent else colors.special)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = if (selected) colors.onAccent else colors.text, fontSize = 14.sp) }
}

// ======================= Panellar =======================

@Composable
fun PanelHost(kb: KbState, s: KbSettings, colors: KbColors, heightDp: Int) {
    Column(Modifier.fillMaxWidth().height((heightDp + 8).dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (kb.panel) {
                Panel.EMOJI -> EmojiPanel(kb, colors)
                Panel.CLIPBOARD -> ClipboardPanel(kb, s, colors)
                Panel.TEMPLATES -> TemplatesPanel(kb, s, colors)
                Panel.CALC -> CalcPanel(kb, colors)
                Panel.TOOLS -> ToolsPanel(kb, colors)
                Panel.VOICE -> VoicePanel(kb, s, colors)
                else -> {}
            }
        }
        // Pastki qator
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BottomKey("ABC", colors, Modifier.width(64.dp)) { kb.closePanel() }
            if (kb.panel == Panel.EMOJI) {
                BottomKey("🔍", colors, Modifier.width(52.dp)) {
                    kb.internal = Internal.EMOJI_SEARCH; kb.internalText = ""; kb.mode = Mode.LETTERS
                }
            }
            BottomKey("␣", colors, Modifier.weight(1f)) { kb.commitRaw(" ") }
            BottomKey("⌫", colors, Modifier.width(64.dp)) { kb.feedback(KT.DEL); kb.delete() }
            BottomKey(kb.enterLabel, colors, Modifier.width(56.dp), accent = true) { kb.enter() }
        }
    }
}

@Composable
private fun BottomKey(t: String, colors: KbColors, modifier: Modifier, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent) colors.accent else colors.special)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = if (accent) colors.onAccent else colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

// ---------- Emoji ----------

@Composable
private fun EmojiPanel(kb: KbState, colors: KbColors) {
    var group by remember { mutableIntStateOf(if (Recents.emojis.value.isEmpty()) 0 else -1) }
    var skinFor by remember { mutableStateOf<EmojiItem?>(null) }
    val recents by Recents.emojis.collectAsState()
    val all = EmojiData.items
    Column(Modifier.fillMaxSize()) {
        LazyRow(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            items(EmojiData.groups) { (g, icon) ->
                Box(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (g == group) colors.accent.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { group = g; skinFor = null }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(icon, fontSize = if (icon.length > 3) 12.sp else 20.sp, color = colors.text) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (group) {
                -2 -> LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp)) {
                    items(EmojiData.kaomoji) { k ->
                        Box(
                            Modifier.height(44.dp).clickable { kb.insertEmoji(k) },
                            contentAlignment = Alignment.Center,
                        ) { Text(k, color = colors.text, fontSize = 13.sp, maxLines = 1) }
                    }
                }
                else -> {
                    val list: List<EmojiItem> = if (group == -1) {
                        recents.map { e -> all.firstOrNull { it.e == e } ?: EmojiItem(e, -1, "", emptyList()) }
                    } else all.filter { it.group == group }
                    if (list.isEmpty()) {
                        Text(
                            if (group == -1) "Hali emoji ishlatilmagan" else "Yuklanmoqda…",
                            color = colors.sub, modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp)) {
                        items(list, key = { it.e + it.group }) { item ->
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .combinedClickable(
                                        onClick = { kb.insertEmoji(item.e) },
                                        onLongClick = { if (item.skins.isNotEmpty()) skinFor = item },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(item.e, fontSize = 26.sp)
                                if (item.skins.isNotEmpty()) {
                                    Box(
                                        Modifier.align(Alignment.BottomEnd).padding(4.dp).size(4.dp)
                                            .clip(CircleShape).background(colors.sub.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            skinFor?.let { item ->
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.popup)
                        .padding(6.dp)
                ) {
                    (listOf(item.e) + item.skins).forEach { v ->
                        Box(
                            Modifier.size(48.dp).clickable { kb.insertEmoji(v); skinFor = null },
                            contentAlignment = Alignment.Center,
                        ) { Text(v, fontSize = 28.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiSearchStrip(kb: KbState, colors: KbColors) {
    val res = remember(kb.internalText) { EmojiData.search(kb.internalText) }
    LazyRow(
        Modifier.fillMaxWidth().height(50.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (res.isEmpty()) {
            item {
                Text(
                    if (kb.internalText.isBlank()) "Soʻz yozing (oʻzbek, rus yoki ingliz tilida)" else "Topilmadi",
                    color = colors.sub, fontSize = 13.sp, modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(res) { e ->
            Box(Modifier.size(46.dp).clickable { kb.insertEmoji(e) }, contentAlignment = Alignment.Center) {
                Text(e, fontSize = 26.sp)
            }
        }
    }
}

// ---------- Bufer ----------

@Composable
private fun ClipboardPanel(kb: KbState, s: KbSettings, colors: KbColors) {
    val items by Clip.items.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📋 Bufer tarixi", color = colors.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (items.any { !it.pinned }) Chip("Tozalash", colors) { Clip.clear() }
        }
        if (!s.clipboardHistory) {
            Text("Bufer tarixi sozlamalarda oʻchirilgan.", color = colors.sub, modifier = Modifier.padding(8.dp))
        } else if (items.isEmpty()) {
            Text("Hali hech narsa nusxalanmagan. Biror matnni nusxalang — u shu yerda paydo boʻladi.", color = colors.sub, modifier = Modifier.padding(8.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.time.toString() + it.text.hashCode() }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.key)
                        .clickable { kb.commitRaw(item.text) }
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.text, color = colors.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    )
                    Box(Modifier.size(40.dp).clickable { Clip.togglePin(item) }, contentAlignment = Alignment.Center) {
                        Text(if (item.pinned) "📌" else "📍", fontSize = 16.sp, color = colors.text)
                    }
                    Box(Modifier.size(40.dp).clickable { Clip.remove(item) }, contentAlignment = Alignment.Center) {
                        Text("✕", fontSize = 16.sp, color = colors.sub)
                    }
                }
            }
        }
    }
}

// ---------- Shablonlar ----------

@Composable
private fun TemplatesPanel(kb: KbState, s: KbSettings, colors: KbColors) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📝 Tezkor iboralar", color = colors.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Chip("✎ Tahrirlash", colors) { kb.openSettings("templates") }
        }
        Text(
            "Qisqartmani yozib boʻsh joy bossangiz, ibora avtomatik qoʻyiladi.",
            color = colors.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.templates) { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.key)
                        .clickable { kb.commitRaw(t.text) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.shortcut, color = colors.accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                    Text(t.text, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------- Kalkulyator ----------

@Composable
private fun CalcPanel(kb: KbState, colors: KbColors) {
    val result = remember(kb.calcExpr) { Calc.eval(kb.calcExpr) }
    val keys = listOf(
        listOf("C", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("%", "0", ".", "⌫"),
    )
    Row(Modifier.fillMaxSize().padding(6.dp)) {
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEach { r ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.forEach { k ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (k[0].isDigit() || k == ".") colors.key else colors.special)
                                .clickable { kb.calcInput(k) },
                            contentAlignment = Alignment.Center,
                        ) { Text(k, color = colors.text, fontSize = 20.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.width(130.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.key).padding(8.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(kb.calcExpr.ifEmpty { "0" }, color = colors.sub, fontSize = 15.sp, maxLines = 3)
                    if (result != null && result != kb.calcExpr) Text("= $result", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Box(
                Modifier.height(48.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.special).clickable { kb.calcInput("=") },
                contentAlignment = Alignment.Center,
            ) { Text("=", color = colors.text, fontSize = 22.sp) }
            Box(
                Modifier.height(48.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.accent)
                    .clickable { kb.commitRaw(result ?: kb.calcExpr); kb.calcExpr = "" },
                contentAlignment = Alignment.Center,
            ) { Text("Qoʻyish", color = colors.onAccent, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------- Matn vositalari ----------

private class ToolItem(val label: String, val action: () -> Unit)

@Composable
private fun ToolsPanel(kb: KbState, colors: KbColors) {
    val tools = listOf(
        ToolItem("Lotin → Yangi alifbo") { kb.transformField { Translit.latToNew(it) } },
        ToolItem("Yangi alifbo → Lotin") { kb.transformField { Translit.newToLat(it) } },
        ToolItem("Lotin → Kirill") { kb.transformField { Translit.latToCyr(it) } },
        ToolItem("Kirill → Lotin") { kb.transformField { Translit.cyrToLat(it) } },
        ToolItem("Kirill → Yangi alifbo") { kb.transformField { Translit.latToNew(Translit.cyrToLat(it)) } },
        ToolItem("Tutuq belgilarini tuzatish ʻ ʼ") { kb.transformField { Translit.fixApostrophes(it) } },
        ToolItem("Aralash harflarni tozalash") { kb.transformField { Translit.cleanMixed(it) } },
        ToolItem("KATTA HARF") { kb.transformField { it.uppercase() } },
        ToolItem("kichik harf") { kb.transformField { it.lowercase() } },
        ToolItem("Har So'z Bosh Harf") { kb.transformField { t -> t.split(" ").joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercaseChar() } } } },
        ToolItem("Hammasini tanlash") { kb.contextAction(android.R.id.selectAll) },
        ToolItem("Nusxalash") { kb.contextAction(android.R.id.copy) },
        ToolItem("Kesish") { kb.contextAction(android.R.id.cut) },
        ToolItem("Qoʻyish") { kb.contextAction(android.R.id.paste) },
        ToolItem("↶ Bekor qilish") { kb.undo() },
        ToolItem("◀ Kursor") { kb.moveCursor(-1) },
        ToolItem("Kursor ▶") { kb.moveCursor(1) },
    )
    Column(Modifier.fillMaxSize()) {
        Text(
            "Belgilangan matnga, belgilanmagan boʻlsa butun maydonga qoʻllanadi",
            color = colors.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(tools) { t ->
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.key)
                        .clickable { kb.feedback(KT.CHAR); t.action() }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t.label, color = colors.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

// ---------- Ovoz ----------

@Composable
private fun VoicePanel(kb: KbState, s: KbSettings, colors: KbColors) {
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("uz-UZ" to "Oʻzbekcha", "ru-RU" to "Русский", "en-US" to "English").forEach { (code, name) ->
                Chip(name, colors, selected = s.voiceLang == code) {
                    Store.update { it.copy(voiceLang = code) }
                    kb.stopVoice()
                }
            }
        }
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(if (kb.voiceListening) Color(0xFFE53935) else colors.accent)
                .clickable { if (kb.voiceListening) kb.stopVoice() else kb.startVoice() },
            contentAlignment = Alignment.Center,
        ) { Text(if (kb.voiceListening) "■" else "🎤", fontSize = 36.sp, color = Color.White) }
        Text(
            kb.voiceText.ifEmpty { "Mikrofonni bosing va gapiring" },
            color = colors.text, fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- Tarjima ----------

@Composable
fun TranslateBox(kb: KbState, s: KbSettings, colors: KbColors) {
    val langs = listOf("uz" to "Oʻzbek", "ru" to "Rus", "en" to "Ingliz", "tr" to "Turk", "de" to "Nemis", "ar" to "Arab")
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(langs.firstOrNull { it.first == s.translateFrom }?.second ?: s.translateFrom, colors, selected = pickFrom) { pickFrom = !pickFrom; pickTo = false }
            Chip("⇄", colors) { Store.update { it.copy(translateFrom = it.translateTo, translateTo = it.translateFrom) } }
            Chip(langs.firstOrNull { it.first == s.translateTo }?.second ?: s.translateTo, colors, selected = pickTo) { pickTo = !pickTo; pickFrom = false }
            Spacer(Modifier.weight(1f))
            Chip(if (kb.translateBusy) "…" else "Tarjima", colors, selected = true) { kb.translate() }
        }
        if (pickFrom || pickTo) {
            LazyRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(langs) { (code, name) ->
                    Chip(name, colors) {
                        Store.update { if (pickFrom) it.copy(translateFrom = code) else it.copy(translateTo = code) }
                        pickFrom = false; pickTo = false
                    }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(if (kb.translateWholeField) "☑ Butun maydon" else "☐ Butun maydon", colors) { kb.translateWholeField = !kb.translateWholeField }
            Spacer(Modifier.width(6.dp))
            val r = kb.translateResult
            if (r != null) {
                Text(r, color = colors.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Chip("Qoʻyish", colors, selected = true) { kb.insertTranslation() }
            } else {
                Text(
                    if (s.internet) "Internet orqali (MyMemory)" else "Internet funksiyalari oʻchirilgan",
                    color = colors.sub, fontSize = 12.sp,
                )
            }
        }
    }
}

// ---------- GIF ----------

@Composable
fun GifStrip(kb: KbState, s: KbSettings, colors: KbColors) {
    val ctx = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(ctx).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()
    }
    Box(Modifier.fillMaxWidth().height(130.dp)) {
        when {
            !s.internet -> Text("Internet funksiyalari sozlamalarda oʻchirilgan.", color = colors.sub, modifier = Modifier.padding(12.dp))
            s.giphyKey.isBlank() -> Column(Modifier.padding(10.dp)) {
                Text(
                    "GIF uchun bepul GIPHY kalitini sozlamalarga kiriting (developers.giphy.com).",
                    color = colors.sub, fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                Chip("Sozlamalarni ochish", colors, selected = true) { kb.openSettings("gif") }
            }
            kb.gifBusy && kb.gifResults.isEmpty() -> Text("Yuklanmoqda…", color = colors.sub, modifier = Modifier.align(Alignment.Center))
            else -> LazyRow(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(kb.gifResults, key = { it.id + it.preview }) { g ->
                    AsyncImage(
                        model = g.preview,
                        contentDescription = null,
                        imageLoader = loader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(118.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.key)
                            .clickable { kb.sendGif(g) },
                    )
                }
            }
        }
    }
}
