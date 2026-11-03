package com.livescore.floating

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    const val CH_SERVICE = "overlay_service"
    const val CH_GOALS = "goals"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Floating widget", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_GOALS, "Goal alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    @SuppressLint("MissingPermission")
    fun goal(ctx: Context, m: Match) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        createChannels(ctx)
        val last = m.events.lastOrNull { it.isGoal }
        val who = if (last != null) "${last.player} (${last.team}) ${last.minute}'" else m.clock
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CH_GOALS)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setContentTitle("⚽ GOAL!  ${m.home} ${m.homeScore} - ${m.awayScore} ${m.away}")
            .setContentText(who)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(ctx).notify(1000 + m.id, n)
    }
}
