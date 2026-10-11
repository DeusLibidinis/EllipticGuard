package dev.deus.ellipticprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground sensor guard. Aggressive, bounded strategy:
 *  - one continuously registered proximity sensor (no rotating registrations)
 *  - react to Elliptic events and to SCREEN_ON / USER_PRESENT
 *  - repeated burst after unlocking plus watchdog every 4s while screen is on
 *  - watchdog every 30s while screen is off (may be delayed by doze)
 *  - no more than one dumpsys concurrently; minimum 1000ms between attempts
 *  - no UID, root, Shizuku, networking, or accessibility.
 *
 * Important: Xiaomi's disable is not a permanent HAL deny-list. The watchdog
 * applies it repeatedly and correctness must be checked on this ROM.
 */
public final class GuardService extends Service {
    public static final String ACTION_START = "dev.deus.ellipticprobe.START";
    public static final String ACTION_STOP = "dev.deus.ellipticprobe.STOP";
    public static final String ACTION_POKE = "dev.deus.ellipticprobe.POKE";
    private static final String CHANNEL = "elliptic_guard_channel_v05";
    private static final int NOTIFICATION_ID = 3305;
    private static final long AWAKE_PERIOD_MS = 4000L;
    private static final long ASLEEP_PERIOD_MS = 30000L;
    private static final long MIN_INTERVAL_MS = 1000L;
    private static final long[] UNLOCK_BURST = {0L, 900L, 2600L, 6200L, 12500L};

    public static volatile boolean running = false;
    public static volatile int attempts = 0;
    public static volatile int sensorEvents = 0;
    public static volatile String lastResult = "Sin comandos";

    private HandlerThread sensorThread;
    private Handler monitor;
    private ExecutorService worker;
    private SensorManager sensorManager;
    private Sensor proximity;
    private SensorEventListener listener;
    private BroadcastReceiver displayReceiver;
    private PowerManager powerManager;
    private boolean receiverRegistered = false;
    private boolean sensorRegistered = false;
    private volatile boolean interactive = true;
    private boolean stopping = false;
    private long lastAttemptAt = 0L;
    private long lastEventLogAt = 0L;
    private long lastCommandLogAt = 0L;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (stopping || !running) return;
            requestDisable("WATCHDOG_" + (interactive ? "ON" : "OFF"));
            monitor.postDelayed(this, interactive ? AWAKE_PERIOD_MS : ASLEEP_PERIOD_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        interactive = powerManager == null || powerManager.isInteractive();
        sensorThread = new HandlerThread("elliptic-guard-events");
        sensorThread.start();
        monitor = new Handler(sensorThread.getLooper());
        worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "elliptic-guard-dumpsys"));
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action) || !GuardState.enabled(this)) {
            if (ACTION_STOP.equals(action)) GuardState.setEnabled(this, false);
            GuardState.log(this, "VIGILANTE DETENIDO por solicitud del usuario.");
            stopping = true;
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIFICATION_ID, notification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Exception e) {
            GuardState.log(this, "ERROR FGS: " + e + " (posible bloqueo HyperOS/Android).");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!SensorControl.hasDump(this)) {
            GuardState.log(this, "ERROR: no se concedio DUMP; el vigilante se detiene.");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) startMonitoring();
        else if (ACTION_POKE.equals(action)) {
            monitor.post(() -> launchBurst("PETICION_MANUAL"));
        }
        return START_STICKY;
    }

    private void startMonitoring() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        proximity = SensorControl.findElliptic(sensorManager);
        if (proximity == null) {
            GuardState.log(this, "ERROR: Elliptic Proximity no encontrado. Vigilante detenido.");
            stopSelf();
            return;
        }
        sensorEvents = 0;
        attempts = 0;
        lastAttemptAt = 0L;
        stopping = false;
        running = true;
        GuardState.log(this, "VIGILANTE INICIADO. UID=" + android.os.Process.myUid()
                + "; handle=0x" + SensorControl.HANDLE
                + "; pantalla=" + (interactive ? "ON" : "OFF")
                + "; sensor=" + proximity.getName());

        listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {
                sensorEvents++;
                long now = SystemClock.elapsedRealtime();
                if (now - lastEventLogAt >= 30000L) {
                    lastEventLogAt = now;
                    float value = event.values.length > 0 ? event.values[0] : Float.NaN;
                    GuardState.log(GuardService.this, String.format(Locale.ROOT,
                            "EVENTO Elliptic #%d: %.4f cm; solicitar disable", sensorEvents, value));
                }
                requestDisable("EVENTO_SENSOR");
            }
            @Override public void onAccuracyChanged(Sensor s, int accuracy) { }
        };
        try {
            sensorRegistered = sensorManager.registerListener(listener, proximity,
                    SensorManager.SENSOR_DELAY_NORMAL, monitor);
        } catch (Exception e) {
            sensorRegistered = false;
            GuardState.log(this, "ERROR registrando Elliptic: " + e);
        }
        if (!sensorRegistered) {
            GuardState.log(this, "ERROR: registerListener devolvio false. Cerrando vigilante.");
            stopSelf();
            return;
        }
        GuardState.log(this, "Listener Elliptic registrado y PERMANENTE mientras este activo el vigilante.");

        displayReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    interactive = false;
                    GuardState.log(GuardService.this, "PANTALLA OFF: conservando listener.");
                    monitor.removeCallbacks(watchdog);
                    monitor.postDelayed(watchdog, ASLEEP_PERIOD_MS);
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    interactive = true;
                    GuardState.log(GuardService.this, "SCREEN_ON: rafaga de desactivacion.");
                    monitor.post(() -> launchBurst("SCREEN_ON"));
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    interactive = true;
                    GuardState.log(GuardService.this, "USER_PRESENT: rafaga de desactivacion.");
                    monitor.post(() -> launchBurst("USER_PRESENT"));
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            registerReceiver(displayReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        } catch (Exception e) {
            GuardState.log(this, "ERROR registrando eventos de pantalla: " + e);
        }

        // First event may arrive earlier, but after 1.8s we also enforce the proven sequence.
        monitor.postDelayed(() -> requestDisable("INICIO_1800MS"), 1800L);
        monitor.postDelayed(watchdog, AWAKE_PERIOD_MS);
    }

    private void launchBurst(String reason) {
        if (stopping || !running) return;
        monitor.removeCallbacks(watchdog);
        for (long delay : UNLOCK_BURST) {
            monitor.postDelayed(() -> requestDisable(reason + "_" + delay + "ms"), delay);
        }
        // Resume periodic checks regardless of whether a burst command was rejected/deduplicated.
        monitor.postDelayed(watchdog, AWAKE_PERIOD_MS);
    }

    private void requestDisable(String reason) {
        if (stopping || !running || !GuardState.enabled(this)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastAttemptAt < MIN_INTERVAL_MS) return;
        if (!inFlight.compareAndSet(false, true)) return;
        lastAttemptAt = now;
        attempts++;
        final int sequence = attempts;
        try {
            worker.execute(() -> {
                PowerManager.WakeLock wake = null;
                try {
                    if (powerManager != null) {
                        wake = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                "EllipticGuard:Control");
                        wake.acquire(8500L);
                    }
                    String result = SensorControl.command("disable");
                    lastResult = result;
                    long loggedAt = SystemClock.elapsedRealtime();
                    if (sequence <= 4 || loggedAt - lastCommandLogAt >= 45000L
                            || !result.startsWith("exit=0")
                            || reason.startsWith("SCREEN_ON")
                            || reason.startsWith("USER_PRESENT")
                            || reason.startsWith("PETICION_MANUAL")) {
                        lastCommandLogAt = loggedAt;
                        GuardState.log(GuardService.this, "DISABLE #" + sequence
                                + " (" + reason + "): " + result
                                + "; eventos=" + sensorEvents);
                    }
                } catch (Throwable e) {
                    lastResult = "ERROR " + e;
                    GuardState.log(GuardService.this, "FALLO vigilante: " + e);
                } finally {
                    if (wake != null && wake.isHeld()) wake.release();
                    inFlight.set(false);
                }
            });
        } catch (Exception e) {
            inFlight.set(false);
            GuardState.log(this, "Error enviando trabajo al ejecutor: " + e);
        }
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Elliptic Guard",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Vigilancia de reactivaciones del sensor Elliptic Proximity");
        manager.createNotificationChannel(channel);
    }

    private int notificationIcon() {
        int id = getResources().getIdentifier("ic_elliptic_status", "drawable", getPackageName());
        return id != 0 ? id : android.R.drawable.ic_dialog_info;
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, GuardService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(notificationIcon())
                .setContentTitle("Elliptic Guard activo")
                .setContentText("Vigilando y desactivando la proximidad virtual")
                .setOngoing(true)
                .setContentIntent(content)
                .addAction(new Notification.Action.Builder(null, "Detener", stopIntent).build())
                .build();
    }

    @Override public void onDestroy() {
        stopping = true;
        running = false;
        if (monitor != null) monitor.removeCallbacksAndMessages(null);
        if (sensorManager != null && listener != null) {
            try { sensorManager.unregisterListener(listener); }
            catch (Exception e) { GuardState.log(this, "Error liberando listener: " + e); }
        }
        if (receiverRegistered) {
            try { unregisterReceiver(displayReceiver); } catch (Exception ignored) { }
        }
        if (sensorThread != null) sensorThread.quitSafely();
        if (worker != null) worker.shutdownNow();
        GuardState.log(this, "VIGILANTE FINALIZADO. Intentos=" + attempts
                + "; eventos observados=" + sensorEvents);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
