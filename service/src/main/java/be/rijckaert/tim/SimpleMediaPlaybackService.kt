package be.rijckaert.tim

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY
import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
import android.media.AudioManager.STREAM_MUSIC
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE
import android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY
import android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE
import android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP
import android.support.v4.media.session.PlaybackStateCompat.Builder
import android.support.v4.media.session.PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
import android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING
import android.support.v4.media.session.PlaybackStateCompat.STATE_NONE
import android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
import android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media.AudioAttributesCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.devbrackets.android.exomedia.EMAudioPlayer

class SimpleMediaPlaybackService : MediaBrowserServiceCompat(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val EMPTY_MEDIA_ROOT_ID = "empty_root_id"

        const val EXTRA_TITLE = "be.vrt.simple.audio.playback.EXTRA_TITLE"
        const val EXTRA_DESC = "be.vrt.simple.audio.playback.EXTRA_DESC"
        const val EXTRA_ICON = "be.vrt.simple.audio.playback.EXTRA_ICON"

        private const val LOG_TAG = "MusicService"
        private const val CHANNEL_ID = "1349"
        private const val NOTIFICATION_ID = 1345
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaController: MediaControllerCompat
    private var isForegroundService = false
    private var shouldPlayWhenReady = false

    private val notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioPlayer by lazy {
        EMAudioPlayer(this).apply {
            setWakeMode(this@SimpleMediaPlaybackService, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(STREAM_MUSIC)
            prepareAsync()
            seekTo(Int.MIN_VALUE)
            setOnErrorListener {
                stopSelf()
                true
            }
            setOnCompletionListener {

            }
        }
    }

    private val noisyReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == ACTION_AUDIO_BECOMING_NOISY) {
                        mediaController.transportControls.pause()
                    }
                }
            }

    private fun handlePause() {
        audioPlayer.pause()
        setMediaPlaybackState(false)
    }

    private fun handlePlay() {
        setPlayWhenReady(true)
        audioPlayer.start()
        setMediaPlaybackState(true)
    }

    private fun setMediaPlaybackState(isPlaying: Boolean) {
        mediaSession.setPlaybackState(
                Builder()
                        .setState(
                                if (isPlaying) STATE_PLAYING else STATE_PAUSED,
                                PLAYBACK_POSITION_UNKNOWN,
                                0f
                        )
                        .apply {
                            setActions(
                                    when {
                                        isPlaying -> ACTION_PLAY_PAUSE or ACTION_PAUSE
                                        else      -> ACTION_PLAY_PAUSE or ACTION_PLAY
                                    }
                            )
                        }.build()
        )
    }

    //<editor-fold desc="Notification">
    private fun updateNotification(state: PlaybackStateCompat) {
        fun buildNotification(): Notification {
            if (shouldCreateNowPlayingChannel()) {
                initializeChannelForOreo()
            }

            return NotificationCompat.Builder(this, CHANNEL_ID).apply {
                val isPlaying = mediaController.playbackState.isPlaying

                setContentTitle(mediaController.metadata.description.title)
                setContentText(mediaController.metadata.description.description)

                val sessionActivity = mediaSession.controller.sessionActivity

                setContentIntent(sessionActivity)
                setDeleteIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                baseContext,
                                ACTION_STOP
                        )
                )
                setOnlyAlertOnce(true)

                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                setSmallIcon(R.drawable.ic_audio_notification_icon)
                setLargeIcon(mediaController.metadata.description.iconBitmap)

                addAction(
                        NotificationCompat.Action(
                                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                                getString(if (isPlaying) R.string.pause else R.string.play),
                                MediaButtonReceiver.buildMediaButtonPendingIntent(
                                        baseContext,
                                        ACTION_PLAY_PAUSE
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
                                                ACTION_STOP
                                        )
                                )
                )
            }.build()
        }

        val updatedState = state.state
        if (mediaController.metadata == null) {
            return
        }

        // Skip building a notification when state is "none".
        val notification: Notification? = if (updatedState != STATE_NONE) {
            buildNotification()
        } else {
            null
        }

        when (updatedState) {
            STATE_BUFFERING,
            STATE_PLAYING -> {
                registerReceiver(noisyReceiver, IntentFilter(ACTION_AUDIO_BECOMING_NOISY))

                if (!isForegroundService) {
                    startService(Intent(applicationContext, this@SimpleMediaPlaybackService.javaClass))
                    startForeground(NOTIFICATION_ID, notification)
                    isForegroundService = true
                } else if (notification != null) {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
            else          -> {
                unregisterReceiver(noisyReceiver)

                if (isForegroundService) {
                    stopForeground(false)
                    isForegroundService = false

                    // If playback has ended, also stop the service.
                    if (updatedState == STATE_NONE) {
                        stopSelf()
                    }

                    if (notification != null) {
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } else {
                        stopForeground(false)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeChannelForOreo() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            CHANNEL_ID,
                            "VRT Audio Service",
                            NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "VRT Audio Service" })
        }
    }

    private fun shouldCreateNowPlayingChannel() =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !nowPlayingChannelExists()

    @RequiresApi(Build.VERSION_CODES.O)
    fun nowPlayingChannelExists() =
            notificationManager.getNotificationChannel(CHANNEL_ID) != null
    //</editor-fold>

    //<editor-fold desc="Audio Focus">
    @get:RequiresApi(Build.VERSION_CODES.O)
    private val audioFocusRequest by lazy { buildFocusRequest() }

    private val audioAttributes = AudioAttributesCompat.Builder()
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .build()

    @TargetApi(Build.VERSION_CODES.O)
    private fun buildFocusRequest(): AudioFocusRequest =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes.unwrap() as AudioAttributes)
                    .setOnAudioFocusChangeListener(this)
                    .build()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocusOreo(): Int = audioManager.requestAudioFocus(audioFocusRequest)

    @RequiresApi(Build.VERSION_CODES.O)
    private fun abandonAudioFocusOreo() = audioManager.abandonAudioFocusRequest(audioFocusRequest)

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AUDIOFOCUS_GAIN -> {
                if (shouldPlayWhenReady) {
                    audioPlayer.setVolume(1F, 1F)
                }
                shouldPlayWhenReady = false
            }
            AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> audioPlayer.setVolume(0.2F, 0.2F)
            AUDIOFOCUS_LOSS_TRANSIENT -> {
                shouldPlayWhenReady = true
                handlePause()
            }
            AUDIOFOCUS_LOSS -> {
                setPlayWhenReady(false)
            }
        }
    }

    private fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            requestAudioFocus()
        } else {
            if (shouldPlayWhenReady) {
                shouldPlayWhenReady = false
            }
            handlePause()
            abandonAudioFocus()
        }
    }

    private fun requestAudioFocus() {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestAudioFocusOreo()
        } else {
            @Suppress("deprecation")
            audioManager.requestAudioFocus(
                    this,
                    audioAttributes.legacyStreamType,
                    AudioManager.AUDIOFOCUS_GAIN
            )
        }

        // Call the listener whenever focus is granted - even the first time!
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            shouldPlayWhenReady = true
            onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        } else {
            Log.i(LOG_TAG, "Playback not started: Audio focus request denied")
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            abandonAudioFocusOreo()
        } else {
            @Suppress("deprecation")
            audioManager.abandonAudioFocus(this)
        }
    }
    //</editor-fold>

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(
                baseContext,
                LOG_TAG,
                ComponentName(applicationContext, MediaButtonReceiver::class.java),
                null
        ).apply {
            setMediaButtonReceiver(
                    PendingIntent.getBroadcast(
                            this@SimpleMediaPlaybackService, 0, Intent(Intent.ACTION_MEDIA_BUTTON)
                            .apply {
                                setClass(
                                        this@SimpleMediaPlaybackService,
                                        MediaButtonReceiver::class.java
                                )
                            },
                            0
                    )
            )

            setPlaybackState(Builder().setActions(ACTION_PLAY or ACTION_PLAY_PAUSE).build())

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPrepare() {
                    this@SimpleMediaPlaybackService.mediaSession.isActive = true
                }

                override fun onPlay() {
                    handlePlay()
                }

                override fun onPause() {
                    handlePause()
                }

                override fun onStop() {
                    stopSelf()
                }

                override fun onPrepareFromMediaId(mediaId: String, extras: Bundle) {
                    audioPlayer.setDataSource(this@SimpleMediaPlaybackService, Uri.parse(mediaId))
                    setMetadata(
                            MediaMetadataCompat.Builder()
                                    .putString(METADATA_KEY_TITLE, extras.getString(EXTRA_TITLE))
                                    .putString(METADATA_KEY_ARTIST, extras.getString(EXTRA_DESC))
                                    .putBitmap(METADATA_KEY_ALBUM_ART, BitmapFactory.decodeResource(resources, extras.getInt(EXTRA_ICON)))
                                    .build()
                    )
                    handlePlay()
                }
            })
            setSessionToken(sessionToken)
        }

        mediaController = MediaControllerCompat(this, mediaSession).also {
            it.registerCallback(object : MediaControllerCompat.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                    state?.let { updateNotification(it) }
                }

                override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                    mediaController.playbackState?.let { updateNotification(it) }
                }
            })
        }

        startService(Intent(applicationContext, this@SimpleMediaPlaybackService.javaClass))
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.release()
        mediaSession.release()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) = result.sendResult(null)
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) = MediaBrowserServiceCompat.BrowserRoot(
            EMPTY_MEDIA_ROOT_ID,
            null
    )
}
