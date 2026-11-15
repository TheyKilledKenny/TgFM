Questo progetto è realizzato da Codex con la supervisione di TheyKilledKenny.

# TgFM — Telegram File Manager

TgFM è un'app Android nativa per consultare gli allegati dei propri canali Telegram, selezionarli attraverso regole configurabili, scaricarli e poi condividerli o spostarli in una cartella del dispositivo.

L'app utilizza l'account Telegram personale tramite **TDLib** e funziona autonomamente sul telefono, senza un computer o un server applicativo. L'interfaccia è in italiano.

## Indice

- [Funzionalità](#funzionalità)
- [Requisiti](#requisiti)
- [Compilazione](#compilazione)
- [Installazione e primo accesso](#installazione-e-primo-accesso)
- [Utilizzo](#utilizzo)
- [Configurazione dei filtri](#configurazione-dei-filtri)
- [Download e gestione dei file](#download-e-gestione-dei-file)
- [Condivisione e salvataggio](#condivisione-e-salvataggio)
- [Dati locali e sicurezza](#dati-locali-e-sicurezza)
- [Architettura](#architettura)
- [Integrazione TDLib](#integrazione-tdlib)
- [Verifiche e limiti](#verifiche-e-limiti)
- [Licenze di terze parti](#licenze-di-terze-parti)

## Funzionalità

- Accesso Telegram tramite numero telefonico, codice e verifica in due passaggi, quando richiesta.
- Conservazione locale della sessione e disconnessione volontaria.
- Selezione di più canali preferiti, anche privati o archiviati, con ricerca per nome e cambio rapido del canale attivo.
- Lettura paginata dei metadati degli allegati, senza scaricarne preventivamente i contenuti.
- Riconoscimento di documenti, immagini, video, audio, animazioni e sticker scaricabili.
- Regole persistenti assegnate a un canale preferito, sul nome, sulla data variabile, sulle estensioni, sul MIME e sulla categoria Telegram.
- Proposte di regole comuni da più esempi desiderati e indesiderati, con anteprima e conferma.
- Selezione manuale degli allegati prima del download.
- Fino a **quattro download paralleli**, avanzamento individuale, annullamento e ripetizione degli insuccessi.
- Scelta tra cancellazione e mantenimento dei download precedenti.
- Condivisione tramite Android Sharesheet, anche verso WhatsApp.
- Spostamento dei file selezionati tramite Storage Access Framework (SAF).

## Requisiti

### Dispositivo

| Requisito | Valore |
|---|---|
| Sistema operativo minimo | Android 8.0 / API 26 |
| Architettura inclusa | ARM64 (`arm64-v8a`) |
| Dispositivo di riferimento | Samsung Galaxy S23 Ultra |
| Telegram | Account personale già esistente |
| Connessione | Necessaria per accesso, lettura dei canali e download |

Il progetto include soltanto la libreria nativa ARM64. Per un emulatore x86/x86_64 o un dispositivo con un'altra ABI occorre compilare e aggiungere la corrispondente libreria TDLib.

### Compilazione

| Componente | Versione configurata |
|---|---|
| Java | JDK 21 consigliato; bytecode JVM 17 |
| Gradle | 8.13, fornito dal Wrapper |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / plugin Compose | 2.2.21 |
| Android SDK di compilazione | API 36 |
| Android SDK target | API 35 |
| Android Build Tools | 35.0.0 |
| Android NDK | 27.2.12479018 |
| Compose BOM | 2025.11.00 |

La libreria TDLib precompilata è inclusa: **per la normale build dell'app non è necessario compilare TDLib né installare CMake**. Il NDK configurato viene utilizzato dagli strumenti Android per elaborare le librerie native. Le dipendenze Gradle vengono scaricate da Google Maven, Maven Central e dal portale dei plugin Gradle.

## Compilazione

### 1. Clonare il repository

```bash
git clone https://github.com/TheyKilledKenny/TgFM.git
cd TgFM
```

### 2. Preparare Android SDK e Java

Con Android Studio, installare i componenti indicati nella tabella precedente tramite SDK Manager. In alternativa, con gli Android SDK Command-line Tools disponibili:

```bash
sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools" "ndk;27.2.12479018"
sdkmanager --licenses
```

Impostare `JAVA_HOME` sul JDK e `ANDROID_HOME` sulla directory dell'SDK. In alternativa ad `ANDROID_HOME`, creare nella radice del progetto un file locale `local.properties`:

```properties
sdk.dir=/percorso/assoluto/Android/Sdk
```

`local.properties` non deve essere aggiunto al repository.

### 3. Generare l'APK debug

Su Linux/macOS:

```bash
./gradlew :app:assembleDebug
```

Su Windows, da PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

L'APK viene generato in:

```text
app/build/outputs/apk/debug/app-debug.apk
```

La build debug viene firmata automaticamente con la chiave debug locale degli strumenti Android. Il repository non contiene chiavi di firma, credenziali Telegram o APK già generati. Per aggiornare un'app già installata mantenendone i dati, il nuovo APK deve avere lo stesso application ID e lo stesso certificato di firma; una nuova macchina può generare una chiave debug diversa.

### Controlli disponibili

```bash
./gradlew :app:lintDebug
```

Con gli Android Build Tools nel `PATH`, è possibile verificare il pacchetto:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk
```

## Installazione e primo accesso

1. Installare l'APK sul telefono autorizzando l'installazione dalla sorgente utilizzata, oppure collegare un dispositivo con debug USB abilitato:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Aprire **Configura** e inserire il proprio **API ID** e **API hash**, ottenuti da [my.telegram.org](https://my.telegram.org) nella sezione *API development tools*. Sono le credenziali di un'app Telegram, non un token Bot API. Non serve ricompilare l'APK.
3. Inserire il numero con prefisso internazionale, il codice ricevuto e l'eventuale password di verifica in due passaggi. L'app gestisce anche i passaggi e-mail richiesti da TDLib.
4. Premere **Aggiorna elenco canali**, cercare i canali per nome e spuntare quelli da aggiungere ai preferiti. Per un canale su invito, unirsi prima tramite l'app Telegram ufficiale.

La registrazione di un nuovo account e gli eventuali passaggi di acquisto richiesti da Telegram devono essere completati nell'app ufficiale. La sessione viene riutilizzata agli avvii successivi finché resta valida.

## Utilizzo

Il menu segue questo ordine:

| Area | Operazioni principali |
|---|---|
| **Selezione** | Cambiare canale fra i preferiti toccandone il nome, leggere gli allegati, cercarli e scegliere i download |
| **Download** | Seguire i trasferimenti, aprire i file completati, selezionare, condividere e salvare |
| **Filtri** | Assegnare regole ai preferiti, modificarle con azioni a icone e configurarle da esempi ricercabili |
| **Configura** | Gestire accesso, canali preferiti e politica sui download precedenti |

All'avvio, l'app apre Selezione quando sessione e canale preferito sono pronti; altrimenti apre Configura. Una riconnessione temporanea di Telegram, anche dopo il ritorno dal background, non blocca Selezione: è possibile consultare i metadati già letti mentre la connessione viene ripristinata. Sessione non valida o assenza del canale attivo richiedono Configura. Download e Filtri restano accessibili manualmente per le funzioni locali.

Flusso abituale:

1. In Selezione, toccare il nome del canale per sceglierne uno fra i preferiti, poi premere **Leggi canale**. La ricerca per nome file si combina con la vista corrente senza perdere le checkbox dei file nascosti.
2. Verificare i risultati, scegliendo **Mostra solo selezionati**, **Mostra selezionati e Affini** oppure **Mostra tutti i file**. I selezionati sono sempre in alto, anche se selezionati manualmente; Affini indica le corrispondenze Possibili. La vista scelta viene conservata.
3. Modificare le checkbox e premere **Scarica selezionati**.
4. In Download, attendere il completamento e scegliere i file da condividere o salvare.

## Canali preferiti

In Configura le checkbox aggiungono o rimuovono i preferiti. Il canale attivo può essere cambiato toccandone il nome in Selezione. Cambiare canale azzera i risultati precedenti, che vanno riletti con Leggi canale; si applicano soltanto i filtri assegnati al canale attivo.

Ogni regola nuova o modificata deve essere assegnata a un preferito nell'editor. Le proposte da esempi sono assegnate al canale dal quale sono stati letti gli allegati. Regole uguali su canali diversi rimangono distinte.

Rimuovere un preferito cancella anche i filtri associati a quel canale. Se ci sono filtri, compare una sola conferma con il loro numero; annullandola non cambia nulla. Senza filtri associati la rimozione è immediata. I file già scaricati non vengono eliminati. Se si rimuove il canale attivo viene scelto il primo preferito rimasto; senza preferiti si apre Configura.

All'aggiornamento, il vecchio canale selezionato diventa un preferito e riceve le regole precedenti. Eventuali regole senza un vecchio canale vengono conservate come Da assegnare e possono essere associate dall'editor.

## Configurazione dei filtri

### Il modello riguarda il nome, non il formato

Il modello viene confrontato con l'intero nome **senza estensione**. Il campo della radice non aggiunge automaticamente `.pdf` o altre estensioni.

Per esempio, creare due inclusioni separate:

```text
La_Gente_{data}
Il_Pino_Verde_{data}
```

Con **Estensioni** vuoto, queste regole consentono PDF, EPUB, TXT e altri formati:

| Nome | Selezionato automaticamente |
|---|---|
| `La_Gente_14_Novembre_2026.pdf` | Sì |
| `La_Gente_15_Novembre_2026.epub` | Sì |
| `Il_Pino_Verde_14_Novembre_2026.txt` | Sì |
| `La_Gente_Roma_14_Novembre_2026.pdf` | No |
| `D_La_Gente_14_Novembre_2026.pdf` | No |
| `Il_Pino_Verde_apostolico_14_Novembre_2026.pdf` | No |
| `Estratto_Il_Pino_Verde.pdf` | No |

Nel campo **Radice esatta prima della data**, inserire ad esempio `La_Repubblica_`, compreso il separatore finale. Il modello generato sarà `La_Repubblica_{data}`.

`{data}` riconosce, tra gli altri, `14Nov`, `14Nov2026`, `14_Novembre_2026`, `14 Novembre 2026`, `2026-11-14`, `14.11.2026` e `20261114`. Il riconoscimento è sintattico: non verifica la validità del giorno nel calendario. Non accetta segmenti aggiuntivi al posto della data.

### Criteri di tipo e combinazioni

| Criterio | Esempio | Se lasciato vuoto / su Qualsiasi |
|---|---|---|
| Estensioni | `pdf` oppure `pdf, epub` | Tutti i formati |
| MIME | `application/pdf` oppure `image/*` | Nessun vincolo MIME |
| Categoria Telegram | Documento, Immagine, Video, Audio, Sticker | Nessun vincolo di categoria |

La categoria **Documento** può comprendere PDF, EPUB e archivi: non è sinonimo di PDF.

- I criteri compilati nella stessa regola sono combinati in **AND**.
- Più inclusioni sono combinate in **OR**.
- Le esclusioni esplicite prevalgono sulle inclusioni.
- Le regole disattivate o prive di criteri non selezionano nulla.
- I confronti non distinguono maiuscole e minuscole.

Le regole precedenti con un suffisso come `.pdf` nel modello vengono interpretate sul solo nome. Per limitarsi ai PDF occorre specificare `pdf` nel campo Estensioni. Un criterio di tipo già salvato continua a essere applicato.

### Classificazione e selezione manuale

- **Certa:** soddisfa un'inclusione e non un'esclusione; selezionata inizialmente.
- **Possibile:** assomiglia a una regola, ma non la soddisfa completamente; non selezionata inizialmente.
- **Non corrispondente:** non soddisfa le regole; non selezionata.
- **Escluso:** soddisfa un'esclusione esplicita; non selezionato.

Una variante indesiderata può comparire come Possibile senza essere scaricata automaticamente. L'utente può selezionare manualmente anche un escluso: la scelta vale soltanto per l'operazione corrente e non cambia i filtri. La conferma di una modifica alle regole ricalcola le selezioni automatiche.

### Configura da esempi

1. Dopo aver letto un canale, aprire Filtri e cercare i nomi desiderati.
2. Contrassegnare più file come **Desiderato** o **Indesiderato**. Le etichette restano assegnate anche quando la ricerca nasconde i file; è disponibile **Solo esempi già contrassegnati**.
3. Premere **Proponi regole comuni**.
4. Verificare modelli, conteggi, anteprima e avvisi, quindi confermare. L’anteprima elenca soltanto i file che verrebbero selezionati automaticamente, senza Possibili, Non corrispondenti o Esclusi.

Le date vengono generalizzate conservando la radice completa. Cinquanta numeri della stessa testata possono produrre un'unica regola, anziché cinquanta nomi esatti. Con esempi positivi, i negativi vincolano le proposte senza generare una blacklist per ogni variante. Proposte ambigue vengono segnalate e omesse. Con soli esempi negativi possono essere proposte esclusioni raggruppate.

L'inferenza è deterministica e non è un classificatore generale: convenzioni senza una data riconosciuta richiedono una regola manuale. Non sono esposte espressioni regolari arbitrarie. L'anteprima considera anche le vecchie regole: un'inclusione generica come “contiene La_Gente” può continuare a includere le edizioni locali finché non viene modificata o disattivata.

## Download e gestione dei file

In Configura, **Cancella i download precedenti** stabilisce il comportamento di una nuova operazione:

| Impostazione | Comportamento |
|---|---|
| Disattivata — valore iniziale | Mantiene gli altri file; elimina l'omonimo precedente quando inizia il relativo download |
| Attivata | Svuota la cartella privata dei download prima di avviare la nuova operazione |

**La sovrascrittura è distruttiva:** se il nuovo download fallisce o viene annullato dopo l'eliminazione dell'omonimo, il precedente è perso. Non vengono conservati backup.

L'app richiede fino a quattro trasferimenti contemporanei. Gli allegati con lo stesso file ID Telegram sono coordinati per evitare accessi simultanei al medesimo file nativo. Sono mostrati conteggio, file attivi, avanzamenti ed errori; **Riprova** ritenta gli insuccessi senza ripetere la pulizia generale.

TDLib usa i propri file parziali interni durante il trasferimento. L'app non aggiunge copie temporanee del contenuto: al completamento rinomina il file nello stesso filesystem. I parziali non vengono presentati come pronti per Condividi o Salva. I nomi locali sono sanitizzati; omonimi selezionati nello stesso batch ricevono nomi distinti. Per omonimo si intende lo stesso nome originale completo, estensione compresa.

Dopo il completamento i file sono inizialmente tutti selezionati. Dopo una terminazione forzata dell'app i completati restano disponibili; non è previsto il ripristino automatico del batch interrotto.

## Condivisione e salvataggio

### Condividi

Usa Android Sharesheet con `ACTION_SEND` o `ACTION_SEND_MULTIPLE`, URI `content://` del FileProvider e permessi temporanei di sola lettura. Per formati misti viene usato `*/*`.

Le righe dei download sono compatte: l'icona a forma di occhio a destra apre il file, senza una riga aggiuntiva per il pulsante Apri.

L'utente sceglie l'app destinataria, il gruppo o contatto e conferma l'invio. Limiti di numero, formato e dimensione dipendono dalla destinazione. Il ritorno a TgFM non certifica che il destinatario abbia effettivamente ricevuto i file.

### Salva

Apre il selettore cartelle Android e **sposta** soltanto i file selezionati:

1. Crea un documento nella destinazione, evitando sovrascritture di omonimi.
2. Trasferisce e verifica dimensione e SHA-256 del contenuto.
3. Elimina l'originale privato soltanto dopo la verifica riuscita.
4. Rimuove dall'elenco i file spostati.

Gli originali non trasferiti restano disponibili. La protezione dell'originale nello spostamento SAF è distinta dalla sovrascrittura distruttiva dei download precedenti.

Un journal consente di recuperare gli spostamenti interrotti; il comando è disponibile in Configura. Se il provider perde accessibilità o rifiuta di eliminare una copia parziale, l'app segnala il problema e può richiedere di riconcedere la destinazione. Non è possibile ottenere una transazione atomica tra due provider di archiviazione indipendenti.

## Dati locali e sicurezza

- API ID/hash e chiave casuale del database sono cifrati con AES-GCM e una chiave Android Keystore.
- Il database della sessione TDLib è cifrato; codici di accesso e password non vengono salvati dall'app.
- Canale, regole e preferenze sono conservati in DataStore.
- Sessione, contenuti TDLib e download restano nello spazio privato dell'app.
- Il FileProvider espone esclusivamente la cartella dei file completati.
- Backup e trasferimento automatico dei dati Android sono disabilitati.
- L'app non richiede accesso generale alla memoria: Salva usa il permesso concesso dal selettore SAF.
- Le operazioni sono avviate manualmente; un servizio foreground mantiene attivi i trasferimenti e il ViewModel conserva lo stato nelle normali ricreazioni dell'Activity.

## Architettura

```text
app/src/main/
├── java/it/tgfm/
│   ├── core/         Modelli, filtri, nomi, navigazione e parallelismo
│   ├── data/         DataStore, Keystore, filesystem, SAF e condivisione
│   ├── telegram/     Client TDLib JSON e conversione dei metadati
│   ├── ui/           Interfaccia Compose e ViewModel
│   ├── MainActivity.kt
│   ├── TgfmApp.kt
│   └── TransferService.kt
├── java/org/drinkless/tdlib/JsonClient.java
├── jniLibs/arm64-v8a/libtdjsonjava.so
├── res/
└── AndroidManifest.xml
```

Il repository contiene i file necessari alla compilazione dell'app e gli avvisi di licenza. APK, cache, log, rapporti, suite e strumenti locali di sviluppo/test sono esclusi.

## Integrazione TDLib

L'integrazione utilizza l'interfaccia ufficiale **JSONJava**, non la Bot API.

| Componente | Provenienza |
|---|---|
| TDLib | 1.8.70, commit `c8828d50f5eac2fc7089ce1089d7ce9095c61b5e` |
| Binding Java | `JsonClient.java` della stessa revisione TDLib |
| Binario incluso | `app/src/main/jniLibs/arm64-v8a/libtdjsonjava.so` |
| OpenSSL | 3.5.4, collegamento statico |
| Runtime C++ | Statico, Android NDK 27.2.12479018 |
| Allineamento ELF | 16 KiB |

SHA-256 del binario TDLib incluso:

```text
511c1d6505de65136876f3ca6d9ca0e0ac27995914fcd44788ea02848cc72102
```

Per ricompilare la componente nativa o aggiungere altre ABI, seguire le [istruzioni Android upstream della revisione utilizzata](https://github.com/tdlib/td/tree/c8828d50f5eac2fc7089ce1089d7ce9095c61b5e/example/android), scegliendo l'interfaccia `JSONJava`. Copiare le librerie risultanti in `app/src/main/jniLibs/<ABI>/` e aggiornare `abiFilters` nella configurazione Gradle. Binding e libreria devono provenire dalla stessa revisione.

## Verifiche e limiti

Durante lo sviluppo della versione 1.3.0 sono passati 129 test locali JUnit/Robolectric/Compose, inclusi preferiti, migrazione, isolamento dei filtri per canale, riconnessione, navigazione, righe compatte, concorrenza, sostituzione dei file, persistenza e condivisione Android. La build debug, la firma, l'allineamento delle librerie e Android Lint sono stati verificati. Le suite e i rapporti di sviluppo non sono inclusi nel repository; il controllo riproducibile disponibile qui è `lintDebug`, oltre alla build dell'APK.

Rimangono da verificare sul dispositivo fisico e con account reali:

- Login, 2FA, ripristino della sessione e lettura di un canale privato.
- Download Telegram paralleli, velocità, annullamento e comportamento con rete o spazio insufficienti.
- Interfaccia e gestione dei trasferimenti in background sul Galaxy S23 Ultra.
- Condivisione effettiva con WhatsApp e altri destinatari.
- Spostamenti, collisioni e recupero degli errori con il provider SAF Samsung.

Sono supportati un account, più canali preferiti con filtri associati e un canale attivo alla volta. Scansione simultanea di più canali, cartella predefinita e download avviato direttamente da Condividi/Salva non sono presenti in questa versione.

## Licenze di terze parti

- **TDLib:** [Boost Software License 1.0](docs/licenses/TDLib-Boost-1.0.txt).
- **OpenSSL:** [Apache License 2.0](docs/licenses/OpenSSL-Apache-2.0.txt).
- **SQLite/SQLCipher incluso in TDLib:** [avviso BSD di ZETETIC LLC](docs/licenses/SQLite.txt).
- **AndroidX, Jetpack Compose, Kotlin e coroutines:** rispettive licenze e avvisi upstream; dipendenze dichiarate nella configurazione Gradle.

I nomi Telegram e WhatsApp identificano servizi di terze parti. TgFM è un progetto indipendente.
