component extends="Cache" {
	variables.fields = [
		field(displayName = "Host",
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

		,group("Time Management","")
		,field("Time to live in seconds","timeToLiveSeconds","0",true,"Sets the timeout to live for an element before it expires. If all fields are set to 0 the element live as long the server live.","time")
		

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
			required = true,
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
		,field(displayName = "Timeout",
			name = "timeout",
			defaultValue = 2000,
			required = true,
			description = "Timeout in milliseconds for connections that are idling.",
			type = "text"
		)
		,field(displayName = "Database index",
			name = "databaseIndex",
			defaultValue = -1,
			required = false,
			description = "Define a specific Database index.",
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
