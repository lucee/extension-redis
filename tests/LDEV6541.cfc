component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	// LDEV-6541: connect to Redis via a Unix domain socket (custom setting "socket").
	//
	// Needs a Redis server listening on a Unix socket, path passed in via the env var REDIS_SOCKET
	// (CI starts one in .github/workflows/main.yml). Optional REDIS_SOCKET_USERNAME / REDIS_SOCKET_PASSWORD
	// for an ACL user. Skipped when REDIS_SOCKET is not set.

	variables.cacheName = "ldev6541_unix_socket";

	function isNotSupported() {
		var socket = server.system.environment.REDIS_SOCKET ?: "";
		// fileExists() is false for a socket file (not a regular file), java.io.File.exists() is not
		return !len( socket ) || !createObject( "java", "java.io.File" ).init( socket ).exists();
	}

	public function beforeAll() {
		if ( isNotSupported() ) return;
		var env = server.system.environment;
		var caches = {
			"#variables.cacheName#": {
				"class": "lucee.extension.io.cache.redis.simple.RedisCache",
				"maven": "org.lucee:redis:#env.EXTENSION_VERSION#",
				"custom": {
					"socket": env.REDIS_SOCKET,
					// host/port must be ignored when a socket path is set
					"host": "redis-host-is-ignored.invalid",
					"port": 1,
					"username": env.REDIS_SOCKET_USERNAME ?: "",
					"password": env.REDIS_SOCKET_PASSWORD ?: "",
					"nearCache": false,
					"minIdle": 0,
					"maxTotal": 8,
					"maxIdle": 4,
					"socketTimeout": 2000,
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
		describe( "LDEV-6541: Redis via Unix domain socket", function() {

			it( title="PING over the socket", skip=isNotSupported(), body=function( currentSpec ) {
				expect( redisCommand( arguments: [ "PING" ], cache: variables.cacheName ) ).toBe( "PONG" );
			} );

			it( title="the connection really is a Unix socket connection", skip=isNotSupported(), body=function( currentSpec ) {
				var info = redisCommand( arguments: [ "CLIENT", "INFO" ], cache: variables.cacheName );
				// flags=U marks a client connected via Unix socket
				expect( info ).toInclude( "flags=U" );
			} );

			it( title="authenticates with the configured user", skip=isNotSupported() || !len( server.system.environment.REDIS_SOCKET_USERNAME ?: "" ), body=function( currentSpec ) {
				var user = redisCommand( arguments: [ "ACL", "WHOAMI" ], cache: variables.cacheName );
				expect( user ).toBe( server.system.environment.REDIS_SOCKET_USERNAME );
			} );

			it( title="cachePut / cacheGet / cacheRemove via the socket", skip=isNotSupported(), body=function( currentSpec ) {
				var key = "ldev6541-#createUUID()#";
				var value = { name: "unix socket", arr: [ 1, 2, 3 ], nested: { ok: true } };

				cachePut( id: key, value: value, cacheName: variables.cacheName );
				expect( cacheKeyExists( key, variables.cacheName ) ).toBeTrue();

				var fromCache = cacheGet( id: key, cacheName: variables.cacheName );
				expect( fromCache.name ).toBe( "unix socket" );
				expect( fromCache.arr ).toBe( [ 1, 2, 3 ] );
				expect( fromCache.nested.ok ).toBeTrue();

				cacheRemove( ids: key, cacheName: variables.cacheName );
				expect( cacheKeyExists( key, variables.cacheName ) ).toBeFalse();
			} );

			it( title="large value round trip via the socket", skip=isNotSupported(), body=function( currentSpec ) {
				var key = "ldev6541-large-#createUUID()#";
				var value = repeatString( "0123456789", 200000 ); // ~2MB
				cachePut( id: key, value: value, cacheName: variables.cacheName );
				expect( cacheGet( id: key, cacheName: variables.cacheName ) ).toBe( value );
				cacheRemove( ids: key, cacheName: variables.cacheName );
			} );

			it( title="parallel access through the connection pool", skip=isNotSupported(), body=function( currentSpec ) {
				var prefix = "ldev6541-par-#createUUID()#-";
				var cn = variables.cacheName;
				var results = arrayMap( [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 ], function( i ) {
					cachePut( id: prefix & i, value: "v" & i, cacheName: cn );
					return cacheGet( id: prefix & i, cacheName: cn );
				}, true, 8 );
				for ( var i = 1; i <= 16; i++ ) {
					expect( results[ i ] ).toBe( "v" & i );
					cacheRemove( ids: prefix & i, cacheName: cn );
				}
			} );

		} );
	}
}
