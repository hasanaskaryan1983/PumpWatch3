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

base = "app/src/main/java/com/pumpwatch/app/"

# 1. FavCenter.kt
patch(base + "ui/FavCenter.kt", [
    ("import com.pumpwatch.app.data.GatewayProviders", 
     "import com.pumpwatch.app.data.GatewayProviders\nimport com.pumpwatch.app.store.SecureStorage"),
    ("ctx.getSharedPreferences(\"pumpwatch_prefs\", 0)",
     "SecureStorage.getEncryptedPrefs(ctx)"),
])

# 2. TraderStore.kt
patch(base + "data/TraderStore.kt", [
    ("import com.google.gson.reflect.TypeToken",
     "import com.google.gson.reflect.TypeToken\nimport com.pumpwatch.app.store.SecureStorage"),
    ("ctx.getSharedPreferences(PREF, 0)",
     "SecureStorage.getEncryptedPrefs(ctx)"),
])

# 3. FollowedWhalesStore.kt
old_fw = """    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<FollowedWhale> {
        return try {
            val json = file(context).readText()
            val type = object : TypeToken<List<FollowedWhale>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, whales: List<FollowedWhale>) {
        try {
            file(context).writeText(gson.toJson(whales))
        } catch (_: Exception) {
            // خطای نوشتن نباید اپ را کرش کند
        }
    }"""

new_fw = """    private const val KEY = "followed_whales_encrypted"

    fun load(context: Context): List<FollowedWhale> {
        return try {
            val json = SecureStorage.getString(context, KEY)
            if (json.isNullOrEmpty()) {
                // Migration from old plaintext file
                val oldFile = java.io.File(context.filesDir, FILE_NAME)
                if (oldFile.exists()) {
                    val oldJson = oldFile.readText()
                    SecureStorage.putString(context, KEY, oldJson)
                    oldFile.delete()
                    val type = object : TypeToken<List<FollowedWhale>>() {}.type
                    return gson.fromJson(oldJson, type) ?: emptyList()
                }
                return emptyList()
            }
            val type = object : TypeToken<List<FollowedWhale>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, whales: List<FollowedWhale>) {
        try {
            SecureStorage.putString(context, KEY, gson.toJson(whales))
        } catch (_: Exception) {
            // خطای نوشتن نباید اپ را کرش کند
        }
    }"""

patch(base + "data/FollowedWhalesStore.kt", [
    ("import java.io.File", "import java.io.File\nimport com.pumpwatch.app.store.SecureStorage"),
    (old_fw, new_fw)
])

print("ALL PATCHED")
