package de.ercuem.fastenuhr

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Komplikation fürs Zifferblatt: zeigt die vergangene Zeit seit "Gegessen".
 * Die Uhr zählt den Text selbst live hoch, die App muss dafür nicht laufen.
 */
class FastenComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        daten(type, System.currentTimeMillis() - (14 * 60 + 32) * 60_000L)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val start = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_START, 0L)
        return daten(request.complicationType, start)
    }

    private fun daten(type: ComplicationType, start: Long): ComplicationData? {
        val text: ComplicationText =
            if (start == 0L) PlainComplicationText.Builder("–").build()
            else TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.SHORT_DUAL_UNIT,
                CountUpTimeReference(Instant.ofEpochMilli(start)),
            ).setMinimumTimeUnit(TimeUnit.MINUTES).build()
        val beschreibung = PlainComplicationText.Builder("Fastenzeit").build()
        val bild = MonochromaticImage.Builder(
            Icon.createWithResource(this, R.drawable.ic_launcher_fg),
        ).build()
        val tippen = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text, beschreibung)
                .setMonochromaticImage(bild).setTapAction(tippen).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text, beschreibung)
                .setTitle(PlainComplicationText.Builder("Fasten").build())
                .setMonochromaticImage(bild).setTapAction(tippen).build()
            else -> null
        }
    }

    companion object {
        /** Nach "Gegessen" oder "Reset" das Zifferblatt sofort aktualisieren. */
        fun aktualisieren(ctx: Context) {
            ComplicationDataSourceUpdateRequester.create(
                ctx, ComponentName(ctx, FastenComplicationService::class.java),
            ).requestUpdateAll()
        }
    }
}
