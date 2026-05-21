# Changelog

## 4.2.0.0-SNAPSHOT (in development on `fix/LDEV-6327-stale-wins`)

- [LDEV-6327](https://luceeserver.atlassian.net/browse/LDEV-6327) prevent near-cache data loss on transient redis failure — duplicate `cachePut`s no longer stale-win, and async writes during a Redis hiccup are retried instead of silently dropped
- [LDEV-6327](https://luceeserver.atlassian.net/browse/LDEV-6327) near-cache eviction is now O(1) (was O(n) deque scan) — noticeable under high churn
- [LDEV-4413](https://luceeserver.atlassian.net/browse/LDEV-4413) `cacheGet` no longer returns the live in-cache object reference — values are defensively copied, so mutating the returned struct/array can't corrupt the cache
- connection-drop failures now surface as `Unexpected EOF from Redis` instead of misleading null/cast errors — `cacheGet(key, false)` and other default-value calls still return null on miss, only the error message on real connection failures changes

## 4.0.1.3-SNAPSHOT (2026-04-21)

- allow different default values for the log name

## 4.0.1.2-SNAPSHOT (2026-04-21)

- log name is now configurable; more diagnostic logging in general

## 4.0.1.1-SNAPSHOT (2026-02-20)

- default tag type changed to `javax` (so install-and-go works on Lucee 6)

## 4.0.1.0-SNAPSHOT (2026-02-20)

- [LDEV-6120](https://luceeserver.atlassian.net/browse/LDEV-6120) extension now works again for Lucee 6 (javax) and Lucee 7 (jakarta) — single artefact, dual tag classes

## 4.0.0.2 (2026-01-31)

- add GAV (Group-Artifact-Version) to manifest

## 4.0.0.1 (2025-11-19)

- declare minimum Lucee core version in manifest
- internal: clean up avoidable jakarta references

## 4.0.0.0 (2025-08-08) — Lucee 7 / Jakarta EE

- [LDEV-5373](https://luceeserver.atlassian.net/browse/LDEV-5373) **migrate to Jakarta EE for Lucee 7 compatibility** — first release of the 4.x line. Lucee 6 users stay on 3.x.
- publishing moved to Maven Central (from legacy nexus-staging)

## 3.0.1.0 (2025-08-08)

Parallel 3.x release shipped alongside the 4.0.0.0 cut.

- publishing moved to Maven Central (from legacy nexus-staging)

## 3.0.0.55–3.0.0.57 (late 2023, untagged)

Ant-era work that landed on master but was never tagged. Visible in `git log`:

- bundle name reverted to previous names (post-3.0.0.54 bundle-rename fallout)
- [LDEV-5019](https://luceeserver.atlassian.net/browse/LDEV-5019) remove unused `amazon.ion` library
- [LDEV-5019](https://luceeserver.atlassian.net/browse/LDEV-5019) update Secret Manager lib
- remove unnecessary generic type causing problems with some Java versions
- [LDEV-4733](https://luceeserver.atlassian.net/browse/LDEV-4733) fix gzip detection — short payloads were occasionally mis-identified as gzipped (gzip-issue fix targeted 3.0.0.50)

## 3.0.0.54 (2023-06-06)

- [LDEV-4529](https://luceeserver.atlassian.net/browse/LDEV-4529) bundle amazon.ion

## 3.0.0.53 (2023-06-06)

- [LDEV-4529](https://luceeserver.atlassian.net/browse/LDEV-4529) bundle bouncycastle

## 3.0.0.52 (2023-06-05)

- [LDEV-4516](https://luceeserver.atlassian.net/browse/LDEV-4516) add fasterxml to the main Manifest (with a range to avoid version conflicts)

## 3.0.0.51 (2023-06-05)

- support loading classes from other extensions (e.g. Postgres driver)

## 3.0.0.49 (2023-04-27)

- update bundled `com.fasterxml.jackson` jars to 2.15.0; use merged AWS Secret Manager jar

## 3.0.0.48 (2023-03-03)

- test case for race conditions

## 3.0.0.47 (2022-11-28)

- [LDEV-4281](https://luceeserver.atlassian.net/browse/LDEV-4281) update bundled `httpcomponents` libs

## 3.0.0.46 (2022-08-09)

- clean up

## 3.0.0.45 (2022-06-16)

- update bundled jars

## 3.0.0.44 (2022-05-13)

- [LDEV-3990](https://luceeserver.atlassian.net/browse/LDEV-3990) stop logging stack traces for default-value lookups — expected failure, was spamming logs

## 3.0.0.43 (2022-05-11)

- remove all `Require-Bundle` entries that were already linked indirectly

## 3.0.0.42 (2022-05-11)

- merge branch tidy

## 3.0.0.41 (2022-04-26)

- change when `expire` gets set

## 3.0.0.40 (2022-04-26)

- fix a minor issue in bundle relation

## 3.0.0.38 (2022-04-11)

- fix conflict with `apache.commons.logging`

## 3.0.0.37 (2022-03-31)

- merge the two separate commands from release into one

## 3.0.0.36 (2022-03-31)

- support reading int64 values from BSON

## 3.0.0.34 (2022-03-31)

- make sure the time of the redlock is always updated when moving a key from open to closed

## 3.0.0.33 (2022-02-02)

- improve error message on connection error

## 3.0.0.32 (2022-01-26)

- support BSON `IKStorageValue` directly

## 3.0.0.31 (2022-01-05)

- update timestamp of existing records

## 3.0.0.30 (2022-01-05)

- improve SSL support

## 3.0.0.29 (2021-12-03)

(tagged as `2.0.0.29` — likely a typo for `3.0.0.29`)

- resolve the issue where the extension breaks when different versions of the extension are loaded at the same time

## 3.0.0.28 (2021-12-02)

- add alias `connectionAcquireTimeout` for `connectionTimeout`

## 3.0.0.27 (2021-12-01)

- add `connectionTimeout` setting to the driver (including admin frontend)

## 3.0.0.26 (2021-11-30)

- fix timeout in redlock

## 3.0.0.25 (2021-11-30)

- **add `RedisCommandLowPriority()` and `RedisConnectionPoolInfo()` BIFs and `<cfdistributedlock>` tag**

## 3.0.0.24 (2021-11-30)

- **add SSL support**

## 3.0.0.23 (2021-10-12)

- **add AWS Secret Manager support** for credentials

## 3.0.0.22 (2021-06-22)

- **BSON serialisation by default** (with fallback to object serialisation, backwards-compatible reads)
- add method so Lucee can look for object serialisation support

## 3.0.0.21 (2021-03-03)

- improve near cache

## 3.0.0.20 (2021-03-01)

- resolve host every time a new connection is made (handles changing container IPs)

## 3.0.0.19 (2021-02-25)

- **add logging support**

## 3.0.0.18 (2021-02-24)

- do not allow defining when the validators are used

## 3.0.0.17 (2021-02-24)

- improve debugging; default `testOnBorrow` to true

## 3.0.0.16 (2021-02-23)

- improve performance of `getCacheEntry(..., defaultValue)`

## 3.0.0.15 (2021-02-23)

- add support for live and idle timeout; invalidate connection on socket timeout

## 3.0.0.14 (2021-02-17)

- do not invalidate connection when an exception happens

## 3.0.0.13 (2021-02-16)

- improve pool handling

## 3.0.0.12 (2020-12-17)

- convert numbers to strings consistently

## 3.0.0.11 (2020-12-16)

- fix `isObjectStream` method

## 3.0.0.10 (2020-12-15)

- fix locking issue with NearCache (early sibling of LDEV-4413 / LDEV-6327)

## 3.0.0.9 (2020-12-03)

- add support for database index

## 3.0.0.8 (2020-12-03)

- use pipeline in `SET` and `EXPIRE`

## 3.0.0.7 (2020-12-02)

- **DC-3283** add support for pipelining

## 3.0.0.6 (2020-11-19)

- fix NPE

## 3.0.0.5 (2020-11-17)

- add socket timeout

## 3.0.0.4 (2020-11-17)

- **add `RedisCommand()` BIF** — run arbitrary Redis commands from CFML

## 3.0.0.3 (2020-10-15)

- additional null check; passthrough `WildcardFilter`

## 3.0.0.2 (2020-09-25)

- **add support for (in-process) near cache**

## 3.0.0.1 (2020-09-24)

- fix idle time being ignored

## 3.0.0.0 (2020-09-18)

- **switch to a different Redis library**

## 2.9.0.10 (2020-08-21)

- improve connection pool handling

## 2.9.0.7 (2020-07-31)

- ensure the connection always gets closed

## 2.9.0.6 (2020-05-28)

- improve performance for `cacheClear(filter)`

## 2.9.0.3 (2020-05-28)

- improve performance when grabbing more than one entry; switch to binary storage

## 2.9.0.2 (2018-12-12)

- fix issue with `cacheClear`

## 2.9.0.1 (2018-12-12)

- fix case issue

## 2.9.0.0 (2018-11-23)

- **complete rewrite** focused on performance
- split into two drivers: plain Redis and Redis-with-Sentinel
- `hitcount` support removed (cost outweighed value)

## 2.7.2.1-BETA (2017-09-06)

- add UTF-8 BOM to properties files

## 2.7.2.0-BETA (2017-09-06)

- update version info and build process

## Earlier

- Initial commit 2016-08-19 (Thomas Rotter, Michael Offner). Pre-2.7.2.0-BETA history is in `git log`.
