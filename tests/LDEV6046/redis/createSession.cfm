<cfscript>
	if ( structKeyExists( url, "testValue" ) ) {
		session.testValue = url.testValue;
	}

	session.user_id = createUUID();

	result = {
		sessionId: session.sessionid,
		cfid: session.cfid,
		cftoken: session.cftoken,
		user_id: session.user_id,
		testValue: isNull( session.testValue ) ? "" : session.testValue
	};

	echo( serializeJSON( result ) );
</cfscript>
