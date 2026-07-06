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
