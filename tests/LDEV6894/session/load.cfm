<cfscript>
	qry = session.qry ?: "";
	result = {
		sessionId: session.sessionid,
		type: isNull( session.qry ) ? "null" : session.qry.getClass().getName(),
		isQuery: isQuery( qry ),
		isArray: isArray( qry ),
		recordcount: isQuery( qry ) ? qry.recordcount : -1,
		columnList: isQuery( qry ) ? qry.columnList : "",
		names: isQuery( qry ) ? valueList( qry.name ) : "",
		ids: isQuery( qry ) ? valueList( qry.id ) : ""
	};
	echo( serializeJSON( result ) );
</cfscript>
