component {
	this.name = "ldev-6046-redis-" & hash( getCurrentTemplatePath() );
	this.sessionManagement = true;
	this.sessionTimeout = createTimeSpan( 0, 0, 5, 0 );
	this.setClientCookies = true;

	redis = server.getDatasource( "redis" );
	version = server.system.environment.EXTENSION_VERSION;

	this.cache.connections[ "ldev6046SessionRedis" ] = {
		class: "lucee.extension.io.cache.redis.simple.RedisCache",
		maven: "org.lucee:redis:#version#",
		custom: {
			minIdle: 8,
			maxTotal: 40,
			maxIdle: 24,
			host: redis.server,
			port: redis.port,
			socketTimeout: 2000,
			liveTimeout: 3600000,
			idleTimeout: 60000,
			timeToLiveSeconds: 0,
			testOnBorrow: true
		},
		readOnly: false,
		storage: true,
		default: ""
	};

	this.sessionStorage = "ldev6046SessionRedis";
	this.sessionCluster = isNull( url.sessionCluster ) ? true : url.sessionCluster;

	public function onRequestStart() {
		setting requesttimeout=10 showdebugOutput=false;
	}

	function onSessionStart() {
		systemOutput( "------onSessionStart (redis)", true );
	}

	function onSessionEnd( sessionScope, applicationScope ) {
		systemOutput( "------onSessionEnd (redis)", true );
	}
}
