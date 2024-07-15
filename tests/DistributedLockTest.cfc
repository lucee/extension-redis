<cfcomponent extends="org.lucee.cfml.test.LuceeTestCase" labels="redis">

	<cfscript>
		public void function beforeAll(){
			defineCache();
		}

		public void function afterAll(){
			application action="update" caches={};
		}
	
		private string function defineCache(){
			var redis = server.getDatasource("redis");
			if ( structCount(redis) eq 0 )
				throw "Redis is not configured?";

			admin
				action="updateCacheConnection"
				type="server"
				password=server.SERVERADMINPASSWORD
				class="lucee.extension.io.cache.redis.simple.RedisCache"
				bundleName="org.lucee.redis.extension"
				name="distLock"
				custom={
					"minIdle":8,
					"maxTotal":40,
					"maxIdle":24,
					"host":redis.server,
					"port":redis.port,
					"socketTimeout":2000,
					"liveTimeout":3600000,
					"idleTimeout":60000,
					"timeToLiveSeconds":0,
					"testOnBorrow":true,
					"rnd":1
				},
				default=""
				readonly=false
				storage=false
				remoteClients="";
			return true;
		}
	</cfscript>

	<cffunction name="testNotLockedSingleAccess">
		<cfsavecontent  variable="local.res" trim=true>
			<cfDistributedLock name="testNotLocked-#createUniqueId()#" cache="distLock" timeout=1.1111 logontimeout=false>
				output
			</cfDistributedLock>
		</cfsavecontent>
		<cfset expect(res).ToBe("output")>
	</cffunction>

	<cffunction name="testAlreadyLockedNoErrorSingleAccess">
		<cfset local.lockName="testAlreadyLockedNoError-#createUniqueId()#">
		
		<cfthread lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 logontimeout=false>
				<cfset sleep(5000)>
			</cfDistributedLock>
        </cfthread>
		
		<!--- waiting a little bit so we are sure the lock is set --->
    	<cfset sleep(100)>
		
		<!--- now we try to aquire the same lock --->
		<cfsavecontent  variable="local.res" trim=true>
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 throwontimeout=false logontimeout=false>
				output
			</cfDistributedLock>
		</cfsavecontent>
		<cfset expect(res).ToBe("")>
	</cffunction>

	<cffunction name="testAlreadyLockedWithErrorSingleAccess">
		<cfset local.lockName="testAlreadyLockedNoError-#createUniqueId()#">
		
		<cfthread lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 logontimeout=false>
				<cfset sleep(5000)>
			</cfDistributedLock>
        </cfthread>
		
		<!--- waiting a little bit so we are sure the lock is set --->
    	<cfset sleep(100)>
		
		<!--- now we try to aquire the same lock --->
		<cfset local.fail=false>
        <cfset local.message="">
		<cftry>
			<cfsavecontent  variable="local.res" trim=true>
				<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 throwontimeout=true logontimeout=false>
					output
				</cfDistributedLock>
			</cfsavecontent>
			<cfcatch>
				<cfset local.message=cfcatch.message>
                <cfset local.fail=true>
			</cfcatch>
		</cftry>
		
		<cfscript>
			expect( fail ).ToBeTrue();
			expect( message ).toInclude("Could not aquire a distributed lock for the name");
		</cfscript>
	</cffunction>


	<cffunction name="testOneLockedOneAvailable">
		<cfset local.lockName="testAlreadyLockedNoError-#createUniqueId()#">
		
		<cfthread lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 amount=2 logontimeout=false>
				<cfset sleep(5000)>
			</cfDistributedLock>
        </cfthread>
		
		<!--- waiting a little bit so we are sure the lock is set --->
    	<cfset sleep(100)>
		
		<!--- now we try to aquire the same lock --->
		<cfsavecontent  variable="local.res" trim=true>
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 throwontimeout=false amount=2 logontimeout=false>
				output
			</cfDistributedLock>
		</cfsavecontent>
		<cfset expect(res).ToBe("output")>
	</cffunction>


	<cffunction name="testTwoLocked">
		<cfset local.lockName="testAlreadyLockedNoError-#createUniqueId()#">
		
		<cfthread lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 amount=2 logontimeout=false>
				<cfset sleep(5000)>
			</cfDistributedLock>
        </cfthread>
		<cfthread lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 amount=2 logontimeout=false>
				<cfset sleep(5000)>
			</cfDistributedLock>
        </cfthread>

		<!--- waiting a little bit so we are sure the lock is set --->
    	<cfset sleep(100)>
		
		<!--- now we try to aquire the same lock --->
		<cfsavecontent  variable="local.res" trim=true>
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 throwontimeout=false logontimeout=false amount=2>
				output
			</cfDistributedLock>
		</cfsavecontent>
		<cfset expect(res).ToBeEmpty()>
	</cffunction>


	<cffunction name="testTwoLockedButReleasedOneLater">
		<cfset local.lockName="testAlreadyLockedNoError-#createUniqueId()#">
		
		<cfthread lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 amount=2 logontimeout=false expires="10">
				<cfset sleep(3000)>
			</cfDistributedLock>
        </cfthread>
		<cfthread lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 amount=2 logontimeout=false expires="10">
				<cfset sleep(2000)>
			</cfDistributedLock>
        </cfthread>

		<!--- waiting a little bit so we are sure the lock is set --->
    	<cfset sleep(100)>
		
		<!--- now we try to aquire the same lock --->
		<cfsavecontent  variable="local.res" trim=true>
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 throwontimeout=false logontimeout=false amount=2 expires="10">
				output
			</cfDistributedLock>
		</cfsavecontent>
		<cfset expect(res).ToBeEmpty()>

		<!--- waiting a little bit so we are sure the second lock is released --->
    	<cfset sleep(1000)>
		
		<!--- now we try to open 3 locks, but we should only be able to open 1, one is still blocked --->
		<cfthread name="#lockname#1" lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 amount=1 logontimeout=false expires="10">
				<cfset sleep(1500)>
				output2
			</cfDistributedLock>
        </cfthread>
		<cfthread name="#lockname#2" lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 amount=1 logontimeout=false expires="10">
				<cfset sleep(1500)>
				output2
			</cfDistributedLock>
        </cfthread>
		<cfthread name="#lockname#3" lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 amount=1 logontimeout=false expires="10">
				<cfset sleep(1500)>
				output2
			</cfDistributedLock>
        </cfthread>
		<cfthread action="join" name="#lockname#1,#lockname#2,#lockname#3">
		<cfset local.result=cfthread["#lockname#1"].OUTPUT.trim()&cfthread["#lockname#2"].OUTPUT.trim()&cfthread["#lockname#3"].OUTPUT.trim()>
		<!--- only 1 of 3 could enter the lock --->
		<cfset expect(result).ToBe("output2")>




		<!--- let's sleep for another second, then all will be free again --->
		<cfset sleep(1000)>


		<!--- now we try to open 3 locks, but we should only be able to open 2 --->
		<cfthread name="#lockname#a" lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=0.1 amount=2 logontimeout=false expires="1">
				<cfset sleep(200)>
				output
			</cfDistributedLock>
        </cfthread>
		<cfthread name="#lockname#b" lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=0.1 amount=2 logontimeout=false expires="1">
				<cfset sleep(200)>
				output
			</cfDistributedLock>
        </cfthread>
		<cfthread name="#lockname#c" lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=0.1 amount=2 logontimeout=false expires="1">
				<cfset sleep(200)>
				output
			</cfDistributedLock>
        </cfthread>
		<cfthread action="join" name="#lockname#a,#lockname#b,#lockname#c">
		<cfset local.result=cfthread["#lockname#a"].OUTPUT.trim()&cfthread["#lockname#b"].OUTPUT.trim()&cfthread["#lockname#c"].OUTPUT.trim()>
		<!--- only 2 of 3 could enter the lock --->
		<cfset expect(result).ToBe("outputoutput")>


	</cffunction>

    <cffunction name="testBypass">
		<cfset local.lockName="testByPass-#createUniqueId()#">
		
		<cfthread lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 logontimeout=false>
				<cfset sleep(5000)>
			</cfDistributedLock>
        </cfthread>
		
		<!--- waiting a little bit so we are sure the lock is set --->
    	<cfset sleep(100)>
		
		<!--- now we try to aquire the same lock --->
		<cfsavecontent  variable="local.res" trim=true>
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 throwontimeout=false logontimeout=false bypass=true>
				output
			</cfDistributedLock>
		</cfsavecontent>
		<cfset expect(res).ToBe("output")>
	</cffunction>


	<cffunction name="testExpires">
		<cfset local.lockName="testExpires-#createUniqueId()#">
		
		<cfthread lockname="#lockname#">
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 expires="1" logontimeout=false>
				<cfset sleep(5000)>
			</cfDistributedLock>
        </cfthread>
		
		<!--- waiting a little bit so we are sure the lock is expired --->
    	<cfset sleep(1100)>
		
		<!--- now we try to aquire the same lock, it should be possible even it was not released but expired --->
		<cfsavecontent  variable="local.res" trim=true>
			<cfDistributedLock name="#lockname#" cache="distLock" timeout=1 throwontimeout=false logontimeout=false>
				output
			</cfDistributedLock>
		</cfsavecontent>
		<cfset expect(res).ToBe("output")>
	</cffunction>


	<cfscript>
		function testOneAfterTheOther() {
			var pathInc=createURI("DistributedLockTest/index.cfm");
			var name="test"&hash(createUUID());
			
			var res=_internalRequest(
				template:pathInc,
				urls:{"name":name,cache:"distLock"}
			);
			//dump(res.filecontent);
			var arr=listToArray(res.filecontent,";");
			expect(arr.len()).ToBe(4)
			expect(arr[1]).ToBe("before");
			expect(arr[2]).ToBe("inside");
			expect(arr[3]).ToBe("after");
			expect(arr[4]>=100 && arr[4]<150).ToBeTrue();
			
			var res=_internalRequest(
				template:pathInc,
				urls:{"name":name,cache:"distLock"}
			);
			//dump(res.filecontent);
			var arr=listToArray(res.filecontent,";");
			debug(arr);
			expect(arr.len()).ToBe(4)
			expect(arr[1]).ToBe("before");
			expect(arr[2]).ToBe("inside");
			expect(arr[3]).ToBe("after");
			expect(arr[4]>=100 && arr[4]<150).ToBeTrue();
		}

		function test2AtTheSameTime() {
			var pathInc=createURI("DistributedLockTest/index.cfm");
			var name="test"&hash(createUUID());
			
			debug(pathInc);
			thread name=name&"1" id=name pathInc=pathInc {
				res=_internalRequest(
					template:pathInc,
					urls:{"name":id,cache:"distLock"}
				);
				thread.arr=listToArray(res.filecontent,";");
			}
			
			sleep(50);
			thread name=name&"2" id=name pathInc=pathInc {
				res=_internalRequest(
					template:pathInc,
					urls:{"name":id,cache:"distLock"}
				);
				thread.arr=listToArray(res.filecontent,";");
			}
			thread action="join" name=[name&"1",name&"2"].toList();
			var arr=server[name];
			debug(arr.threads);
			expect(arr.threads.len()).ToBe(4);
			expect(arr.threads[1]).ToBe(arr.threads[2]);
			expect(arr.threads[3]).ToBe(arr.threads[4]);
			expect(arr.threads[1]).NotToBe(arr.threads[4]);
			
		}

		function test2AtTheSameTimeWithThrow() {
			var pathInc=createURI("DistributedLockTest/index.cfm");
			var name="test"&hash(createUUID());
			thread name=name&"1" id=name  pathInc=pathInc {
				res=_internalRequest(
					template:pathInc,
					urls:{"name":id,"throw":true,cache:"distLock"}
				);
				thread.arr=listToArray(res.filecontent,";");
			}
	
			sleep(50);
			thread name=name&"2" id=name  pathInc=pathInc {
				res=_internalRequest(
					template:pathInc,
					urls:{"name":id,"throw":false,cache:"distLock"}
				);
				thread.arr=listToArray(res.filecontent,";");
			}
			thread action="join" name=[name&"1",name&"2"].toList();
			
			var arr=server[name].threads;
			debug(arr);
			expect(arr.len()).ToBe(4);
			expect(arr[1]).ToBe(arr[2]);
			expect(arr[3]).ToBe(arr[4]);
			expect(arr[1]).NotToBe(arr[4]);
		}

		function test2AtTheSameTimeWithAbort() {
			var pathInc=createURI("DistributedLockTest/index.cfm");
			var name="test"&hash(createUUID());
			thread name=name&"1" id=name  pathInc=pathInc {
				res=_internalRequest(
					template:pathInc,
					urls:{"name":id,"abort":true,cache:"distLock"}
				);
				thread.arr=listToArray(res.filecontent,";");
			}
	
			sleep(50);
			thread name=name&"2" id=name  pathInc=pathInc {
				res=_internalRequest(
					template:pathInc,
					urls:{"name":id,cache:"distLock"}
				);
				thread.arr=listToArray(res.filecontent,";");
			}
			thread action="join" name=[name&"1",name&"2"].toList();
	
			var arr=server[name].threads;
			debug(arr);
			expect(arr.len()).ToBe(4);
			expect(arr[1]).ToBe(arr[2]);
			expect(arr[3]).ToBe(arr[4]);
			expect(arr[1]).NotToBe(arr[4]);
	
		}

		function test20AtTheSameTime() {
			var pathInc=createURI("DistributedLockTest/index.cfm");
			var name="test"&hash(createUUID());
			var names=[];
			var threads=20;
			loop from=1 to=threads index="local.i"  {
				arrayAppend(names,name&i);
				thread name=name&i id=name  pathInc=pathInc  {
					res=_internalRequest(
						template:pathInc,
						urls:{"name":id,"amount":1,time:10,timeout:10,cache:"distLock"}
					);
					thread.arr=listToArray(res.filecontent,";");
				}
			}
	
			thread action="join" name=names.toList();
			var arr=server[name].threads;
			var l=len(arr);
			expect(l).ToBe(threads*2);
			for(var i=1;i+1<=l;i+=2) {
				expect(arr[i]).ToBe(arr[i+1]);
			}
			debug(arr);
		}

		function test20AtTheSameTimeAmount4() {
			var pathInc=createURI("DistributedLockTest/index.cfm");
			
			var name="test"&hash(createUUID());
			var names=[];
			var threads=20;
			var amount=4;
			loop from=1 to=threads index="local.i"  {
				arrayAppend(names,name&i);
				thread name=name&i id=name amount=amount  pathInc=pathInc {
					res=_internalRequest(
						template:pathInc,
						urls:{"name":id,"amount":amount,time:100,timeout:100,cache:"distLock"}
					);
					thread.arr=listToArray(res.filecontent,";");
				}
			}
			thread action="join" name=names.toList();
			var arr=server[name].counters;
			var max=0;
			loop array=server[name].counters item="local.c" {
				if(c>max)max=c;
			}
			
			expect(len(arr)).ToBe(threads*2);
			expect(max).ToBe(amount);
		}

		private string function createURI(string calledName){
			var path=getDirectoryFromPath(getCurrenttemplatepath());
			return contractPath(path&calledName);
		}
	
	</cfscript>



</cfcomponent>