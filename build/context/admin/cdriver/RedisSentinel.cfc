<cfcomponent extends="Cache">
	<cfset fields = array(
		field(displayName = "Master name",
			name = "masterName",
			defaultValue = "",
			required = true,
			description = "Sentinel master name",
			type = "text"
			),
		field(displayName = "Sentinels",
			name = "sentinels",
			defaultValue = "localhost:26379",
			required = true,
			description = "Sentinels",
			type = "textarea"
		),
		field(
			displayName = "Namespace",
			name = "namespace",
			defaultValue = "lucee:cache",
			required = true,
			description = "Keys namespace. Be sure that any cache use a unique namespace to avoid keys names clashing."
		)
	)>

	<cffunction name="getClass" returntype="string">
		<cfreturn "{class}">
	</cffunction>

	<cffunction name="getLabel" returntype="string" output="no">
		<cfreturn "{label}">
	</cffunction>

	<cffunction name="getDescription" returntype="string" output="no">
		<cfreturn "{desc}">
	</cffunction>

</cfcomponent>
