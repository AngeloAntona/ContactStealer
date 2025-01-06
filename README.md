# **1. Struttura Generale del Progetto**

## **Scopo Principale**

Il progetto consiste nello sviluppare due applicazioni distinte in grado di *esfiltrare* i contatti della rubrica da un telefono. L’obiettivo è dimostrare un canale di comunicazione “covert” (nascosto) che non insospettisca troppo l’utente, distribuendo i permessi sensibili tra le due app:

- **App1 (Trasmettitore)**: richiede il permesso di lettura dei contatti e invia la rubrica attraverso segnali BFSK a ultrasuoni.
- **App2 (Ricevitore)**: richiede il permesso di utilizzo del microfono e, una volta decodificati i segnali, invia i contatti estratti su Google Sheets (o altra destinazione).

In tal modo, non esiste un’unica app che abbia sia l’accesso ai contatti sia la possibilità di inviarli su Internet: l’esfiltrazione è realizzata tramite un canale audio ultrasonico, e la seconda app ha solo il permesso del microfono (e di conseguenza può inviare i dati in rete senza apparire “sospetta” in termini di permessi contatti).

---

# **2. Descrizione dei Protocolli e delle Precauzioni di Comunicazione**

## **2.1 Protocollo BFSK (Binary Frequency-Shift Keying)**

- **BFSK** è una modulazione che utilizza due frequenze diverse per rappresentare rispettivamente il bit `0` e il bit `1`.
- Nel codice, le frequenze prescelte sono `freq0 = 20000 Hz` e `freq1 = 20500 Hz`, entrambe nella fascia ultrasonica, non udibili dalla gran parte degli esseri umani.
- Per ogni bit, il trasmettitore genera un breve tono sinusoidale di durata prestabilita (ad es. 100 ms), modulato sulla frequenza corrispondente.

## **2.2 Ridondanza e Voto di Maggioranza**

- Ogni bit viene ripetuto 3 volte (tecnica di **repetitions**), così da ridurre errori di decodifica dovuti a disturbi ambientali.
- Sul lato ricevitore, si usa un buffer triplo con **voto di maggioranza** (*majority voting*) per ricostruire il bit originale.

## **2.3 Precauzioni per Migliorare la Trasmissione**

- **Fade In / Out**: In trasmissione, ogni tono include un *fade-in* e *fade-out* (5 ms) per ridurre i “click” e i transienti bruschi.
- **Pausa fra i contatti**: Tra la trasmissione di un contatto e il successivo si introduce una pausa (delay 1000 ms) per evitare sovrapposizioni e disturbi.
- **Filtri BandPass**: Nel ricevitore, si applicano filtri passa-banda centrati sulle due frequenze per migliorare l’estrazione del segnale BFSK e ridurre il rumore.

---

# **3. Descrizione di App1 (Trasmettitore)**

## **3.1 Struttura Generale**

- **Package**: `com.example.contactreader`
- **Permessi**: Richiede la *READ_CONTACTS* per accedere ai contatti del dispositivo.
- **Funzionalità Principale**: Legge tutti i contatti della rubrica, quindi li trasmette uno dopo l’altro in formato BFSK (ultrasuoni).

## **3.2 Classi Principali**

1. **MainActivity**
   - Gestisce la UI con Jetpack Compose:
     - Un pulsante `Avvia Trasmissione` (`onClickStart`) inizia il processo di invio.
     - Un pulsante `Ferma Trasmissione` (`onClickStop`) interrompe la trasmissione.
   - Richiede il permesso *READ_CONTACTS* se non è stato già concesso.
   - Esegue un job in coroutine (`Dispatchers.Default`) per trasmettere i contatti in loop, uno alla volta.
   - Mostra lo stato (`info`) su schermo.

2. **ContactReader (Singleton object)**
   - Classe di supporto che legge la rubrica tramite le API di Android (`ContactsContract`).
   - Ritorna una lista di stringhe in formato `"Nome:Numero"`.

3. **BFSKTransmitter (Singleton object)**
   - Contiene la logica di trasmissione BFSK:
     - Frequenze usate: 20 kHz (bit `0`), 20.5 kHz (bit `1`).
     - Ripetizione dei bit (3 volte) e *fade in/out* di 5 ms.
   - **Funzioni principali**:
     - `transmitSingleContact(contact: String)`: crea la frame BFSK (preambolo + lunghezza + payload + suffisso), ripete i bit, quindi genera i toni e li invia usando un `AudioTrack`.
     - `buildFrame(...)`: converte una stringa in bit, aggiunge preambolo e suffisso BFSK.
     - `playBitString(...)`: per ogni bit scrive i dati su `AudioTrack`.
     - `generateToneWithFade(...)`: genera l’array di `Short` per un singolo tono con *fadeIn/fadeOut* per ridurre i click audio.

## **3.3 Tecnologie Usate in App1**

- **Kotlin + Coroutines** per gestire la trasmissione in background.
- **Jetpack Compose** per l’interfaccia utente (pulsanti, testo informativo).
- **API Android Contacts** per leggere la rubrica.
- **AudioTrack** (API Android) per l’uscita audio BFSK.

---

# **4. Descrizione di App2 (Ricevitore)**

## **4.1 Struttura Generale**

- **Package**: `com.example.contactsender`
- **Permessi**: Richiede la *RECORD_AUDIO* per poter ascoltare i suoni tramite il microfono.
- **Funzionalità Principale**: Riceve i segnali BFSK, decodifica i contatti e li invia a un file di Google Sheets utilizzando credenziali OAuth (service account).

## **4.2 Classi Principali**

1. **MainActivity**
   - UI con Jetpack Compose:
     - Un pulsante `Start Listening` (`onStartListening`) che avvia la ricezione BFSK.
     - Un pulsante `Stop Listening` (`onStopListening`) che ferma la registrazione.
   - Inizializza `SheetsHelper` all’avvio per preparare le credenziali e la connessione al foglio Google.
   - Mostra i contatti decodificati e l’eventuale stato di ricezione in un testo (`info`).

2. **BFSKReceiver**
   - Si occupa di gestire un `AudioRecord` per ascoltare i segnali BFSK dal microfono.
   - Usa un `BFSKDecoder` per decodificare i bit e applica la logica di *majority voting* (3 bit -> 1 bit).
   - Riconosce la frame BFSK (preambolo `10101010`, lunghezza, payload, suffisso `11110000`), ricostruisce la stringa `Nome:Numero`.
   - Chiama `onContactReceived(...)` quando ha estratto un contatto completo.

3. **BFSKDecoder**
   - Filtra l’audio in streaming con due band-pass (`BandPassFilter`) centrati sulle frequenze BFSK.
   - Calcola l’energia in ciascun canale e decide se sia `0` o `1` per ogni bit.
   - Ritorna una stringa di bit, che poi viene gestita dal ricevitore per estrarre i contatti.

4. **BandPassFilter**
   - Classe che implementa un filtro passa-banda (biquad semplificato) per isolare la frequenza 20 kHz e 20,5 kHz, aumentando l’SNR e migliorando la qualità della decodifica.

5. **SheetsHelper**
   - Si occupa di autenticare e inviare i dati su Google Sheets:
     - Carica da `assets/credentials.json` le credenziali di un account di servizio.
     - Inizializza l’oggetto `Sheets` (Google API) per scrivere i dati.
     - Con `appendRowToSheet(contact: String)`, inserisce una riga (Nome, Numero, Timestamp) nel foglio `"Foglio1"` (colonne A, B, C).

## **4.3 Tecnologie Usate in App2**

- **Kotlin + Coroutines** per gestire in background la registrazione audio e l’invio a Sheets.
- **AudioRecord** (API Android) per catturare i segnali BFSK dal microfono.
- **Google Sheets API** per salvare i contatti nel foglio.
- **Jetpack Compose** per i componenti dell’interfaccia utente.

---

# **Conclusioni**

Attraverso queste due app, i contatti vengono esfiltrati dal telefono in maniera discreta:

1. **App1** ha il permesso di leggere la rubrica ma non chiede permessi di rete; trasmette i contatti via *ultrasuoni* BFSK.
2. **App2** cattura i suoni via microfono (unico permesso richiesto) e invia i dati verso Google Sheets.

Così, l’utente non vede mai un’unica app che abbia sia il permesso di leggere i contatti sia di utilizzare la rete, riducendo il sospetto. Le tecniche BFSK e i filtri band-pass assicurano la decodifica accurata, mentre un meccanismo di ripetizione dei bit e *majority voting* gestisce errori e rumori ambientali.