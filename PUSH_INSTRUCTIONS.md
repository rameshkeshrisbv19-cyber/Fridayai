# Build FridayAI.apk on GitHub — no token needed from anyone

This repo is wired with GitHub Actions. GitHub builds the debug APK for you in
the cloud on its own runners, using a temporary `GITHUB_TOKEN` that GitHub
issues for free per run — **you never have to share a personal access token
with anybody, including an AI assistant.**

---

## 1. Create a GitHub account (skip if you already have one)
https://github.com/signup — free.

## 2. Create a new repository under your account
https://github.com/new
- Repository name: `FridayAI` (or anything you like)
- Visibility: Private (recommended) or Public
- **Do NOT** tick "Add a README" / "Add .gitignore" / "Choose a license".
  Leave the repo **empty**.

## 3. Upload the project to the repo

Extract the zip. You get one folder: **`friday-android-studio`**.
That folder *is* the repo root — it already contains `.github/`, so the
workflow will be picked up automatically.

### Option A — GitHub website (easiest, no Git installed)
1. On your new empty repo page click **"uploading an existing file"**.
2. Open the `friday-android-studio` folder on your PC, select **everything
   inside it** (including the hidden `.github` folder — on Windows turn on
   View → Show → Hidden items) and drag it all into the browser.
3. Type a message like `FridayAI v1.1` → click **Commit changes**.

### Option B — Git command line
```bash
unzip FridayAI-fixed.zip
cd friday-android-studio

git init
git add .
git commit -m "FridayAI v1.1 - WhatsApp typing fix + new logo"
git branch -M main
git remote add origin https://github.com/<YOUR-GITHUB-USERNAME>/FridayAI.git
git push -u origin main
```
> 🔐 If Git asks you to sign in, use a personal access token **you** create in
> **your own** browser at https://github.com/settings/tokens (scope: `repo`).
> Create it, use it, then click **Delete** on that same page. Never paste it
> into a chat.

## 4. Run the workflow
1. On your repo page click the **Actions** tab.
2. If asked, click **"I understand my workflows, go ahead and enable them"**.
3. On the left, click **"Build FridayAI Debug APK"** → **Run workflow**
   (branch `main`) → green **Run workflow** button.
4. Wait 3–5 minutes. You will see green ticks next to *Build debug APK* and
   *Upload APK as a downloadable artifact*.

> The workflow also runs automatically on every push to `main`.

## 5. Download your APK
1. Click the finished green run → scroll to the bottom.
2. Under **Artifacts** click **`FridayAI-debug-apk`** → it downloads a zip.
3. Unzip it → you get **`app-debug.apk`**. That is your installable app.

## 6. Install on your phone
Send `app-debug.apk` to your phone any normal way (Google Drive, email to
yourself, Telegram to yourself, USB cable). On the phone:
1. Open the file → Android says "For your security, your phone is not allowed
   to install unknown apps from this source".
2. Tap **Settings** → toggle **"Allow from this source"** ON → back.
3. Tap **Install** → **Open**.

## 7. First-run setup on the phone (one time, in order)
1. **Grant microphone + notifications** → Allow.
2. **Allow floating bubble (Display over apps)** → toggle Friday on.
3. **Enable Friday in Accessibility (god controls)** → find Friday AI → ON.
   ⚠️ This is the switch that lets Friday type into WhatsApp. Without it,
   Friday can only talk, never touch other apps.
4. (Optional) paste any OpenAI-compatible API key → **Save AI settings**.
5. Tap **Start "Hey Friday" listening**.
6. Tap **Show floating bubble on screen**.

Now say: *"Hey Friday, open WhatsApp and send a message to Rishika about how an
AI agent works."* ✨

---

## Optional: a signed release APK (for sharing / sideloading long-term)

A debug APK is perfectly fine for your own phone. If you want a properly signed
`release` APK:

**A. Create a keystore once** (on your PC, with JDK installed):
```bash
keytool -genkey -v -keystore friday.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias friday
```

**B. Turn it into base64:**
```bash
# Linux / macOS
base64 -w0 friday.jks > keystore.txt
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("friday.jks")) > keystore.txt
```

**C. Add four repository secrets** (repo → Settings → Secrets and variables →
Actions → New repository secret):

| Secret name | Value |
|-------------|-------|
| `KEYSTORE_BASE64` | contents of `keystore.txt` |
| `KEYSTORE_PASSWORD` | the store password you chose |
| `KEY_ALIAS` | `friday` |
| `KEY_PASSWORD` | the key password you chose |

**D.** Re-run the workflow. A second job, **Build signed release APK
(optional)**, now runs and uploads `FridayAI-release-apk`. Without those
secrets the job simply skips — nothing breaks.

---

## If the build fails
- Open the failed step's log and read the **first** error line.
- `SDK location not found` → the workflow writes `local.properties` itself; make
  sure you pushed the whole `.github/` folder.
- `Could not find method …` / version errors → confirm you pushed
  `build.gradle`, `settings.gradle` and `app/build.gradle` unchanged.

---

The boss of Friday is Ramesh sir. Friday serves Rishabh sir.
