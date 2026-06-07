package com.diez.stoiclauncher.presentation.widget.calendar

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.provider.CalendarContract
import android.database.Cursor
import android.net.Uri
import com.diez.stoiclauncher.R
import java.util.*
import java.text.SimpleDateFormat

/**
 * AppWidgetProvider for the Minimalist Stoic Calendar.
 */
class StoicCalendarProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        // Set up the intent that starts the CalendarRemoteViewsService, which will
        // provide the views for this collection.
        val intent = Intent(context, CalendarRemoteViewsService::class.java).apply {
            // Add the app widget ID to the intent extras.
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }

        // Instantiate the RemoteViews object for the app widget layout.
        val rv = RemoteViews(context.packageName, R.layout.widget_stoic_calendar)
        
        // Month Title
        val dateFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val monthName = dateFormat.format(Date()).replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        }
        rv.setTextViewText(R.id.tv_month_title, monthName)

        // Set up the RemoteViews object to use a RemoteViews adapter.
        // This adapter connects to a RemoteViewsService through the specified intent.
        // This is how you populate the data.
        rv.setRemoteAdapter(R.id.lv_events, intent)

        // The empty view is displayed when the collection has no items.
        // It should be a sibling of the collection view.
        rv.setEmptyView(R.id.lv_events, R.id.tv_empty)
        
        // Pending Intent for header click (Open Calendar)
        val calendarIntent = Intent(Intent.ACTION_MAIN).apply {
             addCategory(Intent.CATEGORY_APP_CALENDAR)
             flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
             context, 
             0, 
             calendarIntent, 
             android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.tv_month_title, pendingIntent)

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, rv)
    }
}

/**
 * Service that connects the RemoteViews to the Factory.
 */
class CalendarRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CalendarRemoteViewsFactory(this.applicationContext)
    }
}

/**
 * Factory that fetches Calendar data.
 */
class CalendarRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    data class CalendarEvent(
        val id: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val color: Int
    )

    private val events = ArrayList<CalendarEvent>()

    override fun onCreate() {
        // No-op
    }

    override fun onDataSetChanged() {
        events.clear()
        
        // Check permissions
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.READ_CALENDAR
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val now = System.currentTimeMillis()
            val later = now + 30L * 24 * 60 * 60 * 1000 // 30 Days
            
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, now)
            android.content.ContentUris.appendId(builder, later)
            
            val uri = builder.build()
            
            val projection = arrayOf(
                CalendarContract.Instances._ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR
            )
            
            val selection = "${CalendarContract.Instances.VISIBLE} = 1"
            val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
            
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                sortOrder
            )
            
            cursor?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Instances._ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = c.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val colorIdx = c.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                
                while (c.moveToNext()) {
                    events.add(CalendarEvent(
                        id = c.getLong(idIdx),
                        title = c.getString(titleIdx) ?: "Evento",
                        begin = c.getLong(beginIdx),
                        end = c.getLong(endIdx),
                        allDay = c.getInt(allDayIdx) == 1,
                        color = if (colorIdx != -1) c.getInt(colorIdx) else 0xFFFFFFFF.toInt()
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching calendar events", e)
        }
    }

    override fun onDestroy() {
        events.clear()
    }

    override fun getCount(): Int = events.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= events.size) return RemoteViews(context.packageName, R.layout.widget_calendar_item) // Fallback
        
        val event = events[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_calendar_item)
        
        rv.setTextViewText(R.id.tv_title, event.title)
        
        // Format Time
        val timeStr = if (event.allDay) {
            val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
            dateFormat.format(Date(event.begin))
        } else {
            val timeFormat = SimpleDateFormat("EEE, HH:mm", Locale.getDefault())
            val endFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            "${timeFormat.format(Date(event.begin))} - ${endFormat.format(Date(event.end))}"
        }
        
        rv.setTextViewText(R.id.tv_time, timeStr)
        
        // Color Indicator
        rv.setInt(R.id.v_indicator, "setBackgroundColor", event.color)
        
        // Click Intent (Open specific event)
        val fillInIntent = Intent()
        val builder = CalendarContract.CONTENT_URI.buildUpon()
        builder.appendPath("time")
        android.content.ContentUris.appendId(builder, event.begin)
        fillInIntent.data = builder.build()
        rv.setOnClickFillInIntent(R.id.tv_title, fillInIntent)
        
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = events.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    
    // Extension for capitalize
    private fun String.capitalize(): String {
        return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    companion object {
        private const val TAG = "CalendarWidget"
    }
}
