component {
    this.name = "RedisSessionTest";
    this.sessionManagement = true;
    this.sessionTimeout = createTimeSpan(0, 0, 30, 0);

    // Use Redis for session storage
    this.sessionStorage = "redisSession";
    this.sessionCluster = true;

    // Configure Redis cache for sessions
    this.cache = {
        "redisSession": {
            class: "lucee.extension.io.cache.redis.RedisCache",
            bundleName: "redis.extension",
            custom: {
                host: server.system.environment.REDIS_HOST ?: "redis",
                port: server.system.environment.REDIS_PORT ?: 6379,
                socketTimeout: 2000,
                liveTimeout: 3600000,
                idleTimeout: 60000,
                timeToLiveSeconds: 1800,
                minIdle: 8,
                maxTotal: 40,
                maxIdle: 24,
                // New production readiness features
                keyPrefix: "session:",           // Namespace isolation
                sessionLockingEnabled: true,    // Enable distributed locking
                sessionLockExpiration: 30,      // Lock expires after 30s
                sessionLockTimeout: 5000        // Wait up to 5s for lock
            },
            storage: true,
            default: ""
        }
    };

    function onApplicationStart() {
        application.serverID = createUUID();
        return true;
    }

    function onSessionStart() {
        session.created = now();
        session.serverID = application.serverID;
        session.accessCount = 0;
    }
}
