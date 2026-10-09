component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	// LDEV-6538: keys are stored lowercased, so wildcard filters passed to
	// cacheClear() / cacheGetAllIds() / cacheGetAll() need the same normalization,
	// otherwise a filter with uppercase characters matches nothing.

	variables.cacheName = "ldev6538";

	function isNotSupported() {
		var redis = server.getDatasource( "redis" );
		return structCount( redis ) eq 0;
	}

	function beforeAll() {
		if ( isNotSupported() ) return;
		var redis = server.getDatasource( "redis" );
		application action="update" caches={
			"#variables.cacheName#": {
				"class": "lucee.extension.io.cache.redis.RedisCache",
				"maven": "org.lucee:redis:#server.system.environment.EXTENSION_VERSION#",
				"custom": {
					"host": redis.server,
					"port": redis.port,
					"socketTimeout": 2000,
					"timeToLiveSeconds": 0
				},
				"readOnly": false,
				"storage": false,
				"default": ""
			}
		};
	}

	function afterAll() {
		application action="update" caches={};
	}

	function run( testResults, testBox ) {
		describe( "LDEV-6538: Redis wildcard filters with uppercase characters", function() {

			it( title="cacheGetAllIds() with a mixed-case wildcard filter finds the keys", skip=isNotSupported(), body=function( currentSpec ) {
				var prefix = createPrefix();
				fill( prefix );

				expect( cacheGetAllIds( "#prefix#:Users:Active:*", variables.cacheName ) ).toHaveLength( 2 );
				expect( cacheGetAllIds( "#ucase( prefix )#:USERS:*", variables.cacheName ) ).toHaveLength( 3 );
				expect( cacheGetAll( "#prefix#:Users:Active:*", variables.cacheName ) ).toHaveLength( 2 );

				cacheClear( "#prefix#:*", variables.cacheName );
			} );

			it( title="cacheClear() with a mixed-case wildcard filter removes the matching keys only", skip=isNotSupported(), body=function( currentSpec ) {
				var prefix = createPrefix();
				fill( prefix );

				expect( cacheClear( "#prefix#:Users:Active:*", variables.cacheName ) ).toBe( 2 );
				expect( cacheGetAllIds( "#prefix#:*", variables.cacheName ) ).toHaveLength( 2 );
				expect( cacheIdExists( "#prefix#:users:inactive:x3", variables.cacheName ) ).toBeTrue();
				expect( cacheIdExists( "#prefix#:orders:x4", variables.cacheName ) ).toBeTrue();

				cacheClear( "#prefix#:*", variables.cacheName );
			} );

			it( title="lowercase wildcard filters still work as before", skip=isNotSupported(), body=function( currentSpec ) {
				var prefix = createPrefix();
				fill( prefix );

				expect( cacheGetAllIds( "#prefix#:users:active:*", variables.cacheName ) ).toHaveLength( 2 );
				expect( cacheClear( "#prefix#:users:*", variables.cacheName ) ).toBe( 3 );
				expect( cacheGetAllIds( "#prefix#:*", variables.cacheName ) ).toHaveLength( 1 );

				cacheClear( "#prefix#:*", variables.cacheName );
				expect( cacheGetAllIds( "#prefix#:*", variables.cacheName ) ).toHaveLength( 0 );
			} );

		} );
	}

	private string function createPrefix() {
		return "ldev6538-" & lcase( hash( createUUID(), "quick" ) );
	}

	private void function fill( required string prefix ) {
		cachePut( id="#arguments.prefix#:Users:Active:x1", value=1, cacheName=variables.cacheName );
		cachePut( id="#arguments.prefix#:Users:Active:x2", value=2, cacheName=variables.cacheName );
		cachePut( id="#arguments.prefix#:Users:Inactive:x3", value=3, cacheName=variables.cacheName );
		cachePut( id="#arguments.prefix#:Orders:x4", value=4, cacheName=variables.cacheName );
	}

}
