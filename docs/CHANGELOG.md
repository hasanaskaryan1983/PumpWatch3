# Changelog — Sprint 14 (Stage 1→7)

All entries reflect shipped, CI-green commits. No planned features listed.

## Stage 1 — Data truth (scanner & engines)
- Scanner revived: kline array 7th member (closeTime) fixed a silent
  IndexOutOfBounds that had killed every quick-scan signal since day one
- FUT path can now fire: history limit raised so the 4h regime layer works
- SPOT reads true 1h candles (previously daily candles mislabeled as 1h)
- Meme radar: GoPlus now checks the BASE TOKEN contract (was checking the
  pool address => safety check was dead); pool links use real pool address
- Notification permission guard + honest "alerts blocked" flag
- Backtest: intrabar stop/target with stop-priority, next-bar fills,
  real fee+slippage model (was close-only & hardcoded fees)

## Stage 2 — Provenance (every number names its source)
- MarketMeta: market data carries observedAt + served-from (network/mem/disk)
- Kline venue recorded per symbol (Bybit/OKX/Gate) and shown in UI
- Whale flow label shows the real answering venue (was hardcoded "aggTrades")
- Meme cards state source + real GoPlus status (READY/EMPTY/FAILED)
- Charts disclose synthesized CoinGecko candles in orange; real venue in gray

## Stage 3 — Security & privacy
- AES-256-GCM encryption via Android Keystore (no new dependencies)
- Manifest: backup disabled, cloud/device-transfer exclusions, HTTPS-only,
  minimal permission set (INTERNET + POST_NOTIFICATIONS), locked by test
- Paper ledger encrypted with one-time transparent migration; Gson
  default-value trap caught by regression test and sanitized
- Privacy Center tab: live security status, provider list, cache clear,
  two-step export, two-step wipe

## Stage 7 — Release documents
- Privacy policy, Data Safety answer map, store listing, risk disclosure
  written from actual code behavior (claim-freeze enforced)

## Open decisions before release
- App label: manifest says "PumpDump", store listing says "PumpWatch" — pick one
- Support email must be added to privacy policy (Play requirement)
- Market-tab freshness badge pending (needs MarketScreen source)
