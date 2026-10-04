# Fobol

**xPitch** — football stats for the Premier League, La Liga, Bundesliga, Serie A, Ligue 1 and 3F Superliga:
current league tables (reorderable, collapsible) and per-league advanced stats (xG, xPts, shots) with sortable standings.

- **Backend** — Clojure (Ring + Reitit) in `src/clj`. Serves EDN from `/api/leagues` and `/api/leagues/:id`, plus the static page.
- **Frontend** — ClojureScript (Reagent + re-frame, built with shadow-cljs) in `src/cljs`.

Data comes from football-data.co.uk (see [Live data](#live-data)). If a league can't be loaded, the app falls back to
illustrative sample data (`src/clj/xpitch/data.clj`, with derived stats in `src/clj/xpitch/stats.clj`).

## Requirements

- Java 21+
- [Babashka](https://babashka.org) (`bb`), which also provides the Clojure CLI via `bb clojure`
- Node.js + npm

## Commands

| Command      | What it does                                                  |
|--------------|---------------------------------------------------------------|
| `bb install` | Install npm dependencies (React, shadow-cljs)                 |
| `bb dev`     | Server on http://localhost:3000 + ClojureScript hot reload     |
| `bb start`   | Optimized frontend build, then serve on http://localhost:3000 |
| `bb test`    | Run backend tests                                             |
| `bb build`   | Optimized frontend build only                                 |

Set `PORT` to change the server port.

## Live data

By default the server loads the current season from **football-data.co.uk** (free CSVs, no key):

- **Big 5 leagues:** results, xG, shots and shots on target → standings, form, home/away splits,
  xG/xGA/xGD, and xPts computed from each match's xG with a Poisson model.
- **3F Superliga:** results and closing odds only → xPts comes from pre-match betting odds; xG and shots show "–".
- **Possession** isn't in the files, so it shows "–" everywhere.
- Files are updated a few times a week. The server re-checks every `:refresh-minutes` (default 60) and caches in `.cache/`.
- Standings tie-break on points, goal difference, then goals scored; leagues' head-to-head rules aren't applied.
- If a league can't be loaded it falls back to sample data, with a note on the page.

`GET /api/status` shows the provider, last sync, cached match counts and any errors.
Settings live in `config.local.edn` (see `config.local.edn.example`).

### API-Football (optional)

Set `:provider :api-football` and `:api-football-key` in `config.local.edn`. It needs a plan that covers the
season you want (the free plan only covers 2022–2024). Each sync costs about 2 requests per league plus 1 per
20 new matches; the first sync is ~40 requests. On a 100-requests/day plan, set `:refresh-minutes` to 240+.
