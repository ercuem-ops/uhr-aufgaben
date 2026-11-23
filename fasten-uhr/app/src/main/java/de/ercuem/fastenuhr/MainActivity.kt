package de.ercuem.fastenuhr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import java.util.Calendar

const val PREFS = "fasten"
const val KEY_START = "start"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Ab Android 13 muss man Benachrichtigungen einmal erlauben
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            MaterialTheme {
                FastenScreen(
                    initialStart = prefs.getLong(KEY_START, 0L),
                    initialZiel = zielStd(this),
                    onSave = {
                        prefs.edit().putLong(KEY_START, it).apply()
                        Alarm.schedule(this, it)
                    },
                    onReset = {
                        prefs.edit().remove(KEY_START).apply()
                        Alarm.cancel(this)
                    },
                    onZiel = { std ->
                        prefs.edit().putInt(KEY_ZIEL, std).apply()
                        val start = prefs.getLong(KEY_START, 0L)
                        if (start != 0L) Alarm.schedule(this, start)
                    },
                )
            }
        }
    }
}

@Composable
fun FastenScreen(
    initialStart: Long,
    initialZiel: Int,
    onSave: (Long) -> Unit,
    onReset: () -> Unit,
    onZiel: (Int) -> Unit,
) {
    var ziel by remember { mutableIntStateOf(initialZiel) }
    var start by remember { mutableLongStateOf(initialStart) } // 0 = nichts erfasst
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmReset by remember { mutableStateOf(false) }

    // Anzeige jede Sekunde aktualisieren (Minuten springen so pünktlich um)
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (confirmReset) {
            Text("Wirklich zurücksetzen?", fontSize = 18.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Button(
                    onClick = { confirmReset = false },
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) { Text("Nein") }
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    onReset()
                    start = 0L
                    confirmReset = false
                }) { Text("Ja") }
            }
            return@Column
        }

        if (start == 0L) {
            Text("Noch nichts erfasst", fontSize = 20.sp, textAlign = TextAlign.Center)
        } else {
            Text(
                dauerText(now - start),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(seitText(start), fontSize = 14.sp, color = Color.LightGray)
        }

        Spacer(Modifier.height(12.dp))
        Chip(
            onClick = {
                val t = System.currentTimeMillis()
                onSave(t)
                start = t
                now = t
            },
            label = { Text("Gegessen", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            // Ziel umschalten: 4 -> 8 -> 16 -> 4 Std.
            CompactChip(
                onClick = {
                    ziel = ZIEL_STUFEN[(ZIEL_STUFEN.indexOf(ziel) + 1) % ZIEL_STUFEN.size]
                    onZiel(ziel)
                },
                label = { Text("Ziel $ziel Std.") },
                colors = ChipDefaults.secondaryChipColors(),
            )
            if (start != 0L) {
                Spacer(Modifier.width(6.dp))
                CompactChip(
                    onClick = { confirmReset = true },
                    label = { Text("Reset") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
}

/** z. B. "14 Std. 32 Min." bzw. "5 Min." */
fun dauerText(ms: Long): String {
    val minGesamt = maxOf(0L, ms) / 60_000
    val std = minGesamt / 60
    val min = minGesamt % 60
    return if (std > 0) "$std Std. $min Min." else "$min Min."
}

/** z. B. "Seit Mo, 12:30 Uhr" */
fun seitText(start: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = start }
    val tag = arrayOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")[c.get(Calendar.DAY_OF_WEEK) - 1]
    return "Seit %s, %02d:%02d Uhr".format(tag, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}
