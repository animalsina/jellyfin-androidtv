# External providers in SuperJelly

## Goal

SuperJelly should not behave like a simple app launcher. External services should appear as catalog rows when a legal, reachable catalog/feed exists, so a free movie or episode can look and feel like a Jellyfin item in the home. When the title cannot be played internally, the user should still be able to open the installed provider app on the best available destination.

## Implemented in v1.0.15

- The old external provider row is now treated as a streaming catalog row.
- Pluto TV VOD Italy is loaded from a catalog playlist and rendered as normal home cards with remote poster/backdrop URLs.
- Empty external rows stay hidden, so the home never shows a big placeholder when a feed is unavailable.
- Selecting a Pluto VOD card tries to open the stream URL first, then falls back to the provider detail URL.
- Movie and series detail pages now expose a “Where to watch” action.
- Provider actions are filtered by installed app package where possible, so unavailable apps are not promoted as direct provider actions.
- A generic JustWatch search remains available as an online availability lookup fallback because exact provider availability requires a data feed/API contract.

## Provider status

### Pluto TV

- Best current candidate for catalog rows because VOD playlists can expose titles, logos, groups and HLS URLs.
- Streams are free/ad-supported, but regional restrictions and tokenized URLs can change.
- Current adapter uses the Italy VOD playlist and keeps the integration defensive: no row is shown if the playlist cannot be read or contains no playable entries.

### RaiPlay

- RaiPlay has a free catalog and Android TV app, but this pass did not add a stable RaiPlay catalog adapter because there is no public API contract in the app.
- Recommended next step: add a feature-flagged RaiPlay adapter only after choosing a reliable catalog source and a safe refresh/cache strategy.

### Prime Video and Netflix

- These services can be surfaced as “available on” actions only when the app is installed or through a generic availability search.
- Internal playback inside SuperJelly is not a realistic target without provider authorization, DRM handling and official deep-link/player contracts.
- Current behavior avoids pretending these are local Jellyfin items unless a real catalog/playback source exists.

## Next safe step

Add a configurable provider catalog registry:

1. Provider id/name/icon.
2. Catalog URL or official feed source.
3. Parser type: M3U, JSON feed, partner API.
4. Country/language.
5. Cache TTL and last successful snapshot.
6. Capability flags: internal HLS playback, app deep link, browser fallback, subscription-only.
7. Per-provider visibility preferences.


## Implemented in v1.0.19

- External catalog cards no longer behave as blind app shortcuts. Opening a catalog title now shows a small action menu with internal playback for free streams, local server lookup, provider opening and direct local detail when a matching server item is known.
- The home can show a “New online releases” row populated from an online release source. Titles are re-matched against the Jellyfin library on every home rebuild, including library scan notifications, so a newly imported title can switch to “Available on your server”.
- Android TV/Projectivy channels are populated with SuperJelly external catalog and online-release rows when the feeds return content.
- Trailer playback tries to stay inside SuperJelly first, then falls back to SmartTube/YouTube packages if the embedded player cannot load.
- Provider availability remains conservative: exact free/included availability for Prime Video, Netflix, RaiPlay or Pluto requires a stable feed or partner API. Without that, SuperJelly shows search/open actions rather than pretending a full availability match is guaranteed.

## Implemented in v1.0.21

- "Where to watch" now first searches the free external catalog already loaded by SuperJelly. If a playable match is found, the menu shows a direct "Play free on ..." action that opens the stream inside SuperJelly.
- Provider entries still remain conservative for subscription platforms: Netflix, Prime Video and other DRM services are only shown as installed-app/search actions unless a proper provider feed/API is configured.
- The recommended exact-availability path is a TMDb/JustWatch-based provider adapter with a configured API key, country filter and cache. That can map known TMDb IDs to watch-provider availability instead of relying on broad web searches.
- Pluto TV catalog images now fall back to a provider artwork URL when an M3U row does not expose a poster/backdrop, so cards should not degrade to the generic movie icon unless no usable artwork exists.

## Trailer playback notes

- YouTube trailer playback now uses a direct `youtube.com/embed/...` URL in hardware-accelerated WebView instead of injecting an iframe through `loadDataWithBaseURL`.
- This is still best-effort on Android TV WebView. When the embedded player fails, SuperJelly falls back to SmartTube/YouTube packages in order.
- A fully reliable trailer source would require storing trailer URLs from Jellyfin metadata, TMDb/YouTube IDs, or another configured trailer provider instead of scraping search results on focus.

## v1.0.22 - Artwork e disponibilità

- Le card Pluto non usano più il logo generico del provider come poster del film. Se la playlist espone `tvg-logo`, `tvc-guide-art`, `fanart` o `trailer`, quei valori vengono usati direttamente.
- Quando il feed non fornisce immagini o teaser, SuperJelly prova un arricchimento leggero tramite Wikidata Query Service: cerca corrispondenze esatte del titolo in italiano/inglese, recupera immagine pubblica (`P18`) e, quando presente, l’ID video YouTube (`P1651`).
- Il risultato è best-effort: evita copertine palesemente sbagliate, preferendo lasciare una card neutra se non trova una corrispondenza precisa.
- Per disponibilità provider precise per paese/abbonamento/noleggio resta consigliata una fonte dati strutturata tipo TMDb Watch Providers/JustWatch o una API partner configurabile.


## v1.0.26 - Cataloghi filtrati e carousel

- Il hero carousel è separato dalle righe: le righe home restano liste normali, mentre il blocco superiore pesca candidati casuali e ruota in modo autonomo.
- I trailer nel hero vengono precaricati nascosti e appaiono solo dopo una permanenza stabile sul titolo, per evitare flash e caricamenti aggressivi durante la navigazione.
- Pluto TV viene esposto sia come catalogo generale sia come righe filtrabili per genere/categoria quando il feed contiene `group-title`.
- RaiPlay viene trattato come catalogo gratuito best-effort tramite pagine pubbliche Film/Serie. Non viene promessa riproduzione interna se la pagina RaiPlay non espone un flusso HLS diretto.
- Projectivy riceve anche canali Android TV separati per le categorie esterne più popolate, così può mostrare più righe rispetto al singolo canale catalogo generico.
- Le immagini vengono prese prima dal feed/provider; se Pluto non espone poster reali, SuperJelly prova un arricchimento da metadati pubblici prima di ricadere su placeholder.

## v1.0.27 - Rollback carousel e canali Projectivy

- Il hero carousel separato è stato rimosso: la home torna al comportamento precedente in cui il blocco alto segue il focus delle righe.
- Projectivy/Android TV launcher pubblica tutte le righe filtrate disponibili nel programma, incluse le nuove righe casuali e i principali generi.
- I cataloghi esterni restano divisi per provider/categoria quando il feed espone dati sufficienti.
