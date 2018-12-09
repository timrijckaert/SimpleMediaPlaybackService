package be.vrt.simpleaudioplayback

import android.support.v4.media.session.PlaybackStateCompat

inline val PlaybackStateCompat.isPlaying
    get() = (state == PlaybackStateCompat.STATE_BUFFERING) ||
            (state == PlaybackStateCompat.STATE_PLAYING)