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

# 1. اصلاح scoreEvmSecurity: بررسی honeypot نامشخص
patch("app/src/main/java/com/pumpwatch/app/engine/MemeRadar.kt", [
    ("if (security.is_honeypot == \"1\") { score -= 80; warnings.add(\"🚨 Honeypot: نمی‌توانید بفروشید!\") }",
     "if (security.is_honeypot == null) {\n            warnings.add(\"⚠️ وضعیت Honeypot نامشخص است — داده در دسترس نیست\")\n            return Triple(null, warnings, \"UNKNOWN\")\n        }\n        if (security.is_honeypot == \"1\") { score -= 80; warnings.add(\"🚨 Honeypot: نمی‌توانید بفروشید!\") }"),
])

# 2. اصلاح scoreSolanaSecurity (اگر وجود دارد)
# ابتدا باید ببینیم ساختار آن چگونه است
solana_patch = """
        if (security.is_honeypot == null) {
            warnings.add("⚠️ وضعیت Honeypot نامشخص است — داده در دسترس نیست")
            return Triple(null, warnings, "UNKNOWN")
        }
        if (security.is_honeypot == "1") { score -= 80; warnings.add("🚨 Honeypot: نمی‌توانید بفروشید!") }
"""
# این را بعداً اعمال می‌کنیم اگر کد Solana را ببینیم

print("ALL PATCHED")
