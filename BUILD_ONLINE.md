# Build the APK online — no Android Studio needed

Your laptop does not need any free space for this. The build happens on
GitHub's computers, and you just download the finished APK.

## Option A (recommended): GitHub Actions — free

This project already contains the build recipe in
`.github/workflows/build-apk.yml`, so there is nothing to configure.

1. Create a free account at github.com if you don't have one.
2. Click **+ → New repository**, name it `pdf-reader`, and create it.
   Public is fine and gives free build minutes; Private also works
   (free accounts include 2,000 build minutes/month — one build of this
   app takes roughly 3–8 minutes).
3. In the new empty repository, click **uploading an existing file**
   (or **Add file → Upload files**).
4. Unzip `pdf-reader-android` on your laptop first, open the extracted
   folder, select **everything inside it** (including the `.github`
   folder — that folder is what makes the automatic build work) and
   drag it into the GitHub upload page.
5. Click **Commit changes**. The build starts automatically.
6. Go to the **Actions** tab → open the latest **Build APK** run.
7. When it turns green (✓), scroll to **Artifacts** and download
   **pdf-reader-apk**. That download is a small zip containing
   `app-debug.apk`.
8. Copy `app-debug.apk` to your phone and install it. Android will ask
   you to allow "install from this source" — allow it for your Files /
   browser app.

To rebuild later (after any code change): push the change, or open
**Actions → Build APK → Run workflow**.

If a build turns red (✗), open the run, click the failed step to see the
log, and send that log to Muse — the fix is usually a small code or
version change in the project, not something on your side.

## Option B: Codemagic

codemagic.io is a build service made for mobile apps. Sign up with your
GitHub account, add the same `pdf-reader` repository, choose a native
Android workflow, and start a build; download the APK from the build's
artifacts. Its free tier is aimed at macOS builds (500 minutes/month),
so Android/Linux builds may use paid minutes — check the current pricing
page before relying on it. Option A stays free for this project.

## Option C: GitHub Codespaces

If you later want to *edit* the code online too: in your GitHub repo,
click **Code → Codespaces → Create codespace**. That opens a full online
editor with a cloud computer behind it, where the Android SDK and Gradle
can be installed in the cloud — still nothing on your laptop.

## What to avoid

Sites/apps promising "upload a ZIP and get an APK" mostly wrap a
*website* inside a WebView shell. They cannot compile a real Kotlin
project like this one, and some are scams. A real build always runs
Gradle with the Android SDK — which is exactly what Options A–C do in
the cloud.
