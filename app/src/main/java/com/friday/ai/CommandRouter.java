package com.friday.ai;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Friday's brain: turns one voice or typed command into real actions.
 * Examples:
 *  - "open whatsapp" / "open youtube" / "open camera"
 *  - "open whatsapp and send a message to Rishika about how an AI agent works"
 *  - "type hello world" (types into whatever app is open)
 *  - "go home" / "go back" / "show notifications"
 *  - "who is your boss"
 *
 * v2 (WhatsApp auto-typing fix): the WhatsApp mission now runs one step per
 * *verified* state on a background worker instead of firing on blind timers.
 * Every step waits for the real node (WhatsApp window, search box, contact row,
 * message box, send button) and reports exactly which step failed, so Friday
 * never "types" into a screen that is not ready yet.
 */
public class CommandRouter {
    public interface Responder { void say(String text); }

    private static final String[] WA_SEARCH_FIELD_IDS = {
            "search_src_text", "search_input", "search_edit_text", "search_field"
    };
    private static final String[] WA_ENTRY_IDS = { "entry", "conversation_compose", "message_entry" };

    private final Context c;
    private final Responder responder;
    private final Handler main = new Handler(Looper.getMainLooper());

    public CommandRouter(Context c, Responder responder) {
        this.c = c;
        this.responder = responder;
    }

    /** Safe from any thread: Friday always answers on the UI thread. */
    private void say(String text) {
        final String t = text;
        main.post(() -> {
            try { responder.say(t); } catch (Exception ignored) { }
        });
    }

    /** Strips "hey friday", "friday", "please" etc from the front. */
    public static String norm(String s) {
        String t = s == null ? "" : s.toLowerCase().trim();
        t = t.replaceFirst("^hey[\\s,]+friday[\\s,.:!?-]*", "").trim();
        t = t.replaceFirst("^friday[\\s,.:!?-]+", "").trim();
        t = t.replaceFirst("^(please|ok|okay)[\\s,]+", "").trim();
        return t;
    }

    public void handle(final String raw) {
        final String cmd = norm(raw);
        if (cmd.isEmpty()) return;

        if (cmd.contains("boss")) { say(FridayConfig.BOSS_LINE); return; }
        if (cmd.contains("who are you") || cmd.contains("your name")) {
            say("I am " + FridayConfig.APP_NAME + ", the personal AI of "
                    + FridayConfig.USER_NAME + ". " + FridayConfig.BOSS_LINE);
            return;
        }
        if (cmd.contains("go home") || cmd.equals("home")) { act(a -> a.goHome()); return; }
        if (cmd.contains("go back") || cmd.equals("back")) { act(a -> a.goBack()); return; }
        if (cmd.contains("recent apps") || cmd.contains("show recents")) { act(a -> a.recents()); return; }
        if (cmd.contains("notification")) { act(a -> a.notifications()); return; }

        boolean whatsappMission = cmd.contains("whatsapp")
                && (cmd.contains("send") || cmd.contains("message") || cmd.contains("text"));
        if (whatsappMission) { handleWhatsappMission(cmd); return; }

        if (!cmd.contains("whatsapp") && cmd.contains("send") && cmd.contains("message")) {
            say("Right now I can only auto-send messages through WhatsApp, " + FridayConfig.USER_NAME + ".");
            return;
        }

        if (cmd.startsWith("open ") || cmd.startsWith("launch ") || cmd.startsWith("start ")) {
            openAppByName(cmd.replaceFirst("^(open|launch|start)\\s+", "").trim());
            return;
        }

        if (cmd.startsWith("type ") || cmd.startsWith("search ")) {
            final String text = cmd.replaceFirst("^(type|search)\\s+", "").trim();
            act(a -> {
                boolean ok = a.typeText(text);
                say(ok ? "Typed it." : "I could not find a text box on this screen.");
            });
            return;
        }

        say("Sorry " + FridayConfig.USER_NAME + ", I did not understand. Try: open WhatsApp, open YouTube, type hello, or: open WhatsApp and send a message to Rishika about how an AI agent works.");
    }

    private interface AccAction { void run(FridayAccessibilityService a); }

    private void act(final AccAction action) {
        main.postDelayed(() -> {
            FridayAccessibilityService a = FridayAccessibilityService.peekInstance();
            if (a == null) {
                say("First enable Friday in Accessibility settings, " + FridayConfig.USER_NAME + ".");
                return;
            }
            try { action.run(a); } catch (Exception e) { say("That action failed: " + e.getMessage()); }
        }, 700);
    }

    /* =====================================================================
     *  WHATSAPP MISSION
     * ===================================================================== */

    private void handleWhatsappMission(final String cmd) {
        final String contact = extractContact(cmd);
        final String topic = extractTopic(cmd);
        final String searchName = contact.isEmpty() ? new Prefs(c).contact() : contact;

        say("Opening WhatsApp"
                + (topic.isEmpty() ? "." : ". I will write a note about " + topic + " and send it to " + searchName + "."));

        Thread worker = new Thread(() -> runWhatsappMission(searchName, topic), "friday-whatsapp");
        worker.start();
    }

    /** One verified step after another - no blind timers. */
    private void runWhatsappMission(final String searchName, final String topic) {
        try {
            final FridayAccessibilityService a = awaitService(6000);
            if (a == null) {
                say("Turn Friday on in Accessibility settings first, " + FridayConfig.USER_NAME + ", then ask me again.");
                return;
            }
            a.setTargetPackage(FridayConfig.WHATSAPP_PACKAGE);

            a.goHome();
            sleep(700);
            openAppByName("whatsapp");

            if (!a.awaitPackage(FridayConfig.WHATSAPP_PACKAGE, 15000)) {
                say("WhatsApp did not open, so I stopped.");
                return;
            }
            sleep(900);

            // WhatsApp may have restored an old chat: go one level back to the chat list.
            a.goBack();
            sleep(500);

            if (!a.openWhatsappSearch()) {
                say("I could not find the search button inside WhatsApp.");
                return;
            }
            if (!a.awaitEditable(6000)) {
                say("WhatsApp's search box did not appear.");
                return;
            }
            if (!a.typeTextInto(WA_SEARCH_FIELD_IDS, searchName)) {
                say("I could not type " + searchName + " into WhatsApp's search box.");
                return;
            }
            if (!a.awaitNode(searchName, 8000)) {
                say("I could not find " + searchName + " in the WhatsApp search results.");
                return;
            }
            a.clickContactRow(searchName);

            if (!a.awaitResourceId("entry", 10000)) {
                a.goBack();
                if (!a.awaitResourceId("entry", 6000)) {
                    say("The chat with " + searchName + " did not open.");
                    return;
                }
            }

            if (topic.isEmpty()) {
                say("The chat with " + searchName + " is open. Tell me the topic and I will write and send the note.");
                return;
            }

            final String note = writeNote(topic);
            if (!a.typeTextInto(WA_ENTRY_IDS, note)) {
                say("I could not write into the message box, " + FridayConfig.USER_NAME + ".");
                return;
            }
            sleep(1200);
            a.pressSend();
            sleep(1000);

            if (a.isEntryEmpty()) {
                say("Done - the note about " + topic + " is sent to " + searchName + ".");
            } else {
                a.pressSend();
                sleep(900);
                say(a.isEntryEmpty()
                        ? "Done - the note about " + topic + " is sent to " + searchName + "."
                        : "I pressed send but the message box still has text - please check WhatsApp once.");
            }
        } catch (Exception e) {
            say("Something went wrong inside WhatsApp: " + e.getMessage());
        }
    }

    /** Ask the AI brain for a ~1000 character note; fall back to the offline note. */
    private String writeNote(String topic) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] holder = new String[1];
        LlmClient.generateNote(c, topic, (text, error) -> {
            holder[0] = text;
            latch.countDown();
        });
        try { latch.await(35, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        String note = holder[0];
        if (note == null || note.trim().length() < 40) {
            say("No AI key is set, so I wrote the note myself and I am sending it.");
            note = buildLocalNote(topic);
        }
        return note;
    }

    private FridayAccessibilityService awaitService(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            FridayAccessibilityService a = FridayAccessibilityService.peekInstance();
            if (a != null) return a;
            sleep(250);
        }
        return FridayAccessibilityService.peekInstance();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractContact(String cmd) {
        int i = cmd.indexOf(" to ");
        if (i < 0) return "";
        String tail = cmd.substring(i + 4).trim();
        String[] stops = { " about ", " regarding ", " saying ", " that " };
        int end = tail.length();
        for (String s : stops) {
            int p = tail.indexOf(s);
            if (p > 0) end = Math.min(end, p);
        }
        String contact = tail.substring(0, end).trim();
        contact = contact.replaceFirst("\\bsir\\b$", "").trim();
        return contact;
    }

    private String extractTopic(String cmd) {
        int i = cmd.indexOf(" about ");
        if (i >= 0) return cmd.substring(i + 7).trim();
        i = cmd.indexOf(" regarding ");
        if (i >= 0) return cmd.substring(i + 11).trim();
        return "";
    }

    /** Offline fallback note (~1000 characters) when no LLM key is configured. */
    private String buildLocalNote(String topic) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here is a short note on ").append(topic).append(".\n\n");
        sb.append("1) Meaning: ").append(topic)
          .append(" is about software doing a task that normally needs human thinking - understanding, deciding and acting.\n\n");
        sb.append("2) Why it is useful: it saves time, cuts repeated manual work, reduces mistakes and keeps working non-stop.\n\n");
        sb.append("3) How it works: first a goal is fixed, then the system watches the situation, picks the best next step, performs it and checks the result, repeating this loop till the goal is met.\n\n");
        sb.append("4) Simple examples: spam filters in email, maps that predict traffic, chatbots that answer customers, and voice assistants like me, Friday.\n\n");
        sb.append("5) Key point: start with one small task, check the result, then slowly automate more work.\n\n");
        sb.append("This note was written and sent by Friday AI for ").append(FridayConfig.USER_NAME).append(".");
        String s = sb.toString();
        if (s.length() > FridayConfig.NOTE_MAX_CHARS) s = s.substring(0, FridayConfig.NOTE_MAX_CHARS);
        return s;
    }

    /** Opens an installed app by spoken name; falls back to a Google search. */
    public void openAppByName(String name) {
        final String q = name.toLowerCase().trim();
        if (q.isEmpty()) return;
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        ApplicationInfo best = null;
        int bestScore = 0;
        for (ApplicationInfo ai : apps) {
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            String label;
            try { label = pm.getApplicationLabel(ai).toString().toLowerCase(); } catch (Exception e) { continue; }
            int score = 0;
            if (label.equals(q)) score = 100;
            else if (label.startsWith(q)) score = 80;
            else if (label.contains(q)) score = 60;
            if (q.contains("whatsapp") && ai.packageName.equals(FridayConfig.WHATSAPP_PACKAGE)) score = 200;
            if (q.contains("youtube") && ai.packageName.contains("youtube")) score = 200;
            if (q.contains("camera") && ai.packageName.contains("camera")) score = 200;
            if (q.contains("chrome") && ai.packageName.contains("chrome")) score = 200;
            if (q.contains("instagram") && ai.packageName.contains("instagram")) score = 200;
            if (q.contains("settings") && ai.packageName.equals("com.android.settings")) score = 200;
            if (score > bestScore) { bestScore = score; best = ai; }
        }
        if (best != null) {
            Intent i = pm.getLaunchIntentForPackage(best.packageName);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(i);
                    say("Opening " + name + ".");
                    return;
                } catch (Exception ignored) { }
            }
        }
        try {
            Intent g = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(name)));
            g.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(g);
            say("No app named " + name + " was found, so I searched it on Google.");
        } catch (Exception e) {
            say("I could not open " + name + ".");
        }
    }
}
