package be.vrt.simpleaudioplayback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager.STREAM_MUSIC
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.devbrackets.android.exomedia.AudioPlayer
import com.google.android.exoplayer2.C

class MediaPlaybackService : MediaBrowserServiceCompat() {

    companion object {
        private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
        private const val LOG_TAG = "log_tag"
        private const val CHANNEL_ID = "1"
        private const val NOTIFICATION_ID = 1345
    }

    private lateinit var mediaSession: MediaSessionCompat

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val audioPlayer by lazy {
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
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(baseContext, LOG_TAG, ComponentName(applicationContext, MediaButtonReceiver::class.java), null).apply {

            //Media button receiver for pre lollipop
            setMediaButtonReceiver(PendingIntent.getBroadcast(this@MediaPlaybackService, 0, Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setClass(
                        this@MediaPlaybackService,
                        MediaButtonReceiver::class.java
                )
            }, 0))

            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setPlaybackState(PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE).build())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPrepare() {
                    super.onPrepare()
                    this@MediaPlaybackService.mediaSession.isActive = true
                }

                override fun onPlay() {
                    startForeground(NOTIFICATION_ID, updateNotification(true))
                    audioPlayer.start()
                    setMediaPlaybackState(true)
                }

                override fun onPause() {
                    super.onPause()
                    updateNotification(false)
                    stopForeground(false)
                    audioPlayer.pause()
                    setMediaPlaybackState(false)
                }
            })
            setSessionToken(sessionToken)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initializeChannelForOreo()
        }

        startService(Intent(applicationContext, this@MediaPlaybackService.javaClass))
    }

    private fun setMediaPlaybackState(isPlaying: Boolean) {
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder().apply {
            if (isPlaying) {
                setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PAUSE)
            } else {
                setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY)
            }
            setState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
        }.build())
    }

    private fun updateNotification(isPlaying: Boolean): Notification = NotificationCompat.Builder(this, CHANNEL_ID).apply {
        setContentTitle("Title")
        setContentText("Description")
        setSubText("Subtext")

        setContentIntent(mediaSession.controller.sessionActivity)

        setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        baseContext,
                        PlaybackStateCompat.ACTION_STOP
                )
        )

        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        setSmallIcon(R.drawable.ic_notification_icon)
        color = ContextCompat.getColor(baseContext, R.color.colorPrimary)

        addAction(
                NotificationCompat.Action(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                        getString(if (isPlaying) R.string.pause else R.string.play),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                baseContext,
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                        )
                )
        )

        setStyle(
                android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken)
                        .setShowActionsInCompactView(0)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(
                                MediaButtonReceiver.buildMediaButtonPendingIntent(
                                        baseContext,
                                        PlaybackStateCompat.ACTION_STOP
                                )
                        )
        )
    }.build().also { NotificationManagerCompat.from(this@MediaPlaybackService).notify(NOTIFICATION_ID, it) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeChannelForOreo() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            CHANNEL_ID,
                            "whatever",
                            NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "whatever channel name" })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) = result.sendResult(null)
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = MediaBrowserServiceCompat.BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null)
}
