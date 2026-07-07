# Publishing Štih to Google Play (organization account)

## 0. What you need before starting (company account!)

You said you want to publish through a company. Google requires **organization
accounts** to be verified:

- A **D-U-N-S number** for the company (free from Dun & Bradstreet:
  https://www.dnb.com/duns.html — can take up to 30 days if the company
  doesn't have one yet, so request it FIRST).
- Company registration details matching the D-U-N-S record.
- A company email address on the company's domain (not gmail) and a company
  website — Google checks these during verification.
- A Google account to own the developer account (make a dedicated one like
  play@<company-domain> rather than a personal account).

## 1. Create the developer account

1. Go to https://play.google.com/console/signup
2. Sign in with the company Google account.
3. Choose **"An organization"** (NOT "Yourself") — this cannot be changed later.
4. Enter the D-U-N-S number and company details; pay the one-time **$25 USD** fee.
5. Complete identity/organization verification (can take a few days).

## 2. Host the privacy policy

Put `privacy-policy.md` (rendered as a web page) somewhere public, e.g.
`https://<company-domain>/stih/privacy` or a GitHub Pages page from the repo.
You'll need the URL during setup.

## 3. Create the app

Play Console → **Create app**:
- App name: `Štih — Tarok zapisovalnik`
- Default language: Slovenian (sl)
- App or game: Game → Card
- Free or paid: Free (a free app can never be made paid later; paid→free is possible)

## 4. Fill in the required declarations (Dashboard → "Set up your app")

- **Privacy policy**: paste the URL from step 2.
- **App access**: all functionality available without login.
- **Ads**: no ads.
- **Content rating**: fill the questionnaire (card game, no gambling with real
  money → typically PEGI 3). Note: it asks about "simulated gambling" — tarok
  scorekeeping is NOT gambling, answer no.
- **Target audience**: 18+ or 13+ is simplest (avoids families-policy extras).
- **Data safety**: declare "No data collected, no data shared" (true — the app
  has no internet permission).

## 5. Store listing (Main store listing)

Everything is prepared in this folder:
- Texts: `listing.md` (name, short + full description)
- App icon: `icon-512.png`
- Feature graphic: `feature-1024x500.png`
- Phone screenshots: `screenshot-*.png` (need at least 2, these are 1080×2400)

## 6. Upload the build

- Play Console → **Testing → Internal testing** → Create release.
- On first upload, accept **Play App Signing** (Google keeps the app signing
  key; your `release.keystore` becomes the *upload key*). Recommended — say yes.
- Upload `app/build/outputs/bundle/release/app-release.aab`.
- Add your own email as an internal tester, install via the opt-in link, sanity-check.
- Then **Production → Create release**, reuse the same bundle, submit for review.
- First review typically takes a few days. New personal/org accounts must run
  a closed test with 12+ testers for 14 days ONLY for personal accounts created
  after Nov 2023 — organization accounts are exempt from that requirement.

## 7. Future updates

1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. `./gradlew bundleRelease`
3. Upload the new `.aab` to a new release.

## CRITICAL: back these up (outside this machine!)

- `release.keystore` + `keystore.properties` (repo root, gitignored).
  If you enroll in Play App Signing, losing the upload key is recoverable
  (you can request an upload-key reset), but back it up anyway.
- The Play Console owner account credentials.
