# Changelog

## v1.0.27 - 2026-07-06

- Rimosso il nuovo hero carousel separato introdotto nella versione precedente: la home torna al comportamento precedente, dove il blocco grande in alto segue il focus delle card nelle righe.
- Rimossa l'impostazione del hero carousel dedicato, così la configurazione home torna a mostrare solo le righe.
- Projectivy/Android TV launcher pubblica tutte le righe filtrate disponibili nel programma, non solo quelle già selezionate nella home.
- Aggiunte nuove righe configurabili, non attive di default, e pubblicabili su Projectivy: film casuali, serie casuali, film mai visti a caso e film che non vedi da tanto.
- Projectivy crea anche canali per i principali generi disponibili, così puoi avere righe dedicate per azione, commedia, drama, horror, fantascienza, thriller e altri generi.

## v1.0.26 - 2026-07-06

- Il blocco superiore della home diventa un vero hero carousel fisso e indipendente dalle righe: pesca contenuti casuali, ruota automaticamente e mantiene azioni Riproduci/Dettagli.
- I trailer nel hero vengono caricati in anticipo e appaiono in fade solo dopo 4 secondi di permanenza sul contenuto e almeno 3 secondi di preload.
- Le righe della home non pilotano più il carousel quando il carousel hero è attivo, così le righe restano semplici elenchi navigabili.
- Aggiunte impostazioni dedicate per abilitare/disabilitare il hero carousel.
- Aggiunti nuovi filtri/sezioni home per Pluto TV per genere e RaiPlay Film/Serie.
- Il catalogo esterno ora divide Pluto TV e RaiPlay in più righe per categoria quando disponibili.
- Le copertine dei contenuti esterni vengono prese dal feed quando presenti e, per Pluto, arricchite tramite metadati pubblici per ridurre i placeholder generici.
- Projectivy/Android TV riceve anche canali filtrati per categoria/provider, così può mostrare molte più righe disponibili.

## v1.0.25 - 2026-07-06

- La home evita il rebuild completo quando torni indietro: mantiene righe e copertine già caricate, aggiorna solo dopo scan libreria o dopo una finestra di refresh morbido.
- Il primo frame della home è più leggero: carica subito solo le prime righe utili, rimanda le altre, rimuove il probe Live TV bloccante e aumenta la cache delle card.
- Le copertine in home non decodificano più BlurHash per ogni card e rispettano davvero il crossfade disattivato, riducendo flicker e micro-lag durante lo scroll.
- Il tasto Back fisico torna alla schermata precedente prima di proporre l’uscita dall’app, così entrando in una scheda dalla home puoi rientrare correttamente alla home.
- Il blocco superiore diventa un carousel hero in stile streaming: ruota automaticamente sui contenuti più interessanti, si ferma quando navighi manualmente e mostra azioni rapide Riproduci/Dettagli.
- I trailer YouTube non caricano più la pagina `watch`, che su alcune WebView TV mostra il falso messaggio Android 4: ora usano un documento iframe interno anche nel player fullscreen.

## v1.0.24 - 2026-07-06

- Corretto il blocco compilazione Kotlin nella home: il listener `setOnKeyInterceptListener` ora riceve una vera istanza `BaseGridView.OnKeyInterceptListener`, non il fragment.
- Il guard del tasto Su resta attivo prima che Leanback sposti il focus al menu alto, ma senza rompere la compilazione.

## v1.0.23 - 2026-07-06

- Il pulsante trailer resta dentro SuperJelly: YouTube non viene più aperto subito in SmartTube/YouTube, ma viene caricato nel layer WebView dedicato con supporto fullscreen interno.
- Nella home il tasto Su viene intercettato prima del focus Leanback: dalle righe inferiori risale riga per riga e può andare al menu alto solo quando il focus è davvero sulla primissima riga.

## v1.0.22 - 2026-07-06

- Il logo/banner SuperJelly torna a mostrare chiaramente il testo “SUPER JELLY” nella card Android TV/launcher, senza tagli.
- I trailer YouTube aprono prima SmartTube/YouTube quando disponibili e usano un embed con referrer quando devono ricadere nella WebView, evitando l’errore player 153 quando possibile.
- Nella home il focus può uscire verso il menu alto solo dalla primissima riga: dalle righe inferiori il tasto Su riporta prima alle righe precedenti.
- L’uscita dall’app richiede una seconda pressione Back entro 5 secondi e mostra una modale esplicativa prima di chiudere SuperJelly.
- La pressione prolungata su una copertina in home apre un menu rapido con scheda, riproduzione, Dove guardarlo, ricerca server e altre azioni disponibili.
- I contenuti Pluto non usano più il logo canale come copertina film: quando il feed non espone poster/backdrop, SuperJelly prova ad arricchire titolo, immagine e teaser tramite metadati pubblici.
- Projectivy/Android TV riceve canali anche per le sezioni home configurate, non solo per le righe speciali SuperJelly.

## v1.0.21 - 2026-07-06

- Logo, banner e icone SuperJelly sono stati ripuliti: niente testo tagliato nella card TV e stile visivo più vicino alle forme XML originali Jellyfin.
- Il controllo manuale aggiornamenti APK è tornato nella schermata Informazioni e resta compatibile con `superjelly-androidtv-vX.Y.Z-debug.apk`, varianti release e vecchi `jellyfin-androidtv-vX.Y.Z-debug.apk` nella cartella remota configurata.
- Nella home, l'uscita verso il menu superiore richiede una doppia pressione su "Su" in breve tempo quando sei sulla prima riga, evitando salti accidentali al menu.
- La home carica le prime righe insieme, mantiene più card in cache e alleggerisce blurhash/crossfade delle copertine per ridurre scatti e schermate temporaneamente rotte.
- In Impostazioni > Personalizzazione, la sezione Navigazione è ora in cima e "Pagina iniziale" viene mostrata prima delle librerie.
- Il player trailer interno usa WebView hardware-accelerata con embed YouTube diretto e fallback SmartTube/YouTube quando il layer non è supportato.
- "Dove guardarlo" ora cerca anche corrispondenze nei cataloghi gratuiti caricati da SuperJelly e, quando trova un flusso libero, propone la riproduzione interna prima delle ricerche provider.
- Le card dei cataloghi esterni usano un'immagine provider di fallback quando il feed non espone poster o backdrop, così non degradano subito all'icona generica film.

## v1.0.19 - 2026-07-06

- I trailer in home partono in modo più affidabile dopo focus stabile: prima vengono usati i trailer già presenti nei metadati Jellyfin, poi una ricerca YouTube più tollerante e cancellabile al cambio elemento.
- Il pulsante “Riproduci trailer” nella scheda dettaglio usa un layer WebView interno a SuperJelly e passa a SmartTube/YouTube solo come fallback.
- Le schede dei cataloghi streaming ora aprono un menu smart: riproduzione interna dei flussi liberi, ricerca nel server, apertura della scheda locale se il contenuto è già presente, oppure apertura provider.
- Aggiunta la riga “Nuove uscite online”, popolata da una sorgente online e riallineata alla libreria locale dopo gli aggiornamenti/scansioni, così un titolo già posseduto viene marcato come disponibile sul server.
- Le righe SuperJelly esterne e le nuove uscite vengono pubblicate anche come canali Android TV/Projectivy quando hanno contenuti disponibili.
- Le righe della home hanno più respiro verticale e la navigazione riduce micro-lag caricando copertine e trailer con debounce più conservativi.
- Il controllo update iniziale resta prima della home: se trova una nuova APK non prosegue automaticamente finché non scegli di aggiornare o ignorare.

## v1.0.15 - 2026-07-06

- Verificata la migrazione sul fork aggiornato: le personalizzazioni principali di SuperJelly risultano ancora presenti e la cronologia patch viene ricreata per questo pacchetto.
- Le modifiche alle righe della home dalle impostazioni vengono applicate senza riavviare l’app: al salvataggio viene richiesta una ricostruzione immediata della home e, al ritorno, la lista viene riallineata alle preferenze aggiornate.
- La vecchia riga dei provider esterni ora può mostrare un vero catalogo streaming gratuito: i contenuti Pluto TV VOD vengono letti da una playlist catalogo e appaiono come card della home con titolo, immagine remota e apertura del flusso quando disponibile.
- Nelle schede di film e serie è disponibile l’azione “Dove guardarlo”, con ricerca online delle disponibilità e collegamenti mirati solo alle app provider rilevate sul dispositivo.
- Migliorata la compatibilità Android 11+ per rilevare Pluto TV, RaiPlay, Prime Video e Netflix quando installati.

## v1.0.14 - 2026-07-05

- La navigazione nella home è più fluida: i backdrop vengono caricati con debounce, dimensioni ridotte e salto dei caricamenti duplicati.
- Le righe vuote della home vengono nascoste invece di mostrare il grande placeholder “Nessun elemento”.
- Le copertine/anteprime superiori restano stabili in alto e vengono nascoste correttamente quando un elemento non ha immagini disponibili.
- Le anteprime trailer sono più leggere: le richieste restano cancellabili al cambio focus e partono solo dopo una selezione stabile.
- La sezione Navigazione/Librerie è stata spostata in alto nelle impostazioni di personalizzazione.
- La riga dei provider esterni è stata rinominata e l’apertura delle app installate è più affidabile per Pluto TV, RaiPlay e Prime Video Android TV.

## v1.0.13 - 2026-07-05

- Rinominata l’app visibile in SuperJelly, inclusi launcher, splash/logo, banner e testi principali dell’interfaccia.
- Aggiornata l’icona con una variante SuperJelly che mantiene il linguaggio visivo viola/ciano del client originale.
- Aggiunto nelle Impostazioni il controllo manuale degli aggiornamenti APK dalla cartella remota configurata.
- Le preferenze di visualizzazione delle librerie restano persistenti tramite cache locale, così dimensione immagini, tipo immagine, direzione griglia, filtri e ordinamento non vengono persi cambiando pagina o in caso di salvataggio remoto non disponibile.

## v1.0.12 - 2026-07-05

- Aggiunto controllo automatico degli aggiornamenti APK dalla cartella remota predefinita `https://files.animalsina.work/jellyfin/android-tv/`.
- Il client confronta la versione installata con i file `jellyfin-androidtv-vX.Y.Z-debug.apk` disponibili online e propone l’installazione solo quando trova una versione più recente.
- Aggiunto flusso guidato con modale: download dell’APK, richiesta permesso Android per installare APK da questa app e apertura dell’installer di sistema.
- L’aggiornamento resta volontario: nessuna installazione silenziosa, nessun cambio player e nessuna modifica ai provider esterni.

## v1.0.11

- Migliorata la configurazione di build: Gradle ora può risolvere automaticamente il toolchain Java 21 richiesto dal progetto quando non è installato localmente.
- Aggiunto nome esplicito al progetto `buildSrc` per evitare instabilità/cache diverse tra cartelle di checkout.
- Documentata la soluzione per l'errore di compilazione legato a Java 21.

## v1.0.10 - 2026-07-05

- Corretto il comportamento mobile/touch della home rimuovendo la richiesta di focus XML dal contenitore delle righe e applicandola solo sui dispositivi TV/D-pad.
- Su telefoni e tablet la home Leanback non riceve più focus persistente dal contenitore `FragmentContainerView`, evitando che la lista venga riportata alla prima riga durante lo scroll.
- Le righe verticali e orizzontali usano lo scroll touch nativo di RecyclerView su mobile, senza intercettazione manuale del gesto, così restano disponibili fling e nested scroll.
- Le righe orizzontali vengono rese non focusabili solo sui dispositivi touch non-TV, mantenendo invariato il comportamento Android TV/Fire TV.

## v1.0.9 - 2026-07-05

- Corretto in modo più radicale lo scroll touch/mobile della home: sui dispositivi non Android TV il gesto viene gestito manualmente senza passare dal modello di selezione Leanback, che poteva riportare la lista alla riga iniziale.
- Aggiunto supporto allo scroll orizzontale manuale delle righe e allo scroll verticale manuale della home su mobile/touch.
- Preservata la posizione verticale durante l'aggiunta progressiva delle righe, evitando che un aggiornamento della home faccia risalire la lista.
- Mantenuto invariato il comportamento Android TV/D-pad.

# Changelog

## v1.0.27 - 2026-07-06

- Rimosso il nuovo hero carousel separato introdotto nella versione precedente: la home torna al comportamento precedente, dove il blocco grande in alto segue il focus delle card nelle righe.
- Rimossa l'impostazione del hero carousel dedicato, così la configurazione home torna a mostrare solo le righe.
- Projectivy/Android TV launcher pubblica tutte le righe filtrate disponibili nel programma, non solo quelle già selezionate nella home.
- Aggiunte nuove righe configurabili, non attive di default, e pubblicabili su Projectivy: film casuali, serie casuali, film mai visti a caso e film che non vedi da tanto.
- Projectivy crea anche canali per i principali generi disponibili, così puoi avere righe dedicate per azione, commedia, drama, horror, fantascienza, thriller e altri generi.

## v1.0.19 - 2026-07-06

- I trailer in home partono in modo più affidabile dopo focus stabile: prima vengono usati i trailer già presenti nei metadati Jellyfin, poi una ricerca YouTube più tollerante e cancellabile al cambio elemento.
- Il pulsante “Riproduci trailer” nella scheda dettaglio usa un layer WebView interno a SuperJelly e passa a SmartTube/YouTube solo come fallback.
- Le schede dei cataloghi streaming ora aprono un menu smart: riproduzione interna dei flussi liberi, ricerca nel server, apertura della scheda locale se il contenuto è già presente, oppure apertura provider.
- Aggiunta la riga “Nuove uscite online”, popolata da una sorgente online e riallineata alla libreria locale dopo gli aggiornamenti/scansioni, così un titolo già posseduto viene marcato come disponibile sul server.
- Le righe SuperJelly esterne e le nuove uscite vengono pubblicate anche come canali Android TV/Projectivy quando hanno contenuti disponibili.
- Le righe della home hanno più respiro verticale e la navigazione riduce micro-lag caricando copertine e trailer con debounce più conservativi.
- Il controllo update iniziale resta prima della home: se trova una nuova APK non prosegue automaticamente finché non scegli di aggiornare o ignorare.

## v1.0.8 - Mobile touch row selection build fix

- Fixed the mobile/touch home scroll stabilization build error by using the Leanback `setSelectedPosition(position, subposition)` overload available in this project.
- Kept the v1.0.7 mobile touch behavior that prevents Leanback row selection from staying stuck on the first home row during touch scrolling.

## v1.0.7 - 2026-07-05

- Corretto alla radice il salto verso l'alto della home su telefoni/tablet: la `VerticalGridView` Leanback ora sincronizza la riga selezionata interna con la riga realmente visibile durante lo scroll touch, invece di mantenere la selezione bloccata sulla prima riga.
- In modalità touch/mobile la home blocca la propagazione del focus ai discendenti Leanback e disattiva il preserve-focus-after-layout, evitando che i relayout riportino la viewport alla riga 0.
- Mantenuto invariato il comportamento Android TV/D-pad: il fix si applica solo ai dispositivi touch non Leanback.

## v1.0.6 - 2026-07-05

- Migliorata la navigazione mobile/touch della home: su telefoni e tablet non Android TV il client non forza più il focus Leanback sul primo elemento selezionato mentre si scorre.
- Mantenuto il comportamento TV/D-pad su Android TV, Fire TV e dispositivi Leanback, così il telecomando continua ad avere focus e preview coerenti.
- Le card della home restano cliccabili su touch, ma non richiedono più focus persistente quando il dispositivo non è una TV.

## v1.0.5 - 2026-07-05

- Corretto un errore di compilazione Kotlin nel fix scroll: l’identità stabile degli elementi ora funziona anche quando l’adapter Leanback espone elementi come `Any/Object`.
- Mantenuta la stabilizzazione dello scroll senza forzare cast non validi su `BaseRowItem`.

## v1.0.4 - 2026-07-05

- Migliorata la stabilità dello scroll su mobile/touch e Android TV evitando che le righe tornino all'inizio durante il caricamento progressivo.
- Corretto il comportamento di selezione quando Leanback segnala temporaneamente una riga senza elemento selezionato.
- Reso più stabile l'aggiornamento incrementale delle righe per non ricostruire l'intero carosello durante lo scroll.


## v1.0.3

- Aggiunte nuove sezioni Smart Home configurabili per mood leggero, mood azione e scelte rapide, pensate per rendere più utile la home senza calcoli pesanti lato TV.
- Aggiunta una riga "Watch outside Jellyfin" con launcher per Pluto TV, RaiPlay e Prime Video: il client apre l'app ufficiale se installata, altrimenti Play Store o browser.
- Aggiornate le sezioni home predefinite per includere le nuove righe SuperJelly mantenendo fallback e player Jellyfin invariati.
- I provider esterni restano collegamenti/app launcher: nessuna gestione password, nessun bypass DRM e nessuna riproduzione interna non autorizzata.

## v1.0.1

- Ottimizzata la home in stile Netflix per ridurre blocchi e caricamenti pesanti durante lo scorrimento.
- Mantenuta la preview trailer automatica, ora con avvio ritardato, cache, riuso della WebView e backoff automatico in caso di errori ripetuti.
- La home ora mostra prima le righe prioritarie e carica le sezioni successive in modo progressivo, migliorando la percezione di velocità su Android TV.
- Aggiunto documento tecnico `docs/SUPERJELLY_TV_PLAN.md` per la roadmap SuperJelly TV: home intelligente, provider esterni, Pluto TV/RaiPlay/Prime come schede esterne e Live TV/M3U dove possibile.
