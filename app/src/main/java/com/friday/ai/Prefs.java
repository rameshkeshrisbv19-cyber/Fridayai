package com.friday.ai;

import android.content.Context;
import android.content.SharedPreferences;

/** Small settings store for Friday. */
public class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE);
    }

    public String apiKey() { return sp.getString("api_key", ""); }
    public void apiKey(String v) { sp.edit().putString("api_key", v).apply(); }

    public String baseUrl() { return sp.getString("base_url", FridayConfig.DEFAULT_LLM_BASE); }
    public void baseUrl(String v) { sp.edit().putString("base_url", v).apply(); }

    public String model() { return sp.getString("model", FridayConfig.DEFAULT_MODEL); }
    public void model(String v) { sp.edit().putString("model", v).apply(); }

    public String contact() { return sp.getString("contact", FridayConfig.DEFAULT_CONTACT); }
    public void contact(String v) { sp.edit().putString("contact", v).apply(); }
}
