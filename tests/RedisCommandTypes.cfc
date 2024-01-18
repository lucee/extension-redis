component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

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


	function run() {
        describe("My Redis Extension Tests", function() {

            it("can increment Redis key value using Float", function() {
                var Float = createObject("java", "java.lang.Float");
                var f = Float.parseFloat("100");
                redisCommand(arguments: ["INCRBY", keyName, f], cache: cacheName);
                var result = redisCommand(arguments: ["GET", keyName], cache: cacheName);
                expect(result).toBe(200); // Or your expected result
            });

            it("can increment Redis key value using Double", function() {
                var Double = createObject("java", "java.lang.Double");
                var d = Double.parseDouble("100");
                redisCommand(arguments: ["INCRBY", keyName, d], cache: cacheName);
                var result = redisCommand(arguments: ["GET", keyName], cache: cacheName);
                expect(result).toBe(200); // Or your expected result
            });

            it("can increment Redis key value using Short", function() {
                var Short = createObject("java", "java.lang.Short");
                var s = Short.parseShort("100");
                redisCommand(arguments: ["INCRBY", keyName, s], cache: cacheName);
                var result = redisCommand(arguments: ["GET", keyName], cache: cacheName);
                expect(result).toBe(200); // Or your expected result
            });

            it("can increment Redis key value using Integer", function() {
                var Integer = createObject("java", "java.lang.Integer");
                var i = Integer.parseInt("100");
                redisCommand(arguments: ["INCRBY", keyName, i], cache: cacheName);
                var result = redisCommand(arguments: ["GET", keyName], cache: cacheName);
                expect(result).toBe(200); // Or your expected result
            });

            it("can increment Redis key value using Long", function() {
                var Long = createObject("java", "java.lang.Long");
                var l = Long.parseLong("100");
                var res = redisCommand(arguments: ["INCRBY", keyName, l], cache: cacheName);
                var result = redisCommand(arguments: ["GET", keyName], cache: cacheName);
                expect(result).toBe(200); // Or your expected result
            });

        });
    }
}