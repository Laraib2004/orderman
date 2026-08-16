# Ordrino

Android point-of-sale app for restaurants, bars and cafés. Waiters take orders at the table, the kitchen/bar sees them live, and payment is taken on the same phone via **Stripe Tap to Pay** (or in cash) — with Italian fiscal receipts issued automatically through **A-Cube**.

The app is multi-tenant: every user belongs to a `restaurantId`, and all data, Stripe keys and backend settings are scoped to that restaurant.

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Project layout](#project-layout)
- [Architecture](#architecture)
- [Roles & navigation](#roles--navigation)
- [Firestore data model](#firestore-data-model)
- [Backend API](#backend-api)
- [Getting started](#getting-started)
- [Building & releasing](#building--releasing)
- [In-app auto-update](#in-app-auto-update)
- [Troubleshooting](#troubleshooting)

---

## Features

**Ordering**
- Table map with status (Available / Occupied) and running total per table
- Menu browsing with category filter chips, per-item availability
- Incremental ordering: only newly added quantities are pushed to the kitchen queue
- Order summary per table: edit quantities, delete items, **move/merge items to another table**

**Kitchen & bar**
- Live order queue (Firestore snapshot listener), sorted by status priority
- Status flow `New → Preparing → Ready → Served`
- Separate roles for kitchen and bar preparers

**Payments**
- Stripe Terminal **Tap to Pay** on NFC-capable Android devices
- Bluetooth/internet reader discovery as an alternative
- Manual capture flow: create intent → collect → capture
- Cash payments
- Tipping dialog before capture
- Split-friendly: pay part of a table's order

**Fiscal / receipts (Italy)**
- Fiscal receipt creation via A-Cube (sandbox + production)
- VAT rate codes per menu item; tips booked as `N2`
- Receipt QR code screen — customer scans to open the hosted receipt
- **Void receipt** support, with the voided receipt getting its own public URL
- Today's receipt history per table + archived orders

**Admin**
- Menu management (create/edit items, price, category, VAT code, availability, image)
- Menu items are synced to Stripe as products (`prodId`)
- Table management (number, capacity, section)

**Ops**
- In-app auto-updater: checks Firestore for a newer `version_code` and installs the APK
- GitHub Actions builds and signs the release APK on every push to `main`

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 11 |
| Min / target / compile SDK | 26 / 33 / 34 |
| Build | Gradle 8.12, AGP 8.10.1 |
| UI | AppCompat + Material Components, ViewBinding, RecyclerView, CardView |
| Auth | Firebase Authentication (email/password) |
| Database | Cloud Firestore (+ FirebaseUI Firestore adapters) |
| Payments | Stripe Terminal SDK 4.5.0 (`stripeterminal`, `-taptopay`, `-core`) |
| Networking | `HttpURLConnection` (Stripe/backend calls), OkHttp, Gson |
| QR codes | ZXing (`core` + `zxing-android-embedded`) |
| Backend | Node.js / Express 5 on Render, Firebase Admin SDK, Stripe SDK, A-Cube API |

---

## Project layout

```
Orderman2/                          # Android Studio project (this repo)
├── app/
│   ├── google-services.json        # Firebase config — not in git
│   └── src/main/
│       ├── java/com/ordrino/orderman/
│       ├── res/                    # layouts, values, values-night, values-w600dp/w1240dp
│       └── AndroidManifest.xml
├── .github/workflows/              # CI: build + sign release APK
└── build.gradle / settings.gradle
```

Related material lives outside this repo, under `OneDrive/Dokumente/Ordrino/`:

```
Ordrino/
├── backend/stripe-terminal-backend/   # Express server (server.js, crypto.js)
├── documentation/                     # A-Cube / gov-it API spec
├── firebase/                          # Firebase Admin service account key
├── website/item-pay-pro/              # marketing / web front
└── ordrino-release-signed/            # signed APK output
```

### Source map

| Area | Classes |
|---|---|
| Auth & routing | `LoginActivity`, `MyApplication` |
| Dashboards | `AdminDashboardActivity`, `WaiterDashboardActivity`, `PreparerDashboardActivity` |
| Ordering | `OrderTakingActivity`, `OrderSummaryActivity`, `OrderSummaryAdapter`, `MenuAdapter`, `MenuItemAdapter`, `CategoryFilterAdapter` |
| Kitchen | `OrderQueueActivity`, `PreparerOrderAdapter`, `OrderAdapter` |
| Payments | `PaymentActivity`, `DiscoverReadersActivity`, `CustomConnectionTokenProvider`, `CustomTapToPayReaderListener` |
| Receipts | `InvoiceQRCodeActivity`, `HistoryReceiptActivity`, `ArchivedOrdersActivity`, `ReceiptAdapter` |
| Admin | `MenuManagementActivity`, `AddEditMenuItemActivity`, `MenuManagementItemAdapter`, `TableManagementActivity`, `AddEditTableActivity`, `TableAdapter` |
| Models | `Order`, `OrderItem`, `MenuItem`, `Table`, `Receipt` |
| Updates | `AppUpdater`, `DownloadReceiver` |
| Utils | `WrapContentLinearLayoutManager` |

> `CustomConnectionTokenProvider` is the single gateway to the backend — it owns `/connection_token`, `/create_payment_intent`, `/capture_payment_intent`, `/cash_payment`, `/void_receipt` and `/create-update-product`.

---

## Architecture

```
Android app ──► Firebase Auth        (who am I, which restaurant)
            ──► Cloud Firestore      (menu, tables, orders, receipts — realtime)
            ──► Ordrino backend      (Stripe secrets never touch the device)
                     │
                     ├─► Stripe      (connection tokens, PaymentIntents, products)
                     ├─► A-Cube      (fiscal receipts, voids)
                     └─► Firestore   (Admin SDK — receipt records, public lookup)
```

Key point: the device never holds a Stripe secret key. Each restaurant's `stripe_secret_key` is stored **encrypted** in its Firestore document and decrypted per request by the backend (`crypto.js`, AES with `ENCRYPTION_KEY`). The app only sends `restaurant_id`.

The backend base URL is also per-restaurant — read from `restaurants/{id}.api_domain` and passed between activities as `EXTRA_BACKEND_URL` (default deployment: `https://ordrino-backend.onrender.com`).

---

## Roles & navigation

`LoginActivity` reads `users/{uid}` and routes on `role`:

| Role | Lands on | Can do |
|---|---|---|
| `admin` | `AdminDashboardActivity` | Manage menu, manage tables |
| `waiter` | `WaiterDashboardActivity` | Tables → take order → summary → payment |
| `kitchen_preparer` | `PreparerDashboardActivity` | Order queue, advance item status |
| `barkeeper_preparer` | `PreparerDashboardActivity` | Order queue, advance item status |

If `role` or `restaurantId` is missing, the user is signed out.

Typical waiter flow:

```
Tables → OrderTaking → OrderSummary → DiscoverReaders → Payment → InvoiceQRCode
                            └─► cash payment ───────────────────────┘
```

---

## Firestore data model

```
users/{uid}
  role            "admin" | "waiter" | "kitchen_preparer" | "barkeeper_preparer"
  restaurantId

restaurants/{restaurantId}
  name, address, city, province, country
  vat_number, recipient_code          # Italian fiscal identity
  api_domain                          # backend base URL for this tenant
  stripe_secret_key                   # AES-encrypted, backend-only

  menuItems/{itemId}
    name, description, price, category, type,
    available, imageUrl, taxCode, prodId   # prodId = Stripe product id

  tables/{tableId}
    number, capacity, status, section,
    totalPrice, currentOrderId, activeOrderQueueId

    currentOrder/{orderItemId}
      menuItemId, name, price, quantity, category, type, status

    historyReceiptToday/{receiptId}
      url, timestamp, voided

  orderQueue/{queueId}
    tableId, tableNr, orderedItems[], status, timestamp, totalPrice

config/app_update
  version_code, apk_url
```

Status vocabularies: items `Sent → Preparing → Ready → Served`; tables `Available` / `Occupied`.

---

## Backend API

Express server, all routes tenant-scoped by `restaurant_id` in the body.

| Method | Route | Purpose |
|---|---|---|
| POST | `/connection_token` | Stripe Terminal connection token |
| POST | `/create_payment_intent` | PaymentIntent, `card_present`, `capture_method: manual` |
| POST | `/capture_payment_intent` | Capture + fiscalize (electronic) |
| POST | `/cash_payment` | Record + fiscalize a cash sale |
| POST | `/create-update-product` | Create/update the Stripe product for a menu item |
| POST | `/void_receipt` | Void a fiscal receipt, return new receipt URL |
| GET | `/public/receipt/:uuid` | Public receipt page behind the QR code |

Required environment variables:

```
PORT                        # default 3000
FIREBASE_SERVICE_ACCOUNT    # full service-account JSON, as a string
ENCRYPTION_KEY              # 32-byte hex key for stripe_secret_key decryption
ACUBE_API_URL               # default https://api-sandbox.acubeapi.com
NODE_ENV                    # 'production' switches A-Cube login to the live host
```

Run locally:

```bash
cd Ordrino/backend/stripe-terminal-backend
npm install
node server.js
```

---

## Getting started

**Prerequisites**
- Android Studio (AGP 8.10.1 / Gradle 8.12), JDK 17
- A physical Android device — **API 26+, NFC required**. Tap to Pay does not work on emulators.
- Firebase project with Auth + Firestore
- Stripe account with Terminal / Tap to Pay enabled

**Steps**

1. Clone and open `Orderman2` in Android Studio.
2. Drop your `google-services.json` into `app/`. It is gitignored — get it from Firebase Console → Project settings → Your apps (`com.ordrino.orderman`).
3. `local.properties` is generated by Android Studio; make sure `sdk.dir` points at your SDK.
4. In Firestore, create:
   - `restaurants/{id}` with `name`, `api_domain`, `vat_number`, and the encrypted `stripe_secret_key`
   - `users/{uid}` with `role` and `restaurantId` for each Firebase Auth user
   - at least one document in `tables` and `menuItems`
5. Deploy (or run) the backend and point `api_domain` at it.
6. Run the app on a real device and log in.

**Permissions the app requests:** internet, network state, fine/coarse location (required by Stripe Terminal), NFC, Bluetooth (scan/connect, plus legacy up to SDK 30), and install-packages for the auto-updater.

---

## Building & releasing

```bash
./gradlew assembleDebug
./gradlew assembleRelease      # unsigned locally; minify is off
```

CI (`.github/workflows/android_build.yml`) runs on every push to `main`: JDK 17 → `assembleRelease` → signs with `ilharp/sign-android-release` → uploads the `ordrino-release-signed` artifact.

Required repository secrets:

| Secret | Meaning |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | keystore file, base64-encoded |
| `KEY_ALIAS` | signing key alias |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password |

To ship an update, bump `versionCode`/`versionName` in `app/build.gradle`, let CI build and sign, upload the APK somewhere public, then update `config/app_update` in Firestore.

---

## In-app auto-update

`AppUpdater` runs on each dashboard's `onCreate`:

1. Reads `config/app_update` → `version_code`, `apk_url`.
2. If `version_code > BuildConfig.VERSION_CODE`, shows a non-cancellable "New Update Available" dialog.
3. `DownloadManager` fetches the APK into the app's external files dir.
4. `DownloadReceiver` catches `DOWNLOAD_COMPLETE` and launches the installer through `FileProvider` (`${applicationId}.provider`).

---

## Troubleshooting

**"Restaurant ID not found"** — the `users/{uid}` document is missing `restaurantId` (or `role`). The app signs out on purpose here.

**"Stripe key not configured for this restaurant"** — `restaurants/{id}.stripe_secret_key` is absent, or was stored unencrypted / encrypted with a different `ENCRYPTION_KEY` than the backend holds.

**Reader discovery finds nothing** — check that location permission is granted at runtime (Terminal needs it even for Tap to Pay), the device has NFC on, and you're on a physical device.

**Update dialog never appears** — `version_code` in Firestore is a number, and must be strictly greater than the installed `versionCode`; `apk_url` must be directly downloadable.

**First backend call is slow** — the Render free tier cold-starts; `/connection_token` can take several seconds after idle.

**Hardcoded placeholder** — `InvoiceQRCodeActivity` still contains a `https://your-backend.com/...` comment; the live value comes from `api_domain`.
