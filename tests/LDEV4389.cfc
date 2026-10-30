component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	// LDEV-4389: optional per-cache key prefix (custom setting "keyPrefix"), so several caches can share
	// one Redis without seeing, or clearing, each other's keys. Without a prefix nothing changes.
	// The key prefix design follows bpamiri's work in lucee/extension-redis#18.

	variables.unique = lcase( hash( createUUID(), "quick" ) );
	// no prefix, behaves like every cache before LDEV-4389
	variables.plain = "ldev4389_plain";
	// two caches with different prefixes on the same Redis, once with and once without near cache
	variables.modes = [ true, false ];

	function isNotSupported() {
		var redis = server.getDatasource( "redis" );
		return structCount( redis ) eq 0;
	}

	function beforeAll() {
		if ( isNotSupported() ) return;
		var caches = {};
		caches[ variables.plain ] = define();
		for ( var near in variables.modes ) {
			caches[ cacheA( near ) ] = define( "ldev4389-a-#variables.unique#", near );
			caches[ cacheB( near ) ] = define( "ldev4389-b-#variables.unique#", near );
		}
		// the prefix gets a separator appended when it has none, the same prefix written with one is the same cache namespace
		caches[ "ldev4389_a_sep" ] = define( " ldev4389-a-#variables.unique#: ", false );
		// glob characters in a prefix are literal: "ns*" must not reach into the keys of the prefix "nsother"
		caches[ "ldev4389_glob" ] = define( "ldev4389-g*-#variables.unique#", false );
		caches[ "ldev4389_globother" ] = define( "ldev4389-gother-#variables.unique#", false );
		caches[ "ldev4389_glob_q" ] = define( "ldev4389-q?-#variables.unique#", false );
		caches[ "ldev4389_glob_qother" ] = define( "ldev4389-qx-#variables.unique#", false );
		caches[ "ldev4389_glob_b" ] = define( "ldev4389-gb[ab]-#variables.unique#", false );
		caches[ "ldev4389_glob_bother" ] = define( "ldev4389-gba-#variables.unique#", false );
		application action="update" caches=#caches#;
	}

	function afterAll() {
		if ( isNotSupported() ) return;
		// remove what the tests left behind, only our own keys
		cacheClear( "ldev4389-*", variables.plain );
		application action="update" caches={};
	}

	function run( testResults, testBox ) {
		describe( "LDEV-4389: Redis key prefix", function() {

			for ( var near in variables.modes ) {
				// every closure has to see its own copy of the loop variable
				( function( near ) {
					var a = cacheA( near );
					var b = cacheB( near );
					var label = "(nearCache=#near#)";

					describe( "two caches with different prefixes on one Redis #label#", function() {

						beforeEach( function() {
							if ( isNotSupported() ) return;
							cacheClear( cacheName=a );
							cacheClear( cacheName=b );
						} );

						it( title="the same key holds a separate value per cache #label#", skip=isNotSupported(), body=function( currentSpec ) {
							cachePut( id="Shared", value="from a", cacheName=a );
							cachePut( id="Shared", value="from b", cacheName=b );

							expect( cacheGet( id="Shared", cacheName=a ) ).toBe( "from a" );
							expect( cacheGet( id="Shared", cacheName=b ) ).toBe( "from b" );
							expect( cacheIdExists( id="Shared", cacheName=a ) ).toBeTrue();

							cacheRemove( ids="Shared", cacheName=a );
							expect( cacheIdExists( id="Shared", cacheName=a ) ).toBeFalse();
							expect( cacheGet( id="Shared", cacheName=b ) ).toBe( "from b" );
						} );

						it( title="clearing one cache leaves the other intact #label#", skip=isNotSupported(), body=function( currentSpec ) {
							loop from=1 to=5 index="local.i" {
								cachePut( id="key#i#", value="a#i#", cacheName=a );
								cachePut( id="key#i#", value="b#i#", cacheName=b );
							}
							awaitCount( a, 5 );
							awaitCount( b, 5 );
							expect( cacheCount( a ) ).toBe( 5 );
							expect( cacheCount( b ) ).toBe( 5 );

							cacheClear( cacheName=a );

							expect( cacheCount( a ) ).toBe( 0 );
							expect( cacheIdExists( id="key1", cacheName=a ) ).toBeFalse();
							expect( cacheCount( b ) ).toBe( 5 );
							loop from=1 to=5 index="local.i" {
								expect( cacheGet( id="key#i#", cacheName=b ) ).toBe( "b#i#" );
							}
						} );

						it( title="clear by filter only touches the keys of its own cache #label#", skip=isNotSupported(), body=function( currentSpec ) {
							cachePut( id="user:1", value="a1", cacheName=a );
							cachePut( id="user:2", value="a2", cacheName=a );
							cachePut( id="order:1", value="a3", cacheName=a );
							cachePut( id="user:1", value="b1", cacheName=b );
							cachePut( id="user:2", value="b2", cacheName=b );

							awaitCount( a, 3 );
							awaitCount( b, 2 );
							expect( cacheClear( "user:*", a ) ).toBe( 2 );

							expect( cacheGetAllIds( "*", a ) ).toBe( [ "order:1" ] );
							expect( cacheGetAllIds( "user:*", b ).sort( "text" ) ).toBe( [ "user:1", "user:2" ] );
							expect( cacheGet( id="user:1", cacheName=b ) ).toBe( "b1" );
						} );

						it( title="keys, ids and entries do not show the prefix and only list the own cache #label#", skip=isNotSupported(), body=function( currentSpec ) {
							cachePut( id="one", value="a1", cacheName=a );
							cachePut( id="two", value="a2", cacheName=a );
							cachePut( id="three", value="b3", cacheName=b );

							awaitCount( a, 2 );
							awaitCount( b, 1 );
							expect( cacheGetAllIds( cacheName=a ).sort( "text" ) ).toBe( [ "one", "two" ] );
							expect( cacheGetAllIds( "t*", a ) ).toBe( [ "two" ] );
							expect( cacheGetAllIds( cacheName=b ) ).toBe( [ "three" ] );

							var all = cacheGetAll( "*", a );
							expect( structKeyExists( all, "one" ) ).toBeTrue();
							expect( all.one ).toBe( "a1" );
							expect( all ).toHaveLength( 2 );
						} );

						it( title="the keys are stored as prefix:key in Redis #label#", skip=isNotSupported(), body=function( currentSpec ) {
							cachePut( id="Stored", value="x", cacheName=a );
							cachePut( id="Stored", value="y", cacheName=b );
							awaitCount( a, 1 );
							awaitCount( b, 1 );
							var stored = redisCommand( arguments=[ "KEYS", "ldev4389-*-#variables.unique#:stored" ], cache=variables.plain );
							stored = stored.sort( "text" );
							expect( stored ).toHaveLength( 2 );
							expect( stored[ 1 ] ).toBe( "ldev4389-a-#variables.unique#:stored" );
							expect( stored[ 2 ] ).toBe( "ldev4389-b-#variables.unique#:stored" );
							// not stored without the prefix
							expect( redisCommand( arguments=[ "EXISTS", "stored" ], cache=variables.plain ) ).toBe( 0 );
						} );

						it( title="a key with a time to live still expires with a prefix #label#", skip=isNotSupported(), body=function( currentSpec ) {
							cachePut( id="short", value="x", timespan=createTimeSpan( 0, 0, 0, 1 ), cacheName=a );
							expect( cacheIdExists( id="short", cacheName=a ) ).toBeTrue();
							sleep( 2500 );
							expect( cacheIdExists( id="short", cacheName=a ) ).toBeFalse();
						} );
					} );
				} )( near );
			}

			it( title="a prefix without separator and the same prefix with separator and surrounding blanks are the same namespace", skip=isNotSupported(), body=function( currentSpec ) {
				var a = cacheA( false );
				cacheClear( cacheName=a );
				cachePut( id="sepkey", value="v", cacheName=a );
				awaitCount( a, 1 );
				expect( cacheGet( id="sepkey", cacheName="ldev4389_a_sep" ) ).toBe( "v" );
				cacheClear( cacheName=a );
				expect( cacheIdExists( id="sepkey", cacheName="ldev4389_a_sep" ) ).toBeFalse();
			} );

			it( title="glob characters in the prefix are literal and do not reach into other prefixes", skip=isNotSupported(), body=function( currentSpec ) {
				var pairs = [
					[ "ldev4389_glob", "ldev4389_globother" ],
					[ "ldev4389_glob_q", "ldev4389_glob_qother" ],
					[ "ldev4389_glob_b", "ldev4389_glob_bother" ]
				];
				for ( var pair in pairs ) {
					cacheClear( cacheName=pair[ 1 ] );
					cacheClear( cacheName=pair[ 2 ] );
					cachePut( id="k1", value="own", cacheName=pair[ 1 ] );
					cachePut( id="k1", value="foreign", cacheName=pair[ 2 ] );
					awaitCount( pair[ 1 ], 1 );
					awaitCount( pair[ 2 ], 1 );

					expect( cacheGetAllIds( cacheName=pair[ 1 ] ) ).toBe( [ "k1" ], pair[ 1 ] );
					expect( cacheCount( pair[ 1 ] ) ).toBe( 1, pair[ 1 ] );
					cacheClear( cacheName=pair[ 1 ] );
					expect( cacheGet( id="k1", cacheName=pair[ 2 ] ) ).toBe( "foreign", pair[ 1 ] );
					cacheClear( cacheName=pair[ 2 ] );
				}
			} );

			describe( "no prefix behaves as before", function() {

				it( title="put, get, exists, keys and remove work with the plain key", skip=isNotSupported(), body=function( currentSpec ) {
					var id = "ldev4389-plain-#variables.unique#-1";
					cachePut( id=id, value={ a: 1 }, cacheName=variables.plain );
					awaitCount( variables.plain, 1, "ldev4389-plain-#variables.unique#-*" );

					expect( cacheGet( id=id, cacheName=variables.plain ).a ).toBe( 1 );
					expect( cacheIdExists( id=id, cacheName=variables.plain ) ).toBeTrue();
					expect( cacheGetAllIds( "ldev4389-plain-#variables.unique#-*", variables.plain ) ).toBe( [ id ] );
					// stored in Redis under the plain, lowercase key
					expect( redisCommand( arguments=[ "EXISTS", id ], cache=variables.plain ) ).toBe( 1 );

					cacheRemove( ids=id, cacheName=variables.plain );
					expect( cacheIdExists( id=id, cacheName=variables.plain ) ).toBeFalse();
				} );

				it( title="clear by filter removes only the matching keys", skip=isNotSupported(), body=function( currentSpec ) {
					var p = "ldev4389-plain-#variables.unique#-f";
					cachePut( id="#p#:1", value="1", cacheName=variables.plain );
					cachePut( id="#p#:2", value="2", cacheName=variables.plain );
					cachePut( id="#p#x:1", value="3", cacheName=variables.plain );
					awaitCount( variables.plain, 3, "#p#*" );

					expect( cacheClear( "#p#:*", variables.plain ) ).toBe( 2 );
					expect( cacheGetAllIds( "#p#*", variables.plain ) ).toBe( [ "#p#x:1" ] );
					cacheClear( "#p#*", variables.plain );
				} );

				it( title="a cache without prefix sees the keys of a prefixed cache as full keys, like any other key on the same Redis", skip=isNotSupported(), body=function( currentSpec ) {
					var a = cacheA( false );
					cachePut( id="seen", value="v", cacheName=a );
					awaitCount( a, 1 );
					expect( cacheGetAllIds( "ldev4389-a-#variables.unique#:*", variables.plain ) ).toBe( [ "ldev4389-a-#variables.unique#:seen" ] );
					cacheClear( cacheName=a );
				} );
			} );
		} );
	}

	// the near cache writes to Redis in the background, so wait until the keys are listed before
	// asserting on a listing or a clear (otherwise the test would depend on the drain timing)
	private void function awaitCount( required string cacheName, required numeric count, string filter="*" ) {
		var tries = 0;
		while ( arrayLen( cacheGetAllIds( arguments.filter, arguments.cacheName ) ) != arguments.count && ++tries <= 100 ) {
			sleep( 50 );
		}
	}

	private string function cacheA( required boolean near ) {
		return "ldev4389_a_#near ? 'near' : 'direct'#";
	}

	private string function cacheB( required boolean near ) {
		return "ldev4389_b_#near ? 'near' : 'direct'#";
	}

	private struct function define( string keyPrefix="", boolean near=true ) {
		var redis = server.getDatasource( "redis" );
		var custom = {
			"host": redis.server,
			"port": redis.port,
			"socketTimeout": 2000,
			"timeToLiveSeconds": 0,
			"nearCache": arguments.near
		};
		if ( len( arguments.keyPrefix ) ) custom[ "keyPrefix" ] = arguments.keyPrefix;
		return {
			"class": "lucee.extension.io.cache.redis.simple.RedisCache",
			"maven": "org.lucee:redis:#server.system.environment.EXTENSION_VERSION#",
			"custom": custom,
			"readOnly": false,
			"storage": false,
			"default": ""
		};
	}
}
