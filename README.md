Questo progetto è realizzato da Codex con la supervisione di TheyKilledKenny.

# TgFM — APK di develop

Branch unico per i pacchetti debug compilati. I sorgenti sono in `develop`; `main` e `develop` non contengono APK o strumenti locali di sviluppo/test.

**[Scarica l’ultima versione: TgFM 1.4.0](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-debug.apk)** · [Checksum](TgFM-debug.apk.sha256)

Il repository è privato: aprire il collegamento in Chrome con un account GitHub autorizzato. Se necessario, aprire la pagina del file e usare **Download raw file**. Senza accesso GitHub può mostrare 404.

| Versione | APK | Codice | Sorgenti |
|---|---|---|---|
| 1.4.0 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.4.0-debug.apk) · [SHA-256](TgFM-1.4.0-debug.apk.sha256) | 7 | [bc15657](https://github.com/TheyKilledKenny/TgFM/commit/bc1565730f47c6d89a7ed5fdf1ed2965fb718442) |
| 1.3.0 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.3.0-debug.apk) · [SHA-256](TgFM-1.3.0-debug.apk.sha256) | 6 | [d1885a5](https://github.com/TheyKilledKenny/TgFM/commit/d1885a5272cdba2543323fdda17f4b12ca43c6e7) |

## Versione 1.4.0

- Lettura dei post: Oggi (default), Tutti i file, Un giorno preciso o Intervallo di date. Date nel fuso orario del telefono, giorni iniziale e finale inclusi.
- Per i periodi delimitati, ricerca Telegram alla data finale e arresto della paginazione alla data iniziale; nessuna scansione preventiva dell’intera cronologia.
- Annulla operazione mantiene i metadati già letti, le selezioni automatiche e l’indicazione Lettura parziale.
- Elenco di canali e gruppi di base/supergruppi, inclusi gli archiviati, senza limite totale applicativo di 100 elementi.
- Nessuna migrazione o recupero automatico aggiunto. Il formato delle preferenze esistenti resta compatibile.

Android 8.0/API 26 o successivo; ARM64 (`arm64-v8a`). Firma con lo stesso certificato Android Debug della 1.3.0: installare come aggiornamento senza disinstallare, se la build presente ha questo certificato.

Verificati 145 test locali, build, Lint (0 errori, 15 avvisi), firma APK, TDLib e allineamento nativo a 16 KiB. I test del trasporto usano risposte simulate. Restano da provare sul telefono/account: tempi reali e date della cronologia, annullamento con successivo download, oltre 100 chat effettive, tocco del calendario, background, condivisione e SAF Samsung.

Certificato SHA-256: `8c03859b96aa2ece48b3f19fec374074d5092287a53a3b34665f13af03d3304c`.

I pacchetti sono prerelease debug per test. API ID e API hash si inseriscono nell’app e non sono inclusi negli APK.
