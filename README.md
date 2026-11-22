Questo progetto è mantenuto da TheyKilledKenny. Il codice è stato scritto da una combinazione di intelligenza umana, caffeina e Codex. 😄

# TgFM - APK di develop

La build di test corrente è **TgFM 1.5.8**, codice versione 17, per Android 8+ ARM64. In **Configura → Canali preferiti** puoi scegliere copertine, periodo iniziale e invio degli archivi per ogni preferito.

**[Scarica l’APK di test 1.5.8](https://raw.githubusercontent.com/TheyKilledKenny/TgFM/develop-apk/TgFM-1.5.8-debug.apk)** · [SHA-256](TgFM-1.5.8-debug.apk.sha256) · [Ultima build di test](https://raw.githubusercontent.com/TheyKilledKenny/TgFM/develop-apk/TgFM-debug.apk)

Per la versione stabile consulta le [GitHub Releases](https://github.com/TheyKilledKenny/TgFM/releases/latest). Documentazione della build di test in [italiano](https://github.com/TheyKilledKenny/TgFM/blob/develop/README_IT.md) e [inglese](https://github.com/TheyKilledKenny/TgFM/blob/develop/README.md).

| Versione | APK | Codice | Sorgenti |
|---|---|---|---|
| 1.5.8 | [Scarica](https://raw.githubusercontent.com/TheyKilledKenny/TgFM/develop-apk/TgFM-1.5.8-debug.apk) · [SHA-256](TgFM-1.5.8-debug.apk.sha256) | 17 | [c7153db](https://github.com/TheyKilledKenny/TgFM/commit/c7153db) |
| 1.5.7 | [Scarica](https://raw.githubusercontent.com/TheyKilledKenny/TgFM/develop-apk/TgFM-1.5.7-debug.apk) · [SHA-256](TgFM-1.5.7-debug.apk.sha256) | 16 | [e5efba5](https://github.com/TheyKilledKenny/TgFM/commit/e5efba5) |
| 1.5.6 | [Scarica](https://raw.githubusercontent.com/TheyKilledKenny/TgFM/develop-apk/TgFM-1.5.6-debug.apk) · [SHA-256](TgFM-1.5.6-debug.apk.sha256) | 15 | [61e6b55](https://github.com/TheyKilledKenny/TgFM/commit/61e6b55) |
| 1.5.5 | [Scarica](https://raw.githubusercontent.com/TheyKilledKenny/TgFM/develop-apk/TgFM-1.5.5-debug.apk) · [SHA-256](TgFM-1.5.5-debug.apk.sha256) | 14 | [7737d69](https://github.com/TheyKilledKenny/TgFM/commit/7737d69) |
| 1.5.4 | [Scarica](https://raw.githubusercontent.com/TheyKilledKenny/TgFM/650a41286424130283c290de4dc2896a2a08b25f/TgFM-1.5.4-debug.apk) · [SHA-256](TgFM-1.5.4-debug.apk.sha256) | 13 | [412e55d](https://github.com/TheyKilledKenny/TgFM/commit/412e55d) |
| 1.5.3 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.5.3-debug.apk) · [SHA-256](TgFM-1.5.3-debug.apk.sha256) | 11 | Workspace develop, modifiche UI non ancora committate |
| 1.5.2 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.5.2-debug.apk) · [SHA-256](TgFM-1.5.2-debug.apk.sha256) | 10 | [c7cb2aa](https://github.com/TheyKilledKenny/TgFM/commit/c7cb2aa9ac546dd0133fdc7175696a67b4e8e8c3) |
| 1.5.1 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.5.1-debug.apk) · [SHA-256](TgFM-1.5.1-debug.apk.sha256) | 9 | [5c115bd](https://github.com/TheyKilledKenny/TgFM/commit/5c115bdb3daf2c2e8af6f97225ce0fd99c9aff3c) |
| 1.5.0 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.5.0-debug.apk) · [SHA-256](TgFM-1.5.0-debug.apk.sha256) | 8 | [6a29c76](https://github.com/TheyKilledKenny/TgFM/commit/6a29c76b8d08b35bb7bf1f75f33bebe1d8a90bb3) |
| 1.4.0 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.4.0-debug.apk) · [SHA-256](TgFM-1.4.0-debug.apk.sha256) | 7 | [bc15657](https://github.com/TheyKilledKenny/TgFM/commit/bc1565730f47c6d89a7ed5fdf1ed2965fb718442) |
| 1.3.0 | [Scarica](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.3.0-debug.apk) · [SHA-256](TgFM-1.3.0-debug.apk.sha256) | 6 | [d1885a5](https://github.com/TheyKilledKenny/TgFM/commit/d1885a5272cdba2543323fdda17f4b12ca43c6e7) |

## Versione 1.5.3

APK esistente, senza ricompilazione per questa consegna. Verificati in fase di build: compilazione debug, firma invariata, allineamento e TDLib ARM64. Come concordato, nessuna nuova suite automatica o Lint; prove funzionali affidate all’utente.

Sul telefono: nomi lunghi e comandi fissi anche senza file, ricerca e cambio canale, ritorno dalle sottosezioni, filtri da esempi, Salva/Condividi/Apri/Elimina, flag Conserva nella conferma FTP e ritorno dal background. Conserva spento elimina la copia locale dopo invio riuscito; la scelta nella conferma non cambia la preferenza salvata.

Android 8+, ARM64. Codice versione 11. SHA-256: `325648c6ec4ef948622f6db6be287f0610a677dafb5e1751c43fc24ed60cc37e`.

## Versione 1.5.2

L’app attendeva la risposta a un’opzione TDLib prima di inviare i parametri necessari a inizializzare TDLib stessa: il login si bloccava anche dopo aver cancellato tutti i dati. Ora accoda le opzioni, invia i parametri e poi attende le risposte. Rimossi Riprova accesso salvato e le protezioni sui dati preesistenti; Configura accesso avvia una sessione nuova. Gli errori restano visibili in Configura.

Verificati 9 test mirati e un test con TDLib nativa della stessa revisione: riprodotto il vecchio blocco; il client corretto arriva alla richiesta del telefono su database nuovo. Build, Lint (0 errori, 20 avvisi), firma e allineamento APK superati. Le suite precedenti non sono state ripetute. Il test nativo gira su Linux con parametri sintetici: telefono, codice, 2FA e account reale restano da provare sul dispositivo.

Stesso certificato debug; APK ARM64, Android 8+. Versione 1.5.2, codice 10. SHA-256: `5e85819d4d5e8a6ebde081e6eba2dcd0e8f764b318014e6b8148506b3f84fbfe`.

## Versione 1.5.1

Corregge la schermata di accesso Telegram quando manca il permesso ai file. Le credenziali vengono lette indipendentemente dal permesso e distinte in disponibili, mancanti o non leggibili; non vengono cancellate o sostituite. La pulizia dei vecchi allegati non coinvolge il vault o il database Telegram.

L’app presenta una richiesta guidata. Su Android 11+ aprire **Accesso speciale → Accesso a tutti i file → TgFM**, che non è fra i permessi ordinari dell’app. Al ritorno TgFM ritenta l’avvio usando credenziali e database esistenti. Se la pagina specifica manca, apre la lista generale degli accessi speciali. Corretta anche la gestione del ritorno dalle impostazioni; nessuna migrazione o rigenerazione delle chiavi salvate.

Installare sopra la 1.5.0 senza disinstallare o cancellare dati. La firma è invariata. Il riutilizzo della sessione sul telefono e la validità lato Telegram restano da verificare; la schermata con campi API vuoti della 1.5.0 non dimostrava da sola la perdita dei dati.

Build, Lint (0 errori, 20 avvisi), firma e manifest dell’APK verificati. Della suite già avviata, 166 test erano passati; il solo test rimasto aperto è passato nella verifica isolata finale. Non sono state ripetute le verifiche già completate. Test, log e strumenti di sviluppo restano esclusi dal repository sorgente.

APK 1.5.1 SHA-256: `ad837faf1b486132ea4065517c5f8c2e300900228ed2f6d7df81bd2f0ff6da5e`. Codice Android: 9. Gli aggiornamenti rimangono **1.5.x** finché non viene approvato un cambiamento del secondo numero.

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

- APK 1.5.0 SHA-256: `6f4b4982c671787b904da99e80f1fe25f09192b3a1f95a3859849b0399022038`
- Certificato SHA-256: `8c03859b96aa2ece48b3f19fec374074d5092287a53a3b34665f13af03d3304c`

Pacchetti debug per test. Credenziali Telegram/FTP non incluse negli APK. Documentazione completa nel [README dei sorgenti](https://github.com/TheyKilledKenny/TgFM/blob/develop/README.md).
