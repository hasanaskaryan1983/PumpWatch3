feat(Sprint 14/Stage 3, Commit 7C): unified paper ledger + encryption + migration

Executive report Stage 3 (P0): "two paper trading systems in parallel"
- TradesScreen: SharedPreferences("pumpwatch_prefs") with paper_state
- TradeStore: SharedPreferences("pumpdump_trades") with trades

This commit unifies them into a single encrypted ledger:

1. Trade model v2: added venue, contract, slippagePct, feePct,
   fillTime, ledgerVersion (all with defaults → backward compatible)
2. TradeStore now uses SecureStorage (AES-256-GCM) instead of plain
   SharedPreferences
3. Automatic one-time migration: legacy plain-text trades are
   upgraded to v2 and encrypted (KEY_MIGRATED flag)
4. PaperTradingEngine now records real venue (via lastSource) and
   fillTime (next-bar fill, not signal candle close)
5. PnL calculations use actual feePct and slippagePct (not hardcoded
   0.1% and 0.2%)

Migration is transparent: existing trades are preserved, just
encrypted. If SecureStorage fails (old device), trades fall back to
plain text with a flag for Privacy Center disclosure.

Tests cover: legacy JSON parsing, default values, PnL with real
fees, R-multiple calculation.

Next: 7D Privacy Center (export/delete/disclosure), 6D Market
freshness badge, Stage 7 Play Store prep.
