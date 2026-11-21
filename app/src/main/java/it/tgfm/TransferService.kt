package it.tgfm

import it.tgfm.R
import it.tgfm.Words

import android.app.*
import android.content.Intent
import android.os.IBinder

/** Keeps user-initiated transfers alive while the SAF picker/another app is visible. */
class TransferService: Service() {
 override fun onCreate() {
  super.onCreate()
  val manager = getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("transfer", Words.get(R.string.tgfm_transfers), NotificationManager.IMPORTANCE_LOW))
  val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  startForeground(1, Notification.Builder(this, "transfer").setSmallIcon(R.drawable.ic_tgfm).setContentTitle(Words.get(R.string.tgfm_transfer_in_progress)).setContentText(Words.get(R.string.tap_to_see_progress_or_cancel)).setContentIntent(open).setOngoing(true).build())
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
 override fun onBind(intent: Intent?): IBinder? = null
 override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
}
