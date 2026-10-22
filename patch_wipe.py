import sys, io

def patch(path, pairs):
    with io.open(path, encoding="utf-8") as f:
        src = f.read()
    for old, new in pairs:
        if old not in src:
            print("WARN %s : pattern not found" % path)
            continue
        src = src.replace(old, new)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("OK " + path)

# 1. اضافه کردن wipeAll به SecureStorage.kt
patch("app/src/main/java/com/pumpwatch/app/store/SecureStorage.kt", [
    ("fun clear(context: Context) {\n        getEncryptedPrefs(context).edit().clear().apply()\n    }\n}",
     "fun clear(context: Context) {\n        getEncryptedPrefs(context).edit().clear().apply()\n    }\n\n    /**\n     * پاک‌سازی کامل (مورد استفاده در Privacy Center)\n     */\n    fun wipeAll(context: Context) {\n        clear(context)\n    }\n}")
])

# 2. بهبود پاک‌سازی در PrivacyCenterScreen (پاک‌سازی کامل prefs معمولی)
patch("app/src/main/java/com/pumpwatch/app/ui/PrivacyCenterScreen.kt", [
    ("prefs.edit().remove(\"paper_state\").apply()\n                            prefs.edit().remove(\"paper_alloc\").apply()",
     "prefs.edit().clear().apply() // پاک‌سازی کامل همهٔ prefs معمولی برای اطمینان")
])

print("ALL PATCHED")
