Questo progetto è realizzato da Codex con la supervisione di TheyKilledKenny.

# TgFM — Telegram File Manager

TgFM è un'app Android nativa per consultare gli allegati dei propri canali Telegram, selezionarli attraverso regole configurabili, salvarli nella cartella scelta, condividerli o inviarli a un server FTP/FTPS.

L'app utilizza l'account Telegram personale tramite **TDLib** e funziona autonomamente sul telefono, senza un computer o un server applicativo. L'interfaccia è in italiano.

## Stato del progetto

La versione disponibile per il test è **1.5.2** (codice Android **10**), distribuita come APK debug firmato per ARM64. Il completamento del login con un account reale e le altre prove sul telefono elencate in [Verifiche e limiti](#verifiche-e-limiti) restano da confermare; la compilazione riuscita non equivale a una validazione completa sul dispositivo.

- [`develop`](https://github.com/TheyKilledKenny/TgFM/tree/develop): sorgenti della versione descritta in questo README.
- [`develop-apk`](https://github.com/TheyKilledKenny/TgFM/tree/develop-apk): APK di test e checksum, separati dai sorgenti.
- [`main`](https://github.com/TheyKilledKenny/TgFM/tree/main): non ancora allineato alla 1.5.2; il passaggio della versione è da approvare.

**[Scarica TgFM 1.5.2](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-1.5.2-debug.apk)** · [Checksum SHA-256](https://github.com/TheyKilledKenny/TgFM/blob/develop-apk/TgFM-1.5.2-debug.apk.sha256). Per un repository privato serve un browser con accesso GitHub autorizzato. Se l'app di chat tenta un'anteprima non supportata, aprire il collegamento nel browser; dalla pagina GitHub del file è disponibile anche **Download raw file**.

È in valutazione una riprogettazione della sola interfaccia. I mockup sono proposte da approvare: barra delle azioni sempre raggiungibile, nuova disposizione delle impostazioni e nuovi moduli **non sono implementati nell'APK 1.5.2**. Le istruzioni seguenti descrivono l'app attuale.

## Correzione 1.5.2: avvio e nuovo login Telegram

La regressione introdotta nella 1.5.0 era nell’ordine delle richieste TDLib: l’app attendeva la risposta a `setOption` prima di inviare `setTdlibParameters`. TDLib accoda le opzioni fino all’inizializzazione, quindi l’attesa impediva di inviare proprio i parametri necessari a sbloccarla. Questo blocco riguardava anche una prima installazione, con dati e cache cancellati. La modifica della 1.5.1 alla richiesta dei permessi non risolveva questa causa.

La 1.5.2 accoda le opzioni per la cartella esterna, invia subito i parametri e solo dopo attende le risposte. In questo modo le opzioni sono già presenti quando TDLib crea i gestori dei file. Gli errori iniziali rimangono visibili in Configura.

Il login è diretto: **Concedi accesso ai file → API ID e API hash → Configura accesso → telefono → codice → eventuale password 2FA**. I pulsanti Riprova accesso salvato e Riprova lettura credenziali, i blocchi relativi a dati preesistenti e la vecchia pulizia di migrazione sono rimossi. Premere Configura accesso chiude l’eventuale client, elimina il precedente database della sessione Telegram e salva una nuova configurazione, senza dipendere dalla decifratura dei dati precedenti. Il normale riutilizzo della sessione dopo un login riuscito resta automatico agli avvii successivi.

Su Android 11 o successivo, **Accesso a tutti i file** rimane un accesso speciale, non un permesso ordinario. Usare Concedi accesso oppure **Impostazioni → Applicazioni → Accesso speciale → Accesso a tutti i file → TgFM**. Su Android 8–10 viene richiesto il permesso di archiviazione ordinario.

Versionamento concordato: serie **1.5.x**, senza incrementare il secondo numero senza approvazione. Questa correzione è **1.5.2**, codice Android 10.

## Indice

- [Funzionalità](#funzionalità)
- [Requisiti](#requisiti)
- [Compilazione](#compilazione)
- [Installazione e primo accesso](#installazione-e-primo-accesso)
- [Utilizzo](#utilizzo)
- [Canali e gruppi preferiti](#canali-e-gruppi-preferiti)
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
- Selezione di più canali e gruppi preferiti, anche privati o archiviati, con ricerca per nome e cambio rapido del canale attivo, senza un limite totale di 100 elementi.
- Lettura paginata dei metadati degli allegati, senza scaricarne preventivamente i contenuti; risultati parziali disponibili anche dopo l'annullamento.
- Periodo dei post da leggere: **Oggi** (predefinito), **Tutti i file**, **Un giorno preciso** o **Intervallo di date**, applicato durante la lettura.
- Riconoscimento di documenti, immagini, video, audio, animazioni e sticker scaricabili.
- Regole persistenti assegnate a un canale preferito, sul nome, sulla data variabile, sulle estensioni, sul MIME e sulla categoria Telegram.
- Proposte di regole comuni da più esempi desiderati e indesiderati, con anteprima e conferma.
- Selezione manuale degli allegati prima del download.
- Fino a **quattro download paralleli**, avanzamento individuale, annullamento e ripetizione degli insuccessi.
- Scelta tra cancellazione e mantenimento dei download precedenti.
- Condivisione tramite Android Sharesheet, anche verso WhatsApp.
- Cartella di lavoro esterna configurabile (default `Download/TgFM`), con cache TDLib separata al suo interno.
- Invio FTP/FTPS anche mentre Telegram sta ancora scaricando, senza una seconda copia completa.
- Opzione **Conserva al termine**, eliminazione singola/multipla e pulizia esplicita della cache.

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

### Ambiente di compilazione

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
git clone --branch develop https://github.com/TheyKilledKenny/TgFM.git
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

La build debug viene firmata automaticamente con la chiave debug locale degli strumenti Android. I branch sorgente `main` e `develop` non contengono chiavi di firma, credenziali Telegram o APK già generati. I pacchetti per il test sono pubblicati separatamente nel branch [`develop-apk`](https://github.com/TheyKilledKenny/TgFM/tree/develop-apk), con APK e checksum per versione e un collegamento stabile all'ultima build: [scarica TgFM-debug.apk](https://github.com/TheyKilledKenny/TgFM/raw/refs/heads/develop-apk/TgFM-debug.apk). Se il repository è privato, aprire il collegamento nel browser con un account GitHub autorizzato.

Per aggiornare un'app già installata mantenendone i dati, il nuovo APK deve avere lo stesso application ID e lo stesso certificato di firma; una nuova macchina può generare una chiave debug diversa.

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

2. Aprire **Configura**, concedere l’accesso ai file e scegliere la cartella di lavoro (default `Download/TgFM`). Inserire il proprio **API ID** e **API hash**, ottenuti da [my.telegram.org](https://my.telegram.org) nella sezione *API development tools*. Sono le credenziali di un'app Telegram, non un token Bot API. Non serve ricompilare l'APK.
3. Premere **Configura accesso**, poi inserire il numero con prefisso internazionale, il codice ricevuto e l'eventuale password di verifica in due passaggi. L'app gestisce anche i passaggi e-mail richiesti da TDLib. Configura accesso avvia una nuova sessione, eliminando il precedente database Telegram; non è necessario premerlo a ogni avvio dopo un login riuscito.
4. Premere **Aggiorna elenco canali e gruppi**, cercare per nome e spuntare quelli da aggiungere ai preferiti. Per un canale su invito, unirsi prima tramite l'app Telegram ufficiale.

La registrazione di un nuovo account e gli eventuali passaggi di acquisto richiesti da Telegram devono essere completati nell'app ufficiale. La sessione viene riutilizzata agli avvii successivi finché resta valida.

## Utilizzo

Il menu segue questo ordine:

| Area | Operazioni principali |
|---|---|
| **Selezione** | Cambiare canale fra i preferiti toccandone il nome, leggere gli allegati, cercarli e scegliere Salva, Condividi o Salva su FTP |
| **Download** | Seguire i trasferimenti, aprire i file completati, selezionare, condividere, inviare a FTP o eliminare |
| **Filtri** | Assegnare regole ai preferiti, modificarle con azioni a icone e configurarle da esempi ricercabili |
| **Configura** | Gestire accesso, canali preferiti, cartella di lavoro, cache, FTP e politica sui download |

All'avvio, l'app apre Selezione quando sessione e canale preferito sono pronti; altrimenti apre Configura. Una riconnessione temporanea di Telegram, anche dopo il ritorno dal background, non blocca Selezione: è possibile consultare i metadati già letti mentre la connessione viene ripristinata. Sessione non valida o assenza del canale attivo richiedono Configura. Download e Filtri restano accessibili manualmente per le funzioni locali.

Flusso abituale:

1. In Selezione, toccare il nome del canale per sceglierne uno fra i preferiti, scegliere **Post pubblicati** (predefinito **Oggi**), poi premere **Leggi canale**. La ricerca per nome file si combina con la vista corrente senza perdere le checkbox dei file nascosti.
2. Verificare i risultati, scegliendo **Mostra solo selezionati**, **Mostra selezionati e Affini** oppure **Mostra tutti i file**. I selezionati sono sempre in alto, anche se selezionati manualmente; Affini indica le corrispondenze Possibili. La vista scelta viene conservata.
3. Modificare le checkbox e premere **Salva**, **Condividi** oppure **Salva su FTP**.
4. In Download sono disponibili i file completi della cartella di lavoro: condivisione, FTP, apertura ed eliminazione.

**Seleziona tutti** e **Deseleziona tutti** agiscono su tutti gli allegati letti del canale, anche quando ricerca e vista ne nascondono alcuni. In Download agiscono sull'intero elenco locale. I pulsanti Salva, Condividi e Salva su FTP usano l'intera selezione, non soltanto le righe visibili.

### Periodo di pubblicazione e annullamento della lettura

Il selettore **Post pubblicati** riguarda la data del messaggio Telegram, indipendentemente dalle date presenti nel nome del file:

| Opzione | Post letti |
|---|---|
| **Oggi** | Dal primo istante del giorno corrente alla mezzanotte successiva |
| **Tutti i file** | Intera cronologia accessibile |
| **Un giorno preciso** | Tutti i post della data scelta nel calendario |
| **Intervallo di date** | Dalla mezzanotte iniziale alla fine dell'ultimo giorno scelto, inclusi entrambi i giorni |

Si usa il fuso orario del telefono, rispettando anche i cambi di ora legale. Oggi viene ricalcolato quando si preme Leggi canale. Il periodo scelto rimane nella sessione dell'app; una nuova sessione parte da Oggi. Cambiarlo non altera gli allegati già mostrati: per leggere il nuovo periodo premere nuovamente Leggi canale. Il calendario consente date dal 1970 al 2037, compatibili con i timestamp TDLib; l'intervallo invertito disabilita la lettura e mostra un errore.

Per i periodi delimitati TgFM usa `getChatMessageByDate` per raggiungere l'ultimo post entro la data finale, poi legge a ritroso e si ferma al primo post precedente alla data iniziale. Non percorre tutta la cronologia per filtrarla successivamente. La lettura riguarda soltanto i metadati, senza avviare download.

I risultati vengono pubblicati a ogni pagina ricevuta e vengono applicate le regole del canale attivo. **Annulla operazione** interrompe le richieste successive mantenendo i nomi, i riferimenti e le selezioni automatiche già raccolti: la dicitura **Lettura parziale** distingue il risultato da una lettura completata. I risultati restano utilizzabili per selezione e download. Una nuova lettura sostituisce l'elenco; il cambio canale lo azzera. I metadati sono conservati in memoria, senza introdurre un archivio persistente o migrazioni dei dati.

## Canali e gruppi preferiti

L'aggiornamento enumera tutte le pagine TDLib della lista principale e di quella archiviata e include canali, supergruppi e gruppi di base; le chat personali sono escluse. Il valore 100 è la dimensione di una pagina di caricamento, non il numero massimo di preferiti o risultati. L'elenco completo viene ordinato per nome e i duplicati vengono rimossi.

In Configura le checkbox aggiungono o rimuovono i preferiti. Il canale attivo può essere cambiato toccandone il nome in Selezione. Cambiare canale azzera i risultati precedenti, che vanno riletti con Leggi canale; si applicano soltanto i filtri assegnati al canale attivo.

Ogni regola nuova o modificata deve essere assegnata a un preferito nell'editor. Le proposte da esempi sono assegnate al canale dal quale sono stati letti gli allegati. Regole uguali su canali diversi rimangono distinte.

Rimuovere un preferito cancella anche i filtri associati a quel canale. Se ci sono filtri, compare una sola conferma con il loro numero; annullandola non cambia nulla. Senza filtri associati la rimozione è immediata. I file già scaricati non vengono eliminati. Se si rimuove il canale attivo viene scelto il primo preferito rimasto; senza preferiti si apre Configura.

Rimane la compatibilità con le preferenze delle versioni precedenti ai preferiti multipli: il vecchio canale selezionato diventa un preferito e riceve le regole precedenti. Eventuali regole senza un vecchio canale vengono conservate come Da assegnare e possono essere associate dall'editor. Questo comportamento già presente riguarda le preferenze, non il recupero della sessione Telegram.

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

La cartella di lavoro è esterna allo spazio privato dell’app. Il percorso predefinito è `Download/TgFM`; **Configura → Scegli cartella** permette di scegliere un’altra cartella locale, anche su un volume rimovibile accessibile. Non sono supportate cartelle cloud come destinazione diretta di TDLib. Android 11 e successivi richiedono **Accesso a tutti i file**, perché TDLib deve accedere al percorso reale; il solo permesso del selettore documenti non basta. Su Android 8–10 serve il permesso di scrittura nella memoria condivisa.

I file completi risiedono nella cartella scelta. TDLib scrive in `.tgfm-cache/tdlib` al suo interno; al salvataggio il file completo viene rinominato sullo stesso filesystem, senza una seconda copia completa. Il filesystem deve consentire questa operazione e file della dimensione richiesta: FAT32, ad esempio, non supporta file da 4 GiB o superiori. Nessun fallback allo spazio privato o a una copia integrale aggiuntiva viene eseguito.

La versione 1.5.2 non esegue recuperi o migrazioni della sessione Telegram né pulizie del precedente archivio privato degli allegati. Un nuovo accesso Telegram si configura dal relativo modulo; filtri e file della cartella esterna sono separati dalla sessione. La compatibilità delle vecchie preferenze è descritta nella sezione sui canali preferiti.

Cambiare cartella chiude e riapre TDLib, senza spostare o eliminare i file della precedente cartella esterna. La cache può essere eliminata esplicitamente con **Svuota cache temporanea**, a trasferimenti fermi; non elimina i file definitivi.

In Configura, **Cancella i download precedenti** riguarda Salva e Condividi dalla Selezione:

| Impostazione | Comportamento |
|---|---|
| Disattivata — valore iniziale | Riutilizza l’allegato già salvato e riconosciuto; conserva gli altri file e sostituisce gli altri omonimi all’avvio del download |
| Attivata | Elimina prima i file gestiti da TgFM nella cartella attuale; gli altri file e sottocartelle non vengono svuotati |

FTP non applica questa pulizia generale. La sovrascrittura degli omonimi è distruttiva: un errore può far perdere il precedente. Non vengono conservati backup. I nomi sono sanitizzati; omonimi dello stesso batch ricevono nomi distinti. Un massimo di quattro download può procedere contemporaneamente, coordinando gli allegati con lo stesso file ID TDLib.

## Condivisione e salvataggio

**Salva**, nella pagina Selezione, scarica i file direttamente nella cartella di lavoro. **Condividi** esegue prima lo stesso salvataggio (riutilizzando i file già disponibili), quindi apre la condivisione Android. Se alcuni salvataggi falliscono, la condivisione non parte automaticamente: è possibile riprovare o condividere i completati dalla pagina Download.

La condivisione usa `ACTION_SEND`/`ACTION_SEND_MULTIPLE`, URI `content://` e permessi temporanei di sola lettura. Il provider espone soltanto file completi direttamente nella cartella di lavoro, escludendo cache e dati privati. Non crea una cartella di preparazione né una copia aggiuntiva; l’app destinataria può fare proprie copie e imporre limiti di dimensione, numero e formato. Il ritorno a TgFM non certifica l’invio al destinatario.

In Download restano **Condividi**, **Salva su FTP**, l’occhio per aprire e i cestini per eliminare un file o la selezione. Non è più presente il precedente Salva/spostamento. Le eliminazioni locali non modificano Telegram o il server FTP.

### Salva su FTP

Configurare host, porta, utente, password e cartella remota già esistente. È usata la modalità passiva, con FTP binario oppure **FTPS esplicito (TLS)**. FTPS verifica catena e nome del certificato; non accetta automaticamente certificati non attendibili. FTP semplice non cifra credenziali e contenuto. **Verifica accesso** controlla login e cartella; non crea file remoti per provare la scrittura.

| Conserva al termine | Comportamento |
|---|---|
| Spento — predefinito | Un file per volta. Dopo la conferma FTP viene eliminata la copia locale, anche se proveniva da Download |
| Acceso | Il file rimane nella cartella di lavoro; fino a quattro trasferimenti contemporanei |

Da Download si inviano i file esistenti. Da Selezione si riutilizza l’allegato già salvato quando disponibile; altrimenti parte il download TDLib e l’upload può cominciare prima che sia terminato. Un singolo flusso FTP `STOR` legge soltanto i byte contigui confermati da `getFileDownloadedPrefixSize`, aspettando quando non sono ancora disponibili. La dimensione apparente del file parziale non viene usata come indicazione dei byte scaricati. Il buffer di lettura/invio è da 256 KiB; non si caricano interi file in RAM.

Il download resta **integrale sul dispositivo**: l’invio anticipato può sovrapporre ricezione e trasmissione, ma non riduce lo spazio necessario per il file completo. Con Conserva attivo serve spazio per tutti i nuovi file selezionati. Sono controllati lo spazio dichiarato disponibile e un margine di 16 MiB; altri processi possono comunque consumare spazio durante l’operazione.

La copia locale viene eliminata soltanto dopo risposta finale positiva del server e verifica della dimensione, se il server supporta `SIZE`. Non basta aver scritto i byte nel socket. Non è prevista verifica hash remota universale. Un errore o annullamento conserva il file completo o il parziale nella cache; con Conserva disattivato la coda si arresta al primo errore per non accumulare altri file. La cache si elimina manualmente da Configura. Non ci sono riprese, recuperi o migrazioni automatici al riavvio.

I nomi omonimi sul server sono sovrascritti direttamente. Un errore può lasciare un file remoto incompleto e perdere il vecchio omonimo; non vengono introdotti backup o nomi temporanei remoti. La velocità dipende da Telegram, server e rete; l’invio sovrapposto non garantisce un’accelerazione su ogni connessione.

## Dati locali e sicurezza

- API ID/hash, chiave del database e configurazione FTP sono cifrati con AES-GCM e Android Keystore.
- Il database della sessione TDLib è cifrato e rimane privato; codici di accesso e password 2FA non vengono salvati dall’app.
- Canali, regole e preferenze restano nello spazio privato.
- Allegati salvati e cache TDLib sono nella cartella esterna; sono quindi accessibili anche tramite un gestore file autorizzato.
- La condivisione concede sola lettura ai file scelti e non espone database o cache.
- Backup e trasferimento automatico dei dati Android sono disabilitati.
- Un servizio foreground accompagna i trasferimenti avviati dall’utente. La normale ricreazione dell’Activity mantiene lo stato; una terminazione del processo non riavvia automaticamente le operazioni.

## Architettura

```text
app/src/main/
├── java/it/tgfm/
│   ├── core/         Modelli, filtri, nomi, navigazione e parallelismo
│   ├── data/         DataStore, Keystore, cartella esterna, FTP e condivisione
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

I branch sorgente contengono i file necessari alla compilazione dell'app e gli avvisi di licenza. APK, cache, log, rapporti, suite e strumenti locali di sviluppo/test sono esclusi; i pacchetti compilati sono nel solo branch `develop-apk`.

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

Per la **1.5.2** sono passati 9 test mirati su inizializzazione, permessi e schermata del nuovo accesso, oltre a una prova con TDLib nativa della stessa revisione inclusa nell’app: il vecchio ordine riproduce il blocco, il client corretto segnala parametri API non validi e, con parametri sintetici, raggiunge `authorizationStateWaitPhoneNumber` su database nuovo. Questa prova gira su Linux con il client Kotlin di produzione e non autentica un account Telegram. Build debug, Lint (0 errori, 20 avvisi), firma e allineamento APK verificati; firma invariata. Le suite precedenti non sono state rieseguite.

Come riferimento storico, la **1.5.0** aveva superato 157 test locali JUnit/Robolectric/Compose, la build debug e Android Lint (0 errori, 18 avvisi). Erano state verificate firma e allineamento dell’APK. Le prove FTP usavano un server locale reale con download simulato progressivo: invio prima del completamento, integrità SHA-256, rinomina del file nativo, contatori oltre 4 GiB, risposta finale negativa, dimensione remota errata, cancellazione, file vuoti e nomi UTF-8. Comprendevano conservazione/eliminazione dopo FTP, protezione della cache nel provider, filtri, preferiti, lettura per periodo e la pulizia legacy successivamente rimossa nella 1.5.2. Queste prove non avevano rilevato il blocco di inizializzazione TDLib corretto nella 1.5.2.

Suite, rapporti e strumenti di test sono locali ed esclusi dal repository come richiesto; i controlli riproducibili dalla checkout pubblicata sono build e `lintDebug`. Le prove con file progressivi simulati non sostituiscono la verifica del trasferimento TDLib reale su Android.

Rimangono da verificare sul dispositivo fisico e con account reali:

- Login, 2FA, ripristino della sessione e lettura di un canale privato.
- Su canali con cronologie lunghe: Oggi, giorno preciso, intervallo e Tutti i file; tempi reali della ricerca Telegram, confini dei giorni e annullamento seguito da download dei risultati parziali.
- Elenco effettivo con oltre 100 canali/gruppi, inclusi quelli archiviati; uso del calendario tramite tocco sul telefono.
- Download Telegram paralleli, velocità, annullamento e comportamento con rete o spazio insufficienti.
- Interfaccia e gestione dei trasferimenti in background sul Galaxy S23 Ultra.
- Condivisione effettiva con WhatsApp e altri destinatari.
- Autorizzazione alla memoria, selettore Samsung, cartella su SD/USB, rinomina e condivisione esterna.
- Pipeline TDLib reale → FTP/FTPS, file fino a 4 GB, integrità sul server, timeout, interruzioni, spazio e velocità sul telefono.
- FTPS con certificato valido e comportamento del server FTP personale.

Sono supportati un account, più canali preferiti con filtri associati e un canale attivo alla volta. La scansione simultanea di più canali non è presente in questa versione.

## Licenze di terze parti

- **TDLib:** [Boost Software License 1.0](docs/licenses/TDLib-Boost-1.0.txt).
- **OpenSSL:** [Apache License 2.0](docs/licenses/OpenSSL-Apache-2.0.txt).
- **SQLite/SQLCipher incluso in TDLib:** [avviso BSD di ZETETIC LLC](docs/licenses/SQLite.txt).
- **Apache Commons Net 3.12.0:** [Apache License 2.0 e avviso](docs/licenses/Commons-Net.txt).
- **AndroidX, Jetpack Compose, Kotlin e coroutines:** rispettive licenze e avvisi upstream; dipendenze dichiarate nella configurazione Gradle.

I nomi Telegram e WhatsApp identificano servizi di terze parti. TgFM è un progetto indipendente.
