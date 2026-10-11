package dev.deus.ellipticprobe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Diagnostic UI and manual override for the foreground guard. */
public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder messages = new StringBuilder();
    private TextView status;
    private TextView console;
    private Switch guardSwitch;
    private Button stopAndEnable;
    private Button forceDisable;
    private boolean busy;
    private SensorManager manager;
    private Sensor sensor;
    private SensorEventListener temporaryListener;
    private int temporaryEvents;
    private int secretTapCount = 0;
    private long lastSecretTap = 0L;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = SensorControl.findElliptic(manager);
        buildUi();
        refresh();
        log("Elliptic Guard " + GuardState.version(this) + ". Objetivo 0x" + SensorControl.HANDLE);
        log("Permiso DUMP: " + (SensorControl.hasDump(this) ? "CONCEDIDO" : "DENEGADO"));
        log("Diagnostico de arranque/actualizacion; vigilante 4 s / 30 s conservado.");
        log("No hay bloqueo UID ni permiso MANAGE_SENSORS.");
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 248, 251));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root);

        // Identidad visual: escudo DL. El icono y la version comparten un secreto.
        ImageView shield = new ImageView(this);
        int logo = getResources().getIdentifier("ic_elliptic_guard", "drawable", getPackageName());
        if (logo != 0) shield.setImageResource(logo);
        shield.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams shieldLp = new LinearLayout.LayoutParams(dp(94), dp(94));
        shieldLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        shield.setContentDescription("Elliptic Guard. Escudo Deus Libidinis.");
        root.addView(shield, shieldLp);
        shield.setOnClickListener(v -> secretTap());

        TextView title = text("ELLIPTIC GUARD", 26, Color.rgb(20, 33, 49));
        title.setTypeface(null, 1);
        root.addView(title);
        TextView version = text(GuardState.version(this) + "  ·  POCO X6 Pro  ·  Sin root ni Shizuku", 13,
                Color.rgb(91, 106, 121));
        root.addView(version);
        version.setOnClickListener(v -> secretTap());
        TextView credit = text("DEUS LIBIDINIS  /  SENSOR RESEARCH", 11,
                Color.rgb(25, 115, 133));
        credit.setLetterSpacing(0.06f);
        margin(root, credit, 5);

        status = text("", 14, Color.rgb(35, 50, 62));
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        status.setBackgroundColor(Color.rgb(227, 235, 246));
        margin(root, status, 12);

        guardSwitch = new Switch(this);
        guardSwitch.setText("Vigilante persistente / autoinicio");
        guardSwitch.setTextSize(16);
        guardSwitch.setChecked(GuardState.enabled(this));
        margin(root, guardSwitch, 12);
        guardSwitch.setOnCheckedChangeListener((v, checked) -> {
            GuardState.setEnabled(this, checked);
            log(checked ? "Activando vigilante..." : "Deteniendo vigilante (no reactiva el sensor).");
            if (checked) startGuard(GuardService.ACTION_START);
            else stopGuard();
            ui.postDelayed(this::refresh, 1200L);
        });

        Button start = button("INICIAR VIGILANTE AHORA");
        margin(root, start, 6);
        start.setOnClickListener(v -> {
            if (!GuardState.enabled(this)) {
                GuardState.setEnabled(this, true);
                guardSwitch.setChecked(true);
            } else startGuard(GuardService.ACTION_START);
            log("Inicio solicitado. Comprobar la notificacion persistente.");
            ui.postDelayed(this::refresh, 1200L);
        });

        Button burst = button("FORZAR RAFAGA AHORA");
        margin(root, burst, 6);
        burst.setOnClickListener(v -> {
            if (GuardService.running) {
                startGuard(GuardService.ACTION_POKE);
                log("Rafaga solicitada al servicio vigilante.");
            } else {
                log("Servicio no activo: se probara una desactivacion manual.");
                manualDisable();
            }
        });

        forceDisable = button("DESACTIVAR Elliptic (prueba manual)");
        margin(root, forceDisable, 6);
        forceDisable.setOnClickListener(v -> manualDisable());

        stopAndEnable = button("DETENER VIGILANTE Y REACTIVAR Elliptic");
        margin(root, stopAndEnable, 6);
        stopAndEnable.setOnClickListener(v -> {
            if (busy) return;
            guardSwitch.setChecked(false);
            GuardState.setEnabled(this, false);
            stopGuard();
            log("Vigilante detenido. Esperando 1.5s antes de enable...");
            setBusy(true);
            ui.postDelayed(() -> new Thread(() -> {
                String result = SensorControl.command("enable");
                runOnUiThread(() -> {
                    log("ENABLE manual: " + result);
                    log("Verifica en el diagnostico; exit=0 no demuestra el efecto.");
                    setBusy(false);
                    refresh();
                });
            }, "elliptic-enable").start(), 1500L);
        });

        Button refresh = button("ACTUALIZAR ESTADO");
        margin(root, refresh, 6);
        refresh.setOnClickListener(v -> refresh());

        Button read = button("VER REGISTRO DEL VIGILANTE");
        margin(root, read, 6);
        read.setOnClickListener(v -> log("=== LOG PERSISTENTE ===\n" + GuardState.report(this)));

        Button copy = button("COPIAR LOG PERSISTENTE");
        margin(root, copy, 6);
        copy.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cb != null) {
                cb.setPrimaryClip(ClipData.newPlainText("Elliptic Guard", GuardState.report(this)));
                log("Log copiado al portapapeles.");
            }
        });

        TextView note = text("Vigilancia agresiva: listener continuo, rafagas de desbloqueo y "
                + "comprobacion cada 4 s con pantalla encendida. El servicio usa una "
                + "notificacion permanente y puede gastar mas bateria. Sin root, DUMP es "
                + "necesario. El resultado depende de HyperOS; valida con otra app.", 13,
                Color.rgb(102, 82, 40));
        margin(root, note, 14);

        console = text("", 12, Color.rgb(231, 241, 249));
        console.setTypeface(android.graphics.Typeface.MONOSPACE);
        console.setPadding(dp(12), dp(14), dp(12), dp(14));
        console.setBackgroundColor(Color.rgb(20, 31, 44));
        console.setMinHeight(dp(250));
        margin(root, console, 10);
        setContentView(scroll);
    }

    /** Non-invasive easter egg: seven taps on the shield or version. */
    private void secretTap() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastSecretTap > 3500L) secretTapCount = 0;
        lastSecretTap = now;
        secretTapCount++;
        if (secretTapCount < 7) return;
        secretTapCount = 0;
        new AlertDialog.Builder(this)
                .setTitle("DEUS LIBIDINIS  //  DL-03000000")
                .setMessage("PROTOCOLO ELLIPTIC GUARD\n\n"
                        + "El fantasma ultrasónico ha sido identificado.\n"
                        + "Su acceso a la realidad queda bajo vigilancia.\n\n"
                        + "Sin root. Sin Shizuku. Sin rendirse ante un sensor de proximidad.\n\n"
                        + "Investigación y proyecto: Deus Libidinis\n"
                        + "Firma interna: DL / 0x03000000\n\n"
                        + "«Si insiste en despertar, volveremos a apagarlo»."
                )
                .setPositiveButton("Cerrar archivo", (dialog, which) -> { })
                .show();
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        return b;
    }

    private void margin(LinearLayout parent, android.view.View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(topDp), 0, 0);
        parent.addView(child, params);
    }

    private int dp(float dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

    private void log(String message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> log(message));
            return;
        }
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        messages.append('[').append(time).append("] ").append(message).append('\n');
        if (messages.length() > 24000) messages.delete(0, 12000);
        if (console != null) console.setText(messages.toString());
    }

    private void refresh() {
        sensor = SensorControl.findElliptic(manager);
        status.setText("DUMP: " + (SensorControl.hasDump(this) ? "CONCEDIDO" : "DENEGADO")
                + "\nElliptic: " + (sensor == null ? "NO ENCONTRADO" : sensor.getName())
                + "\nVigilante (proceso): " + (GuardService.running ? "ACTIVO" : "DETENIDO")
                + "\nAutoinicio: " + (GuardState.enabled(this) ? "ACTIVADO" : "DESACTIVADO")
                + "\nIntentos disable: " + GuardService.attempts
                + " | eventos: " + GuardService.sensorEvents
                + "\nUltimo comando: " + GuardService.lastResult);
        if (guardSwitch != null && guardSwitch.isChecked() != GuardState.enabled(this)) {
            guardSwitch.setChecked(GuardState.enabled(this));
        }
    }

    private void startGuard(String action) {
        try {
            startForegroundService(new Intent(this, GuardService.class).setAction(action));
        } catch (Exception e) {
            GuardState.log(this, "ERROR lanzando vigilante desde interfaz: " + e);
            log("ERROR inicio: " + e);
        }
    }

    private void stopGuard() {
        stopService(new Intent(this, GuardService.class));
    }

    private void setBusy(boolean active) {
        busy = active;
        if (forceDisable != null) forceDisable.setEnabled(!active);
        if (stopAndEnable != null) stopAndEnable.setEnabled(!active);
    }

    private void manualDisable() {
        if (busy) return;
        if (sensor == null || !SensorControl.hasDump(this)) {
            log("No se puede desactivar: falta Elliptic o permiso DUMP.");
            return;
        }
        setBusy(true);
        temporaryEvents = 0;
        temporaryListener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) { temporaryEvents++; }
            @Override public void onAccuracyChanged(Sensor s, int accuracy) { }
        };
        boolean ok;
        try {
            ok = manager.registerListener(temporaryListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception e) {
            ok = false;
            log("Error registro temporal: " + e);
        }
        if (!ok) {
            log("No se pudo registrar temporalmente Elliptic.");
            setBusy(false);
            return;
        }
        log("Registro temporal activo; esperando 1800ms...");
        ui.postDelayed(() -> new Thread(() -> {
            String result = SensorControl.command("disable");
            runOnUiThread(() -> {
                releaseTemporary();
                log("DISABLE manual: " + result + "; eventos=" + temporaryEvents);
                log("Verifica con otra app. Es un control reversible.");
                setBusy(false);
                refresh();
            });
        }, "elliptic-manual-disable").start(), 1800L);
    }

    private void releaseTemporary() {
        if (temporaryListener != null) {
            manager.unregisterListener(temporaryListener);
            temporaryListener = null;
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) refresh();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        releaseTemporary();
        super.onDestroy();
    }
}
