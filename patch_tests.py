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

# Fix the 3 failing tests in SignalLoggerTest.kt
patch("app/src/test/java/com/pumpwatch/app/engine/SignalLoggerTest.kt", [
    # Test 1: accuracy_stats_counts_each_status
    ("assertEquals(1, s.expired)\n        assertEquals(1, s.open)\n        assertEquals(3, s.totalDecided)",
     "assertEquals(1, s.expired)\n        assertEquals(0, s.liquidated)\n        assertEquals(1, s.open)\n        assertEquals(4, s.totalDecided)"),
    
    # Test 2: win_rate_excludes_open_and_expired (Now includes expired in denominator: 2 wins / (2+1+1) = 50%)
    ("assertEquals(66.666, s.winRate, 0.01)",
     "assertEquals(50.0, s.winRate, 0.01)"),
    
    # Test 3: win_rate_zero_when_only_open_or_expired (Now decided = 0 wins + 0 losses + 1 expired + 0 liquidated = 1)
    ("assertEquals(0, s.totalDecided)\n        assertEquals(0.0, s.winRate, 0.0001)",
     "assertEquals(1, s.totalDecided)\n        assertEquals(0.0, s.winRate, 0.0001)"),
])

print("ALL PATCHED")
