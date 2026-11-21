<h1 align="center">
  <img src="src/main/resources/static/logo.png" alt="Komm logo" width="80"><br>
  komm-hub
</h1>

<p align="center">
  <b>The central hub for <a href="https://kommvoice.com">Komm</a> - a free, self-hosted voice, video &amp; text chat platform.</b><br>
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

Komm is a chat platform built around one idea: **your community's messages and voice traffic belong on hardware you control.** Every community runs on its own self-hosted server; the hub only handles accounts and introductions.

| Piece | Role | Who runs it |
|---|---|---|
| [komm](https://github.com/B077AS/komm) | Desktop client (Windows & Linux) and its launcher | Everyone |
| [komm-server](https://github.com/B077AS/komm-server) | A community's own server: channels, messages, voice, permissions. Windows installer, Linux script or Docker image | Community owners |
| **komm-hub** (this repo) | Accounts, friends, DMs, the server directory, and the CA that vouches for servers | Almost nobody |

> **You probably don't need to run this.** Most people use the official hub at [kommvoice.com](https://kommvoice.com). Running your own hub means running a **fully independent Komm network** with its own accounts. It's aimed at developers and tinkerers.

## What the hub does

- **Accounts & identity** - registration with email verification, ES384 JWT sessions
- **Friends & direct messages** - friend lists and DMs (editing, reactions, GIFs, attachments) over WebSocket
- **Server directory & CA** - servers register here; the hub signs their X.509 certificates and issues short-lived connection tickets to clients
- **Public website** - the server-rendered pages (home, download, register, dashboard, invites) behind kommvoice.com

It deliberately does **not** carry channel messages, voice, files or permissions: those live on each community's own server, and **the hub never sees them.**

## How it works

```
┌────────────┐     ticket + TLS      ┌───────────────┐
│   Client   │ ────────────────────► │  Komm Server  │  ← channels, messages,
│ (Komm app) │ (CN-pinned to hub CA) │ (komm-server) │    voice, permissions
└─────┬──────┘                       └───────┬───────┘
      │ account, friends, DMs                │ X.509 mutual auth
      ▼                                      ▼
┌──────────────────────────────────────────────┐
│              komm-hub  ·  CA                 │
└──────────────────────────────────────────────┘
```

**Creating a server (once):**

1. In the [Komm client](https://github.com/B077AS/komm), the owner creates an installation: a name, ports, an optional **public address** (a name or IP players should use, e.g. a reverse-proxy domain) and a P-384 CSR generated locally. The hub validates it and returns a single-use **verification code**.
2. The owner installs komm-server (Windows installer, Linux script or the Docker image `ghcr.io/b077as/komm-server`) and gives it the code.
3. **The server activates before it starts serving.** It posts `{setupToken, csr}` to `POST /api/installations/validate`; the hub signs the certificate (BouncyCastle) and answers with the certificate, its public key, the ports and `publicAddress`, the address players use (the public address, else the IP the hub saw the server call from). The server writes all of it to disk first, so its very first start is already **HTTPS on the chosen port**: no plain-HTTP phase, no restart.
4. The server connects over WebSocket (`/ws/installations`) and goes **online**. **On every connect** the hub records the address it sees and sends an `ADDRESS_UPDATE` message with the address players use; the server hands it to its media server (LiveKit) as the address to advertise for voice, so voice works behind a home router and follows a changing IP.

A rejected code comes back as `422` with a machine-readable `code` (`SETUP_CODE_INVALID`, `INSTALLATION_ALREADY_VERIFIED`) plus a human message; the server shows it and waits for a corrected code instead of crash-looping. `/api/installations/validate` is rate limited (5 per minute per IP), and a busy or unreachable hub makes the server retry with a growing pause.

**Joining a community:** the client asks `POST /api/servers/{serverId}/ticket`; the hub checks membership, that the installation is online and its certificate not revoked, and issues a **60 s** single-use JWT. The client then connects **directly** to the community's server, validating its certificate against the hub CA (public `GET /api/auth/ca`) and the installation ID inside it - not a hostname. Messages never pass through the hub.

**WebSockets:** `/ws` for app clients and `/ws/installations` for servers, each with its own session manager. Message types are dispatched to auto-registered handler beans; permissions, voice presence and moderation are proxied hub → server via `CompletableFuture` registries.

## Security

- **X.509 mutual authentication** - every server proves its identity with a certificate signed by the hub's CA (P-384).
- **Automatic TLS** - that certificate is also the server's HTTPS/WSS identity, so servers get real transport encryption on a bare IP, with no domain and no third-party certificate. (A reverse proxy in front has to pass TLS through untouched.)
- **ES384 tokens, single-use tickets** - sessions use elliptic-curve signatures; join tickets expire in 60 seconds.
- **Rate limiting** - token buckets on `/api/**` (Bucket4j), per user when authenticated, per client IP otherwise. See [Rate limiting](#rate-limiting).
- **Data stays with you** - the hub never sees community content.

The hub's EC P-384 key pair (generated in `keys/` on first boot) is both its JWT signing key and its CA identity: **every certificate in your network chains to it.**

Updates are not the hub's business: the client and the server update from their own repos' GitHub releases (via [jpack](https://github.com/B077AS/jpack)), and the server image is published to GHCR on every release.

## Run your own hub

**Needs:** Java 21, PostgreSQL (any recent version; the schema is auto-managed) and an SMTP account (registration sends verification codes).

1. Create a database (e.g. `komm`).
2. Copy `src/main/resources/application-local.properties.example` to `application-local.properties` (gitignored) and fill in the database and SMTP credentials. Optional: a [Klipy](https://klipy.com) key for GIF search, beta mode, `komm.site.*` overrides.
3. Build and run:

```bash
mvn clean package -DskipTests
java -jar target/komm-hub-*.jar        # listens on port 8085
```

**Back up `keys/`**: it is your hub's identity and CA. Lose it and every server in your network has to re-verify.

Maven runs the icon/font pipeline for the website automatically (`frontend-maven-plugin`, during `generate-resources`). Only if you start the main class straight from an IDE on a fresh clone do you need to run `mvn generate-resources` once, or the site's icons are missing (the API is unaffected).

| Property | Required | Purpose |
|---|---|---|
| `spring.datasource.*` | yes | PostgreSQL connection |
| `spring.mail.*` | yes | SMTP for verification codes and beta requests |
| `klipy.api-key` | - | GIF search in the client |
| `komm.beta.enabled` | - | Require an invite key to register (off by default) |
| `komm.site.*` | - | Base URL, GitHub repo and download links (including the server's Docker link) for the website |
| `komm.ratelimit.enabled` / `.enforce` | - | Rate limiting on (default) / `false` = shadow mode (log only) |

**The bundled website** (`src/main/resources/templates/`) is the kommvoice.com showcase, not a white-label product: if you run your own hub, expect to fork and rebrand the marketing pages. The functional ones (register, login, verify-email, dashboard, invite) work out of the box.

### Closed beta

Optional and off by default (`komm.beta.enabled=true` to enable). A `SUPER_ADMIN` generates single-use keys (`KOMM-XXXX-XXXX-XXXX`) from `/dashboard` (promote your account with `role = 'SUPER_ADMIN'` in the `users` table); visitors without one can request access, emailed to `komm.beta.request-recipient`. Accounts that never verify their email are deleted after 7 days.

### Rate limiting

Per-endpoint buckets, so exhausting one never blocks another; breaches get `429` with `Retry-After`. Behind a reverse proxy the hub must see the real client IP: `server.forward-headers-strategy=framework` trusts `X-Forwarded-For`, which is only safe when port **8085 is reachable only through the proxy**.

## License

[MIT](LICENSE)
