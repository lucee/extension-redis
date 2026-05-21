component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

    public void function beforeAll(){
        variables.cacheName = "NearCacheEntriesAreCopiedOnReadPriorToWriteCommit"
        defineCache();
    }

    public void function afterAll(){
        application action="update" caches={};
    }

    private string function defineCache(){
        var redis = server.getDatasource("redis");
        if ( structCount(redis) eq 0 )
            throw "Redis is not configured?";

        admin
            action="updateCacheConnection"
            type="server"
            password=server.SERVERADMINPASSWORD
            class="lucee.extension.io.cache.redis.simple.RedisCache"
            bundleName="redis.extension"
            name=cacheName
            custom={
                "minIdle":8,
                "maxTotal":40,
                "maxIdle":24,
                "host":redis.server,
                "port":redis.port,
                "socketTimeout":2000,
                "liveTimeout":3600000,
                "idleTimeout":60000,
                "timeToLiveSeconds":0,
                "testOnBorrow":true,
                "rnd":1,
                "__test__writeCommitDelay_ms": 1500
            },
            default=""
            readonly=false
            storage=false
            remoteClients="";
    }

    function run() {
        describe("Near cache regression tests", () => {
            it("LDEV-4413 mutating a cacheGet result does not mutate the cached value", () => {
                var someObj = {v: 42}
                var key = "redis-test/#createGuid()#";

                cachePut(key = key, value = someObj, cacheName = cacheName);

                var fromCache = cacheGet(key = key, cacheName = cacheName);

                expect(fromCache.v).toBe(42);

                fromCache.v += 1;

                expect(someObj.v).toBe(42, "mutating 'fromCache' did not mutate the initial cache source 'someObj'");
            })

            it("LDEV-4413 mutating the original value after cachePut does not leak into the cache", () => {
                var someObj = {v: 42}
                var key = "redis-test/#createGuid()#";

                cachePut(key = key, value = someObj, cacheName = cacheName);

                // Caller mutates after put — eager serialisation at put time must isolate the cached copy
                someObj.v = 99;

                var fromCache = cacheGet(key = key, cacheName = cacheName);

                expect(fromCache.v).toBe(42, "post-put mutation leaked into the near cache (got #fromCache.v#, expected 42)");
            })

            it("LDEV-6327 cacheGet returns the latest value when two puts race on the same key", () => {
                var key = "redis-test/#createGuid()#";

                cachePut(key = key, value = "version-1", cacheName = cacheName);
                cachePut(key = key, value = "version-2", cacheName = cacheName);

                var val = cacheGet(key = key, cacheName = cacheName);

                expect(val).toBe("version-2", "stale-wins: expected 'version-2', got '#val#'");
            })

            it("LDEV-6327 burst of 20 duplicate puts — only the last wins", () => {
                var key = "redis-test/#createGuid()#";

                for ( var i = 1; i <= 20; i++ ) {
                    cachePut(key = key, value = "v#i#", cacheName = cacheName);
                }

                var val = cacheGet(key = key, cacheName = cacheName);

                expect(val).toBe("v20", "burst-overwrite: expected 'v20', got '#val#'");
            })

            it("LDEV-6327 burst of 30 unique keys all survive the drain", () => {
                var keys = [];
                for ( var i = 1; i <= 30; i++ ) {
                    var k = "redis-test/burst-#createGuid()#";
                    arrayAppend(keys, k);
                    cachePut(key = k, value = "burst-#i#", cacheName = cacheName);
                }

                // All should be readable immediately from the near cache
                for ( var i = 1; i <= 30; i++ ) {
                    var v = cacheGet(key = keys[i], cacheName = cacheName);
                    expect(v).toBe("burst-#i#", "burst-survival: keys[#i#] returned '#v#'");
                }

                // Wait past the commit delay and verify drain completed cleanly via the
                // back-of-Redis listing — cacheGetAllIds forces doJoin then queries Redis directly.
                sleep(2000);
                var allIds = cacheGetAllIds("redis-test/burst-*", cacheName);
                var found = 0;
                for ( var k in keys ) {
                    if ( arrayFindNoCase(allIds, k) ) found++;
                }
                expect(found).toBe(30, "burst-drain: expected 30 keys drained to Redis, found #found#");

                // Cleanup
                for ( var k in keys ) cacheRemove(k, false, cacheName);
            })

            it("cacheRemove during drain stall actually removes the queued put", () => {
                var key = "redis-test/remove-#createGuid()#";

                cachePut(key = key, value = "should-be-gone", cacheName = cacheName);
                cacheRemove(key, false, cacheName);

                // After remove + waiting past the commit delay, the key should not be in Redis.
                sleep(2000);
                var allIds = cacheGetAllIds("redis-test/remove-*", cacheName);
                expect(arrayFindNoCase(allIds, key)).toBe(0, "remove-during-stall: key '#key#' still in Redis after cacheRemove");
            })

            it("LDEV-4413 mutations under burst don't cross-contaminate", () => {
                var keys = [];
                var originals = [];
                for ( var i = 1; i <= 10; i++ ) {
                    var k = "redis-test/contam-#createGuid()#";
                    var obj = { v: i };
                    arrayAppend(keys, k);
                    arrayAppend(originals, obj);
                    cachePut(key = k, value = obj, cacheName = cacheName);
                }

                // Mutate every original — none of these should reach the cache
                for ( var i = 1; i <= 10; i++ ) {
                    originals[i].v = -999;
                }

                // Each cacheGet should return the value at put-time, not the mutated one
                for ( var i = 1; i <= 10; i++ ) {
                    var fromCache = cacheGet(key = keys[i], cacheName = cacheName);
                    expect(fromCache.v).toBe(i, "burst-contam: keys[#i#] saw mutation, v=#fromCache.v#");
                }

                // Cleanup
                for ( var k in keys ) cacheRemove(k, false, cacheName);
            })
        })
    }
}
