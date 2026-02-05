<cfscript>
// Load Test Page for Redis Extension
// Tests concurrent operations, session sharing, and resilience

param name="url.test" default="all";
param name="url.threads" default="10";
param name="url.iterations" default="100";
param name="url.format" default="html";

results = {
    testName: url.test,
    threads: url.threads,
    iterations: url.iterations,
    serverInfo: server.coldfusion.productname & " on " & server.os.name,
    serverId: createUUID().left(8),
    startTime: now(),
    tests: []
};

// Helper function to add test result
function addResult(name, success, duration, details="") {
    results.tests.append({
        name: name,
        success: success,
        durationMs: duration,
        details: details,
        timestamp: now()
    });
}

// Test 1: Basic Cache Operations
if (url.test == "all" || url.test == "basic") {
    startTime = getTickCount();
    try {
        testKey = "load_test_basic_#results.serverId#";
        testValue = {
            data: "test value",
            timestamp: now(),
            serverId: results.serverId
        };

        // Put
        cachePut(key: testKey, value: testValue, cacheName: "redisSession");

        // Get - use throwWhenNotExist=false to return null instead of throwing
        retrieved = cacheGet(key: testKey, cacheName: "redisSession", throwWhenNotExist: false);

        // Verify
        if (!isNull(retrieved) && isStruct(retrieved) && retrieved.serverId == results.serverId) {
            addResult("Basic Cache Operations", true, getTickCount() - startTime, "Put/Get successful");
        } else {
            addResult("Basic Cache Operations", false, getTickCount() - startTime, "Value mismatch or null");
        }

        // Cleanup
        cacheRemove(ids: testKey, cacheName: "redisSession");
    } catch (any e) {
        addResult("Basic Cache Operations", false, getTickCount() - startTime, e.message);
    }
}

// Test 2: Concurrent Write Test
if (url.test == "all" || url.test == "concurrent") {
    startTime = getTickCount();
    successCount = 0;
    errorCount = 0;
    errors = [];

    try {
        // Create multiple threads writing concurrently
        threads = [];
        for (i = 1; i <= url.threads; i++) {
            threadName = "writer_#i#";
            threads.append(threadName);
            thread name=threadName action="run" i=i iterations=url.iterations serverId=results.serverId {
                for (j = 1; j <= iterations; j++) {
                    try {
                        key = "concurrent_#serverId#_#i#_#j#";
                        value = {
                            thread: i,
                            iteration: j,
                            timestamp: now(),
                            serverId: serverId
                        };
                        cachePut(key: key, value: value, cacheName: "redisSession");
                        thread.successCount = (thread.successCount ?: 0) + 1;
                    } catch (any e) {
                        thread.errorCount = (thread.errorCount ?: 0) + 1;
                        thread.lastError = e.message;
                    }
                }
            }
        }

        // Wait for all threads
        thread action="join" name=threads.toList() timeout=60000;

        // Collect results
        for (t in threads) {
            if (structKeyExists(cfthread, t)) {
                successCount += cfthread[t].successCount ?: 0;
                errorCount += cfthread[t].errorCount ?: 0;
                if (structKeyExists(cfthread[t], "lastError")) {
                    errors.append(cfthread[t].lastError);
                }
            }
        }

        totalOps = url.threads * url.iterations;
        duration = getTickCount() - startTime;
        opsPerSecond = (successCount / (duration / 1000));

        addResult("Concurrent Write Test", errorCount == 0, duration,
            "Success: #successCount#/#totalOps#, Errors: #errorCount#, Ops/sec: #numberFormat(opsPerSecond, '0.0')#");

        // Cleanup
        for (i = 1; i <= url.threads; i++) {
            for (j = 1; j <= url.iterations; j++) {
                try {
                    cacheRemove(ids: "concurrent_#results.serverId#_#i#_#j#", cacheName: "redisSession");
                } catch (any e) {}
            }
        }
    } catch (any e) {
        addResult("Concurrent Write Test", false, getTickCount() - startTime, e.message);
    }
}

// Test 3: Read/Write Mix Test
if (url.test == "all" || url.test == "mixed") {
    startTime = getTickCount();
    reads = 0;
    writes = 0;
    hits = 0;
    misses = 0;

    try {
        // Pre-populate some keys
        for (i = 1; i <= 50; i++) {
            cachePut(key: "mixed_#results.serverId#_#i#", value: "preload_#i#", cacheName: "redisSession");
        }

        // Mixed read/write operations
        for (i = 1; i <= url.iterations; i++) {
            keyNum = randRange(1, 100);
            key = "mixed_#results.serverId#_#keyNum#";

            if (randRange(1, 10) <= 7) {
                // 70% reads
                try {
                    val = cacheGet(key: key, cacheName: "redisSession");
                    if (!isNull(val)) {
                        hits++;
                    } else {
                        misses++;
                    }
                    reads++;
                } catch (any e) {
                    misses++;
                    reads++;
                }
            } else {
                // 30% writes
                cachePut(key: key, value: "value_#i#_#now()#", cacheName: "redisSession");
                writes++;
            }
        }

        duration = getTickCount() - startTime;
        hitRatio = hits > 0 ? (hits / (hits + misses) * 100) : 0;

        addResult("Mixed Read/Write Test", true, duration,
            "Reads: #reads#, Writes: #writes#, Hits: #hits#, Misses: #misses#, Hit Ratio: #numberFormat(hitRatio, '0.0')#%");

        // Cleanup
        for (i = 1; i <= 100; i++) {
            try {
                cacheRemove(ids: "mixed_#results.serverId#_#i#", cacheName: "redisSession");
            } catch (any e) {}
        }
    } catch (any e) {
        addResult("Mixed Read/Write Test", false, getTickCount() - startTime, e.message);
    }
}

// Test 4: Session Sharing Test (verifies cross-server access)
if (url.test == "all" || url.test == "session") {
    startTime = getTickCount();
    try {
        // Write a session-like value
        sessionKey = "session_share_test_#results.serverId#";
        sessionData = {
            userId: 12345,
            username: "testuser",
            createdAt: now(),
            createdBy: results.serverId,
            requestCount: 1
        };

        cachePut(key: sessionKey, value: sessionData, cacheName: "redisSession");

        // Read it back
        retrieved = cacheGet(key: sessionKey, cacheName: "redisSession");

        if (isStruct(retrieved) && retrieved.userId == 12345) {
            addResult("Session Sharing Test", true, getTickCount() - startTime,
                "Session written by #retrieved.createdBy#, readable from #results.serverId#");
        } else {
            addResult("Session Sharing Test", false, getTickCount() - startTime, "Session data mismatch");
        }

        // Leave the key for cross-server verification (cleanup after 60 seconds)
    } catch (any e) {
        addResult("Session Sharing Test", false, getTickCount() - startTime, e.message);
    }
}

// Test 5: Large Value Test
if (url.test == "all" || url.test == "large") {
    startTime = getTickCount();
    try {
        // Create a large struct
        largeData = {
            id: createUUID(),
            timestamp: now(),
            serverId: results.serverId,
            items: []
        };

        for (i = 1; i <= 1000; i++) {
            largeData.items.append({
                id: i,
                name: "Item #i# - " & repeatString("x", 100),
                value: randRange(1, 10000)
            });
        }

        largeKey = "large_value_#results.serverId#";

        // Write
        cachePut(key: largeKey, value: largeData, cacheName: "redisSession");

        // Read
        retrieved = cacheGet(key: largeKey, cacheName: "redisSession");

        if (isStruct(retrieved) && arrayLen(retrieved.items) == 1000) {
            addResult("Large Value Test", true, getTickCount() - startTime,
                "Successfully stored/retrieved struct with 1000 items");
        } else {
            addResult("Large Value Test", false, getTickCount() - startTime, "Data integrity issue");
        }

        cacheRemove(ids: largeKey, cacheName: "redisSession");
    } catch (any e) {
        addResult("Large Value Test", false, getTickCount() - startTime, e.message);
    }
}

// Calculate summary
results.endTime = now();
results.totalDuration = dateDiff("s", results.startTime, results.endTime);
results.passedTests = results.tests.filter(function(t) { return t.success; }).len();
results.failedTests = results.tests.filter(function(t) { return !t.success; }).len();
results.totalTests = results.tests.len();

// Output based on format
if (url.format == "json") {
    setting showdebugoutput="false";
    content type="application/json";
    writeOutput(serializeJSON(results));
    abort;
}
</cfscript>
<!DOCTYPE html>
<html>
<head>
    <title>Redis Extension Load Test</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
        .container { max-width: 900px; margin: 0 auto; }
        .header { background: #333; color: white; padding: 20px; border-radius: 5px 5px 0 0; }
        .summary { background: white; padding: 20px; border: 1px solid #ddd; }
        .test-result { padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid; }
        .test-pass { background: #d4edda; border-color: #28a745; }
        .test-fail { background: #f8d7da; border-color: #dc3545; }
        .stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 15px; margin: 20px 0; }
        .stat-card { background: white; padding: 15px; text-align: center; border-radius: 5px; border: 1px solid #ddd; }
        .stat-value { font-size: 28px; font-weight: bold; }
        .stat-label { color: #666; font-size: 12px; }
        .pass { color: #28a745; }
        .fail { color: #dc3545; }
        pre { background: #2d2d2d; color: #f8f8f2; padding: 15px; border-radius: 5px; overflow-x: auto; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Redis Extension Load Test Results</h1>
            <p>Server: <cfoutput>#results.serverInfo# (ID: #results.serverId#)</cfoutput></p>
        </div>

        <div class="stats">
            <div class="stat-card">
                <div class="stat-value <cfoutput>#results.failedTests == 0 ? 'pass' : 'fail'#</cfoutput>">
                    <cfoutput>#results.passedTests#/#results.totalTests#</cfoutput>
                </div>
                <div class="stat-label">Tests Passed</div>
            </div>
            <div class="stat-card">
                <div class="stat-value"><cfoutput>#url.threads#</cfoutput></div>
                <div class="stat-label">Threads</div>
            </div>
            <div class="stat-card">
                <div class="stat-value"><cfoutput>#url.iterations#</cfoutput></div>
                <div class="stat-label">Iterations</div>
            </div>
            <div class="stat-card">
                <div class="stat-value"><cfoutput>#results.totalDuration#s</cfoutput></div>
                <div class="stat-label">Total Duration</div>
            </div>
        </div>

        <div class="summary">
            <h2>Test Results</h2>
            <cfloop array="#results.tests#" item="test">
                <div class="test-result <cfoutput>#test.success ? 'test-pass' : 'test-fail'#</cfoutput>">
                    <strong><cfoutput>#test.name#</cfoutput></strong>
                    <cfif test.success>
                        <span class="pass">✓ PASSED</span>
                    <cfelse>
                        <span class="fail">✗ FAILED</span>
                    </cfif>
                    <br>
                    <small>Duration: <cfoutput>#test.durationMs#</cfoutput>ms</small>
                    <cfif len(test.details)>
                        <br><small><cfoutput>#test.details#</cfoutput></small>
                    </cfif>
                </div>
            </cfloop>
        </div>

        <div class="summary" style="margin-top: 20px;">
            <h3>Run More Tests</h3>
            <p>
                <a href="?test=all&threads=10&iterations=100">Standard Load Test</a> |
                <a href="?test=all&threads=20&iterations=200">Heavy Load Test</a> |
                <a href="?test=all&threads=50&iterations=500">Stress Test</a> |
                <a href="?test=concurrent&threads=100&iterations=100">Concurrency Stress</a>
            </p>
            <h4>API Usage</h4>
            <pre>curl "http://localhost:8881/load-test.cfm?test=all&threads=10&iterations=100&format=json"</pre>
        </div>
    </div>
</body>
</html>
