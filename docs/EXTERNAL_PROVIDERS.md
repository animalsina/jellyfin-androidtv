# External providers in SuperJelly

## Goal

SuperJelly should eventually show legal watch options next to Jellyfin results: play locally when the movie exists in Jellyfin, otherwise open an installed provider app directly on the matching title when a stable deep link exists.

## Current provider status

### Pluto TV

- Best first candidate for native rows because community tooling and public playlists can expose live channels and some VOD entries as M3U/EPG data.
- Free ad-supported streams can often be opened as HLS, but availability, regions and URLs change frequently.
- Recommended next implementation: configurable M3U source, cached catalog, title matching, and playback through Jellyfin/SuperJelly player only for direct HLS streams that are reachable without DRM or proprietary app state.

### RaiPlay

- RaiPlay has a free on-demand catalog and Android TV app, but no stable public API contract for third-party catalog ingestion was found in this pass.
- Stream extraction is possible in community tools, but it should be treated as fragile and refreshed often.
- Recommended next implementation: open installed RaiPlay Android TV app reliably first; add optional catalog ingestion later only behind a feature flag.

### Prime Video

- Prime Video does not provide a public third-party catalog/player API suitable for importing lists into SuperJelly.
- Direct playback inside SuperJelly is not realistic because Prime Video content is DRM/account/app controlled.
- Recommended next implementation: title-aware web/app deep link attempts when an external ID or known Prime URL is available; otherwise open Prime Video app/home search.

## Implemented in v1.0.14

- Replaced the old “Watch outside Jellyfin” label with a generic streaming provider row.
- Improved provider app launching by checking Android TV package variants before falling back to provider URLs or Play Store.
- Kept the integration conservative: no fake catalog rows are shown until a provider source can be resolved reliably.
