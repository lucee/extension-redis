component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	variables.sleepInterval = 10; // ms

	public function beforeAll(){
		defineCache();
	}

	public function afterAll(){
		try {
			cacheRemove(filter:"*", cacheName:"metricsCache");
		} catch(any e) {}
		application action="update" caches={};
	}

	private string function defineCache(){
		var redis = server.getDatasource("redis");
		if (structCount(redis) eq 0)
			throw "Redis is not configured?";

		var caches = {
			"metricsCache": {
				"class": "lucee.extension.io.cache.redis.RedisCache",
				"bundleName": "redis.extension",
				"custom": {
					"minIdle": 8,
					"maxTotal": 40,
					"maxIdle": 24,
					"host": redis.server,
					"port": redis.port,
					"socketTimeout": 2000,
					"liveTimeout": 3600000,
					"idleTimeout": 60000,
					"timeToLiveSeconds": 60
				},
				"readOnly": false,
				"storage": false,
				"default": ""
			}
		};

		application action="update" caches=#caches#;
		return true;
	}

	function run(testResults, testBox) {
		describe("Cache Metrics Tests", function() {

			it(title="test hit and miss counters", body=function(currentSpec) {
				var key = "metricsTestKey";
				var val = "test value";

				// Get cache metadata which includes statistics
				var info = cacheGetMetadata(cacheName: "metricsCache");

				// Get initial counts (may not be zero if other tests ran)
				var initialHits = info.cacheStatistics.hitCount ?: 0;
				var initialMisses = info.cacheStatistics.missCount ?: 0;
				var initialPuts = info.cacheStatistics.putCount ?: 0;

				// Cause a miss
				try {
					cacheGet(key: key, cacheName: "metricsCache");
				} catch(any e) {
					// Expected - key doesn't exist
				}

				// Put a value
				cachePut(key: key, value: val, cacheName: "metricsCache");
				sleep(variables.sleepInterval);

				// Cause a hit
				var retrieved = cacheGet(key: key, cacheName: "metricsCache");

				// Get updated metadata
				info = cacheGetMetadata(cacheName: "metricsCache");

				// Verify counters increased
				expect(info.cacheStatistics.hitCount).toBeGTE(initialHits + 1);
				expect(info.cacheStatistics.missCount).toBeGTE(initialMisses + 1);
				expect(info.cacheStatistics.putCount).toBeGTE(initialPuts + 1);

				// Verify hit ratio is calculated
				expect(structKeyExists(info.cacheStatistics, "hitRatio")).toBe(true);

				// Cleanup
				cacheDelete(key: key, cacheName: "metricsCache");
			});

			it(title="test pool statistics are available", body=function(currentSpec) {
				// Get cache metadata
				var info = cacheGetMetadata(cacheName: "metricsCache");

				// Verify connection pool info is present
				expect(structKeyExists(info, "connectionPool")).toBe(true);
				expect(structKeyExists(info.connectionPool, "MaxTotal")).toBe(true);
				expect(structKeyExists(info.connectionPool, "NumActive")).toBe(true);
				expect(structKeyExists(info.connectionPool, "NumIdle")).toBe(true);
			});

		});
	}
}
