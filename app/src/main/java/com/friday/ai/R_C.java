package com.friday.ai;

import android.app.Activity;
import android.content.Context;

/** Avoids compile-time dependency on the generated R class. */
final class R_C {
    private R_C() {}
    static int layout(Context c, String name) {
        return c.getResources().getIdentifier(name, "layout", c.getPackageName());
    }
    static int id(Context c, String name) {
        return c.getResources().getIdentifier(name, "id", c.getPackageName());
    }
    static int drawable(Context c, String name) {
        return c.getResources().getIdentifier(name, "drawable", c.getPackageName());
    }
}
