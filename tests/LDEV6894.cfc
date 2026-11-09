component extends="org.lucee.cfml.test.LuceeTestCase" labels="redis" {

	// LDEV-6894: a query stored in a Redis cache / Redis session storage came back as an
	// array of structs. The BSON serializer treated a query like any other collection and
	// wrote its rows as structs into a BSON array, without any marker to restore the query.

	variables.cacheName = "ldev6894";

	function isNotSupported() {
		var redis = server.getDatasource( "redis" );
		return structCount( redis ) eq 0;
	}

	function beforeAll() {
		if ( isNotSupported() ) return;
		var redis = server.getDatasource( "redis" );
		application action="update" caches={
			"#variables.cacheName#": {
				"class": "lucee.extension.io.cache.redis.RedisCache",
				"maven": "org.lucee:redis:#server.system.environment.EXTENSION_VERSION#",
				"custom": {
					"host": redis.server,
					"port": redis.port,
					"socketTimeout": 2000,
					"timeToLiveSeconds": 0,
					// read every value back from Redis, not from the in-process near cache
					"nearCache": false
				},
				"readOnly": false,
				"storage": false,
				"default": ""
			}
		};
	}

	function afterAll() {
		application action="update" caches={};
	}

	function run( testResults, testBox ) {
		describe( "LDEV-6894: queries stored in Redis come back as queries", function() {

			it( title="a query inside a struct round-trips as a query (columns, types, rows)", skip=isNotSupported(), body=function( currentSpec ) {
				var id = createId();
				var qry = createQuery();
				cachePut( id=id, value={ qry: qry, label: "x" }, cacheName=variables.cacheName );

				var res = cacheGet( id=id, cacheName=variables.cacheName );
				expect( res.label ).toBe( "x" );
				expect( isQuery( res.qry ) ).toBeTrue( "expected a query, got [#res.qry.getClass().getName()#]" );
				expect( isArray( res.qry ) ).toBeFalse();
				assertSameQuery( qry, res.qry );

				cacheRemove( ids=id, cacheName=variables.cacheName );
			} );

			it( title="an empty query keeps its columns", skip=isNotSupported(), body=function( currentSpec ) {
				var id = createId();
				var qry = queryNew( "id,name", "integer,varchar" );
				cachePut( id=id, value={ qry: qry }, cacheName=variables.cacheName );

				var res = cacheGet( id=id, cacheName=variables.cacheName );
				expect( isQuery( res.qry ) ).toBeTrue();
				expect( res.qry.recordcount ).toBe( 0 );
				expect( listSort( res.qry.columnList, "textnocase" ) ).toBe( "ID,NAME" );

				cacheRemove( ids=id, cacheName=variables.cacheName );
			} );

			it( title="queries nested in arrays and structs, and null / complex cells", skip=isNotSupported(), body=function( currentSpec ) {
				var id = createId();
				var qry = queryNew( "id,data", "integer,object" );
				queryAddRow( qry, { id: 1, data: { a: 1, b: [ 1, 2 ] } } );
				queryAddRow( qry, { id: 2 } );
				var value = { list: [ createQuery(), qry ], nested: { deeper: createQuery() } };
				cachePut( id=id, value=value, cacheName=variables.cacheName );

				var res = cacheGet( id=id, cacheName=variables.cacheName );
				expect( isQuery( res.list[ 1 ] ) ).toBeTrue();
				expect( isQuery( res.list[ 2 ] ) ).toBeTrue();
				expect( isQuery( res.nested.deeper ) ).toBeTrue();
				assertSameQuery( createQuery(), res.list[ 1 ] );
				assertSameQuery( createQuery(), res.nested.deeper );

				var q2 = res.list[ 2 ];
				expect( q2.recordcount ).toBe( 2 );
				expect( q2.id[ 1 ] ).toBe( 1 );
				expect( q2.data[ 1 ].a ).toBe( 1 );
				expect( q2.data[ 1 ].b ).toBe( [ 1, 2 ] );
				expect( q2.id[ 2 ] ).toBe( 2 );
				expect( len( q2.data[ 2 ] ?: "" ) ).toBe( 0 );

				cacheRemove( ids=id, cacheName=variables.cacheName );
			} );

			it( title="a query stored directly still round-trips", skip=isNotSupported(), body=function( currentSpec ) {
				var id = createId();
				var qry = createQuery();
				cachePut( id=id, value=qry, cacheName=variables.cacheName );

				var res = cacheGet( id=id, cacheName=variables.cacheName );
				expect( isQuery( res ) ).toBeTrue();
				assertSameQuery( qry, res );

				cacheRemove( ids=id, cacheName=variables.cacheName );
			} );

			it( title="a query stored in a Redis backed session comes back as a query", skip=isNotSupported(), body=function( currentSpec ) {
				var uri = createURI( "LDEV6894" );

				var result1 = _InternalRequest( template="#uri#session/store.cfm" );
				var data1 = deserializeJSON( result1.filecontent.trim() );
				expect( data1.stored ).toBeTrue();

				var result2 = _InternalRequest(
					template = "#uri#session/load.cfm",
					cookies = { cfid: data1.cfid, cftoken: data1.cftoken }
				);
				var data2 = deserializeJSON( result2.filecontent.trim() );
				expect( data2.sessionId ).toBe( data1.sessionId );
				expect( data2.isQuery ).toBeTrue( "session.qry came back as [#data2.type#]" );
				expect( data2.isArray ).toBeFalse();
				expect( data2.recordcount ).toBe( 3 );
				expect( listSort( data2.columnList, "textnocase" ) ).toBe( "CREATED,ID,NAME,PRICE" );
				expect( data2.names ).toBe( "a,b,c" );
				expect( data2.ids ).toBe( "1,2,3" );
			} );

		} );
	}

	private query function createQuery() {
		return queryNew(
			"id,name,created,price",
			"integer,varchar,timestamp,double",
			[
				[ 1, "a", createDateTime( 2026, 1, 2, 3, 4, 5 ), 1.5 ],
				[ 2, "b", createDateTime( 2026, 2, 3, 4, 5, 6 ), 2.25 ],
				[ 3, "c", createDateTime( 2026, 3, 4, 5, 6, 7 ), 3 ]
			]
		);
	}

	private void function assertSameQuery( required query expected, required query actual ) {
		expect( actual.recordcount ).toBe( expected.recordcount );
		// same column names, same case and order
		expect( queryColumnArray( actual ) ).toBe( queryColumnArray( expected ) );
		// same column types
		var expectedTypes = {};
		loop array=getMetaData( expected ) item="local.col" {
			expectedTypes[ col.name ] = col.typeName;
		}
		loop array=getMetaData( actual ) item="local.col" {
			expect( col.typeName ).toBe( expectedTypes[ col.name ], "type of column [#col.name#]" );
		}
		// same data
		loop query=expected {
			var row = expected.currentrow;
			loop array=queryColumnArray( expected ) item="local.column" {
				var e = expected[ column ][ row ];
				var a = actual[ column ][ row ];
				if ( isDate( e ) ) expect( dateCompare( a, e ) ).toBe( 0, "[#column#][#row#]" );
				else expect( a ).toBe( e, "[#column#][#row#]" );
			}
		}
	}

	private string function createId() {
		return "ldev6894-" & lcase( hash( createUUID(), "quick" ) );
	}

	private string function createURI( string calledName ) {
		var path = getDirectoryFromPath( getCurrentTemplatePath() );
		return contractPath( path & calledName ) & "/";
	}

}
