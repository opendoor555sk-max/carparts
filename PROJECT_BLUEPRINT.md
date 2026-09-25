# Kabadi Market Hisab — Project Blueprint

This file is the single reference document for this application. Anyone
picking up this project — a new developer, a freelancer, or an AI assistant
(ChatGPT, Claude, etc.) — should start by reading this file. It explains
what the app is, how it is built, everything it currently does, and how
changes get from a code edit to the phone in the owner's hand.

**Owner:** AbdulSalam (scrap auto parts dealership, Anand, Gujarat)
**Repo:** `opendoor555sk-max/carparts` on GitHub
**Working branch:** `conflict_040926_0622`

---

## 1. What this app is

"Kabadi Market Hisab" is a multi-tenant inventory, billing and staff
management app for scrap/used auto-parts dealers. "Multi-tenant" means one
deployment serves many independent stores — each store's data (stock,
customers, vendors, sales) is completely separate from every other store's,
even though they all run on the same app and the same database. A shop
owner signs up, gets their own store, and can add staff accounts under it.

There is also a platform **owner** role (AbdulSalam himself) who can see
and manage every store on the platform — approve new store sign-ups, lock/
unlock stores, view every store's staff and search activity, and move stock
between stores.

## 2. Tech stack

| Layer | Technology |
|---|---|
| Mobile app (frontend) | React Native + Expo (SDK 54), Expo Router (file-based routing) |
| Backend API | Python, FastAPI |
| Database | MongoDB |
| Backend hosting | Render (`kabadi-market-backend.onrender.com`) |
| App distribution | EAS (Expo Application Services) — `eas update` for instant JS-only updates, `eas build` for a new installable APK/IPA |
| Source control | GitHub, edited mostly via GitHub's web "Upload files" page (no local dev machine used day-to-day) |

### Why "eas update" vs "eas build" matters
- **`eas update`** pushes a JS-code-only change to everyone's already-installed
  app *instantly*, without needing a new install. This covers almost every
  change: new screens, new buttons, new logic, text/translation edits,
  backend calls.
- **`eas build`** produces a brand new installable app file (APK for
  Android). This is only needed when something *native* changes — a new
  native permission, a new native library, an app icon/name change, or the
  app version number. Building costs "build minutes" on Expo's free tier
  (limited per month); pushing updates does not.
- Almost all feature work in this app's history has shipped via `eas
  update`, never touching the installed APK at all.

## 3. Architecture

### Multi-tenancy and roles
Every record in the database (a part, a sale, a customer, a vendor entry,
a cash-book row, etc.) is tagged with a `store_id`. Every API call is
scoped to the caller's own `store_id` server-side — a store's staff can
never see another store's data by design, not just by UI hiding.

Roles, from least to most access:
- **staff** — a store employee. Has a specific set of *permissions*
  (e.g. `buy`, `sell`, `requirement`, `search`) assigned by their store's
  admin; sees only the Home-screen tiles their permissions cover.
- **admin** — a store's owner/manager account. Full access within their
  own store: manage staff, see all reports, delete records, etc.
- **super_admin** ("owner") — AbdulSalam's own account. Full access across
  *every* store on the platform: approve new stores, lock/delete stores,
  view all staff and all search activity, move stock between stores.

### Distribution model (why staff sometimes see an "old" app)
The **APK** (the installed app file) rarely changes. What changes
constantly is the **JS bundle** inside it, delivered via `eas update` over
the internet the next time the app is opened with a network connection.
So: install the APK once from the builds page, and every future feature
arrives automatically — no reinstalling needed. If a device seems stuck on
an old version, the fix is: open the app with internet on, wait, and
fully close + reopen it once (the update downloads on one launch and
applies on the next).

## 4. Every screen / feature in the app

Grouped by what they're for. Route = the file in `frontend/app/` that
renders that screen.

### Core inventory & sales (staff, permission-gated)
- **Search** (`/scan?mode=search`) — look up a part by barcode/QR/manual entry
- **Buy** (`/buy`) — record new stock coming in, with barcode/QR scanning
- **Sell** (`/scan?mode=sell`) — record a sale, generates a GST invoice
- **Requirement / Inquiry** (`/scan?mode=requirement`) — log a part a
  customer wants that isn't in stock yet
- **Customers** (`/customers`, `/customer-new`, `/customer/[id]`) —
  customer records, running ledger/balance, payments
- **Vendors** (`/vendors`, `/vendor-new`, `/vendor/[id]`) — supplier records
- **Damaged / Returns** (`/damaged-returns`) — customer returns, vendor
  returns, damaged-stock write-offs
- **Cash Book** (`/cash-book`) — day-to-day cash-in-hand ledger, separate
  from customer/vendor ledgers
- **Purchase Orders** (`/purchase-orders`) — order stock from a vendor
  before it physically arrives; mark received/cancelled by hand
- **Quotations** (`/quotations`) — price estimate for a customer before a
  real sale; accept/reject/mark-converted
- **Stock Hold / Reservations** (`/reservations`) — hold stock aside for a
  specific customer without touching the actual stock count
- **Stock Transfer** (`/stock-transfer`, **owner-only**) — move physical
  stock between two different stores

### Reports & admin (mostly admin/owner)
- **Reports tab** (`(tabs)/reports.tsx`) — sales/purchase/stock summaries
- **Profit Report** (`/profit-report`) — profit margin analysis, Excel export
- **Inventory** (`(tabs)/inventory.tsx`, `/unlinked-stock`) — full stock
  list, Excel export, low-stock and unlinked-stock views
- **Stock Verification** (`/stock-verify`) — physical stock count vs.
  system count reconciliation
- **Activity Log / Audit Log** (`/audit-log`, **admin-only**) — a running
  "who did what, when" trail across the newer features (cash book entries,
  purchase orders, quotations, reservations, stock transfers)
- **Search Logs** (`/search-logs`) — what staff have been searching for
- **Users** (`/users`) — a store's own staff accounts, permissions,
  verify/unverify, view password (admin)
- **Store Profile** (`/store-profile`) — store name/branding for invoices
- **Store Arrangement** (`/store-arrangement`) — physical rack/shelf/wall
  location system for organizing where stock physically sits
- **Categories** (`(tabs)/categories.tsx`) — part category management
- **Limits** (`/limits`) — per-part or global stock limits, low-stock alerts
- **Backup** (`/backup`) — export/import the store's data, Excel export
- **AI Approvals** (`/ai-approvals`) — review/approve AI-assisted research
  results before they're trusted
- **Labels / Stickers** (`/labels`, `/scan-sticker`) — barcode label
  printing and template management
- **Tools** (`/tools`) — a grab-bag of admin utilities

### Platform owner only (`/owner-panel`)
Four tabs in one screen:
- **Stores** — every store on the platform: lock/unlock, delete
- **Requests** — new "open my store" sign-up requests: generate the OTP
  that completes their sign-up, or **reject/delete** a request you don't
  want to act on
- **Staff** — every user account across every store: deactivate/reactivate
- **Logs** — platform-wide search activity, with device GPS location

### Account / auth
- **Login** (`/login`), **Signup** (`/signup`), **Change Password**
  (`/change-password`), **Settings** (`/settings`)
- **Store Locked** (`/store-locked`) — shown if the owner has locked the store

## 5. Backend API reference

The backend is one file: `backend/server.py`. All endpoints are prefixed
`/api` and grouped below by area (method + path):

**Auth & store setup**
`POST /store-requests`, `POST /store-requests/{id}/verify-otp`,
`POST /auth/login`, `GET /auth/me`, `GET/POST /auth/settings`,
`POST /auth/change-password`, `GET/POST /store/profile`

**Platform owner** (`/owner/...`)
`GET /owner/stores`, `POST /owner/stores/{id}/lock`,
`POST /owner/stores/{id}/unlock`, `DELETE /owner/stores/{id}`,
`GET /owner/store-requests`, `POST /owner/store-requests/{id}/generate-otp`,
`DELETE /owner/store-requests/{id}`, `GET /owner/users`,
`POST /owner/users/{id}/deactivate`, `POST /owner/users/{id}/reactivate`,
`GET /owner/device-locations`, `GET /owner/search-logs`

**Store admin** (`/admin/...`)
`GET /admin/stores`, `GET /admin/gps-locations`, `GET/POST /admin/users`,
`DELETE /admin/users/{id}`, `PATCH /admin/users/{id}`,
`POST /admin/users/{id}/verify`, `POST /admin/users/{id}/unverify`,
`GET /admin/search-logs`, `GET /admin/users/{id}/password`

**Catalog & stock**
`GET /companies`, `GET /categories`, `GET /conditions`, `GET /search`,
`GET/POST /parts`, `GET/PATCH /parts/{part_number}`,
`GET /catalog/{part_number}`, `POST /buy`, `POST /sell`,
`GET /invoices/{id}`, `GET /inventory`, `GET /inventory/excel`,
`GET /inventory/low-stock`, `GET /inventory/location-check`,
`GET /inventory/unlinked-stock`, `POST /stock/adjust`,
`PATCH/DELETE /stock/unit/{id}`

**Customers & vendors**
`POST/GET /customers`, `GET/PATCH /customers/{id}`,
`GET /customers/{id}/ledger`, `GET /customers/{id}/ledger/excel`,
`POST /customers/{id}/payments`, `POST/GET /vendors`,
`GET/PATCH/DELETE /vendors/{id}`

**Returns**
`POST /returns/customer`, `POST /returns/damaged`, `POST /returns/vendor`,
`GET /returns`

**Cash Book, Quotations, Purchase Orders, Reservations, Stock Transfer, Audit Log**
`POST/GET/DELETE /cash-book`, `POST/GET/PATCH/DELETE /quotations`,
`POST/GET/PATCH/DELETE /purchase-orders`,
`POST/GET/PATCH/DELETE /reservations`, `POST/GET /stock-transfer`,
`GET /audit-log`

**Stock verification, limits, requirements**
`GET/POST /stock/verification`, `POST/GET/PATCH /requirements`,
`GET/POST /limits/global`, `POST /limits/part`, `GET /limits/{part_number}`,
`POST /limits/low-stock`, `GET /limits/low-stock/{part_number}`,
`POST/GET /known-parts`

**AI research, search history, reports**
`POST/GET /ai/research`, `POST /ai/research/{id}/approve`,
`POST /ai/research/{id}/reject`, `GET /search-history`, `GET /demand`,
`GET /stats`, `GET /transactions`, `POST /transactions/delete`,
`GET /reports/profit`, `GET /reports/profit/excel`

**Backup, labels, uploads**
`GET /backup/export`, `POST /backup/import`, `GET /backup/excel`,
`POST /scan-sticker`, `POST/GET/DELETE /sticker-templates`,
`POST/GET/DELETE /company-formats`, `POST/GET/DELETE /logos`,
`POST /upload`, `GET /files/{path}`

## 6. Repo folder structure

```
carparts/
├── backend/
│   └── server.py          ← the entire backend API (one file)
├── frontend/
│   ├── app/                ← every screen (Expo Router: file path = URL route)
│   │   ├── (tabs)/          ← the 4 bottom-tab screens (Home, Reports, Catalog, Admin)
│   │   ├── customer/[id].tsx, vendor/[id].tsx, invoice/[id].tsx, part/[pn].tsx
│   │   │                    ← dynamic routes (the [id] fills in at runtime)
│   │   └── *.tsx            ← every other screen (buy, sell, cash-book, etc.)
│   ├── src/
│   │   ├── api/client.ts    ← the `api.get/post/patch/del(...)` helper every screen uses
│   │   ├── components/ui.tsx← shared UI pieces (Button, Card, Field, Header, ...)
│   │   ├── context/         ← Auth, Language, Toast — app-wide state
│   │   ├── i18n/translations.ts ← every piece of UI text, in Gujarati/Hindi/English
│   │   ├── theme/           ← colors, spacing, fonts
│   │   └── utils/            ← barcode parsing, printing, etc.
│   ├── app.json             ← app config: name, version, permissions, plugins
│   └── eas.json             ← build profiles (development/preview/production)
└── PROJECT_BLUEPRINT.md     ← this file
```

## 7. How a change actually ships (the workflow used throughout this project)

1. Code is written/edited (by an AI assistant, in chat) and the finished
   file(s) are sent to AbdulSalam.
2. He uploads each file to the exact right path on GitHub, using GitHub's
   web "Upload files" page for that folder (a direct link like
   `.../upload/conflict_040926_0622/backend` for the backend, or
   `.../upload/conflict_040926_0622/frontend/app/(tabs)` for the Home
   screen specifically), then clicks **Commit changes**.
3. On his PC, in Command Prompt, inside the `frontend` folder:
   ```
   git pull origin conflict_040926_0622
   eas update --channel preview --message "describe the change"
   ```
   `git pull` brings the just-uploaded GitHub changes down to his PC;
   `eas update` bundles the current code and pushes it live. **Skipping
   `git pull` is the single most common mistake** — it publishes whatever
   old code was already on the PC, not what was just uploaded.
4. On the phone: open the app, (optionally) tap "Check For Update Now" if
   the debug panel is visible on the login screen, then fully close and
   reopen the app once for the update to apply.

Only when a **native** change is needed (new permission, new native
library, app icon, version bump) does step 3 change to `eas build
--platform android --profile preview` instead, followed by downloading and
installing the new APK from
`https://expo.dev/accounts/andulsalam77/projects/frontend/builds`.

## 8. Handing this project to someone else (developer or AI)

Give them:
1. **This file** — read it first, it explains everything above.
2. **Repo access** — `opendoor555sk-max/carparts`, branch `conflict_040926_0622`.
3. **The Expo/EAS account** — `andulsalam77` on expo.dev, needed to publish
   updates or trigger builds.
4. **The Render backend URL** — `kabadi-market-backend.onrender.com` (and
   dashboard access if the backend itself ever needs redeploying/restarting
   outside of a normal `eas update`, which only touches the frontend).

A new developer or an AI assistant reading this file plus the two main code
files (`backend/server.py` and any relevant screen under `frontend/app/`)
has everything needed to add a feature, fix a bug, or remove something —
the same way every feature in section 4 above was actually built.

---
*Keep this file up to date: whenever a meaningfully new feature is added,
add one line for it in section 4 (and section 5 if it has new API
endpoints). This file is only useful if it stays current.*
