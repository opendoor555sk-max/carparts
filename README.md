# Kabadi Market — Kotlin (native Android) app

New, small and fast native Android app for Kabadi Market, written in Kotlin + Jetpack Compose.
It uses the **same server** as the old Expo app (`kabadi-market-backend.onrender.com`),
so all stock, parts, customers and users stay the same. Login with the same username/password.

## How to get the APK
Every push to this branch builds the app automatically on GitHub:
**Releases** (right side of the repo page) → newest "Kabadi Market v2.0.x" → tap the `.apk` file.

## Phase 1 (this version)
- Login (same users as before)
- Home: search / scan a part, quick stats
- Part details: status (IN STOCK / KNOWN / NEW…), purchase limit, stock pieces with rack location
- Inventory: all stock, search + condition filter
- Buy: add a piece to stock (condition, price, barcode, wall/rack/shelf), purchase-limit warning
- Sell: pick the piece, price, cash or khata customer, GST bill (18%)

## Coming next
Customers & khata, vendors, cash book, reports, quotations, purchase orders,
labels/sticker printing, photos, stock transfer/verify, users & owner panel, Hindi/Gujarati.

## Project layout
- `app/src/main/java/com/kabadimarket/app/data` — server connection, login session, barcode scanner
- `app/src/main/java/com/kabadimarket/app/ui` — screens
- `.github/workflows/build-apk.yml` — builds the APK on GitHub

Note: `app/kabadi-release.jks` is a signing key for direct (side-load) installs only.
Before publishing on the Play Store, create a private key and keep it out of the repo.
