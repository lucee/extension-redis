component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	variables.sleepInterval = 10; // ms, used for sleep()s

	public function beforeAll(){
		defineCache();
	}

	public function afterAll(){
		application action="update" caches={};
	}

	private string function defineCache(){
		var redis = server.getDatasource("redis");
		if ( structCount(redis) eq 0 )
			throw "Redis is not configured?";
		var caches ={
			 "querybuffer":{
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
			"queues":{
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
			},
			"sessionStorage":{
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
			},
			"queues":{
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

	private function printCacheConns() {
	    var pc=getPageContext();
	    var c=pc.getConfig();
	    var conns=c.getCacheConnections();
	    loop struct=conns index="local.name" item="local.cc" {
	        debug(label:name,var:cc.getCustom());
	    }
	}

	function run( testResults , testBox ) {
		describe( "Redis tests", function() {
			
			it( title='test the Redis Cache cachePut,cacheidexists,cacheGet,cacheDelete  [queryBuffer] ', body=function( currentSpec ) {
				var key="k"&hash(createUniqueId()&":"&server.lucee.version,"quick");	
				var val="Test Value from Test case";
				try {
					cachePut(key:key,value:val,cacheName:"querybuffer");
					sleep(variables.sleepInterval);
					expect(cacheidexists(key:key,cacheName:"querybuffer")).toBe(true);
					expect(cacheGet(key:key,cacheName:"querybuffer")).toBe(val);
					cacheDelete(key:key,cacheName:"querybuffer");
					sleep(variables.sleepInterval);
					expect(cacheidexists(key:key,cacheName:"querybuffer")).toBe(false);
				}
				catch(e) {
					printCacheConns();
					rethrow;
				} 
			});

			it( title='test the Redis Cache cachePut,cacheidexists,cacheGetAll,cacheGetAllIds,cacheDelete  [queryBuffer] ', body=function( currentSpec ) {
				var key="k"&hash(createUniqueId()&":"&server.lucee.version,"quick");	
				var val="Test Value from Test case";
				
				cachePut(key:key,value:val,cacheName:"querybuffer");
				sleep(variables.sleepInterval);
				expect(cacheidexists(key:key,cacheName:"querybuffer")).toBe(true);
				expect(cacheGetAllIds(filter:key,cacheName:"querybuffer")[1]).toBe(key);
				expect(cacheGetAll(filter:key,cacheName:"querybuffer")[key]).toBe(val);

				cacheDelete(key:key,cacheName:"querybuffer");
				sleep(variables.sleepInterval);
				expect(cacheidexists(key:key,cacheName:"querybuffer")).toBe(false);
			});

			it( title='test the Redis Cache cachePut,cacheidexists,cacheGet,cacheDelete  [queues] ', body=function( currentSpec ) {
				var key="k"&hash(createUniqueId()&":"&server.lucee.version,"quick");	
				var val="Test Value from Test case";
				try {
					cachePut(key:key,value:val,cacheName:"queues");
					sleep(variables.sleepInterval);
					expect(cacheidexists(key:key,cacheName:"queues")).toBe(true);
					expect(cacheGet(key:key,cacheName:"queues")).toBe(val);
					cacheDelete(key:key,cacheName:"queues");
					sleep(variables.sleepInterval);
					expect(cacheidexists(key:key,cacheName:"queues")).toBe(false);
				}
				catch(e) {
					printCacheConns();
					rethrow;
				} 
			});

			it( title='test the Redis Cache cachePut,cacheidexists,cacheGet,cacheDelete  [sessionstorage] ', body=function( currentSpec ) {
				var key="k"&hash(createUniqueId()&":"&server.lucee.version,"quick");	
				var val="Test Value from Test case";
				try {
					cachePut(key:key,value:val,cacheName:"sessionstorage");
					sleep(variables.sleepInterval);
					expect(cacheidexists(key:key,cacheName:"sessionstorage")).toBe(true);
					expect(cacheGet(key:key,cacheName:"sessionstorage")).toBe(val);
					cacheDelete(key:key,cacheName:"sessionstorage");
					sleep(variables.sleepInterval);
					expect(cacheidexists(key:key,cacheName:"sessionstorage")).toBe(false);
				}
				catch(e) {
					printCacheConns();
					rethrow;
				} 
			});

		});
	}
}