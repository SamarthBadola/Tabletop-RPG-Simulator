# Tabletop RPG Simulator: UX and API Specification (v1.5)

Nov 21, 2026 · @Samarth

**Status:** **version 1.5.** All four parts are accepted by the owner: Part A (section 3) and Part B (section 4), with the screens as drawn in Figma, and Parts C and D on 2026-11-21 after a last pass. So are the decisions UXD-1 to UXD-11 and the questions UXQ-1 to UXQ-24 (7.5). UXD-11 and UXQ-24, with rules UX-059 to UX-061, were accepted on 2026-11-22 and their frames are drawn in Figma. The changes the other documents needed are applied (7.4).

This document specifies what the people at the table see and do (the screens and how they behave) and what the browser may ask of the server (the routes, their checks and their failures). It also holds the threat model of the web layer. It does not restate rules, content or engine contracts. It names them by id and says how a screen or a route reaches them.

| Question | Where the answer lives |
| --- | --- |
| What a rule says | Ruleset Specification (RS-\* ids) |
| What a pack may contain, which screens show attribution, secrecy classes | Content Schema Specification (CS-\* ids) |
| Structure, transactions, error handling, the reaction window, the settings registry | Architecture |
| What the product must do (FR, NFR, UC ids) | Requirements Specification |
| Why a choice was made | Decision Log |
| Grid, movement and the pathfinder interface | Grid and Pathfinder Specification |

**Ids.** Decisions are UXD-n. Screens are SCR-ACC-nnn, SCR-DM-nnn, SCR-PLY-nnn and SCR-ADM-nnn (section 2 explains the codes). UX rules are UX-nnn, route rows are API-\<module>-nnn, controls are SEC-nnn and threats are TM-nnn. New tests are numbered from T-120 (the Content Schema Specification ends at T-116). Ids are never renumbered. Cross-references name ids and sections, not versions; the version appears only in this header and in the AI Build Brief's list of companion versions.

**Release marks** follow the Requirements Specification: M, S or C for priority, A or B for the milestone (A is levels 1 to 5, B is later). A screen or route that exists only from v1.5 says so. Players submitting actions (FR-LIV-004) is set for version 2 by the owner and carries the mark V2.

## 1. Scope and decisions

**This is one document with four parts, not two documents.** The application renders HTML on the server with Thymeleaf and HTMX, so a screen and the routes behind it are one design: the fragment a route returns is what the person sees. Splitting the UX from the API would mean describing every screen twice, once as a picture and once as a list of routes, and keeping the two in step. The threat model, the error codes, the idempotency rule and the audience filtering also cut across both halves. The four parts are A (shared conventions), B (UX), C (API) and D (threat model). Ids carry the part, so if the document grows too large to read in one session it can be split along those lines without renumbering anything.

**What the stack means for the design.** There is one client, and it is our own templates, shipped with the server. Nothing outside the application calls it. That removes most of what API design guidance exists for (public versioning, client SDKs, field selection) and leaves what still matters: precise routes, one error model, safe retries, strict checks on who may do what, and nothing sent that the viewer may not see.

| ID | Decision | Why | Status |
| --- | --- | --- | --- |
| UXD-1 | One document in four parts: A shared conventions, B UX, C API, D threat model. | A screen and its routes are one design in this stack, and the threat model, errors and idempotency are shared. Part-prefixed ids allow a later split. | Accepted |
| UXD-2 | No public JSON API in version 1. The browser gets HTML pages and fragments, posts forms and holds one SSE stream. JSON appears only in error bodies for non-HTMX clients, the SSE signal, the health route and the v1.5 export file. | One client, shipped with the server, so a version prefix on routes protects nothing. Tests hold the contract (MockMvc and browser tests). File formats (pack, export, checkpoint) carry their own format version. | Accepted |
| UXD-3 | Routes live under the campaign: `/campaigns/{campaignId}/...`. The service resolves the principal's membership and role before anything else. A non-member gets NOT\_FOUND. Ids in URLs are UUIDs. | FR-ACC-005, FR-ACC-006, NFR-SEC-001. | Accepted |
| UXD-4 | Every state change is a POST and a GET never changes state. A normal GET returns a whole page and an HTMX request returns a fragment. A form posted without HTMX ends in a redirect to a GET. | Safe to retry, prefetch and bookmark. Login and join work without scripting. | Accepted |
| UXD-5 | The server mints the action id when it renders a form and puts it in a hidden field, with the expected version beside it. | A double click or a retry sends the same id and gets the stored result (Architecture 5.2). A new action needs a freshly rendered form, so it gets a new id. | Accepted |
| UXD-6 | SSE carries a signal, not data: the event names a scope and the new version, and the viewer fetches its own fragment. A change is published to an audience (the DM, all players or named players), and only to those who may know it happened. | The projection is made in one place, the request path. A hidden event does not reveal even its timing to a player (Architecture 8 and 10, Content Schema section 15). | Accepted |
| UXD-7 | Sessions are server-side and stored in PostgreSQL, so a restart does not sign anyone out mid-game. Idle limit 8 hours, absolute limit 14 days, both configurable. Sign-out deletes the row, and a password change ends the user's other sessions. | FR-ACC-007. Architecture 5.3 names stored sessions as the fix for relogin after a restart. The idle limit is longer than a long pause at the table. | Accepted |
| UXD-8 | Three surfaces: the DM console (desktop and tablet, dense, keyboard-friendly), the player view (phone first, large targets) and administration (small, rarely used). | The DM runs the game for hours, while a player acts for seconds on a phone (NFR-USA-003). | Accepted |
| UXD-9 | WCAG 2.2 level AA is the bar for every core screen, checked by an automated scan in CI and a manual keyboard pass. | Tightens NFR-USA-002, which asks for keyboard use and "aims for" AA contrast. NFR-USA-002 is reworded in the Requirements Specification (7.4). | Accepted |
| UXD-10 | Four error codes are added for the web layer: UNAUTHENTICATED (401), FORBIDDEN (403), CSRF\_INVALID (403) and RATE\_LIMITED (429, with Retry-After). | The Architecture table has no code for them. They join it in the Architecture (7.4). | Accepted |
| UXD-11 | The server sets the audience of a DM override. The DM chooses players or quiet. Quiet is accepted only when no player can see the changed value; otherwise the request is refused with VALIDATION\_FAILED and the field visibility INCORRECT. A player never receives the old value or the reason. | A correction must not change what a player sees without a trace, and a secret correction must not tip the table off. Both follow from FR-LIV-002 and SEC-012, so neither can be left to the form. | Accepted |

**Outside this document**. Player-submitted actions (FR-LIV-004, priority C) and the homebrew editor (post-MVP) are outside version 1. The owner has set player-submitted actions for version 2, not version 1.5. Rules, content and engine contracts belong to their own documents, and this one only points at them.

## 2. Users, roles, surfaces and devices

The application has three kinds of user (Requirements Specification 1.2). The administrator is an attribute of the account. The DM and player roles belong to a campaign membership, so one person can be DM in one campaign and a player in another (FR-ACC-005). A request is made by a principal: a user, plus the membership that applies to the campaign in the route.

| Role | Scope | What they do here | Main surface |
| --- | --- | --- | --- |
| Instance administrator | The installation | The first account on a fresh install. Manages the installation (what that covers in version 1 is open question UXQ-2). | Administration |
| Dungeon Master | One campaign | Creates and runs the campaign: content packs, characters, encounters, rests, settings, the clock, who sees what. Answers every reaction prompt in version 1. | DM console |
| Player | One campaign | Owns characters, reads their sheet, watches the table, and from v1.5 answers for the creatures they control. | Player view |

There are four surfaces. Each gets its own id code, and a screen that two roles both use has one id, with the differences between the role views written in its row.

| Code | Surface | Posture | Device it is designed for |
| --- | --- | --- | --- |
| ACC | Sign-in, registration with an invite, the join screen, account settings | Transient: a visit of seconds | Any |
| DM | DM console: campaigns, characters, encounters, the combat screen, rests, packs, settings | Sovereign: the DM stays in it for hours, so it is dense, shows state at all times and has keyboard shortcuts | Laptop or desktop at 1024 px and wider, usable on a tablet from 768 px |
| PLY | Player view: my characters, the live encounter, my reactions | Transient: a glance at turn order, hit points and the next prompt | A phone in portrait from 360 px, also tablets (NFR-USA-003) |
| ADM | Administration: users, instance status, attribution | Transient and rare | Laptop |

**What the postures decide.** The DM console keeps the turn order, the log and the acting creature's options on screen together, so the DM does not navigate during a turn. It asks for a decision only when a rule needs one (a reaction window, a setting that matters now) and never modally for anything that can be undone or reversed by the next action. The player view shows one thing at a time and nothing a player may not see. On the player view every control is at least 44 by 44 CSS pixels; elsewhere the minimum is 24 by 24 (WCAG 2.5.8) and 44 wherever space allows.

**Devices and the network.** The application runs on a LAN that may have no internet, so every font, script and image is served by the application itself (a system font stack by default). Pages reflow at 320 CSS pixels without two-dimensional scrolling and text can be enlarged to 200 percent (WCAG 1.4.4 and 1.4.10). The grid is the exception to reflow: it may scroll in its own region, and it has a keyboard route to every action it offers by pointer.

## 3. Part A: shared conventions

Parts B, C and D use these rules and do not restate them. Security controls that every request passes through are defined here and numbered SEC-nnn; Part D adds the rest and maps each threat to its controls.

### 3.1 Routes and naming

| Area | Pattern | Notes |
| --- | --- | --- |
| Public | `/login`, `/register`, `/static/**`, `/health` | The only routes open without a session (SEC-001). FR-ACC-004 names registration, login and static files; the health route is added here, and the requirement is reworded (7.4). An invite code is entered on the registration page, or on the join page of a signed-in user. |
| Account | `/account/**` | Own profile, password, active sessions, sign-out. Also /join (a code) and, from v1.5, /imports (a campaign file), because neither belongs to a campaign yet. |
| Campaign | `/campaigns`, `/campaigns/{campaignId}/**` | Every campaign route resolves membership first (SEC-002). Characters, encounters, scenes, rests, packs, settings, assets, maps, placement, journal and the SSE stream all sit below it. |
| Administration | `/admin/**` | Instance administrator only. |

- Collection names are plural nouns in kebab-case. Ids in paths are UUIDs, except content keys, which follow the key format of the Content Schema and are URL-encoded, and asset ids, which are the content hash of the stored file (5.19).
- A route names a thing or a command, never a type switch. A command is a noun for what it records (`/attacks`, `/moves`, `/rests`), not a verb with a `type` field that selects different behaviour. A transition of an existing thing takes its name (`/start`, `/end-turn`, `/pause`).
- Invite codes, passwords and tokens never appear in a path or query string. They travel in a POST body, so they stay out of logs, history and the Referer header.
- Query strings carry only filters, sort order, paging and the return address of the sign-in page (returnTo on GET /login, a relative path that is checked). Pages use GET and POST only, because a browser form can send nothing else, and a state change is always a POST.
- There is no version segment in the path in version 1 (UXD-2). If a JSON API is added later it goes under `/api/v1`.

### 3.2 Requests and responses

| Request | Detected by | Success | Failure |
| --- | --- | --- | --- |
| Page load | `GET`, no `HX-Request` header | 200, full page | Error page with the right status |
| Fragment fetch | `GET` with `HX-Request: true` | 200, the region only, with `Vary: HX-Request` | Error fragment, right status |
| Form post from HTMX | `POST` with `HX-Request: true` | 200, the changed region, plus out-of-band regions that moved with it (turn order, resource strip) | 400 `VALIDATION_FAILED` returns the form with field errors; any other failure returns the error fragment |
| Form post without scripting | `POST`, no `HX-Request` | 303 to a `GET` | The full page again with the same status and the errors inline. Never a redirect that drops the error. |
| Other client | `Accept` does not include `text/html` | As above, with `application/json` where it applies | `application/problem+json` |

**Error regions.** htmx does not swap 4xx and 5xx responses by default. One small script file served from the application sets the swap rule (`htmx:beforeSwap`) so that an error fragment lands in the nearest `#error-region`, and a validation failure replaces the form it came from. There is no inline script (SEC-007). A session that has ended returns 401 with `HX-Redirect` to the sign-in page, so a login screen is never swapped into a panel. A test pins that htmx honours `HX-Redirect` on a 401.

**Navigation and history.** `hx-push-url` is used only when the person moves to another screen, never for an action, so a reload always lands on a full page for a `GET`. htmx is configured with `historyCacheSize` 0, `allowEval` false, `allowScriptTags` false, `selfRequestsOnly` true and `includeIndicatorStyles` false (the indicator styles move into the stylesheet, so the CSP needs no inline style). The history cache is off because htmx would otherwise keep page snapshots in the browser's local storage, and a tablet at a game table is shared.

**Regions.** Each screen in Part B lists its regions with stable element ids. A fragment route returns exactly one region, and the same template renders that region inside the full page, so there is one template per region. A command that moves the person to another screen answers an htmx request with 200, an empty body and an HX-Redirect header (5.14).

**Caching.** Authenticated HTML carries `Cache-Control: no-store`. Fingerprinted static files are `public, max-age=31536000, immutable`. Images carry content-hash names and are `private, max-age=86400` (SEC-013, Part D).

**Formats.** Timestamps are ISO 8601 in UTC in the markup (`<time datetime>`) and shown in the viewer's local time. Game time is shown from the campaign clock and is never mixed with real time (FR-CLK-001). Numbers in forms use the browser's locale on input and are parsed with one function on the server.

### 3.3 Errors

One `@ControllerAdvice` maps every exception to one error model (Architecture section 9). Rules:

- A caller fault is 4xx, carries a stable code and is not logged at ERROR. A system fault is 5xx, carries a trace id and is logged at ERROR.
- The message shown comes from a catalogue keyed by code, never from the exception text, so no internals leak (SEC-009). Each entry has a message, a line that says what to do next, and the fields it may point at.
- `application/problem+json` carries `type` (`urn:trpg:error:<CODE>`), `title`, `status`, `code`, `detail`, `traceId` (5xx, and IDEMPOTENCY\_KEY\_CONFLICT), `errors[]` (field and code, for 400) and `details` (structured, for example the version the caller should refetch).
- The stable codes and their statuses are in the Architecture, section 9. This document adds four (UXD-10), section 5.8 adds a fifth, PACK\_REVIEW\_STALE (409), and section 5.15 adds ENCOUNTER\_STARTED, MAP\_IN\_USE, PLACEMENT\_INVALID and LAST\_ADMINISTRATOR. The four of UXD-10 are:

| Code | Status | Meaning | Shown as |
| --- | --- | --- | --- |
| UNAUTHENTICATED | 401 | No session, or it ended | `HX-Redirect` to sign-in with a return address, or the sign-in page |
| FORBIDDEN | 403 | A signed-in member lacks the role | Inline message: "Only the DM can do this." |
| CSRF\_INVALID | 403 | Missing or wrong token | Message with a Reload action; the page is fetched again to get a new token |
| RATE\_LIMITED | 429 | Too many attempts | Message with the wait time from `Retry-After` |

**404 or 403.** If the principal may know the thing exists, the answer is FORBIDDEN. If not, it is NOT\_FOUND. A non-member asking for a campaign gets NOT\_FOUND, and so does a player asking for a DM-only record. A player acting on another player's character that is visible in the party list gets FORBIDDEN.

**Status choice.** 400 means the request is malformed or a field is missing or out of range. 422 means the request is well formed and a rule refuses it (NOT\_YOUR\_TURN, NO\_ACTIONS\_LEFT, TARGET\_OUT\_OF\_RANGE). 409 means the state moved (versions, an open window, an active session). 503 means the database cannot be reached. A pathfinder failure is never a user-visible error (NFR-REL-001): the engine falls back to its own path search, and the only effect is a longer wait.

**Boundary.** Input is validated once, at the controller, with Bean Validation on form objects. Services receive parsed values and do not re-check their shape. Anything unmapped becomes INTERNAL\_ERROR (500). Framework refusals use the same model: an unmatched route is NOT\_FOUND, a wrong method or media type is VALIDATION\_FAILED (400), and a body above the server limit is refused with 413, code VALIDATION\_FAILED and a field error that names the limit.

### 3.4 Action ids, versions and replays

The client does not invent ids. The server writes them into the page.

- When the server renders a form or a control that issues a command, it mints a UUID and puts it in a hidden field `actionId`, with `expectedVersion` beside it (the encounter version for combat commands, the campaign version for campaign commands).
- After a success, the response carries a fresh form with a new `actionId` and the new version.
- A rejected command stores nothing, so the form keeps its id and the person edits and sends again. This depends on the Architecture not storing rejections; it is open question UXQ-3.
- A double click, a retry after a lost response and a duplicated tab all send the same actionId, so they get the stored result (the Architecture, 5.2). The stored result goes to the principal who sent the first request and to nobody else. Another request with the same id and a different body gets `IDEMPOTENCY_KEY_CONFLICT`. The screen treats that as a bug and shows the generic message with the trace id.
- On `ENCOUNTER_VERSION_CONFLICT` or `CAMPAIGN_VERSION_CONFLICT` the response is the refreshed region with a banner: "This changed while you were looking at it." The person's input is kept in the form where the fields still apply, and nothing is retried automatically.
- A submit control is disabled after the first click until the response arrives (`hx-disabled-elt`). This is a courtesy; the id is the guarantee.

### 3.5 Lists and paging

| List | Paging | Why |
| --- | --- | --- |
| Combat log and campaign event log | Cursor on `seq`: `?before=<seq>&limit=50`, maximum 200. A player gets an opaque cursor and no seq (SEC-012) | Append-only, and new rows arrive while someone reads, so offsets would skip or repeat rows |
| Monster, spell and item browsers, user list | Offset: ?page=&size=25 (maximum 100), with search and filters | Stable data, and people jump to a page |
| Characters, encounters, packs, scenes, maps, invites, checkpoints, sessions | Not paged below 100 rows; above that, offset | Small by nature |

A page response says how many rows there are in total for offset lists and whether there are older rows for cursor lists. The older-rows control is a real button that works without scripting.

### 3.6 Live updates

The Architecture decides the model: the server pushes a signal and the client fetches its own fragment (Architecture 5.1, 8). This section fixes the HTTP side. It applies from v1.5.

- `GET /campaigns/{campaignId}/events` returns `text/event-stream`. The role is taken from the session. There is no audience parameter.
- Two event names. `state` is sent once on connect. For the DM it holds the current encounter and campaign versions, so a reconnecting client finds out what it missed (FR-LIV-003). For a player it holds no version, and the player's page refetches its regions on connect (UXQ-19). `changed` carries the scope (`encounter`, `campaign`, `scene`, `reaction`), the id of that scope and, for the DM only, the new version (UXQ-19). Neither carries game data.
- The notifier publishes to an audience: the DM, all players, or named players. A change that only the DM may know about (a secret roll) is published to the DM and to nobody else, so nobody learns from timing that something happened (SEC-012).
- On receipt the client fetches the affected region with an ordinary `GET`. The fragment route applies the audience filter again, so a signal that arrives early or to the wrong person shows nothing they may not see.
- A heartbeat comment every 15 seconds keeps proxies from closing the stream. The browser reconnects by itself. Membership is checked again on every connect (SEC-002), and a removed member is disconnected at the next heartbeat. A stream also closes when its session ends, by timeout, sign-out or revoke, and a heartbeat does not count as activity for the idle timeout.
- At most 5 open streams per user. The hub is in memory and single instance (Architecture), so a restart drops all streams and clients reconnect with `state`.
- In version 1 there is no stream and no player view of a running encounter (FR-LIV-001 is milestone B). A player's character sheet is an ordinary page that shows the state as of the last load, with a Refresh control. Whether it should refresh itself in version 1 is open question UXQ-4.

### 3.7 Sessions, passwords and CSRF

| ID | Control |
| --- | --- |
| SEC-001 | Deny by default. The filter chain ends with "any request must be authenticated"; the routes in 3.1 marked public are an explicit allowlist. A test enumerates every mapped route and fails if one is open that is not on the list. |
| SEC-002 | Membership first. Every `/campaigns/{campaignId}/**` request resolves `campaign_member` for the principal before the handler runs. No row gives NOT\_FOUND. The row gives the role for the rest of the request. |
| SEC-003 | Data-layer scoping. Repository methods for campaign data take the campaign id and the principal, and filter on membership in the query. Foreign ids give NOT\_FOUND. Controllers bind form objects, never entities. |
| SEC-004 | Server-side sessions in PostgreSQL through Spring Session JDBC (UXD-7). Idle timeout 8 hours, absolute timeout 14 days, both configurable. Spring Session has no absolute limit of its own, so a filter compares the creation time. A new session id is issued at sign-in. Sign-out deletes the row. A password change deletes the person's other sessions. Deactivating an account, or an administrator resetting a password, deletes all of the person's sessions, and the principal is reloaded on every request so a deactivated person is refused at once. The account page lists active sessions with a Revoke control. |
| SEC-005 | Cookie: `HttpOnly`, `SameSite=Lax`, `Secure` when `APP_COOKIE_SECURE` is true (default false, because the LAN runs over HTTP). Name without a prefix while `Secure` is off. |
| SEC-006 | CSRF. A token is bound to the session. It sits in a meta tag and in a hidden field. A small script adds it to every htmx request as the `X-CSRF-TOKEN` header. Every POST is checked, in constant time. Sign-in and registration have no session yet, so their forms carry a signed double-submit token in a cookie, and an anonymous visit creates no session row. As a second line, an Origin header that does not match the host the request was sent to is refused. `GET` never changes state, so it needs no token. The SSE stream is read-only. |
| SEC-007 | Headers. CSP ` default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self';  `frame-ancestors 'none'; object-src 'none'; base-uri 'none'`; form-action 'self'`, with no inline script and no inline style. Also `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, a `Permissions-Policy` that turns off camera, microphone and geolocation, and HSTS only when the request came over HTTPS. No CORS headers are sent, because no other origin calls this application. |
| SEC-008 | Rate limits (3.8). |
| SEC-009 | Error responses carry no stack, no SQL, no file path and no account detail. Sign-in failure says the same thing for an unknown name and a wrong password. |
| SEC-010 | Logging. Each request logs method, route template (not the raw path), status, duration, principal id, campaign id and trace id. It never logs bodies, passwords, invite codes or session ids. Failed sign-in, FORBIDDEN, CSRF\_INVALID and RATE\_LIMITED are logged at WARN and counted. |
| SEC-011 | Passwords: Argon2id (BCrypt as the fallback the Architecture allows), a minimum of 10 characters, no composition rules, a short bundled list of very common passwords refused, paste allowed, no maximum below 128. Sign-in has no puzzle or recall test (WCAG 3.3.8). |

**First account.** On an empty installation the first registration creates the instance administrator and needs no invite. After that, registration needs an invite code (FR-ACC-003). Passwords cannot be reset by email, because the application keeps no email address; open question UXQ-6 covers recovery.

### 3.8 Rate limits

Counters are in memory, which matches the single instance. They reset on restart. They are bounded and the oldest entries are dropped, so a flood of random usernames cannot use up memory. The source address is the connection's remote address; forwarded headers are trusted only when APP\_TRUSTED\_PROXIES names the proxy. That is acceptable on a LAN, and Part D lists it as residual risk. The response is 429 with `Retry-After`. Limits below are the starting values, to be tuned in the first playtest. There are no `X-RateLimit-*` headers on every response, because there is no third-party client to read them.

| Route | Key | Limit | Notes |
| --- | --- | --- | --- |
| Sign-in | Username, and separately source address | 5 failures per 15 minutes per username, 20 per 15 minutes per address | The account is never locked, so nobody can lock out another person. Each failure past the limit adds delay. |
| Registration and invite redemption | Source address | 10 attempts per 15 minutes | A wrong code counts, a right one does not. |
| Password change | User | 5 per hour |  |
| Image upload | User | 20 per hour | Map and monster images share the counter. Part D has the file rules. |
| Pack upload and import (v1.5) | User | 5 per hour | A pack upload and an import share the counter. Plus the size and entry limits in the Content Schema and 5.21. |
| Obstacle detection (v1.5) | User, and the instance | 30 per hour for each user, and at most 3 runs at once on the instance | Detection runs inside the request (5.19), so the limit also protects the table. The request that would be the third at once answers RATE\_LIMITED with Retry-After. |
| SSE connect | User | 5 open streams, 30 connects per minute |  |
| Commands in play | None in v1 |  | A DM taps fast during combat, and idempotency handles repeats. Revisit if a client misbehaves. |

### 3.9 Who may do what

The check order is fixed: signed in (SEC-001), member of the campaign (SEC-002), role for the route, then the rule of the game. Cells show the status a request gets. A tick means allowed. "Own" means the principal's own characters and creatures.

| Route group | Not signed in | Not a member | Player | DM | Instance administrator (not a member) |
| --- | --- | --- | --- | --- | --- |
| Public pages, static files, health | ✓ | ✓ | ✓ | ✓ | ✓ |
| Account: profile, password, sessions | 401 | ✓ own | ✓ own | ✓ own | ✓ own |
| Campaign list, create a campaign, redeem an invite, import a campaign (v1.5) | 401 | ✓ | ✓ | ✓ | ✓ |
| Campaign pages and fragments (filtered by audience) | 401 | 404 | ✓ filtered | ✓ | 404 |
| Own character: read, edit, level up, rest choices | 401 | 404 | ✓ own, 403 others' | ✓ | 404 |
| Encounter state: read | 401 | 404 | ✓ filtered | ✓ | 404 |
| Combat commands, clock, scenes, rests | 401 | 404 | 403 (v1 and v1.5); own creature on its turn from v2, else 422 NOT\_YOUR\_TURN | ✓ | 404 |
| Reaction answers | 401 | 404 | 403 (v1); own creatures from v1.5, else REACTION\_NOT\_PERMITTED | ✓ any creature | 404 |
| Encounter set-up, add or remove combatants, DM edits and overrides | 401 | 404 | 403 | ✓ | 404 |
| Content packs: list, load, enable, upgrade, disable, delete | 401 | 404 | 403 | ✓ | 404 |
| Settings: read, decide, change | 401 | 404 | 403 (read-only list from v1.5) | ✓ | 404 |
| Invite codes: create, revoke | 401 | 404 | 403 | ✓ | 404 |
| Assets: upload | 401 | 404 | 403 | ✓ | 404 |
| Assets: view | 401 | 404 | ✓ if the audience allows | ✓ | 404 |
| Maps: upload, set the grid, detect, edit, delete (v1.5) | 401 | 404 | 403 | ✓ | 404 |
| Placement before an encounter starts (v1.5) | 401 | 404 | ✓ own character, inside the start area, if the DM did not place it; 403 for the rest | ✓ | 404 |
| Held characters of an import: view, discard (v1.5) | 401 | 404 | 403 | ✓ | 404 |
| Export, checkpoints, restore (v1.5) | 401 | 404 | 403 | ✓ | 404 |
| Live stream | 401 | 404 | ✓ | ✓ | 404 |
| Administration | 401 | 403 | 403 | 403 | ✓ |

Whether the administrator should be able to see or recover campaigns they are not a member of is open question UXQ-2. This table assumes not, so an administrator cannot read a table's secrets by default. In version 1 a player has no view of a running encounter (FR-LIV-001 and FR-LIV-002 are milestone B), so the encounter-state and live-stream rows apply from v1.5; before then the encounter-state route answers 403 and the stream does not exist (404).

**Enforcement points.** The route group is checked in the controller (coarse). The service checks the rule of the game and the ownership of the creature (specific). The repository filters by membership (the floor, SEC-003). A missing rule denies.

## 4. Part B: UX

This part says what each screen is for, what is on it, and how it behaves in every state. It does not set colours, type or spacing values; section 4.11 explains why and what the build uses meanwhile.

### 4.1 Principles

Each rule is testable by looking at a screen or running a flow.

| ID | Rule |
| --- | --- |
| UX-001 | **The DM does not navigate during a turn.** At 1024 px and wider the combat screen keeps the turn order, the acting creature's options, the map and the log in view together. On narrower screens the turn order and the acting panel stay in view and the rest is one tap away (4.9). Nothing needed to run a turn is behind a menu. |
| UX-002 | **Ask only before what cannot be corrected.** Deleting a campaign or a pack, restoring a checkpoint and cancelling a reaction window are asked about. Everything else happens at once, and a mistake is fixed with a DM override that the log records (FR-ENC-010). There is no blanket "Are you sure?". |
| UX-003 | **A decision is asked for only when a rule needs one, and the screen says why.** A reaction window and a setting decision name the rule and the creature involved. |
| UX-004 | **Dice show their working.** Every roll shows the dice, each modifier with its source, the total and the outcome. The DM view can expand any roll to the rule that applied. |
| UX-005 | **Defaults come from the campaign and from the last choice.** The target, the rest type and the save DC start from what was used last or what the settings say. |
| UX-006 | **Frequent actions have a visible control and a keyboard shortcut.** A shortcut never exists without the control. Shortcuts can be turned off (WCAG 2.1.4). The ? key opens the list of shortcuts, which also sits on the help page. |
| UX-007 | **Secrets are marked in words.** A DM-only item shows a "DM only" label with text, not colour alone. A player screen has no placeholder that reveals a hidden thing exists unless the rules show it. |
| UX-008 | **Errors say what happened in game terms and what to do next.** The code is a small reference line, not the message (4.7). |
| UX-009 | **Every region has designed states** for loading, empty, error, success and stale (4.6). |
| UX-010 | **No hidden data in the markup.** A player page never contains DM data that style rules hide. The server leaves it out (SEC-012). |
| UX-011 | **No operation needs dragging.** Placing and moving a token is select, then choose a cell, by pointer or keyboard (WCAG 2.5.7). |

**How the rules are checked.** The application is self-hosted and sends nothing out, so it has no analytics. The rules are checked in playtests, with these targets (starting values, to be tuned after the first session): a new DM goes from an empty installation to a first attack in 15 minutes; a player joins from a phone in 2 minutes; a DM resolves an opportunity attack without leaving the combat screen. A rule that fails twice in playtest is rewritten here.

### 4.2 Navigation

Every page has a header with the campaign switcher, the session state (active, paused, ended), the game time, a search field for the rules reference (SCR-DM-016 for the DM, SCR-PLY-009 for a player from B) and the account menu. Every authenticated page has a footer link to the rules attribution (4.10) and to help.

| Surface | Primary navigation | Wide screen | Narrow screen |
| --- | --- | --- | --- |
| DM console | Table, Party, Encounters, Scenes, Content, House rules, Players, Log | Left rail, always visible | A menu button opens the rail; the Table entry stays in the header |
| Player view | Table, Characters, Journal, Rules. Only Characters exists in version 1; the other three arrive in version 1.5 (UXQ-18) | Top tabs | Bottom tab bar with 44-pixel targets |
| Administration | Users, Status | Top tabs | Top tabs |

The page title names the screen and the campaign. The current item is marked in text and with `aria-current`, not by colour only. A skip link goes to the main region, and the order of landmarks is header, navigation, main, footer.

&#91;embedded content: sitemap · 4 areas, every screen id\]

The DM rail and the player tabs reach every area directly. The Table entry opens the combat screen (SCR-DM-010) while an encounter is active and the campaign home (SCR-DM-003) otherwise. Account, joining with a code and the attribution page are on every page. A player reaches My campaigns (SCR-PLY-001) from the app bar, not from a tab. The DM Log page groups three areas, shown as chips under the page title: Event log, Journal and quests, and Checkpoints and export (UXQ-18).

### 4.3 Screen inventory

A screen used by two roles has one id. The role differences are in the row. The release column is the first release in which the screen exists (A is version 1, B is version 1.5, V2 is version 2).

| ID | Screen | Used by | Realises | Release |
| --- | --- | --- | --- | --- |
| SCR-ACC-001 | Sign in | Anyone | FR-ACC-004, FR-ACC-007 | A |
| SCR-ACC-002 | Register (first account, or with an invite code) | Anyone | FR-ACC-001, FR-ACC-003 | A |
| SCR-ACC-003 | Account: password, active sessions, shortcut setting, sign out | Signed in | FR-ACC-002, FR-ACC-007 | A |
| SCR-ACC-004 | Join a campaign with a code | Signed in | FR-CAM-003 (UXQ-5) | A |
| SCR-ACC-005 | Help, keyboard shortcuts and rules attribution | Signed in | CS-SRD-010 | A |
| SCR-DM-001 | Campaigns: list and create | DM | UC-01, FR-CAM-001, FR-CAM-004 | A |
| SCR-DM-002 | Campaign setup wizard | DM | UC-01, FR-SET-003 | A |
| SCR-DM-003 | Campaign home: recap, session controls, clock | DM | FR-SES-001 to FR-SES-003, FR-CLK-001, FR-CLK-003 | A |
| SCR-DM-004 | Players and invites, with the join address and code, and the held characters of an import (B) | DM | FR-NET-001, FR-NET-002, FR-ACC-003, FR-CAM-003, FR-CHR-017 (held characters, UXQ-23) | A |
| SCR-DM-005 | Content packs: list, enable, disable, warnings; upload from B | DM | UC-01, UC-08, FR-CAM-002, FR-MON-015, CS-HBR-003, CS-HBR-008 | A |
| SCR-DM-006 | House rules | DM | FR-SET-001 to FR-SET-006 | A |
| SCR-DM-007 | Party: all characters of the campaign | DM | UC-02 | A |
| SCR-DM-008 | Encounters: list | DM | UC-04 | A |
| SCR-DM-009 | Encounter builder, with how each monster will be placed | DM | UC-04, FR-ENC-001, FR-ENC-002, FR-MON-015 | A |
| SCR-DM-010 | Combat screen | DM | UC-05, FR-ENC-003 to FR-ENC-011, FR-ACT-001 to FR-ACT-008 | A |
| SCR-DM-011 | Reaction window | DM | UC-06, FR-ACT-005, FR-RXN-001 to FR-RXN-007, FR-RXN-009, FR-RXN-010 | A |
| SCR-DM-012 | Add or remove a combatant, with join options | DM | FR-ENC-009, FR-ENC-012 | A |
| SCR-DM-013 | Rest | DM | UC-07, FR-RST-001 to FR-RST-007 | A |
| SCR-DM-014 | Scenes and light | DM | FR-SCN-001, FR-SCN-002 | A |
| SCR-DM-015 | Campaign event log | DM | FR-RST-007, FR-SET-004, FR-AUD-001 | A |
| SCR-DM-016 | Rules reference: monsters, spells, items and conditions, with stat blocks and images | DM | FR-AST-001, FR-AST-003, FR-MON, FR-SPL | A |
| SCR-DM-017 | Setting decision | DM | FR-SET-007 | B |
| SCR-DM-018 | Journal and quests | DM | FR-JRN-001, FR-JRN-002 | B |
| SCR-DM-019 | Checkpoints, export and import | DM | FR-CHK-001, FR-CHK-002, FR-EXP-001 | B |
| SCR-DM-020 | Map import: upload an image, set the grid, review obstacles | DM | FR-GRD-010 to FR-GRD-012, UXQ-10, UXQ-11 | B |
| SCR-DM-021 | Place creatures: by hand, at random, or at random inside an area, with monster images | DM | FR-GRD-013 to FR-GRD-015, FR-AST-004, UXQ-13 | B |
| SCR-PLY-001 | My campaigns | Player | FR-ACC-005 | A |
| SCR-PLY-002 | My characters | Player | UC-02 | A |
| SCR-PLY-003 | Character sheet. The DM sees every character and can edit; a player sees and edits their own. | Player, DM | UC-02, FR-CHR-010 to FR-CHR-016 | A |
| SCR-PLY-004 | Create a character: six steps (species, class, abilities, background and skills, equipment, review) | Player, DM | UC-02, FR-CHR-016 | A |
| SCR-PLY-005 | Level up: four steps (class and features, hit points, ability scores, review with the lost-points warning) | Player, DM | UC-03, FR-CHR-012 to FR-CHR-015 | A |
| SCR-PLY-006 | Table: the live encounter, filtered, with a full-screen map mode (UXQ-12) | Player | UC-09, FR-LIV-001 to FR-LIV-003 | B |
| SCR-PLY-007 | My reaction | Player | UC-06, FR-RXN-008 | B |
| SCR-PLY-008 | My rest | Player | FR-RST-008 | B |
| SCR-PLY-009 | Rules: house rules and the reference, read only | Player | FR-SET-008 | B |
| SCR-PLY-010 | Journal, read only | Player | FR-JRN-001 | B |
| SCR-PLY-011 | Submit an action for approval | Player | FR-LIV-004 (Could) | V2 |
| SCR-ADM-001 | Users: list, deactivate, reset a password | Administrator | FR-ACC-003, open questions UXQ-2 and UXQ-6 | A |
| SCR-ADM-002 | This installation: simulator version, space used, health, and technical details for bug reports | Administrator | NFR (operations), open question UXQ-2 | A |

### 4.4 Key flows

**First run.** An empty installation sends every visit to SCR-ACC-002. The first account becomes the administrator (FR-ACC-003) and lands on SCR-DM-001 with one action: create a campaign. The setup wizard (SCR-DM-002) asks the five structural choices from FR-SET-003 with a preset for each, and a "Use the defaults and decide later" link on the first step that skips the rest. It ends on SCR-DM-004 with the join address and an invite code ready.

**Players join.** The DM reads out or shows the address and code on SCR-DM-004. The address comes from the DM's browser URL, and the screen warns when it is `localhost`, because other devices cannot reach that (FR-NET-002). The screen also shows the address as a QR code, so nobody types it on a phone. The QR code holds the address only, never the code (3.1). A player opens the address, registers with the code on SCR-ACC-002, and lands on SCR-PLY-001 inside the campaign. A signed-in user enters a code on SCR-ACC-004 instead. The code is single use or limited, expires, and is rate limited (SEC-008, SEC-016).

**Running a turn (SCR-DM-010).**

1. The acting creature is marked in the turn order and in its own panel. The panel shows the five things a turn allows (action, bonus action, reaction, movement left, object interaction) as chips with their state in text (FR-ENC-003).
2. The DM picks an action from the panel. Options that are not available are shown with the reason beside them ("No action left"), not only dimmed. The server still checks (NO\_ACTIONS\_LEFT).
3. The DM picks the target by selecting it on the map or in the target list. Both do the same thing.
4. For movement the DM selects a destination cell. The map shows the reachable cells and the path from the pathfinder. Where the path leaves an enemy's reach, the preview lists who may get an opportunity attack before the move is confirmed (FR-ACT-006). A second selection or Enter confirms.
5. The result lands in the log and in a result card in the acting panel: dice, modifiers, total, outcome and damage applied (UX-004). If a reaction is possible, SCR-DM-011 opens before the rest of the action resolves.
6. End turn is a button and a shortcut. The next creature becomes the acting creature and the clock advances by the round setting (FR-CLK-003).

A correction to a log entry (a wrong damage figure, a missed condition) is a DM override on that entry (FR-ENC-010, milestone A, Should). It writes a new event that cites the old one. The log is never edited. What players see of a correction is in UX-059 to UX-061 (UXQ-24).

**Undo last action (UXQ-8).** The log offers Undo on the most recent action only. It is a DM override that reverses the effects and cites the entry (FR-ENC-010), so the log stays append-only. It is the safety net that lets UX-002 do without confirmation dialogs.

**Ending an encounter (UXQ-9).** Ending combat shows a summary on the combat screen: rounds fought, creatures that fell and resources spent. It names anyone who can level up and links to SCR-PLY-005 where the Requirements allow it. The DM leaves with one control, back to the table.

**Map import (UXQ-10 and UXQ-11, accepted, SCR-DM-020).** The DM adds a map in three steps: upload an image, set the grid, review obstacles. The grid is the model. Movement, distance and pathfinding read the grid only, and the image is a background.

| ID | Behaviour |
| --- | --- |
| UX-019 | The upload step accepts PNG, JPEG and WebP up to the size limit, by drop or by a file picker, and asks for a map name. The server decides the file type from the content, not the name. A refused file says why in text. |
| UX-020 | The grid step takes square size, offset X and offset Y in pixels, draws the grid over the image as the numbers change, and shows the result as columns and rows ("20 × 13 squares"). |
| UX-021 | Detection only suggests. Suggested squares look different from confirmed ones (a lighter fill and a dashed outline against a solid fill) and a legend names both. No suggestion blocks movement until the DM saves. |
| UX-022 | The DM edits square by square with three tools: obstacle, difficult terrain and erase. Accept all suggestions and Clear suggestions are single controls, and the counts of suggested and confirmed squares are shown. Every edit also works from the keyboard: arrow keys move a square cursor and Space applies the active tool (WCAG 2.1.1, 2.5.7). |
| UX-023 | If detection fails or finds nothing, the step says so in text and the DM places obstacles by hand. A map can be saved with no obstacles. A detection failure never blocks saving. |

**Full-screen map (UXQ-12, accepted).** The map has a display mode where it fills the screen and the side panel is hidden. It applies to the map on SCR-PLY-006 and to the map on the DM combat screen.

| ID | Behaviour |
| --- | --- |
| UX-024 | A Full screen button sits on the map. In full screen the map fills the display, Exit full screen stays visible, and Esc exits. |
| UX-025 | In full screen a thin handle on the right edge hints that stats are there. Moving the pointer to the edge, activating the handle with the keyboard, or tapping it opens a translucent overlay, at most 340 px wide, with the acting creature's hit points, armor class, speed and movement left, and the turn order. The overlay closes when the pointer leaves it, on Esc, or on a second tap. It can be dismissed without moving the pointer and stays open while the pointer is over it (WCAG 1.4.13). |
| UX-026 | The controls that finish a map action stay on screen in full screen. Planning a move shows a bar with the distance, Cancel and Confirm move, so a move can be confirmed without leaving full screen. |

**Placing creatures (UXQ-13, accepted, SCR-DM-021).** The DM decides where each monster stands before the encounter starts. Group monsters, the creatures a DM adds to raise the difficulty, can be placed by the server. A boss or any creature the DM wants in a particular spot is placed by hand.

| ID | Behaviour |
| --- | --- |
| UX-027 | The builder shows under each monster in the roster how it will be placed, and Place creatures on the map opens SCR-DM-021. Each group of the same monster has one choice: Anywhere eligible, Inside an area, or I place it. |
| UX-028 | Eligible squares are the squares that are not obstacles (detected or confirmed, UXQ-11) and not occupied. The screen shows how many there are, and every random choice leaves the others out. |
| UX-029 | Inside an area: the DM drags a rectangle on the map, or types the first and last column and row into number fields (WCAG 2.5.7). The server picks random eligible squares inside it. If the area has fewer eligible squares than creatures, the screen says so beside the area and Done stays unavailable until the area grows or the choice changes. |
| UX-030 | I place it: the DM clicks a square to put the creature there, clicks the token to move it, and can remove it from the map. With the keyboard, arrow keys move a square cursor and Enter places. This is the way to put a boss where the DM wants it when players enter or explore. |
| UX-031 | Random positions are chosen by the server, so every screen shows the same layout. Faded tokens preview them, and Shuffle random positions shows another layout. The preview binds nothing until the DM starts the encounter. |
| UX-032 | A monster can carry an image the DM uploads and maps to it. One image serves every copy of that monster, and the token shows the monster's initials until one is uploaded. Image files follow the same rules as map images (UXQ-10). |
| UX-033 | The DM can draw a start area for the player characters, a rectangle on eligible squares like a placement area (UX-029). Each player then places their own token inside it from their own table view. A token cannot be placed outside the area, on an obstacle, or on an occupied square. The DM sees who has placed a token and who has not. |
| UX-034 | The DM can also place any player character by hand, with or without a start area, and can move a placed token until the encounter starts. A character the DM places is not placed by the player. Both ways can be used in one encounter, for example one character placed by hand and the others placing their own tokens. |
| UX-035 | Starting an encounter while a player character has no position shows a warning that names those characters. The DM can start anyway. Each unplaced character then joins on the nearest free eligible square inside the start area, or the nearest eligible square when no area is drawn. |

**Resting (SCR-DM-013).** The DM picks short or long, selects who takes part, and sees for each person what will happen before applying: hit dice to spend (a count, or until a target), Arcane Recovery and Song of Rest where they apply (FR-RST-006), and warnings (a second long rest inside the interval, a creature at 0 hit points that is not stable). The server returns the whole result at once. The rest is one command with one action id, so a second submit changes nothing (FR-RST-003). Players spend their own hit dice from B (SCR-PLY-008).

**Content pack changes (SCR-DM-005).** Enabling, upgrading or disabling a pack opens a review in the same screen. The review lists what the change does, in groups: keys a pack overrides, keys it shadows, same-name warnings, and what disabling removes from characters and monsters already in use (CS-HBR-008). The DM confirms each group with its own checkbox before the button works. The review carries a hash of what the server computed. If the warnings changed before the confirmation arrives, the server answers PACK\_REVIEW\_STALE (409) and shows the review again.

**Screens by role.** The rules below describe the screens as drawn in the Figma file (4.12). Each screen keeps to one task and reuses the patterns above. They hold in both themes.

**Shortcuts (UXQ-17, accepted).**

| ID | Behaviour |
| --- | --- |
| UX-036 | Version 1 has six single-key shortcuts: ? opens the list, E ends the turn, M puts the map in full screen, R rolls for the acting creature, U undoes the last action (UXQ-8) and / moves focus to the rules search. A shortcut acts only while the console has focus and no text field does. Each one has a visible control that shows its key, and the account page turns them off (UX-006, WCAG 2.1.4). |

**Account screens (SCR-ACC-001 to 005).**

| ID | Behaviour |
| --- | --- |
| UX-037 | Sign in is one form with the sign-in name and password. A failed attempt says the pair is not right and never says which half. After success the person lands on the page they asked for. A blocked attempt shows RATE\_LIMITED with the seconds left. |
| UX-038 | Register on a fresh installation says the first account will manage the installation (FR-ACC-003). After that it asks for an invite code first and names the campaign once the code is accepted. A code that is not accepted says it may have expired or been used up, with the next step: ask the DM for a new one. The code is not asked again after registration (WCAG 3.3.7). |
| UX-039 | Account is one page in four groups: password (current and new, with a Show control), active sessions (device and last activity, this one marked in text, End session on the others), preferences (theme: follow the system, dark or light; single-key shortcuts on or off) and Sign out. |
| UX-040 | Join with a code takes the code and opens the campaign on SCR-PLY-001. A code that is not accepted gets one message, whether it is unknown, expired or used up (INVITE\_INVALID), so a guess learns nothing. The message never names a campaign. Redemption is rate limited (SEC-016). |
| UX-041 | Help has two parts: the keyboard shortcuts (UX-036) and the rules attribution (4.10). It is the same page for every role. |

**Player screens (SCR-PLY-001 to 011).**

| ID | Behaviour |
| --- | --- |
| UX-042 | My campaigns lists every campaign the person belongs to, with their role in text (DM or player) and the session state. The empty state offers Join with a code. |
| UX-043 | My characters lists each character with level, class and hit points as text ("21 of 28 hit points"). Create a character starts the six-step wizard and Level up starts the four-step one, where the Requirements allow it (UXQ-9). A stepper names the current step in text, and going back keeps every answer (WCAG 3.3.7). On the abilities step, a rolled method shows a Roll control. The server rolls, and the six values appear as one set that the player assigns to the abilities in any order. Leaving the wizard and coming back shows the same set, so leaving gives no new roll (UXQ-21, RS-ABL-007). |
| UX-044 | Table (SCR-PLY-006, B) shows the live encounter filtered to what the character can know (FR-LIV-002). The player's own turn is stated in words ("It is your turn") as well as marked. The map has the full-screen mode of UX-024 to UX-026. On a phone the map and the turn order are tabs (4.9). |
| UX-045 | My rest (SCR-PLY-008, B) shows the player's hit dice and lets them choose how many to spend. The result lists each die and the hit points regained. When no rest is under way the screen says so. |
| UX-046 | Submit an action (SCR-PLY-011, V2, Could) lets the player pick an action and a target from their own character's options and send it. It is a request: nothing is rolled until the DM approves (FR-LIV-004). The screen says Waiting for the DM, Approved or Declined, in text. |
| UX-047 | Journal (SCR-PLY-010) and Rules (SCR-PLY-009) are read only. The journal lists only entries whose audience includes the player, in game-time order, with no placeholder for the others (UX-007). Rules shows the house rules in plain words and the reference, each with its source label (4.10). |

**DM screens (SCR-DM-001 to 019).**

| ID | Behaviour |
| --- | --- |
| UX-048 | Campaigns lists name, state and last session. Archive is one control and reversible (FR-CAM-004). Delete asks for the campaign name (UX-002). |
| UX-049 | Campaign home shows the recap, the session controls (start, pause, resume, end) with the state in text, and the game clock with an Advance control. The clock never moves by itself (FR-CLK-001). |
| UX-050 | Party lists every character of the campaign with hit points, armor class and conditions. A row opens the character sheet (SCR-PLY-003), where the DM can edit. |
| UX-051 | Encounters lists each encounter with its name, creature count and status (not started, active, finished). Run opens the combat screen. The empty state is the one in 4.6. |
| UX-052 | House rules groups settings by chapter. Each setting has its plain-language explanation, its default marked, a "Changed from default" label in text when it differs, and its mode: ask as needed, configure now, or defaults only (FR-SET-006). |
| UX-053 | Scenes and light lists scenes with their map, participants and ambient light (bright, dim, dark) as a segmented control. A change applies at once and is logged. A scene may have no encounter (FR-SCN-001, FR-SCN-002). |
| UX-054 | Journal and quests (SCR-DM-018, B) shows each entry or quest with its in-game time and its audience in text (DM only, everyone, chosen players). Events captured automatically (FR-JRN-002) appear as candidates that the DM keeps or dismisses. Nothing reaches a player until the DM keeps it. |
| UX-055 | Checkpoints, export and import (SCR-DM-019, B). The list shows when each checkpoint was made and why (before an encounter, session end, on demand). Restore asks for the campaign name and says that a checkpoint of the current state is saved first, so a restore can be undone by restoring that one (UXQ-16, FR-CHK-002). Export downloads one file and lists what it holds: state, homebrew packs, assets and the pack manifest. Import checks the file and shows a review (name, versions, sizes, problems) before it creates a new campaign. It never overwrites one. A refused file says why in text (FR-EXP-001). The export also holds who plays and which character each person owns, by username, so the screen says that the file names people and holds DM-only information, and that it should be kept private (SEC-022). The import review shows each username as matched to an account here or as account missing (UXQ-23). |
| UX-056 | Rules reference (SCR-DM-016) has a search field and four filters: monsters, spells, items and conditions. An entry shows the stat block, the image when there is one and the source label. A DM-only item says "DM only" in text (UX-007). The header search and the / key reach it from every page. |
| UX-058 | Held characters (SCR-DM-004, B, UXQ-23). After an import, a character whose username has no account here is listed under Account missing, with its name, class, level and the username it waits for. A held character stays in the campaign and the DM can open and edit it, but it takes no part in an encounter start, a rest or the unplaced warning. Keeping it needs no action. Discard asks for the character's name (UX-002) and says that the character is removed. When someone joins with that username, the character moves to their list and the log records it. |
| UX-059 | Correcting a value (SCR-DM-010, A, UXQ-24). Correct opens a side panel beside the log, not a dialog, so the table stays in view. It starts from a log entry (Correct this entry, Undo) or from a creature (Adjust). The panel asks what changed (hit points, a condition, a resource or initiative), the new value, and a reason of 1 to 200 characters that only the DM ever sees. A choice named Who sees this follows, with two options: Players see a correction, which is the default, and Only me. Under it the panel shows the sentence players will read, as the server builds it (API-ENC-015). Only me is offered only when no player can see the changed value. When a player can, the option stays on screen but unavailable, with the reason beside it in text: "Players can see this value, so they will see the correction." The button reads Apply correction and nothing is asked afterwards; the latest correction can be undone with Undo (UXQ-8, UX-002). If a player gained sight of the value while the panel was open, the server refuses Only me (UXD-11), and the panel opens again with that option unavailable and the banner "A player can now see this value, so the correction will be shown to them." |
| UX-060 | What a player reads (SCR-PLY-006, B, UXQ-24). A player sees a correction only for a value they can see (FR-LIV-002). The log shows one standard sentence that names the creature and the kind of change, for example "The DM corrected Mira's hit points. Mira now has 14 of 22." or "The DM undid the last action: Goblin 2 takes 7 damage." The new value appears only where that player's own view already shows it. A player never sees the old value, the DM's reason or a quiet correction, and a quiet correction sends no signal, so nothing shows that it happened (SEC-012). The earlier entry stays in the log, marked Corrected or Undone in words, and links to the new entry. Nothing interrupts the table: the new entry is added to the log and a screen reader announces it politely, with no dialog, toast or sound. The sentence names the DM and says nothing about a mistake. Before milestone B a player has no view of an encounter, so only the DM sees corrections and the audience is still recorded. |
| UX-061 | The DM's log of a correction (SCR-DM-010 and SCR-DM-015, A, UXQ-24). Each correction lists the old value, the new value, the reason and who sees it, in words: Shown to players, or Only the DM (quiet). It links to the entry it cites. A quiet correction carries a Quiet label, so the DM can find it when a player asks about a number. |

**Administration (SCR-ADM-001, 002).**

| ID | Behaviour |
| --- | --- |
| UX-057 | Users lists accounts with Deactivate and Reset password. A reset shows the temporary password once and requires a new one at the next sign-in (UXQ-6). This installation states health, space used and simulator version in plain words, with the details for a bug report behind one control (UXQ-2). |

### 4.5 Interaction patterns

**Reaction window (SCR-DM-011, SCR-PLY-007).** A window is a persisted pause (FR-ACT-005, FR-RXN-004).

| ID | Behaviour |
| --- | --- |
| UX-012 | The panel opens in place on the combat screen when the server marks a window open, including after a reload or restart. Focus moves to its heading, which names the creature and the trigger, so the move itself announces the window. When the window closes, focus returns to the control that started the action, or to the result card. |
| UX-013 | While the panel is open, every control that would start another action is inert. Answering and cancelling still work (FR-RXN-009). The log stays readable. |
| UX-014 | The panel shows who may react, what option is offered and what it costs, in the fixed order of FR-RXN-003. It shows only what the deciding person may know (FR-RXN-006, FR-RXN-011): the total, the natural die, or the outcome, as the setting says. |
| UX-015 | Each option is a button, and Pass is a button of the same size beside them. An option that needs a follow-up choice shows it next, and that choice has no Pass (FR-RXN-010). |
| UX-016 | A stack line shows nesting ("Reaction 2 of 6, to the opportunity attack on Mira"). At the limit the panel says no further reactions are offered, and the log records it (FR-RXN-005). |
| UX-017 | Cancel the action is a two-step control inside the panel and says what stays: "The action will not apply. Dice already rolled stay in the log." (FR-RXN-007). |
| UX-018 | On the player screen (B) the prompt fills the screen, with buttons at least 44 pixels high and a "Always pass on {feature}" checkbox (FR-RXN-008). The DM can answer for any creature. |

A state diagram of the window follows in this section.

&#91;embedded content: reaction window · 5 states\]

Answering, passing and cancelling work while the window is open. Anything else waits (FR-RXN-009).

**Setting decision (SCR-DM-017, B).** A command that needs an undecided setting returns SETTING\_DECISION\_REQUIRED before any die is rolled. The response is a panel with the setting name, each option with its plain-language explanation from the registry, the default marked, and three choices: use an option, use the default this time, always use defaults (FR-SET-006, FR-SET-007). The decision is its own campaign action with its own id. The original command is not lost, because it stored nothing; the panel keeps it and sends it again with its original action id once the decision is saved. A setting that becomes necessary after dice were rolled uses the default for that action and stays undecided, and nothing is asked (FR-SET-007).

**Confirmation for irreversible changes (UX-002).** The control changes in place to a statement of the consequence and a second button. Deleting a campaign and restoring a checkpoint ask for the name to be typed. No modal dialog is used for confirmation.

**Forms.** Labels are visible and above the field. Required fields say so in text. Errors sit next to the field and in a summary at the top that receives focus after a failed post (WCAG 3.3.1, 3.3.3). `autocomplete` is set on sign-in and registration. The character and level-up wizards keep every earlier answer when the person goes back or the server rejects a step (WCAG 3.3.7).

### 4.6 States

| State | Rule |
| --- | --- |
| Loading | Nothing for the first 300 ms. After that a skeleton of the region, with `aria-busy`. A wait over 1 second says what is happening. A wait over 10 seconds offers Cancel. |
| Empty | Says what the region is for and offers the next action ("No encounters yet. Build one."). Never a blank panel. |
| Error | The error fragment from 3.3 in the region that failed, with a retry control where a retry is safe. Other regions stay usable. |
| Success | The changed region updates, and a short status line is announced politely. A success never opens a dialog. |
| Conflict (stale) | The banner from 3.4 and the refreshed region. |
| Disabled | The reason is in text next to the control. |
| Connection lost | The browser cannot reach the server, or gets no answer in 30 seconds. A status line, "Cannot reach the server.", and a Retry control. The form keeps what was typed. Retry sends the same action id, so a command that did arrive is not applied twice (3.4). |
| Pathfinder slow or down | Nothing is shown except a longer wait, under the loading rule above. The engine falls back to its own path search (NFR-REL-001). |
| Stream lost (B) | A status line, "Live updates paused. Reconnecting.", and a Refresh control. On reconnect the state event refreshes the regions. |
| Session expired | The sign-in page opens with "Sign in to continue." and returns the person to the page they were on (UNAUTHENTICATED, SEC-015). The server keeps nothing typed into an unsent form, so the screen tells the person when a form was lost (CSRF\_INVALID). |
| Code not accepted | One message beside the code field, with the next step, and the field keeps what was typed (UX-038, UX-040). The message is the same for an unknown, expired or used-up code. |
| File refused | A map image, a monster image or an import file that fails its checks says why in text beside the upload control (wrong type, over the size limit, not a valid export). Nothing is saved (UX-019, UX-055). |
| Window open | The reaction panel (UX-012 to UX-017) and the inert regions. |

### 4.7 Copy

Sentence case. The names of rules, conditions and creatures come from the content, so a house rule or a homebrew creature appears under its own name. Messages name the creature or item involved. Each code in the Architecture and in 3.3 has one catalogue entry with a message and a next step. The rows below cover the codes this document names. The build adds a row for every other code in the Architecture table, and T-157 fails when one is missing. The style:

| Code | Message | Next step |
| --- | --- | --- |
| NOT\_YOUR\_TURN | It is Goblin 2's turn, not Mira's. | Choose Goblin 2, or end its turn. |
| NO\_ACTIONS\_LEFT | Mira has used her action this turn. | Use a bonus action or move, or end the turn. |
| TARGET\_OUT\_OF\_RANGE | The target is {distance} ft away and the {weapon} reaches {range} ft. | Move closer or choose another target. |
| REACTION\_PENDING | A reaction is waiting. | Answer it or cancel the action first. |
| REACTION\_NOT\_PERMITTED | Only the DM or {creature}'s player can answer for {creature}. | Ask your DM. |
| INVALID\_PATH | That path is not valid. | Choose the destination again. |
| ENCOUNTER\_VERSION\_CONFLICT | This changed while you were looking at it. | Check the new state and try again. |
| CAMPAIGN\_VERSION\_CONFLICT | This changed while you were looking at it. | Check the new state and try again. |
| SETTING\_DECISION\_REQUIRED | A house rule needs a decision first. Nothing was rolled. | Choose an option for {setting}. |
| SESSION\_NOT\_ACTIVE | The session is paused. | Resume it to continue. |
| REST\_NOT\_ALLOWED | {creature} cannot take this rest. {reason} | Choose a different rest or remove {creature} from it. |
| PACK\_REVIEW\_STALE | The warnings for this pack changed while you were reviewing. | Read the new review and confirm again. |
| VALIDATION\_FAILED | Some fields need attention. | Fix the marked fields and send again. |
| NOT\_FOUND | That page or item does not exist, or you cannot see it. | Go back to the table. |
| UNAUTHENTICATED | Sign in to continue. | You will return to this page after signing in. |
| FORBIDDEN | Only {who} can do this. | Ask your DM if you think you should be able to. |
| CSRF\_INVALID | This page is out of date. | Reload the page and try again. Retype anything that was lost. |
| RATE\_LIMITED | Too many attempts. | Try again in {seconds} seconds. |
| IDEMPOTENCY\_KEY\_CONFLICT | Something went wrong with this request. | Reload and try again. If it repeats, give whoever runs the server this reference: {traceId}. |
| SERVICE\_UNAVAILABLE | The application cannot reach its database. | Wait a moment and try again. If it continues, tell whoever runs the server. |
| INTERNAL\_ERROR | Something went wrong on the server. | Try again. If it repeats, give whoever runs the server this reference: {traceId}. |
| ENCOUNTER\_ACTIVE | A rest cannot start while an encounter is active. | End the encounter, then rest. |
| REST\_TOO\_SHORT | This rest is shorter than the campaign allows. | Choose a longer rest or change the minimum in house rules. |
| HIT\_DICE\_UNAVAILABLE | {creature} has {count} Hit Dice left. | Spend {count} or fewer. |
| REACTION\_OPTION\_INVALID | That option is no longer offered. | Read the window again and choose from what it shows. |
| SETTING\_INVALID | That value is not allowed for {setting}. | Choose one of the options listed. |
| INVITE\_INVALID | This code is not valid. It may have expired or been used up. | Ask your DM for a new code. |
| ASSET\_INVALID | {filename} could not be used as an image. | Use a PNG, JPEG or WebP file under {maxMegabytes} MB. |
| ENCOUNTER\_STARTED | This encounter has already started. | Add the creature to the running encounter instead. |
| MAP\_IN\_USE | {count} encounters use this map. | Give those encounters another map, then delete this one. |
| PLACEMENT\_INVALID | {creature} cannot stand there. {reason} | Choose another square, or change the area. |
| LAST\_ADMINISTRATOR | This is the only administrator account. | Keep it active. The installation needs one administrator. |
| CONTENT\_INVALID | {filename} does not follow the content format. | Fix the problems listed and upload it again. Nothing was saved. |
| PACK\_NOT\_ENABLED | The pack {pack} is not enabled in this campaign. | Enable the pack first, or choose other content. |
| REACTION\_NOT\_AVAILABLE | {creature} has no reaction available. | Choose Pass. |

The copy never blames the person, never shows an exception message, and never says "invalid" without saying what is wrong.

### 4.8 Accessibility

The bar is WCAG 2.2 level AA on every screen in 4.3 (UXD-9), which tightens NFR-USA-002. The table says how each newer or less obvious criterion is met here.

| Criterion | How |
| --- | --- |
| 2.1.1, 2.1.2 Keyboard | Every function works by keyboard. The map is a grid with arrow-key movement between cells, Enter to select, Escape to clear. No trap. |
| 2.1.4 Character key shortcuts | Single-key shortcuts act only while the console has focus, and the account page turns them off or remaps them. |
| 1.1.1 Text alternative for the map | A table lists each creature, its cell and its distance from the acting creature, and offers the same select actions as the map. |
| 1.3.1, 2.4.6 Structure | One h1 per page, naming the screen. Landmarks are header, navigation, main and footer. Headings do not skip levels. The map is `role="grid"`, the turn order is a list, and each action chip is a button whose name includes its state. |
| 1.3.4 Orientation | No orientation lock. Both orientations work on a phone and a tablet. |
| 1.4.3, 1.4.11 Contrast | 4.5:1 for text, 3:1 for controls, focus rings and the map's markers. Outcomes (hit, miss, critical), conditions and factions use text or an icon as well as colour. |
| 1.4.4, 1.4.12 Text size and spacing | Text resizes to 200 percent and accepts the user's own spacing settings without loss of content. Layout uses relative units and no fixed heights on text. |
| 1.4.10 Reflow | Single column at 320 px with no sideways scroll. The map scrolls inside its own region, which the criterion allows for a map. Controls stay outside that region. |
| 1.4.13 Content on hover or focus | A roll's working opens on click or Enter, never on hover alone. Any tooltip can be dismissed with Escape, stays while hovered and hides nothing else. |
| 2.2.1 Timing | The only time limit is session expiry, 8 hours idle by default (SEC-004), and the sign-in page returns the person to the same page. A reaction window has no timer. |
| 2.4.3 Focus order | Order follows the reading order in 4.9. Opening a panel moves focus into it. Closing it returns focus to the control that opened it. |
| 2.4.11 Focus not obscured | Sticky header and the reaction panel reserve space with scroll-padding, so a focused control is never fully hidden. |
| 2.4.7, 2.4.13 Focus visible | A 3 px ring with 3:1 contrast against both neighbours, on every control. |
| 2.5.3 Label in name | A control's accessible name starts with its visible label. |
| 2.5.7 Dragging | No drag operation exists (UX-011). |
| 2.5.8 Target size | 24 by 24 CSS pixels at least on every surface, 44 by 44 on the player view and wherever space allows. |
| 3.1.1 Language | The page language is set on the html element. |
| 3.2.6 Consistent help | The help link is in the footer and the account menu on every page, in the same place. |
| 3.3.7 Redundant entry | The wizards keep earlier answers. The join code is not asked again after registration. |
| 3.3.8 Accessible authentication | No puzzle, no recall test. Paste into the password field is allowed and autocomplete is set. |
| 4.1.3 Status messages | The log is `role="log"`, polite. A reaction window moves focus to its heading and adds no second alert. A change of turn is announced politely. Updates caused by other people are batched for one second and are not read out for every roll. |
| Motion | Dice and transitions are short and stop under `prefers-reduced-motion`. |

**Testing.** An automated scan (axe) runs in CI on every screen in 4.3 and on each state in 4.6 that has its own markup. A keyboard-only pass of the flows in 4.4 and one screen-reader pass (NVDA with Firefox, VoiceOver with Safari on a phone) run before each release. A failure of the scan blocks the build. The tests are listed in section 7.

### 4.9 Layout and devices

| Width | DM console | Player view |
| --- | --- | --- |
| 320 to 767 px | One column. The turn order is a strip above the acting panel. The log and the map are tabs. The rail is a menu. | One column. Bottom tab bar. One task per screen. |
| 768 to 1023 px | Two columns: the acting panel and the map, with the turn order and the log in a drawer. | Two columns on a tablet in landscape. |
| 1024 px and wider | Three columns: turn order, map and acting panel, with the log below. Dense type. | As the tablet layout, centred, at most 960 px. |

The application serves its own fonts or none. The default is the system font stack, because a LAN game night may have no internet. Pointer and touch behave the same.

### 4.10 Rules attribution

CS-SRD-010 asks this document to say where the SRD attribution appears. SCR-ACC-005 holds the statement that the Content Schema defines. It is reachable from the footer of every authenticated page. Every screen that shows rules text, a stat block, a spell or an item also carries a source label with the pack name, which links to SCR-ACC-005 for SRD 5.1 content. A viewer therefore reaches the statement from the same screen. The statement is not copied here.

### 4.11 Visual direction

The owner chose the direction on 2026-11-21 (UXQ-1). The web app is built on Material 3. The dark theme is the Material 3 Teal dark scheme. The light theme is Sky, a custom scheme built from two light blues: a pale one for the page and a deeper one for panels. The screens are in the Figma file "TTRPG DM Tool: UX screens (draft)", page "v2 Material 3", and the values below are the ones those screens use. The file's Index page maps each screen id to its dark and Sky frames.

- Colour roles use the Material 3 names and live as CSS custom properties, one set per theme. Every pair used for text meets the contrast rules in 4.8.
- The themes are selected by `prefers-color-scheme`, with a manual override on the account page.
- Warning and success are not Material roles. The screens use the tertiary container for attention and suggestions and the error container for danger, always with text beside the colour.
- Type is Roboto on the Material 3 scale, served by the application itself (4.9). Spacing is a 4-pixel scale. Base text is 16 px on the player view and 14 to 16 px on the DM console.
- Shape carries meaning together with colour: player characters are circles and monsters are rounded squares.
- The strict content security policy forbids inline styles and scripts, so the tokens are served in a stylesheet. The components are our own CSS, written to the Material 3 specification, with no component library (Architecture, web layer). The Figma kit is the visual reference.

Colour roles in both themes:

| Role | Dark (Teal) | Light (Sky) |
| --- | --- | --- |
| Surface | #0E1513 | #F5FAFE |
| Surface container lowest | #090F0E | #FBFDFF |
| Surface container low | #171D1B | #D7EAF9 |
| Surface container | #1A211F | #CBE3F6 |
| Surface container high | #252B2A | #BCD9F0 |
| Surface container highest | #303634 | #A9CBE8 |
| On surface | #DDE4E1 | #0B2A40 |
| On surface variant | #BEC9C5 | #3A576E |
| Outline | #88938F | #5F7A90 |
| Outline variant | #3F4946 | #9DB8CC |
| Primary | #82D5C7 | #0B5C9E |
| On primary | #003731 | #FFFFFF |
| Primary container | #006B5F | #9CCBF0 |
| On primary container | #FFFFFF | #042B49 |
| Secondary | #B1CCC6 | #3F6A8C |
| Secondary container | #334B47 | #FFFFFF |
| On secondary container | #CCE8E2 | #0B2A40 |
| Tertiary | #ACCAE5 | #7A5A00 |
| Tertiary container | #2D4A60 | #FFE4A0 |
| On tertiary container | #CBE6FF | #3E2D00 |
| Error | #F2B8B5 | #B3261E |
| Error container | #8C1D18 | #FFD9D4 |
| On error container | #F9DEDC | #6E0A02 |

Measured contrast. In Sky the lowest text pair is on surface variant on surface container high, 5.17:1, and body text on the page is 14.08:1. Outline on the page is 4.28:1 and on the highest container 3.06:1, above the 3:1 minimum for the edges of controls. In Teal the lowest text pair is on primary container, 6.43:1, and outline on the highest container is 3.89:1. The step between the Sky page and its panels is 1.26:1 on purpose: panels are separated by the colour step and controls by their outline, never by the step alone.

### 4.12 Design reference and coverage

Every screen in 4.3 has a frame in the Figma file "TTRPG DM Tool: UX screens (draft)", page "v2 Material 3", in the dark theme and in Sky. The Index page lists each frame with its screen id, its dark frame, its Sky frame and its milestone. This part holds the rules and the frames are examples of them. Where a frame and a rule differ, the rule wins and the frame is corrected.

| Item | What the frames show |
| --- | --- |
| Coverage | All 39 screens in 4.3 are drawn. Several have more than one frame for a state that matters: first run, empty list, invite code expired, localhost warning, a long and a short rest, and the prompt and sent forms of a player reaction. |
| Sizes | DM screens 1660 by 900. Account screens 1280 by 800 (the account page is 1280 by 1000). Player screens on a phone are 390 by 844, except SCR-PLY-006. |
| Not drawn | SCR-PLY-006 is drawn at 1280 wide only, and its phone layout follows 4.9. The DM console at widths under 1024 px follows 4.9 and is not drawn. Loading skeletons, connection lost and stream lost (4.6) are specified in text only. |
| Partial | SCR-DM-016 shows the list and one monster entry. Spell, item and condition entries use the same layout with their own fields. SCR-DM-017 is a side sheet over the combat screen, not a page. |
| Placeholders | Navigation icons are generic. The build chooses the icon set. Names, numbers and rules text on the frames are example data. The attribution wording on SCR-ACC-005 is the usual SRD 5.1 statement. The Content Schema defines the real text (CS-SRD-010). |
| Components | The frames use the Material 3 kit. How the build implements them is open (4.11). |

## 5. Part C: API

This part lists every route the browser calls. It is the contract between the templates, the controllers and the tests. By UXD-2 there is no public JSON API in version 1, so there is no OpenAPI file: the routes return HTML pages and fragments, and the catalog below is the reference. If a JSON API is added later, it gets an OpenAPI description at that point.

Sections 5.1 to 5.12 list the routes, and 5.18 and 5.19 add the routes for the move preview, maps and placement. Sections 5.13 to 5.21 give the schemas: the field types, what each route accepts and returns, the errors, the replay rules, paging, limits and how the contract may change. A route is read together with its rows in 5.17 to 5.20. The owner accepted this part on 2026-11-21.

**Notation.**

- `~` means `/campaigns/{campaignId}`.
- **Who**: Any (no session), SI (signed in), Mem (member of the campaign), P (a player of the campaign), DM, Adm (instance administrator).
- **Key** says what makes a repeat safe. **E**: `actionId` and the encounter's `expectedVersion`, stored in `combat_event`. **K**: `actionId` and the campaign's `expectedVersion`, stored in `campaign_action`. **T**: a single-use form token, for requests that create something outside a campaign's action log (open question UXQ-7). **N**: the request sets a state, so repeating it changes nothing. A dash is a read.
- Every POST also carries the CSRF token (SEC-006). The envelope fields are bound by the controller and are not repeated below. The fields of a command are the engine's action record (Architecture 5.1) bound one to one.
- A transition of an existing thing takes its name (`/start`, `/end-turn`, `/pause`). A request that records an event takes a plural noun (`/attacks`, `/rests`).
- **Errors** lists only the codes specific to the route. Every route can also return UNAUTHENTICATED, NOT\_FOUND (campaign routes), FORBIDDEN (wrong role), INTERNAL\_ERROR, and, where the route takes a body, VALIDATION\_FAILED and CSRF\_INVALID. Rate-limited routes (3.8) can return RATE\_LIMITED. Codes are in the Architecture, section 9, and in 3.3.
- **Rel** is the first release (A is version 1, B is version 1.5, V2 is version 2).

### 5.1 Identity and account (IDN)

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-IDN-001 | GET `/login` | Any |  | Page SCR-ACC-001 |  | A |
| API-IDN-002 | POST `/login` | Any |  | 303 to the return address, a relative path on this site that is checked | UNAUTHENTICATED (401, one message for every failure), RATE\_LIMITED | A |
| API-IDN-003 | GET `/register` | Any |  | Page SCR-ACC-002 |  | A |
| API-IDN-004 | POST `/register` | Any | T | 303 to `/campaigns` | INVITE\_INVALID, RATE\_LIMITED | A |
| API-IDN-005 | POST `/logout` | SI | N | 303 to `/login`; the session row is deleted |  | A |
| API-IDN-006 | GET `/account` | SI |  | Page SCR-ACC-003 |  | A |
| API-IDN-007 | POST `/account/password` | SI | N | Fragment `#account-password`; the other sessions end | RATE\_LIMITED | A |
| API-IDN-008 | POST `/account/sessions/{sessionId}/revocations` | SI | N | Fragment `#sessions` | NOT\_FOUND for another person's session | A |
| API-IDN-009 | POST `/account/preferences` | SI | N | Fragment `#preferences` (shortcuts, theme) |  | A |
| API-IDN-010 | GET and POST `/join` | SI | T | Page SCR-ACC-004; 303 into the campaign | INVITE\_INVALID, RATE\_LIMITED | A |
| API-IDN-011 | GET `/about` | SI |  | Page SCR-ACC-005 |  | A |

### 5.2 Campaign, sessions, clock, scenes, invites (CMP)

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-CMP-001 | GET `/campaigns` | SI |  | One page for SCR-DM-001 and SCR-PLY-001; the role is shown per campaign |  | A |
| API-CMP-002 | POST `/campaigns` | SI | T | 303 to `~/setup`; the creator becomes DM |  | A |
| API-CMP-003 | GET `~` | Mem |  | SCR-DM-003 for the DM, SCR-PLY-002 for a player |  | A |
| API-CMP-004 | GET and POST `~/setup` | DM | K | Wizard step fragment; the last step 303 to `~/players` | CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CMP-005 | POST `~/sessions` | DM | K | Fragment `#session-bar` | CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CMP-006 | POST `~/sessions/{sessionId}/pause`, `/resume`, `/end` | DM | K | Fragment `#session-bar` | CAMPAIGN\_VERSION\_CONFLICT, REACTION\_PENDING | A |
| API-CMP-007 | POST `~/clock/advances` | DM | K | Fragment `#clock`, plus any region an expiring effect changes | REACTION\_PENDING, SESSION\_NOT\_ACTIVE, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CMP-008 | GET `~/players` | DM |  | Page SCR-DM-004 with the address, the invites and, from B, the held characters of an import (UXQ-23) |  | A |
| API-CMP-009 | POST `~/invites` | DM | T | Fragment `#invites`; the code is shown once, and only its hash is stored |  | A |
| API-CMP-010 | POST `~/invites/{inviteId}/revocations` | DM | K | Fragment `#invites` |  | A |
| API-CMP-011 | GET `~/scenes` | DM |  | Page SCR-DM-014 |  | A |
| API-CMP-012 | POST `~/scenes`, POST `~/scenes/{sceneId}/edits`, POST `~/scenes/{sceneId}/activations` | DM | K | Fragment `#scene-list` | VALIDATION\_FAILED, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CMP-013 | POST `~/archive` | DM | K | 303 to `/campaigns` | CAMPAIGN\_VERSION\_CONFLICT | B |

### 5.3 Characters (CHR)

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-CHR-001 | GET `~/characters` | P, DM |  | SCR-PLY-002 (own) or SCR-DM-007 (all) |  | A |
| API-CHR-002 | GET `~/characters/new`, POST `~/characters` | P, DM | K | Wizard step fragment; the last step 303 to the sheet | PACK\_NOT\_ENABLED, CONTENT\_INVALID, SETTING\_DECISION\_REQUIRED, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CHR-003 | GET `~/characters/{characterId}` | Owner, DM |  | Page SCR-PLY-003 | FORBIDDEN for another player's visible character, NOT\_FOUND where it is not visible to them | A |
| API-CHR-004 | POST `~/characters/{characterId}/edits` | Owner (limited fields), DM | K | Fragment `#sheet` | REACTION\_PENDING, ENCOUNTER\_ACTIVE, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CHR-005 | POST `~/characters/{characterId}/level-grants` | DM | K | Fragment `#sheet` (milestone advancement, FR-CHR-012) | CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CHR-006 | GET and POST `~/characters/{characterId}/level-ups` | Owner, DM | K | Wizard step fragment; a warning step shows any ability excess that would be lost before it applies (FR-CHR-015) | REACTION\_PENDING, ENCOUNTER\_ACTIVE, SETTING\_DECISION\_REQUIRED, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CHR-007 | POST `~/characters/{characterId}/reaction-preferences` | Owner | K | Fragment `#reaction-preferences` (always pass, FR-RXN-008) |  | B |
| API-CHR-008 | POST \~/characters/{characterId}/discards | DM | K | Fragment #held-characters (UXQ-23) | ENCOUNTER\_ACTIVE, CAMPAIGN\_VERSION\_CONFLICT | B |

### 5.4 Encounters and combat (ENC)

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-ENC-001 | GET `~/encounters` | DM |  | Page SCR-DM-008 |  | A |
| API-ENC-002 | POST `~/encounters` | DM | K | 303 to the builder | PACK\_NOT\_ENABLED, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-ENC-003 | GET `~/encounters/{encounterId}` | DM; P from B |  | Builder (SCR-DM-009) before the start, combat screen (SCR-DM-010) after; the filtered table (SCR-PLY-006) for a player | FORBIDDEN for a player before B | A |
| API-ENC-004 | POST `~/encounters/{encounterId}/combatants` | DM | E | Fragment `#turn-order` and out-of-band regions | PACK\_NOT\_ENABLED, REACTION\_PENDING, ENCOUNTER\_VERSION\_CONFLICT | A |
| API-ENC-005 | POST `~/encounters/{encounterId}/combatants/{combatantId}/removals` | DM | E | Fragment `#turn-order` | REACTION\_PENDING, ENCOUNTER\_VERSION\_CONFLICT | A |
| API-ENC-006 | POST `~/encounters/{encounterId}/start` | DM | E | The combat screen regions, with the initiative dice in the log | SESSION\_NOT\_ACTIVE, ENCOUNTER\_STARTED, PLACEMENT\_INVALID, ENCOUNTER\_VERSION\_CONFLICT | A |
| API-ENC-007 | POST `~/encounters/{encounterId}/initiative-ties` | DM | E | Fragment `#turn-order` (the DM's decision or a d20 roll-off, FR-ENC-002) | ENCOUNTER\_VERSION\_CONFLICT | A |
| API-ENC-008 | POST `~/encounters/{encounterId}/{command}` (5.4.1) | DM; P for the acting creature they own from B | E | Fragments `#result`, `#turn-order`, `#acting-panel`, `#log`, `#map`, or the reaction panel when a window opens | NOT\_YOUR\_TURN, NO\_ACTIONS\_LEFT, TARGET\_OUT\_OF\_RANGE, INVALID\_PATH, REACTION\_PENDING, SESSION\_NOT\_ACTIVE, SETTING\_DECISION\_REQUIRED, ENCOUNTER\_VERSION\_CONFLICT, IDEMPOTENCY\_KEY\_CONFLICT | A |
| API-ENC-009 | POST `~/encounters/{encounterId}/end-turn` | DM; P from B | E | Fragments `#turn-order`, `#acting-panel`, `#clock` | NOT\_YOUR\_TURN, REACTION\_PENDING, ENCOUNTER\_VERSION\_CONFLICT | A |
| API-ENC-010 | POST `~/encounters/{encounterId}/overrides` | DM | E | Fragments `#log` and the changed region | REACTION\_PENDING, ENCOUNTER\_VERSION\_CONFLICT | A |
| API-ENC-011 | POST `~/encounters/{encounterId}/end` | DM | E | The page without the combat regions; survivors' state written back (FR-ENC-008) | REACTION\_PENDING, ENCOUNTER\_VERSION\_CONFLICT | A |
| API-ENC-012 | GET `~/encounters/{encounterId}/{region}`, region one of `turn-order`, `acting-panel`, `map`, `log`, `reaction` | DM; P from B, filtered |  | The one region. `log` takes `before` and `limit`. |  | A |
| API-ENC-013 | GET `~/encounters/{encounterId}/reach?combatant={combatantId}` | DM |  | Fragment `#map` with the reachable cells. The application asks the pathfinder; the browser never does. A pathfinder failure only makes the answer slower (NFR-REL-001). | Common errors only (section 3.3) | A |

#### 5.4.1 Commands under API-ENC-008

Each command is its own controller method and its own template. None is a switch on a type field.

| Command | Records | Requirement |
| --- | --- | --- |
| `attacks` | An attack roll and, on a hit, damage | FR-ACT-001, FR-ACT-002 |
| `spells` | A spell cast and its effects | UC-05 |
| `moves` | A move along a path the pathfinder has checked | FR-ACT-006, FR-ACT-009 |
| `dashes`, `dodges`, `disengages`, `helps`, `hides`, `readies`, `object-uses`, `grapples`, `shoves` | The matching action | FR-ACT-004 |
| `saving-throws`, `ability-checks` | A save against a DC, or a check. Also valid in a scene as `~/scenes/{sceneId}/{command}` with the campaign's key and version. | FR-ACT-003 |
| `death-saves` | A death save for a creature at 0 hit points | FR-ENC-005 |

### 5.5 Reactions (RXN)

The window id identifies a window in an encounter or in a scene. The version is the encounter's, or the campaign's for a scene.

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-RXN-001 | POST `~/reactions/{windowId}/answers` (an option or Pass) | DM for any creature; P for own creatures from B | E | Fragment `#reaction` and the regions the resolved step changed | REACTION\_OPTION\_INVALID, REACTION\_NOT\_AVAILABLE, REACTION\_NOT\_PERMITTED, ENCOUNTER\_VERSION\_CONFLICT | A |
| API-RXN-002 | POST `~/reactions/{windowId}/follow-ups` | The same decider | E | As above. A follow-up has no Pass. | REACTION\_OPTION\_INVALID, ENCOUNTER\_VERSION\_CONFLICT | A |
| API-RXN-003 | POST `~/reactions/{windowId}/cancellations` | DM | E | Fragment `#reaction` closed, log entry, action not applied | ENCOUNTER\_VERSION\_CONFLICT | A |

### 5.6 Rests (RST)

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-RST-001 | GET `~/rests/new` | DM |  | Page SCR-DM-013 |  | A |
| API-RST-002 | POST `~/rests/previews` | DM |  | Fragment `#rest-preview`: what each person can spend and the warnings. It rolls no die and stores nothing. | REST\_NOT\_ALLOWED, HIT\_DICE\_UNAVAILABLE | A |
| API-RST-003 | POST `~/rests` | DM | K | Fragments `#rest-result` and `#clock`; every die in the log | REST\_NOT\_ALLOWED, REST\_TOO\_SHORT, HIT\_DICE\_UNAVAILABLE, ENCOUNTER\_ACTIVE, REACTION\_PENDING, SETTING\_DECISION\_REQUIRED, CAMPAIGN\_VERSION\_CONFLICT, IDEMPOTENCY\_KEY\_CONFLICT | A |
| API-RST-004 | POST `~/rests/{restId}/plans` (a player's Hit Dice plan) | P, own | K | Fragment `#my-rest` | HIT\_DICE\_UNAVAILABLE, CAMPAIGN\_VERSION\_CONFLICT | B |

### 5.7 House rules (SET)

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-SET-001 | GET `~/settings` | DM; P read only from B |  | Page SCR-DM-006 or SCR-PLY-009 |  | A |
| API-SET-002 | POST `~/settings/{key}/decisions` | DM | K | Fragment `#setting-row`. In ask-as-needed mode the panel then sends the held command again (4.5). | SETTING\_INVALID, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-SET-003 | POST `~/settings/{key}/resets` | DM | K | Fragment `#setting-row` | CAMPAIGN\_VERSION\_CONFLICT | A |
| API-SET-004 | POST `~/settings/mode` | DM | K | Fragment `#settings-mode` | SETTING\_INVALID, CAMPAIGN\_VERSION\_CONFLICT | A |

### 5.8 Content (CNT)

All pack routes are DM only (CS-HBR-003). A player has no pack route.

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-CNT-001 | GET `~/packs` | DM |  | Page SCR-DM-005 |  | A |
| API-CNT-002 | GET `~/packs/{packId}` | DM |  | Fragment `#pack-detail` |  | A |
| API-CNT-003 | POST `~/packs/{packId}/reviews` (for an enable, an upgrade or a disable) | DM |  | Fragment `#pack-review` with the warnings in groups and a review hash. Nothing is stored. | CONTENT\_INVALID | A |
| API-CNT-004 | POST `~/packs/{packId}/enablements` | DM | K | Fragment `#pack-list` | PACK\_REVIEW\_STALE, CONTENT\_INVALID, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CNT-005 | POST `~/packs/{packId}/disablements` | DM | K | Fragment `#pack-list` | PACK\_REVIEW\_STALE, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CNT-006 | POST `~/packs/{packId}/upgrades` | DM | K | Fragment `#pack-list` | PACK\_REVIEW\_STALE, CONTENT\_INVALID, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CNT-007 | POST `~/packs/{packId}/deletions` (the pack's name typed) | DM | K | Fragment `#pack-list` | PACK\_REVIEW\_STALE, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-CNT-008 | POST `~/packs` (multipart pack file) | DM | T | Fragment `#pack-review`. Size and entry limits are the Content Schema's. | CONTENT\_INVALID, RATE\_LIMITED | B |
| API-CNT-009 | GET `~/content/{type}`, GET `~/content/{type}/{contentKey}` | Mem, by type and audience |  | Browser page with search and paging; stat block, spell or item detail with its source label (4.10). Monster stat blocks are DM only. | NOT\_FOUND | A |

`PACK_REVIEW_STALE` (409) is a new code. The confirmation carries the hash of the review the DM saw. If the warnings the server now computes differ, nothing is applied and the review is shown again (4.4).

### 5.9 Assets (AST)

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-AST-001 | POST `~/monsters/{contentKey}/images` (multipart image) | DM | K | Fragment `#monster-image`. Part D has the file rules. | ASSET\_INVALID, RATE\_LIMITED, CAMPAIGN\_VERSION\_CONFLICT | A |
| API-AST-002 | POST `~/monsters/{contentKey}/images/removals` | DM | K | Fragment `#monster-image` showing the placeholder silhouette | CAMPAIGN\_VERSION\_CONFLICT | A |
| API-AST-003 | GET `~/assets/{assetId}` | Mem if the audience allows |  | The image bytes with `X-Content-Type-Options: nosniff`, `private` caching and a content-hash name | NOT\_FOUND | A |

### 5.10 Logs and live updates (LOG)

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-LOG-001 | GET `~/log` | DM; P from B, own audience |  | Page SCR-DM-015, cursor paging (3.5) |  | A |
| API-LOG-002 | GET `~/events` | Mem |  | `text/event-stream` (3.6) | NOT\_FOUND, RATE\_LIMITED | B |

**Stream events (B).**

| Event | When | Carries |
| --- | --- | --- |
| `state` | Once, on connect | For the DM, the encounter version and the campaign version. For a player, no version |
| `changed` | After a commit, to the audience that may know it | `scope` (`encounter`, `campaign`, `scene`, `reaction`), the id of that scope and, for the DM only, the new version |

The client maps each scope to the regions it refetches: `encounter` to the turn order, acting panel, map and log; `reaction` to the reaction panel; `campaign` to the clock, session bar and party strip; `scene` to the scene region. No event carries game data.

### 5.11 System and administration (SYS)

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-SYS-001 | GET `/health` | Any |  | 200 `{"status":"UP"}`, or 503 when the database cannot be reached. No version and no detail. |  | A |
| API-SYS-002 | GET `/admin/users` | Adm |  | Page SCR-ADM-001 | FORBIDDEN | A |
| API-SYS-003 | POST `/admin/users/{userId}/deactivations` | Adm | N | Fragment `#user-row`; the person's sessions end | FORBIDDEN | A |
| API-SYS-004 | POST `/admin/users/{userId}/password-resets` | Adm | T | Fragment `#user-row` showing a temporary password once (open question UXQ-6) | FORBIDDEN | A |
| API-SYS-005 | GET `/admin/status` | Adm |  | Page SCR-ADM-002 | FORBIDDEN | A |

### 5.12 Version 1.5 and version 2 routes without a screen detail yet

| ID | Request | Who | Key | Success | Rel |
| --- | --- | --- | --- | --- | --- |
| API-JRN-001 | GET `~/journal`; POST `~/journal/entries`; POST `~/journal/entries/{entryId}/edits` | DM writes, P reads the entries whose audience includes them | K | SCR-DM-018, SCR-PLY-010 | B |
| API-CHK-001 | GET `~/checkpoints`; POST `~/checkpoints`; POST `~/checkpoints/{checkpointId}/restorations` (the campaign name typed) | DM | K | SCR-DM-019. A restore is atomic and logged (FR-CHK-002). | B |
| API-EXP-001 | POST `~/exports` | DM | N | A file download with `Content-Disposition: attachment`. The file carries its own format version (UXD-2) and the campaign's access data (UXQ-23). | B |
| API-EXP-002 | POST `/imports` (multipart) | SI | T | A new campaign owned by the importer. Schema, size and version checks as in FR-EXP-001; errors CONTENT\_INVALID and RATE\_LIMITED. | B |
| API-LIV-001 | POST `~/encounters/{encounterId}/proposals`; POST `~/encounters/{encounterId}/proposals/{proposalId}/decisions` | P proposes for own creature, DM decides | E | SCR-PLY-011 (FR-LIV-004, Could) | V2 |

### 5.13 Field types and binding rules

The browser sends application/x-www-form-urlencoded. Only the upload routes (API-AST-001, API-CNT-008, API-EXP-002 and API-MAP-002) send multipart/form-data. A client that asks for application/json gets the same field names in a JSON object. Each route has one form object (SEC-003), and the field tables in 5.17 to 5.20 are that object.

| Type | Wire form | Rules |
| --- | --- | --- |
| uuid | Lower-case canonical UUID | A malformed id in a path is NOT\_FOUND, the same answer as an unknown one. A malformed id in a body is VALIDATION\_FAILED with INVALID\_FORMAT. |
| key | A content key | The key format of the Content Schema, URL-encoded in a path. An unknown key is NOT\_FOUND in a path and UNKNOWN\_VALUE in a body. |
| text | UTF-8 | Normalised to NFC and trimmed. Control characters are refused, except a line break in a multi-line field. Length counts characters, not bytes. Always shown as text (SEC-015). |
| int | Decimal digits, with a leading minus where the range allows it | Parsed by the one function that reads the viewer's locale on input (3.2). The range is in each table. |
| flag | A checkbox | Present means true. Absent means false. |
| choice | One value from a list | Lower-case kebab-case. A value outside the list is UNKNOWN\_VALUE. |
| cell | Two fields, column and row | Zero-based integers inside the map. Column counts from the left, row from the top. |
| area | Four fields: fromColumn, fromRow, toColumn, toRow | Inclusive corners, with the from corner not after the to corner, inside the map. |
| file | One multipart part named file | The type is decided from the content (SEC-013). Size and pixel limits are in 5.21. |

**Binding rules.**

- A field that is not in the form object is ignored and never bound (TM-009, T-124).
- A single-valued field sent twice is refused with REPEATED. A multi-valued field is named as one in its table.
- Input is validated once, at the controller (3.3). A refusal lists every field that failed, not only the first.
- A text field that is empty after trimming counts as missing when it is required and as absent when it is optional.
- The client never sends a result. No die, roll total, hit, miss or damage result, role, owner or campaign id is a field. A value that is the DM's own entry, such as an override amount or a journal time, is a field of its route and is checked there. The campaign comes from the path and the principal from the session (SEC-003, TM-008, TM-009).
- A password, an invite code and a form token are never echoed in a response, a log line or an error detail (SEC-010).

**Envelope fields.** These are bound by the controller on every route that has the key, and are not repeated in the tables below.

| Field | Type | Sent on | Rule |
| --- | --- | --- | --- |
| \_csrf | string | Every POST, as a hidden field or as the X-CSRF-TOKEN header | SEC-006. Missing or wrong gives CSRF\_INVALID. |
| actionId | uuid | Keys E and K | Minted by the server into the form (3.4). Required. |
| expectedVersion | int | Keys E and K | The encounter version for E and the campaign version for K. At least 0. Required. |
| formToken | uuid | Key T | Single use, minted into the form. Required. Open question UXQ-7. |

### 5.14 Success responses and headers

| Request kind | Success | Headers |
| --- | --- | --- |
| Page or region read (GET) | 200 with the page or the one region | Cache-Control: no-store. Vary: HX-Request. Content-Type: text/html; charset=UTF-8 |
| Command that changes a region, from htmx | 200 with the changed region and the out-of-band regions that moved with it | As above |
| Command that moves the person to another screen, from htmx | 200 with an empty body | HX-Redirect: the path of the next screen |
| Command without scripting | 303 | Location: a relative path, the page the person should see |
| A replay of a command that already succeeded | The same response as the first one (5.16) | The same |
| Download (API-EXP-001) | 200 with the file | Content-Disposition: attachment, a fixed Content-Type, Cache-Control: no-store, X-Content-Type-Options: nosniff |
| Image (API-AST-003) | 200 with the bytes, or 304 when If-None-Match matches | Content-Type from the stored type, ETag: the content hash, Cache-Control: private, max-age=86400, X-Content-Type-Options: nosniff |
| Stream (API-LOG-002, B) | 200, text/event-stream | Cache-Control: no-store. X-Accel-Buffering: no |
| Health (API-SYS-001) | 200 {"status":"UP"}, or 503 {"status":"DOWN"} | Content-Type: application/json. Cache-Control: no-store |

Status 201 and 204 are not used. A form post that creates something moves the person to it, and an htmx swap needs a body. In version 1 only the health route and the error model have a JSON form. A route without one answers a client that does not accept text/html with its HTML.

Request headers the application reads: Cookie (the session, SEC-005), X-CSRF-TOKEN, Origin (it must match the host, SEC-006), HX-Request (3.2), Accept, Content-Type and If-None-Match (images only). The Content-Type of a body must be the form type or, on an upload route, multipart/form-data. Any other type is VALIDATION\_FAILED. A form body is limited to 64 KB, and the map editor save to 1 MB; uploads have their own limits in 5.21. Response headers beyond these are in 3.2 and SEC-007. Retry-After is sent with 429 and with 503.

### 5.15 Error contract

There is one error model for every route (3.3, Architecture section 9). An htmx request gets an error fragment with the right status. A client that does not accept text/html gets application/problem+json with the members below.

| Member | Type | Present | Meaning |
| --- | --- | --- | --- |
| type | string | Always | urn:trpg:error:CODE |
| title | string | Always | A fixed short title from the message catalogue |
| status | int | Always | The HTTP status |
| code | string | Always | The stable code. A client branches on the code, never on the status or the text |
| detail | string | Always | The catalogue message for the code. It is never exception text and never a copy of what the caller sent (SEC-009) |
| errors | array | 400 only | One entry per failed field, with field (the form field name) and code (a field code, below) |
| details | object | Where the catalogue below lists members | Structured values the screen uses. No prose |
| traceId | string | 5xx, and IDEMPOTENCY\_KEY\_CONFLICT | The id a person quotes in a bug report. 3.3 sets it for 5xx and 3.4 shows it for the conflict |

Field codes: REQUIRED, INVALID\_FORMAT, TOO\_SHORT, TOO\_LONG, OUT\_OF\_RANGE, UNKNOWN\_VALUE, REPEATED, NOT\_UNIQUE, INCORRECT (a current password that is wrong, or an ability method that differs from the rolled set's), NOT\_HELD (a character that is not held, API-CHR-008), TOO\_COMMON (a password on the refused list) and TOO\_LARGE (a body or file above its limit, status 413).

The error fragment of an htmx request is an element with role="alert", data-error-code with the code, data-trace-id where there is one, the catalogue message and the line that says what to do next (4.7). It lands in the nearest #error-region. For a 400 the form comes back with each message beside its field.

```
HTTP/1.1 400
Content-Type: application/problem+json

{"type":"urn:trpg:error:VALIDATION_FAILED","title":"Some fields need another look","status":400,
 "code":"VALIDATION_FAILED","detail":"Check the marked fields and send again.",
 "errors":[{"field":"name","code":"REQUIRED"},{"field":"lightLevel","code":"UNKNOWN_VALUE"}]}

HTTP/1.1 409
Content-Type: application/problem+json

{"type":"urn:trpg:error:ENCOUNTER_VERSION_CONFLICT","title":"This changed while you were looking at it",
 "status":409,"code":"ENCOUNTER_VERSION_CONFLICT","detail":"The table was refreshed. Check it and try again.",
 "details":{"currentVersion":42}}

HTTP/1.1 429
Retry-After: 600
Content-Type: application/problem+json

{"type":"urn:trpg:error:RATE_LIMITED","title":"Too many tries","status":429,"code":"RATE_LIMITED",
 "detail":"Wait a few minutes before trying again.","details":{"retryAfterSeconds":600}}
```

The strings are placeholders. The copy is in 4.7.

**Order of checks.** One request meets the checks in this order, and the first that fails answers: authentication (401; the sign-in and registration forms are checked for their own token instead), CSRF and Origin (403), rate limit (429), membership (404), role (403), form validation (400), replay of an action id (the stored result), version (409), then the rule of the game (422). A replay is answered before the version check, so a retry after a lost response returns the stored result even though the version has moved on.

**Catalogue.** Client says what the caller should do: Edit means the person changes the request. Refetch means load the region again and let the person decide, with no automatic retry. Wait means send the same request after Retry-After. Resend means send the same request with the same id again, which is safe (5.16). None means the answer will not change. Log is INFO for a caller fault (counted by code), WARN where SEC-010 says so, and ERROR for a system fault. A player's response uses the same code with the members that name a DM-only thing left out (SEC-012).

| Code | Status | Fault | Client | details members | Log |
| --- | --- | --- | --- | --- | --- |
| VALIDATION\_FAILED | 400 | Caller | Edit | None; the errors member carries the fields | INFO |
| UNAUTHENTICATED | 401 | Caller | Sign in, then repeat | None. The return address travels in HX-Redirect or Location | WARN on a failed sign-in, INFO otherwise |
| CSRF\_INVALID | 403 | Caller | Reload, then repeat | None | WARN |
| FORBIDDEN | 403 | Caller | None | requiredRole: dm, owner or administrator | WARN |
| NOT\_FOUND | 404 | Caller | None | None | INFO |
| RATE\_LIMITED | 429 | Caller | Wait | retryAfterSeconds | WARN |
| INVITE\_INVALID | 403 | Caller | Edit | None. One code for unknown, expired and used up | INFO |
| ENCOUNTER\_VERSION\_CONFLICT | 409 | Caller | Refetch | currentVersion, DM only (UXQ-19) | INFO |
| CAMPAIGN\_VERSION\_CONFLICT | 409 | Caller | Refetch | currentVersion, DM only (UXQ-19) | INFO |
| IDEMPOTENCY\_KEY\_CONFLICT | 409 | Caller | None; it is a bug | None, with traceId | WARN |
| PACK\_REVIEW\_STALE | 409 | Caller | Refetch | reviewHash of the review now shown | INFO |
| REACTION\_PENDING | 409 | Caller | Answer the window first | windowId, DM only | INFO |
| SESSION\_NOT\_ACTIVE | 409 | Caller | None until the session resumes | sessionState: none, paused or ended | INFO |
| ENCOUNTER\_ACTIVE | 409 | Caller | None until the encounter ends | None | INFO |
| SETTING\_DECISION\_REQUIRED | 409 | Caller | Edit: decide the setting | settingKeys, the keys to decide | INFO |
| ENCOUNTER\_STARTED (new) | 409 | Caller | None | None | INFO |
| MAP\_IN\_USE (new) | 409 | Caller | None | encounterCount | INFO |
| NOT\_YOUR\_TURN | 422 | Caller | None | actingCombatantId | INFO |
| NO\_ACTIONS\_LEFT | 422 | Caller | None | resource: action, bonus-action, reaction, movement or object-interaction | INFO |
| TARGET\_OUT\_OF\_RANGE | 422 | Caller | Edit | reason: out-of-range, not-visible or not-allowed | INFO |
| INVALID\_PATH | 422 | Caller | Edit | reason: blocked, too-costly or not-adjacent | INFO |
| REACTION\_NOT\_AVAILABLE | 422 | Caller | None | combatantId | INFO |
| REACTION\_OPTION\_INVALID | 422 | Caller | Refetch | windowId | INFO |
| REACTION\_NOT\_PERMITTED | 403 | Caller | None | None | WARN |
| SETTING\_INVALID | 422 | Caller | Edit | settingKey | INFO |
| REST\_NOT\_ALLOWED | 422 | Caller | Edit | rule, a key of the rest rules, and characterIds | INFO |
| REST\_TOO\_SHORT | 422 | Caller | Edit | minimumSeconds | INFO |
| HIT\_DICE\_UNAVAILABLE | 422 | Caller | Edit | characterId, available, requested | INFO |
| PACK\_NOT\_ENABLED | 422 | Caller | Edit | packId | INFO |
| CONTENT\_INVALID | 422 | Caller | Edit | problems: up to 20 entries of path and rule, and truncated | INFO |
| ASSET\_INVALID | 422 | Caller | Edit | reason: unsupported-type, too-large, too-many-pixels or undecodable, with maxMegabytes and maxPixelsPerSide for the two limits | INFO |
| PLACEMENT\_INVALID (new) | 422 | Caller | Edit | reason: no-map, outside-map, outside-area, obstacle, occupied, too-few-squares or no-start-area, and combatantId | INFO |
| LAST\_ADMINISTRATOR (new) | 409 | Caller | None | None. Deactivating or demoting the only administrator is refused | INFO |
| SERVICE\_UNAVAILABLE | 503 | System | Resend after Retry-After | None | ERROR |
| INTERNAL\_ERROR | 500 | System | Resend once, then report the traceId | None | ERROR |

The four codes marked new, like PACK\_REVIEW\_STALE, are added to the Architecture error table (7.4). A path value in CONTENT\_INVALID problems is a position in the pack file, never a value from it.

### 5.16 Replays, versions and retries

3.4 and Architecture 5.2 set the rule: the same request sent twice has the effect of one. This section fixes what each key stores and what a second request gets.

| Key | Carried by | Unique per | Stored in | Same id, same body | Same id, other body | Two at the same moment |
| --- | --- | --- | --- | --- | --- | --- |
| E | actionId and expectedVersion | Encounter and action id | combat\_event | The stored result, with every die as first rolled | IDEMPOTENCY\_KEY\_CONFLICT | The unique index lets one through. The other waits, then gets the stored result |
| K | actionId and expectedVersion | Campaign and action id | campaign\_action | The same | The same | The same |
| T | formToken | Session and token (UXQ-7) | A token record, which the Architecture does not have yet (7.4) | The first success response again, with the same Location | IDEMPOTENCY\_KEY\_CONFLICT | One creates. The other gets the stored response |
| N | None | Not applicable | Not applicable | The same end state and a response that shows it | Not applicable | Both responses show the end state |
| A dash in Key | None | Not applicable | Not applicable | A read, or a preview that stores nothing and rolls no die | Not applicable | Not applicable |

**Rules.**

- The request hash is taken over a canonical form: stable field order, normalised numbers, and list order kept where it fixes the order of rolls (Architecture 4.14). The envelope fields are left out of it.
- The stored result belongs to the principal who sent the first request (3.4). Another principal who sends the same id gets IDEMPOTENCY\_KEY\_CONFLICT and nothing from the first result.
- A rejected request stores nothing and does not use up its action id or token (UXQ-3). A second attempt with corrected fields may reuse them.
- A replay of a creation that showed a secret once (API-CMP-009, API-SYS-004) returns the list without the secret and a line that says it was shown once. Only a hash is stored (SEC-016), so the code cannot be shown twice.
- A form token is valid for 24 hours after the form was rendered. A used token is kept for another 24 hours to answer replays, then removed.
- The key and the write are one transaction. Nothing inside it calls another system, so no outbox is needed (Architecture 4.14). The pathfinder is called before the write transaction opens (Architecture 5.1). After the commit the application publishes the changed signal (3.6).
- New records get their ids from the server (UUID version 7). A client never chooses the id of a thing it creates.

**Versions.** A version is an integer that goes up by one for each committed command and never goes down. A checkpoint restore continues upward, so a tab opened before the restore gets a conflict (FR-CHK-002). A command is checked against one version, the one in its Key column. A browser form cannot set an If-Match header, so the version travels in the form. The check and the write are in one transaction, and a conflict stores nothing.

**Retries.** No client retries a command by itself (3.4). The only retry on the server is the pathfinder fallback to the Java search (NFR-REL-001), so there is one retry layer and attempts do not multiply. A person who resends after a timeout sends the same action id, and the stored result makes that safe (NFR-REL-003). A request that has not finished at its deadline (5.21) is cancelled, its transaction rolls back, and it answers SERVICE\_UNAVAILABLE. Nothing is applied, so resending is safe.

### 5.17 Schemas: account, campaign and characters

In 5.17 to 5.20, Request lists the fields of the form object, with path and query values named as such. The envelope fields (5.13) are not repeated. Response says what the success carries. Errors beyond the common ones are in 5.1 to 5.12, and the details of every code are in 5.15. Every limit written here belongs to the set accepted in UXQ-20. A route that is not listed has no fields and shows a page.

| Route | Request | Response |
| --- | --- | --- |
| API-IDN-001, API-IDN-003 | Query: returnTo (text, optional, at most 512 characters, a relative path on this site, SEC-015) | The page. On an empty installation every visit goes to /register (4.4) |
| API-IDN-002 | username (text, required, 1 to 64); password (text, required, 1 to 128); returnTo (text, optional, as above). The sign-in form carries the pre-login token (SEC-006) | 303 to returnTo or /campaigns, with a new session id (SEC-004). A failure is 401 UNAUTHENTICATED with one message for every cause, and the form again with the username kept and the password cleared |
| API-IDN-004 | username (text, required, 3 to 32, letters, digits, dot, dash and underscore, stored lower-case so Anna and anna are one name); password (text, required, 10 to 128, not on the common list, SEC-011); inviteCode (text, required unless this is the first account, 10 characters, SEC-016); formToken | 303 to /campaigns with a new session. The first account becomes the administrator (FR-ACC-003). The invite is checked before the name, so a taken name is never reported to someone without a valid code. Field errors: username NOT\_UNIQUE, password TOO\_SHORT or TOO\_COMMON |
| API-IDN-005 | None | 303 to /login. The session row is deleted and the cookie expired. Sent with no session it answers 401, which the page treats as signed out |
| API-IDN-007 | currentPassword (text, required, 1 to 128); newPassword (text, required, 10 to 128, not on the common list) | Fragment #account-password with sessionsEnded (int, the other sessions removed, SEC-004). A wrong current password is VALIDATION\_FAILED with currentPassword INCORRECT. Every attempt counts toward the 5 per hour in 3.8 |
| API-IDN-008 | Path: sessionId (uuid), a public handle for the session row and never the value in the cookie | Fragment #sessions. Each row has sessionId, device (the browser and system as text, from the User-Agent), createdAt, lastActiveAt and current (flag). Revoking the current session answers as a sign-out |
| API-IDN-009 | shortcuts (flag); theme (choice: system, dark, light) | Fragment #preferences with the saved values. They belong to the person, not to a campaign |
| API-IDN-010 | POST: inviteCode (text, required); formToken | 303 into the campaign as a player. A person who is already a member is taken there and the code keeps its use |
| API-CMP-001 | Query: page and size, only above 100 rows (3.5) | Rows with campaignId, name, role (dm or player) and sessionState (none, active, paused or ended) |
| API-CMP-002 | name (text, required, 1 to 80); formToken | 303, or HX-Redirect from htmx, to \~/setup. The creator becomes DM (FR-CAM-001) |
| API-CMP-003 | None | The home page for the role: recap, session bar, clock and party strip |
| API-CMP-004 | step (int, 1 to 5); on step 1 only, skip (flag, use the defaults); the structural setting of that step under its registry key (Architecture 4.15), one value from the registry's allowed values | The next step, or 303 to \~/players after step 5 or a skip. Each step is stored when it is sent, so leaving halfway keeps the answers. Errors: SETTING\_INVALID |
| API-CMP-005 | None | Fragment #session-bar with sessionId, number (int), state (active) and startedAt (a real-time instant) |
| API-CMP-006 | None. The transition is the last segment of the path | Fragment #session-bar with the new state. An open reaction window refuses it with REACTION\_PENDING |
| API-CMP-007 | amount (int, required, at least 1); unit (choice: minutes, hours, days). The total may not pass 366 days, the cap Architecture 4.14 sets for a rest, used here for the clock too | Fragment #clock with gameTimeSeconds (int, seconds since the campaign's start) and the effects that ended, each with its creature and name, in the order they ended (Architecture 4.14). Out-of-band regions for what an expiry changed |
| API-CMP-008 | None | The page: the join address (text, built from the request's host), localhost (flag, true when other devices cannot reach it, FR-NET-002), the QR code of the address only as inline SVG (the content security policy refuses a data address in an image), the members (username, role, joinedAt), the invites and, from B, fragment #held-characters with the held characters, each with characterId, name and the username it waits for (UXQ-23) |
| API-CMP-009 | maxUses (int, 1 to 20, default 1); expiresInDays (int, 1 to 30, default 7, SEC-016); formToken | Fragment #invites. The new code appears in this response only, as text to read out. Each row has inviteId, createdAt, expiresAt, usesLeft and state (active, expired, used-up or revoked), and never the code or its hash |
| API-CMP-010 | Path: inviteId (uuid) | Fragment #invites. Revoking a revoked invite is a no-op with the same response |
| API-CMP-011 | None | Rows with sceneId, name, light (bright, dim or dark) and active (flag) |
| API-CMP-012 | Create and edit: name (text, required, 1 to 80); notes (text, optional, at most 2000, DM only); light (choice: bright, dim, dark). Activation: none | Fragment #scene-list. One scene is active. Activating another ends the first |
| API-CMP-013 | None | 303 to /campaigns. The campaign is kept and leaves the list (FR-CAM-004) |
| API-CHR-001 | None | Rows follow the audience filter (SEC-012): characterId, name, species, classes (name and level) and, for the owner and the DM, hitPoints (current, maximum) |
| API-CHR-002 | step (choice: species, class, abilities, background, equipment, review); speciesKey (key); classKey (key); abilityMethod (choice, from the methods the DM enabled, FR-CHR-002); abilityScores (six ints, 0 to 30, then checked against the method, for the methods that take scores; for a rolled method they must be the six values of the player's rolled set, each used once, in any order); rollSet (flag, on the abilities step of a rolled method only); backgroundKey (key); skillChoices (key, multi-valued); equipmentChoices (key, multi-valued); name (text, required at review, 1 to 80). Earlier steps travel in hidden fields, and every step validates all steps so far | A step before review returns the next step with the values kept. Review answers 303, or HX-Redirect, to the sheet. Review and a roll are the two sends that use the action id and store anything. A roll (rollSet) makes the server draw six values with the dice engine, 4d6 dropping the lowest or 3d6 according to the method, each die in the log, and keep them as the player's rolled set for this campaign. The step then shows the six values and the player assigns them to the abilities freely (RS-ABL-007, UXQ-21). Asking again while a set exists rolls nothing and shows the same set, so leaving the wizard and coming back gives no new roll. The method is fixed by the set, and another method is VALIDATION\_FAILED with abilityMethod INCORRECT. Review uses the set up. Errors: PACK\_NOT\_ENABLED, CONTENT\_INVALID, SETTING\_DECISION\_REQUIRED |
| API-CHR-003 | None | The sheet. Derived values are computed and not stored (FR-CHR-004): modifiers, proficiency bonus, armor class, initiative, speed, saves and skills. Stored values are current and temporary hit points, Hit Dice spent, resources, slots, conditions and inventory |
| API-CHR-004 | section (choice: details, inventory, spells, resources, and for the DM only, hit-points, conditions, ability-scores); then that section's fields. The owner may send details, inventory, spells and resources (UXQ-20) | Fragment #sheet. A character in an open reaction window is REACTION\_PENDING. While an encounter is active only the DM may send hit-points and conditions, and the rest is ENCOUNTER\_ACTIVE |
| API-CHR-005 | classKey (key, optional, the class that gains the level, required when the character has more than one) | Fragment #sheet |
| API-CHR-006 | step (choice: class, hit-points, ability-scores, review); classKey (key); hitPointMethod (choice: average or roll, from the setting, with no number sent); abilityImprovements (up to two entries, each an ability and an amount) or featKey (key); acknowledgeLoss (flag, required at review when the warning lists points that would be lost, FR-CHR-015) | A step returns the next step. Review applies the level and returns #sheet. The warning step names each ability excess that would be lost |
| API-CHR-007 | optionKey (key, a reaction option of the character); policy (choice: ask, always-pass) | Fragment #reaction-preferences |
| API-CHR-008 | None. The character is in the path | Fragment #held-characters without that character. Only a held character (an import whose username has no account here) can be discarded; any other is VALIDATION\_FAILED with characterId NOT\_HELD, and a held character on the roster of an active encounter is ENCOUNTER\_ACTIVE. Discarding a character already discarded answers as the first time. The campaign log records who discarded it and the username (SEC-021). Keeping a held character needs no request |

### 5.18 Schemas: encounters, reactions, rests and settings

One route is added to 5.4. The DM needs the opportunity-attack list before confirming a move (4.4, FR-ACT-006), and no route in 5.4 gives it.

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-ENC-014 | POST \~/encounters/{encounterId}/moves/previews | DM; P for own creature from B | None | Fragment #move-preview. It stores nothing and rolls nothing. | NOT\_YOUR\_TURN, INVALID\_PATH | A |
| API-ENC-015 | GET \~/encounters/{encounterId}/overrides/new?kind={kind}&combatantId={combatantId}&entryId={entryId} | DM |  | Fragment #override-panel. It stores nothing and rolls nothing. | Common errors only (section 3.3) | A |

| Route | Request | Response |
| --- | --- | --- |
| API-ENC-002 | name (text, required, 1 to 80); mapId (uuid, optional, a ready map of the campaign, 5.19; any other map is VALIDATION\_FAILED with mapId UNKNOWN\_VALUE) | 303, or HX-Redirect, to the builder |
| API-ENC-004 | Exactly one of monsterKey (key) with count (int, 1 to 20, default 1), or characterId (uuid); joinMode (choice from the join options of FR-ENC-012, only after the encounter has started) | Fragment #turn-order and the out-of-band regions for the roster and the map tokens |
| API-ENC-005 | Path: combatantId (uuid) | Fragment #turn-order |
| API-ENC-006 | startAnyway (flag). When a player character has no position and the flag is off, the answer is 200 with #start-warning, which names those characters, and nothing is stored or rolled (UX-035). With the flag on, each unplaced character takes the nearest free eligible square, then initiative is rolled | The combat screen regions with the initiative dice in the log. ENCOUNTER\_STARTED if it has started |
| API-ENC-007 | Exactly one of order (combatantId, multi-valued, the tied creatures in the order the DM chooses) or rollOff (flag) | Fragment #turn-order |
| API-ENC-008 | actorId (uuid, required, must be the creature whose turn it is, else NOT\_YOUR\_TURN) and the fields of the command in the table below | The #result region and the regions the command changed. When a reaction window opens, the reaction panel instead of the rest of the result |
| API-ENC-009 | actorId (uuid, required) | Fragments #turn-order, #acting-panel and #clock |
| API-ENC-010 | kind (choice: set-hit-points, adjust-hit-points, add-condition, remove-condition, set-resource, set-initiative, undo-last); combatantId (uuid, except for undo-last); amount (int, for the hit-point and resource kinds); conditionKey (key); resourceKey (key); entryId (uuid, the log entry corrected or undone, required for undo-last); reason (text, required, 1 to 200, shown to the DM only); visibility (choice: players, quiet; default players). Quiet is VALIDATION\_FAILED with visibility INCORRECT when a player can see the changed value (UXD-11) | Fragment #log with the new entry, which cites the old one, and the region that changed. undo-last on an entry that is no longer the latest is ENCOUNTER\_VERSION\_CONFLICT (UXQ-8). The entry shows its audience in words to the DM (players or quiet), and the stream signal goes to that audience only (UXD-11, SEC-012) |
| API-ENC-011 | None | The page without the combat regions, with the summary: rounds, creatures that fell and resources spent (UXQ-9) |
| API-ENC-012 | Path: region (choice: turn-order, acting-panel, map, log, reaction). For log, query: before (the seq for the DM; an opaque cursor for a player, SEC-012) and limit (int, 1 to 200, default 50) | The one region, filtered by audience (5.18 regions) |
| API-ENC-013 | Query: combatant (uuid, required) | Fragment #map with budgetFeet (int, the movement left) and cells, each a cell with costFeet (int). A player may ask only for their own creature |
| API-ENC-014 | actorId (uuid, required); destination (cell, required). No path is sent. The server finds the path (the browser never asks the pathfinder, API-ENC-013) | Fragment #move-preview with path (the cells in order), costFeet (int), withinMovement (flag), difficultTerrain (flag) and opportunityAttackers (combatantId and name). A player gets the first four only |
| API-RXN-001 | deciderId (uuid, the combatant answering); choice (choice: use, pass); optionKey (key, required when choice is use). One decider for each request | Fragment #reaction, the next step or closed, and the regions the resolved step changed |
| API-RXN-002 | deciderId (uuid); optionKey (key, required; a follow-up has no pass) | As above |
| API-RXN-003 | reason (text, optional, at most 200) | Fragment #reaction closed and a log entry. The dice already rolled stay in the log (Architecture 5.4) |
| API-RST-002 | kind (choice: short, long); durationMinutes (int, 1 to 527040); participantIds (uuid, multi-valued, 1 to 30, no duplicates, Architecture 4.14); spendCount\[characterId\] (int, 0 up to the Hit Dice left) or spendUntil\[characterId\] (int, a hit point target), at most one of the two for each person | Fragment #rest-preview. For each person: hitDiceAvailable (int), spendable (int), arcaneRecovery and songOfRest where they apply (FR-RST-006), and warnings (keys, for example a second long rest inside the interval or a creature at 0 hit points that is not stable) |
| API-RST-003 | The fields of API-RST-002, and confirmWarnings (flag, required when the preview listed warnings) | Fragments #rest-result and #clock. For each person: every Hit Die rolled (as in #result), hitPointsRestored, resourcesRecovered and effectsEnded. Every die is also in the log |
| API-RST-004 | spendCount (int) or spendUntil (int). The character is the principal's own | Fragment #my-rest |
| API-SET-002 | Path: key (a setting key of the registry); value (the type and allowed values of that key) | Fragment #setting-row with key, label, value and whether it is the default. In ask-as-needed mode the panel then sends the held command again, with its own action id, which the refusal did not use (UXQ-3) |
| API-SET-003 | Path: key | Fragment #setting-row back at its default |
| API-SET-004 | mode (choice from the setting modes of FR-SET-003 to FR-SET-007) | Fragment #settings-mode |
| API-ENC-015 | Query: kind (choice, as in API-ENC-010); combatantId (uuid, except for undo-last); entryId (uuid, for a correction of an entry and for undo-last) | Fragment #override-panel: the current value for the DM, quietAllowed (flag, false when a player can see the changed value) and playersText (text, the sentence a player will read, without the new value) |

**Fields of the commands under API-ENC-008** (5.4.1). Dice, results and totals are never fields. The server rolls every die (TM-008).

| Command | Fields besides actorId |
| --- | --- |
| attacks | attackKey (key, one of the attacks the actor has); targetId (uuid) |
| spells | spellKey (key); slotLevel (int, 0 to 9, default the spell's level); targetIds (uuid, multi-valued, at most 30); targetCell (cell, for a spell with an area) |
| moves | destination (cell, required). The server finds the path and the engine checks it again (Architecture 4.3) |
| dashes, dodges, disengages, hides | None |
| helps | targetId (uuid); helpKind (choice: attack, ability-check) |
| readies | trigger (text, required, 1 to 200); actionKind (choice: attack, spell, move, other) |
| object-uses | itemKey (key, optional); note (text, optional, at most 200) |
| grapples | targetId (uuid) |
| shoves | targetId (uuid); shoveKind (choice: prone, push) |
| saving-throws | ability (choice: strength, dexterity, constitution, intelligence, wisdom, charisma); dc (int, 1 to 40); targetIds (uuid, multi-valued, at most 30); reason (text, optional, at most 120) |
| ability-checks | ability (choice as above); skillKey (key, optional); dc (int, 1 to 40, optional); subjectIds (uuid, multi-valued, at most 30) |
| death-saves | None |

In a scene, saving-throws and ability-checks go to \~/scenes/{sceneId}/{command} with the same fields, no actorId, and the campaign's key and version.

**Regions.** A region is the response schema of a fragment. Fields marked DM are left out of a player's region, not hidden in it (SEC-012, UX-010).

| Region | Fields |
| --- | --- |
| #turn-order | round (int); actingCombatantId; entries, each with combatantId, name, kind (character or monster), initiative (int), conditions (keys), hitPoints (current, maximum, temporary; for a monster DM only) |
| #acting-panel | combatantId; economy with action, bonusAction, reaction and objectInteraction (each a used flag) and movementLeftFeet (int); options, each with key, label, available (flag) and unavailableReason (key, shown as text, 4.5) |
| #map | columns, rows, squareSizeFeet; backgroundAssetId; cells with obstacle and difficult flags (unseen cells are left out for a player, FR-VIS-001); tokens, each with combatantId, column, row, size and imageAssetId; the overlays for reachable cells, the path and areas |
| #log | entries, each with entryId, text (from the catalogue), gameTime (seconds) and round, and seq (DM); hasOlder (flag); nextCursor (opaque). A player's entries carry no seq and the region has no count and no gap (SEC-012) |
| #result | action; actorId; outcome (choice: hit, miss, critical-hit, saved, failed-save, success, failure, moved, or the key of another outcome); rolls, each a RollResult (Ruleset 3.10, Result model: terms, total, naturalD20, mode); damage, each with amount, type and applied; hitPointsAfter (DM); conditionsApplied (keys); awaitingReaction (windowId or none). A player sees only the rolls the roll information setting allows (FR-RXN-011) |
| #reaction | windowId; point (key); step (int, 1 to 4); depth (int); deciders, each with combatantId, name, state (waiting, answered or passed) and options (key, label, cost, available). A player sees only their own deciders and options (FR-RXN-006) |

### 5.19 Schemas: content, assets, maps and placement

The accepted questions UXQ-10, UXQ-11, UXQ-13, UXQ-14 and UXQ-15 need routes that 5.8 and 5.9 do not have. They are listed here, in the same form as the other groups. A map is a record of the campaign, with an image, a grid and the squares that block movement. The grid is the model and the image is only a background (4.5). A map has two states: draft, while it is being set up, and ready, once the DM has finished the squares. Only a ready map can be given to an encounter. All of these routes ship in milestone B. They are DM only, except API-PLC-004, where a player may place their own character inside the start area.

| ID | Request | Who | Key | Success | Errors | Rel |
| --- | --- | --- | --- | --- | --- | --- |
| API-MAP-001 | GET \~/maps | DM | None | Fragment #map-list | None beyond the common ones | B |
| API-MAP-002 | POST \~/maps (multipart image) | DM | K | Step 2 of SCR-DM-020: fragment #map-grid, or 303 to \~/maps/{mapId} | ASSET\_INVALID, RATE\_LIMITED, CAMPAIGN\_VERSION\_CONFLICT | B |
| API-MAP-003 | GET \~/maps/{mapId} | DM | None | SCR-DM-020 at the step the map has reached | None beyond the common ones | B |
| API-MAP-004 | POST \~/maps/{mapId}/grid | DM | K | Fragment #map-grid with the columns and rows | VALIDATION\_FAILED, CAMPAIGN\_VERSION\_CONFLICT | B |
| API-MAP-005 | POST \~/maps/{mapId}/detections | DM | N | 200 with fragment #map-editor | None beyond the common ones | B |
| API-MAP-007 | POST \~/maps/{mapId}/squares | DM | K | Fragment #map-editor with the counts | VALIDATION\_FAILED, CAMPAIGN\_VERSION\_CONFLICT | B |
| API-MAP-008 | POST \~/maps/{mapId}/deletions | DM | K | Fragment #map-list | MAP\_IN\_USE, CAMPAIGN\_VERSION\_CONFLICT | B |
| API-ENC-015 | POST \~/encounters/{encounterId}/map-assignments | DM | E | Fragment #placement. Placements already made are cleared | ENCOUNTER\_STARTED, ENCOUNTER\_VERSION\_CONFLICT | B |
| API-PLC-001 | GET \~/encounters/{encounterId}/placement | DM | None | Page SCR-DM-021 | None beyond the common ones | B |
| API-PLC-002 | POST \~/encounters/{encounterId}/placement/groups/{groupId}/choices | DM | E | Fragment #placement | ENCOUNTER\_STARTED, ENCOUNTER\_VERSION\_CONFLICT, PLACEMENT\_INVALID | B |
| API-PLC-003 | POST \~/encounters/{encounterId}/placement/shuffles | DM | E | Fragment #placement with another preview layout | ENCOUNTER\_STARTED, ENCOUNTER\_VERSION\_CONFLICT, PLACEMENT\_INVALID | B |
| API-PLC-004 | POST \~/encounters/{encounterId}/placement/tokens | DM; P for own character inside the start area | E | Fragment #placement, or #map for a player | ENCOUNTER\_STARTED, ENCOUNTER\_VERSION\_CONFLICT, PLACEMENT\_INVALID, FORBIDDEN | B |
| API-PLC-005 | POST \~/encounters/{encounterId}/placement/tokens/{combatantId}/removals | DM | E | Fragment #placement | ENCOUNTER\_STARTED, ENCOUNTER\_VERSION\_CONFLICT | B |
| API-PLC-006 | POST \~/encounters/{encounterId}/placement/start-areas | DM | E | Fragment #placement | ENCOUNTER\_STARTED, ENCOUNTER\_VERSION\_CONFLICT, PLACEMENT\_INVALID | B |

A roster group is the set of creatures one API-ENC-004 request adds for one monster. It has a groupId, which the roster region shows. Each group has one placement choice (UX-027).

| Route | Request | Response |
| --- | --- | --- |
| API-CNT-001 | None | Rows with packId, name, source (built in or imported), enabled (flag), and counts of monsters, spells and items |
| API-CNT-002 | None | Fragment #pack-detail: description, attribution text, counts, and the warnings the pack has now |
| API-CNT-003 | action (choice: enable, upgrade, disable, delete); targetVersion (text, only for upgrade) | Fragment #pack-review: groups (overrides, shadows, same-name, removals), each with key and items (content keys, at most 100, and truncated); reviewHash (string, the hash of the warnings computed now). Nothing is stored. CONTENT\_INVALID for an upgrade whose file fails the schema |
| API-CNT-004, API-CNT-005, API-CNT-006 | reviewHash (string, required); confirmGroups (choice, multi-valued, must hold every group of the review, so each checkbox counts); targetVersion (text, for upgrade) | Fragment #pack-list. If the warnings now differ from the review the person saw, PACK\_REVIEW\_STALE, nothing is applied, and the review is shown again with its new hash |
| API-CNT-007 | reviewHash (string, required); confirmGroups as above; confirmName (text, must equal the pack's name) | Fragment #pack-list |
| API-CNT-008 | file (file, required, parsed in memory, SEC-014). The format, size and entry limits are the Content Schema's; formToken | Fragment #pack-review for the new pack, in the same form as API-CNT-003. The pack stays disabled until it is enabled |
| API-CNT-009 | Path: type (choice: monsters, spells, items, conditions, and the other content types of the Content Schema); contentKey for one entry. Query: q (text, 1 to 100); source (a pack key); page (int, from 1); size (int, 1 to 100, default 25); and the filters of the type: level and school for spells, crMin and crMax for monsters (DM only), category for items | Rows with contentKey, name, source label (4.10) and a one-line summary, with total (int), page and size. One entry: the stat block, spell or item with its source label. A player asking for monsters gets NOT\_FOUND |
| API-AST-001 | Path: contentKey (a monster). file (file, required, PNG, JPEG or WebP, SEC-013) | Fragment #monster-image: assetId, widthPx, heightPx. The image replaces the one the monster had and serves every copy of it (UX-032). The same bytes sent twice give the same assetId |
| API-AST-002 | Path: contentKey | Fragment #monster-image showing the placeholder silhouette |
| API-AST-003 | Path: assetId (the SHA-256 of the stored bytes in lower-case hex, 64 characters, the name SEC-013 gives the file). This is the one id in a path that is not a UUID (7.4) | The bytes, as 5.14 says. A player gets a monster image only where the audience allows, and otherwise NOT\_FOUND |
| API-MAP-002 | name (text, required, 1 to 80); file (file, required, PNG, JPEG or WebP, at most 20 MB, UXQ-10) | Step 2: mapId, state (draft), backgroundAssetId, widthPx, heightPx. The header dimensions are read before the image is decoded, so an oversized image is refused without being loaded (5.21) |
| API-MAP-004 | squareSizePx (int, required, 8 to 512); offsetX (int, 0 up to squareSizePx minus 1); offsetY (int, as offsetX); squareSizeFeet (int, 1 to 100, default 5); name (text, optional, renames the map) | Fragment #map-grid with columns and rows. The browser draws the grid from the image size and these numbers, and the server computes them again. More than 200 columns or 200 rows is VALIDATION\_FAILED with squareSizePx OUT\_OF\_RANGE (the 200 by 200 limit, FR-GRD-001) |
| API-MAP-005 | None. The saved grid is used | Fragment #map-editor with suggestedCount (int) and the suggested squares marked. Detection runs inside this request. It is limited to 30 seconds, and the instance runs at most 3 detections at once (a fourth request answers RATE\_LIMITED with Retry-After). A run that is not finished by then, or an image that cannot be read, ends with failureReason (choice: timeout, unreadable) in the fragment and the answer is still 200, because a failure never blocks saving (UX-023). Nothing is stored until the run ends, so cancelling the request (the Cancel that a wait over 10 seconds offers) leaves the map as it was. Sending it again replaces the earlier suggestions with a new run |
| API-MAP-007 | obstacle, difficult, erase (text, each multi-valued, a square as column,row, at most 40000 values in all); acceptSuggestions (flag); clearSuggestions (flag); finish (flag) | Fragment #map-editor with suggestedCount, obstacleCount and difficultCount. Erase is applied first, then obstacle and difficult, then the two flags. With finish the map becomes ready and each suggestion still standing becomes an obstacle marked detected. A square the DM marked is marked confirmed (7.4, Grid and Pathfinder Specification) |
| API-MAP-008 | None | Fragment #map-list. The image file is removed when nothing else uses it |
| API-ENC-015 | mapId (uuid, a ready map of the campaign; empty removes the map) | Fragment #placement with placementsCleared (int) |
| API-PLC-001 | None | The page: the map, eligibleCount (int), the groups with their choice, the start area, the preview layout, and for each player character its state (placed by the DM, placed by the player, or not placed) |
| API-PLC-002 | Path: groupId (uuid). choice (choice: anywhere-eligible, inside-area, by-hand, default anywhere-eligible); area (area, required for inside-area) | Fragment #placement. For inside-area it carries areaEligibleCount (int) and fits (flag, false when the area has fewer eligible squares than creatures, UX-029) |
| API-PLC-003 | None | Fragment #placement with a new random layout for every random group. The positions are stored as a preview and bind nothing until the encounter starts (UX-031). The draws come from the engine's random source, not from the web layer, and the layout is written to the log when the encounter starts, so a replay places the same squares |
| API-PLC-004 | combatantId (uuid, required); cell (cell, required). A creature larger than Medium is placed by its anchor square, and every square of its footprint must be eligible | Fragment #placement. A DM may place any creature or character. A player may place only their own character, inside the start area, and not one the DM placed (UX-033, UX-034) |
| API-PLC-005 | Path: combatantId (uuid) | Fragment #placement. The creature is off the map and no longer takes part in random placement |
| API-PLC-006 | area (area, required), or clear (flag) | Fragment #placement with the start area and the list of characters who have not placed |

Starting an encounter (API-ENC-006) turns the preview into the positions. A random group whose area has too few eligible squares refuses the start with PLACEMENT\_INVALID and too-few-squares. An encounter with no map is placed with no positions, as in version 1.

**Detection, step by step.** The DM uploads (API-MAP-002) and sets the grid (API-MAP-004). The DM asks for detection (API-MAP-005), and the answer is the map editor with the suggestions marked. The work runs on the server in Java, inside that request, and does not use the pathfinder (UXQ-11, NFR-REL-001). It runs outside the database transaction (section 7), and only its result is written. There is no queue, no background task and no status route, so API-MAP-006 is not used and its number is kept so the other route ids stay as they are. The DM edits and finishes (API-MAP-007). A map that was uploaded and not finished after 7 days is removed with its image. The removal runs when the application starts and when the map list is read, so no scheduler is needed.

**What the image shows.** The background image is served whole to every member of an encounter that uses the map. A part of the map that players should not see is not hidden by this version, because that needs a vision model beyond full (FR-VIS-001). SCR-DM-020 says so in words (UXQ-22).

### 5.20 Schemas: logs, stream, system and the version 1.5 and version 2 groups

| Route | Request | Response |
| --- | --- | --- |
| API-LOG-001 | Query: area (choice: events, journal, checkpoints, default events, UXQ-18); before (the seq for the DM; an opaque cursor for a player, SEC-012); limit (int, 1 to 200, default 50); type (choice, multi-valued, optional, an event type) | The page. Entries have entryId, type, text (from the catalogue), gameTime (seconds), actor (a username, where the person may see it) and seq (DM). Also hasOlder (flag) and nextCursor. The older-rows control is a link to the same route with before set, and works without scripting (3.5) |
| API-LOG-002 | None. The role comes from the session, and there is no audience field (3.6) | text/event-stream. See the events below |
| API-SYS-001 | None | 200 {"status":"UP"} or 503 {"status":"DOWN"}. The database check has a 2 second timeout. No version, no host and no detail |
| API-SYS-002 | Query: q (text, 1 to 64, a username prefix); page (int, from 1); size (int, 1 to 100, default 25) | Rows with userId, username, active (flag), administrator (flag), createdAt and lastSignInAt; total (int) |
| API-SYS-003 | Path: userId (uuid) | Fragment #user-row with active false. The person's sessions end (SEC-004). Deactivating the last active administrator is refused with LAST\_ADMINISTRATOR |
| API-SYS-004 | Path: userId (uuid); formToken. The key is T, not N, because each reset issues a new password (5.16) | Fragment #user-row with temporaryPassword (text, 16 characters from the alphabet SEC-016 uses, shown once) and mustChangePassword (flag, UXQ-6). Cache-Control: no-store. The person's sessions end |
| API-SYS-005 | Query: details (flag, default off) | The page: appVersion, database (up or down), pathfinder (off, up, or down and using the fallback), storage (assetBytes, databaseBytes, volumeFreeBytes). With details on, also the runtime and build values for a bug report. No secret and no path is shown (SEC-009) |
| API-JRN-001 | GET: query area, before and limit as in API-LOG-001, and kind (choice: entry, quest) and status (choice: open, done, for quests). POST entries and edits: kind (choice: entry, quest); title (text, required, 1 to 120); body (text, at most 20000, shown as text); gameTime (int, seconds, default the campaign clock); audience (choice: dm-only, all-players, named-players); playerIds (uuid, multi-valued, required for named-players); status (choice: open, done, for quests) | Fragment #journal-list. Each entry has entryId, kind, title, body, gameTime, audience and createdAt. A player gets the entries whose audience includes them, without the audience field |
| API-CHK-001 | GET: none. POST \~/checkpoints: label (text, optional, 1 to 80). POST restorations: confirmName (text, required, must equal the campaign's name) | The list: checkpointId, label, reason (choice: before-encounter, session-end, manual, before-restore), gameTime, createdAt and sizeBytes. A restore first saves a checkpoint of the current state (UXQ-16), replaces the state in one transaction, logs it, and answers 303 to \~ with the id of the checkpoint it made. Versions continue upward (5.16). The oldest checkpoints beyond the limit of FR-CHK-001 are removed |
| API-EXP-001 | None | A file download (5.14). The name is the campaign name reduced to letters, digits and dashes, and the date. It holds the campaign's access data: each member by username with the role, each character with the username of its owner, and each invite with its state, expiry and uses left (UXQ-23). It holds no password hash, session, account id or invite code (SEC-022). The file names people, so the screen that starts the export says to keep it private. The export is entered in the campaign log with the person and the time (SEC-021) |
| API-EXP-002 | file (file, required, parsed in memory, SEC-014); name (text, optional, 1 to 80, default the exported name); formToken | 303 to \~/players of the new campaign, which the importer owns as DM. Each member in the file is matched by username to an account on this instance. A match joins with the role the file gives, except that a file's DM who is not the importer joins as a player, and takes the characters the file assigns to that username. A username with no account here is listed on the players page as account missing, with its characters. Those characters are held: they stay in the campaign, the DM can still edit them, and they take no part in a start, a rest or the unplaced warning, so a session goes ahead without them. When someone joins the campaign with that username, they are given the characters. The DM can discard a held character (API-CHR-008). Invites come back as a record of their state and uses left and cannot be redeemed, because no code is stored (SEC-022). Ids are generated again, so none from the file is kept (UXQ-23). Errors: CONTENT\_INVALID with the rule format-version, size or schema |
| API-LIV-001 | POST proposals: command (choice: the commands of 5.4.1 that a player may propose) with that command's fields, for the player's own creature. POST decisions: decision (choice: approve, reject); note (text, optional, at most 200) | Proposals: fragment #my-proposal with state (pending). Decisions: on approve the server runs the stored command as the creature's player would have, under the DM's action id, and answers as API-ENC-008. On reject the proposal closes with the note |

**Stream events (B).** Each event is a text block with an event name and one line of JSON data. A comment line starting with a colon is sent every 15 seconds (3.6). The retry delay is 3000 ms. No event carries game data.

| Event | Data for the DM | Data for a player |
| --- | --- | --- |
| state | encounterId (uuid or null), encounterVersion (int or null), campaignVersion (int) | encounterId (uuid or null) |
| changed | scope (choice: encounter, campaign, scene, reaction), id (uuid), version (int) | scope and id, with no version (UXQ-19) |

On state the client fetches every region of the open screen once. On changed it fetches the regions of the scope (5.10) with an ordinary GET, and the fragment route filters again (3.6). The browser's EventSource cannot read a status code. After a failed reconnect the script makes one ordinary GET of the page, and a 401 there takes the person to sign-in (3.2). An open stream is closed by the server when the session ends or the member is removed (SEC-018).

### 5.21 Paging, limits and how the contract changes

**Paging.** 3.5 chooses the style for each list. These are the parameters.

| List | Style | Parameters | Order | Response members |
| --- | --- | --- | --- | --- |
| Combat log, campaign event log, journal | Cursor | before (opaque to a player, the seq for the DM) and limit (1 to 200, default 50) | Newest first, on the seq, which is unique, so rows never repeat or skip while new ones arrive | hasOlder, nextCursor. No total, because a count tells a player how many hidden rows there are (SEC-012) |
| Content browsers, user list | Offset | page (from 1) and size (1 to 100, default 25), with q and the filters of the route | The name, then the key, so the order is total and stable | total, page, size |
| Characters, encounters, packs, scenes, maps, invites, checkpoints, sessions | None up to 100 rows. Above that, offset as above | As above | Name, then creation time | As above, only when paged |

A list route never returns more than 100 rows when it takes no paging parameters. Past that it pages. A cursor or page value that does not parse, or is out of range, is VALIDATION\_FAILED. A search matches the start of the name and the start of each word, through an index, and not a substring scan of the table. A page is built with a fixed number of queries, one for the rows and one for each related set, never one for each row.

**Limits.** These are the set accepted in UXQ-20; a source is named where a requirement or a control fixes the value. A body above its limit is refused with 413, VALIDATION\_FAILED and a TOO\_LARGE field error that names the limit (3.3).

| Item | Limit | Source |
| --- | --- | --- |
| Form body | 64 KB | UXQ-20 |
| Map image | 20 MB; each side at most 8192 pixels and at most 40 million pixels in all; route body limit 21 MB | UXQ-10, UXQ-20 |
| Monster image | 5 MB; each side at most 4096 pixels | SEC-013 |
| Pack file | The Content Schema's size, entry, depth and value caps | SEC-014 |
| Import file | 256 MB; at most 5000 entries; read as a stream, and no entry is written under a name taken from the file | UXQ-20, SEC-014 |
| Map size | 200 by 200 squares | FR-GRD-001 |
| Participants in a rest | 30 | Architecture 4.14 |
| Targets in one command | 30 | UXQ-20 |
| Creatures added in one request | 20 | UXQ-20 |
| Squares in one save of the map editor (the body of this route is limited to 1 MB) | 40000 | Follows from 200 by 200. 40000 values of up to 19 characters each is about 760 KB |
| Text fields | The lengths in 5.17 to 5.20 | UXQ-20 |

**Rate limits this part adds to 3.8**. Map and monster image uploads share the image upload counter, 20 an hour for each user. A pack upload and an import share the pack counter, 5 an hour for each user. Starting a detection is 30 an hour for each user, and the instance runs at most 3 detections at once.

**Deadlines**. A read or a command that has not finished after 10 seconds is cancelled and answers SERVICE\_UNAVAILABLE, and its transaction rolls back. An upload, an import, an export and a detection request have 60 seconds. The database health check has 2 seconds. The pathfinder call has the timeout and the fallback in the Architecture (section 7), so a slow pathfinder never turns into an error (NFR-REL-001). The targets themselves are the Requirements': NFR-PERF-001 for resolving an action and NFR-PERF-002 for the pathfinder. This part adds none.

**Uploads.** The browser sends the file as multipart/form-data with the envelope fields. The server reads the first bytes to decide the type, then the header dimensions, and only then decodes. It re-encodes into a new file, drops all metadata, names the file by the hash of the new bytes, and stores it outside the web root (SEC-013). An animated image is refused. The same bytes uploaded twice give one file.

**Versioning and compatibility.** There is no version segment in the path in version 1 (UXD-2). The contract is the set of routes, form field names, error codes, region ids and event names in this part. The browser always gets its pages and forms from the same build, so they change together. What must stay compatible is narrower: a page that was open in a browser while the application restarted or was updated, the tests, the export and checkpoint files, and a JSON API if one is added later.

- Compatible changes: a new route, a new optional field, a new member in a region or in details, a new event name, a new field code, and a new error code for a condition that had none. A client that does not know a code shows the message for its status.
- Breaking changes: removing or renaming a route, a field, a region id, an event name or a code; changing the status of an existing condition; making an optional field required; changing what a field means; changing the paging style. A breaking change needs the owner's acceptance, a note in this part and a new document version. Where a form can still be open in a browser, the old field names are accepted for one release.
- A form sent to a route that no longer exists answers NOT\_FOUND, with a Reload action.
- Codes are never reworded and never reused for another meaning.
- An export and a checkpoint carry a formatVersion (integer). An import reads the same version and older ones through a migration. A newer one is refused with CONTENT\_INVALID and the rule format-version (FR-EXP-001).
- If a JSON API is added, it goes under /api/v1 (3.1), has an OpenAPI description generated from the form objects, uses this error model, and carries the action id in an Idempotency-Key header. Deprecation and Sunset headers announce the end of a version.

**Contract tests.** Each route in 5.1 to 5.20 has a test of its status, its headers and its region ids. One test pins the error model: every code in 5.15 produces the members the table gives and nothing more. One test sends each key's replay twice and a different body once. One test posts an upload with a wrong type, an oversized body and an oversized image. The test numbers are in 7.2.

## 6. Part D: threat model

This part asks what could go wrong on a game-night LAN, who would do it, and which control stops it. The controls SEC-001 to SEC-011 are in section 3.7. The rest are defined in 6.4. Every threat has a test in section 7.

### 6.1 Assets

| Asset | Why it matters | What is at stake |
| --- | --- | --- |
| DM-only information: monster hit points and armor class, hidden stats, DM notes, secret rolls, DM-only events, the obstacle data of squares a player has not seen (the map image itself is shown whole, 6.8) | A DM runs the game on the players not knowing it | Confidentiality |
| Game state and the log: hit points, resources, dice, the order of events | The table trusts the numbers | Integrity. Dice are rolled on the server only. |
| Accounts, password hashes, sessions | Access to everything a person may do | Confidentiality, integrity |
| Content packs and homebrew | A DM's own work, and an input the engine must survive | Integrity, availability |
| Uploaded images | Map and monster images: files from users, decoded by the server, which costs processor time and memory | Integrity, availability |
| Exports and checkpoints (B) | They contain a whole campaign, DM secrets included, and the usernames of its members | Confidentiality |
| The table's evening | The application must work during a session | Availability |
| Access data: members, roles, character owners, held characters | They decide who sees and does what, and who owns a character | Confidentiality, integrity |
| Rolled sets | A player's ability scores come from one, and the table trusts that it was rolled fairly | Integrity |

### 6.2 Actors

| Actor | Capability | Motive in this model |
| --- | --- | --- |
| Player | Signed in, member of a campaign, a browser with developer tools | Read DM-only data, change their own character, act out of turn, cheat on a roll |
| Other person on the network | Can reach the address, has no account | Read or alter traffic, guess codes and passwords |
| Pack or image author | Their file is imported by a DM | Attack the importer: exploit the parser, plant markup |
| Administrator | Runs the instance | Read campaigns they are not in (also the person who can read the database) |
| DM by mistake | Trusted | An action that harms the table without malice |
| Remote attacker | Only if someone exposes the port to the internet | Out of scope by default. The deployment guide says not to expose the application without HTTPS and a reverse proxy. |
| Importer | Any signed-in person, with a campaign file they made or were given (B) | Learn which usernames have an account, put people into a campaign they did not choose, or attack the parser |

### 6.3 Trust boundaries

The browser and the network are untrusted. The application trusts PostgreSQL and the pathfinder, which sit on the compose network. A pack file and an uploaded image are untrusted input from a trusted person, and an import file is untrusted input from any signed-in person, handled like a pack file. One player's browser is untrusted from another's, and the audience filter is the boundary between them. A diagram follows.

&#91;embedded content: trust boundaries · 3 zones, 2 untrusted file inputs\]

SEC-012 guards the line between one browser and another. SEC-013 and SEC-014 guard the file inputs, SEC-024 bounds the work they cause, and SEC-025 guards what an import file may say about people.

### 6.4 Controls defined here

| ID | Control |
| --- | --- |
| SEC-012 | **Audience filtering.** The server builds separate view models for the DM and for players, and a player model is an allowlist (`PlayerViewFilter`). A player response never holds DM data that is then hidden (UX-010). The reaction prompt is built the same way and never carries hidden information (FR-RXN-006). A player's log pages show no `seq` value, no count of rows and no gap: their cursor is the last visible row. A signal that carries nothing a player may know goes to no player (3.6). |
| SEC-013 | **Images.** Accept PNG, JPEG and WebP, decided by the file's leading bytes and not by its name or declared type. Reject anything else, SVG included. A monster image is at most 5 MB and 4096 pixels on a side. A map image is at most 20 MB, 8192 pixels on a side and 40 million pixels in all. The header dimensions are read before any decode, so an oversized image is refused without being loaded, and an animated image is refused. Decode, then re-encode to a new file, which drops metadata (FR-AST-001). Name the file by the hash of the re-encoded bytes, and ignore the uploaded name. Store it on the data volume, outside the web root. Serve it only through API-AST-003, with `nosniff`, a fixed content type and the audience check. |
| SEC-014 | **Pack content is data.** Pack text is rendered as text, never as markup. The pack file is parsed in memory and no entry is written to the file system under a name taken from the file. The size, entry count, nesting depth and value caps are the Content Schema's. Only the DM can load, enable, upgrade, disable or delete a pack (CS-HBR-003, SEC-017). An import file (API-EXP-002) is handled the same way: read as a stream, at most 256 MB and 5000 entries, checked against the schema before anything is written, every id regenerated, and no entry written under a name taken from the file. |
| SEC-015 | **Injection and redirects.** Queries use bound parameters only. Thymeleaf's default escaping is on and `th:utext` is not used for user or pack text, and a build check fails on it. The sign-in return address must be a relative path on this site, so an absolute address or one starting `//` is dropped. |
| SEC-016 | **Invite codes.** A code has 10 characters from an alphabet without look-alike letters (about 50 bits). It is single use or has a use limit, and it expires (default 7 days). Only a keyed hash (HMAC with an instance secret) is stored, and the code is shown once. Redemption is rate limited (3.8) and compared in constant time. |
| SEC-017 | **Role checks.** The route group decides the role in the controller (3.9), and the service checks the role again for the command and the ownership of the creature. A player acting for a creature they do not control gets REACTION\_NOT\_PERMITTED or FORBIDDEN. A missing rule denies. |
| SEC-018 | **Streams (B).** Membership is checked on every connect and on every heartbeat, a removed member is disconnected, each user has a stream cap, and an event holds no game data. |
| SEC-019 | **Deployment.** The database and the pathfinder are not published outside the compose network. Secrets come from the environment and not from the image. The application runs as a non-root user. On a first run over plain HTTP the DM sees a notice: "Anyone on this network can read what is sent. Use HTTPS if guests share the network." `APP_COOKIE_SECURE` and HSTS apply when HTTPS is used. |
| SEC-020 | **Supply chain.** Versions are pinned and htmx is served from the application with its checksum recorded. CI scans the dependencies and fails on a critical finding. The page loads nothing from another origin (SEC-007). |
| SEC-021 | **Audit.** Security events are logged and counted (SEC-010). Administrator actions (deactivate, password reset) and DM actions that change rules or content are in the event log with the person and the time. A campaign export, an import and the discard of a held character are logged too, with the person and, for a discard, the username. |
| SEC-022 | **Data minimisation.** The application stores a username and a password hash, and no email address. Exports hold no password hash, no session and no invite code. An export holds the usernames of the campaign's members, and it says that it contains DM-only information and names people. |
| SEC-023 | **Pathfinder boundary.** The application sends the pathfinder only grid data it built itself, with a timeout and a size limit, and checks the answer (adjacency, cost, bounds) before it uses it. No address from a request is ever fetched. |
| SEC-024 | Bounded work. Work that one request can start has a limit. An upload is read in the order type, header dimensions, decode, with the pixel limits of SEC-013, and an import is read as a stream with the limits of SEC-014. Obstacle detection stops after 30 seconds, the instance runs at most 3 at once, and a user starts 30 an hour (3.8). A read or a command has a deadline of 10 seconds, and an upload, import, export or detection request has 60 seconds (5.21). A request past its deadline is cancelled and its transaction rolls back. |
| SEC-025 | Import and held characters. An import never trusts what the file says about people. The importer becomes the DM of the new campaign, whatever the file says. Members are matched by exact username to accounts on this instance. A role from the file applies only to a matched member, and it never makes anyone but the importer a DM. A username with no account is held, and its characters take no part in play. They pass to a person only when that person joins with that username, and the DM can discard them (API-CHR-008). Every invite in the file comes back as a record that cannot be redeemed. An import is limited by the counter in 3.8. |
| SEC-026 | Server-owned results. The server draws every die, including the six values of a rolled ability set, and keeps the set as a record that a request can read or use up but never write. A player cannot roll again while a set is unused, and the scores sent with a character are checked against the set (RS-ABL-007, UXQ-21). |

### 6.5 Threats

| ID | Threat | Actor | Class | Controls | Test |
| --- | --- | --- | --- | --- | --- |
| TM-001 | A session cookie is read from plain-HTTP traffic and reused | Network | Spoofing | SEC-004, SEC-005, SEC-019. Residual risk: on HTTP it is possible. | T-126, T-127 |
| TM-002 | Passwords are guessed or stuffed at the sign-in form | Network | Spoofing | SEC-008, SEC-009, SEC-011 | T-128, T-129 |
| TM-003 | A page the DM opens makes the browser post a command | Network | Tampering | SEC-005 (`SameSite=Lax`), SEC-006 | T-125 |
| TM-004 | A player or outsider changes an id in the URL to reach another campaign, character or encounter | Player, network | Disclosure | SEC-001, SEC-002, SEC-003 | T-121, T-123 |
| TM-005 | A player calls a DM-only route (a command, a pack, a setting, a map, a discard) directly | Player | Elevation | SEC-017, section 3.9 | T-122 |
| TM-006 | A player reads DM-only data in a response, in the markup, in a reaction prompt or in an error | Player | Disclosure | SEC-012, SEC-009 | T-130, T-133 |
| TM-007 | A player infers hidden events from log cursors, gaps, counts or stream timing | Player | Disclosure | SEC-012, SEC-018 | T-131, T-132 |
| TM-008 | A request is replayed or altered to roll again, or a client sends its own roll | Player | Tampering | Architecture 5.2, section 3.4. The server rolls every die and no field carries a result. | T-139 |
| TM-009 | Extra form fields (`role`, `ownerId`, `hp`) are bound to a record | Player | Tampering, elevation | SEC-003 (form objects only) | T-124 |
| TM-010 | Script is stored in a name, note, pack text or journal entry and runs for others | Player, pack author | Elevation | SEC-007, SEC-014, SEC-015 | T-127, T-134 |
| TM-011 | A pack or an import file is oversized, deeply nested, names files outside its folder, or breaks a rule cap | Pack author | Denial, tampering | SEC-014, SEC-017 | T-136, T-152, T-173 |
| TM-012 | An image is a disguised file, an SVG with script, a decompression bomb or carries location data | Image author | Tampering, denial | SEC-013, SEC-024, SEC-008 | T-135 |
| TM-013 | Invite codes are guessed, reused or read from the database | Network | Spoofing | SEC-016, SEC-008 | T-137 |
| TM-014 | The administrator's password reset is weaker than sign-in and leaves a long-lived secret | Administrator, network | Spoofing | The temporary password is shown once, must be changed at next sign-in (a must-change flag, UXQ-6), and the reset is logged (SEC-021). | T-126 |
| TM-015 | A session survives sign-out or a password change, or a session id is fixed before sign-in | Network | Spoofing | SEC-004 | T-126 |
| TM-016 | The DM console is framed by another site to trick a click | Network | Tampering | SEC-007 (`frame-ancestors 'none'`) | T-127 |
| TM-017 | The sign-in return address sends the person to another site | Network | Spoofing | SEC-015 | T-138 |
| TM-018 | One client floods streams, uploads or requests and the table stalls | Player, network | Denial | SEC-008, a request size cap, SEC-018, SEC-024. Residual risk: the counters live in memory and reset on restart. | T-129 |
| TM-019 | The pathfinder returns a bad path or is replaced | Network | Tampering | SEC-019, SEC-023 | T-140 |
| TM-020 | Logs, exports or backups hold passwords, codes or secrets | Any | Disclosure | SEC-010, SEC-022 | T-142 |
| TM-021 | The administrator reads campaigns they are not in | Administrator | Disclosure | Section 3.9 gives the role no campaign access. Residual risk: whoever runs the server can read the database. | T-122 |
| TM-022 | A poisoned dependency or script replaces htmx or a library | Supply chain | Tampering | SEC-020, SEC-007 | T-158 |
| TM-023 | A player answers a reaction for another player's creature | Player | Elevation | SEC-017 | T-122 |
| TM-024 | A removed member keeps reading through an open stream | Player | Disclosure | SEC-018 | T-132 |
| TM-025 | A shared tablet shows another person's page from history or cache | Any | Disclosure | `no-store`, htmx history cache off (3.2) | T-127 |
| TM-026 | An import file names usernames, so the importer learns which accounts exist here, or puts other people into a campaign they did not choose | Importer | Disclosure | SEC-025, SEC-008 (the shared counter in 3.8), SEC-021. Residual risk: accepted in 6.8. | T-173 |
| TM-027 | A person joins under the username of a held character to take it | Player | Elevation | SEC-016 (joining needs an invite from the DM), SEC-025, SEC-021 (the log records the handover). Residual risk: accepted in 6.8. | T-171 |
| TM-028 | Repeated or very large map uploads and detection runs use the processor and memory the table needs | DM, player, network | Denial | SEC-024, SEC-013, SEC-008. Residual risk: accepted in 6.8. | T-172 |
| TM-029 | A player rolls ability scores again until they are good, or sends scores that were not rolled | Player | Tampering | SEC-026, SEC-003, section 3.4 | T-170 |
| TM-030 | A player reads the whole map image, or the obstacle data of squares their character has not seen | Player | Disclosure | SEC-012 (flags of unseen squares are left out of #map) and a plain statement on SCR-DM-020. Residual risk: the image itself is shown whole (UXQ-22), accepted in 6.8. | T-174 |
| TM-031 | A player places a token for another person, outside the start area or after the encounter started, or changes a map | Player | Tampering, elevation | SEC-017, SEC-003 | T-122, T-167 |
| TM-031 | A DM correction shows a player a value or a reason they may not know, or changes something they can see with no trace, or a quiet correction is guessed from a signal | Player | Disclosure | UXD-11: the server sets the audience, a player's entry holds only the standard sentence and a new value that player can already see, the old value and the reason stay with the DM, and quiet is refused when a player can see the value. SEC-012 covers the signal and the log cursor. | T-175 |

### 6.6 Controls by route group

| Route group | Controls that apply |
| --- | --- |
| Sign-in, registration, join, account | SEC-005, SEC-006, SEC-008, SEC-009, SEC-011, SEC-015, SEC-016 |
| Campaign pages and fragments | SEC-001, SEC-002, SEC-003, SEC-012 |
| Commands in play (ENC, RXN, RST, SET, CHR, CMP) | SEC-001 to SEC-003, SEC-006, SEC-017, SEC-026 and the idempotency rules in 3.4 |
| Content packs (CNT) | SEC-006, SEC-008, SEC-014, SEC-017, SEC-024 |
| Images (AST) | SEC-006, SEC-008, SEC-013, SEC-017, SEC-024 |
| Streams (LOG-002) | SEC-002, SEC-008, SEC-012, SEC-018 |
| Administration | SEC-001, SEC-017, SEC-021 |
| Health | No data beyond up or down |
| Maps and placement (MAP, PLC, B) | SEC-001 to SEC-003, SEC-006, SEC-008, SEC-012, SEC-013, SEC-017, SEC-024 |
| Export, import, checkpoints, journal (B) | SEC-006, SEC-008, SEC-014, SEC-017, SEC-021, SEC-022, SEC-024, SEC-025 |

### 6.7 OWASP Top 10 check

| Item | Where it is met |
| --- | --- |
| A01 Broken access control | SEC-001 to SEC-003, SEC-017, section 3.9 |
| A02 Cryptographic failures | SEC-011, SEC-016, SEC-019 (HTTPS optional, warning shown) |
| A03 Injection | SEC-015, SEC-014 |
| A04 Insecure design | This part, the audience filter as a design rule (SEC-012), bounded work (SEC-024) and server-owned results (SEC-026) |
| A05 Security misconfiguration | SEC-007, SEC-009, SEC-019 |
| A06 Vulnerable components | SEC-020 |
| A07 Authentication failures | SEC-004, SEC-008, SEC-011 |
| A08 Integrity failures | SEC-020, SEC-014, SEC-013, SEC-025 |
| A09 Logging and monitoring failures | SEC-010, SEC-021 |
| A10 Server-side request forgery | SEC-023. The application fetches no address from a request. |

### 6.8 Residual risk

- **Plain HTTP on the LAN** is the default (FR-NET-001). Anyone who can capture traffic can read it and take a session. The warning and the HTTPS option reduce the risk; they do not remove it.
- **Counters in memory.** Rate limits and streams reset on restart. A restart is rare during play and the LAN is small.
- **Version counters in player forms.** A form sent to a player carries the version it was rendered with (3.4). The version rises with every committed command, including ones the player cannot see, so a player who reads the page source can tell that something changed. UXQ-19 keeps the version out of stream events and conflict answers for players and does not remove this. The risk is accepted, because it shows that a change happened and never what it was.
- **The person who runs the server** can read the database. This is a self-hosted tool, and the model does not defend a DM's secrets from its own host.
- **No email recovery.** A lost password needs the administrator. If the administrator is the lost account, recovery is a command-line step in the deployment guide (UXQ-6).
- **A DM sees everything**, including a player's private notes if any are ever added. Player-private notes are not in scope.
- The map image shows the whole map. A player sees every part of the image, including areas their character has not reached, because a vision model that crops it is a later change (UXQ-22). SCR-DM-020 says so to the DM in words. The obstacle flags of squares a player may not see stay out of the player's map region.
- An import tells the importer which usernames exist. A file that names a username shows whether an account with that name is here, and a matched person becomes a member of the new campaign without being asked. Any signed-in person may import, and imports share the counter of 5 an hour (3.8). The owner chose username matching so that an import has nothing to assume (UXQ-23). The risk is accepted: everyone with an account joined through a DM's invite, a member sees only what a player sees, and the information leaked is that a username exists.
- A held character goes to whoever joins with its username. Joining needs an invite from the DM, so the DM chose the person, but a guest invited for another seat could register under the username of a missing player. The log records the handover and the DM can edit the character. The risk is accepted for a trusted table.
- Detection and large uploads run inside the request, on the server that runs the game. The limits in SEC-024 bound this and do not remove it: while a detection runs, the table can be slower. The DM chooses when to run it.
- A rolled set is kept for the campaign until a character uses it. An abandoned set stays and gives no re-roll.

## 7. Traceability, tests and release

This part ties the requirements to the screens, routes and controls in Parts A to D, lists the new tests, marks what ships in milestone A and what waits for B, and records where each accepted change went and the questions the owner answered. Part C rows are the exact route lists; the tables here point to a group.

### 7.1 Requirements to screens, API groups, controls and tests

| Requirements | Screens | API group | Controls | Tests |
| --- | --- | --- | --- | --- |
| FR-ACC-001 to FR-ACC-007 | SCR-ACC-001 to 003, SCR-ADM-001 | API-IDN | SEC-004 to SEC-009, SEC-011 | T-125 to T-129, T-137, T-160, T-161 |
| FR-CAM-001 to FR-CAM-004, FR-NET-001, FR-NET-002 | SCR-DM-001, 002, 004, 005, SCR-ACC-004, SCR-PLY-001 | API-CMP, API-CNT | SEC-002, SEC-003, SEC-016, SEC-017 | T-121 to T-123, T-137, T-152, T-161 |
| FR-SES, FR-CLK, FR-SCN | SCR-DM-003, SCR-DM-014 | API-CMP, API-ENC | SEC-002, SEC-017 | T-122, T-139 |
| FR-CHR-010 to FR-CHR-016 | SCR-PLY-002 to 005, SCR-DM-007 | API-CHR | SEC-003, SEC-017 | T-122 to T-124 |
| FR-ENC-001 to FR-ENC-017, FR-ACT-001 to FR-ACT-010, FR-MON | SCR-DM-008 to 010, SCR-DM-012 | API-ENC | SEC-006, SEC-012, SEC-023 | T-139, T-140, T-153 |
| FR-RXN-001 to FR-RXN-010 | SCR-DM-011, SCR-PLY-007 | API-RXN | SEC-012 | T-133, T-150 |
| FR-RST-001 to FR-RST-008 | SCR-DM-013, SCR-PLY-008 | API-RST | SEC-017 | T-122 |
| FR-SET-001 to FR-SET-008 | SCR-DM-006, 017, SCR-PLY-009 | API-SET | SEC-017 | T-151 |
| FR-AST-001 to FR-AST-003 | SCR-DM-016 | API-AST | SEC-013 | T-135 |
| FR-AUD-001 | SCR-DM-015 | API-LOG | SEC-012, SEC-021 | T-130, T-131 |
| FR-LIV-001 to FR-LIV-003, and FR-LIV-004 (V2) | SCR-PLY-006, SCR-PLY-011 | API-LIV and the stream in 3.6 | SEC-012, SEC-018 | T-130, T-132, T-155 |
| FR-JRN, FR-CHK, FR-EXP, FR-VIS | SCR-DM-018, 019, SCR-PLY-010 | API-JRN, API-CHK, API-EXP | SEC-012, SEC-014 | T-130, T-136 |
| FR-GRD-010 to FR-GRD-015, FR-AST-004 | SCR-DM-020, SCR-DM-021, SCR-DM-016 | API-MAP, API-PLC, API-AST | SEC-012, SEC-013, SEC-024 | T-166, T-167, T-172, T-174 |
| FR-CHR-002, FR-CHR-017, FR-EXP-001 | SCR-PLY-004, SCR-DM-004, SCR-DM-019 | API-CHR, API-EXP | SEC-021, SEC-022, SEC-025, SEC-026 | T-170, T-171, T-173 |

| Use case | Screens | Flow in 4.4 |
| --- | --- | --- |
| UC-01 | SCR-DM-001, SCR-DM-002, SCR-DM-005 | First run |
| UC-02 | SCR-PLY-002, SCR-PLY-003, SCR-PLY-004, SCR-DM-007 | Join |
| UC-03 | SCR-PLY-005 | Join |
| UC-04 | SCR-DM-008, SCR-DM-009 | Running a turn |
| UC-05 | SCR-DM-010 | Running a turn |
| UC-06 | SCR-DM-011, SCR-PLY-007 | Running a turn |
| UC-07 | SCR-DM-013, SCR-PLY-008 | Resting |
| UC-08 | SCR-DM-005 | Pack changes |
| UC-09 | SCR-PLY-006 (milestone B) | None in v1 |
| UC-10 | No screen in 4.3 cites it | The Requirements place the homebrew subclass editor after the MVP, so no screen cites it |

| Non-functional requirement | Where it is met |
| --- | --- |
| NFR-SEC-001 to NFR-SEC-007 | Part A 3.7 (SEC-001 to SEC-011), Part D 6.4 (SEC-012 to SEC-023), tests T-120 to T-143, T-158, T-159 |
| NFR-USA-001 to NFR-USA-003 | UX-001 to UX-011, 4.6, 4.8, tests T-144 to T-149, T-156, T-157 |
| NFR-REL-001 | Pathfinder failure is never shown to a user: 3.3, 4.6, API-ENC-013, SEC-023, T-140 |
| NFR-REL-002 to NFR-REL-005 | Retry and conflict behaviour in 3.4 and 3.3, T-139, T-141 |
| NFR-PERF-001, NFR-PERF-002 | Loading rules in 4.6, paging in 3.5 |
| NFR-OBS-001 | SEC-010, SEC-021, T-142 |
| NFR-LEG-001 | SCR-ACC-005 and 4.10 (CS-SRD-010) |
| NFR-PERF-003 | Detection runs inside the request and stops after 30 seconds, within the 60 second limit: 5.21, SEC-024, T-172 |

### 7.2 New tests

Ids continue after the last test in the Architecture. A test marked B ships with milestone B.

| Id | Test | Covers | Release |
| --- | --- | --- | --- |
| T-120 | Route walk. Every route answers as the matrix in 3.9 says, and the public allowlist is exactly the four routes in 3.1 | SEC-001 | A |
| T-121 | A non-member gets NOT\_FOUND on every campaign route | SEC-002 | A |
| T-122 | Role matrix. Each role against each route in 3.9 | SEC-017 | A |
| T-123 | Cross-campaign access. Ids from campaign X used under campaign Y are refused | SEC-003 | A |
| T-124 | Mass assignment. Extra form fields are ignored or rejected | SEC-003 | A |
| T-125 | A state-changing request with no token or a wrong token answers CSRF\_INVALID, on sign-in and registration too, and a mismatched Origin is refused | SEC-006 | A |
| T-126 | Sessions. Idle and absolute timeouts, sign-out deletes the row, an administrator reset or a deactivation ends the user's sessions | SEC-004 | A |
| T-127 | Cookie flags, headers and CSP. No inline script or style, no-store where 3.2 says so, history cache off | SEC-005, SEC-007 | A |
| T-128 | Sign-in gives the same message and a similar response time for an unknown user and a wrong password | SEC-009 | A |
| T-129 | Each limit in 3.8 answers RATE\_LIMITED with Retry-After | SEC-008 | A |
| T-130 | Audience golden test. One encounter rendered for the DM and for a player; hidden fields are absent from the player's markup and not only hidden by style | SEC-012 | A |
| T-131 | A log cursor does not reveal gaps in `seq` to a player | SEC-012 | A |
| T-132 | Stream audience classes, closure on removal from the campaign, the five-stream cap | SEC-018 | B |
| T-133 | The reaction prompt carries no hidden information to the reacting player | SEC-012 | A, B |
| T-134 | Hostile names, notes and pack text render escaped. No `th:utext` in any template | SEC-015 | A |
| T-135 | Image upload. Type by leading bytes, SVG refused, size caps, re-encode, name by hash | SEC-013 | A |
| T-136 | Pack file limits: size, entry count, depth and schema | SEC-014 | B |
| T-137 | Invite codes. Expiry, use limits, rate limit, never in a URL or a log | SEC-016 | A |
| T-138 | Open redirect. The return address accepts same-origin paths only | SEC-015 | A |
| T-139 | Idempotency at the HTTP edge. The same action id twice gives one event; the same id with a different body is refused | 3.4 | A |
| T-140 | Pathfinder boundary. A bad path is refused with INVALID\_PATH. A timeout or failure falls back to the engine's own search with no user-visible error | SEC-023, NFR-REL-001 | A |
| T-141 | Error mapping. Every code in 3.3 returns its status and shape, framework refusals included, and a 401 on an HTMX request returns HX-Redirect | 3.2, 3.3 | A |
| T-142 | Logs hold no password, session id, invite code or CSRF token | SEC-010 | A |
| T-143 | The health route returns no version and no detail | SEC-019 | A |
| T-144 | Automated accessibility scan of every screen in its default, empty and error states | 4.8 | A |
| T-145 | Keyboard walk of each flow in 4.4 | 4.8 | A |
| T-146 | Screen reader pass with NVDA and VoiceOver on the flows in 4.4 | 4.8 | A |
| T-147 | No operation needs dragging | 4.8 | A |
| T-148 | Target sizes: 44 px on the player view, 24 px elsewhere | 4.8 | A |
| T-149 | Reflow at 320 px and text at 200 percent | 4.8 | A |
| T-150 | Reaction panel states, timing and focus as in 4.5 | UX-012 to UX-018 | A |
| T-151 | Setting decision flow | SCR-DM-017 | B |
| T-152 | Pack review. A stale hash answers PACK\_REVIEW\_STALE; groups and confirm work | API-CNT | A |
| T-153 | Fragment and full-page parity, and the Vary header | 3.2 | A |
| T-154 | Flows that 3.2 says must work with scripting off do so | 3.2 | A |
| T-155 | A stream reconnect sends `state` first | 3.6 | B |
| T-156 | Layout at the widths in 4.9 | 4.9 | A |
| T-157 | Every error code has a row in the copy catalogue (4.7) | 4.7 | A |
| T-158 | Dependency scan, and the htmx file matches its recorded checksum | SEC-020 | A |
| T-159 | Compose lint. Database and pathfinder ports not published, containers not run as root | SEC-019 | A |
| T-160 | Single-key shortcuts can be turned off or remapped (WCAG 2.1.4) | SCR-ACC-003 | A |
| T-161 | First-account and invite-code registration rules | FR-ACC-003, SEC-016 | A |
| T-162 | Contract walk. For every route in 5.1 to 5.20 the status, the headers and the region ids are as the tables say, for the success and for each listed error | 5.14, 5.15 | A (B for the B routes) |
| T-163 | Error model. Every code in 5.15 produces the members the table gives and nothing more; a 5xx shows no stack, path or SQL; an htmx request gets the fragment and a client that does not accept text/html gets application/problem+json | SEC-009 | A |
| T-164 | Replay by key. A command of each key is sent twice (the stored result, no second effect, no second die), then with another body (IDEMPOTENCY\_KEY\_CONFLICT), then by another principal. A rejected request leaves its id usable, and a form token is used up only by a success | 5.16, TM-008 | A |
| T-165 | Order of checks. A request that fails several checks gets the first failure in the order of 5.15, and a replay after the version has moved returns the stored result | 5.15 | A |
| T-166 | Upload contract. A wrong type, a renamed file, an SVG, an oversized body, an oversized image, an animated image and a decompression bomb are each refused with the right code and nothing stored; the same bytes twice give one file | SEC-013, 5.21 | A for monster images, B for maps |
| T-167 | Map and placement. Detection runs inside the request and ends within its limit, and a failure never blocks saving; a map is ready only after finish; random placement never uses an obstacle or an occupied square, and the preview is the layout that starts; too few squares refuse the start; a player cannot place outside the start area or move another person's token | UX-019 to UX-035, UXQ-11 | B |
| T-168 | Paging. A cursor list gives no repeat and no gap while rows arrive; a player's pages show no seq, no count and no gap; offset lists are stable; no list route returns more than 100 rows unpaged; a page uses a fixed number of queries | 5.21, SEC-012 | A |
| T-169 | Versions seen by players. A player's stream events and conflict responses carry no version (UXQ-19) | UXQ-19, SEC-012 | B |
| T-170 | Rolled ability scores. The server rolls through the dice engine and each die is in the log; the six values are kept as a rolled set; asking again returns the same set and rolls nothing; scores that are not the set's six values, each used once, are VALIDATION\_FAILED; any order of assignment is accepted; creating the character uses the set up, and a second character needs a new roll | UXQ-21, RS-ABL-007 | A |
| T-171 | Export and import of access. The export holds members by username, roles, character owners and invite state, and no hash, session, account id or code. Importing it gives the importer the DM role, matches usernames to accounts, gives the matched members their roles and characters, lists the unmatched ones as account missing, holds their characters out of starts, rests and the unplaced warning, gives them to the person who later joins with that username, lets the DM discard one (a player gets 403), and logs the handover and the discard, and makes every invite unusable. The export is DM only, and a player gets 403 | UXQ-23, SEC-021, SEC-022 | B |
| T-172 | Bounded work. A detection that runs past 30 seconds ends with failureReason timeout and still answers 200. A fourth detection at once and a thirty-first in an hour answer RATE\_LIMITED. Cancelling the request leaves the map as it was. An image whose header claims more than 40 million pixels is refused before decoding. A read past 10 seconds and an upload past 60 seconds are cancelled with SERVICE\_UNAVAILABLE and nothing is stored | SEC-024, 5.21 | B |
| T-173 | Hostile import file. A truncated file, a file over 256 MB or 5000 entries, an entry named with a path outside its folder, a newer formatVersion, and a file that gives a role to someone it should not are each refused or limited as 5.20, 5.21 and SEC-025 say. The importer is the only DM, a file's DM who is not the importer joins as a player, every id is regenerated, every invite is unusable, and a pack upload and an import share the counter of 5 an hour | SEC-014, SEC-025 | B |
| T-174 | Map visibility. A player's map region leaves out the obstacle and difficult flags of squares the player may not see, and SCR-DM-020 says in words that players see the whole image | SEC-012, UXQ-22 | B |
| T-175 | Override audience. A correction of a value a player can see appears in that player's log as the standard sentence, without the old value or the reason, and the earlier entry is marked Corrected. The same correction sent as quiet is refused with VALIDATION\_FAILED and visibility INCORRECT. A quiet correction of a value no player can see sends a signal to the DM only, and appears in no player's log, region or stream. An undo of an entry no player saw is quiet, and an undo of an entry a player saw is announced. The panel route gives quietAllowed false for a value a player can see | UXD-11, UX-060, SEC-012 | B |

### 7.3 Release mapping

| Release | Contents |
| --- | --- |
| A | All of Part A except 3.6. Screens SCR-ACC-001 to 005, SCR-DM-001 to 016, SCR-PLY-001 to 005, SCR-ADM-001 and 002. Parts C and D for those routes |
| B | The stream and live updates (3.6, FR-LIV), SCR-PLY-006 to 011, SCR-DM-017 to 019, pack upload on SCR-DM-005, SCR-DM-020 and SCR-DM-021 (maps, obstacle detection, placement, start areas and the full-screen map), export and import with the campaign's access data and the held characters on SCR-DM-019 and SCR-DM-004, the player side of reactions and rests, the setting decision flow. Tests T-132, T-136, T-151, T-155, T-166 (for maps), T-167, T-169, T-171 to T-174 and the player side of T-133 |

SCR-ACC-004 and FR-CAM-003 are both milestone A (UXQ-5, settled in the Requirements Specification).

### 7.4 Changes to other documents

All of these are applied. Each changed document has its own version and a history row, and its cross-references to this document carry no version.

| Document | Change | Reason |
| --- | --- | --- |
| Requirements | FR-ACC-004: add `/health` to the routes that need no sign-in | 3.1, SEC-019 |
| Requirements | NFR-USA-002: raise the target to WCAG 2.2 AA | 4.8 |
| Requirements | FR-CAM-003: milestone A (UXQ-5) | 4.4 |
| Architecture | Section 9 error table: add UNAUTHENTICATED (401), FORBIDDEN (403), CSRF\_INVALID (403), RATE\_LIMITED (429), PACK\_REVIEW\_STALE (409) | UXD-10, 3.3 |
| Architecture | Session store: absolute timeout filter on top of Spring Session JDBC; single-use form token (UXQ-7); signed double-submit cookie for the sign-in and registration forms | SEC-004, UXQ-7 |
| Decision Log | One entry for this document; progress rows for the UX specification and the threat model | Standing rule |
| AI Build Brief | Add this document to the companion list and the build order; extend the stop note | Standing rule |
| Content Schema | Point the first section to this document; close CS-SRD-010 and CS-HBR-003 against 4.10 and SCR-DM-005 | 4.10 |
| Requirements | UXQ-10 and UXQ-12 are accepted: a requirement for DM map upload with grid and obstacle editing, and one for the full-screen map view, each with a milestone | 4.5, UX-019 to UX-026 |
| Architecture | UXQ-10 and UXQ-11 are accepted: map image storage with a content check and re-encode, and obstacle detection as a server-side step that runs inside the request and that the pathfinder does not depend on (changed from a background task when the owner ruled out job queues). The Material 3 implementation approach under the strict content security policy (4.11) | UXQ-1, UXQ-10, UXQ-11 |
| Grid and Pathfinder Specification | Accepted: a saved map is a grid plus a background image; each obstacle square records whether it was detected or confirmed by the DM; the detection method and its accuracy target | UXQ-10, UXQ-11 |
| Requirements | UXQ-13 is accepted: a requirement for placing creatures before an encounter (by hand, at random on eligible squares, at random inside a DM area) and one for monster images, each with a milestone. UXQ-14 is accepted: a start area that players place their own tokens in, and placing a character by hand. UXQ-15 is accepted: starting with an unplaced character warns first | 4.5, UX-027 to UX-035 |
| Architecture | Random placement chosen on the server from the obstacle map, so every screen shows one layout; storage of monster images beside map images; the administrator status page reads storage use and health without exposing developer details by default | UXQ-13, SCR-ADM-002 |
| Grid and Pathfinder Specification | A query for eligible squares (not an obstacle, not occupied) and the random choice of squares inside a rectangle, with the case where too few squares exist | UX-028, UX-029, UX-033, UX-035 |
| Content Schema | An optional image reference for each monster, for SRD monsters and pack monsters, mapped by the DM | UX-032 |
| Content Schema | Accepted: a map asset (image reference, square size, offset, obstacle and difficult-terrain squares) and whether it belongs to a pack or to a campaign | UXQ-10 |
| This document | UXQ-10 is accepted: upload and map routes in Part C, and file-upload controls and threats in Part D (type confusion, oversized or decompression-bomb images, hostile metadata) | UXQ-10 |
| Requirements | UXQ-16 is accepted: FR-CHK-002 says that a restore first saves a checkpoint of the current state, so a restore can be undone | UXQ-16, UX-055 |
| Architecture, error table | Add ENCOUNTER\_STARTED, MAP\_IN\_USE, PLACEMENT\_INVALID and LAST\_ADMINISTRATOR, and the details members listed for each code in 5.15 | The routes in 5.17 to 5.20 return them, and a client branches on the code |
| Architecture, records | Add a form token record for single-use tokens (key T); a map record with a draft or ready state, detected and confirmed squares, an owner that is the campaign, and a version that rises with every save of the squares (the Architecture keys derived values on a map version); a roster group with its placement choice, start area and preview layout; a rolled set for a player in a campaign (the method, the six values, who rolled and when), removed when the character is created; a held state and an intended owner (a username) on a character, for an import whose member has no account here (UXQ-23); and a map assignment on the encounter | The map, placement and replay routes in 5.16, 5.18 and 5.19 read and write them |
| AI Build Brief | The asset size check is 5 MB for monster images and 20 MB for maps. Add the table definitions for maps, placement and form tokens | Limits in 5.21; records in the row above |
| This document, 3.1 and 3.2 | State that the return address is a query on GET /login, that a command which moves the screen answers with HX-Redirect, and that asset ids are content hashes, an exception to the UUID rule | 5.14 and 5.19 |
| This document, 3.3 | IDEMPOTENCY\_KEY\_CONFLICT also carries the traceId | 5.15 treats it as a bug in the client, so support needs the trace |
| This document, 3.6 | Stream events sent to players carry no version, if UXQ-19 is accepted | 5.20 and UXQ-19 |
| This document, SEC-013 | Monster images: 5 MB and 4096 px. Maps: 20 MB, 8192 px and 40 million pixels. Header dimensions are read before any decode | 5.21, upload checks |
| This document, 3.8 | Uploads share one counter per user; obstacle detection is limited to 30 an hour | 5.21, limits |
| Requirements, FR-CHR-002 | A rolled method is rolled by the server with the dice engine and kept as a rolled set until the character is created, and the player assigns the values freely, as RS-ABL-007 says (UXQ-21) | 5.17; the client never sends a roll |
| Architecture, grid geometry | Add a function to engine.grid that returns the eligible squares for a creature footprint. The Grid and Pathfinder Specification does not change, because the pathfinder stays size-blind (G-3) | The move preview and placement checks in 5.18 and 5.19 need it for large creatures |
| Architecture, web layer (section 8) | The live update push says it carries the new version. The version goes to the DM only; a player's event names the scope and nothing else (UXQ-19) | A version counter shows a player that the DM changed something hidden |
| Requirements, FR-LIV-004 | Move from milestone B to v2. The release table already lists players submitting actions in v2 | Set by the owner on 2026-11-21; 5.12 and 5.20 |
| Requirements, FR-EXP-001 | The export also holds the campaign's access data: members by username with roles, character ownership by username, and invite state. It never holds a password hash, session, account id or invite code | UXQ-23; 5.20 |
| Architecture, saved state and export layout | State that the Access group is exported by username and leaves out invite codes, that an import matches usernames to accounts on the instance, and that a member with no account is held as an intended owner name on the character, which the DM can keep or discard | UXQ-23; API-EXP-001 and API-EXP-002 |
| Requirements, FR-ENC-010 | Add that an override is shown to the players who may see the changed value, as a standard sentence without the old value or the reason, and that the DM may make it quiet only when no player can see that value | UXQ-24, UXD-11 |
| Architecture, events and audience | State that an override event takes its audience from the value it changes, computed by the server, and that its stream signal goes to that audience only | UXQ-24, UXD-11, API-ENC-010 and API-ENC-015 |
| AI Build Brief, open items | Remove the open item on how DM overrides are shown to players and point to this specification | UXQ-24 |

### 7.5 Questions and answers

| Id | Question | Answer |
| --- | --- | --- |
| UXQ-1 | Visual direction. The owner picks a source: research a direction, reference images, a design file, or a design tool | Answered on 2026-11-21: Material 3 components, the Teal dark scheme and the Sky light scheme (4.11). The components are our own CSS written to the Material 3 specification, with no component library (4.11) |
| UXQ-2 | Administrator scope: may the administrator restrict campaign creation, see any campaign, or only manage accounts and the status page | Accounts and status only. The administrator is usually the DM, so the status page uses plain words and keeps developer details behind a technical details control (SCR-ADM-002) |
| UXQ-3 | Does the Architecture store a rejected command? This decides whether an action id can be reused after a refusal | Assume it does not, so the id can be reused |
| UXQ-4 | Should the character sheet refresh by itself in v1 | No; refresh on navigation |
| UXQ-5 | How does a registered user become a campaign member in v1, since FR-CAM-003 is B | A campaign-bound invite code, entered on SCR-ACC-004 |
| UXQ-6 | Password recovery without email | An administrator reset that shows a temporary password once and sets a must-change flag; a command-line step if the administrator is lost |
| UXQ-7 | Creations outside the action log (campaigns, invites, registrations, packs, imports) can be double-submitted | A single-use form token |
| UXQ-8 | Undo of the last action. Does the engine support a reversing override for the latest event only | Yes, as an override that cites the entry (FR-ENC-010) |
| UXQ-9 | What the DM sees when an encounter ends, and whether the Requirements award experience | A summary of rounds, creatures that fell and resources spent, with level-up links if experience exists |
| UXQ-10 | Can the DM upload their own map image, with the grid as the model underneath? Which milestone, and what size limit? | Yes. A saved map is a grid plus a background image, and rules and pathfinding read only the grid (4.5, SCR-DM-020). Accepted on 2026-11-21 with milestone B and a 20 MB limit. Part C routes and Part D file-upload controls are added once this is accepted |
| UXQ-11 | Where does obstacle detection run, and what does the DM see when it is wrong or fails? | Accepted on 2026-11-21: on the server, in Java, so every player screen shows the same result. It runs inside the request, with no queue or background task (changed on 2026-11-21, because the owner ruled out job queues for now), and does not depend on the optional pathfinder (NFR-REL-001). It only suggests: suggested squares look different from confirmed ones and block nothing until the DM saves. The method and its accuracy target belong to the Grid and Pathfinder Specification |
| UXQ-12 | A full-screen map that hides the side panel, with the stats shown as a light overlay when the pointer reaches the screen edge | Accepted on 2026-11-21: yes, as a display mode of the map, with a keyboard and a touch way to open the overlay as well as the pointer (UX-024 to UX-026) |
| UXQ-13 | How are creatures placed on the map when an encounter starts? | Accepted on 2026-11-21: the DM chooses per monster. Group monsters can be placed by the server on any eligible square, or on random eligible squares inside an area the DM draws. A boss or any chosen creature is placed by hand. Monsters can carry an uploaded image mapped to them (UX-027 to UX-032) |
| UXQ-14 | Where do the player characters start on the map? | Accepted on 2026-11-21: both are available. The DM can draw a start area, and each player then places their own token inside it; or the DM can place any character by hand, as for a boss (UX-033, UX-034) |
| UXQ-15 | What happens when the DM starts an encounter while a player character has no position yet? | Accepted on 2026-11-21: the DM sees a warning naming the characters and can start anyway; each unplaced character then joins on the nearest free eligible square inside the start area, or the nearest eligible square if no area is drawn (UX-035) |
| UXQ-16 | Should restoring a checkpoint save a checkpoint of the current state first? | Accepted on 2026-11-21: yes. The restore confirmation says so, and the DM can undo a restore by restoring the checkpoint it made (SCR-DM-019, FR-CHK-002) |
| UXQ-17 | Which single-key shortcuts exist in version 1? | Accepted on 2026-11-21: ? for the list, E end turn, M open the map, R roll for the acting creature, U undo the last action, / search the rules. The help page (SCR-ACC-005) shows the list and the account page turns them off (WCAG 2.1.4) |
| UXQ-18 | How are the player tabs and the DM log areas grouped? | Accepted on 2026-11-21: a player sees four tabs inside a campaign (Table, Characters, Journal, Rules) and reaches My campaigns from the app bar. Flows that need an answer, such as a reaction or a rest, hide the tabs. The DM Log page holds three areas: event log, journal and quests, checkpoints and export |
| UXQ-19 | Version counters change when the DM changes something hidden from the players. Should a player's stream events and conflict answers show them? | No. Events and conflicts for players carry no version; only the DM sees currentVersion. A player's page refetches on any event. The residual risk is recorded in 6.8 |
| UXQ-20 | Field limits, deadlines, rate limits and which character sections an owner may edit are not in the requirements. Take the values in 5.21 as the set? | Yes, as one proposed set, checked in the contract tests. Values the requirements fix (FR-DIC-003, NFR-PERF-001) are cited, not restated |
| UXQ-21 | Should rolled ability scores be rolled when the character is created, or earlier in the wizard as a draft? | The server rolls with the dice engine, each die in the log, and keeps the six values as a rolled set for the player in the campaign until a character is created from it. The player assigns the values freely (RS-ABL-007). Leaving the wizard and coming back shows the same set, so abandoning it gives no re-roll. The set is a record (7.4) |
| UXQ-22 | The map background image shows the whole map to players, including areas their characters have not seen. Accept this for version 1? | Yes. SCR-DM-020 tells the DM in words that players see the whole image. A vision model that crops the image is a later change (5.19, 6.8) |
| UXQ-23 | Who owns the characters in an imported campaign? | The owner decided that the export holds the access data (members by username with their roles, character ownership by username, and invite state), so an import has nothing to assume. The importing DM owns the campaign. Members are matched by username to the accounts on this instance and take their roles and characters. A username with no account here is shown to the DM as account missing. Its characters are held, so a session is not stopped by a player who cannot attend, and they pass to that person when they join with that username. The DM can instead discard a held character (API-CHR-008). Decided by the owner on 2026-11-21 |
| UXQ-24 | How are DM overrides shown to players? (Decision Log OQ-04) | Accepted on 2026-11-22: a player sees a correction in the log for any value they can see, as one standard sentence, and never sees the old value, the reason or a quiet correction. The DM chooses players or quiet, and the server decides whether quiet is allowed (UXD-11, UX-059 to UX-061, API-ENC-010 and API-ENC-015) |

### Revision history

| Version | Date | Change |
| --- | --- | --- |
| 0.1 | 2026-11-21 | First draft: shared conventions, UX, API catalog, threat model, traceability and tests |
| 0.2 | 2026-11-21 | Part A reviewed against the API design, OWASP, authentication and authorization, error handling and idempotency checks. Added: GET and POST only; session end on deactivation and reset; pre-login CSRF token and Origin check; object-src and no-CORS statements; common-password refusal; bounded rate counters and source address rule; stored-result ownership; page size cap; stream closes with its session; boundary validation and framework refusals. UXD-2 to UXD-7 and UXD-10 accepted |
| 0.3 | 2026-11-21 | Part B reviewed with UX validation, journey, critique, UX writing and accessibility checks. Changed: UX-001 scoped to wide screens; focus rules for the reaction panel; help screen and rules reference with header search; renamed rail labels; QR code for the join address; connection-lost state; copy rows for every code this document names; eight added WCAG rows; playtest targets. Added proposals UXQ-8 (undo) and UXQ-9 (encounter end) |
| 0.4 | 2026-11-21 | Visual direction recorded (UXQ-1): Material 3, the Teal dark scheme and the Sky light scheme, with colour roles and measured contrast. Added proposals UXQ-10 (map upload and obstacle editing), UXQ-11 (where detection runs) and UXQ-12 (full-screen map with edge overlay), rules UX-019 to UX-026, screen SCR-DM-020 and the impacts in 7.4. The header version, left at 0.2, now matches the history |
| 0.5 | 2026-11-21 | UXQ-10 accepted with milestone B and a 20 MB limit; UXQ-11 accepted (detection runs on the server in Java, so every screen shows the same result). UXQ-12 stays proposed |
| 0.6 | 2026-11-21 | UXQ-12 accepted (full-screen map with the edge overlay, UX-024 to UX-026) |
| 0.7 | 2026-11-21 | UXQ-13 accepted (placing creatures, UX-027 to UX-032, screen SCR-DM-021). Added proposal UXQ-14 (where player characters start). Wizard steps set: six for SCR-PLY-004 and four for SCR-PLY-005. SCR-ADM-002 reworked for a DM reader: plain-language status with developer details behind a control, and UXQ-2 notes that the administrator is usually the DM. The DM full-screen map reuses the player full-screen design (UXQ-12) |
| 0.8 | 2026-11-21 | UXQ-14 accepted (start area where players place their own token, and placement by hand; UX-033 and UX-034, frame "player start" of SCR-DM-021). Added proposal UXQ-15 (starting an encounter with an unplaced character). Screens SCR-DM-001, 002, 004 to 008 and 012 to 016 designed in Figma; SCR-DM-016 covers one monster entry, and the reference list and search are not designed yet |
| 0.9 | 2026-11-21 | UXQ-15 accepted (UX-035). Added proposals UXQ-16 (restore saves a checkpoint first), UXQ-17 (single-key shortcuts) and UXQ-18 (player tabs and DM log areas). Every screen in the inventory is now designed in Figma: SCR-ACC-002 to 005, SCR-PLY-001, 002 and 007 to 011, and SCR-DM-016 to 019 |
| 0.9.1 | 2026-11-21 | UXQ-16, UXQ-17 and UXQ-18 accepted. Part B completed: rules UX-036 to UX-057 for the shortcuts, the account, player, DM and administration screens; states for an expired session, a code that is not accepted and a refused file; copy rows for seven more codes; the DM rail labels and player tabs aligned to the screens; a new 4.12 on the Figma frames, their sizes and what is not drawn. The REST\_NOT\_ALLOWED copy now matches its Architecture meaning, and ENCOUNTER\_ACTIVE carries the old message. Invite code copy is one message for unknown, expired and used-up codes, as INVITE\_INVALID is one code. Part B and the screens as drawn accepted by the owner on 2026-11-21. The version stays below 1.0 because Parts C and D are not finished |
| 0.9.2 | 2026-11-21 | Part C extended, all Proposed: field types and binding rules (5.13), success responses and headers (5.14), one error model with a code catalogue (5.15), replays and versions (5.16), request and response schemas for every route group (5.17 to 5.20), and paging, limits and the compatibility policy (5.21). New routes for the move preview, maps and placement, in line with UXQ-10, 11, 13, 14 and 15. New codes ENCOUNTER\_STARTED, MAP\_IN\_USE, PLACEMENT\_INVALID and LAST\_ADMINISTRATOR. Tests T-162 to T-169. Added proposals UXQ-19 to UXQ-23 and the impacts in 7.4 |
| 0.9.3 | 2026-11-21 | UXQ-19 to UXQ-23 accepted by the owner. Part C checked against the other documents. Map size corrected to 200 by 200 (FR-GRD-001); maps belong to the campaign only, because the Content Schema has no map kind; a map has a version; random placement draws come from the engine's random source and are logged; the footprint change is recorded against the Architecture and not the Grid and Pathfinder Specification; the version counter in player forms is recorded in 6.8. Two points are open for the owner: whether rolled ability scores are assigned freely, as the Ruleset says (UXQ-21), and whether obstacle detection runs inside the request, because the Decision Log keeps job queues out |
| 0.9.4 | 2026-11-21 | The owner's answers to the conflict check. Rolled ability scores are kept as a rolled set for each player and campaign, and the player assigns the values freely (UXQ-21, closed). Obstacle detection runs inside the request, so API-MAP-006, the status fragment, the 202 answer and the background task are gone, and the clean-up of unfinished maps runs at start-up and when the map list is read; no job queue is needed anywhere. The residual risk of version counters in player forms is accepted (6.8). Players submitting actions (FR-LIV-004, SCR-PLY-011, API-LIV-001) is version 2 with the mark V2, a player has no combat commands before then, and the Requirements change is recorded in 7.4. The export and import carry the access data: members, roles, character owners and invite state, with no secrets (UXQ-23); what an import does with a username that has no account here is open. Tests T-170 and T-171. The Architecture's title and its opening line now both say 5.12, the latest in its history |
| 0.9.5 | 2026-11-21 | UXQ-23 closed by the owner. A username in an imported file that has no account here is shown to the DM as account missing. Its characters are held, so a session goes ahead without them, and they pass to that person when they join with that username. The DM can discard a held character with the new route API-CHR-008; keeping needs no request. Players answering their own reaction prompts stays in version 1.5, as written. Test T-171 extended. No question is left open in 7.5 for UXQ-19 to UXQ-23 |
| 1.0 | 2026-11-21 | Parts C and D accepted by the owner after a last pass, so the document is complete and its version moves from 0.9.5 to 1.0. Changed: the status and the title; the traceability rows for maps, placement, the rolled set, held characters and export (7.1), NFR-PERF-003 and the release mapping (7.3, with FR-CAM-003 settled as milestone A); 7.4 now records the changes as applied and 7.5 holds the answers; the inventory cites the new requirements; the Material 3 components are written as our own CSS. The proposed changes were applied to the Requirements, Architecture, Grid and Pathfinder, Content Schema, Decision Log and AI Build Brief documents. |
| 1.1 | 2026-11-21 | Detection limits raised at the owner's request, to leave room for a slow host and for retries: a user may start 30 detections an hour (was 10) and the instance runs at most 3 at once (was 2). The 30 second stop and the 60 second request limit do not change. Updated in 3.8, 5.21, the threat model, the test for bounded work and 7.4. |
| 1.2 | 2026-11-22 | Decision Log question OQ-04 developed with the interaction design and UX writing checks, all Proposed: how a DM override is shown to players. New UXD-11 and UXQ-24; rules UX-059 to UX-061 (the correction panel, what a player reads, the DM's log); a visibility field on API-ENC-010 and a new panel route API-ENC-015; threat TM-031 and test T-175; the impacts in 7.4. The Figma frames for these rules are drawn separately. |
| 1.3 | 2026-11-22 | The Figma frames for UX-059 to UX-061 are drawn (the correction panel with and without the quiet choice, the DM's log, and the player's log). Corrected the screen reference in UX-061: the DM's log of a correction is SCR-DM-010 and SCR-DM-015, not SCR-DM-017, which is the Setting decision sheet. The refusal banner for a quiet correction that a player could see is in the text only. No rule changed. |
| 1.4 | 2026-11-22 | Requirement reference corrected after the Requirements Specification renumbered duplicate identifiers (1.19): the pack choice that SCR-DM-005 and SCR-DM-009 realise is now FR-MON-015 (the monster requirement formerly numbered FR-MON-001). No screen, route or rule changed. |
| 1.5 | 2026-11-22 | The owner accepted UXD-11, UXQ-24 and rules UX-059 to UX-061. Their Proposed marks are removed from the status, the decision and question tables and the three rules. The changes recorded in 7.4 for the Requirements, Architecture and AI Build Brief are applied, and the rows no longer say they wait for the owner. No rule, route or screen changed. |
