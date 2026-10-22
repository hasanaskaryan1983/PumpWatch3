import io

def patch(path, pairs):
    with io.open(path, encoding="utf-8") as f:
        src = f.read()
    for old, new in pairs:
        if old not in src:
            print(f"WARN {path}: pattern not found")
            continue
        src = src.replace(old, new)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print(f"OK {path}")

# 1. اصلاح تب‌های پایین: اسکرول افقی + کوچک‌تر شدن
patch("app/src/main/java/com/pumpwatch/app/ui/EditorialComponents.kt", [
    # اضافه کردن ایمپورت‌های لازم برای اسکرول افقی
    ("import androidx.compose.foundation.layout.Arrangement", 
     "import androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.layout.Arrangement"),
    
    # تغییر ساختار چندردیفی به یک ردیف اسکرول‌دار
    ("""    Surface(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.chunked(5).forEach { rowTabs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val sideWeight = (5 - rowTabs.size) / 2f
                    if (sideWeight > 0f) Spacer(Modifier.weight(sideWeight))
                    rowTabs.forEach { tab ->
                        DockTabItem(
                            tab = tab,
                            selected = tab.id == selectedId,
                            glow = glow,
                            onClick = { onSelect(tab.id) }
                        )
                    }
                    if (sideWeight > 0f) Spacer(Modifier.weight(sideWeight))
                }
            }
        }
    }""",
    """    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { tab ->
                DockTabItem(
                    tab = tab,
                    selected = tab.id == selectedId,
                    glow = glow,
                    onClick = { onSelect(tab.id) }
                )
            }
        }
    }"""),
    
    # کوچک‌تر کردن آیتم‌های تب
    (".weight(1f)\n            .height(66.dp)", ".width(72.dp)\n            .height(48.dp)"),
    ("modifier = Modifier.size(23.dp)", "modifier = Modifier.size(20.dp)"),
    ("fontSize = 9.sp,", "fontSize = 8.sp,")
])

# 2. جمع‌وجور کردن بخش بالای تب هشدارها
patch("app/src/main/java/com/pumpwatch/app/ui/SmartAlertsScreen.kt", [
    ("Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp))", 
     "Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp))"),
    ("Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp))", 
     "Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp))")
])

# 3. نصف کردن فضای بالای تب بازار
patch("app/src/main/java/com/pumpwatch/app/MainActivity.kt", [
    ("contentPadding = PaddingValues(start = 16.dp, top = 17.dp, end = 16.dp, bottom = 24.dp)", 
     "contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp)")
])

print("ALL UI PATCHES APPLIED SUCCESSFULLY")
