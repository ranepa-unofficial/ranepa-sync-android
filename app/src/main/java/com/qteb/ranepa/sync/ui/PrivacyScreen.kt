package com.qteb.ranepa.sync.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Политика конфиденциальности: свежая версия из репозитория, без сети — копия
 * из приложения. Показывается русская часть, до `---`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var blocks by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { blocks = loadPrivacy(context) }
    Scaffold(topBar = { BackBar("Конфиденциальность", onBack) }) { padding ->
        val list = blocks
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items(list) { block ->
                Box(Modifier.widthIn(max = 700.dp).fillMaxWidth()) { Block(block) }
            }
        }
    }
}

@Composable
private fun Block(block: String) {
    when {
        block.startsWith("## ") -> Text(block.removePrefix("## "), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 10.dp))
        block.startsWith("- ") -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.split("\n- ").forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•")
                    Text(inline(item.removePrefix("- ")))
                }
            }
        }
        else -> Text(inline(block))
    }
}

/** **Жирный** — единственная разметка внутри абзацев политики. */
private fun inline(markdown: String): AnnotatedString = buildAnnotatedString {
    markdown.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}

/** Блоки документа без заголовка первого уровня: он дублирует панель. */
private suspend fun loadPrivacy(context: Context): List<String> = withContext(Dispatchers.IO) {
    var text: String? = null
    for (url in listOf(Links.PRIVACY_RAW, Links.PRIVACY_CDN)) {
        text = runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.inputStream.bufferedReader().use { it.readText() }.takeIf {
                connection.responseCode == 200 && it.trim().startsWith("#")
            }
        }.getOrNull()
        if (text != null) break
    }
    if (text == null) text = runCatching { context.assets.open("PRIVACY.md").bufferedReader().use { it.readText() } }.getOrNull()
    (text ?: "").split("\n---").first()
        .split("\n\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("# ") }
}
