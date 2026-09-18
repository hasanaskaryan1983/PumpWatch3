# Privacy Policy — PumpWatch (PumpDump)

Last updated: Sprint 14 (Stage 3 complete)

## What we collect
**Nothing. There is no server, no account, no analytics, no advertising SDK.**
All data lives on your device:

- Wallet addresses and notes you add — encrypted with AES-256-GCM
  (Android Keystore). The key never leaves the device.
- Paper-trading ledger (simulated trades) — encrypted, same mechanism.
- Alert rules and signal log — stored locally.
- Market cache (prices, candles) — temporary, clearest-free of personal data.

## What leaves the device
Only public market-data requests, with no identifiers attached:
CoinGecko, GeckoTerminal, GoPlus Security, Bybit, OKX, Gate.io,
Binance public endpoints, and public TON/Sui chain services.
These services see your IP address as part of normal HTTPS traffic.

## Backups
Cloud backup of app data is **disabled** (allowBackup=false).
Your wallet addresses and ledger are never copied to Google Drive.

## Your controls
In-app: 🔒 Privacy Center → clear cache, export ledger (explicit action),
or wipe all encrypted data permanently.

## Children
This app is not intended for users under 18. It shows financial market
data and simulated trading only.

## Changes
Updates to this policy ship with app updates and are listed in the
in-app changelog.

Contact: (add your support email before release — required by Play)
