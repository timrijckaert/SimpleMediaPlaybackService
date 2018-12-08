package be.vrt.simpleaudioplayback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.STREAM_MUSIC
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.devbrackets.android.exomedia.AudioPlayer
import com.google.android.exoplayer2.C

class MediaPlaybackService : MediaBrowserServiceCompat(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
        private const val STUDIO_BRUSSELS_TITLE = "Studio Brussel"
        private const val STUDIO_BRUSSELS_DESC = "Life is Music"
        private const val STUDIO_BRUSSELS_LIVE_STREAM_URL = "https://live-vrt.akamaized.net/groupc/live/f404f0f3-3917-40fd-80b6-a152761072fe/live.isml/.m3u8"

        private const val LOG_TAG = "MediaPlaybackService"
        private const val CHANNEL_ID = "1"
        private const val NOTIFICATION_ID = 1345
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioPlayer by lazy {
        AudioPlayer(this@MediaPlaybackService).apply {
            setWakeMode(this@MediaPlaybackService, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(STREAM_MUSIC)
            setDataSource(Uri.parse(STUDIO_BRUSSELS_LIVE_STREAM_URL))
            prepareAsync()
            seekTo(C.TIME_UNSET)
            setOnErrorListener {
                true
            }
            setOnCompletionListener {

            }
        }
    }
    private val noisyReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (audioPlayer.isPlaying) {
                    handlePause()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(
                baseContext,
                LOG_TAG,
                ComponentName(applicationContext, androidx.media.session.MediaButtonReceiver::class.java),
                null
        ).apply {
            setMediaButtonReceiver(PendingIntent.getBroadcast(this@MediaPlaybackService, 0, Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setClass(
                        this@MediaPlaybackService,
                        androidx.media.session.MediaButtonReceiver::class.java
                )
            }, 0))

            setPlaybackState(PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE).build())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPrepare() {
                    this@MediaPlaybackService.mediaSession.isActive = true
                }

                override fun onPlay() {
                    handlePlay()
                }

                override fun onPause() {
                    handlePause()
                }

                override fun onStop() {
                    //TODO: Service should stop on delete intent
                    stopSelf()
                }
            })
            setSessionToken(sessionToken)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initializeChannelForOreo()
        }

        startService(Intent(applicationContext, this@MediaPlaybackService.javaClass))
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.release()
        mediaSession.release()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    private fun handlePause() {
        unregisterReceiver(noisyReceiver)
        updateNotification(false)
        stopForeground(false)
        audioPlayer.pause()
        setMediaPlaybackState(false)
    }

    private fun handlePlay() {
        if (!successfullyRetrievedAudioFocus()) {
            return
        }

        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        startForeground(NOTIFICATION_ID, updateNotification(true))
        audioPlayer.start()
        setMediaPlaybackState(true)
    }

    private fun setMediaPlaybackState(isPlaying: Boolean) {
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder().apply {
            if (isPlaying) {
                setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PAUSE)
            } else {
                setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY)
            }
            setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    0f
            )
        }.build())
    }

    private fun updateNotification(isPlaying: Boolean): Notification =
            NotificationCompat.Builder(this, CHANNEL_ID).apply {
                setContentTitle(STUDIO_BRUSSELS_TITLE)
                setContentText(STUDIO_BRUSSELS_DESC)

                val sessionActivity =
                        mediaSession.controller.sessionActivity
                            ?: PendingIntent.getActivity(
                                    this@MediaPlaybackService,
                                    0,
                                    Intent(this@MediaPlaybackService, MediaPlayerActivity::class.java), 0
                            )
                setContentIntent(sessionActivity)
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
                        androidx.media.app.NotificationCompat.MediaStyle()
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

    private fun successfullyRetrievedAudioFocus(): Boolean =
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).requestAudioFocus(
                    this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_GAIN

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS                    -> {
                if (audioPlayer.isPlaying) {
                    //TODO:
                    // Debatable:
                    // Update the notification too or call handlePause?
                    // or should we completely shut down the service?
                    handlePause()
                    audioPlayer.stopPlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT          -> handlePause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> audioPlayer.setVolume(0.3F, 0.3F)
            AudioManager.AUDIOFOCUS_GAIN                    -> {
                audioPlayer.setVolume(1F, 1F)
                handlePlay()
            }
        }
    }

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

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) = result.sendResult(null)
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = MediaBrowserServiceCompat.BrowserRoot(
            MY_EMPTY_MEDIA_ROOT_ID,
            null
    )
}
