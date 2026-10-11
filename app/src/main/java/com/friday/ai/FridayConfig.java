package com.friday.ai;

/** Hard identity and configuration of Friday. */
public final class FridayConfig {
    public static final String APP_NAME = "Friday AI";
    public static final String BOSS_NAME = "Ramesh sir";
    public static final String USER_NAME = "Rishabh sir";
    public static final String BOSS_LINE = BOSS_NAME + " is the boss of the Friday, " + USER_NAME + ".";
    public static final String WAKE_WORD = "hey friday";
    public static final String WAKE_PROMPT = "Yes " + USER_NAME + ", what is your work?";
    public static final String DEFAULT_LLM_BASE = "https://api.openai.com/v1/chat/completions";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_CONTACT = "Rishika";
    public static final int NOTE_MIN_CHARS = 880;
    public static final int NOTE_MAX_CHARS = 1000;
    public static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private FridayConfig() {}
}
