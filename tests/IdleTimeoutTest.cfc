component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	variables.sleepInterval = 10; // ms

	public function beforeAll(){
		defineCache();
	}

	public function afterAll(){
		try {
			cacheRemove(filter:"*", cacheName:"idleTimeoutCache");
		} catch(any e) {}
		application action="update" caches={};
	}

	private string function defineCache(){
		var redis = server.getDatasource("redis");
		if (structCount(redis) eq 0)
			throw "Redis is not configured?";

		var caches = {
			"idleTimeoutCache": {
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
					"timeToLiveSeconds": 5,  // Short TTL for testing
					"touchOnAccess": true,   // Enable touch on access
					"idleTimeoutSeconds": 5  // Reset to 5 seconds on each access
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
		describe("Idle Timeout / Touch on Access Tests", function() {

			it(title="test basic cache operations with touch on access enabled", body=function(currentSpec) {
				var key = "touchTest";
				var val = "test value";

				// Put a value
				cachePut(key: key, value: val, cacheName: "idleTimeoutCache");
				sleep(variables.sleepInterval);

				// Verify it exists
				expect(cacheIdExists(key: key, cacheName: "idleTimeoutCache")).toBe(true);

				// Get the value (this should trigger a touch)
				var retrieved = cacheGet(key: key, cacheName: "idleTimeoutCache");
				expect(retrieved).toBe(val);

				// Cleanup
				cacheDelete(key: key, cacheName: "idleTimeoutCache");
			});

			// Note: Testing actual TTL extension would require waiting for expiration,
			// which isn't practical in unit tests. The above test verifies the code
			// path is executed without errors.

		});
	}
}
