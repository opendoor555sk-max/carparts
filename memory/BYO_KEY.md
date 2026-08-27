# BYO-Key Google Custom Search (2026-06)

Each user stores their OWN Google Custom Search API key + Search Engine ID (CX) in Settings.
Part-number web search runs on that user's free quota (100/day). Host LLM credits = ZERO
(field extraction is keyword-based, no LLM).

## Backend (server.py)
- User doc fields: google_api_key, google_cx (per-user).
- GET/POST /api/auth/settings — save/read (raw key never returned; only google_cx + has_google_key).
- POST /api/search/web {part_number} (perm: search):
  - DB-FIRST cache: if part Verified in master DB -> returns cached=true (no Google call).
  - Else uses the current user's key+cx -> Google Custom Search JSON API -> keyword-extract
    company/brands/models/variants from titles+snippets -> returns fields + sources.
  - Errors: NO_KEY (400), QUOTA (429), SEARCH_ERR (400 — kept as 4xx so ingress doesn't rewrite to HTML).
- Master DB = parts collection; entries tagged created_by + created_at (+ verified via approve).

## Frontend
- app/settings.tsx — Google API key + CX inputs, save, how-to steps. Linked from Admin ("Google Search Setup").
- app/buy.tsx — "Google Autofill" button in PART & COMPATIBILITY card -> fills name/vehicles/variant;
  on NO_KEY routes to /settings.
