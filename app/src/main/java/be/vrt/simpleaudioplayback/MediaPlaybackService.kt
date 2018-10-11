package be.vrt.simpleaudioplayback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager.STREAM_MUSIC
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.devbrackets.android.exomedia.AudioPlayer
import com.google.android.exoplayer2.C

class MediaPlaybackService : MediaBrowserServiceCompat() {

    companion object {
        private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
        private const val LOG_TAG = "log_tag"
        private const val CHANNEL_ID = "1"
    }

    private lateinit var mediaSession: MediaSessionCompat

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(baseContext, LOG_TAG).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setPlaybackState(PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE).build())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPrepare() {
                    super.onPrepare()
                    this@MediaPlaybackService.mediaSession.isActive = true
                }

                override fun onPlay() {
                    super.onPlay()
                    AudioPlayer(this@MediaPlaybackService).apply {
                        setWakeMode(this@MediaPlaybackService, PowerManager.PARTIAL_WAKE_LOCK)
                        setAudioStreamType(STREAM_MUSIC)
                        setDataSource(Uri.parse("https://live-vrt.akamaized.net/groupc/live/f404f0f3-3917-40fd-80b6-a152761072fe/live.isml/.m3u8"))
                        prepareAsync()
                        seekTo(C.TIME_UNSET)
                        setOnErrorListener {
                            println("blablablablbal: $it")
                            true
                        }
                        setOnCompletionListener {

                        }
                    }.start()
                }
            })
            setSessionToken(sessionToken)
        }

        val controller = mediaSession.controller

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initializeChannelForOreo()
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            // Add the metadata for the currently playing track
            setContentTitle("Title")
            setContentText("Description")
            setSubText("Subtext")

            // Enable launching the player by clicking the notification
            setContentIntent(controller.sessionActivity)

            // Stop the service when the notification is swiped away
            setDeleteIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                            baseContext,
                            PlaybackStateCompat.ACTION_STOP
                    )
            )

            // Make the transport controls visible on the lockscreen
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // Add an app icon and set its accent color
            // Be careful about the color
            setSmallIcon(R.drawable.ic_notification_icon)
            color = ContextCompat.getColor(baseContext, R.color.colorPrimary)

            // Add a pause button
            addAction(
                    NotificationCompat.Action(
                            R.drawable.ic_pause,
                            getString(R.string.pause),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    baseContext,
                                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                            )
                    )
            )

            // Take advantage of MediaStyle features
            setStyle(
                    android.support.v4.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSession.sessionToken)
                            .setShowActionsInCompactView(0)

                            // Add a cancel button
                            .setShowCancelButton(true)
                            .setCancelButtonIntent(
                                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                                            baseContext,
                                            PlaybackStateCompat.ACTION_STOP
                                    )
                            )
            )
        }

        startService(Intent(applicationContext, this@MediaPlaybackService.javaClass))
        startForeground(345, builder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = Service.START_STICKY

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeChannelForOreo() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val notificationChannel = NotificationChannel(CHANNEL_ID, "whatever", NotificationManager.IMPORTANCE_LOW).apply {
                description = "erheruigher"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            }
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("SimpleAudioPlayBack", "onTaskRemoved")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SimpleAudioPlayBack", "onDestroy")
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) = result.sendResult(null)
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = MediaBrowserServiceCompat.BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null)
}
