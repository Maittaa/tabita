package org.maittaa.tabita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Экран входа: свой PIN и/или отпечаток / блокировка телефона (PIN, графический ключ, пароль). */
@Composable
fun LockScreen(act: MainActivity) {
    val prefs = act.prefs
    val useDevice = prefs.biometric && act.canUseDeviceAuth()
    LaunchedEffect(Unit) { if (useDevice) act.promptDeviceAuth() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Tabita", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            if (prefs.hasPin) {
                PinPad(
                    title = t("Введите PIN-код"),
                    onDone = { pin -> prefs.checkPin(pin).also { if (it) act.locked = false } },
                )
            }
            if (useDevice) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = { act.promptDeviceAuth() }) {
                    Icon(Icons.Filled.Fingerprint, null)
                    Spacer(Modifier.size(8.dp))
                    Text(t("Отпечаток / блокировка телефона"))
                }
            }
            if (!prefs.hasPin && !useDevice) {
                // защитный случай: способ входа пропал (например, на телефоне сняли блокировку)
                Text(t("Способ входа недоступен. Блокировка отключена."), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { prefs.lockEnabled = false; act.locked = false }) { Text(t("Войти")) }
            }
        }
    }
}

/**
 * Цифровая клавиатура. [onDone] вызывается, когда введено 4+ цифр и нажата ✓ (или сразу при 8 цифрах);
 * вернуть false — неверно, поле очищается.
 */
@Composable
fun PinPad(title: String, onDone: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun submit() {
        if (pin.length < 4) return
        if (!onDone(pin)) { error = true; pin = "" }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (error) t("Неверный PIN-код") else title,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(16.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(
                        if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                r.forEach { k ->
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        when (k) {
                            "⌫" -> IconButton(onClick = { pin = pin.dropLast(1) }) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, t("Стереть"))
                            }
                            "✓" -> TextButton(onClick = { submit() }, enabled = pin.length >= 4) { Text("OK", fontSize = 20.sp) }
                            else -> TextButton(onClick = {
                                error = false
                                if (pin.length < 8) pin += k
                                if (pin.length == 8) submit()
                            }, modifier = Modifier.size(72.dp)) { Text(k, fontSize = 28.sp) }
                        }
                    }
                }
            }
        }
    }
}
