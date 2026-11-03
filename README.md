# Live Score Float (Android)

Football live score app with a draggable floating overlay widget.
Kotlin · Jetpack Compose · no third-party network libs (HttpURLConnection + org.json).

## Build
1. Install Android Studio (Koala or newer). File ▸ Open ▸ this folder.
2. Wait for Gradle sync (needs internet the first time).
3. Run ▶ on a phone/emulator (Android 8.0+), or Build ▸ Build APK(s).

## Real live data
Get a free key at https://dashboard.api-football.com, then paste it in
Settings ▸ API key ▸ Save & refresh. Without a key the app runs in Demo mode
(simulated matches, 1 match-minute = 3 seconds) so you can test the widget.

## Widget
* Switch it on from the Live tab (grant "Display over other apps" once).
* Drag anywhere; tap to expand last events; ⇄ switches match; ✕ closes.
* ★ on a match pins it to the widget.
