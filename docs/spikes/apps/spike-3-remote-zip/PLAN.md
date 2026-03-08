# Prove Spike 3 remote ZIP enumeration and selected-entry download with an isolated desktop harness

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `.agent/PLANS.md`, even though the user explicitly chose this spike-local location instead of the default ExecPlan directory.

- Plan ID: EP-2026-03-06__spike-3-remote-zip-harness
- Status: DONE
- Created: 2026-03-06
- Last Updated: 2026-03-06
- Owner: UNCONFIRMED

## Purpose / Big Picture

After this work ships, a developer can stand up a disposable desktop harness that serves local ZIP fixtures over HTTP, enumerates their internal files without an explicit non-range full-archive fallback path, filters the user-visible candidate set, downloads only the chosen internal entries, and writes a clear evidence bundle for Spike 3. Today that proof is visible from the repo-local Gradle command `./gradlew -p docs/spikes/apps/spike-3-remote-zip test runSpike3Matrix` and the default operator wrapper `cd docs/spikes && just spike-3`.

The user-visible outcome is not a reusable product feature. It is a spike harness that proves the remote ZIP path is feasible on desktop JVM with the exact stack already locked for this spike: Kotlin/JVM 17, JDK `HttpServer`, OkHttp for HTTP range requests, Commons Compress for ZIP structure work, and no fallback full-download path. A successful run must leave a timestamped artifact directory under `docs/spikes/fixtures/generated/spike-3/run-artifacts/` showing request traces, candidate filtering, selected-only outputs, and deterministic failures for unsupported cases.

A novice should be able to read only this file, create the harness, run it, and know they succeeded because the regular, ZIP64, and large-directory cases enumerate correctly, the duplicate-entry case proves offset-based selection, and the no-range, constrained-range, malformed, and encrypted cases fail with explicit stage labels instead of silently switching to a non-range fallback path.

## Progress

- [x] (2026-03-06T00:00Z) Created this draft execution plan in `docs/spikes/apps/spike-3-remote-zip/PLAN.md` with the locked stack, isolated project shape, operator surface, artifact contract, and acceptance evidence.
- [x] (2026-03-06T16:44Z) Re-read the source-of-truth docs, confirmed the owned-folder boundary, and converted this plan from draft to active execution after the user approved the implementation assumptions.
- [x] (2026-03-06T16:50Z) Scaffolded the isolated Gradle/Kotlin JVM project under `docs/spikes/apps/spike-3-remote-zip/` with its own `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and main/test source sets.
- [x] (2026-03-06T16:54Z) Implemented the fixture HTTP server with `/health`, `/range`, `/no-range`, and `/capped-range` behaviors plus explicit `X-Spike-Range-Mode` diagnostics.
- [x] (2026-03-06T16:58Z) Implemented the OkHttp-backed remote range client, read-only remote `SeekableByteChannel`, metadata-first Commons Compress ZIP session, duplicate-safe entry identity, and ignore-glob filtering.
- [x] (2026-03-06T17:02Z) Implemented the Spike 3 matrix runner and artifact writer under `docs/spikes/fixtures/generated/spike-3/run-artifacts/`.
- [x] (2026-03-06T17:05Z) Added automated tests for server behavior, remote channel behavior, duplicate-safe entry selection, encrypted/malformed failures, and matrix artifact writing.
- [x] (2026-03-06T17:09Z) Verified `./gradlew -p docs/spikes/apps/spike-3-remote-zip test runSpike3Matrix` succeeds and writes a passing artifact bundle at `docs/spikes/fixtures/generated/spike-3/run-artifacts/2026-03-06T17-09-02Z/`.
- [x] (2026-03-06T17:18Z) Coordinated the shared `docs/spikes/justfile` changes needed for the server-only and full-matrix operator flows.
- [x] (2026-03-06T20:41Z) Rewrote `docs/spikes/outputs/spike-3-remote-zip.md` into a migration-facing post-run guide and aligned it to the then-latest verified artifact root.
- [x] (2026-03-06T21:01Z) Reran the full operator flow with `cd docs/spikes && just spike-3` after the accuracy pass, producing verified artifact root `docs/spikes/fixtures/generated/spike-3/run-artifacts/2026-03-06T21-01-11Z/`.

## Surprises & Discoveries

- Observation: the generated Spike 3 fixture workspace contains the ZIP files needed for success and invalid-archive coverage, but it does not contain a dedicated “no range support” fixture.
  Evidence: `docs/spikes/fixtures/generated/spike-3/fixtures.md` lists `regular.zip`, `zip64.zip`, `big_directory.zip`, `duplicate_entries.zip`, `nested_archive.zip`, `malformed.zip`, and `strong_encrypted.zip`, but nothing that encodes range support itself.

- Observation: the duplicate-entry fixture contains `sample_text1.txt` twice, and both duplicates share the same CRC and size, so byte content alone cannot prove which duplicate was selected.
  Evidence: `zipinfo -v docs/spikes/fixtures/generated/spike-3/http-root/duplicate_entries.zip` shows `sample_text1.txt` at local-header offsets `0` and `5450`, both with CRC `d335ee7a` and uncompressed size `449`.

- Observation: Commons Compress already handles duplicate names and selected-entry streaming, but it requires a `SeekableByteChannel`; it does not provide an HTTP range channel itself.
  Evidence: `docs/spikes/research/commons-compress.md` identifies `ZipFile.builder().setChannel(...)` as the extension point and explicitly notes that no HTTP transport or range implementation is provided by the module.

- Observation: remote ZIP parsing is tail-read heavy, so a naive one-request-per-byte-seek channel would be noisy and fragile.
  Evidence: `docs/spikes/research/cloudzip.md` and `docs/spikes/research/commons-compress.md` both describe tail scanning of the end-of-central-directory records; the large-directory fixture exists specifically to stress this path.

- Observation: the repo already pins Kotlin `2.2.21` at the root and uses OkHttp `4.12.0` in the Android app, so the isolated spike build can align with those versions without introducing a second HTTP-client version.
  Evidence: `build.gradle.kts` at the repo root applies Kotlin `2.2.21`, and `app/build.gradle.kts` already depends on `com.squareup.okhttp3:okhttp:4.12.0`.

- Observation: the host does not have a discoverable JDK 17 toolchain for Gradle, even though `java -version` reports Java 21 on `PATH`.
  Evidence: `./gradlew -p docs/spikes/apps/spike-3-remote-zip ...` initially failed with “Cannot find a Java installation ... matching languageVersion=17”, `java -version` reported `21.0.8`, and `/usr/libexec/java_home -V` only listed Java 8.

- Observation: the resolved Commons Compress builder API in the harness build exposes `setSeekableByteChannel(...)`, not `setChannel(...)`.
  Evidence: the first compile attempt failed on unresolved `setChannel`, and switching to `setSeekableByteChannel` compiled successfully while preserving the required channel-injection behavior.

- Observation: the constrained-range failure surfaces at the ZIP enumeration phase because the first required archive-body range request happens while Commons Compress opens the central directory.
  Evidence: the verified trace for `run-f-constrained-range-failure` records `HEAD /capped-range/regular.zip` followed by `GET /capped-range/regular.zip` at stage `ZIP_ENUMERATION` with `416` and error code `RANGE_WINDOW_REJECTED`.

## Decision Log

- Decision: this ExecPlan began as a `DRAFT` plan and is now retained as the completed execution record for Spike 3.
  Rationale: the user first requested a future-facing implementation plan, then approved execution in this folder and kept the plan as the durable record of what was built and verified.
  Date/Author: 2026-03-06 / USER + Codex

- Decision: keep the plan in `docs/spikes/apps/spike-3-remote-zip/PLAN.md`.
  Rationale: the user explicitly asked for a self-contained app-local plan so the spike folder owns the implementation brief.
  Date/Author: 2026-03-06 / USER + Codex

- Decision: lock the harness stack to Kotlin/JVM 17, JDK `HttpServer`, OkHttp, Commons Compress, and no fallback full-download path.
  Rationale: these stack choices were already fixed by the user and should not be reopened during implementation.
  Date/Author: 2026-03-06 / USER + Codex

- Decision: implement the harness as a standalone nested Gradle project rooted at `docs/spikes/apps/spike-3-remote-zip/`, but invoke it with the repository's existing root wrapper using `./gradlew -p docs/spikes/apps/spike-3-remote-zip ...`.
  Rationale: this keeps the spike disposable and isolated from the Android build while avoiding a second wrapper toolchain and avoiding edits to the root Android project.
  Date/Author: 2026-03-06 / CODE

- Decision: simulate missing and constrained range support in the local fixture server instead of inventing new ZIP fixtures.
  Rationale: range capability is an HTTP-server behavior, not a ZIP-file property, and the existing fixture inventory already covers the archive shapes the spike needs.
  Date/Author: 2026-03-06 / CODE

- Decision: define entry identity with offset-bearing metadata, not name alone.
  Rationale: duplicate names are required coverage, and `duplicate_entries.zip` proves that path, CRC, and size can still collide in a way that only the local-header offset cleanly disambiguates.
  Date/Author: 2026-03-06 / CODE

- Decision: write run artifacts into timestamped run directories under `docs/spikes/fixtures/generated/spike-3/run-artifacts/`.
  Rationale: repeated runs must be safe, comparable, and non-destructive; timestamped directories satisfy that without needing a database or manual cleanup.
  Date/Author: 2026-03-06 / CODE

- Decision: keep shared spike workspace files such as `docs/spikes/justfile` under coordinator ownership rather than Spike-3-agent ownership.
  Rationale: Spike 1, Spike 2, and Spike 3 all need shared workspace commands and fixture behavior. Centralizing those edits avoids agent collisions while still letting this plan define the exact contract Spike 3 needs from the shared workspace.
  Date/Author: 2026-03-06 / CODE

- Decision: align the nested spike build with the repo's existing Kotlin and OkHttp versions unless verification proves the harness requires a newer release.
  Rationale: matching the repo's current toolchain reduces drift and minimizes dependency risk inside the disposable spike workspace.
  Date/Author: 2026-03-06 / CODE

- Decision: compile the spike harness to JVM 17 bytecode with explicit Java and Kotlin compatibility settings instead of using a strict Gradle toolchain declaration.
  Rationale: the host environment available to this spike run does not expose a discoverable JDK 17 to Gradle, but it does provide a working Java 21 runtime. Explicit compatibility settings let the owned harness compile and run while preserving the locked JVM 17 target.
  Date/Author: 2026-03-06 / CODE

- Decision: preserve wrapped `Spike3FailureException` causes when Commons Compress rethrows range-read failures during ZIP open.
  Rationale: without unwrapping the cause chain, the constrained-range case collapsed into a generic `INVALID_ZIP` result instead of the intended deterministic `RANGE_WINDOW_REJECTED` failure.
  Date/Author: 2026-03-06 / CODE

- Decision: classify the constrained-range failure as `ZIP_ENUMERATION/RANGE_WINDOW_REJECTED`.
  Rationale: the rejecting `416` occurs on the first archive-body range request that Commons Compress issues while opening the central directory, so the operational stage is ZIP enumeration even though the transport symptom is an HTTP range failure.
  Date/Author: 2026-03-06 / CODE

## Outcomes & Retrospective

As of 2026-03-06, the owned-folder Spike 3 harness is implemented and verified. The nested Gradle project now builds from `docs/spikes/apps/spike-3-remote-zip/`, the local fixture server serves the generated ZIP set, the matrix runner writes timestamped evidence under `docs/spikes/fixtures/generated/spike-3/run-artifacts/`, and the latest verified run is `2026-03-06T21-01-11Z`.

The key observed outcomes match the spike contract. `regular.zip`, `zip64.zip`, and `big_directory.zip` enumerate over the metadata-first remote path; `duplicate_entries.zip` downloads only `0000000315__sample_text_large.txt` and `0000005450__sample_text1.txt`; `no-range`, `capped-range`, `malformed`, and `strong_encrypted` produce explicit failure records; and the summary records `Success-path ranged GETs downgraded to 200 responses: 0`, which proves the success path stayed on range semantics even though small fixtures may still fit entirely inside one `206` response window.

The remaining work is outside the owned folder and belongs to the broader architecture phase. The shared `docs/spikes/justfile` recipes and the post-run evidence handoff doc now exist, and the Spike 3 outcome is ready to be consumed as an accepted architecture input.

## Context and Orientation

The repository root is `/Users/nskaria/projects/romulus`. Spike 3 is defined by `docs/spikes/specs/spike-3-remote-zip.md`, which says this spike must prove remote ZIP enumeration and selected-only internal download over HTTP without an explicit full-archive fallback path. The harness guidance already lives in `docs/spikes/apps/spike-3-remote-zip/README.md`, which says the harness should stay under this folder, use a tiny local HTTP server, read generated fixtures from `docs/spikes/fixtures/generated/spike-3/http-root/`, and write runtime evidence to `docs/spikes/fixtures/generated/spike-3/run-artifacts/`.

The fixture workflow is already established. `cd docs/spikes && just fixtures` copies the checked-in reference ZIPs into `docs/spikes/fixtures/generated/spike-3/http-root/`. Today that generated HTTP root contains `regular.zip`, `uncompressed.zip`, `big_directory.zip`, `zip64.zip`, `duplicate_entries.zip`, `nested_archive.zip`, `malformed.zip`, and `strong_encrypted.zip`. Those are the only fixture archives this plan depends on.

The implementation stayed confined to the spike-local project. Shared spike workspace files remain coordinator-owned. The repository paths now created or updated inside this owned folder are:

- `docs/spikes/apps/spike-3-remote-zip/settings.gradle.kts`
- `docs/spikes/apps/spike-3-remote-zip/build.gradle.kts`
- `docs/spikes/apps/spike-3-remote-zip/gradle.properties`
- `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/...`
- `docs/spikes/apps/spike-3-remote-zip/src/test/kotlin/com/romulus/spikes/spike3/...`
- `docs/spikes/apps/spike-3-remote-zip/README.md`

The implementation must not require edits to the repository root `settings.gradle.kts`, root `build.gradle.kts`, or the Android app. The nested project must be self-contained enough that the root wrapper can point at it with `-p` and run its tasks without coupling the spike harness to the mobile app build.

A few plain-English terms matter here. A “range request” means an HTTP request with a `Range: bytes=...` header asking the server for only part of the ZIP file. A “central directory” is the ZIP metadata block near the end of the archive that lists the internal entries. A “selected entry” is one internal file the spike chooses to download after enumeration. A “full-download fallback” would mean retrieving the entire source ZIP when the range path fails; that fallback is explicitly forbidden in this plan.

## Plan of Work

### Milestone 1: scaffold the isolated desktop harness project and hard-code the spike matrix

At the end of this milestone, `docs/spikes/apps/spike-3-remote-zip/` is a runnable Kotlin/JVM 17 project with a main entrypoint, a test source set, and explicit case definitions for every Spike 3 run. The project must define its own `settings.gradle.kts` so Gradle treats this folder as a standalone build when invoked with `-p`.

Create `settings.gradle.kts`, `build.gradle.kts`, and `gradle.properties` in `docs/spikes/apps/spike-3-remote-zip/`. Use the Gradle `application` plugin and the Kotlin/JVM plugin. The only non-test runtime dependencies should be OkHttp and Commons Compress. Use Kotlin test support for unit tests. Do not add a web framework, a second ZIP library, or a full HTTP server library because those would violate the locked stack.

Create a main package root at `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/`. The entrypoint should support two modes: `matrix` and `server`. `matrix` runs the full Spike 3 plan, including starting and stopping the local fixture server in-process. `server` starts the fixture server and waits so a developer can probe it with `curl`.

Put the run matrix in code, not in chat memory. The simplest form is `Spike3CaseDefinitions.kt` under the main source set. It should define these exact cases:

- `run-a-regular-enumeration`: `regular.zip` served with full range support. Enumerate entries and record the visible file list for `a/b/c/d.txt`, `baz.txt`, and `foo/bar.txt`.
- `run-b-zip64-enumeration`: `zip64.zip` served with full range support. Enumerate entries and record that the archive opens successfully as a ZIP64 fixture.
- `run-c-big-directory-enumeration`: `big_directory.zip` served with full range support. Enumerate entries and record the total visible entry count; the artifact must preserve the count without dumping all 150,000 names into one enormous file.
- `run-d-selected-only-duplicate`: `duplicate_entries.zip` served with full range support. Apply ignore glob `*.pdf`, keep both `sample_text1.txt` entries visible, and select exactly two entries: `sample_text_large.txt` at local-header offset `315`, and the second `sample_text1.txt` at local-header offset `5450`. Do not download the first `sample_text1.txt` at offset `0`.
- `run-e-no-range-failure`: `regular.zip` served from the no-range endpoint. Expect an explicit range-support failure before any full ZIP body is downloaded.
- `run-f-constrained-range-failure`: `regular.zip` served from the constrained-range endpoint. Expect a deterministic failure when the server rejects or truncates a required range window.
- `run-g-malformed-failure`: `malformed.zip` served with full range support. Expect an explicit invalid-ZIP failure.
- `run-h-encrypted-failure`: `strong_encrypted.zip` served with full range support. Expect an explicit unsupported-encryption failure.

Store shared path resolution in a small helper such as `Spike3Paths.kt`. It must derive the fixture HTTP root as `docs/spikes/fixtures/generated/spike-3/http-root/` and the artifact root as `docs/spikes/fixtures/generated/spike-3/run-artifacts/` relative to the project directory so the harness remains repo-local and predictable.

### Milestone 2: implement the tiny fixture server and the request-tracing HTTP client

At the end of this milestone, the harness can serve the generated ZIP fixtures over loopback HTTP in three behaviors and record every probe and range request made by the runner.

Create `server/FixtureHttpServer.kt`. Use only JDK `com.sun.net.httpserver.HttpServer`. Bind to `127.0.0.1` and default port `8788`; allow override via `SPIKE3_SERVER_PORT` so a developer can recover from a port conflict without editing code. Expose these endpoints:

- `GET /health` returns `200 OK` with body `OK`.
- `HEAD` and `GET /range/<fixtureName>` return the real file size, advertise `Accept-Ranges: bytes`, and honor valid byte ranges with `206 Partial Content`.
- `HEAD` and `GET /no-range/<fixtureName>` omit `Accept-Ranges`, ignore any `Range` header, and return `200 OK` with the full file body. This endpoint exists only to prove that the harness fails fast rather than falling back.
- `HEAD` and `GET /capped-range/<fixtureName>` advertise `Accept-Ranges: bytes` but reject any requested range window larger than `4096` bytes with `416 Range Not Satisfiable`. Smaller ranges may still return `206`. This endpoint exists to produce a deterministic constrained-range failure.

Keep server behavior obvious. Serve only files that exist in `docs/spikes/fixtures/generated/spike-3/http-root/`. Return `404` for anything else. The server should also add a small diagnostic header such as `X-Spike-Range-Mode` so the trace artifacts make the endpoint behavior easy to read.

Create `http/OkHttpRangeClient.kt` and `http/HttpTraceRecorder.kt`. The client should own two responsibilities: first, probe archive size and range capability with `HEAD`; second, perform exact `GET` range requests for the channel and record a trace line for every request and response. Record at least case id, stage, method, URL path, range header, response code, content length, elapsed milliseconds, and any failure message. Write these trace lines into `http-trace.ndjson` so they stay append-only and easy to inspect.

Do not silently recover from server misbehavior. If `HEAD` does not produce a usable content length, if a success-path `GET` comes back as `200` instead of `206`, or if a required range read is rejected, the client should throw a typed failure that includes the stage and response details. That failure should later become a failure artifact; it must never trigger a full ZIP download.

### Milestone 3: implement the remote channel, ZIP session, candidate filtering, and selected-entry copy

At the end of this milestone, the harness can open a remote ZIP through Commons Compress, enumerate its entries, filter the visible file set, and copy only the selected internal files into a run artifact directory.

Create `zip/RemoteSeekableByteChannel.kt`. This class should implement `SeekableByteChannel` on top of `OkHttpRangeClient`. It must support `size()`, `position()`, `position(long)`, `read(ByteBuffer)`, `isOpen()`, and `close()`. It should reject writes and truncation. Keep a small in-memory cache so the tail window and the most recent read window are reused instead of re-fetching the same bytes repeatedly. A simple and sufficient policy is one tail cache plus one last-read cache. The tail cache should be large enough to cover the end-of-central-directory search, for example `131072` bytes or the whole file if smaller.

Create `zip/RemoteZipSession.kt`. This class is the high-level adapter around `org.apache.commons.compress.archivers.zip.ZipFile`. It should open a `ZipFile` using the remote channel with metadata-first configuration: set `ignoreLocalFileHeader=true` so enumeration comes from the central directory without eagerly resolving every local header. Preserve a mapping from each stable entry identity to the live `ZipArchiveEntry` instance needed for `getInputStream(entry)`. It should expose one method for enumeration and one method for selected-entry copy. Enumeration must exclude directories from the visible user set but still record that they were present in the raw archive listing. Only selected-entry copy is allowed to resolve local headers or entry streams.

Define a stable entry identity in `model/Spike3Types.kt`. The identity must carry enough metadata to distinguish duplicate names. Use these fields: archive fixture name, entry path, local-header offset, compressed size, uncompressed size, and CRC32 when available. The duplicate proof case depends on local-header offset, so that field is mandatory.

Add `filter/IgnoreGlobFilter.kt`. This should accept the enumerated file entries and a list of ignore globs and return three explicit sets: ignored entries, visible entries, and selected entries. Keep glob semantics simple and documented. The ignore logic should use the internal archive path exactly as written in the ZIP. The selected-entry step must match on the stable entry identity, not on name alone.

Create `runner/Spike3MatrixRunner.kt`. It should execute the cases in order, reuse the same artifact writer for the whole run, and keep each case isolated so one failure does not stop later cases from running. For the duplicate-selection case, copy the chosen entry streams into `downloads/` files whose names start with the local-header offset, for example `0000000315__sample_text_large.txt` and `0000005450__sample_text1.txt`. This makes the artifact tree human-readable and duplicate-safe.

### Milestone 4: write the evidence bundle, wire the operator surface, test it, and keep the docs aligned

At the end of the owned-folder portion of this milestone, a developer can run `./gradlew -p docs/spikes/apps/spike-3-remote-zip test runSpike3Matrix` and receive a complete run artifact bundle plus passing tests. The final `cd docs/spikes && just spike-3` wrapper remains a coordinator-owned shared-file follow-up.

Create `artifacts/ArtifactWriter.kt`. Each run must create a fresh timestamped directory under `docs/spikes/fixtures/generated/spike-3/run-artifacts/`, for example `2026-03-06T18-25-41Z/`. Write case artifacts under `cases/<case-id>/`. Use prose-first Markdown for the human-facing summaries and newline-delimited JSON only for the HTTP trace. Each run directory must contain:

- `summary.md`: one-line status for every case plus links or paths to the case-specific artifacts.
- `http-trace.ndjson`: the request and response trace for the entire run.
- `cases/<case-id>/candidate-set.md`: for success cases that enumerate entries.
- `cases/<case-id>/download-manifest.md`: only for the selected-download case.
- `cases/<case-id>/downloads/`: only for the selected-download case.
- `cases/<case-id>/failure.md`: for every failure case.

Keep the case files concise. `candidate-set.md` should include the raw archive file count, the directory count, the visible file count, the ignore globs used, and a small sample of entry identities. For `big_directory.zip`, record the total count and only a small head/tail sample so the artifact remains readable. `download-manifest.md` should list exactly which entry identities were selected, which output files were written, and which visible entries were intentionally not downloaded. `failure.md` should include the fixture name, URL, stage, error code, exception class, and a short explanation that the harness intentionally performed no fallback full download.

Coordinate with the shared spike workspace so `docs/spikes/justfile` eventually adds exactly these recipes:

    spike-3:
        just fixtures
        ../../gradlew -p apps/spike-3-remote-zip clean test runSpike3Matrix

    spike-3-server:
        just fixtures
        ../../gradlew -p apps/spike-3-remote-zip runSpike3Server

`spike-3` is the default operator surface. `spike-3-server` exists only for manual HTTP inspection and troubleshooting. Do not make raw Gradle commands the primary documented flow. The shared fixture workflow must be able to prepare Spike 3 without requiring `.env.local`, because this spike uses only repo-provided fixtures.

After the harness exists, update `docs/spikes/apps/spike-3-remote-zip/README.md` so it matches the actual Gradle task names, server port behavior, artifact tree, and `just` recipes implemented by the code. Do not create `docs/spikes/outputs/spike-3-remote-zip.md` until after a real spike run is complete and the evidence has been reviewed.

## Concrete Steps

Run these commands from the repository root `/Users/nskaria/projects/romulus` unless a different working directory is shown.

1. Prepare the generated Spike 3 fixture workspace.

    cd docs/spikes
    just fixtures

   Expected evidence: `docs/spikes/fixtures/generated/spike-3/http-root/` contains the eight named ZIP fixtures, and `docs/spikes/fixtures/generated/spike-3/fixtures.md` lists them.

2. Confirm the nested build verifies cleanly from the repository root.

    ./gradlew -p docs/spikes/apps/spike-3-remote-zip test runSpike3Matrix

   Verified evidence on 2026-03-06: Gradle ended with `BUILD SUCCESSFUL in 3s`, and the latest verified run wrote `docs/spikes/fixtures/generated/spike-3/run-artifacts/2026-03-06T21-01-11Z/summary.md`.

3. Start the server-only mode when manual HTTP inspection is needed.

    ./gradlew -p docs/spikes/apps/spike-3-remote-zip runSpike3Server

   Expected evidence: the harness prints `Spike 3 server ready at http://127.0.0.1:8788/` unless `SPIKE3_SERVER_PORT` overrides the port.

4. Verify the server-only mode manually.

    cd docs/spikes
    just spike-3-server

   In another shell, run:

    curl -i http://127.0.0.1:8788/health
    curl -i -H 'Range: bytes=0-31' http://127.0.0.1:8788/range/regular.zip
    curl -i -H 'Range: bytes=0-31' http://127.0.0.1:8788/no-range/regular.zip
    curl -i -H 'Range: bytes=0-8191' http://127.0.0.1:8788/capped-range/regular.zip

   Expected evidence:

    HTTP/1.1 200 OK
    OK

    HTTP/1.1 206 Partial Content
    Accept-Ranges: bytes
    X-Spike-Range-Mode: full

    HTTP/1.1 200 OK
    X-Spike-Range-Mode: none

    HTTP/1.1 416 Range Not Satisfiable
    X-Spike-Range-Mode: capped

5. Review the evidence bundle after a successful run.

    ls -1 docs/spikes/fixtures/generated/spike-3/run-artifacts
    sed -n '1,200p' docs/spikes/fixtures/generated/spike-3/run-artifacts/<run-id>/summary.md
    sed -n '1,120p' docs/spikes/fixtures/generated/spike-3/run-artifacts/<run-id>/cases/run-d-selected-only-duplicate/download-manifest.md
    sed -n '1,40p' docs/spikes/fixtures/generated/spike-3/run-artifacts/<run-id>/http-trace.ndjson

   Expected evidence: the summary lists success for the regular, ZIP64, big-directory, and duplicate-selection cases; failure records for the no-range, constrained-range, malformed, and encrypted cases; and the duplicate download manifest shows only offsets `315` and `5450` written to disk.

## Validation and Acceptance

Acceptance is met only when all of the following are true and evidenced by the commands above.

1. `cd docs/spikes && just spike-3` completes with `BUILD SUCCESSFUL` and produces a new timestamped artifact directory under `docs/spikes/fixtures/generated/spike-3/run-artifacts/`.

2. `summary.md` in that run directory records successful enumeration for `regular.zip`, `zip64.zip`, and `big_directory.zip`.

3. The large-directory artifact records the visible entry count for `big_directory.zip` without dumping the entire directory listing into a single unreadable file.

4. `cases/run-d-selected-only-duplicate/candidate-set.md` shows four raw central-directory file entries from `duplicate_entries.zip`, ignores only `sample.pdf`, keeps both `sample_text1.txt` duplicates visible with different offsets, and marks exactly two selected identities: offset `315` and offset `5450`.

5. `cases/run-d-selected-only-duplicate/download-manifest.md` proves that only two output files were written under `downloads/`, that no output was written for the duplicate at offset `0`, and that the output filenames include the offset prefix used to disambiguate duplicates.

6. `http-trace.ndjson` proves that the success cases use only `HEAD` and ranged `GET` requests for archive bodies. A body-bearing success-path request that receives `200 OK` instead of `206 Partial Content` is a failure. This is the practical proof that no non-range fallback path ran, even though a small fixture may still fit entirely inside one `206 Partial Content` response window.

7. `cases/run-c-big-directory-enumeration/candidate-set.md` or a sibling parse-evidence artifact records metadata-first parse mode, including that enumeration used the central directory with `ignoreLocalFileHeader=true` and did not trigger one archive-body read per entry.

8. `cases/run-e-no-range-failure/failure.md` records a range-support failure before the harness downloads the entire ZIP body from `/no-range/regular.zip`.

9. `cases/run-f-constrained-range-failure/failure.md` records a constrained-range failure from `/capped-range/regular.zip` with the stage that triggered it.

10. `cases/run-g-malformed-failure/failure.md` records an invalid-ZIP failure for `malformed.zip`.

11. `cases/run-h-encrypted-failure/failure.md` records an unsupported-encryption or unsupported-feature failure for `strong_encrypted.zip`.

12. `./gradlew -p docs/spikes/apps/spike-3-remote-zip test` passes after implementation, proving the server, channel, and runner behavior are covered by automation.

13. After each real run is reviewed, keep `docs/spikes/outputs/spike-3-remote-zip.md` aligned with the latest accepted evidence and wording.

## Idempotence and Recovery

The fixture-preparation step is already idempotent. Re-running `cd docs/spikes && just fixtures` safely refreshes the generated HTTP root.

The harness run should also be idempotent. Each invocation of `just spike-3` must create a fresh timestamped directory under `docs/spikes/fixtures/generated/spike-3/run-artifacts/` instead of reusing a mutable “current” directory. Build the run directory as `<run-id>.tmp` first and rename it to `<run-id>` only after `summary.md` is written. If the process crashes mid-run, the recovery step is simple: delete the unfinished `.tmp` directory and rerun `just spike-3`.

If port `8788` is already in use, rerun with `SPIKE3_SERVER_PORT=<free-port>` exported in the shell and keep the same operator commands. If a Gradle build gets into a bad state, rerun `./gradlew -p docs/spikes/apps/spike-3-remote-zip clean test runSpike3Matrix`; this is safe because the nested project should write only under its own `build/` directory and the generated spike artifact directory.

Do not add any recovery behavior that fetches the entire archive. The correct recovery for range failures is to record the failure artifact, stop that case, and continue to the next case.

## Artifacts and Notes

The artifact tree should look like this after one successful full run:

    docs/spikes/fixtures/generated/spike-3/run-artifacts/
      2026-03-06T18-25-41Z/
        summary.md
        http-trace.ndjson
        cases/
          run-a-regular-enumeration/
            candidate-set.md
          run-b-zip64-enumeration/
            candidate-set.md
          run-c-big-directory-enumeration/
            candidate-set.md
          run-d-selected-only-duplicate/
            candidate-set.md
            download-manifest.md
            downloads/
              0000000315__sample_text_large.txt
              0000005450__sample_text1.txt
          run-e-no-range-failure/
            failure.md
          run-f-constrained-range-failure/
            failure.md
          run-g-malformed-failure/
            failure.md
          run-h-encrypted-failure/
            failure.md

A representative trace line should look like this:

    {"case":"run-d-selected-only-duplicate","stage":"ENTRY_COPY","method":"GET","path":"/range/duplicate_entries.zip","range":"bytes=5450-5750","status":206,"elapsedMs":3}

A representative failure summary should read like this:

    Fixture: regular.zip
    URL: http://127.0.0.1:8788/no-range/regular.zip
    Stage: HEAD_PROBE
    Error Code: RANGE_UNSUPPORTED
    Message: Server omitted usable byte-range support; full-download fallback is disabled by plan.

## Interfaces and Dependencies

Use these dependencies and no substitutes unless the user explicitly reopens the decision:

- Kotlin/JVM 17 for the desktop harness.
- JDK `com.sun.net.httpserver.HttpServer` for the local fixture server.
- `com.squareup.okhttp3:okhttp` for `HEAD` and `GET` byte-range requests.
- `org.apache.commons:commons-compress` for `ZipFile` and `ZipArchiveEntry` handling.
- Kotlin test support for automated tests.

The nested build in `docs/spikes/apps/spike-3-remote-zip/build.gradle.kts` must register these task names:

- `runSpike3Matrix`: runs the full case matrix and writes artifacts.
- `runSpike3Server`: starts only the local fixture server.

Create these files and keep their responsibilities narrow.

In `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/Spike3Main.kt`, define the CLI entrypoint that dispatches to `matrix` or `server` mode.

In `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/model/Spike3Types.kt`, define plain Kotlin models at least equivalent to:

    data class EntryIdentity(
        val fixtureName: String,
        val entryPath: String,
        val localHeaderOffset: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val crc32: Long?
    )

    data class Spike3Case(
        val id: String,
        val fixtureName: String,
        val endpointMode: RangeMode,
        val ignoreGlobs: List<String>,
        val selectedEntries: List<EntryIdentity>,
        val expectedOutcome: ExpectedOutcome
    )

    data class CaseFailure(
        val caseId: String,
        val stage: FailureStage,
        val errorCode: String,
        val message: String,
        val causeClass: String?
    )

In `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/server/FixtureHttpServer.kt`, define a server surface at least equivalent to:

    class FixtureHttpServer(
        private val httpRoot: Path,
        private val port: Int
    ) : AutoCloseable {
        fun start(): URI
        override fun close()
    }

    enum class RangeMode {
        FULL,
        NONE,
        CAPPED
    }

In `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/http/OkHttpRangeClient.kt`, define an HTTP adapter at least equivalent to:

    interface RangeHttpClient {
        fun probe(caseId: String, url: HttpUrl): RemoteArchiveInfo
        fun read(caseId: String, stage: FailureStage, url: HttpUrl, start: Long, endInclusive: Long): ByteArray
    }

In `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/zip/RemoteSeekableByteChannel.kt`, define the read-only random-access bridge for Commons Compress. It must implement `SeekableByteChannel` and keep all archive-body reads on the range-request path.

In `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/zip/RemoteZipSession.kt`, define a high-level ZIP service at least equivalent to:

    class RemoteZipSession(
        private val caseId: String,
        private val fixtureName: String,
        private val archiveUrl: HttpUrl,
        private val rangeHttpClient: RangeHttpClient
    ) : AutoCloseable {
        fun enumerate(): List<EnumeratedEntry>
        fun copySelected(entries: List<EntryIdentity>, outputDir: Path): List<Path>
        override fun close()
    }

`RemoteZipSession` must open Commons Compress `ZipFile` in metadata-first mode with `ignoreLocalFileHeader=true` during enumeration. Selected-entry copy may then resolve local headers lazily for only the chosen entries.

In `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/filter/IgnoreGlobFilter.kt`, define one small function that turns enumerated entries into ignored, visible, and selected sets without collapsing duplicate names.

In `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/artifacts/ArtifactWriter.kt`, define a writer surface at least equivalent to:

    class ArtifactWriter(private val artifactRoot: Path) {
        fun openRun(runId: String): Path
        fun writeCandidateSet(caseId: String, ...)
        fun writeDownloadManifest(caseId: String, ...)
        fun writeFailure(caseId: String, failure: CaseFailure)
        fun writeSummary(...)
    }

In `docs/spikes/apps/spike-3-remote-zip/src/main/kotlin/com/romulus/spikes/spike3/runner/Spike3MatrixRunner.kt`, define the orchestrator at least equivalent to:

    class Spike3MatrixRunner(
        private val cases: List<Spike3Case>,
        private val fixtureServer: FixtureHttpServer,
        private val artifactWriter: ArtifactWriter
    ) {
        fun runAll(): RunSummary
    }

Create these test files under `src/test/kotlin/com/romulus/spikes/spike3/`:

- `server/FixtureHttpServerTest.kt` to verify `/range`, `/no-range`, `/capped-range`, and `/health` behavior.
- `zip/RemoteSeekableByteChannelTest.kt` to verify size probing, range reads, and failure on `200 OK` archive-body responses.
- `zip/RemoteZipSessionTest.kt` to verify enumeration succeeds for normal archives, duplicate selection is offset-safe, and encrypted or malformed fixtures fail with explicit error codes.
- `runner/Spike3MatrixRunnerTest.kt` to verify a run writes the expected artifact files and never records a success-path full-download request.

## Plan Revision Notes

- (2026-03-06) Created the initial draft plan in `docs/spikes/apps/spike-3-remote-zip/PLAN.md`, locking the isolated project layout, justfile operator surface, range-mode server design, artifact tree, entry-identity rules, and validation evidence for Spike 3.
- (2026-03-06) Tightened the remote ZIP contract so large-directory enumeration must use Commons Compress metadata-first open mode, and moved shared workspace ownership such as `docs/spikes/justfile` out of the Spike 3 agent boundary.
- (2026-03-06) Promoted the plan from draft to active execution after the user approved the owned-folder and Gradle-escalation boundaries, and recorded the decision to align the isolated build with the repo's existing Kotlin and OkHttp versions.
- (2026-03-06) Updated the plan to the verified implementation state: marked the owned-folder harness work complete, recorded the Java 21 host and JVM 17 compatibility workaround, captured the constrained-range stage decision, and left the coordinator-owned `justfile` wiring plus post-run output doc as explicit blocked follow-ups.
