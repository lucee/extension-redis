component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	// LDEV-6046 (Redis variant): sessionInvalidate() with Redis-backed session storage.
	//
	// The core fix for LDEV-6046 lives in ScopeContext.removeCFSessionScope() and is
	// storage-type agnostic. The MySQL test (lucee7.1/test/tickets/LDEV6046.cfc) covers the
	// database-storage path. This test covers the Redis-storage path to confirm the same
	// fix carries through.
	//
	// Also bears on GH lucee/extension-redis#5 (sessionInvalidate doesn't remove the key
	// from Redis).

	function isNotSupported() {
		var redis = server.getDatasource( "redis" );
		return structCount( redis ) eq 0;
	}

	function run( testResults, testBox ) {
		describe( "LDEV-6046: sessionInvalidate() with Redis session storage", function() {

			it( title="sessionInvalidate() should de-activate old session so it cannot be reused", skip=isNotSupported(), body=function( currentSpec ) {
				var uri = createURI( "LDEV6046" );

				var result1 = _InternalRequest(
					template = "#uri#redis/createSession.cfm"
				);
				var data1 = deserializeJSON( result1.filecontent.trim() );
				expect( data1.sessionId ).notToBeEmpty();
				expect( data1.user_id ).notToBeEmpty();

				var originalSessionId = data1.sessionId;
				var originalCfid = data1.cfid;
				var originalCftoken = data1.cftoken;

				var result2 = _InternalRequest(
					template = "#uri#redis/invalidateSession.cfm",
					cookies = {
						cfid: originalCfid,
						cftoken: originalCftoken
					}
				);
				var data2 = deserializeJSON( result2.filecontent.trim() );

				expect( data2.oldSessionId ).toBe( originalSessionId );
				expect( data2.newSessionId ).notToBe( originalSessionId, "Session ID should change after sessionInvalidate()" );
				expect( data2.hasUserId ).toBeFalse( "New session should not have user_id from old session" );

				var result3 = _InternalRequest(
					template = "#uri#redis/accessWithOldCfid.cfm",
					cookies = {
						cfid: originalCfid,
						cftoken: originalCftoken
					}
				);
				var data3 = deserializeJSON( result3.filecontent.trim() );

				expect( data3.hasUserId ).toBeFalse( "Old session should not be accessible after sessionInvalidate() - user_id should not exist" );
			} );

			it( title="old session key should be removed from Redis after sessionInvalidate()", skip=isNotSupported(), body=function( currentSpec ) {
				var uri = createURI( "LDEV6046" );

				var result1 = _InternalRequest(
					template = "#uri#redis/createSession.cfm"
				);
				var data1 = deserializeJSON( result1.filecontent.trim() );
				var originalCfid = data1.cfid;
				var originalCftoken = data1.cftoken;

				_InternalRequest(
					template = "#uri#redis/invalidateSession.cfm",
					cookies = {
						cfid: originalCfid,
						cftoken: originalCftoken
					}
				);

				var result3 = _InternalRequest(
					template = "#uri#redis/checkSession.cfm",
					url = { cfid: originalCfid }
				);
				var data3 = deserializeJSON( result3.filecontent.trim() );
				expect( data3.sessionExists ).toBeFalse( "Old session key (cfid #originalCfid#) should be removed from Redis. Matching keys: #serializeJSON( data3.matchingKeys )#" );
			} );

		} );

		describe( "LDEV-6046: sessionInvalidate() with Redis session storage (sessionCluster=false)", function() {

			it( title="sessionInvalidate() should de-activate old session with sessionCluster=false", skip=isNotSupported(), body=function( currentSpec ) {
				var uri = createURI( "LDEV6046" );

				var result1 = _InternalRequest(
					template = "#uri#redis/createSession.cfm",
					url = { sessionCluster: false }
				);
				var data1 = deserializeJSON( result1.filecontent.trim() );
				expect( data1.sessionId ).notToBeEmpty();
				expect( data1.user_id ).notToBeEmpty();

				var originalSessionId = data1.sessionId;
				var originalCfid = data1.cfid;
				var originalCftoken = data1.cftoken;

				var result2 = _InternalRequest(
					template = "#uri#redis/invalidateSession.cfm",
					url = { sessionCluster: false },
					cookies = {
						cfid: originalCfid,
						cftoken: originalCftoken
					}
				);
				var data2 = deserializeJSON( result2.filecontent.trim() );

				expect( data2.oldSessionId ).toBe( originalSessionId );
				expect( data2.newSessionId ).notToBe( originalSessionId, "Session ID should change after sessionInvalidate()" );
				expect( data2.hasUserId ).toBeFalse( "New session should not have user_id from old session" );

				var result3 = _InternalRequest(
					template = "#uri#redis/accessWithOldCfid.cfm",
					url = { sessionCluster: false },
					cookies = {
						cfid: originalCfid,
						cftoken: originalCftoken
					}
				);
				var data3 = deserializeJSON( result3.filecontent.trim() );

				expect( data3.hasUserId ).toBeFalse( "Old session should not be accessible after sessionInvalidate() with sessionCluster=false" );
			} );

		} );
	}

	private string function createURI( string calledName ) {
		var path = getDirectoryFromPath( getCurrentTemplatePath() );
		return contractPath( path & calledName ) & "/";
	}

}
