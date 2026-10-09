## Lucee Redis Cache Extension

[![Java CI](https://github.com/lucee/extension-redis/actions/workflows/main.yml/badge.svg)](https://github.com/lucee/extension-redis/actions/workflows/main.yml)

Issues: https://luceeserver.atlassian.net/issues/?jql=labels%20%3D%20redis

Docs: https://docs.lucee.org/categories/cache.html

For performance, the Redis extension stores data using BSON

The Redis extension previously used Jedis.

Please provide your feedback.

### Versions

- 4.x is for Lucee 7+ for Jakarta based servlet engines (Tomcat 10+)
- 3.x is for Lucee 5.4 and 6, for Javax based sevlet engines (Tomcat 9)

### Installation

Install the extension from the Lucee extension store in Lucee admin. Please note that the extension is installable only in the *server* admin.
This means that is not possible to install it for a single web context.

### Create and configure the cache

Create a new cache selecting Redis Cache as Type.

Add some configuration:

* If you like you can use the driver to store the Session Scope. If this is your intention you can flag "Allow to use this cache as client/session storage."
* Server/Host => Tells Lucee how to connect to Redis. By default this is set to localhost:6379.
Please tune this following your environment's needs. Note that the driver actually support a single Redis Server.
* Namespace => choose the namespace that will be used to avoid keys name clashing between differents cache instances.

All set. You are done.

### Connecting via a Unix domain socket

If Redis listens on a Unix domain socket (`unixsocket` in `redis.conf`), for example a socket file shared with the Lucee container through a volume, set the "Socket path" field in the admin, or the `socket` key in the cache definition, to the absolute path of the socket file:

```
this.cache.connections["redis"] = {
	class: "lucee.extension.io.cache.redis.simple.RedisCache",
	bundleName: "redis.extension",
	custom: {
		socket: "/tmp/redis/redis.sock",
		username: "lucee",   // optional, ACL user
		password: "secret"   // optional
	}
};
```

* When `socket` is set, `host`, `port` and `ssl` are ignored (TLS does not apply to a local socket). Username/password authentication and `databaseIndex` work as usual.
* Requires Java 16 or newer.
* The Lucee process needs read/write permission on the socket file (see `unixsocketperm` in `redis.conf`).
* `socketTimeout` only applies to TCP connections; connecting to a local socket fails or succeeds immediately.

### Important

* *Metadata*:
    * The cache will return only the hits count for any single key.
    * The general counter (missed, hits) for the cache instance itself are not updated

* *idletime*:
  Not supported. Any passed value will be ignored. Timespan is fully supported.

### Building

To build the extension, run `mvn package` in the root directory

### Versioning and releases (4.1 branch)

Works like Lucee core (LDEV-6516): every code change gets its own version number. The version is not edited by hand.
`pom.xml` uses a CI-friendly `${revision}` built from `extension.version.base` (`4.1.0`), a build number and
`extension.version.qualifier` (`-SNAPSHOT`).

* **Every push to `4.1`** (a merged PR or a direct commit) takes the highest existing build of the base, from the git tags,
  Maven Central and the Central snapshot repository, adds 1, builds, tests, publishes that version (e.g. `4.1.0.2-SNAPSHOT`)
  to the [Central snapshot repository](https://central.sonatype.com/repository/maven-snapshots/) and creates the tag `4.1.0.2`.
  No `[release]` keyword and no "set version" commit is needed.
* Pushes run one after another (workflow concurrency queue), so two quick merges get two different numbers.
* PR builds, forks and runs without the deploy secrets build and test `4.1.0.0-SNAPSHOT` and never use up a number.
  A commit with `[skip ci]` in its message does not start a build, so it does not get a number either.
  Like Lucee core there is no exception for documentation-only changes: they get a new snapshot too.
* **Release candidate or final release**, either way gets the next number:
  * like Lucee core, commit a change of `extension.version.qualifier` in `pom.xml` to `-RC` (release candidate) or to empty
    (final release). That push publishes e.g. `4.1.0.3-RC` to Maven Central. Afterwards commit `-SNAPSHOT` back
    ("new cycle"), otherwise every following push publishes another RC.
  * or run the workflow by hand on `4.1` (Actions > Run workflow), tick `deploy` and pick `RC`, `BETA`, `ALPHA` or `release`
    as qualifier. That publishes the current code once, without a commit; the pom keeps `-SNAPSHOT`.
* Central keeps snapshots for a limited time only; the git tags keep the numbers unique after a snapshot was removed.
* To start a new line change `extension.version.base`. 4.1 is the stable line for Lucee 6.2 to 7.1; cut an RC / release from here when the fixes should reach users.
