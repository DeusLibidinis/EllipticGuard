package dev.deus.ellipticprobe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
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

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Language.wrap(base));
    }

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
        shield.setContentDescription(getString(R.string.icon_description));
        root.addView(shield, shieldLp);
        shield.setOnClickListener(v -> secretTap());

        TextView title = text(getString(R.string.app_name).toUpperCase(Locale.ROOT), 26, Color.rgb(20, 33, 49));
        title.setTypeface(null, 1);
        root.addView(title);
        TextView version = text(getString(R.string.version_line, GuardState.version(this)), 13,
                Color.rgb(91, 106, 121));
        root.addView(version);
        version.setOnClickListener(v -> secretTap());
        TextView credit = text("DEUS LIBIDINIS  /  SENSOR RESEARCH", 11,
                Color.rgb(25, 115, 133));
        credit.setLetterSpacing(0.06f);
        margin(root, credit, 5);

        TextView languageTitle = text(getString(R.string.language_label), 13, Color.rgb(35, 50, 62));
        margin(root, languageTitle, 12);
        Spinner language = new Spinner(this);
        String[] languages = {getString(R.string.language_system), getString(R.string.language_es), getString(R.string.language_en)};
        ArrayAdapter<String> languageAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, languages);
        language.setAdapter(languageAdapter);
        String selectedLanguage = Language.get(this);
        language.setSelection("es".equals(selectedLanguage) ? 1 : "en".equals(selectedLanguage) ? 2 : 0);
        language.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
                    int position, long id) {
                String selected = position == 1 ? "es" : position == 2 ? "en" : Language.SYSTEM;
                if (first) { first = false; return; }
                if (!selected.equals(Language.get(MainActivity.this))) {
                    Language.set(MainActivity.this, selected);
                    recreate();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        root.addView(language);

        Button guide = button(getString(R.string.guide_button));
        margin(root, guide, 6);
        guide.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(getString(R.string.guide_title))
                .setMessage(getString(R.string.guide_text))
                .setPositiveButton(android.R.string.ok, null).show());

        status = text("", 14, Color.rgb(35, 50, 62));
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        status.setBackgroundColor(Color.rgb(227, 235, 246));
        margin(root, status, 12);

        guardSwitch = new Switch(this);
        guardSwitch.setText(getString(R.string.guard_switch));
        guardSwitch.setTextSize(16);
        guardSwitch.setChecked(GuardState.enabled(this));
        margin(root, guardSwitch, 12);
        guardSwitch.setOnCheckedChangeListener((v, checked) -> {
            GuardState.setEnabled(this, checked);
            log(checked ? getString(R.string.log_enable_guard) : getString(R.string.log_disable_guard));
            if (checked) startGuard(GuardService.ACTION_START);
            else stopGuard();
            ui.postDelayed(this::refresh, 1200L);
        });

        Button start = button(getString(R.string.start_now));
        margin(root, start, 6);
        start.setOnClickListener(v -> {
            if (!GuardState.enabled(this)) {
                GuardState.setEnabled(this, true);
                guardSwitch.setChecked(true);
            } else startGuard(GuardService.ACTION_START);
            log(getString(R.string.log_start_requested));
            ui.postDelayed(this::refresh, 1200L);
        });

        Button burst = button(getString(R.string.force_burst));
        margin(root, burst, 6);
        burst.setOnClickListener(v -> {
            if (GuardService.running) {
                startGuard(GuardService.ACTION_POKE);
                log(getString(R.string.log_burst_requested));
            } else {
                log(getString(R.string.log_fallback));
                manualDisable();
            }
        });

        forceDisable = button(getString(R.string.manual_disable));
        margin(root, forceDisable, 6);
        forceDisable.setOnClickListener(v -> manualDisable());

        stopAndEnable = button(getString(R.string.stop_enable));
        margin(root, stopAndEnable, 6);
        stopAndEnable.setOnClickListener(v -> {
            if (busy) return;
            guardSwitch.setChecked(false);
            GuardState.setEnabled(this, false);
            stopGuard();
            log(getString(R.string.log_stop_wait));
            setBusy(true);
            ui.postDelayed(() -> new Thread(() -> {
                String result = SensorControl.command("enable");
                runOnUiThread(() -> {
                    log(getString(R.string.log_enable_result, result));
                    log(getString(R.string.log_verify));
                    setBusy(false);
                    refresh();
                });
            }, "elliptic-enable").start(), 1500L);
        });

        Button refresh = button(getString(R.string.refresh));
        margin(root, refresh, 6);
        refresh.setOnClickListener(v -> refresh());

        Button read = button(getString(R.string.show_log));
        margin(root, read, 6);
        read.setOnClickListener(v -> log(getString(R.string.log_header, GuardState.report(this))));

        Button copy = button(getString(R.string.copy_log));
        margin(root, copy, 6);
        copy.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cb != null) {
                cb.setPrimaryClip(ClipData.newPlainText("Elliptic Guard", GuardState.report(this)));
                log(getString(R.string.log_copied));
            }
        });

        TextView note = text(getString(R.string.battery_note), 13,
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
                .setTitle(getString(R.string.easter_title))
                .setMessage(getString(R.string.easter_message))
                .setPositiveButton(getString(R.string.close_easter), (dialog, which) -> { })
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
        status.setText(getString(R.string.status_line,
                getString(SensorControl.hasDump(this) ? R.string.permission_granted : R.string.permission_denied),
                sensor == null ? getString(R.string.found_no) : sensor.getName(),
                getString(GuardService.running ? R.string.state_running : R.string.state_stopped),
                getString(GuardState.enabled(this) ? R.string.state_enabled : R.string.state_disabled),
                GuardService.attempts, GuardService.sensorEvents, GuardService.lastResult));
        if (guardSwitch != null && guardSwitch.isChecked() != GuardState.enabled(this)) {
            guardSwitch.setChecked(GuardState.enabled(this));
        }
    }

    private void startGuard(String action) {
        try {
            startForegroundService(new Intent(this, GuardService.class).setAction(action));
        } catch (Exception e) {
            GuardState.log(this, "ERROR lanzando vigilante desde interfaz: " + e);
            log(getString(R.string.log_start_error, e.toString()));
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
            log(getString(R.string.log_disable_unavailable));
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
            log(getString(R.string.log_register_error, e.toString()));
        }
        if (!ok) {
            log(getString(R.string.log_register_failed));
            setBusy(false);
            return;
        }
        log(getString(R.string.log_register_wait));
        ui.postDelayed(() -> new Thread(() -> {
            String result = SensorControl.command("disable");
            runOnUiThread(() -> {
                releaseTemporary();
                log(getString(R.string.log_disable_result, result, temporaryEvents));
                log(getString(R.string.log_check_external));
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
