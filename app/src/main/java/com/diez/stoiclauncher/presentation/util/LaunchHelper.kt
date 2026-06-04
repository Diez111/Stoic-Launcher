package com.diez.stoiclauncher.presentation.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log

object LaunchHelper {

    private const val TAG = "LaunchHelper"

    fun openClock(context: Context) {
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open clock app", e)
        }
    }

    fun openCalendar(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val calIntent = Intent(Intent.ACTION_VIEW)
                calIntent.data = Uri.parse("content://com.android.calendar/time/")
                context.startActivity(calIntent)
            } catch (e2: Exception) {
                Log.w(TAG, "Could not open calendar app", e2)
            }
        }
    }

    fun openDialer(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open dialer", e)
        }
    }
}
