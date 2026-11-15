Questo progetto è realizzato da Codex con la supervisione di TheyKilledKenny.

# TgFM — APK di develop

Branch unico per le build debug. I sorgenti sono in `develop`; `main` e `develop` non contengono APK, suite o strumenti locali di sviluppo/test.

**[Scarica TgFM 1.5.0](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.5.0-debug.apk)** · [Collegamento stabile all’ultima build](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-debug.apk)

Il repository è privato: aprire il link in Chrome con un account GitHub autorizzato. In alternativa aprire la pagina del file e premere **Download raw file**. Senza accesso GitHub può rispondere 404.

**Aggiornamento 1.5: al primo avvio vengono eliminati i vecchi allegati nella cartella interna, senza recupero o migrazione, come concordato. Filtri, preferiti e sessione Telegram restano conservati.** Concedere l’accesso ai file in Configura; la cartella di lavoro predefinita è Download/TgFM, modificabile.

| Versione | APK | Codice | Sorgenti |
|---|---|---|---|
| 1.5.0 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.5.0-debug.apk) · [SHA-256](TgFM-1.5.0-debug.apk.sha256) | 8 | [6a29c76](https://github.com/TheyKilledKenny/TgFM/commit/6a29c76b8d08b35bb7bf1f75f33bebe1d8a90bb3) |
| 1.4.0 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.4.0-debug.apk) · [SHA-256](TgFM-1.4.0-debug.apk.sha256) | 7 | [bc15657](https://github.com/TheyKilledKenny/TgFM/commit/bc1565730f47c6d89a7ed5fdf1ed2965fb718442) |
| 1.3.0 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.3.0-debug.apk) · [SHA-256](TgFM-1.3.0-debug.apk.sha256) | 6 | [d1885a5](https://github.com/TheyKilledKenny/TgFM/commit/d1885a5272cdba2543323fdda17f4b12ca43c6e7) |

## Versione 1.5.0

- FTP passivo/FTPS esplicito configurabile; credenziali cifrate sul dispositivo.
- Da Selezione: Salva, Condividi (prima salva, poi condivide), Salva su FTP.
- Upload FTP anticipato sui byte confermati da TDLib, mentre continua il download. Serve comunque spazio per l’intero file; nessun file integrale in RAM o seconda copia completa.
- Conserva al termine spento: un file per volta, cancellazione locale solo dopo conferma FTP. Acceso: fino a quattro trasferimenti, file mantenuti nella cartella di lavoro.
- Da Download: Condividi, Salva su FTP, Apri e cestini singolo/multiplo. Conserva spento elimina anche i file inviati da questo elenco, dopo successo.
- Errori/annullamento non cancellano automaticamente il file FTP locale o il parziale; la cache è eliminabile da Configura. Senza Conserva la coda si ferma al primo errore per non accumulare altri file.
- Cartella esterna con cache TDLib interna alla cartella scelta. Nessun recupero dei vecchi allegati privati. Cambiare cartella non sposta i file precedenti.

Verificati **157 test locali**, build, Lint (**0 errori, 18 avvisi**), firma APK, TDLib e allineamento nativo a 16 KiB. Il server FTP locale ha ricevuto dati prima del completamento del download simulato e il file finale ha SHA-256 corretto. Verificati anche errori server, annullamento e conservazione/eliminazione.

Da provare sul telefono/account/server personale: TDLib reale durante l’invio, FTPS, file fino a 4 GB, velocità, rete/background, autorizzazioni/cartella Samsung/SD/USB e condivisione. Nessun dispositivo ADB collegato durante le verifiche. Il test oltre 4 GiB copre contatori e offset, non un trasferimento completo di 4 GB. Le prestazioni non sono garantite.

Android 8.0/API 26 o successivo; ARM64. Stesso certificato Android Debug della 1.4.0: installare come aggiornamento senza disinstallare, se la build presente ha questo certificato.

- APK SHA-256: `6f4b4982c671787b904da99e80f1fe25f09192b3a1f95a3859849b0399022038`
- Certificato SHA-256: `8c03859b96aa2ece48b3f19fec374074d5092287a53a3b34665f13af03d3304c`

Pacchetti debug per test. Credenziali Telegram/FTP non incluse negli APK. Documentazione completa nel [README dei sorgenti](https://github.com/TheyKilledKenny/TgFM/blob/develop/README.md).
