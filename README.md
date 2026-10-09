## Lucee Redis Cache Extension

[![Java CI](https://github.com/lucee/extension-redis/actions/workflows/main.yml/badge.svg)](https://github.com/lucee/extension-redis/actions/workflows/main.yml)

Issues: https://luceeserver.atlassian.net/issues/?jql=labels%20%3D%20redis

Docs: https://docs.lucee.org/categories/cache.html

For performance, the Redis extension stores data using BSON

The Redis extension previously used Jedis.

Please provide your feedback.

### Versions

- 4.2.x is for Lucee 7.1+ only, Maven-based extension build (no OSGi bundling)
- 4.0.x-4.1.x is for Lucee 6 and 7+ (dual javax/jakarta tag classes), OSGi bundle-based build
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

### Important

* *Metadata*:
    * The cache will return only the hits count for any single key.
    * The general counter (missed, hits) for the cache instance itself are not updated

* *idletime*:
  Not supported. Any passed value will be ignored. Timespan is fully supported.

### Building

To build the extension, run `mvn package` in the root directory

### Versioning and releases

The version is not edited by hand. `pom.xml` uses a CI-friendly `${revision}` built from
`extension.version.base` (e.g. `4.2.0`), a build number and `extension.version.qualifier` (e.g. `-ALPHA`).

* Local, PR and normal `master` builds use build number `0` (e.g. `4.2.0.0-ALPHA`) and publish nothing.
* A release build (push to `master` with `[release]` in the commit message, or a manual run with `deploy` checked)
  takes the highest existing build of the base, from the git tags and from Maven Central, adds 1,
  publishes that version (e.g. `4.2.0.1-ALPHA`) and creates the tag `4.2.0.1`.
* A `-SNAPSHOT` qualifier publishes to the Central snapshot repository, any other qualifier (or none) publishes a release.
* To start a new line change `extension.version.base`; for a stable line set `extension.version.qualifier` to empty.



