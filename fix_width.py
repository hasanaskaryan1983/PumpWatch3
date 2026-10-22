import io

path = "app/src/main/java/com/pumpwatch/app/ui/EditorialComponents.kt"
with io.open(path, encoding="utf-8") as f:
    src = f.read()

# اصلاح دقیق: اطمینان از وجود Modifier قبل از width
if ".width(72.dp)" in src and "Modifier.width(72.dp)" not in src:
    src = src.replace(".width(72.dp)", "Modifier.width(72.dp)")
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("✅ خطای width اصلاح شد (Modifier.width جایگزین شد)")
else:
    print("⚠️ الگو پیدا نشد. لطفاً خروجی دستور sed زیر را بفرستید:")
    print("sed -n '190,210p' app/src/main/java/com/pumpwatch/app/ui/EditorialComponents.kt")
