package be.vrt.simpleaudioplayback

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.appcompat.app.AppCompatActivity
import be.rijckaert.tim.SimpleMediaPlaybackService
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*


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

    private suspend fun downloadLogo(logoUrl: String): Bitmap =
            withContext(Dispatchers.Default) {
                Glide.with(this@MediaPlayerActivity)
                        .asBitmap()
                        .load(logoUrl)
                        .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                        .get()
            }

    private val rootJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + rootJob)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        studioBrussel.setOnClickListener {
            uiScope.launch {
                mediaPlaybackController.prepareFromMediaId(
                        "https://live-vrt.akamaized.net/groupc/live/f404f0f3-3917-40fd-80b6-a152761072fe/live.isml/.m3u8",
                        Bundle().apply {
                            putString(SimpleMediaPlaybackService.EXTRA_TITLE, "Studio Brussel")
                            putString(SimpleMediaPlaybackService.EXTRA_DESC, "Life is your mother")
                            putParcelable(SimpleMediaPlaybackService.ALBUM_ART, downloadLogo("https://nbocdn.akamaized.net/Assets/Images_Upload/2019/01/18/c2d0f9c2-1b3b-11e9-967c-0bc51c450548_web_translate_-82.6691_-30.00524__scale_0.0586292_0.0586292__.jpg?maxheight=460&maxwidth=638"))
                        }
                )
            }
        }

        mnm.setOnClickListener {
            uiScope.launch {
                mediaPlaybackController.prepareFromMediaId(
                        "https://live-radio.lwc.vrtcdn.be/groupa/live/68dc3b80-040e-4a75-a394-72f3bb7aff9a/live.isml/.m3u8 ",
                        Bundle().apply {
                            putString(SimpleMediaPlaybackService.EXTRA_TITLE, "MNM")
                            putString(SimpleMediaPlaybackService.EXTRA_DESC, "Music & More")
                            putParcelable(SimpleMediaPlaybackService.ALBUM_ART, downloadLogo("https://cdn.uc.assets.prezly.com/cd88a436-7afd-41a1-90c4-888f874037eb/-/preview/400x400/-/quality/best/-/format/auto/"))
                        }
                )
            }
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
        rootJob.cancel()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(mediaControllerCallback)
        mediaBrowser.disconnect()
    }
}
