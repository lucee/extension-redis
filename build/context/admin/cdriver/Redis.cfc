component extends="Cache" {
	variables.fields = [
		group("Connection Mode","Choose the Redis deployment topology")
		,field(displayName = "Connection Mode",
			name = "connectionMode",
			defaultValue = "standalone",
			required = true,
			description = "The Redis deployment mode: standalone (single server), sentinel (high availability with automatic failover), or cluster (distributed with sharding).",
			type = "select",
			values = "standalone,sentinel,cluster"
		)

		,group("Standalone Configuration","Settings for single Redis server (connectionMode=standalone)")
		,field(displayName = "Host",
			name = "host",
			defaultValue = "localhost",
			required = true,
			description = "Host name or IP address of the server you want to connect to.",
			type = "text"
			)
		,field(displayName = "Port",
			name = "port",
			defaultValue = 6379,
			required = true,
			description = "Port Redis is listening on.",
			type = "text"
		)

		,group("Sentinel Configuration","Settings for Redis Sentinel (connectionMode=sentinel)")
		,field(displayName = "Sentinel Master Name",
			name = "sentinelMasterName",
			defaultValue = "mymaster",
			required = false,
			description = "The name of the master set configured in Sentinel.",
			type = "text"
		)
		,field(displayName = "Sentinel Nodes",
			name = "sentinelNodes",
			defaultValue = "",
			required = false,
			description = "Comma-separated list of Sentinel nodes in host:port format (e.g., 'sentinel1:26379,sentinel2:26379,sentinel3:26379').",
			type = "text"
		)

		,group("Cluster Configuration","Settings for Redis Cluster (connectionMode=cluster)")
		,field(displayName = "Cluster Nodes",
			name = "clusterNodes",
			defaultValue = "",
			required = false,
			description = "Comma-separated list of cluster nodes in host:port format (e.g., 'node1:6379,node2:6379,node3:6379').",
			type = "text"
		)

		,group("Security","")
		,field(displayName = "SSL",
			name = "ssl",
			defaultValue = false,
			required = false,
			description = "Establish an SSL connection to the Redis Server",
			type = "checkbox",
			values = true
		)

		,group("Direct Authentication","Authentication Credentials")
		,field(displayName = "Username",
			name = "username",
			defaultValue = "",
			required = false,
			description = "Username (if) necessary to connect.",
			type = "text"
		)
		,field(displayName = "Password",
			name = "password",
			defaultValue = "",
			required = false,
			description = "Password (if) necessary to connect.",
			type = "text"
		)

		,group("Authentication via AWS Secret Manager","You can get the authentication credentials from AWS Secret Manager")
		,field(displayName = "Secret Name",
			name = "secretName",
			defaultValue = "",
			required = false,
			description = "Name of the secret within the AWS Secret Manager",
			type = "text"
		)
		,field(displayName = "Region",
			name = "region",
			defaultValue = "",
			required = false,
			description = "A Region is a named set of AWS resources in the same geographical area. An example of a Region is us-east-1, which is the US East (N. Virginia) Region.",
			type = "text"
		)
		,field(displayName = "AccessKeyId",
			name = "accessKeyId",
			defaultValue = "",
			required = false,
			description = "The accessKeyId is not required if you wanna access a SecretManager on an AWS EC2 instance. 
			If the accessKeyId is not set and you're not using an EC2 instance, the Secret Manager Client will look for the accessKeyId in the environment variables and system properties.",
			type = "text"
		)
		,field(displayName = "SecretKey",
			name = "secretKey",
			defaultValue = "",
			required = false,
			description = "Corresponding secretKey for the accessKeyId, for more details see accessKeyId above.",
			type = "text"
		)

		,group("Key Namespace","Isolate keys from other applications sharing the same Redis instance")
		,field(displayName = "Key Prefix",
			name = "keyPrefix",
			defaultValue = "",
			required = false,
			description = "Optional prefix for all cache keys (e.g., 'myapp:cache:'). Enables namespace isolation when multiple applications share the same Redis instance. A colon separator will be automatically appended if not present.",
			type = "text"
		)

		,group("Session Locking","Distributed locking for safe multi-server session storage")
		,field(displayName = "Enable Session Locking",
			name = "sessionLockingEnabled",
			defaultValue = false,
			required = false,
			description = "Enable distributed locking for session operations to prevent race conditions in multi-server environments. Disabled by default for backward compatibility.",
			type = "checkbox",
			values = true
		)
		,field(displayName = "Lock Expiration (seconds)",
			name = "sessionLockExpiration",
			defaultValue = 30,
			required = false,
			description = "Maximum time in seconds a lock can be held before auto-expiring. Prevents orphaned locks if a server crashes.",
			type = "text"
		)
		,field(displayName = "Lock Timeout (ms)",
			name = "sessionLockTimeout",
			defaultValue = 5000,
			required = false,
			description = "Maximum time in milliseconds to wait when acquiring a lock before giving up.",
			type = "text"
		)

		,group("Object Handling","Control how cached objects are returned")
		,field(displayName = "Always Clone Objects",
			name = "alwaysClone",
			defaultValue = false,
			required = false,
			description = "When enabled, objects are cloned (re-serialized) on every get operation. This prevents modifications to returned objects from affecting the cached values, but adds overhead. Useful for debugging reference issues.",
			type = "checkbox",
			values = true
		)

		,group("Time Management","")
		,field("Time to live in seconds","timeToLiveSeconds","0",true,"Sets the timeout to live for an element before it expires. If all fields are set to 0 the element live as long the server live.","time")
		,field(displayName = "Idle Timeout (seconds)",
			name = "idleTimeoutSeconds",
			defaultValue = 0,
			required = false,
			description = "Idle timeout in seconds. When touchOnAccess is enabled and this value is > 0, the TTL will be reset to this value on each read (sliding expiration). Set to 0 to disable.",
			type = "text"
		)
		,field(displayName = "Touch on Access",
			name = "touchOnAccess",
			defaultValue = false,
			required = false,
			description = "When enabled, resets the TTL to idleTimeoutSeconds on each cache read. This implements sliding expiration where entries stay alive as long as they are being accessed.",
			type = "checkbox",
			values = true
		)
		

		,group("Pool","Connection to Redis are handled within a Pool, the following settings allows you to configure this pool.")
		,field(displayName = "Max Total",
			name = "maxTotal",
			defaultValue = 24,
			required = true,
			description = "The cap on the total number of active connections in the pool.",
			type = "text"
		)
		,field(displayName = "Max Low Priority",
			name = "maxLowPriority",
			defaultValue = 0,
			required = false,
			description = "The limitation of connection available for the function RedisCommandLowPriority. Any number lower than 0 will be substracted from max total connections.",
			type = "text"
		)
		,field(displayName = "Max Idle",
			name = "maxIdle",
			defaultValue = 8,
			required = true,
			description = "The cap on the number of idle connections in the pool.",
			type = "text"
		)
		,field(displayName = "Min Idle",
			name = "minIdle",
			defaultValue = 0,
			required = true,
			description = "the target for the minimum number of idle connections to maintain in the pool.",
			type = "text"
		)

		,field(displayName = "Live Timeout",
			name = "liveTimeout",
			defaultValue = 3600000,
			required = false,
			description = "The maximal live span of a connection inside the pool in milliseconds.",
			type = "text"
		)
		,field(displayName = "Idle Timeout",
			name = "idleTimeout",
			defaultValue = 300000,
			required = false,
			description = "Timeout in milliseconds for connections that are idling.",
			type = "text"
		)
		,field(displayName = "Connection Timeout",
			name = "connectionTimeout",
			defaultValue = 5000,
			required = false,
			description = "Timeout in milliseconds to aquire a connection from pool. This becomes necessary in case the pool is exhausted",
			type = "text"
		)
		,field(displayName = "Socket Timeout",
			name = "socketTimeout",
			defaultValue = 2000,
			required = false,
			description = "Timeout in milliseconds to create the connection (create a socket connection to Redis).",
			type = "text"
		)
	];

	public string function getClass() {
		return "{class}";
	}

	public string function getLabel() {
		return "{label}";
	}

	public string function getDescription() {
		return "{desc}";
	}
}
