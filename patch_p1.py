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

# 1. Fix fee in BacktestEngine.kt
patch("app/src/main/java/com/pumpwatch/app/engine/BacktestEngine.kt", [
    ("val feeRoundTrip = 2.0 * (feeRate * 100.0 + 0.05)",
     "val feeRoundTrip = 2.0 * (feeRate * 100.0 + 0.05) * leverage  // FIX: fee based on leverage"),
])

# 2. Fix win rate in SignalLogger.kt
patch("app/src/main/java/com/pumpwatch/app/engine/SignalLogger.kt", [
    ("val decided = wins + losses",
     "val liquidated = logs.count { it.status == \"LIQUIDATED\" }\n        val decided = wins + losses + expired + liquidated  // FIX: include all decided"),
    ("val winRate = if (decided > 0) wins * 100.0 / decided else 0.0",
     "val winRate = if (decided > 0) wins * 100.0 / decided else 0.0  // FIX: LIQUIDATED and EXP in denominator"),
    ("data class SignalAccuracyStats(\n    val total: Int,\n    val wins: Int,\n    val losses: Int,\n    val expired: Int,\n    val open: Int,\n    val winRate: Double,\n    val totalDecided: Int\n)",
     "data class SignalAccuracyStats(\n    val total: Int,\n    val wins: Int,\n    val losses: Int,\n    val expired: Int,\n    val liquidated: Int,  // FIX: added\n    val open: Int,\n    val winRate: Double,\n    val totalDecided: Int\n)"),
    ("return SignalAccuracyStats(\n            total = logs.size,\n            wins = wins,\n            losses = losses,\n            expired = expired,\n            open = open,\n            winRate = winRate,\n            totalDecided = decided\n        )",
     "return SignalAccuracyStats(\n            total = logs.size,\n            wins = wins,\n            losses = losses,\n            expired = expired,\n            liquidated = liquidated,\n            open = open,\n            winRate = winRate,\n            totalDecided = decided\n        )"),
])

print("ALL PATCHED")
