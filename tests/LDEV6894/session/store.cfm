<cfscript>
	session.qry = queryNew(
		"id,name,created,price",
		"integer,varchar,timestamp,double",
		[
			[ 1, "a", createDateTime( 2026, 1, 2, 3, 4, 5 ), 1.5 ],
			[ 2, "b", createDateTime( 2026, 2, 3, 4, 5, 6 ), 2.25 ],
			[ 3, "c", createDateTime( 2026, 3, 4, 5, 6, 7 ), 3 ]
		]
	);
	echo( serializeJSON( {
		stored: isQuery( session.qry ),
		sessionId: session.sessionid,
		cfid: session.cfid,
		cftoken: session.cftoken
	} ) );
</cfscript>
