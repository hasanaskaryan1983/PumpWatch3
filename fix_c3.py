import sys, io

def patch(path, pairs):
    with io.open(path, encoding="utf-8") as f:
        src = f.read()
    for old, new in pairs:
        n = src.count(old)
        if n != 1:
            print("FAIL %s : pattern x%d : %s" % (path, n, old[:60]))
            sys.exit(1)
        src = src.replace(old, new)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("OK " + path)

base = "app/src/main/java/com/pumpwatch/app/"

patch(base + "engine/SignalLogger.kt", [
    ("fun load(ctx: Context): List<LoggedSignal> {",
     "fun isLongSide(side: String): Boolean =\n        side == \"BUY\" || side == \"PUMP\" || side == \"LONG\"\n\n    fun load(ctx: Context): List<LoggedSignal> {"),
    ("side == \"BUY\" -> maxOf(highestPrice, price)",
     "SignalLogger.isLongSide(side) -> maxOf(highestPrice, price)"),
    ("if (side == \"BUY\") best * 0.92 else best * 1.08",
     "if (SignalLogger.isLongSide(side)) best * 0.92 else best * 1.08"),
    ("side == \"BUY\" -> maxOf(trailingStop, trail)",
     "SignalLogger.isLongSide(side) -> maxOf(trailingStop, trail)"),
    ("val newTarget = if (side == \"BUY\") {",
     "val newTarget = if (SignalLogger.isLongSide(side)) {"),
    ("val hitStop = if (s.side == \"BUY\") px <= u.trailingStop else px >= u.trailingStop",
     "val hitStop = if (isLongSide(s.side)) px <= u.trailingStop else px >= u.trailingStop"),
    ("val profit = if (s.side == \"BUY\") px > s.entry else px < s.entry",
     "val profit = if (isLongSide(s.side)) px > s.entry else px < s.entry"),
])

patch(base + "engine/QuickScanner.kt", [
    ("symbol = signal.symbol, side = signal.side, score = signal.score,",
     "symbol = signal.symbol, side = if (signal.side == \"PUMP\") \"BUY\" else if (signal.side == \"DUMP\") \"SELL\" else signal.side, score = signal.score,"),
])

patch(base + "engine/WalkForwardOptimizer.kt", [
    ("if (s.side == \"BUY\") (ep - s.entry) / s.entry * 100",
     "if (SignalLogger.isLongSide(s.side)) (ep - s.entry) / s.entry * 100"),
])

patch(base + "ui/SignalLogScreen.kt", [
    ("val isLong = ev.side == \"BUY\"",
     "val isLong = com.pumpwatch.app.engine.SignalLogger.isLongSide(ev.side)"),
])

patch(base + "ui/HistoryScreen.kt", [
    ("if (s.side == \"BUY\") (ep - s.entry) / s.entry * 100",
     "if (com.pumpwatch.app.engine.SignalLogger.isLongSide(s.side)) (ep - s.entry) / s.entry * 100"),
])

print("ALL PATCHED")
