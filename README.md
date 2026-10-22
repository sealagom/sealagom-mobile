# SeaLagom app (Android now, iOS later)

The native shell of https://www.sealagom.com, navigational warnings of the whole world.
The site itself lives in its own code; this repository is the app around it. API and integrations:
https://github.com/sealagom/sealagom-api · tools: https://github.com/sealagom/sealagom-tools ·
browser extension: https://github.com/sealagom/sealagom-extension · developers: https://www.sealagom.com/developers/

The app is sealagom.com in a native shell (Capacitor 8). It loads the live site, so every site
change reaches the app at once; only native parts need a new app release.

What the app adds to the site:
- **Offline reading**: inside the app the site registers the site's `sw.js`. Every page,
  warning and map tile the user opened is kept on the phone and opens without a connection;
  the site's `app-offline.html` lists what is saved. Signing out clears it.
- **Downloads**: exports (PDF, KML, GPX, ECDIS charts, CSV) go to the phone's Downloads
  (`MainActivity.java`).
- **Push notifications**: `@capacitor/push-notifications`, switched on once Firebase is set up
  (`google-services.json` as a CI secret; the app then says `push` in its user agent).
- **No purchases in the app**: Google Play only allows its own billing for digital plans, so the
  plans and checkout pages show `AppPlansNotice` inside the app.

The site knows it runs in the app from the user agent (`SeaLagomApp/<version>`) or
`window.Capacitor` (the site's `native-app.ts`).

## Build

Nothing to install on the PC: `.github/workflows/android.yml` builds on GitHub, runs the app in an
emulator (online and offline screenshots) and pushes the results to the `android-ci` branch:

    git fetch origin android-ci && git show origin/android-ci:summary.txt

With the signing secrets set it also builds the signed `app-release.aab` for Google Play
(download it from the run's artifacts).

Local changes to icons: replace `assets/*.png`, run `npm run assets`, commit.


## Secrets the build expects (GitHub Actions, never in the repo)

`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` for the signed bundle, `GOOGLE_SERVICES_JSON` for push. Without them the workflow still builds the debug APK and runs the emulator check.

MIT for the code; the data on sealagom.com stays under the site's terms.
