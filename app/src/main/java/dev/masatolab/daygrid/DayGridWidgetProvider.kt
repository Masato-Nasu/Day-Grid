package dev.masatolab.daygrid

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.RemoteViews
import java.util.Calendar
import kotlin.math.min

class DayGridWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TICK = "dev.masatolab.daygrid.TICK"
        const val BITMAP_WIDTH = 720
        const val COLS = 60
        const val ROWS = 24
        const val BITMAP_HEIGHT = BITMAP_WIDTH * ROWS / COLS

        // Android 12以降でランチャーがウィジェット外周を角丸に切り抜いても、
        // 四隅の粒が欠けないようにグリッドを内側へ収める。
        const val SAFE_MARGIN_RATIO = 0.0625f

        fun renderFrame(withCurrentTile: Boolean): Bitmap {
            val width = BITMAP_WIDTH.toFloat()
            val height = BITMAP_HEIGHT.toFloat()
            val safeX = width * SAFE_MARGIN_RATIO
            val safeY = height * SAFE_MARGIN_RATIO
            val cell = min(
                (width - safeX * 2f) / COLS,
                (height - safeY * 2f) / ROWS
            )
            val gridWidth = cell * COLS
            val gridHeight = cell * ROWS
            val originX = (width - gridWidth) / 2f
            val originY = (height - gridHeight) / 2f

            val bmp = Bitmap.createBitmap(
                BITMAP_WIDTH,
                BITMAP_HEIGHT,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            // Transparent background — the wallpaper remains visible.

            val radius = cell * 0.34f

            val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(128, 255, 255, 255)
                style = Paint.Style.FILL
            }
            val elapsed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            fun tile(t: Int, paint: Paint) {
                val minute = t % COLS
                val hour = t / COLS
                canvas.drawCircle(
                    originX + minute * cell + cell / 2f,
                    originY + hour * cell + cell / 2f,
                    radius,
                    paint
                )
            }

            // The whole day is shown as faint dots.
            for (t in 0 until COLS * ROWS) tile(t, empty)

            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val minute = now.get(Calendar.MINUTE)
            val total = hour * 60 + minute

            // Elapsed minutes are solid white; only the current minute blinks.
            for (t in 0 until total) tile(t, elapsed)
            if (withCurrentTile) tile(total, elapsed)

            return bmp
        }

        private fun widgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(
                ComponentName(context, DayGridWidgetProvider::class.java)
            )
        }

        fun updateAll(context: Context) {
            val ids = widgetIds(context)
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_day_grid)
            views.setImageViewBitmap(R.id.frame_on, renderFrame(true))
            views.setImageViewBitmap(R.id.frame_off, renderFrame(false))
            AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
        }

        fun scheduleNextMinute(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DayGridWidgetProvider::class.java)
                .setAction(ACTION_TICK)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val next = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC,
                next,
                pendingIntent
            )
        }

        fun cancelAlarm(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DayGridWidgetProvider::class.java)
                .setAction(ACTION_TICK)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        fun refreshIfEnabled(context: Context) {
            if (widgetIds(context).isEmpty()) {
                cancelAlarm(context)
                return
            }
            updateAll(context)
            scheduleNextMinute(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
        scheduleNextMinute(context)
    }

    override fun onEnabled(context: Context) {
        refreshIfEnabled(context)
    }

    override fun onDisabled(context: Context) {
        cancelAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> refreshIfEnabled(context)
        }
    }
}
