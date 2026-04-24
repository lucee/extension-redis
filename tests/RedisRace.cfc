component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	variables.sleepInterval = 10; // ms, used for sleep()s

	public function beforeAll(){
		systemOutput("---------------------", true);
		defineCache();
	}

	public function afterAll(){
		CacheClear(cacheName="raceTest_24" );
		CacheClear(cacheName="raceTest_1" );
		application action="update" caches={};
	}

	private string function defineCache(){
		var redis = server.getDatasource("redis");
		if ( structCount(redis) eq 0 )
			throw "Redis is not configured?";
		var caches ={
			 "raceTest_24": {
				"class":"lucee.extension.io.cache.redis.simple.RedisCache",
				"bundleName":"redis.extension",
				"custom":{
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
					"rnd":1
				},
				"readOnly":"false",
				"storage":false,
				"default":""
			},
			"raceTest_1": {
				"class":"lucee.extension.io.cache.redis.simple.RedisCache",
				"bundleName":"redis.extension",
				"custom":{
					"minIdle":8,
					"maxTotal":1,
					"maxIdle":1,
					"host":redis.server,
					"port":redis.port,
					"socketTimeout":2000,
					"liveTimeout":3600000,
					"idleTimeout":60000,
					"timeToLiveSeconds":0,
					"testOnBorrow":true,
					"rnd":1
				},
				"readOnly":"false",
				"storage":false,
				"default":""
			}
		};

		application action="update" caches=#caches#;
		return true;	
	}

	private function directlyUseRedis( cacheName, sleep=0 ) {
		var someObj = {value: 42};
		var someKey = "someKey-devdevdev-justredis";
	
		cachePut(
			someKey,
			someObj,
			createTimeSpan( 9, 9, 9, 9 ),
			createTimeSpan( 9, 9, 9, 9 ),
			arguments.cacheName
		);

		if ( sleep > 0 )
			sleep( arguments.sleep );
		
		var x = cacheGet(cacheName=arguments.cacheName, id=someKey);
		// systemOutput( arguments.cacheName & " " & ( x === someObj ), true);
		// systemOutput( CacheGetMetadata( cacheName=arguments.cacheName, id=someKey), true );
		 // sometimes the cached source and cached origin object are exactly the same, maybe 33% of the time
		
		return (x === someObj);
	}

	function run( testResults , testBox ) {
		describe( "Redis tests", function() {

			beforeEach( function( currentSpec, data ){
				cacheClear(cacheName="raceTest_24" );
				cacheClear(cacheName="raceTest_1" );
			});
			
			it( title='test for race Redis Cache, pool size = 24, no sleep ', body=function( currentSpec ) {
				systemOutput("poolsize 24, no sleep", true);
				var redis = [];
				for (var i=0; i < 11; i++ ){
					redis.append( directlyUseRedis( 'raceTest_24', 0 ) );
				}
				systemOutput(redis, true);
				loop array=redis item="local.r" {
					expect( r ).toBeFalse();
				}
			});

			it( title='test for race Redis Cache, pool size = 1, no sleep ', body=function( currentSpec ) {
				systemOutput("poolsize 1, no sleep", true);
				var redis = [];
				for (var i=0; i < 11; i++ ){
					redis.append( directlyUseRedis( 'raceTest_1', 0 ) );
				}
				systemOutput(redis, true);
				loop array=redis item="local.r"{
					expect( r ).toBeFalse();
				} 
			});

			it( title='test for race Redis Cache, pool size = 24, sleep(10) ', body=function( currentSpec ) {
				systemOutput("poolsize 24, sleep(1)", true);
				var redis = [];
				for (var i=0; i < 11; i++ ){
					redis.append( directlyUseRedis( 'raceTest_24',10 ) );
				}
				systemOutput(redis, true);
				
				loop array=redis item="local.r"{
					expect( r ).toBeFalse();
				}
			});

			it( title='test for race Redis Cache, pool size = 1, sleep(10) ', body=function( currentSpec ) {
				systemOutput("poolsize 1, sleep(1)", true);
				var redis = [];
				for (var i=0; i < 11; i++ ){
					redis.append( directlyUseRedis( 'raceTest_1',10 ) );
				}
				systemOutput(redis, true);
				
				loop array=redis item="local.r"{
					expect( r ).toBeFalse();
				} 
			});

		});
	}
}