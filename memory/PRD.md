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

## Updates (2026-06 — iterations)
- Barcode part-number extractor: pulls real Hyundai/Kia OEM number from coded barcode payloads (scan.tsx / scan.web.tsx / buying-trip). ZXing web scanner (fast, 50ms) + native expo-camera.
- AI research engine reworked: DB-FIRST (verified library returns 100% instantly), then AI enrichment. AI NEVER self-marks Verified — only Admin approval does. Structured schema (compatible_models, cross_reference, status).
- Edit-before-Approve / edit-before-add: admin can correct all AI/manual details before saving Verified (part detail modal).
- Gemini provider: user's own gemini-3.7-flash key (free, non-grounded) primary with retry; Google Search grounding behind GEMINI_GROUNDING flag (needs billing); auto-fallback to Emergent key so AI never errors.
- User management: admin can add / disable / toggle-permissions / REMOVE (soft-delete) staff; Main Admin protected.
- Buy form: auto-fill + PART & COMPATIBILITY (company/vehicles/variant) auto-logged under the part number on purchase (no overwrite of existing verified data).
- All demo/dummy data purged; only Main Admin + code-defined categories remain (fresh data).

## Backlog / Remaining
- P1: True offline-first local queue + auto-sync on reconnect (currently online-first with sync indicator).
- P2: Duplicate protection UI feedback; sticker-color guided ID; old/new number auto-link.
- P2: Hourly cloud backup + versioning + recovery; 360° video (future).
- P2: Scanner UI: Torch/flashlight toggle, tap-to-focus (vibrate-on-scan already done via Haptics).

## Updates (2026-06 — batch buy + photos + password)
- Multiple/Batch Buy FIXED: was hardcoding override:true (bypassed limits). Now sends override:false → per-part purchase limit enforced; blocked parts show error + not counted. GPS auto-synced and sent with each batch buy; header shows GPS status.
- 6-side Part Photos: Buy screen camera/gallery capture (expo-image-picker), upload to object storage, up to 6, thumbnails + remove. Photos shown on part detail stock units.
- GPS shown on part detail stock unit rows.
- Change Password: self-service /change-password screen (Admin Panel → ACCOUNT), backend POST /api/auth/change-password (verifies current, min 6 chars). Verified/tested (iteration 11, 8/8 pytest + frontend).
- Removed leftover buying-trip.tsx route (module fully gone).

## Updates (2026-06 — admin stock + password management)
- Inventory adjust (Admin only): Inventory tab rows have ઘટાડો/વધારો/Delete; Part-details STOCK UNITS has +/- quantity + per-unit trash delete. Backend POST /api/stock/adjust, DELETE /api/stock/unit/{id} (require_admin).
- Admin user management: reset/edit any user's name/username/password + VIEW password (reversible Fernet-encrypted copy at rest; bcrypt still used for login). GET /api/admin/users/{id}/password (admin only), PATCH /api/admin/users/{id} extended for name/username. Seeded admin password_enc backfilled at startup.
- Physical Stock Verification (Admin): /stock-verify screen + Admin Panel link. GET /api/stock/verification (expected qty per part), POST /api/stock/verify (counts -> discrepancies MISSING/EXTRA, saved to db.verifications).
- Verified iteration 12: backend 15/15 pytest + frontend E2E all PASS.
- Confirmed purchase-limit rule = per part number (max units). Fixed deprecated pointerEvents prop across scan/batch-buy.


## Updates (2026-06 — history delete + backup)
- ખરીદ/વેચાણ History (/history, Admin): GET /api/transactions?type=buy|sell + POST /api/transactions/delete (multi-select bulk delete; deleting buy OR sell entry also hard-deletes its stock unit). Admin-only.
- Backup & Restore (/backup, Admin): GET /api/backup/export (JSON), GET /api/backup/excel (xlsx), POST /api/backup/import (upsert restore). Frontend expo-file-system/legacy + expo-sharing (native)/blob download (web) + expo-document-picker import. Data in secure cloud MongoDB.
- Verified iteration 13: backend 14/14 pytest + frontend E2E PASS.
- APK: via Emergent Publish button (Deploy -> Generate Android build) — final step.

## Bug fix (2026-06 — delete not working on mobile browser)
- ROOT CAUSE: Alert.alert confirm buttons don't fire callbacks on React Native Web (mobile Chrome) — so Delete/Restore actions never executed. Adjust +/- worked but per-unit rows made it look static.
- FIX: Added cross-platform <ConfirmModal> in src/components/ui.tsx (Modal-based). Replaced ALL Alert.alert confirms with ConfirmModal in inventory.tsx, part/[pn].tsx, history.tsx, backup.tsx.
- Verified on web: inventory unit delete (confirm modal shows, row removed, toast), part-detail quantity +/- reflects (STOCK UNITS 2->3), counter updates. History bulk delete + backup import use same ConfirmModal.


## Phase 1 — Multi-Store Foundation (COMPLETE, 2026-08-28)
- Converted single-tenant → multi-tenant. Every collection now carries store_id.
- Sign Up (POST /api/auth/register) creates an isolated store + admin owner.
- Per-store data isolation enforced server-side via resolve_store() (client cannot override store_id).
- Super-admin (abdul) god-view: GET /api/admin/stores + /stores screen lists all stores.
- Security fixes: SEC-001 (no staff->admin escalation, cross-store user edit blocked), SEC-003 (file access store-scoped + disabled-user check), SEC-004 (/admin/users no longer leaks password_enc/google keys). SEC-002 reversible password view KEPT per user request, now store-scoped.
- Print (expo-print) added to Buy (Print Slip), Sell (Print Bill), Inventory (Print report).
- Backend /health endpoint added (fixes K8s probe 404).
- Tested: /app/test_reports/iteration_15.json (18/18 pass).
- PENDING: Phase 2 (complete car parts catalog + Honda/Nissan companies), Phase 3 (monetization: subscription/ads).
