# Pipo launch intelligence

One deterministic tracker for every Pipo distribution and community surface:
GitHub, F-Droid, Reddit, Hacker News, Product Hunt, Freebuff, public website
surfaces and XERJ.

The code is committed so GitHub Actions can run it. The **data and reports stay
local and git-ignored** — no install telemetry, no analytics, no accounts.

```bash
python launch_tracker/launch_tracker.py --all        # collect every platform
python launch_tracker/launch_tracker.py --report     # rebuild the markdown report
python launch_tracker/launch_tracker.py --health     # adapter status table
python launch_tracker/launch_tracker.py --events     # recent changes
python launch_tracker/launch_tracker.py --milestones # threshold progress
python launch_tracker/launch_tracker.py --history    # snapshot history
python launch_tracker/launch_tracker.py --quiet      # silent unless something changed
```

## Architecture

```
launch_tracker/
  launch_tracker.py        unified CLI (all commands above)
  config/platforms.json    registry, milestones, severities, definitions
  core/
    http.py                GET client: retries, backoff, Retry-After, no token logging
    model.py               normalized result model; missing data is absent, never 0
    storage.py             atomic JSON, append-only JSONL, run lock, dedupe
    changes.py             snapshot diff -> events (first run is a baseline)
    milestones.py          once-only thresholds, persisted in data/milestones.json
    notify.py              console, Windows toast, step summary, webhook
    report.py              reports/PIPO_LAUNCH_REPORT.md
  adapters/                one module per platform, isolated failures
  tests/                   66 tests, fully mocked, no network
  data/                    snapshots, events, milestones, manual observations (ignored)
  reports/                 generated report (ignored)
```

Every adapter returns the same shape: `status`, `metrics` (numbers only),
`meta` (non-numeric detail), `source` URLs, `errors`, `notes`, `collected_at`.
A failing adapter degrades exactly one platform; the rest still update.

## Platform support (verified, not aspirational)

| Platform | Source | Notes |
|---|---|---|
| GitHub | REST API | repo, releases, APK downloads, issue/PR split, commits, traffic with a token |
| F-Droid | GitLab API + f-droid.org | reuses `fdroid_mr_tracker.py`, refreshes its report too |
| Reddit | OAuth or public JSON | 403 is recorded as `blocked`, never as zero engagement |
| Hacker News | Algolia + Firebase | relevance-filtered; a successful empty search is a real zero |
| Product Hunt | GraphQL | needs `PRODUCT_HUNT_TOKEN`; otherwise honestly `auth_required` |
| Freebuff | manual observation file | no public API; dated entries in `data/manual/freebuff.json` |
| Website | HTTP probes | detects a listing disappearing (critical event) |
| XERJ | none | `unsupported`: no endpoint exists or is documented anywhere in this repo |

## Credentials (all optional)

| Variable | Enables |
|---|---|
| `GITHUB_TOKEN` | higher rate limits and 14-day traffic views |
| `REDDIT_CLIENT_ID` / `REDDIT_CLIENT_SECRET` | official Reddit API instead of public JSON |
| `PRODUCT_HUNT_TOKEN` | Product Hunt votes, comments, rating |
| `LAUNCH_WEBHOOK_URL` | webhook notifications (Discord-compatible `content` field) |

Without them the tracker still collects GitHub, F-Droid, Hacker News, website
surfaces and the manual Freebuff observation. Missing credentials are reported
as `auth_required`; they are never treated as zeros.

## Events, milestones, notifications

Events are emitted only for real transitions: metric changes, adapter status
changes, new releases, F-Droid merge/publication, a public surface going down,
and milestones. The first run is a baseline and produces none. Event ids are
content-derived and stored, so re-running on unchanged data cannot duplicate
them.

Severities: `low` (routine counters), `normal` (engagement, releases),
`high` (reviews, external contributions, milestones), `critical` (F-Droid
merge/publication, a listing disappearing).

Notifications go to the console, a Windows toast (high/critical only), the
GitHub Actions step summary, and the webhook. `--test-notifications` prints one
line per channel with its real result.

## Scheduling

```bash
python launch_tracker/launch_tracker.py --schedule 6        # install/update task
python launch_tracker/launch_tracker.py --migrate-schedule  # replace PipoFdroidMR
python launch_tracker/launch_tracker.py --unschedule        # remove the task
```

The legacy `fdroid_mr_tracker.py` keeps its own CLI and its `PipoFdroidMR` task
still works. `--migrate-schedule` removes that task and installs
`PipoLaunchTracker`, which still refreshes `FDROID_MR_TRACKER.md` through the
F-Droid adapter, so nothing is lost.

## GitHub Actions

`.github/workflows/pipo-launch-tracker.yml` runs every 6 hours and on demand:
tests first, then collection, then artifacts (report + data, 90 days).
Default permissions are `contents: read`. Durable history across runs requires
setting the repository variable `LAUNCH_PERSIST=true`; only the separate
`persist-history` job then gets write access, and it commits solely to the
isolated `launch-data` branch — never to `main`.

## Data model and honesty rules

- Missing is `--`. A missing measurement is never coerced to `0`.
- Downloads are not installs; stars are not users; a merge is not a listing.
- GitHub and Freebuff download counters are reported separately and any sum is
  explicitly labelled non-deduplicated.
- Manual observations carry their date and source and go stale visibly.
- Historical data is append-only JSONL; writes are atomic; a lock prevents two
  runs interleaving; stale locks self-heal after 15 minutes.

## Email alerts (Gmail SMTP)

The cloud workflow is the authoritative email sender; the Windows task runs with
`--no-email` so the same event is never emailed twice.

### 1. Create an app password (never the account password)

1. Enable 2-Step Verification on the Google account.
2. Open <https://myaccount.google.com/apppasswords>.
3. Create an app password named `pipo-launch-tracker` and copy the 16 characters.

### 2. Add GitHub repository secrets

GitHub -> repository -> **Settings -> Secrets and variables -> Actions -> Secrets -> New repository secret**:

| Secret | Value |
|---|---|
| `SMTP_HOST` | `smtp.gmail.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | the Gmail address |
| `SMTP_PASSWORD` | the 16-character app password |
| `LAUNCH_ALERT_FROM` | the Gmail address |

### 3. Add GitHub repository variables

Same page -> **Variables -> New repository variable**:

| Variable | Value |
|---|---|
| `LAUNCH_ALERT_EMAIL` | where alerts are delivered |
| `LAUNCH_PERSIST` | `true` to enable `launch-data` history persistence |

### 4. Verify

```bash
python launch_tracker/launch_tracker.py --test-email
python launch_tracker/launch_tracker.py --test-email --simulate-event fdroid_merge
python launch_tracker/launch_tracker.py --deliveries 10
python launch_tracker/launch_tracker.py --digest --force-digest
```

Or: Actions -> **Pipo launch tracker** -> Run workflow (tick `digest_only` to test the digest).

### Delivery semantics

- One email per material event, subject `[Pipo Launch Alert] SEVERITY - title`.
- A delivery is recorded as `sent` only after the SMTP server accepts it; failures
  are retried (3 attempts, exponential backoff) and remain eligible next run.
- `data/notifications/deliveries.jsonl` is the delivery ledger and travels with the
  `launch-data` branch, so deduplication survives separate workflow runs.
- The digest is sent at most once per IST day, only when there is something to say
  (events, milestones, failures or actions), and never on an empty baseline.
- First-run baselines produce no events, therefore no emails.
