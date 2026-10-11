package dev.deus.ellipticprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts monitoring on boot/package update; failures are logged instead of hidden. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (intent == null || !GuardState.enabled(c)) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        try {
            GuardState.log(c, "RECEPTOR " + action + ": iniciando servicio vigilante.");
            Intent service = new Intent(c, GuardService.class).setAction(GuardService.ACTION_START);
            c.startForegroundService(service);
        } catch (Exception e) {
            GuardState.log(c, "ERROR iniciando vigilante al arrancar: " + e);
        }
    }
}
