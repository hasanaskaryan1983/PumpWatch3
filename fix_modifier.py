import io

path = "app/src/main/java/com/pumpwatch/app/ui/EditorialComponents.kt"
with io.open(path, encoding="utf-8") as f:
    src = f.read()

old_text = """        modifier = Modifier
            Modifier.width(72.dp)
            .height(48.dp)"""

new_text = """        modifier = Modifier
            .width(72.dp)
            .height(48.dp)"""

if old_text in src:
    src = src.replace(old_text, new_text)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("✅ خطای سینتکس Modifier با موفقیت اصلاح شد")
else:
    print("⚠️ الگو پیدا نشد. لطفاً دوباره بررسی کنید.")
