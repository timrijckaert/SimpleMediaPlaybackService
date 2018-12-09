[ ![Download](https://api.bintray.com/packages/timrijckaert/SimpleMediaPlaybackService/simple-media-playback-service/images/download.svg) ](https://bintray.com/timrijckaert/SimpleMediaPlaybackService/simple-media-playback-service/_latestVersion)

# What?

Simple Music Service that is able to stream a simple audio stream to be used like a radio player. ([Studio Brussels][2] and [MNM][3] from [VRT][1])  
No metadata is (_yet_) included.  

The used player is [ExoMedia][4], but any player would do.

The following list of specs are required by [Android best practices][5].  
This is the current state of the service:  
- The service is started as a foreground service which means a notification is shown during music playback. :heavy_check_mark:
- The notification can not be swiped away whilst music is playing :heavy_check_mark:
- The notification can be swiped away when music is paused/stopped :heavy_check_mark:
- The service should be killed when the notification is swiped away :x: [Delete Intent does not fire?]
- The notification should show the current play back state of the service (play/pause) :heavy_check_mark:
- When clicking on a notification it should open the app :heavy_check_mark:
- When the activity is killed the notification should still be shown and music should still play :heavy_check_mark:
- When an audio jack is plugged in while audio is playing and it disconnects, music playback should pause. :heavy_check_mark:
- When a user opens up another music app the service should stop playing music. :heavy_check_mark:
- When a user receives a notification (with sound) the music should lower the volume temporarily :heavy_check_mark:
- When a user receives a call the service should stop playing :heavy_check_mark:
- When the user opens up another music app the music player should pause. :heavy_check_mark:

# How?

```
implementation 'be.rijckaert.tim:simple-media-playback-service:0.0.1'
```

See sample folder for a full sample.

[1]: http://vrt.be/
[2]: https://stubru.be/
[3]: https://mnm.be/
[4]: https://github.com/brianwernick/ExoMedia
[5]: https://developer.android.com/guide/topics/media-apps/audio-app/building-an-audio-app
