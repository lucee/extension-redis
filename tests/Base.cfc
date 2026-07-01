component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {


	function run( testResults , testBox ) {
		describe( "Simple Tests", function() {
			
			it( title='simply load RedisCommand class', body=function( currentSpec ) {
				var version = server.system.environment.EXTENSION_VERSION;
				var RedisCommand=createObject("java","lucee.extension.io.cache.redis.udf.RedisCommand",{maven:"org.lucee:redis:#version#"}).init();
			});


		});
	}
}