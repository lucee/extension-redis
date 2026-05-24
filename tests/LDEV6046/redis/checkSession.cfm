<cfscript>
	// Check whether a session key still lives in the Redis cache after sessionInvalidate().
	// The session-storage key format includes the cfid; we list all keys and look for it.

	cfidToCheck = isNull( url.cfid ) ? "" : url.cfid;

	allKeys = cacheGetAllIds( "*", "ldev6046SessionRedis" );

	matchingKeys = [];
	for ( key in allKeys ) {
		if ( findNoCase( cfidToCheck, key ) ) {
			arrayAppend( matchingKeys, key );
		}
	}

	result = {
		cfidChecked: cfidToCheck,
		sessionExists: arrayLen( matchingKeys ) gt 0,
		matchingKeys: matchingKeys,
		allKeyCount: arrayLen( allKeys )
	};

	echo( serializeJSON( result ) );
</cfscript>
