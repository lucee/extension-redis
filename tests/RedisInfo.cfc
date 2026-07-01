component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	function run( testResults, testBox ) {
		describe( "RedisInfo", function() {

			it( title="RedisInfo returns a struct", body=function( currentSpec ) {
				var info = RedisInfo();
				expect( isStruct( info ) ).toBe( true );
			});

			it( title="RedisInfo returns an empty struct (info to be added in a future version)", body=function( currentSpec ) {
				var info = RedisInfo();
				expect( structCount( info ) ).toBe( 0 );
			});

			it( title="RedisInfo does not accept arguments", body=function( currentSpec ) {
				var hasException = false;
				try {
					RedisInfo( "unexpected" );
				}
				catch ( any e ) {
					hasException = true;
				}
				expect( hasException ).toBe( true );
			});

		});
	}
}
