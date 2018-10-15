Simple music service that is able to stream a simple audio stream (Studio Brussels)
The used player is ExoMedia, but any player would do.

The following specs are required by Android best practices.

Specs:
- The service is started as a foreground service which means a notification is shown at all times music plays.
- The notification can not be swiped away whilst music is playing
- The notification can be swiped away when music is paused/stopped
- The service should be killed when the notification is swiped away [TODO]
- The notification should show the current play back state of the service (play/pause)
- The notification buttons should work (play/pause music)
- When clicking on a notification it should open the app
- When the activity is killed the notification should still be shown and music should still play
- When an audio jack is plugged in while audio is playing and it disconnects, music playback should pause.
- When a user opens up another music app the service should stop playing music.
- When a user receives a notification the music should duck (lower the volume temporarily) [TO CHECK]
- When a user receives a call the service should stop playing [TO CHECK]
- When destroying the activity and reopening it, it should show the correct state
