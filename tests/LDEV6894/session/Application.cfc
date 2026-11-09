component {
	this.name = "ldev-6894-redis-" & hash( getCurrentTemplatePath() );
	this.sessionManagement = true;
	this.sessionTimeout = createTimeSpan( 0, 0, 5, 0 );
	this.setClientCookies = true;

	redis = server.getDatasource( "redis" );
	version = server.system.environment.EXTENSION_VERSION;

	this.cache.connections[ "ldev6894SessionRedis" ] = {
		class: "lucee.extension.io.cache.redis.simple.RedisCache",
		maven: "org.lucee:redis:#version#",
		custom: {
			host: redis.server,
			port: redis.port,
			socketTimeout: 2000,
			timeToLiveSeconds: 0
		},
		readOnly: false,
		storage: true,
		default: ""
	};

	this.sessionStorage = "ldev6894SessionRedis";
	// reload the session from Redis on every request
	this.sessionCluster = true;

	public function onRequestStart() {
		setting requesttimeout=10 showdebugOutput=false;
	}
}
