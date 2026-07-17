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
import android.os.Build
import android.widget.RemoteViews
import java.util.Calendar
import kotlin.math.min

class DayGridWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TICK = "dev.masatolab.daygrid.TICK"
        const val ACTION_REFRESH = "dev.masatolab.daygrid.REFRESH"
        const val BITMAP_WIDTH = 720
        const val COLS = 60
        const val ROWS = 24
        const val BITMAP_HEIGHT = BITMAP_WIDTH * ROWS / COLS
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

            for (t in 0 until COLS * ROWS) tile(t, empty)

            val now = Calendar.getInstance()
            val total = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
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

        private fun alarmPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, DayGridWidgetProvider::class.java)
                .setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun refreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, DayGridWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context,
                1002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun updateAll(context: Context) {
            val ids = widgetIds(context)
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_day_grid)
            views.setImageViewBitmap(R.id.frame_on, renderFrame(true))
            views.setImageViewBitmap(R.id.frame_off, renderFrame(false))
            views.setOnClickPendingIntent(R.id.widget_root, refreshPendingIntent(context))
            AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
        }

        fun scheduleNextMinute(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val pendingIntent = alarmPendingIntent(context)

            // Wake the device at the next minute boundary. On devices that do not
            // grant exact alarms, fall back to an inexact wake-up alarm rather than
            // silently stopping updates.
            try {
                val exactAllowed =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
                if (exactAllowed) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        next,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        next,
                        pendingIntent
                    )
                }
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next,
                    pendingIntent
                )
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(alarmPendingIntent(context))
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
        refreshIfEnabled(context)
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
            ACTION_REFRESH -> refreshIfEnabled(context)
        }
    }
}
