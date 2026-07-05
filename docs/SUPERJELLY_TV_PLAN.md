# SuperJelly TV — piano di modifica contestuale

## Obiettivo

Partire dall’APK Android TV di Jellyfin e trasformarlo in una home TV intelligente, stile Netflix/Amazon, mantenendo il client leggero.

Il principio base è:

```text
Contenuti Jellyfin locali -> player interno Jellyfin.
Contenuti esterni protetti -> scheda nella home + apertura app ufficiale.
Live TV/M3U legale -> integrazione tramite Jellyfin Live TV quando possibile.
```

## Cosa è stato applicato in questa patch

### 1. Trailer preview mantenuti, ma ottimizzati

La preview trailer è una funzione importante e resta attiva. È stata però resa più prudente:

- non viene più svuotata la WebView a ogni movimento del telecomando;
- il trailer parte solo se la selezione resta stabile;
- i trailer già risolti vengono mantenuti in cache LRU;
- i titoli senza trailer vengono messi in cache negativa per non cercarli di continuo;
- viene riusato un solo `OkHttpClient`;
- le chiamate YouTube hanno timeout brevi;
- in caso di errori ripetuti i trailer vengono messi in pausa temporanea;
- durata massima trailer ridotta a 45 secondi per non consumare risorse inutilmente.

### 2. Home caricata in modo progressivo

La home ora privilegia le righe utili subito:

- Continua a guardare;
- Prossimi episodi;
- Aggiunti di recente;
- Consigliati;
- Librerie.

Le altre righe vengono aggiunte progressivamente con piccoli intervalli, evitando di far partire troppe richieste e layout nello stesso istante.

### 3. Live TV più sicura all’avvio

Il controllo Live TV ora ha un timeout breve e non deve bloccare tutta la home se il server, l’EPG o la sorgente M3U rispondono lentamente.

## Roadmap consigliata

### Fase 1 — Smart Home Jellyfin

- Righe intelligenti solo dalla libreria locale.
- Mood base: leggero, azione, breve, classico, non farmi scegliere.
- Scoring leggero: non visto, durata, anno, genere, lingua, qualità, direct play.
- Nessuna AI lato APK.

### Fase 2 — Smart API server-side

Creare un microservizio esterno, ad esempio `superjelly-smart-api`, che prepara righe già pronte per il client TV:

```text
APK Android TV -> /api/smart/home -> Smart API -> Jellyfin API
```

La TV deve solo renderizzare. Il server calcola.

### Fase 3 — Provider esterni

Aggiungere una struttura provider comune:

```text
JellyfinProvider
PlutoProvider
RaiPlayProvider
PrimeVideoProvider
GenericExternalProvider
```

Ogni card deve avere badge chiaro:

```text
[Jellyfin] [Pluto TV] [RaiPlay] [Prime Video] [Live] [On Demand] [Gratis]
```

### Fase 4 — Pluto TV

- Pluto Live: possibile tramite Live TV/M3U se la sorgente è legale e stabile.
- Pluto On Demand: catalogo esterno + apertura app ufficiale Pluto TV.
- Playback interno On Demand: non MVP, solo valutazione separata.

### Fase 5 — RaiPlay e Prime Video

- Schede nella home.
- Apertura app ufficiale.
- Fallback browser/Play Store.
- Nessun salvataggio credenziali.
- Nessun bypass DRM.

### Fase 6 — Ricerca unica

Una ricerca deve ordinare così:

1. contenuto locale Jellyfin;
2. contenuto gratuito esterno;
3. contenuto incluso in provider esterno;
4. noleggio/acquisto o apertura app.

## Cose da evitare

- Login Prime/RaiPlay dentro il client;
- salvataggio password;
- estrazione stream protetti;
- bypass DRM;
- AI locale nell’APK;
- autoplay trailer aggressivo;
- caricamento simultaneo di troppe righe.

## Prossimo passo tecnico

Creare i modelli `SmartItem`, `SmartProvider` e `ExternalProviderLauncher`, poi collegare una prima riga esterna dimostrativa con apertura app ufficiale.
