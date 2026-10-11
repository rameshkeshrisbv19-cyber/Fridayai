# Friday AI — native Android app (v1.1)

A real native Android APK — not a web app — so it can open apps, draw over
them, and type into WhatsApp.

## What changed in v1.1

**1. WhatsApp auto-typing fixed.** Friday was not typing into WhatsApp for five
concrete, code-level reasons — all five are fixed (see
`FridayAccessibilityService.java` for the full list):

| # | Old behaviour | Why it broke WhatsApp | Fix |
|---|---------------|----------------------|-----|
| 1 | `getRootInActiveWindow()` | While Friday's own bubble panel is open that call returns **Friday's own window**, so the text went into Friday and never reached WhatsApp | `getTargetRoot()` reads all windows via `getWindows()`, ignores Friday's own package, and prefers the target app's window |
| 2 | `ACTION_SET_TEXT` on an unfocused field | WhatsApp silently ignores `ACTION_SET_TEXT` unless the field is focused | `ACTION_FOCUS` → `ACTION_SET_TEXT` → clipboard `ACTION_PASTE` → then **verify** the text really landed |
| 3 | "first EditText in the tree" | Could pick an invisible node or the search box instead of the message box | Only visible + enabled fields; prefers WhatsApp's real ids (`entry`, `search_src_text`) |
| 4 | `clickNode(contactName)` | The search box contains the same name, so Friday clicked its own search field and the chat never opened | `clickContactRow()` skips every editable/search node |
| 5 | `tap(rect.width()-90, rect.height()-180)` | Those are *sizes*, not screen coordinates | Taps real screen bounds, scaled by display density |

Also: the mission now runs one step per **verified state** on a worker thread
(waits for the WhatsApp window, the search box, the contact row, the message
box, the send button) instead of firing on blind 1.7-second timers — and it
tells you exactly which step failed. The accessibility service now also sets
`flagIncludeNotImportantViews`, which WhatsApp needs because it marks several
of the nodes Friday must touch as "not important for accessibility".

**2. New logo.** Every launcher icon at every density (mdpi → xxxhdpi, square +
round) is rebuilt from the supplied AI RESERVE artwork.

## Choose how you want to build the APK

| Path | What you do | Time |
|------|-------------|------|
| 🌐 **GitHub Actions** (recommended, **no token needed**) | Push to your GitHub → Actions tab → Run workflow → download artifact | 5 min setup + 3-5 min build |
| 🖥️ **Android Studio** | Open this folder in Android Studio → click Run ▶ → installs to your phone | 60 sec |
| 🐧 **Command line** | `./gradlew assembleDebug` (JDK 17 + Android SDK 34) | ~3 min |

For the GitHub route see **PUSH_INSTRUCTIONS.md** — you never share a personal
access token with anyone; GitHub uses its own temporary `GITHUB_TOKEN` per run.

## What Friday does on your phone
- Floating "F" bubble always on screen; tap → typing panel.
- "Hey Friday" is always listening — says *"Yes Rishabh sir, what is your work?"*
- Real Android `AccessibilityService` control: open any app, tap buttons,
  type into any text field (search boxes, chat boxes, password boxes).
- WhatsApp mission: "Hey Friday, open WhatsApp and send a message to Rishika
  about how an AI agent works" → go Home → open WhatsApp → search contact →
  open chat → write ~1000-char note → press Send.
- Hardcoded identity: `"Ramesh sir is the boss of the Friday, Rishabh sir."`
- Wake word: `"hey friday"`.

## Permissions Android will ask for (one time)
- Microphone + notifications
- Display over other apps (for the bubble)
- Accessibility (the "god controls")

---

The boss of Friday is Ramesh sir. Friday serves Rishabh sir.
