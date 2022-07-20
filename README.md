## Lucee Redis Cache Extension

[![Java CI](https://github.com/lucee/extension-redis/actions/workflows/main.yml/badge.svg)](https://github.com/lucee/extension-redis/actions/workflows/main.yml)

Issues: https://luceeserver.atlassian.net/issues/?jql=labels%20%3D%20redis

Docs: https://docs.lucee.org/categories/cache.html

The redis driver is based on Jedis. While this is a very robust driver and Redis is amazing this project has to be considered in Beta stage.
Please provide your feedback.

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




