package dev.deus.ellipticprobe;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Immediate start during the system broadcast exemption; no delayed FGS retry. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        boolean enabled = GuardState.enabled(c);
        GuardState.log(c, "RECEPTOR " + action + ": recibido; version="
                + GuardState.version(c) + "; autoinicio=" + enabled
                + "; DUMP=" + SensorControl.hasDump(c));
        if (!enabled) {
            GuardState.log(c, "RECEPTOR: inicio omitido por preferencia del usuario.");
            return;
        }
        try {
            Intent service = new Intent(c, GuardService.class)
                    .setAction(GuardService.ACTION_START)
                    .putExtra(GuardService.EXTRA_START_SOURCE, action);
            ComponentName component = c.startForegroundService(service);
            GuardState.log(c, component == null
                    ? "RECEPTOR: solicitud sin componente de servicio."
                    : "RECEPTOR: solicitud enviada; pendiente de confirmar FGS y listener.");
        } catch (Exception e) {
            GuardState.log(c, "ERROR RECEPTOR " + action + ": " + e);
        }
    }
}
