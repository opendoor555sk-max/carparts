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

## Reports feature (COMPLETE, 2026-08-28)
- New /report.tsx screen (mode=buy|sell|stock) — Home has "REPORTS" section with 3 buttons: ખરીદેલો માલ, વેચેલો માલ, Stock.
- Filters: date range presets (all/month/year/today), Company chips, Category chips. Grouped Company -> Category -> Part Number, with date.
- Print via printReport() (grouped HTML). Print also on Buy/Sell/Inventory/Needs/History.
- Backend: /transactions and /inventory now accept date_from,date_to,company,category and return company+category (joined from parts). Store-scoped.

## Branding + Date Picker + Companies + English (2026-08-28)
- Store branding: stores collection gets gst/phone/address/logo_path. GET/POST /api/store/profile. public_user + _attach_store return store_gst/phone/address/logo. New /store-profile.tsx (Admin > Store Profile). print.ts header now shows logo+GST+phone+address (brandingFromUser(user)).
- Report: custom from-to date range — native calendar (@react-native-community/datetimepicker) + web text inputs. All print funcs now take Branding object.
- COMPANIES expanded to real English names incl Honda, Nissan, Toyota etc (backend + home).
- English: report.tsx, store-profile.tsx, home REPORTS + welcome + hint, all print output. PENDING: older screens (buy/sell/inventory/admin/login) still have some Gujarati — convert next on user confirm.

## Bank + Barcode + Drill-down + Full English (2026-08-28)
- Store branding now includes bank field; shown on printed receipts/reports header (logo/GST/phone/address/bank).
- Barcode: Code128 generator src/utils/barcode128.ts + <Barcode> component (react-native-svg installed). Part detail shows barcode + "Print Barcode Label". Receipts now embed the part-number barcode.
- Super-admin drill-down: /stores cards tappable -> /store-detail.tsx shows that store stats + Inventory/Purchases/Sales via ?store_id (super_admin only).
- Report custom date picker: native calendar + web date fields.
- FULL ENGLISH: all Gujarati removed from every screen (verified grep = NONE). Company list = real names (Honda, Nissan, Toyota, etc).
- PENDING: Common Part Catalog (part identity shared across stores, buys/sales/stock private) — big backend refactor, needs focused turn + model confirm (verified/technical shared? default yes).

## Buy Company picker + Signup contact (2026-08-28)
- Buy screen: added COMPANY chip selector (Maruti Suzuki/Hyundai/Honda/Nissan/Toyota/etc) -> saved to part -> reports group by company. Category field already present.
- Signup: CONTACT NUMBER now compulsory (backend 422 without it; stored on store + owner user).
- PENDING (next turn, budget): GPS+contact on inquiry/requirement admin-only tracking; Statistics cards clickable; Common Part Catalog (shared identity); Company picker on Sell too.

## Track Inquiries + Clickable Stats (2026-08-28)
- Requirement create captures GPS (expo-location) + by_contact (user contact). Admin sees a Call chip (tel:) + View-on-map link (maps.google) on each requirement; hidden for non-admin.
- Admin STATISTICS cards now clickable -> navigate (Parts->/parts, In Stock->/report stock, Sold->/report sell, Pending Needs->requirements, AI Pending->/ai-approvals, Verified->/parts).
- Buy has company picker; company flows to reports. Sell/Stock inherit company from the part (set at buy) so reports already group.
- PENDING: Common Part Catalog (shared part identity across stores + per-store limits move) — dedicated next turn.

## GPS banner + printable stat reports (2026-08-28)
- scan.tsx + scan.web.tsx: capture GPS on entry (expo-location) and show a visible GPS banner for ALL modes (Search/Buy/Sell/Requirement). GPS passed to requirement-new and saved.
- Admin STATISTICS cards now route to the printable /report screen (print + custom From-To date) instead of plain list.
- NOTE: browser preview blocks geolocation in iframe (shows Getting GPS...) but real device captures coords.
- STILL PENDING: (1) log GPS+contact for plain Search lookups super-admin-only; (2) category catalogue dropdown in Buy/Sell; (3) Common Part Catalog (big refactor); (4) automated testing pass.

## Report filters = full masters (2026-06 fork)
- report.tsx COMPANY + CATEGORY filter chips now load the FULL master lists from backend (/companies, /categories) instead of only values present in current results. All companies (Maruti Suzuki, Hyundai, Tata, Mahindra, Kia, Toyota, Honda, Nissan, Renault, Ford, Volkswagen, Skoda, MG, Datsun, Chevrolet, Fiat, Jeep, Citroen, Isuzu) + full Category Master (73 items) selectable in Buy/Sale/Stock reports. Backend /transactions + /inventory already filter by company/category. Verified on preview.
- PENDING next: Search GPS super-admin-only tracking + super-admin Search Report (compulsory custom date); Security fixes SEC-001/003/004; Common Part Catalog refactor.

## Demand & Search filters (2026-06 fork)
- demand.tsx now has DATE (presets + custom From-To) + COMPANY (full master) + CATEGORY (full Category Master) filters, mirroring report.tsx. Backend /search-history extended with date_from/date_to/company/category params + joins parts to attach company/category/part_name. Verified via curl (no-filter returns items with company/category; Hyundai + old-date filters correctly return []). SEC-003 already fixed in /files (store ownership enforced).

## Strict GPS Gate (2026-06 fork) — MANDATORY location
- New src/components/LocationGate.tsx wraps the entire app in app/_layout.tsx. On NATIVE (phone): app is fully blocked unless device Location services are ON AND foreground permission granted; verifies with getCurrentPositionAsync. Blocking screen: Turn On GPS / Allow Location / Open Settings + Retry. Auto re-checks on AppState 'active'. WEB passes through (preview only; iframe blocks geolocation). app.json already has location perms. NOTE: only testable on a real phone / Expo Go, not web preview.

## Sticker Sheet Printing — Module 3 (2026-06 fork) — A4 anti-wastage
- New screen app/labels.tsx ("Sticker Sheet Print"): pick from 21 A4 layouts (chart 01P..110L with mm dims + rows×cols), enter Part Number + Line1/Line2, choose code (Barcode/QR/Both/Text), interactive grid to TAP a start block (cells before it skipped = anti-wastage), copies (blank=fill sheet), cut-guide toggle. Prints mm-precise A4 via expo-print.
- New utils: src/utils/qr.ts (pure-JS QR -> scalable SVG via qrcode-generator@2.0.4) and src/utils/labelSheet.ts (SHEET_LAYOUTS master + generateSheetHtml with @page A4 margin:0, absolute mm cell positioning, grid auto-centered on A4).
- Entry points: Home "STICKER PRINTING" tile + Part detail "Print on A4 Sticker Sheet" button (passes pn/company/name).
- Verified on web preview: layout switch, start-cell coloring (cells<start dark, >=start orange), print action fires with no JS errors. Font/layout exact-photo-clone NOT done (per user OK with approximation). 40P layout is a chart anomaly (overflows A4) but included.
- NEXT: Module 1+2 AI Universal Scanner (Gemini 3 Pro vision) — scan sticker -> capture text/fields/barcode-QR region -> editable template -> regenerate code on new part number. Needs integration playbook + confirm.
