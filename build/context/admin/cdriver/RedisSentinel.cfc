component extends="Cache" {
	variables.fields = [
		field(displayName = "Master name",
			name = "masterName",
			defaultValue = "",
			required = true,
			description = "Sentinel master name",
			type = "text"
			)
		,field(displayName = "Sentinels",
			name = "sentinels",
			defaultValue = "localhost:26379",
			required = true,
			description = "Sentinels",
			type = "textarea"
		)
		,field(
			displayName = "Namespace",
			name = "namespace",
			defaultValue = "lucee:cache",
			required = false,
			description = "Keys namespace. If using the same Redis instance for more than one cache use a unique namespace to avoid keys names clashing."
		)

		,group("Authentication","Authentication Credentials if necessary.")
		,field(displayName = "Password",
			name = "password",
			defaultValue = "",
			required = false,
			description = "Password (if) necessary to connect.",
			type = "text"
		)

		,group("Time Management","")
		,field("Time to live in seconds","timeToLiveSeconds","0",true,"Sets the timeout to live for an element before it expires. If all fields are set to 0 the element live as long the server live.","time")
		
		,group("Pool","Connection to Redis are handled within a Pool, the following settings allows you to configure this pool.")
		,field(displayName = "Max Total",
			name = "maxTotal",
			defaultValue = 8,
			required = true,
			description = "The cap on the total number of active connections in the pool.",
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
