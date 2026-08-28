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

## Category Master expanded + Barcode/QR scanner in labels (2026-06 fork)
- CATEGORY_MASTER in server.py expanded from 5 electronic groups to 22 groups / 245 items (added Engine, Fuel, Cooling, Transmission/Clutch, Braking, Suspension/Steering, Body/Exterior, Lighting, Ignition, Filters/Fluids, AC/Heating, Exhaust, Interior/Trim, Wheels/Tyres, Belts/Hoses/Bearings, Gaskets/Seals, Wipers). Auto-flows to Buy/Sell picker, Reports filter, Demand filter, Category Master screen. Verified /categories returns 22 groups.
- labels.tsx: added "Scan" button beside Part Number -> full-screen CameraView modal (expo-camera) scanning qr/code128/ean/etc -> auto-fills Part Number. Camera perms already in app.json. NATIVE feature (Expo Go / build), not web preview.
- labels.tsx grid selection changed from "start cell + copies range" to ARBITRARY multi-select: tap any block toggles it (non-contiguous, any position, single/multi), All / Clear buttons, header shows selected count. generateSheetHtml now takes cells:number[] instead of startCell/copies. Verified toggle + untoggle on web.

## AI Sticker Scanner — Module 1+2 (2026-06 fork) — Gemini 3 Pro vision
- Backend POST /api/scan-sticker (server.py, before include_router): takes {image_base64}, calls emergentintegrations LlmChat .with_model("gemini","gemini-3.1-pro-preview") + ImageContent, send_message, parses strict JSON -> {aspect, part_number, lines[{text,x,y,size,bold,align}], logos[{label,x,y,w,h}], codes[{type,value,x,y,w,h}]}. Uses EMERGENT_LLM_KEY (already in .env). VERIFIED via curl on real sticker: 14 lines, part# 95400-T7110, logos+datamatrix bbox, even read rotated label.
- Frontend app/scan-sticker.tsx ("AI Sticker Scanner", Home tile): pick from Gallery/Camera (expo-image-picker), send base64, crop detected logos from original via expo-image-manipulator v14 (ImageManipulator.manipulate().crop().renderAsync().saveAsync base64), rebuild editable template (edit Part Number -> re-encodes into code; QR/Barcode toggle; edit each text line), live RN preview (absolute Text/Image/SvgXml), then A4 layout + arbitrary grid multi-select -> generateRichStickerSheetHtml -> print.
- New utils in labelSheet.ts: StickerTemplate/TplLine/TplLogo/TplCode types + templateInner + buildSheet helper + generateRichStickerSheetHtml. Positions are % of each label cell (sticker stretched to cell aspect = approximation).
- NATIVE-ONLY full flow (image picker/camera/manipulator) — screen renders on web (no bundle errors) but pick/scan works only on phone (Expo Go/build). DataMatrix originals are re-generated as QR encoding the part number (per user OK). Font/layout = close approximation, editable. Uses managed key balance per scan.

## Sticker Scanner v2 fixes (2026-06 fork) — overlap + speed + logos
- Layout overlap fixed: AI no longer returns per-line coords (was slow+unreliable). New simplified prompt returns ordered line TEXT only + brand logos bbox + code bbox. Frontend normalizeLines() lays lines in an evenly-spaced non-overlapping left column with width-fit font; code forced into a clean RIGHT column (square, x=100-cw-3); brand logos placed in a top header row; text topPad reserved when logos present. Deterministic => never overlaps.
- Logos filtered to KNOWN_BRANDS only (Hyundai/Kia/Maruti/... ) — skips Pb/CE/E11/connector junk crops.
- Speed: switched model gemini-3.1-pro-preview -> gemini-3.7-flash + image downscaled to 1100px before upload. Scan time ~48s -> ~28s. NOTE: still ~20-30s (full AI OCR of a multi-line label cannot be "instant"); set this expectation with user.

## CRITICAL print fix (2026-06 fork) — web/webview printed the app page
- printHtml (src/utils/print.ts) on Platform.OS==="web" used Print.printAsync which in the Emergent in-app webview printed the WHOLE APP SCREEN instead of the generated sticker HTML (user's main complaint: "preview image not taken, prints the paper/app"). FIXED: on web we now inject a hidden iframe, write ONLY our HTML into it, and call iframe.contentWindow.print(). Verified via DOM test: iframe printed our '<div class="sheet"> ...64mm...' HTML (ifprint=1) and window.print of the page was NOT called (pageprint=0). Native (Expo Go/build) path unchanged (Print.printAsync). This fixes BOTH labels.tsx and scan-sticker.tsx so print output == preview, N stickers = N selected blocks.

## Scanner speed: 5-second target (2026-06 fork)
- User demanded scan complete in ~5s "at any cost". Benchmarked vision models on 720-900px image: gpt-4o-mini ~4s, gpt-5-nano ~21s, gemini-3.5-flash ~18s, gemini-3-flash-preview ~42s (gemini flash more accurate but too slow). Switched /scan-sticker model gemini-3.7-flash -> openai gpt-4o-mini. Frontend downscales image to 800px before upload. End-to-end measured ~5.4s at 900px (model ~4s + transfer). TRADEOFF: gpt-4o-mini is less accurate on blurry/rotated/dirty labels (may misread part number) — screen already lets user edit part number + every text line before print. Prompt updated to stress reading upright + exact part number.

## Scanner REDESIGN — real-image overlay (2026-06 fork) — user rejected reconstruction
- User demand: printed sticker must look EXACTLY like the original (logo/design/fonts as-is); ONLY the part number changes. From-scratch reconstruction rejected ("you change the logo, won't work").
- NEW approach: use the ACTUAL sticker photo as print background; overlay ONLY a white patch + new part number at AI part_number_box, and a new QR/barcode at code box. Logo/layout/fonts = original pixels.
- Backend /scan-sticker returns: rotation(0/90/180/270), sticker bbox (% of photo), part_number, part_number_box (% of upright label), code{type,x,y,w,h}|null. Model gpt-4o-mini, ~3.6s.
- Frontend scan-sticker.tsx rewritten: crop sticker bbox + rotate upright (expo-image-manipulator) => bgDataUrl; edit ONLY part number; QR/Barcode toggle; preview = real image + overlays; print via generateRichStickerSheetHtml.
- labelSheet.ts StickerTemplate rewritten: {bgDataUrl, aspect, pnBox, pnText, code:{type,value,box}}. templateInner = bg img(fill) + white patch + new PN + new code.
- CAVEATS: best with CLEAR STRAIGHT photo; wrong rotation guess = tilted; AI part_number_box slightly off => patch misalign. NATIVE-only full flow.

## Scanner FINAL (Option A + Save Templates) 2026-06 fork
- User chose Option A (real photo). Complaints fixed: (1) removed the app-generated QR entirely — the ORIGINAL code stays in the photo (copied), only the part number is overlaid (tpl.code=null; CODE TYPE UI removed). (2) print outline removed (scanner prints with showBorder:false). (3) rotation applied via crop+rotate.
- Save Templates workflow (their real vision): backend store-scoped CRUD /api/sticker-templates (POST/GET/DELETE) storing {name,bg_data_url(base64),aspect,pn_box,part_number}. Frontend: "Save this sticker for reuse" button + "SAVED STICKERS" horizontal thumbnail row on scan-sticker.tsx; tap a saved thumb -> loads template (bg+pnBox), user types new part number -> prints. Delete via x on thumb. Backend CRUD verified via curl (save/list/delete ok).
- NOTE: rotation/part_number_box come from gpt-4o-mini and are approximate; best results with a straight, flat, full-frame photo. Full flow native-only (image picker), screen renders on web.

## Scanner REVERTED to clean-generate (B) — user liked the rebuilt version (2026-06 fork)
- User confirmed the earlier "PREVIEW (rebuilt)" clean white sticker was what they wanted (only junk logo thumbnails were wrong). Reverted from image-overlay back to GENERATION: backend prompt returns {aspect, part_number, lines[{text,bold}], code{type}}. Frontend layoutLines() builds a clean, always-upright, non-overlapping white sticker (font shrinks to fit width; code in right column). NO photo pixels, NO logo crops (brand appears as a text line like "HYUNDAI KIA MOTORS"), so nothing tilts and no photo edges.
- Editable: part number + every text line + QR/Barcode toggle. Print with showBorder:false (no outline). ~5s via gpt-4o-mini.
- Save Templates now store JSON.stringify(StickerTemplate) in bg_data_url; openTemplate JSON.parses it back. CRUD unchanged.
- labelSheet StickerTemplate = {aspect, lines:TplLine[], code:{type,value,box}|null}; templateInner renders typed lines + regenerated code on white cell.
- NATIVE-only full flow; screen verified rendering on web with no errors.

## Company Logo Library + slot + margin fix (2026-06 fork)
- Removed unreliable auto-crop logo (was rendering black-box junk). Added Logo Library: backend store-scoped CRUD /api/logos (POST/GET/DELETE) storing {id,name,data_url(base64),store_id}. Frontend scan-sticker: "COMPANY LOGO" row = Add (upload from gallery -> resize 300px -> save) + None + saved-logo thumbnail buttons; tap sets tpl.logo into a fixed top-left slot LOGO_BOX{x:3,y:2,w:34,h:15} and re-flows text below (topPad 20). Logo persists in saved templates (JSON.stringify).
- Margin fix in labelSheet buildSheet: topM now capped to <= leftM (Math.min) so the grid sits near the top with a small margin instead of large vertical-centered top margin (user: excess top margin spoils pre-cut sheets). Scanner prints with showBorder:false (no outline).
- testing_agent iteration_16: 7/7 backend PASS (scan-sticker shape, /api/logos CRUD, /api/sticker-templates CRUD, multi-tenant isolation, categories=22 groups, companies). Frontend logo/scan flow is native-only (image picker) — user tests on phone.

## Manual paper margins (2026-06 fork)
- SheetOptions gained marginTop/marginLeft (mm, null=auto). buildSheet uses them if provided else auto. Added "PAPER MARGIN (mm) — blank = auto" Top/Left inputs + "0/0" button on BOTH scan-sticker.tsx and labels.tsx print screens. User can set page margins down to 0. Verified inputs render+fillable on web, no errors.

## Sticker font +25% + Common Part Catalog (2026-06 fork)
- STICKER FONTS +25% (both sticker generators): (1) AI Scanner `layoutLines()` in scan-sticker.tsx — vertical slot use 0.8→0.92, max cap 9→11.25, char-width estimate 0.55→0.46, min 2.2→2.75. (2) A4 Grid manual sticker `labelInner()` in labelSheet.ts — fs `max(1.6,min(h*0.16,4.2))`→`max(2.0,min(h*0.20,5.25))`. Text stays width-fit so no overflow of the mm cell. NATIVE-only full AI scan flow — user verifies font size on phone (Expo Go/build); web preview can't trigger image picker.
- COMMON PART CATALOG (shared identity, private stock) IMPLEMENTED: new global `catalog` collection {part_number (unique), name, company, category, compatible_vehicles, variant, year, old_number, new_number, first_store, timestamps}. Helper `upsert_catalog()` is ENRICH-ONLY (fills globally-empty fields, never clobbers another store's good data; treats ""/"All" as empty). Called on create_part, update_part, and buy (both create + fill branches). Startup creates unique index + backfills catalog from all existing per-store parts (idempotent). New `GET /api/catalog/{pn}`. `part_status`/`/search` now return `catalog` + new status "IN CATALOG" (part known globally but not in this store). Buy endpoint pre-fills a NEW store part's identity from the catalog when client omits fields.
- Frontend: buy.tsx prefills Name/Category/Variant/Vehicles/Company from `res.part || res.catalog` (shows "Auto-filled from Common Catalog"). part/[pn].tsx NEW PART card becomes "NEW TO YOUR STORE" with a globe banner + catalog Details rows when `data.catalog` exists; "Add to My Store" pre-fills the new-part form from catalog. StatusChip/statusColor handles "IN CATALOG" (info style).
- VERIFIED via API (two fresh stores A+B): A creates part → B `/catalog/{pn}` found=true w/ A's identity; B `/search` = "IN CATALOG", own part=None, own /parts list=0 (isolation intact); B buy inherits company from catalog. Backend restart clean.
- NOTE (handoff correction): test store `teststore1` password is `Test@123` (handoff said `Password@123` — wrong).

## Hyundai/Kia 2-column OEM format + Company selector (2026-06 fork)
- USER GAVE EXACT ZONE SPEC for the Hyundai/Kia OEM label. New `layoutHyundaiKia()` in scan-sticker.tsx arranges scanned lines into zones (matches real sticker):
  - Right-top block: UNIT ASSY / HKMC P/N / SYEC P/N / LOT N/O / (value) / H/W Ver / S/W Ver (H/W & S/W kept on SEPARATE lines per user).
  - Left-top (under logo): HYUNDAI KIA MOTORS.
  - Left-mid: MODEL / TA / IFT ID, then leftover code (e.g. CRCH-23369) stacked below.
  - Right-mid: QR (10mm, via code box y=46).
  - Bottom-center: VBHH ; Bottom full-width: SEOYON ... MADE IN INDIA.
- Classification is content-based with `lastZone` inheritance for continuation/wrapped lines. CRITICAL ORDER: isLeft is checked BEFORE isRight because the MODEL value "SYECIBUS..." contains "SYEC" (would falsely match the right-block regex). Verified via node sim against user's exact lines → all 14 lines land in correct zones, no overlaps.
- COMPANY FORMAT selector added on scan screen (chips: Hyundai, Kia, Maruti Suzuki, Tata, Mahindra, Toyota, Honda, Nissan, Renault, Ford, Volkswagen, Skoda, MG, Datsun, Chevrolet, Fiat, Jeep, Citroen, Isuzu, Other). FORMATTED_COMPANIES=[Hyundai,Kia] use the 2-column preset (marked ★); all others fall back to the generic single-column `layoutLines` (dedicated formats to be added later per user). Company auto-detected from scan text (HYUNDAI/KIA); user can switch, which re-lays-out live from stored rawLines.
- StickerTemplate gained `company`; saved templates persist it (Save button relabeled "Save this format (Company)"), openTemplate restores company + rawLines.
- QR render (templateInner) now uses code.box.y for vertical position (right-aligned 2mm, fixed 10mm) so the format controls where the 10mm QR sits. Preview updated to match. NATIVE-only full scan flow — user tests on phone.

## DataMatrix code for Hyundai/Kia (2026-06 fork)
- User: OEM Hyundai/Kia labels use a DATAMATRIX 2D code (L-finder, no QR eyes), not a QR. Added pure-JS DataMatrix (ECC200) generator `src/utils/dmatrix.ts` using `datamatrix-svg-ts` (`encodeToMatrix` → bit matrix → SVG rects, same crispEdges approach as qr.ts). Installed via `yarn expo install datamatrix-svg-ts` (ESM-only, bundles fine under Metro; verified matrix + screen load).
- New code type `"datamatrix"` across StickerTemplate + scan-sticker. Hyundai/Kia (FORMATTED_COMPANIES) now DEFAULT to DataMatrix on scan and on company-switch; switching away reverts DataMatrix→QR. Added a "DataMatrix" chip in the CODE TYPE toggle (QR / DataMatrix / Barcode). DataMatrix renders as a fixed 10mm square, right-aligned, positioned via code.box.y (same as QR). templateInner branches: barcode = wide box; else (qr|datamatrix) = square 10mm.

## Per-line manual placement (zone editor) + logo position (2026-06 fork)
- USER wants to arrange EACH line himself (front/back/up/down, which column). Replaced the fixed Hyundai/Kia classifier with a ZONE model. TplLine gained `zone`. `ZONES` = L-Top, R-Top, L-Mid, R-Mid, Bottom-C (auto-centered), Bottom — each with x/y0/dy/width/base. `positionLines(zonedLines, aspect)` groups lines by zone in array order and stacks each group (width-fit fonts). `autoZonesHK(raw)` only guesses a STARTING zone per line (same heuristic, isLeft before isRight); the user overrides freely.
- TEXT LINES editor (only for FORMATTED_COMPANIES): each line row now has the text input + up/down reorder buttons + a horizontal chip row to pick its zone. `setLineZone`, `moveLine`, and `editLine` all call `recompute()` which re-positions using each line's current zone (formatted) or the generic single-column `layoutLines` (others) — preserving the user's arrangement.
- LOGO position presets: `LOGO_POSITIONS` (L-Top/Center/R-Top/L-Mid); when a logo is set, a "Logo position" chip row appears (`moveLogo`). For formatted layouts the logo is independent of line zones (moving/toggling logo never disturbs the line arrangement).
- openTemplate: legacy saved templates (no `zone`) are auto-zoned on open so the editor works; templates saved WITH zones keep the exact saved arrangement. Save persists `zone` (whole tpl JSON-stringified).
- NATIVE-only: zone editor shows after a scan or opening a saved template (needs login on device). Logic verified via lint + node sim; web preview can't trigger the image picker.

## Multiple code types + code size control (2026-06 fork)
- User asked for "30+ QR types" + a size control. Added `@bwip-js/generic` and new util `src/utils/codegen.ts` exposing `CODE_TYPES` (11 types verified to encode plain part numbers): QR, DataMatrix, Aztec, Aztec Compact, Han Xin, DotCode, MaxiCode, PDF417, PDF417 Compact, Micro PDF417, Barcode 128. `codeSvg(type,value)` routes qr→qr.ts, datamatrix→dmatrix.ts, barcode→barcode128.ts, rest→bwip-js drawingSVG (try/catch → QR fallback). `svgRatio()` parses viewBox for aspect.
- HONEST LIMIT (told user): iQR, Frame QR, GS1 QR, and QR Model 1 are Denso-proprietary / need GS1 AI syntax — NO open library produces them, so they are not offered. microqr not in this bwip build.
- StickerTemplate.code.type widened to `string`; added `code.sizeMm` (default 10). templateInner now UNIFIED: one renderer for all types — right-aligned, vertical from box.y, size = sizeMm (capped to cell), wide codes (ratio>1.3) keep aspect capped to 60% cell width, `preserveAspectRatio=xMidYMid meet`.
- scan-sticker: CODE TYPE is now a horizontal chip row from CODE_TYPES; added a "Code size" −/+ stepper (5–30mm) via `changeCodeSize`. Preview uses codeSize + codeRatio for accurate sizing. openTemplate restores code.sizeMm. bwip-js verified to bundle & run in Metro/Hermes (screen loads clean, no errors).

## Free-form arrange (select + nudge pad) replaces zone chips (2026-06 fork)
- User wanted: select any lines (single/multi), then move them TOGETHER with ◀▲▼▶ buttons anywhere, resize fonts, and same for logo. Replaced the per-line zone chips + reorder buttons with a SELECTION + NUDGE system operating on absolute x/y (%).
- State: `selLines: Set<number>`, `nudgeStep` (1/2/3/5 chips). Helpers: `toggleLineSel`, `selectAllLines`, `clearLineSel`, `nudgeSel(dx,dy)` (moves all selected lines by dx/dy*step, clamped 0–99), `resizeSel(±0.5)` (font size 2–20), `boldSel` (toggle bold on selection). `editLine` is now text-only (never repositions — preserves the user's manual layout).
- UI: an ARRANGE block with Step chips + All/Clear + a directional pad (up/left[count]/right/down) + A+/A-/B column. TEXT LINES rows now have a ☐/checkbox to select + the text input (zone chips & up/down removed).
- Logo: position presets replaced with a move+resize row (◀▲▼▶ using nudgeStep, and −/+ that scales keeping aspect) via `nudgeLogo`/`resizeLogo`. applyLogo keeps existing box on re-apply (LOGO_START default).
- Initial auto-layout still uses zone model (autoZonesHK/positionLines) to give a sensible starting arrangement; after that everything is free-form nudge. `zone` field retained only for the initial guess. Verified: lint clean, screen loads, bwip bundles.
