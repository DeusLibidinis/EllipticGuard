package dev.deus.ellipticprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Uses v0.2-v0.4 preference names so updates keep the existing auto-start switch and log. */
public final class GuardState {
    private static final String PREFS = "elliptic_boot_settings";
    private static final String ENABLED = "boot_auto_enabled";
    private static final String REPORT = "last_boot_report";
    private static final Object LOCK = new Object();

    private GuardState() { }

    public static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true);
    }

    public static void setEnabled(Context c, boolean enabled) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(ENABLED, enabled).commit();
    }

    public static String report(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(REPORT, "Sin eventos registrados.");
    }

    public static void clear(Context c) {
        synchronized (LOCK) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(REPORT).commit();
        }
    }

    public static void log(Context c, String message) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        String entry = "[" + stamp + "] " + message + "\n";
        Log.i("EllipticGuard", message);
        synchronized (LOCK) {
            SharedPreferences prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String previous = prefs.getString(REPORT, "");
            String next = previous + entry;
            if (next.length() > 28000) {
                next = next.substring(next.length() - 22000);
                int newline = next.indexOf('\n');
                if (newline >= 0) next = next.substring(newline + 1);
            }
            prefs.edit().putString(REPORT, next).commit();
        }
    }
}
