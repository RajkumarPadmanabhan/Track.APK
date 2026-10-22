# TrackMe

Android app (Kotlin + Jetpack Compose). Sign in, scan your profile links ("tags") once, and your
home page tracks them every day.

## How the app flows

1. **Welcome** - tap **Continue with Google**, enter your email, and your profile is created.
   (Email + name are kept on the device. See "Real Google sign-in" below to use Google's own account picker.)
2. **Home** - "Good morning, Name", today's activity, a git-activity heatmap with streak,
   and one coloured card per scanned platform showing today's count and the change since your last check.
3. **Profile > Scan tags** (or the round scan button in the bottom bar) - pick a platform, paste the
   profile link, scan, then **Add to home**. Remove tags from the same list.
4. Tap a card to open the full detail: GitHub/GitLab get Overview, Graphs and Repos (download / share);
   others get the profile card. X and LinkedIn don't expose counts, so you can type today's number and
   TrackMe still tracks the daily change.

Daily counts: each time the home page opens (and on the refresh button) TrackMe reads every tag and saves
one number per day on the device, so "+N today" is today's value minus the last saved day. Git activity
(events per day) is merged with what was saved before, so the heatmap keeps growing beyond GitHub's ~90-day window.

## Platforms

| Platform | What TrackMe does |
|----------|-------------------|
| GitHub   | Full scan: profile, repos (auto-sorted), graphs, activity heatmap, download and share any repo |
| GitLab   | Full scan: profile, projects (auto-sorted), graphs, activity heatmap, download and share |
| YouTube  | Channel name, photo, subscribers, views, videos via the official YouTube Data API (paste your own free API key in the app) |
| Instagram | Public profile preview: name, photo, followers / following / posts when the page exposes them |
| LinkedIn | Public profile preview: name, headline, photo when LinkedIn allows it |
| X        | Profile link card (Open / Share / Copy) |

Instagram and LinkedIn have no open public API. TrackMe reads the same public link-preview
tags that chat apps use, without logging in or bypassing blocks. Instagram often and LinkedIn
usually refuse this; in that case the app shows the profile link card (Open / Share / Copy)
with a short explanation.

### YouTube API key
Google Cloud Console > create a project > enable "YouTube Data API v3" > Credentials >
Create API key. Paste it into the key field when YouTube is selected. It is stored only on
your device.

You can also share a profile link from another app into TrackMe and it picks the right platform.

## Build the APK (Trackme.apk)

1. Install Android Studio (Hedgehog 2023.1.1 or newer) and open this folder.
2. Let Gradle sync (it downloads Gradle 8.2 and dependencies automatically; set Gradle JDK to 17).
3. Menu: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. The file is created at `app/build/outputs/apk/debug/Trackme.apk`.

## Notes

- GitHub API without a token: 60 requests/hour per IP. A scan uses about 5 requests.
- The heatmap shows the full last year (53 weeks, scrolls sideways). GitHub: read from the public contributions page. GitLab: public calendar.json. If either can't be read, it falls back to recent public events (~90 days).
- GitLab's project list has no language field, so the language chart is empty for GitLab.
- Private repositories are not included.
- Package name: `com.trackme.app`, minSdk 24 (Android 7.0+).

## Real Google sign-in (optional upgrade)

The "Continue with Google" screen is the UI plus an email step. To use Google's own account picker, add
Credential Manager (`androidx.credentials` + `com.google.android.libraries.identity.googleid`), create an
OAuth *Web client ID* in Google Cloud Console, and call `GetSignInWithGoogleOption` from `LoginScreen`,
then pass the returned email to `vm.signIn(email)`. Nothing else in the app needs to change.

## Adding YouTube, Instagram and LinkedIn

- **YouTube**: Profile > Settings > paste your free YouTube Data API key, then scan the channel link. Subscribers, views and videos are read daily.
- **Instagram / LinkedIn**: scan the public profile link. If the site hides the numbers, open the tag and type today's followers; the daily change is still tracked.
- Lists of *who* follows or subscribes are not available: YouTube, Instagram and LinkedIn don't offer them publicly (YouTube only through the channel owner's OAuth, and only for public subscribers). TrackMe tracks the counts.
