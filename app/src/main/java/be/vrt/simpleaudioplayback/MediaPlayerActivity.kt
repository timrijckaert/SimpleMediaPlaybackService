package be.vrt.simpleaudioplayback

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.appcompat.app.AppCompatActivity
import be.rijckaert.tim.SimpleMediaPlaybackService
import be.vrt.vualto.Configuration
import be.vrt.vualto.PlayerActivity
import be.vrt.vualto.SkinTheme
import kotlinx.android.synthetic.main.activity_main.*

class MediaPlayerActivity : AppCompatActivity() {

    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {

        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {

        }
    }

    private lateinit var mediaController: MediaControllerCompat
    private val connectionCallback: MediaBrowserCompat.ConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaBrowser.sessionToken.also { token ->
                MediaControllerCompat(
                        this@MediaPlayerActivity,
                        token
                )

                mediaController = MediaControllerCompat(this@MediaPlayerActivity, mediaBrowser.sessionToken)
                MediaControllerCompat.setMediaController(this@MediaPlayerActivity, mediaController)
            }

            mediaController.registerCallback(mediaControllerCallback)
        }

        override fun onConnectionSuspended() {
            //The service has crashed. Disable transport controls until it automatically reconnects
        }

        override fun onConnectionFailed() {
            //The service has refused our connection
        }
    }

    private val mediaBrowser: MediaBrowserCompat by lazy {
        MediaBrowserCompat(
                this,
                ComponentName(this, SimpleMediaPlaybackService::class.java),
                connectionCallback,
                null
        )
    }

    private val mediaPlaybackController
        get() = mediaController.transportControls

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        studioBrussel.setOnClickListener {
            mediaPlaybackController.prepareFromMediaId(
                    "https://live-vrt.akamaized.net/groupc/live/f404f0f3-3917-40fd-80b6-a152761072fe/live.isml/.m3u8",
                    Bundle().apply {
                        putString(SimpleMediaPlaybackService.EXTRA_TITLE, "Studio Brussel")
                        putString(SimpleMediaPlaybackService.EXTRA_DESC, "Life is your mother")
                        putInt(SimpleMediaPlaybackService.EXTRA_ICON, R.drawable.studio_brussel_logo)
                    }
            )
        }

        mnm.setOnClickListener {
            mediaPlaybackController.prepareFromMediaId(
                    "https://live-radio.lwc.vrtcdn.be/groupa/live/68dc3b80-040e-4a75-a394-72f3bb7aff9a/live.isml/.m3u8 ",
                    Bundle().apply {
                        putString(SimpleMediaPlaybackService.EXTRA_TITLE, "MNM")
                        putString(SimpleMediaPlaybackService.EXTRA_DESC, "Music & More")
                        putInt(SimpleMediaPlaybackService.EXTRA_ICON, R.drawable.mnm_logo)
                    }
            )
        }

        vualto.setOnClickListener {
            val playerConfiguration = Configuration.Builder()
                    .videoIDWith("vualto_mnm")
                    .apiUrlWith("https://media-services-public.vrt.be/vualto-video-aggregator-web/rest/external/v1/")
                    .skinThemeWith(SkinTheme.VRTNWS)
                    .finishOnVideoEndWith(true)
                    .build()
            val playerIntent = Intent(this, PlayerActivity::class.java)
            playerIntent.putExtra(Configuration.INTENT_KEY, playerConfiguration)

            startActivity(playerIntent)
        }
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onStop() {
        super.onStop()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(mediaControllerCallback)
        mediaBrowser.disconnect()
    }
}
