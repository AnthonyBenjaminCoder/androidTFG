package com.example.tfg2.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.tfg2.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class InactivityReminderWorker(
    context: Context, workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = FirebaseFirestore.getInstance()
    private val notificationChannelId = "workout_reminder_channel"

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        // email del usuario
        val userEmail = user.email ?: return Result.success()

        try {
            // buscamos el ultimo registro de ejercicio del usuario
            val querySnapshot = db.collection("userTracking")
                .document(userEmail)
                .collection("exercises")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            var shouldNotify = false

            if (querySnapshot.isEmpty) {
                // si no hay registro se notifica
                shouldNotify = true
            } else {
                // obtenemos timestamp del ultimo documento
                val document = querySnapshot.documents[0]
                val lastTimestamp = document.getTimestamp("timestamp")
                if (lastTimestamp != null) {
                    // calculamos dias desde el ultimo registro
                    val lastDateMillis = lastTimestamp.toDate().time
                    val currentTimeMillis = System.currentTimeMillis()
                    val diffInMillis = currentTimeMillis - lastDateMillis
                    val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                    if (diffInDays >= 30) {
                        // si han pasado 30 dias o mas, notificamos
                        shouldNotify = true
                    }
                } else {
                    // si no hay timestamp, notificamos
                    shouldNotify = true
                }
            }

            if (shouldNotify) {
                sendNotification()
            }
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun sendNotification() {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // creamos canal de notificaciones
            val channelName = "recordatorio de entrenamiento"
            val descriptionText = "recuerda registrar tu actividad fisica"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(notificationChannelId, channelName, importance).apply {
                description = descriptionText
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, notificationChannelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle("Llevas un mes sin ejercicio")
            .setContentText("no has registrado ningun ejercicio en mas de 30 dias, es hora de moverte")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(1, notification)
    }
}
