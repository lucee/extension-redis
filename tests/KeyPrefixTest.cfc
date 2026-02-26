component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	variables.sleepInterval = 10; // ms

	public function beforeAll(){
		defineCache();
	}

	public function afterAll(){
		// Clean up
		try {
			cacheRemove(filter:"*", cacheName:"prefixedCache1");
		} catch(any e) {}
		try {
			cacheRemove(filter:"*", cacheName:"prefixedCache2");
		} catch(any e) {}
		application action="update" caches={};
	}

	private string function defineCache(){
		var redis = server.getDatasource("redis");
		if (structCount(redis) eq 0)
			throw "Redis is not configured?";

		var caches = {
			// Cache with prefix "app1:"
			"prefixedCache1": {
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
					"timeToLiveSeconds": 60,
					"keyPrefix": "app1:"  // Key prefix for namespace isolation
				},
				"readOnly": false,
				"storage": false,
				"default": ""
			},
			// Cache with different prefix "app2:"
			"prefixedCache2": {
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
					"timeToLiveSeconds": 60,
					"keyPrefix": "app2:"  // Different prefix
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
		describe("Key Prefix Tests", function() {

			it(title="test that same key in different prefixed caches are isolated", body=function(currentSpec) {
				var key = "testKey";
				var val1 = "Value for app1";
				var val2 = "Value for app2";

				// Put same key in both caches
				cachePut(key: key, value: val1, cacheName: "prefixedCache1");
				cachePut(key: key, value: val2, cacheName: "prefixedCache2");
				sleep(variables.sleepInterval);

				// Verify each cache has its own value
				expect(cacheGet(key: key, cacheName: "prefixedCache1")).toBe(val1);
				expect(cacheGet(key: key, cacheName: "prefixedCache2")).toBe(val2);

				// Delete from one cache shouldn't affect the other
				cacheDelete(key: key, cacheName: "prefixedCache1");
				sleep(variables.sleepInterval);

				expect(cacheIdExists(key: key, cacheName: "prefixedCache1")).toBe(false);
				expect(cacheIdExists(key: key, cacheName: "prefixedCache2")).toBe(true);
				expect(cacheGet(key: key, cacheName: "prefixedCache2")).toBe(val2);

				// Cleanup
				cacheDelete(key: key, cacheName: "prefixedCache2");
			});

			it(title="test clear only affects prefixed keys", body=function(currentSpec) {
				var key1 = "clearTest1";
				var key2 = "clearTest2";
				var val = "test value";

				// Put keys in both caches
				cachePut(key: key1, value: val, cacheName: "prefixedCache1");
				cachePut(key: key2, value: val, cacheName: "prefixedCache1");
				cachePut(key: key1, value: val, cacheName: "prefixedCache2");
				cachePut(key: key2, value: val, cacheName: "prefixedCache2");
				sleep(variables.sleepInterval);

				// Verify all keys exist
				expect(cacheIdExists(key: key1, cacheName: "prefixedCache1")).toBe(true);
				expect(cacheIdExists(key: key2, cacheName: "prefixedCache1")).toBe(true);
				expect(cacheIdExists(key: key1, cacheName: "prefixedCache2")).toBe(true);
				expect(cacheIdExists(key: key2, cacheName: "prefixedCache2")).toBe(true);

				// Clear cache1 - should only clear app1: prefixed keys
				cacheRemove(filter: "*", cacheName: "prefixedCache1");
				sleep(variables.sleepInterval);

				// Cache1 should be empty, Cache2 should still have its keys
				expect(cacheIdExists(key: key1, cacheName: "prefixedCache1")).toBe(false);
				expect(cacheIdExists(key: key2, cacheName: "prefixedCache1")).toBe(false);
				expect(cacheIdExists(key: key1, cacheName: "prefixedCache2")).toBe(true);
				expect(cacheIdExists(key: key2, cacheName: "prefixedCache2")).toBe(true);

				// Cleanup
				cacheRemove(filter: "*", cacheName: "prefixedCache2");
			});

			it(title="test keys() returns unprefixed keys", body=function(currentSpec) {
				var key = "keysTest";
				var val = "test value";

				// Put a key
				cachePut(key: key, value: val, cacheName: "prefixedCache1");
				sleep(variables.sleepInterval);

				// Get all keys - should return unprefixed key
				var keys = cacheGetAllIds(cacheName: "prefixedCache1");
				expect(arrayLen(keys)).toBeGTE(1);

				// The returned key should NOT have the prefix
				var found = false;
				for (var k in keys) {
					if (k == key) {
						found = true;
						break;
					}
					// Should NOT start with the prefix
					expect(k.startsWith("app1:")).toBe(false);
				}
				expect(found).toBe(true);

				// Cleanup
				cacheDelete(key: key, cacheName: "prefixedCache1");
			});

		});
	}
}
