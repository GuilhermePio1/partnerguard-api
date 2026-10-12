---
name: dependency-cve
description: Triage and fix a vulnerable Maven dependency in PartnerGuard (CVE reported by IntelliJ/Mend, OWASP Dependency-Check or the user), and remove security overrides that are no longer needed after a Spring Boot upgrade. Use when a CVE, a "dependency is vulnerable" warning or a Dependency-Check failure is mentioned, or when the Spring Boot version changes.
---

# Dependency CVE: triage, fix, clean up

Detection comes from scanners (IntelliJ/Mend, OWASP Dependency-Check in CI,
spec `HRD-11`). This skill is the **remediation** procedure. Never trust the
scanner's "update to X" blindly. Verify it against primary sources.

Two modes:

- **Fix mode:** a CVE is reported → steps 1–7.
- **Cleanup mode:** the Spring Boot version changed → step 8 only.

Build commands follow `CLAUDE.md` (JDK 21; see `CLAUDE.local.md` for the
machine-specific way to run Maven).

## 1. Identify what is actually resolved

- Which artifact and version, and who brings it in:
  `./mvnw -q dependency:tree -Dincludes=<groupId>` (paths) and
  `./mvnw -q dependency:list -DincludeGroupIds=<groupId>` (every resolved
  artifact; the tree hides duplicate paths).
- Is it managed by Spring Boot? Look for the property in the Boot BOM
  (`spring-boot-dependencies-<boot>.pom` on Maven Central), e.g.
  `<tomcat.version>`, `<jackson-bom.version>`, `<logback.version>`,
  `<postgresql.version>`. Almost everything in this project is.

## 2. Confirm the CVE and the fixed versions from primary sources

- Confirm the CVE exists (NVD/OSV/vendor advisory or a web search). Note the
  affected ranges **per release line**.
- Read the **project's own release notes/changelog** for the candidate fixed
  versions (GitHub releases, `release-notes/VERSION`, `CHANGELOG.md`). Check
  that each reported CVE ID appears in a version ≤ the one you pick.
- When the scanner lists CVEs without fixed versions, the release notes are
  the only reliable source. Don't guess.

## 3. Choose the version

In order of preference:

1. **A newer Spring Boot patch** that already manages a fixed version. Then
   bump Boot instead of overriding, and run cleanup mode (step 8).
2. **A patch release of the line Boot manages** (e.g. Jackson 3.1.5 → 3.1.7),
   even if the scanner suggests a newer minor (3.2.3). Smaller change, same fixes.
3. A newer minor/major line only when the managed line has no fix (e.g.
   logback 1.5.x → 1.6.x). Check its requirements (Java level, aligned deps such
   as slf4j) in the artifact's parent POM.

Age rule: prefer versions published **≥ 2 weeks** ago (`Last-Modified` of the
POM on Maven Central). A younger version is acceptable only when it is the
only fixed version **and** its release notes show a security-only or
narrowly scoped release. Say so explicitly to the user (pgjdbc 42.7.14 case).

Useful lookups:

```bash
# versions of an artifact
curl -s https://repo1.maven.org/maven2/<group/path>/<artifact>/maven-metadata.xml | grep -oE '<version>[^<]+'
# publication date of one version
curl -sI https://repo1.maven.org/maven2/<group/path>/<artifact>/<v>/<artifact>-<v>.pom | grep -i last-modified
```

## 4. Apply the override

- Override the **Boot-managed property** in the security-overrides block of
  `pom.xml` `<properties>`. Never hard-code `<version>` on a dependency, and
  never add a `<dependencyManagement>` entry for something Boot manages. The
  property keeps sibling artifacts aligned (e.g. `tomcat.version` moves core,
  el and websocket together; `jackson-bom.version` moves core and databind).
- Comment format, one per override:

  ```xml
  <!-- <lib> CVE-XXXX-YYYY[, ...], fixed in <v>. Boot <boot> manages <managed>. -->
  <lib.version>X.Y.Z</lib.version>
  ```

  For an aligned companion property, say why (e.g. slf4j aligned with logback).

## 5. Verify

- `dependency:list` shows **only** the new version for every artifact of the
  group (no leftovers of the old one).
- `./mvnw verify` passes (needs Docker for Testcontainers).
- **Runtime check** for libraries the tests don't really exercise: tests use
  MockMvc, so the embedded server never starts. For Tomcat (or anything
  server-side), run the packaged jar against the local stack and call
  `/actuator/health` (expect `{"status":"UP"}`), then stop it.
- Logging, JSON and JDBC libraries are covered by the integration tests:
  context startup, MockMvc JSON and Flyway plus the app pool on Testcontainers.

## 6. Analyze real exposure

For each CVE, state whether this project's configuration reaches the vulnerable
path (e.g. "needs SiftingAppender: not used", "needs `@JsonTypeInfo`: not used",
"HTTP/2 off by default"). Classify each as:

- **reachable**: fix now, and add a regression test if feasible;
- **not reachable**: fix anyway (scanner noise, CI gate CVSS ≥ 7), but note
  why it isn't urgent;
- **reachable only if a pattern is adopted later**: propose a guard (ArchUnit
  rule, spec rule) so a future change can't make it exploitable.

If the advisory recommends a hardening practice (e.g. pgjdbc's
`sslmode=verify-full`), offer to turn it into a spec rule + ADR (`new-adr`). Don't
add it unasked.

## 7. Report

Report to the user: CVE → chosen version and why (especially when it differs
from the scanner's suggestion), verification results, exposure classification,
and any rule left to their decision. Cite the sources used.

## 8. Cleanup mode: after any Spring Boot version change

For **every** property in the security-overrides block:

1. Find the version the new Boot BOM manages for that property.
2. If managed ≥ override, **delete the override and its comment**. Delete
   aligned companions too (e.g. `slf4j.version`) when the managed values are
   consistent.
3. If managed < override, keep it and update the "Boot X manages Y" part of
   the comment.
4. Run step 5. Then re-scan (IntelliJ or Dependency-Check) to confirm no CVE
   came back.

Overrides left in place go stale: they pin old versions that will collect new
CVEs. The block should shrink with every Boot upgrade.
