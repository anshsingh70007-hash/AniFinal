package com.example.aniflow.utils

object UpdateConfig {
    /**
     * Where installs look for updates: the same signed `update.json` on every listed host. All are
     * asked and the newest validly signed answer wins, so a stale or hostile host can't do harm.
     * Must match `tools/update_channel.json`, which refuses to publish an APK that doesn't carry these.
     * Add mirrors freely; remove one only after every install in the field has a build that knows
     * the others.
     *
     * GitHub Releases: `latest/download/<file>` always points at the newest release, and the APK
     * (`apk.file` in the manifest) resolves next to it, so each release carries update.json + APK.
     * This is currently the only host: if the repo is ever taken down, installs can't hear of
     * updates until a build with another mirror is installed by hand. Add a second host early.
     */
    val MANIFEST_MIRRORS: List<String> = listOf(
        "https://github.com/anshsingh70007-hash/AniFinal/releases/latest/download/update.json",
    )

    /**
     * Public halves of the update-signing keys (ECDSA P-256, SubjectPublicKeyInfo, base64). k1 signs
     * every release; k2 is the offline spare in case k1 is lost or leaked. Private keys live in
     * keystore/update-keys/ (never committed). Never remove one without shipping its replacement
     * first, or installs that only know the old key can't update again.
     */
    val MANIFEST_KEYS: Map<String, String> = mapOf(
        "k1" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEp9OWZgKuUfd0ezA7eoEa09u2lHvt5Z24fUltxoVkXDPh/BW7jGDI/evl4PTMugCvrNvNXm7QdTR5efeBhwFZ2g==",
        "k2" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEmN2e8YA6+8sDJ0OF08kDktPD9YzVVK//ySnScQgaKN5Hfwzefd4D0lbjblifevURsDkYPL2evWqUVpdZo49GxQ==",
    )
}
