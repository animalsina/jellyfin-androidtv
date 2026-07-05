# Changelog

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
