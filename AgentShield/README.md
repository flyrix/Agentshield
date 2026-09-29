# Agent Shield — Phase 1 (MVP)
Surveillance des notifications Wave (règles), alerte sonore plein volume, journal chiffré (Room + SQLCipher).

## Lancer
1. Ouvrir le dossier dans Android Studio (Koala+), laisser Gradle synchroniser (le wrapper est généré par l'IDE).
2. Installer sur un téléphone (Android 8+).
3. Autoriser les notifications, puis « Activer l'accès aux notifications » et cocher Agent Shield.
   Android 13+ (APK hors Play Store) : Infos de l'app > ⋮ > « Autoriser les paramètres restreints » d'abord.
4. Vérifier le package Wave : `adb shell pm list packages | grep -i wave`, puis corriger `Config.WAVE_PACKAGES`.
5. Ajuster les regex de `WaveRuleEngine` avec de vraies notifications Wave.

## Prochaine phase
Gmail -> classification LLM -> lecture TTS (le point d'entrée est le commentaire dans AgentNotificationService).
