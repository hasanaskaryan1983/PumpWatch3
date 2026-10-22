import sys, io

def patch_all(path, pairs):
    with io.open(path, encoding="utf-8") as f:
        src = f.read()
    for old, new in pairs:
        n = src.count(old)
        if n == 0:
            print("FAIL %s : pattern not found : %s" % (path, old[:60]))
            sys.exit(1)
        src = src.replace(old, new)
        print("  replaced %d occurrence(s)" % n)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("OK " + path)

base = "app/src/main/java/com/pumpwatch/app/"

patch_all(base + "engine/QuickScanner.kt", [
    ("symbol = signal.symbol, side = signal.side, score = signal.score,",
     "symbol = signal.symbol, side = if (signal.side == \"PUMP\") \"BUY\" else if (signal.side == \"DUMP\") \"SELL\" else signal.side, score = signal.score,"),
])

print("ALL PATCHED")
