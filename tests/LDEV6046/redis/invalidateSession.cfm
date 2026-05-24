<cfscript>
	oldSessionId = session.sessionid;
	oldCfid = session.cfid;
	oldCftoken = session.cftoken;

	sessionInvalidate();

	result = {
		oldSessionId: oldSessionId,
		oldCfid: oldCfid,
		oldCftoken: oldCftoken,
		newSessionId: session.sessionid,
		newCfid: session.cfid,
		newCftoken: session.cftoken,
		hasTestValue: structKeyExists( session, "testValue" ),
		hasUserId: structKeyExists( session, "user_id" )
	};

	echo( serializeJSON( result ) );
</cfscript>
