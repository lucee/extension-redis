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
                "__test__writeCommitDelay_ms": 5000
            },
            default=""
            readonly=false
            storage=false
            remoteClients="";
    }

    function run() {
        describe("NearCacheEntriesAreCopiedOnReadPriorToWriteCommit", () => {
            it("does not return 'the same object' from a cacheGet call", () => {
                var someObj = {v: 42}
                var key = "redis-test/#createGuid()#";
                
                cachePut(key = key, value = someObj, cacheName = cacheName);

                var fromCache = cacheGet(key = key, cacheName = cacheName);
                
                expect(fromCache.v).toBe(42);
                
                fromCache.v += 1;
                
                expect(someObj.v).toBe(42, "mutating 'fromCache' did not mutate the initial cache source 'someObj'");
            })
        })
    }
}
