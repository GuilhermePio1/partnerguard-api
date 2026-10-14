# ADR-0025: Read NVD data from the NIST data feeds, not the NVD API

- **Status:** Accepted
- **Date:** 2026-10-14
- **Deciders:** Project owner (approved on 2026-10-14)
- **Related:** [specs/hardening.md](../../specs/hardening.md) rule 12 (HRD-11), [`.github/workflows/nvd-database.yml`](../../.github/workflows/nvd-database.yml)

## Context

Rule 12 of the hardening spec makes CI run OWASP Dependency-Check and fail on
CVSS ≥ 7. Dependency-Check needs a local copy of the NVD, about 407,000 CVE
records. Until now it downloaded them from the NVD REST API with an API key
(repository secret `NVD_API_KEY`).

Two full downloads in a row failed on 2026-10-14:

- In the CI job, the API returned `503` with 12% downloaded, after 31 minutes.
- In the dedicated cache workflow, it returned `503` with 39% downloaded,
  after 20 minutes.

Each time the client aborted after its retries. In
Dependency-Check 13.0.0 the API update records its progress only after the
whole download succeeds, so the next run starts again from the first record.
In both logs, retries 15 to 31 landed within one second with no visible
backoff, so neither a higher retry count nor a longer delay between calls
would ride out a `503` window of any length. The vulnerability database lives in the
Actions cache, which drops entries unused for 7 days, so this full download
has to succeed every time the cache is empty.

NIST also publishes the same data as static JSON 2.0 files: one gzipped feed
per year, a `modified` feed with the last 8 days of changes, and a `.meta`
file next to each one with its last-modified date. Dependency-Check 13.0.0
reads them through `nvdDatafeedUrl` and builds its update plan from the
`.meta` files. Its documentation warns that these feeds have at times been
one to two weeks stale.

The brief (`partnerguard-api.md`) asks for dependency scanning, not for a
specific data source.

## Decision

- Dependency-Check reads the NVD from
  `https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz`
  (`nvdDatafeedUrl` in `pom.xml`). It no longer calls the NVD API, and CI no
  longer needs `NVD_API_KEY`.
- `.github/workflows/nvd-database.yml` (nightly and on demand, never
  cancelled) runs `dependency-check:update-only` and is the only writer of
  the database cache. It saves the cache only when the update succeeds.
- The CI job restores that cache, fetches the day's changes and runs the
  check. With no cached database it fails at once and names the workflow to
  run; any error while updating fails the job too.
- The gate does not change: CVSS ≥ 7 fails the build (HRD-11).

## Consequences

- A full download is a few dozen static files from NIST instead of about 200
  paged API calls, so an empty cache stops being a recurring CI outage.
- One fewer secret to provision; a fork no longer needs its own NVD API key.
- NIST remains the only source of NVD data: no third party enters the chain.
- When NIST's feeds fall behind the API, a newly published CVE reaches the
  gate days later than it would through the API. Accepted: a scan with
  slightly older data beats a scan that never completes.
- Every update downloads whole feed files (the `modified` feed daily, about
  8 MB), more data than an API delta.
- HRD-11 proves the gate. The CI job and the nightly workflow show whether
  the data source works.

## Alternatives considered

| Option | Why not |
|---|---|
| Keep the NVD API and re-run until a full download completes | Failed twice in a row at 12% and 39%. Retries are spent in about a second, and progress is lost on every failure, so each empty cache is a new gamble |
| Raise `nvdMaxRetryCount` and `nvdApiDelay` | The retries in the failed runs came within one second with no visible backoff, so neither setting rides out a `503` window |
| Use the mirror the Dependency-Check project builds from the API (`DependencyCheck_Builder`) | Updated on a best-effort basis, stale whenever the API is down, and adds a second party between NIST and the gate |
| Host our own mirror with the `vulnz` CLI | Needs storage, hosting and a second pipeline that downloads from the same unreliable API |
