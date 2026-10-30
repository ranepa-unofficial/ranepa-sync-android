package com.qteb.ranepa.sync.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qteb.ranepa.sync.R
import com.qteb.ranepa.sync.system.UpdateRules.Channel
import com.qteb.ranepa.sync.system.Updates

/** Шторка «Вышла версия»: что нового и кнопка магазина, откуда установлено приложение. */
@Composable
fun UpdateSheetContent(offer: Updates.Offer, current: String, onUpdate: () -> Unit, onLater: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)))
        Text(offer.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text("У тебя $current", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        offer.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Что нового", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp)) { Text(offer.button) }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(48.dp)) { Text("Позже") }
    }
}

/** Эта версия больше не работает: поверх всего приложения, закрыть нельзя. */
@Composable
fun RequiredUpdateScreen(channel: Channel, current: String, onUpdate: () -> Unit, onDebugReset: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(92.dp).clip(RoundedCornerShape(26.dp)))
                Text("Нужно обновить приложение", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp))
                Text("Версия $current больше не работает с личным кабинетом. Расписание и настройки после обновления останутся.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth().padding(top = 28.dp).height(52.dp)) {
                    Text(Updates.Offer(channel, 0).button)
                }
                if (onDebugReset != null) TextButton(onClick = onDebugReset, modifier = Modifier.padding(top = 8.dp)) { Text("Отладка: настоящий манифест") }
            }
        }
    }
}

/** Строка в аккаунте: «Доступна версия X» и кнопка. */
@Composable
fun UpdateRow(offer: Updates.Offer, onUpdate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f)) {
            Text(offer.version?.let { "Доступна версия $it" } ?: "Доступна новая версия", style = MaterialTheme.typography.bodyLarge)
            if (offer.channel != Channel.GITHUB) {
                Text(offer.channel.storeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onUpdate) { Text("Обновить") }
    }
}
