# PRD — કબાડી માર્કેટ હિસાબ (Kabadi Market Hisab)

## Original Problem Statement
Part-Number-first auto electrical scrap parts ERP + AI research (Android, Gujarati). Primary identifier = PART NUMBER / Barcode / QR (not vehicle model). Grows into a Parts Knowledge DB + Scrap Inventory + Requirement + Purchase Control system.

## Locked Decisions
- Company gate: Hyundai+Kia / Maruti / Tata / Mahindra / All. Model/variant come from part number.
- Universal scan (barcode + QR + manual) across Search, Buy, Sell, Requirement, Buying Trip.
- SEARCH, BUY, SELL never mix — three separate modules.
- Purchase Limit = 100% admin-configurable. No hard-coded numbers.
- AI = Gemini 3 Flash — part ID + verification engine (Suggest → Source → Confidence → Admin Approval → Save).
- No permanent change without Admin approval (Main Admin: Abdul Salam).
- Price + Purchase Location = admin-only, hidden by default.
- Full Category master = 73 items / 5 groups.

## User Choices (confirmed)
1. Offline-first + auto-sync (online-first implemented for MVP; sync indicator shown).
2. NEW part saved now; internet details Unverified/Pending until admin approval.
3. Login: password + biometric (fingerprint unlock).
4. Purchase location: manual text (Rack → Shelf → Box → Position).
5. AI model: Gemini 3 Flash (gemini-3-flash-preview).

## Architecture
- Backend: FastAPI + MongoDB (motor). JWT auth (bcrypt), role/permission gating. Gemini via emergentintegrations (EMERGENT_LLM_KEY). Object storage for photos.
- Frontend: Expo Router (React Native, TS). Dark industrial theme (amber on obsidian). Bottom tabs + stack.
- All API routes prefixed /api. Auth via Bearer token (secure storage).

## User Personas
- Main Admin (Abdul Salam): full control — limits, users, AI approval, price/location, stats.
- Staff: search/buy/sell/requirement/buying-trip/manage-parts by default; admin toggles per-user permissions.

## Core Business Rules (implemented)
| Action | Stock effect |
|---|---|
| SEARCH | no change (logs search history) |
| SAVE KNOWN PART | no change |
| REQUIREMENT | no change |
| BUY | +1 (limit engine enforced) |
| SELL | -1 (stock verified) |
Re-search status: IN STOCK / KNOWN PART / REQUIREMENT / NEW PART.

## Implemented (2026-06 — MVP)
- Auth: JWT login, password + biometric unlock, seeded Main Admin, staff creation + per-user permissions.
- Company gate + Home dashboard with 5 module tiles + sync pill.
- Universal scanner (camera barcode/QR + manual entry) with permission-contract handling.
- Search → status result → Part Master detail (details, verification badge, units, limit).
- Buy flow with live Purchase Limit stacked meter + WARNING + DO NOT BUY banner + admin override + admin-only price.
- Sell flow with stock verification + unit selection + admin-only price.
- Inventory list (condition filter chips + location).
- Requirements (priority, status cycle, filter chips) + new requirement form.
- Category master: 73 items / 5 collapsible groups → parts list per category.
- Buying Trip: session scan → stock/limit/requirement + buy decision (DO NOT BUY etc.).
- AI Research (Gemini): structured suggest + confidence meter + conflict flag + sources + admin approve/reject → Verified save.
- Admin Panel: stats, Users, Purchase Limits (global + per-part), AI Approvals, Demand & Search history.
- Backend fully tested (24/24). Frontend critical flows verified.

## Backlog / Remaining
- P1: True offline-first local queue + auto-sync on reconnect (currently online-first with sync indicator).
- P1: 6-side photo capture + upload wired to camera/gallery (endpoints ready; UI capture pending).
- P2: Duplicate protection UI feedback; sticker-color guided ID; old/new number auto-link.
- P2: Hourly cloud backup + versioning + recovery; 360° video (future).
- P2: GPS purchase location (deferred — manual text chosen).

## Next Tasks
- Add photo capture (6-side) in Add Part / Buy using expo-camera + gallery.
- Implement offline scan queue for Buying Trip with auto-sync.
