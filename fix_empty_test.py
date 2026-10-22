import io

path = "app/src/test/java/com/pumpwatch/app/engine/SignalLoggerTest.kt"
with io.open(path, encoding="utf-8") as f:
    src = f.read()

# اصلاح دقیق تست لیست خالی
old_text = """val s = SignalLogger.accuracyStats(emptyList())
        assertEquals(0, s.total)
        assertEquals(0, s.wins)
        assertEquals(0, s.losses)
        assertEquals(0, s.expired)
        assertEquals(0, s.open)
        assertEquals(1, s.totalDecided)"""

new_text = """val s = SignalLogger.accuracyStats(emptyList())
        assertEquals(0, s.total)
        assertEquals(0, s.wins)
        assertEquals(0, s.losses)
        assertEquals(0, s.expired)
        assertEquals(0, s.open)
        assertEquals(0, s.totalDecided)"""

if old_text in src:
    src = src.replace(old_text, new_text)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("✅ OK: تست لیست خالی اصلاح شد")
else:
    print("⚠️ WARN: الگو پیدا نشد. لطفاً خروجی sed را دوباره چک کنید.")
