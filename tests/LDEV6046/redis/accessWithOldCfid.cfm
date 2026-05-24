<cfscript>
	result = {
		sessionId: session.sessionid,
		hasUserId: structKeyExists( session, "user_id" ),
		userId: isNull( session.user_id ) ? "" : session.user_id,
		hasTestValue: structKeyExists( session, "testValue" ),
		testValue: isNull( session.testValue ) ? "" : session.testValue
	};

	echo( serializeJSON( result ) );
</cfscript>
