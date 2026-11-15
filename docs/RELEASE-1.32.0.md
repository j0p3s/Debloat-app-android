# Debloater 1.32.0

- **Install button for app updates:** when an update has finished downloading (Settings → App updates), an **Install** button now opens Android's installer. If Android has not yet allowed Debloater to install apps, the button opens that setting first; allow it, return and tap Install again. The button disappears once the downloaded version is installed.
- New permission `REQUEST_INSTALL_PACKAGES`, used only to hand the downloaded update to Android's installer. You still confirm the installation, and Android only accepts an update signed with the same key.
- Translated into all 11 supported languages.

Includes 1.31.0: support screen once per update with a "Don't show this screen again" checkbox, and Buy me a coffee buttons on Home.

Android 11+, versionCode 37, permanent signing identity unchanged. The install flow was not yet tried on a device.
