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

# 1. تغییر رنگ و ایموجی rug وقتی null است (به جای Muted، Warning شود)
patch("app/src/main/java/com/pumpwatch/app/ui/MemeRadarScreen.kt", [
    ("val rugColor = when { rug == null -> PumpWatchPalette.Muted; rug >= 80 -> PumpWatchPalette.Positive; rug >= 60 -> PumpWatchPalette.Warning; else -> PumpWatchPalette.Negative }",
     "val rugColor = when { rug == null -> PumpWatchPalette.Warning; rug >= 80 -> PumpWatchPalette.Positive; rug >= 60 -> PumpWatchPalette.Warning; else -> PumpWatchPalette.Negative }"),
    ("val rugEmoji = when { rug == null -> \"❓\"; rug >= 80 -> \"✅\"; rug >= 60 -> \"⚠️\"; else -> \"🚨\" }",
     "val rugEmoji = when { rug == null -> \"⚠️\"; rug >= 80 -> \"✅\"; rug >= 60 -> \"⚠️\"; else -> \"🚨\" }"),
])

# 2. بهبود متن هشدار و رنگ آن به Warning (زرد/نارنجی)
patch("app/src/main/java/com/pumpwatch/app/ui/MemeRadarScreen.kt", [
    ("\"EMPTY\" -> \"❓ GoPlus این توکن را شناسایی نکرد — ایمن فرض نمی‌شود.\"\n                            \"FAILED\" -> \"❓ بررسی امنیتی انجام نشد — ایمن فرض نمی‌شود.\"\n                            else -> \"❓ وضعیت امنیتی نامشخص — ایمن فرض نمی‌شود.\"",
     "\"UNKNOWN\" -> \"⚠️ وضعیت امنیتی نامشخص است — داده در دسترس نیست، با احتیاط معامله کنید.\"\n                            \"EMPTY\" -> \"❓ GoPlus این توکن را شناسایی نکرد — ایمن فرض نمی‌شود.\"\n                            \"FAILED\" -> \"❓ بررسی امنیتی انجام نشد — ایمن فرض نمی‌شود.\"\n                            else -> \"❓ وضعیت امنیتی نامشخص — ایمن فرض نمی‌شود.\""),
    ("Text(warningText, fontSize = 9.sp, color = PumpWatchPalette.Muted, fontWeight = FontWeight.Bold)",
     "Text(warningText, fontSize = 9.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)"),
])

print("ALL PATCHED")
