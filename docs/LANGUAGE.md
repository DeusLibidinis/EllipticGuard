# Language selection / Selección de idioma

Elliptic Guard `0.6.1-beta.2` adds a language control to the main screen:

- **System / Sistema**: use the Android-selected language.
- **Español**: Spanish app interface.
- **English**: English app interface.

The app stores this preference only in its local `elliptic_ui` SharedPreferences file and recreates the activity when changed. The foreground service and watchdog **do not stop or restart** when the interface changes language. The notification text is provided by Android string resources; Android may show an already-posted notification in its previous language until the service recreates its notification.

The localization is intentionally restricted to user-facing UI strings. Internal persistent logs and Xiaomi-specific command names are left unchanged so diagnostic evidence stays comparable across language settings.

**Testing still required on a real POCO X6 Pro**:
1. Change System → Español → English and check the interface.
2. Verify enabling/disabling the watchdog and the persistent notification.
3. Check language after force-close/reopen and device reboot.
4. Verify update recovery after installing a same-key-signed APK *without opening the app*.

Do not distribute a public signed APK until the new language resources have passed a full Android SDK build and the remaining device validation.