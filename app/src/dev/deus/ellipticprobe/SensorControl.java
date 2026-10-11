package dev.deus.ellipticprobe;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Only supports the discovered Xiaomi-specific Elliptic handle. No UID or general sensor commands. */
public final class SensorControl {
    public static final String HANDLE = "03000000";
    private SensorControl() { }

    public static boolean hasDump(Context c) {
        return c.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED;
    }

    public static Sensor findElliptic(SensorManager manager) {
        if (manager == null) return null;
        List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_PROXIMITY);
        for (Sensor sensor : sensors) {
            String name = sensor.getName().toLowerCase(Locale.ROOT);
            String vendor = sensor.getVendor().toLowerCase(Locale.ROOT);
            if (name.contains("elliptic") || vendor.contains("elliptic")) return sensor;
        }
        return null;
    }

    public static String command(String action) {
        if (!"disable".equals(action) && !"enable".equals(action)) return "ACCION RECHAZADA";
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/dumpsys", "sensorservice", action, HANDLE)
                    .redirectErrorStream(true).start();
            final Process proc = process;
            final StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (output.length() < 1200) output.append(line).append(' ');
                    }
                } catch (Exception e) {
                    if (output.length() < 1200) output.append("[lectura:").append(e).append(']');
                }
            }, "elliptic-dumpsys-output");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(6000L, TimeUnit.MILLISECONDS);
            if (!finished) process.destroyForcibly();
            reader.join(500L);
            String result = finished ? "exit=" + process.exitValue() : "TIMEOUT";
            return result + (output.length() > 0 ? "; " + output.toString().trim() : "; salida vacia");
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (process != null) process.destroy();
        }
    }
}
