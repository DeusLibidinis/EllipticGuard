package dev.deus.ellipticprobe;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** UI language override. System (default), Spanish or English. No permissions required. */
public final class Language {
    private static final String PREFS = "elliptic_ui";
    private static final String KEY = "language";
    public static final String SYSTEM = "system";
    private Language() { }

    public static String get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, SYSTEM);
    }

    public static void set(Context context, String code) {
        if (!SYSTEM.equals(code) && !"es".equals(code) && !"en".equals(code)) {
            code = SYSTEM;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, code).apply();
    }

    public static Context wrap(Context base) {
        String code = get(base);
        if (SYSTEM.equals(code)) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(new Locale(code));
        return base.createConfigurationContext(config);
    }
}
