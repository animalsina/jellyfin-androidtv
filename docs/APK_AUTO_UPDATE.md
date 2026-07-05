# Auto aggiornamento APK

Questa build include un updater leggero per APK sideload/debug.

## Cartella remota predefinita

```text
https://files.animalsina.work/jellyfin/android-tv/
```

La cartella deve esporre un listing HTTP/HTML contenente APK con nome:

```text
superjelly-androidtv-vX.Y.Z-debug.apk
# compatibilità: accetta ancora anche jellyfin-androidtv-vX.Y.Z-debug.apk
```

Esempio:

```text
superjelly-androidtv-v1.0.13-debug.apk
```

## Comportamento

All’avvio della `MainActivity`, e manualmente dal tasto nelle Impostazioni, l’app:

1. legge il listing della cartella remota;
2. estrae le versioni dai file APK compatibili;
3. confronta la versione più alta trovata con `BuildConfig.VERSION_NAME`;
4. se la versione remota è più recente, mostra una modale;
5. se l’utente accetta, scarica l’APK;
6. se necessario, apre le impostazioni Android per abilitare l’installazione da questa app;
7. apre l’installer Android standard.

L’installazione non è silenziosa: Android chiederà sempre conferma all’utente.

## Requisiti Android

Su Android 8+ serve consentire l’installazione di APK da questa applicazione. Il client apre la schermata corretta tramite `ACTION_MANAGE_UNKNOWN_APP_SOURCES`.

## Firma e package name

L’APK remoto deve avere lo stesso `applicationId` e deve essere firmato con una chiave compatibile con l’app installata, altrimenti Android non permetterà la sostituzione.

Per build debug, il package include il suffisso `.debug`, quindi anche l’APK remoto deve essere una build debug compatibile.

## Sicurezza

L’updater usa solo il folder predefinito e apre l’installer di sistema. Non installa in background e non gestisce credenziali.
