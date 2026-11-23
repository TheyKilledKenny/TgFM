# TgFM 1.5.8 | Specifiche funzionali e tecniche

Il documento descrive la build di sviluppo **1.5.8, codice Android 17**, comprensiva delle immagini profilo e delle anteprime nei dettagli. TgFM usa l’account Telegram personale per leggere gli allegati di canali e gruppi, scegliere quelli desiderati e salvarli, condividerli o inviarli a un server FTP. Le operazioni si svolgono sul dispositivo.

## Accesso e navigazione

L’APK supporta Android 8 e successivi su ARM64. Al primo avvio l’utente concede l’accesso ai file e configura API ID e API hash di Telegram. TDLib gestisce la sessione e richiede numero telefonico, codice e, quando previsto dall’account, password di verifica o verifica e-mail. Un nuovo accesso esplicito avvia una nuova sessione. Le impostazioni dell’app consentono di seguire le lingue di Android o di scegliere italiano o inglese. Il fallback è l’inglese.

| Sezione | Funzioni |
|---|---|
| Selezione | Cambio del canale preferito, periodo dei post, lettura, ricerca, selezione, dettagli e comandi sui file |
| Download | Elenco della cartella di lavoro, apertura, condivisione, invio FTP ed eliminazione singola o multipla |
| Filtri | Regole per canale, gruppi comprimibili, modifica manuale e proposta da esempi |
| Configura | Lingua, accesso Telegram, preferiti, cartella di lavoro, cache temporanea, archivi e server FTP |

Con sessione valida e preferito attivo l’app apre Selezione. In assenza dei prerequisiti apre Configura. Una riconnessione temporanea permette di consultare gli allegati già letti.

## Canali, gruppi e lettura

TgFM enumera le liste Telegram principale e archiviata per pagine, include canali, gruppi di base e supergruppi e li identifica tramite ID Telegram. Ogni preferito conserva titolo, profilo e associazioni ai filtri. La rimozione elimina le regole del canale e richiede una conferma se ne esistono. In Configura si può cercare per nome e toccare la foto con la spunta per cambiare lo stato di preferito. In Selezione il nome e la foto aprono il cambio canale. Se manca una foto l’app mostra l’iniziale.

| Preferenza del canale | Valori e applicazione |
|---|---|
| Foto di copertina | Spente inizialmente per ogni preferito, anche aggiornando da una versione con l’opzione globale attiva |
| Periodo iniziale | Oggi, Ultimi 7 giorni oppure Tutti i file. Il periodo predefinito è Oggi |
| Invio archivi | Usa scelta generale, Archivio originale o Contenuto archivio. L’utente può modificare la scelta per il singolo invio |

Durante una lettura si possono scegliere anche un giorno preciso o due date inclusive. Il calcolo usa i giorni di calendario del dispositivo. Gli ultimi sette giorni includono oggi e i sei giorni precedenti. Per i periodi delimitati il lettore raggiunge la fine del periodo nello storico Telegram e procede a ritroso fino al limite iniziale. Pubblica i risultati per pagina. Annullando la lettura, le pagine già ricevute restano consultabili. Cambiando canale si svuota l’elenco. La memoria persistente degli intervalli già letti è prevista per una fase successiva.

## Selezione, copertine e dettagli

I risultati distinguono documenti, foto, audio, video, sticker e animazioni. La ricerca restringe la vista per nome. Le tre viste mostrano rispettivamente i selezionati, i selezionati con gli affini oppure tutti i file. I selezionati precedono gli altri. Il comando Seleziona tutti opera sull’intero elenco idoneo del canale, compresi gli allegati nascosti dalla vista o dalla ricerca.

Con le copertine attive, la foto nel messaggio immediatamente precedente può illustrare il documento, l’audio o il video successivo. La foto usata come copertina esce dall’elenco selezionabile. Toccando la copertina si cambia la selezione, mentre il resto della riga apre i dettagli. Un file privo di copertina usa la checkbox nella stessa colonna. I dettagli mostrano i metadati disponibili, la corrispondenza al filtro e le didascalie.

Per un allegato immagine i dettagli mostrano anche l’immagine stessa, indipendentemente dalla preferenza copertine. TgFM può leggere la copia già salvata oppure una miniatura Telegram. Questo vale anche per immagini inviate come documenti, per esempio JPEG, PNG, WebP e GIF. La vista mostra un avviso quando manca un’anteprima utilizzabile.

## Regole e proposte da esempi

Ogni regola attiva è di inclusione o esclusione. Può combinare modello del nome, testo contenuto, estensioni, MIME esatto o per famiglia e categoria Telegram. I criteri compilati devono valere tutti. Il modello si applica all’intero nome senza estensione, senza distinzione tra maiuscole e minuscole. L’unico segnaposto è `{data}`. Per esempio, `La_Gente_{data}` accetta `La_Gente_14_Novembre_2026.pdf` e l’omonimo EPUB, mentre una variante con altre parole richiede una regola diversa. Lasciare vuote le estensioni ammette tutti i formati compatibili con gli altri criteri.

Le esclusioni prevalgono sulle inclusioni. Una corrispondenza certa viene selezionata automaticamente. Un affine è un suggerimento per la scelta manuale. La selezione manuale vale per l’operazione corrente. Nella configurazione da esempi l’utente cerca e contrassegna file desiderati e indesiderati. Il motore cerca modelli comuni basati sulle date riconosciute, segnala proposte ambigue e mostra i file che verrebbero selezionati. Il salvataggio richiede conferma. Per includere tutti i PDF si può creare direttamente una regola con la sola estensione `pdf`.

## Salvataggio e condivisione

La cartella di lavoro predefinita è `Download/TgFM`. Si può scegliere una cartella locale scrivibile, anche su un volume rimovibile. Android 11 e successivi richiedono l’accesso speciale a tutti i file. TDLib usa la sottocartella `.tgfm-cache/tdlib`. Il salvataggio sposta il file completo nella destinazione sullo stesso filesystem. Cambiare cartella lascia i file nella posizione precedente. Svuota cache rimuove i dati temporanei dei trasferimenti.

Salva avvia fino a quattro download paralleli. Se la pulizia preventiva è attiva, Salva e Condividi dalla Selezione eliminano prima i file gestiti nella cartella corrente. Con la conservazione attiva, TgFM può riutilizzare una copia dello stesso allegato. Un omonimo sostituito all’avvio del download può andare perso in caso di errore. I file completati restano disponibili in Download.

Condividi dalla Selezione salva i file e apre il selettore Android. Da Download opera sui file esistenti. Le app destinatarie ricevono URI con permessi temporanei di sola lettura, tramite `ACTION_SEND` o `ACTION_SEND_MULTIPLE`. L’apertura e l’accettazione di più file dipendono dalle app installate. L’eliminazione in Download riguarda le copie locali.

## Archivi e FTP

I volumi riconosciuti, tra cui RAR numerati e volumi ZIP o 7z, sono raggruppati prima dei filtri e compaiono come una voce. Salva conserva gli originali. In caso di collisione di nomi con archivi esistenti l’app può proporre un nome alternativo modificabile. Per Condividi e Salva su FTP l’utente conferma una scelta comune fra originali e contenuto. In Download, se le preferenze degli archivi selezionati sono diverse, la proposta usa la scelta generale.

La lettura del contenuto richiede i volumi disponibili e un formato supportato dal lettore. Il provider Android espone i singoli file dell’archivio in lettura e il client FTP può inviarne i contenuti in flusso. Entrambi leggono dagli archivi completi sul telefono, senza una seconda copia estratta. Per la condivisione l’archivio deve restare disponibile durante la lettura. Un archivio cifrato può essere inviato come originale.

La configurazione FTP comprende host, porta, credenziali, cartella base, FTP o FTPS esplicito e conservazione locale. Il trasferimento usa la modalità passiva e binaria. La conferma consente di aggiungere una sottocartella relativa e cambiare la conservazione per il singolo invio. Per i file ordinari idonei, ricezione da Telegram e invio FTP possono sovrapporsi. Occorre comunque spazio locale per il file completo.

Con Conserva spento la coda elabora un file alla volta e cancella la copia locale dopo la conferma del trasferimento. Con Conserva acceso la copia rimane sul dispositivo. Un errore può lasciare un file parziale sul server. Quando l’app propone una pulizia remota dopo un errore di invio degli archivi, la cancellazione richiede conferma.

## Implementazione e dati

Il progetto usa Kotlin, Jetpack Compose e Material 3. Il client Telegram usa TDLib tramite JSONJava e libreria nativa ARM64. Android identifica l’app con `it.tgfm`. DataStore conserva preferenze, profili e regole. Le credenziali usano cifratura con Android Keystore. La sessione TDLib, la cache di trasferimento e i file definitivi hanno cicli di vita distinti. I trasferimenti avviati dall’utente usano un servizio in primo piano. Apache Commons Net gestisce FTP/FTPS, libarchive e Zip4j la lettura degli archivi. Le istruzioni di compilazione e le licenze sono nel [README italiano](../README_IT.md).
