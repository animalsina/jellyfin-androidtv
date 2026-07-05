# SuperJelly TV — Jellyfin Android TV Smart Home Fork

SuperJelly TV è un fork sperimentale di **Jellyfin Android TV** orientato a una home più moderna, più vicina all'esperienza Netflix/Prime Video, ma senza perdere il principio fondamentale di Jellyfin: il server resta il cuore della libreria locale e il client Android TV deve rimanere leggero.

Questa versione parte dalla UI modernizzata già presente nel fork e aggiunge una prima ottimizzazione concreta per rendere più sostenibile la navigazione su dispositivi Android TV, soprattutto quando sono attive preview trailer, righe di contenuti numerose e controlli Live TV.

> Il progetto non prova a trasformare Jellyfin in un player universale per servizi protetti come Prime Video, RaiPlay o Pluto TV On Demand. L'obiettivo corretto è una **home intelligente unificata**: i contenuti locali partono nel player Jellyfin, mentre i contenuti esterni vengono rappresentati come schede e aperti tramite app ufficiale o deep link quando possibile.

---

## Cosa cambia rispetto al client Jellyfin Android TV originale

Questo fork mantiene le fondamenta del client Android TV ufficiale, ma introduce una direzione prodotto diversa:

- home più visiva e cinematografica;
- righe in stile streaming moderno;
- anteprime grandi quando si seleziona un contenuto;
- trailer automatici opzionali durante lo scorrimento;
- caricamento progressivo della home;
- attenzione specifica alle prestazioni su Android TV;
- roadmap per una futura Smart Home con mood, scoring e provider esterni.

Il player Jellyfin non è stato riscritto: la riproduzione dei contenuti locali deve restare il più possibile affidata alla logica Jellyfin esistente.

---

## Modifiche principali della versione v1.0.2

### README di progetto aggiornato

È stato sostituito il README generico precedente con un README coerente con lo stato reale del fork SuperJelly TV.

Il nuovo README documenta:

- obiettivo del progetto;
- differenze rispetto a Jellyfin Android TV originale;
- ottimizzazioni già applicate;
- comportamento delle preview trailer;
- architettura consigliata per la Smart Home;
- gestione dei provider esterni;
- limiti tecnici e legali da rispettare;
- istruzioni build e test;
- roadmap futura.

### Documentazione collegata

Il piano tecnico completo si trova in:

```text
./docs/SUPERJELLY_TV_PLAN.md
```

La documentazione originale del fork precedente, dove presente, è conservata in:

```text
./README_ORIGINAL.md
```

---

## Modifiche principali della versione v1.0.1

La versione precedente ha introdotto il primo intervento mirato sulla lentezza della home e delle preview trailer.

### Preview trailer ottimizzata

La preview trailer è stata mantenuta, perché è una funzione centrale per ottenere un'esperienza simile a Netflix o Prime Video, ma è stata resa più prudente.

Sono stati applicati questi principi:

- il trailer non deve partire immediatamente a ogni singolo movimento del telecomando;
- la selezione deve essere stabile prima di avviare la preview;
- la WebView non deve essere azzerata e ricreata continuamente;
- i risultati dei trailer già trovati devono essere riusati;
- i titoli senza trailer non devono generare ricerche ripetute;
- la rete non deve essere stressata con client HTTP creati a ogni richiesta;
- in caso di errori ripetuti, il sistema deve fare backoff temporaneo;
- la durata della preview deve essere ragionevole per non consumare troppe risorse.

Queste modifiche servono a mantenere l'effetto “trailer mentre scorri” senza trasformarlo nel principale collo di bottiglia dell'app.

### Home con caricamento progressivo

La home è stata resa più progressiva:

- le righe prioritarie vengono mostrate prima;
- le sezioni meno urgenti vengono aggiunte dopo;
- il controllo Live TV non deve bloccare tutta la home;
- la percezione di velocità migliora perché l'utente vede contenuti prima che ogni sezione sia completata.

---

## Principio di performance

Android TV non va trattata come un PC desktop.

Molti dispositivi TV hanno CPU deboli, poca RAM e WebView non particolarmente fluide. Per questo la logica di SuperJelly TV deve seguire queste regole:

```text
Client Android TV:
- rendering UI;
- focus telecomando;
- carousel e card;
- player Jellyfin;
- apertura app esterne;
- cache locale minima.

Backend / Smart API futura:
- scoring;
- mood;
- ranking;
- provider esterni;
- normalizzazione metadati;
- cache pesante;
- eventuale AI.
```

La TV deve mostrare risultati già pronti, non calcolare continuamente tutta la libreria.

---

## Strategia trailer

I trailer sono una funzione importante e non devono essere rimossi. Vanno però trattati come elemento progressivo e opzionale.

Comportamento consigliato:

```text
1. L'utente si sposta tra le card.
2. L'app aggiorna subito poster, backdrop e testi.
3. Solo se la selezione resta stabile per un breve intervallo, parte la ricerca trailer.
4. Se il trailer è già in cache, viene riusato.
5. Se il titolo non ha trailer, viene ricordato per evitare nuove ricerche inutili.
6. Se la rete o YouTube falliscono ripetutamente, le preview vengono sospese per un breve periodo.
```

Questo mantiene l'effetto premium, ma riduce blocchi e micro-lag durante lo scorrimento.

---

## Strategia Smart Home futura

La roadmap del progetto prevede di trasformare la home in una vera home intelligente.

Esempi di righe future:

```text
Continua a guardare
Scelti per stasera
Film recenti che non hai visto
Film passati da recuperare
Qualcosa di leggero
Film sotto i 100 minuti
Thriller ma non horror
Classici importanti
Alta qualità / Direct Play
Gratis da vedere ora
Disponibili su Pluto TV
Live TV in onda adesso
Disponibili su RaiPlay
```

Per evitare lentezza, queste righe dovrebbero essere preparate da un microservizio o modulo server-side, poi inviate al client come JSON già pronto.

---

## Provider esterni

La visione corretta per provider come Pluto TV, RaiPlay o Prime Video è questa:

```text
Jellyfin locale       → riproduzione interna nel player Jellyfin.
Live TV / M3U legale  → possibile riproduzione tramite Jellyfin Live TV.
Pluto On Demand       → scheda esterna + apertura app Pluto TV.
RaiPlay               → scheda esterna + apertura app RaiPlay.
Prime Video           → scheda esterna + apertura app Prime Video.
```

### Cosa evitare

Il progetto non deve implementare:

- salvataggio di password Prime Video, RaiPlay o altri servizi;
- bypass DRM;
- estrazione stream protetti;
- scraping aggressivo o fragile;
- player interno per servizi commerciali protetti;
- AI pesante dentro l'APK.

Queste scelte proteggono stabilità, distribuibilità e sostenibilità del progetto.

---

## Architettura consigliata

La direzione consigliata è:

```text
[SuperJelly TV APK]
        |
        | GET /api/smart/home
        v
[SuperJelly Smart API]
        |
        | Jellyfin API
        v
[Jellyfin Server]
        |
        | opzionale
        v
[Provider esterni]
- Pluto TV
- RaiPlay
- Prime Video
- altri provider futuri
```

L'APK deve poter funzionare anche senza Smart API, mostrando una home Jellyfin classica o una Smart Home ridotta.

---

## Build da sorgente

Requisiti consigliati:

- Android Studio aggiornato;
- JDK compatibile con il progetto;
- Android SDK installato;
- connessione internet per scaricare Gradle e dipendenze;
- dispositivo Android TV, emulatore Android TV o box compatibile.

Comandi:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

APK debug generato normalmente in:

```text
app/build/outputs/apk/debug/
```

---

## Test consigliati dopo ogni modifica

### Test home

- Avviare l'app su Android TV reale o emulatore TV.
- Verificare che la home appaia rapidamente.
- Scorrere velocemente tra righe e card.
- Controllare che il focus non si perda.
- Verificare che il tasto Back mantenga un comportamento prevedibile.

### Test trailer

- Selezionare un film con trailer probabile.
- Verificare che il trailer non parta immediatamente a ogni micro-spostamento.
- Restare fermi su una card e verificare l'avvio differito.
- Spostarsi rapidamente su più card e controllare che non ci siano blocchi evidenti.
- Testare una rete lenta o instabile.

### Test playback

- Aprire un film locale Jellyfin.
- Avviare la riproduzione.
- Tornare alla home.
- Verificare che la posizione di navigazione sia conservata dove possibile.

### Test Live TV, se configurata

- Verificare che l'eventuale controllo Live TV non blocchi l'intera home.
- Aprire un canale live.
- Tornare alla home.

---

## Roadmap sintetica

### MVP 1 — Home Jellyfin intelligente

- Home più veloce.
- Righe ordinate e progressive.
- Trailer ottimizzati.
- Player invariato.
- Nessun provider esterno obbligatorio.

### MVP 2 — Smart API

- Scoring lato server.
- Mood.
- Cache.
- Feedback utente.
- Righe già pronte per Android TV.

### MVP 3 — Provider esterni

- Pluto TV come catalogo esterno.
- RaiPlay come catalogo esterno.
- Prime Video come scheda esterna.
- Apertura app ufficiali.
- Ricerca unica.

### MVP 4 — Live TV

- Righe dedicate a canali live.
- M3U legali tramite Jellyfin Live TV.
- EPG se disponibile.

### MVP 5 — AI lato server

- Ricerca naturale.
- Righe dinamiche.
- Mood tag automatici.
- Spiegazione dei suggerimenti.

---

## Note per contributori

Quando si lavora su questo fork:

- non appesantire il thread UI;
- non fare chiamate di rete ripetute su ogni cambio focus;
- non bloccare la home in attesa di provider secondari;
- mantenere fallback se una funzione smart non risponde;
- testare sempre con telecomando/D-pad;
- preferire cache e caricamento progressivo;
- evitare modifiche premature al player.

---

## Crediti

SuperJelly TV nasce sopra il lavoro della community Jellyfin e del client Android TV originale. Questo fork sperimenta una direzione più orientata alla scoperta intelligente dei contenuti, mantenendo Jellyfin come base open source e server principale per la libreria personale.
