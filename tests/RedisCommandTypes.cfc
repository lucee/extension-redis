component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	variables.sleepInterval = 10; // ms, used for sleep()s

	public function beforeAll(){
		defineCache();
        // Setup code if needed
        cacheName = "testRedis";
        keyName = "test:intKey";
        initialValue = 100;
        redisCommand(arguments: ["SET", keyName, initialValue], cache: cacheName);
    
	}

	public function afterAll(){
		application action="update" caches={};
	}

	private string function defineCache(){
		var redis = server.getDatasource("redis");
		if ( structCount(redis) eq 0 )
			throw "Redis is not configured?";
		var caches ={
			"testRedis":{
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
				"readOnly":false,
				"storage":false,
				"default":""
			}
		};

		application action="update" caches=#caches#;
		return true;	
	}


	function run( testResults , testBox ) {
		describe( "Redis tests", function() {
			




		});
	}
}