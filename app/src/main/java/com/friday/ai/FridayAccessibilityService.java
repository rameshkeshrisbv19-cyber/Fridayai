package com.friday.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The god-level control core of Friday. When enabled in Accessibility settings,
 * Friday can see the screen, open apps, type text and press buttons -
 * including inside WhatsApp.
 *
 * v2 - WhatsApp auto-typing fix. The five concrete defects that were fixed:
 *  1. Friday used getRootInActiveWindow(), which returns FRIDAY'S OWN overlay
 *     window while the bubble panel is up, so the text was typed into Friday
 *     itself and never reached WhatsApp. Friday now resolves the target app's
 *     window through getWindows() and ignores its own package.
 *  2. ACTION_SET_TEXT was fired on a field that was never focused; WhatsApp
 *     silently ignores that. Friday now performs ACTION_FOCUS first and then
 *     verifies that the text really landed (clipboard paste as fallback).
 *  3. "First EditText found in the tree" is not the message box: it could be an
 *     invisible node or the search box. Friday now only accepts visible, enabled
 *     fields and prefers WhatsApp's real ids (entry / search_src_text).
 *  4. When clicking the contact name, the search EditText contains that same
 *     name, so Friday clicked its own search box and the chat never opened.
 *     Contact lookup now skips every editable / search-field node.
 *  5. The send fallback tapped (rect.width()-90, rect.height()-180) - those are
 *     sizes, not screen coordinates. It now taps real bounds scaled by density.
 */
public class FridayAccessibilityService extends AccessibilityService {
    private static final String TAG = "FridayA11y";

    /* ---------- WhatsApp view ids (new + older versions) ---------- */
    private static final String[] WA_SEARCH_IDS = {
            "menuitem_search", "search_input", "search_src_text",
            "search_edit_text", "search_field", "search"
    };
    private static final String[] WA_SEARCH_FIELD_IDS = {
            "search_src_text", "search_input", "search_edit_text", "search_field"
    };
    private static final String[] WA_ENTRY_IDS = { "entry", "conversation_compose", "message_entry" };
    private static final String[] WA_SEND_IDS = { "send", "btn_send", "send_button" };
    private static final String[] WA_CONTACT_ROW_IDS = {
            "contactpicker_row_name", "conversations_row_contact_name",
            "contact_row_container", "contactpicker_row_photo"
    };

    private static FridayAccessibilityService instance;
    private String targetPackage = "";

    public static boolean isReady() { return instance != null; }

    /** Direct handle for the command router. */
    public static FridayAccessibilityService peekInstance() { return instance; }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Friday accessibility service connected");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent intent) {
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    /** Tell Friday which app it is currently driving (e.g. com.whatsapp). */
    public void setTargetPackage(String pkg) {
        targetPackage = pkg == null ? "" : pkg;
    }

    /* =====================================================================
     *  WINDOW / ROOT RESOLUTION
     * ===================================================================== */

    private boolean isOwnWindow(AccessibilityNodeInfo root) {
        if (root == null) return true;
        CharSequence p = root.getPackageName();
        return p != null && p.toString().equals(getPackageName());
    }

    /**
     * The root Friday should act on. Never returns Friday's own overlay window
     * unless there is literally nothing else on screen.
     */
    public AccessibilityNodeInfo getTargetRoot() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && !isOwnWindow(active)) {
            CharSequence p = active.getPackageName();
            if (targetPackage.isEmpty() || (p != null && p.toString().equals(targetPackage))) {
                return active;
            }
        }

        AccessibilityNodeInfo fallback = null;
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo w : windows) {
                if (w == null || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null || isOwnWindow(r)) continue;
                CharSequence p = r.getPackageName();
                if (p == null) continue;
                if (!targetPackage.isEmpty() && p.toString().equals(targetPackage)) return r;
                if (fallback == null) fallback = r;
            }
        }
        if (fallback != null) return fallback;
        return active;
    }

    /** Is the given app actually on screen right now? */
    public boolean isPackageOnScreen(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return false;
        for (AccessibilityWindowInfo w : windows) {
            if (w == null || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo r = w.getRoot();
            if (r == null) continue;
            CharSequence p = r.getPackageName();
            if (p != null && p.toString().equals(pkg)) return true;
        }
        return false;
    }

    /* =====================================================================
     *  NODE HELPERS
     * ===================================================================== */

    private static String idOf(AccessibilityNodeInfo n) {
        String id = n.getViewIdResourceName();
        return id == null ? "" : id.toLowerCase();
    }

    private static String classOf(AccessibilityNodeInfo n) {
        CharSequence c = n.getClassName();
        return c == null ? "" : c.toString();
    }

    private static String textOf(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        return t == null ? "" : String.valueOf(t).toLowerCase();
    }

    private static String descOf(AccessibilityNodeInfo n) {
        CharSequence d = n.getContentDescription();
        return d == null ? "" : String.valueOf(d).toLowerCase();
    }

    private static boolean visibleAndUsable(AccessibilityNodeInfo n) {
        if (n == null || !n.isEnabled() || !n.isVisibleToUser()) return false;
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        return b.width() > 2 && b.height() > 2;
    }

    private static boolean looksEditable(AccessibilityNodeInfo n) {
        if (n == null) return false;
        return n.isEditable() || classOf(n).contains("EditText");
    }

    private static boolean isSearchField(AccessibilityNodeInfo n) {
        String id = idOf(n);
        return id.contains("search") || id.endsWith(":id/entry");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static AccessibilityWindowInfo dummyUnused() { return null; }

    /* =====================================================================
     *  FINDING NODES
     * ===================================================================== */

    /** Depth-first search for the best visible, enabled editable field on screen. */
    public static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collectEditable(root, all, 0);
        AccessibilityNodeInfo first = null;
        for (AccessibilityNodeInfo n : all) {
            String id = idOf(n);
            if (id.contains(":id/entry") || id.contains("search") || id.contains("input")
                    || id.contains("message") || id.contains("text")) {
                return n;
            }
            if (first == null) first = n;
        }
        return first;
    }

    private static void collectEditable(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 60 || out.size() >= 60) return;
        if (looksEditable(n) && visibleAndUsable(n)) out.add(n);
        int c = n.getChildCount();
        for (int i = 0; i < c; i++) collectEditable(n.getChild(i), out, depth + 1);
    }

    /** Find a node whose view id contains {@code part}. */
    public static AccessibilityNodeInfo findByResourceId(AccessibilityNodeInfo root, String part) {
        if (root == null || part == null) return null;
        if (visibleAndUsable(root) && idOf(root).contains(part.toLowerCase())) return root;
        int c = root.getChildCount();
        for (int i = 0; i < c; i++) {
            AccessibilityNodeInfo r = findByResourceId(root.getChild(i), part);
            if (r != null) return r;
        }
        return null;
    }

    private static AccessibilityNodeInfo findNodeMatching(AccessibilityNodeInfo root, String needle) {
        if (root == null) return null;
        if (visibleAndUsable(root)
                && (descOf(root).contains(needle) || textOf(root).contains(needle))) return root;
        int n = root.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo found = findNodeMatching(root.getChild(i), needle);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Finds the chat/search *result row* for a contact name - deliberately
     * skipping editable nodes, because the search EditText contains the very
     * name we are looking for (that bug stopped the chat from ever opening).
     */
    private static AccessibilityNodeInfo findContactRow(AccessibilityNodeInfo n, String needle, int depth) {
        if (n == null || depth > 60) return null;
        if (visibleAndUsable(n) && !looksEditable(n) && !isSearchField(n)) {
            String d = descOf(n);
            String t = textOf(n);
            if (d.contains(needle) || t.contains(needle)) {
                if (hasClickableAncestor(n)) return n;
            }
        }
        int c = n.getChildCount();
        for (int i = 0; i < c; i++) {
            AccessibilityNodeInfo r = findContactRow(n.getChild(i), needle, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    private static boolean hasClickableAncestor(AccessibilityNodeInfo n) {
        if (n.isClickable()) return true;
        int guard = 0;
        AccessibilityNodeInfo p = n.getParent();
        while (p != null && guard++ < 10) {
            if (p.isClickable()) return true;
            p = p.getParent();
        }
        return false;
    }

    private static boolean clickSelfOrParent(AccessibilityNodeInfo node) {
        int guard = 0;
        while (node != null && !node.isClickable() && guard++ < 10) node = node.getParent();
        return node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    /* =====================================================================
     *  TYPING
     * ===================================================================== */

    /** Set text on one node: focus -> ACTION_SET_TEXT -> clipboard paste -> verify. */
    private boolean setTextOn(AccessibilityNodeInfo field, String text) {
        if (field == null) return false;
        try {
            field.refresh();
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean ok = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            if (!ok || !textWasSet(field, text)) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("friday", text));
                    field.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                } catch (Exception ignored) { }
            }
            return textWasSet(field, text);
        } catch (Exception e) {
            Log.w(TAG, "setTextOn failed: " + e);
            return false;
        }
    }

    private static boolean textWasSet(AccessibilityNodeInfo field, String expected) {
        try {
            field.refresh();
            CharSequence now = field.getText();
            if (now == null) return false;
            String s = String.valueOf(now);
            if (s.trim().isEmpty()) return false;
            String head = expected.length() > 24 ? expected.substring(0, 24) : expected;
            return s.contains(head);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Types into the best text field of the target app, retrying while the
     * field / window is still settling.
     */
    public boolean typeText(String text) {
        return typeTextInto(null, text);
    }

    /** Types into a field matching one of {@code idHints} (falls back to any editable field). */
    public boolean typeTextInto(String[] idHints, String text) {
        if (text == null || text.isEmpty()) return false;
        for (int attempt = 0; attempt < 6; attempt++) {
            AccessibilityNodeInfo root = getTargetRoot();
            AccessibilityNodeInfo field = null;
            if (idHints != null) {
                for (String hint : idHints) {
                    field = findByResourceId(root, hint);
                    if (field != null && looksEditable(field)) break;
                    field = null;
                }
            }
            if (field == null) field = findEditable(root);
            if (setTextOn(field, text)) return true;
            sleep(400);
        }
        return false;
    }

    /* =====================================================================
     *  CLICKING
     * ===================================================================== */

    /** Find a node by text/content-desc, climb to the nearest clickable parent, click it. */
    public boolean clickNode(String needle) {
        String n = needle == null ? "" : needle.toLowerCase().trim();
        if (n.isEmpty()) return false;
        return clickSelfOrParent(findNodeMatching(getTargetRoot(), n));
    }

    /** Click the first visible node whose view id contains one of {@code idParts}. */
    public boolean clickAnyResourceId(String[] idParts) {
        if (idParts == null) return false;
        for (String part : idParts) {
            AccessibilityNodeInfo n = findByResourceId(getTargetRoot(), part);
            if (n != null && clickSelfOrParent(n)) return true;
        }
        return false;
    }

    /** Click the search-result / chat row of a contact (never the search box). */
    public boolean clickContactRow(String name) {
        String needle = name == null ? "" : name.toLowerCase().trim();
        if (needle.isEmpty()) return false;
        for (int attempt = 0; attempt < 4; attempt++) {
            AccessibilityNodeInfo row = findContactRow(getTargetRoot(), needle, 0);
            if (row != null && clickSelfOrParent(row)) return true;
            sleep(500);
        }
        return clickAnyResourceId(WA_CONTACT_ROW_IDS);
    }

    /** Tap a screen coordinate (last-resort fallback). */
    public boolean tap(int x, int y) {
        try {
            GestureDescription.Builder b = new GestureDescription.Builder();
            Path p = new Path();
            p.moveTo(x, y);
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, 60));
            return dispatchGesture(b.build(), null, null);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean goHome() { return performGlobalAction(GLOBAL_ACTION_HOME); }
    public boolean goBack() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean recents() { return performGlobalAction(GLOBAL_ACTION_RECENTS); }
    public boolean notifications() { return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); }

    private float density() { return getResources().getDisplayMetrics().density; }

    /* =====================================================================
     *  WHATSAPP SPECIFICS
     * ===================================================================== */

    /** Open WhatsApp's search box (id first, then label). */
    public boolean openWhatsappSearch() {
        if (clickAnyResourceId(WA_SEARCH_IDS)) return true;
        if (clickNode("search")) return true;
        return clickNode("search or start new chat");
    }

    /** Press WhatsApp's Send button. */
    public boolean pressSend() {
        if (clickAnyResourceId(WA_SEND_IDS)) return true;
        AccessibilityNodeInfo root = getTargetRoot();
        if (root != null) {
            AccessibilityNodeInfo label = findSendLabel(root, "send", 0);
            if (label != null && clickSelfOrParent(label)) return true;
        }
        return tapSendFallback();
    }

    private static AccessibilityNodeInfo findSendLabel(AccessibilityNodeInfo n, String needle, int depth) {
        if (n == null || depth > 60) return null;
        if (visibleAndUsable(n) && (!looksEditable(n))) {
            String d = descOf(n);
            String t = textOf(n);
            if (d.equals(needle) || t.equals(needle) || d.startsWith(needle) || t.startsWith(needle)) return n;
        }
        int c = n.getChildCount();
        for (int i = 0; i < c; i++) {
            AccessibilityNodeInfo r = findSendLabel(n.getChild(i), needle, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Last resort: the send button sits just right of the message box, on the
     * same line. Uses real screen bounds - the previous version used
     * rect.width()/rect.height() as if they were absolute coordinates.
     */
    public boolean tapSendFallback() {
        AccessibilityNodeInfo root = getTargetRoot();
        if (root == null) return false;
        AccessibilityNodeInfo entry = null;
        for (String id : WA_ENTRY_IDS) {
            entry = findByResourceId(root, id);
            if (entry != null) break;
        }
        if (entry != null) {
            Rect fb = new Rect();
            entry.getBoundsInScreen(fb);
            if (fb.width() > 2 && fb.height() > 2) {
                int x = Math.min(fb.right + (int) (24 * density()),
                        getResources().getDisplayMetrics().widthPixels - 12);
                if (tap(x, fb.centerY())) return true;
            }
        }
        Rect rb = new Rect();
        root.getBoundsInScreen(rb);
        int x = rb.right - (int) (44 * density());
        int y = rb.bottom - (int) (36 * density());
        return tap(x, y);
    }

    /** True when WhatsApp's message box is empty (means the message left the box). */
    public boolean isEntryEmpty() {
        AccessibilityNodeInfo root = getTargetRoot();
        for (String id : WA_ENTRY_IDS) {
            AccessibilityNodeInfo n = findByResourceId(root, id);
            if (n != null) {
                CharSequence t = n.getText();
                return t == null || String.valueOf(t).trim().isEmpty();
            }
        }
        return false;
    }

    /* =====================================================================
     *  WAITING (replaces the old blind hardcoded delays)
     * ===================================================================== */

    /** Block until any visible node contains {@code needle}. */
    public boolean awaitNode(String needle, long timeoutMs) {
        String n = needle == null ? "" : needle.toLowerCase();
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (findNodeMatching(getTargetRoot(), n) != null) return true;
            sleep(250);
        }
        return false;
    }

    /** Block until a node with the given view-id fragment exists. */
    public boolean awaitResourceId(String idPart, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (findByResourceId(getTargetRoot(), idPart) != null) return true;
            sleep(250);
        }
        return false;
    }

    /** Block until the given app's window is on screen. */
    public boolean awaitPackage(String pkg, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (isPackageOnScreen(pkg)) return true;
            sleep(250);
        }
        return false;
    }

    /** Block until a usable text field exists on the target app's screen. */
    public boolean awaitEditable(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (findEditable(getTargetRoot()) != null) return true;
            sleep(250);
        }
        return false;
    }
}
