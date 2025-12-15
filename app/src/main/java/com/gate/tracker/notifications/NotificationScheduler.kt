package com.gate.tracker.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.gate.tracker.data.local.GateDatabase
import com.gate.tracker.notifications.workers.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Scheduler for all notification types using AlarmManager for exact timing
 * and WorkManager for immediate execution when alarm fires
 */
object NotificationScheduler {
    
    // Tags
    const val TAG_DAILY_REMINDER = "daily_reminder"
    const val TAG_REVISION_ALERT = "revision_alert"
    const val TAG_MOCK_TEST_REMINDER = "mock_test_reminder"
    const val TAG_EXAM_COUNTDOWN = "exam_countdown"
    const val TAG_INACTIVITY_ALERT = "inactivity_alert"
    const val TAG_MOTIVATIONAL = "motivational"
    
    /**
     * Schedule all notifications based on user preferences
     */
    fun scheduleAll(context: Context) {
        scheduleDailyReminder(context)
        scheduleRevisionAlerts(context)
        scheduleMockTestReminders(context)
        scheduleExamCountdown(context)
        scheduleInactivityAlerts(context)
        scheduleMotivational(context)
    }
    
    // Trigger immediate worker when AlarmReceiver fires
    fun triggerImmediateWorker(context: Context, tag: String) {
        val workRequest = OneTimeWorkRequest.Builder(
            when (tag) {
                TAG_DAILY_REMINDER -> DailyReminderWorker::class.java
                TAG_REVISION_ALERT -> RevisionAlertWorker::class.java
                TAG_MOCK_TEST_REMINDER -> MockTestReminderWorker::class.java
                TAG_EXAM_COUNTDOWN -> ExamCountdownWorker::class.java
                TAG_INACTIVITY_ALERT -> InactivityAlertWorker::class.java
                TAG_MOTIVATIONAL -> MotivationalWorker::class.java
                else -> return
            }
        ).build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
    }
    
    fun scheduleDailyReminder(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = GateDatabase.getInstance(context).notificationPreferencesDao().getPreferencesOnce()
            val time = prefs?.dailyReminderTime ?: "09:00"
            val (hour, minute) = parseTime(time)
            scheduleAlarm(context, hour, minute, TAG_DAILY_REMINDER)
        }
    }
    
    fun scheduleRevisionAlerts(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = GateDatabase.getInstance(context).notificationPreferencesDao().getPreferencesOnce()
            val time = prefs?.revisionAlertsTime ?: "18:00"
            val days = prefs?.revisionAlertsDays ?: "1,3,5"
            val (hour, minute) = parseTime(time)
            scheduleAlarmDetailed(context, hour, minute, days, TAG_REVISION_ALERT)
        }
    }
    
    fun scheduleMockTestReminders(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = GateDatabase.getInstance(context).notificationPreferencesDao().getPreferencesOnce()
            val time = prefs?.mockTestRemindersTime ?: "15:00"
            val days = prefs?.mockTestRemindersDays ?: "0,6"
            val (hour, minute) = parseTime(time)
            scheduleAlarmDetailed(context, hour, minute, days, TAG_MOCK_TEST_REMINDER)
        }
    }
    
    fun scheduleExamCountdown(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = GateDatabase.getInstance(context).notificationPreferencesDao().getPreferencesOnce()
            val time = prefs?.examCountdownTime ?: "20:00"
            val (hour, minute) = parseTime(time)
            scheduleAlarm(context, hour, minute, TAG_EXAM_COUNTDOWN)
        }
    }
    
    fun scheduleInactivityAlerts(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = GateDatabase.getInstance(context).notificationPreferencesDao().getPreferencesOnce()
            val time = prefs?.inactivityAlertsTime ?: "19:00"
            val (hour, minute) = parseTime(time)
            scheduleAlarm(context, hour, minute, TAG_INACTIVITY_ALERT)
        }
    }
    
    fun scheduleMotivational(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = GateDatabase.getInstance(context).notificationPreferencesDao().getPreferencesOnce()
            val time = prefs?.motivationalTime ?: "21:00"
            val (hour, minute) = parseTime(time)
            scheduleAlarm(context, hour, minute, TAG_MOTIVATIONAL)
        }
    }
    
    private fun scheduleAlarm(context: Context, hour: Int, minute: Int, tag: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TYPE", tag)
        }
        // Use flag FLAG_UPDATE_CURRENT so extras are updated
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        try {
            // Use inexact alarm (doesn't require special permissions)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.d("NotificationScheduler", "Scheduled $tag for ${calendar.time}")
        } catch (e: SecurityException) {
            Log.d("NotificationScheduler", "Permission error scheduling alarm", e)
        }
    }
    
    private fun scheduleAlarmDetailed(context: Context, hour: Int, minute: Int, daysString: String, tag: String) {
         val selectedDays = daysString.split(",")
            .mapNotNull { it.toIntOrNull() }
            .toSet()
        
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            // Find next occurrence
            var daysToAdd = 0
            val currentDayOfWeek = get(Calendar.DAY_OF_WEEK) - 1 
            
            if (selectedDays.contains(currentDayOfWeek) && after(currentTime)) {
                daysToAdd = 0
            } else {
                for (i in 1..7) {
                    val checkDay = (currentDayOfWeek + i) % 7
                    if (selectedDays.contains(checkDay)) {
                        daysToAdd = i
                        break
                    }
                }
            }
            add(Calendar.DAY_OF_MONTH, daysToAdd)
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TYPE", tag)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Use inexact alarm (doesn't require special permissions)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTime.timeInMillis,
                pendingIntent
            )
            Log.d("NotificationScheduler", "Scheduled $tag for ${targetTime.time}")
        } catch (e: Exception) {
             Log.e("NotificationScheduler", "Error scheduling detailed alarm", e)
        }
    }

    private fun parseTime(time: String): Pair<Int, Int> {
        return try {
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            Pair(hour, minute)
        } catch (e: Exception) {
            Pair(9, 0)
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tags = listOf(TAG_DAILY_REMINDER, TAG_REVISION_ALERT, TAG_MOCK_TEST_REMINDER, 
                         TAG_EXAM_COUNTDOWN, TAG_INACTIVITY_ALERT, TAG_MOTIVATIONAL)
        
        tags.forEach { tag ->
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("TYPE", tag)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                tag.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        WorkManager.getInstance(context).cancelAllWork()
    }
    
    fun cancelNotification(context: Context, tag: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TYPE", tag)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        
        // Also cancel any related work
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
    }

    fun scheduleDailyReminderAtHour(context: Context, hour: Int, minute: Int = 0) {
        scheduleAlarm(context, hour, minute, TAG_DAILY_REMINDER)
    }
}
