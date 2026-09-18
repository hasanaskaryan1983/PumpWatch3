# Play Data Safety — answer map (source of truth: actual code, Sprint 14)

| Data type | Collected? | Sent off-device? | Stored? | Encryption | Purpose |
|---|---|---|---|---|---|
| Wallet addresses / notes | Yes (user input) | No | Yes (device) | AES-256-GCM Keystore | On-chain analysis the user requests |
| Paper-trade ledger | Yes (simulated) | No | Yes (device) | AES-256-GCM Keystore | Practice trading & journal |
| Alert rules | Yes (user input) | No | Yes (device) | Keystore-backed prefs | Price/volume/whale alerts |
| Signal log & scores | Generated | No | Yes (device) | Keystore-backed prefs | Track signal accuracy honestly |
| Market cache (prices/candles) | Fetched | Request to public APIs | Yes (temp) | N/A (public data) | Charts & scanners |
| Device identifiers | **No** | No | No | — | — |
| Location / contacts / files | **No** | No | No | — | — |
| Financial info (real funds) | **No** | No | No | — | App is non-custodial & paper-only |

## Form answers
- Data collected: none of Google's "personal info" categories.
- Data shared: **No data is shared with third parties.**
- Deleted on request: user can wipe in-app (Privacy Center) or uninstall.
- Encryption in transit: HTTPS enforced (cleartext disabled).
- Encryption at rest: AES-256-GCM via Android Keystore.
