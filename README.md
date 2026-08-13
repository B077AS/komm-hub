<h1 align="center">
  <img src="src/main/resources/static/logo.png" alt="Komm logo" width="80"><br>
  komm-hub
</h1>

<p align="center">
  <b>The central hub server for <a href="https://kommvoice.com">Komm</a> — a free, self-hosted voice, video &amp; text chat platform.</b><br>
  Accounts · Friends &amp; DMs · Server directory · Certificate authority · The kommvoice.com website
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white">
  <img alt="Spring Boot 4" src="https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-required-4169E1?logo=postgresql&logoColor=white">
  <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue">
</p>

---

## What is Komm?

Komm is a modern chat platform built around a simple idea: **your community's messages and voice traffic belong on hardware you control.** Every community runs on its own self-hosted server — crystal-clear WebRTC voice channels, HD screen sharing, rich messaging, soundboards, roles & permissions, moderation tools and global hotkeys — without handing your conversations to anyone else. Free, no ads, no tracking, on Windows 10/11 and Linux (both X11 and Wayland, with native PipeWire support).

The platform has three pieces — you choose how many to run:

| Piece | Role | Who runs it |
|---|---|---|
| [komm](https://github.com/B077AS/komm) (+ [komm-launcher](https://github.com/B077AS/komm-launcher)) | Desktop client for Windows & Linux, kept up to date by the launcher | Everyone |
| [komm-server](https://github.com/B077AS/komm-server) | A community's own server: channels, messages, voice rooms, permissions. One JAR, embedded database | Community owners |
| **komm-hub** (this repo) | The network's directory: accounts, friends, DMs, and the CA that vouches for servers | Almost nobody — see below |

> **You probably don't need to run this.** Most people just use the official hub at [kommvoice.com](https://kommvoice.com) — download the client, create an account, join or host community servers. Running your own hub means running a **fully independent Komm network** with its own accounts. It's aimed at developers and tinkerers.

## What the hub does

- **Accounts & identity** — registration with email verification, JWT (ES384) sessions
- **Friends & direct messages** — friend lists, DMs with editing, reactions, GIFs and file attachments, delivered over WebSocket
- **Server directory & certificate authority** — community servers register here; the hub signs their X.509 certificates and issues short-lived connection tickets to clients
- **Public website** — the server-rendered pages (home, download, register, dashboard, invites) that power kommvoice.com

What the hub deliberately does **not** do: channel messaging, voice, files and permissions all live on each community's own komm-server. **The hub never sees your community's content.**

## Architecture

```
┌────────┐   60-second ticket    ┌───────────────┐
│ Client │ ────────────────────► │  Komm Server  │  ← channels, messages,
└───┬────┘                       │ (komm-server) │    voice, permissions
    │                            └───────┬───────┘
    │ account, friends, DMs             │ X.509 mutual auth (mTLS)
    ▼                                   ▼
┌──────────────────────────────────────────────┐
│              komm-hub  ·  CA                 │  ← accounts, friends, DMs,
│  accounts · directory · certificate signing  │    directory, website
└──────────────────────────────────────────────┘
```

**Server (installation) lifecycle:**

1. A user creates an installation from the [Komm client](https://github.com/B077AS/komm) — the hub validates a CSR (P-384 EC key) generated there and issues a single-use setup token, shown back in the client as a verification code.
2. The user gets the generic server JAR running (bare `java -jar`, or as a service via [komm-server-launcher](https://github.com/B077AS/komm-server-launcher)) and supplies that verification code to it.
3. On first start, the JAR presents its CSR and setup token; the hub acts as a certificate authority (BouncyCastle), signs the certificate and marks the installation verified. The certificate doubles as the server's **TLS identity** — from its next start the installation serves HTTPS/WSS with it.
4. The installation connects back over WebSocket (`/ws/installations`) and goes **online**, reporting whether it serves TLS — clients pick `wss://` or legacy `ws://` accordingly.

**A client joining a community server:**

1. The client asks the hub for a ticket (`POST /api/servers/{serverId}/ticket`). The hub verifies membership, checks that the installation is online and its certificate is not revoked.
2. The hub issues a short-lived (**60 s**) single-purpose JWT ticket; the client connects **directly** to the community's server with it, over TLS validated against the hub CA. Messages never pass through the hub.

**WebSockets:** two separate endpoints with distinct session managers — `/ws` for app clients, `/ws/installations` for server JARs. Message types are dispatched to handler beans auto-registered at startup; some features (permissions, voice presence, moderation) are proxied hub → installation via `CompletableFuture` pending registries.

## Security model

Security isn't a feature bolted on — it's the architecture:

- **X.509 mutual authentication** — every self-hosted server proves its identity with a certificate signed by the hub's built-in CA (P-384 elliptic curve).
- **Automatic TLS for every server** — that same hub-signed certificate is the server's HTTPS/WSS identity. Clients fetch the hub CA (public `GET /api/auth/ca`) and validate a server's certificate against it, verifying the installation's identity embedded in the certificate instead of a hostname — so community servers get real transport encryption on a bare IP, with no domain name and no third-party certificate.
- **ES384 signed tokens** — sessions use modern elliptic-curve signatures. No shared secrets, nothing to leak.
- **Short-lived connection tickets** — joining a server uses a single-purpose ticket that expires in 60 seconds, verified against membership and certificate status.
- **API rate limiting** — token-bucket limits on `/api/**` (Bucket4j), keyed per user for authenticated calls and per client IP for public ones, guarding login, registration, email sending and your GIF/CA resources against abuse. See [Rate limiting](#rate-limiting--running-behind-a-proxy).
- **Data stays with you** — channel messages, voice and files live on the community's server; the hub never sees them.

The hub's EC P-384 key pair (generated in `keys/` on first boot) is both its JWT signing key and its CA identity — **every certificate in your network chains to it**.

## Client & launcher updates

The hub is also where the [komm](https://github.com/B077AS/komm) client and [komm-launcher](https://github.com/B077AS/komm-launcher) launcher get their update information from — `ClientUpdateController` and `ClientLauncherController` serve `/api/client/**` and `/api/launcher/**`, both deliberately **public** (no login — see `WebConfig.publicSecurityFilterChain`, since neither the launcher nor a freshly-installed client has a session yet).

Both are backed by a scheduled sync service that polls the relevant repo's GitHub releases directly, rather than requiring anyone to manually place a jar on the server:

| Endpoint | Backed by | What it does |
|---|---|---|
| `GET /api/client/latest` / `/api/client/download` | `ClientReleaseSyncService` | Polls [komm](https://github.com/B077AS/komm)'s releases for the `komm-<version>.jar` asset, mirrors it locally, serves `{ version, sha256, downloadUrl }` |
| `GET /api/launcher/latest?os=` / `/api/launcher/download?os=` | `LauncherReleaseSyncService` | Polls [komm-launcher](https://github.com/B077AS/komm-launcher)'s releases for its two per-OS artifacts, mirrors them locally, serves the same shape per-platform |

- **Polling, not pushing.** Every `kommhub.github.*.poll-interval-ms` (30 s by default) the hub does a conditional `GET .../releases/latest` (`If-None-Match`); GitHub doesn't count an unchanged (`304`) response against the rate limit, so even the default interval stays well inside the unauthenticated 60/hour budget — a `kommhub.github.token` (no scopes needed, just raises the limit to 5000/hour) is optional but recommended.
- **Every download is verified twice** where possible: against the checksum GitHub itself reports for that release asset (`digest`), and by confirming the downloaded jar can state its own version (`client.version` / `launcher.version` in its bundled `app.properties`) matching the release tag. A release published without its asset yet, a GitHub hiccup, or a failed verification never touches what's already being served — the sync just retries on the next tick.
- **Windows and Linux need different launcher artifact *types*, not just different natives.** The launcher's own code is a discrete swappable jar on Windows, but an AppImage is one opaque unit on Linux — so the Linux artifact is a full standalone AppImage, which can't be introspected as a zip the way a jar can. It's verified only against GitHub's digest; its version is tracked via a small sidecar `.version` file written alongside it (see `LauncherReleaseSyncService` for the reasoning, or the launcher repo's README for the client-side half of this — the client is what actually performs the swap, once per start).

### Configuration

| Property | Default | Purpose |
|---|---|---|
| `kommhub.client.jar.path` | `jar/komm-client.jar` | Where the synced client jar is mirrored |
| `kommhub.github.client.owner` / `.repo` | `B077AS` / `komm` | Client repo to poll |
| `kommhub.launcher.jar.windows.path` | `jar/komm-launcher-windows.jar` | Where the synced Windows launcher jar is mirrored |
| `kommhub.launcher.appimage.linux.path` | `jar/komm-launcher-linux.AppImage` | Where the synced Linux launcher AppImage is mirrored |
| `kommhub.github.launcher.owner` / `.repo` | `B077AS` / `komm-launcher` | Launcher repo to poll |
| `kommhub.github.client.poll-interval-ms` | `30000` | Shared poll interval for both sync services |
| `kommhub.github.token` | — | Optional PAT (no scopes needed) — set in `application-local.properties` |

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 4 (Web MVC, Security, WebSocket, Data JPA, Validation, Mail) |
| Database | PostgreSQL (schema auto-managed by Hibernate, `ddl-auto=update`) |
| Auth & crypto | JJWT (ES384 JWTs), BouncyCastle (X.509 CA / CSR signing) |
| Website | Thymeleaf + Layout Dialect (SSR), Material Design Icons (purged at build time via PurgeCSS) |
| Build | Maven (`frontend-maven-plugin` pulls Node/npm automatically for the icon/font pipeline) |
| Rate limiting | Bucket4j (token buckets) backed by an in-memory Caffeine store |
| Misc | Lombok, Gson, Commons IO, Ikonli MaterialDesign2 (badge icon validation, kept in sync with the client) |

## Requirements

- **Java 21**
- **PostgreSQL** (any recent version — the hub auto-manages its schema)
- An **SMTP account** for verification emails (any provider)

## Setup

1. Create a PostgreSQL database (e.g. `komm`).
2. Copy `src/main/resources/application-local.properties.example` to `src/main/resources/application-local.properties` and fill in:
   - **database credentials** (required)
   - **SMTP credentials** (required — registration sends verification codes)
   - optionally: a [Klipy](https://klipy.com) API key for GIF search, closed-beta settings, site overrides
3. Build and run:

```bash
mvn clean package -DskipTests
java -jar target/komm-hub-*.jar
```

The hub starts on port **8085**. On first boot it generates its EC P-384 key pair in `keys/` — **back this directory up**: it is the identity of your hub and the CA that all your installations' certificates chain to. Lose it and every server in your network has to re-verify.

`application-local.properties` is gitignored on purpose: it holds your credentials and deployment-specific settings. Everything in the committed `application.properties` is a default you can override there.

> **Running from an IDE (frontend assets):** `mvn package` and `mvn spring-boot:run` run the icon/font pipeline automatically — the `frontend-maven-plugin` purges the Material Design icon CSS and copies the webfonts into `src/main/resources/static/` during the `generate-resources` phase. Those outputs live in the source tree and survive `mvn clean`, so you rarely think about them. The one exception: on a **fresh clone**, the font files are gitignored (only the purged CSS is committed), so if you launch the app by **running the main class directly in your IDE** — which bypasses Maven — the website's icons will be missing. Run `mvn generate-resources` once (or build/run through Maven) to generate them; it persists afterward. This affects only the bundled website, never the API or the desktop client.

### Configuration at a glance

| Property | Required | Purpose |
|---|---|---|
| `spring.datasource.*` | ✅ | PostgreSQL connection |
| `spring.mail.*` | ✅ | SMTP for verification codes & beta requests |
| `klipy.api-key` | — | GIF search in the client (empty picker without it) |
| `komm.beta.enabled` | — | Require an invite key to register (off by default) |
| `komm.beta.request-recipient` | — | Where beta access requests are emailed (default: SMTP username) |
| `komm.site.*` | — | Base URL, GitHub repo links and download links for the bundled website |
| `komm.ratelimit.enabled` | — | Master switch for API rate limiting (on by default) |
| `komm.ratelimit.enforce` | — | `true` returns HTTP 429 on breach; `false` is shadow mode (log only) |
| `server.forward-headers-strategy` | — | `framework` behind a reverse proxy so limits see the real client IP |
| `kommhub.github.*` | — | Client & launcher GitHub-release sync — see [Client & launcher updates](#client--launcher-updates) |

## A note on the bundled website

The Thymeleaf pages under `src/main/resources/templates/` are the **kommvoice.com showcase site** — hero, features, downloads, FAQ and all. They are not a white-label, ready-to-brand product: they exist to show how the hub works and to give passionate developers a working starting point. If you self-host a hub, expect to **fork and personalize** them (texts, branding, links, legal pages). The functional pages (register, login, verify-email, dashboard, invite) work out of the box; the marketing pages talk about Komm itself.

Basic customization without touching templates is possible via the `komm.site.*` properties — see `application-local.properties.example`.

## Closed beta mode

The official hub is currently in **closed beta**: registration requires an invite key. This is optional and **off by default** for self-hosted hubs.

- Enable with `komm.beta.enabled=true` in `application-local.properties`.
- Keys are generated by a `SUPER_ADMIN` user from the website dashboard (`/dashboard`, or `POST /api/admin/beta-keys`). Promote your account by setting `role = 'SUPER_ADMIN'` on your row in the `users` table.
- Each key (`KOMM-XXXX-XXXX-XXXX`) is single-use and gets bound to the account that registers with it.
- Visitors without a key can request access from the website; requests are emailed to `komm.beta.request-recipient`.
- Accounts that never verify their email are deleted after 7 days (`app.unverified-accounts.retention-days`), freeing their username/email and the beta key they consumed.

## Rate limiting & running behind a proxy

The hub applies token-bucket rate limits (Bucket4j, backed by an in-memory Caffeine store) to every `/api/**` endpoint via `RateLimitFilter`. Authenticated requests are keyed **per user**; public ones (login, registration, invite lookups, client download) **per client IP**. Each endpoint group has its own bucket, so exhausting one never blocks another. Limits are generous for real usage and tight only where abuse is costly — credential brute-forcing, account/email spam, and your GIF-provider and CA resources.

- **`komm.ratelimit.enabled`** (default `true`) — master switch; set `false` to disable entirely.
- **`komm.ratelimit.enforce`** (default `true`) — when `true`, breaches get HTTP `429` with a `Retry-After` header. Set `false` for **shadow mode**: breaches are only logged (`RATE LIMIT (shadow) would block …`), letting you watch real traffic and tune limits before enforcing.

**Behind a reverse proxy:** per-IP limits are only correct if the hub sees the real client IP, not the proxy's. `application.properties` sets `server.forward-headers-strategy=framework` so the hub trusts the `X-Forwarded-For` header from your proxy (e.g. nginx). This is only safe when port **8085 is reachable *only* through the proxy** — never expose it directly, or clients could spoof that header to dodge the limits.

## Related repositories

| Repo | What it is |
|---|---|
| [komm](https://github.com/B077AS/komm) | Desktop client (JavaFX, Windows & Linux) |
| [komm-launcher](https://github.com/B077AS/komm-launcher) | Auto-updating launcher — Windows installer & Linux AppImage |
| [komm-server](https://github.com/B077AS/komm-server) | Self-hosted community server (single JAR, embedded database) |
| komm-hub | This repo — accounts, DMs, server directory, CA, website |

## FAQ

**Is Komm really free?** Yes — the client, launcher, server and hub are all free. No ads, no tracking, no paid tiers.

**Do I have to host anything to use Komm?** No. Download the client, create an account and join communities via invite links. Hosting is for communities that want full ownership of their data.

**Can I run my own hub?** Yes — that's this repo. Unlike komm-server (one JAR, zero config), the hub needs PostgreSQL, SMTP credentials and a properties file. See [Setup](#setup).

## License

This project is licensed under the [MIT License](LICENSE).
