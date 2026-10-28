component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	// LDEV-6734: with the near cache enabled and the Redis endpoint down, the async drain thread keeps
	// retrying the pending writes. Every request that needs Redis (a cache miss, a command) waited for that
	// drain, up to 100 seconds per call, instead of failing fast with the connection error.
	//
	// The cache points at a port nothing listens on, so no Redis server has to be stopped.

	variables.cacheName = "ldev6734_endpoint_down";
	// a call that needs Redis has to be done well below the 100 seconds the old drain wait took
	variables.maxMillis = 20000;

	public function beforeAll() {
		var env = server.system.environment;

		// find a free port and release it right away, nothing listens there afterwards
		var ss = createObject( "java", "java.net.ServerSocket" ).init( 0, 1, createObject( "java", "java.net.InetAddress" ).getByName( "127.0.0.1" ) );
		variables.closedPort = ss.getLocalPort();
		ss.close();

		var caches = {
			"#variables.cacheName#": {
				"class": "lucee.extension.io.cache.redis.simple.RedisCache",
				"maven": "org.lucee:redis:#env.EXTENSION_VERSION#",
				"custom": {
					"host": "127.0.0.1",
					"port": variables.closedPort,
					"nearCache": true,
					"minIdle": 0,
					"maxTotal": 8,
					"maxIdle": 4,
					"socketTimeout": 1000,
					"connectionTimeout": 1000,
					"liveTimeout": 3600000,
					"idleTimeout": 60000,
					"timeToLiveSeconds": 0
				},
				"readOnly": false,
				"storage": false,
				"default": ""
			}
		};
		application action="update" caches=#caches#;
	}

	public function afterAll() {
		application action="update" caches={};
	}

	function run( testResults, testBox ) {
		describe( "LDEV-6734: near cache with the Redis endpoint down", function() {

			it( title="cachePut does not block or fail, the value is served from the near cache", body=function( currentSpec ) {
				var key = "ldev6734-put-#createUUID()#";
				var start = getTickCount();
				cachePut( id: key, value: "pending", cacheName: variables.cacheName );
				expect( getTickCount() - start ).toBeLT( variables.maxMillis );

				expect( cacheGet( key, true, variables.cacheName ) ).toBe( "pending" );
			} );

			it( title="a cache miss does not wait for the drain of the pending writes", body=function( currentSpec ) {
				cachePut( id: "ldev6734-pending-#createUUID()#", value: "pending", cacheName: variables.cacheName );
				// give the drain thread time to hit the unreachable endpoint
				sleep( 2000 );

				var start = getTickCount();
				var result = cacheGet( "ldev6734-missing-#createUUID()#", false, variables.cacheName );
				var elapsed = getTickCount() - start;

				expect( isNull( result ) ).toBeTrue();
				expect( elapsed ).toBeLT( variables.maxMillis, "the cache miss took #elapsed#ms, it waited for the drain thread" );
			} );

			it( title="a command fails fast with the connection error instead of waiting for the drain", body=function( currentSpec ) {
				cachePut( id: "ldev6734-cmd-#createUUID()#", value: "pending", cacheName: variables.cacheName );
				sleep( 2000 );

				var start = getTickCount();
				expect( function() {
					redisCommand( arguments: [ "PING" ], cache: variables.cacheName );
				} ).toThrow();
				var elapsed = getTickCount() - start;

				expect( elapsed ).toBeLT( variables.maxMillis, "the command took #elapsed#ms, it waited for the drain thread" );
			} );

		} );
	}
}
