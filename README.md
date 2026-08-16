# GadgetLoader

Hosted-only Android instrumentation loader for authorized lab/CTF targets.

## v0.1 scope

- Android arm64-v8a
- Hosted Unity targets (IL2CPP or Mono)
- Frida Gadget 17.17.0 in autonomous Script mode
- Mutable `main.js` stored in GadgetLoader external app files
- No root, ptrace, ROM changes, cross-UID injection, or target APK modification

The target code runs inside GadgetLoader's host process, so Binder/UID/signature remain GadgetLoader's identity.
