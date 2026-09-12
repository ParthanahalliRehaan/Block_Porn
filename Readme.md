# Block

Block is a personal, on-device Android app that helps you keep a DNS-level
content filter in place by making it deliberately hard to switch off or
uninstall on impulse. It's a "commitment device" in the same spirit as apps
like Freedom or Cold Turkey Blocker: you set it up while motivated, and it
resists being casually undone later.

## What it does

- Monitors and re-applies your chosen DNS/content-filtering configuration
  in the background (`DnsWatcherService`, `BootReceiver`), so it survives
  reboots.
- Uses Android **Device Administrator** privileges so the app can't be
  uninstalled through the normal Settings flow while active.
- Uses an **Accessibility Service** (`TamperGuardAccessibilityService`) to
  detect and interrupt attempts to reach the Settings screens that would
  disable Device Admin or the app itself.
- Requires removal to go through an in-app flow: enter your PIN, then wait
  out a fixed delay (currently one hour) before the app will let you
  deactivate Device Admin and uninstall.

## What it does NOT do

- It does not make network requests of its own. There is no backend server,
  no analytics SDK, and no networking library anywhere in the codebase —
  everything (your PIN, timers, settings) is stored locally on-device in
  private app storage.
- It does not access contacts, SMS, call logs, location, camera, or the
  microphone. It doesn't request any of those permissions.
- It does not read or transmit the content of what you view; the
  Accessibility Service only watches for specific system Settings screens
  related to disabling this app.

## Permissions used, and why

| Permission | Why it's needed |
|---|---|
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps DNS protection monitoring running reliably in the background. |
| `POST_NOTIFICATIONS` | Shows the persistent status/health notification. |
| `SYSTEM_ALERT_WINDOW` | Shows blocking/warning overlays. |
| `RECEIVE_BOOT_COMPLETED` | Restarts protection after the device reboots. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevents the OS from killing the background service to save battery. |
| Device Administrator | Prevents uninstallation outside the sanctioned in-app removal flow. |
| Accessibility Service | Detects attempts to reach Settings screens that would disable Device Admin or uninstall the app, outside the sanctioned removal flow. |

## Important: who this is for

**This app is meant to be installed by you, on your own device, as a tool
you're voluntarily using against your own future impulses.**

It is *not* meant to be installed on someone else's phone without them
knowing exactly what it does and agreeing to it. An app that resists being
disabled looks identical from a legal and ethical standpoint whether it's a
self-control tool or covert monitoring software — the only thing that tells
them apart is consent. Installing this on another adult's device without
their informed agreement, or using it to covertly monitor/restrict a
partner, can run afoul of stalkerware and computer-misuse laws in many
places. If you're setting this up for a family member (e.g., a teen's
device you manage), do it openly, with their knowledge.

## Limitations

- DNS-based filtering can be bypassed by changing network settings, using a
  VPN, using apps with their own encrypted DNS, or a factory reset. This
  app raises the friction of turning protection off; it does not make
  bypassing physically impossible.
- This is a self-control aid, not a treatment. If you're dealing with
  compulsive behavior you want more support with, consider talking to a
  licensed therapist or counselor in addition to using tools like this.

## Removing the app

1. Open Block and start the removal flow.
2. Enter your PIN.
3. Wait out the cooldown period.
4. Confirm — the app will deactivate Device Admin and let you uninstall
   normally.

There is intentionally no faster path than this from within the app.

## License

MIT License, plus additional terms covering self-install/consent, no
guarantee of effectiveness, and liability limits specific to the Device
Admin / Accessibility features. See [LICENSE](./LICENSE) for the full text.

## Disclaimer

This project is provided as-is by an independent developer. It is not
affiliated with, reviewed by, or endorsed by Google or any DNS provider.
This README and LICENSE are not a substitute for legal advice; if you plan
to distribute this app publicly, have someone review your specific
listing and disclosures.