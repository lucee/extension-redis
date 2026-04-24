<cfscript>
// Concurrent Session Modification Test
// This tests that distributed locking prevents race conditions

param name="url.action" default="view";
param name="url.threads" default="10";
param name="url.iterations" default="5";

// Initialize counter if not present
if (!structKeyExists(session, "counter")) {
    session.counter = 0;
}
if (!structKeyExists(session, "testResults")) {
    session.testResults = [];
}

if (url.action == "increment") {
    // Simple increment - should be protected by session locking
    session.counter++;
    session.lastModified = now();
    session.lastModifiedBy = application.serverID;

    // Return JSON for AJAX calls
    if (structKeyExists(url, "ajax")) {
        setting showdebugoutput="false";
        content type="application/json";
        writeOutput(serializeJSON({
            counter: session.counter,
            server: application.serverID
        }));
        abort;
    }
}

if (url.action == "reset") {
    session.counter = 0;
    session.testResults = [];
}

if (url.action == "runTest") {
    // Run concurrent increment test
    threads = val(url.threads);
    iterations = val(url.iterations);
    expectedFinal = threads * iterations;

    session.counter = 0;
    startTime = getTickCount();

    // Create multiple threads that each increment the counter
    for (i = 1; i <= threads; i++) {
        thread name="test_#i#" action="run" iterations=iterations {
            for (j = 1; j <= iterations; j++) {
                session.counter++;
                sleep(randRange(1, 10)); // Random small delay
            }
        }
    }

    // Wait for all threads to complete
    for (i = 1; i <= threads; i++) {
        thread name="test_#i#" action="join" timeout="30000";
    }

    endTime = getTickCount();
    duration = endTime - startTime;

    // Record test result
    result = {
        threads: threads,
        iterations: iterations,
        expected: expectedFinal,
        actual: session.counter,
        success: session.counter == expectedFinal,
        duration: duration,
        timestamp: now(),
        server: application.serverID
    };

    arrayAppend(session.testResults, result);
}
</cfscript>
<!DOCTYPE html>
<html>
<head>
    <title>Session Concurrency Test</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .info-box { background: #f5f5f5; padding: 15px; margin: 10px 0; border-radius: 5px; }
        .success { background: #d4edda; border: 1px solid #28a745; }
        .failure { background: #f8d7da; border: 1px solid #dc3545; }
        table { border-collapse: collapse; width: 100%; margin-top: 10px; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background: #f2f2f2; }
        button { padding: 10px 20px; margin: 5px; cursor: pointer; }
        .big-number { font-size: 48px; font-weight: bold; color: #333; }
        code { background: #e9ecef; padding: 2px 6px; border-radius: 3px; }
    </style>
</head>
<body>
    <h1>Session Concurrency Test</h1>
    <p>Tests distributed locking to prevent race conditions during concurrent session modifications.</p>

    <div class="info-box">
        <h3>Current Counter Value</h3>
        <div class="big-number"><cfoutput>#session.counter#</cfoutput></div>
        <p>Server: <cfoutput>#application.serverID#</cfoutput></p>
    </div>

    <div class="info-box">
        <h3>Manual Test</h3>
        <p>
            <a href="?action=increment"><button>Increment +1</button></a>
            <a href="?action=reset"><button>Reset to 0</button></a>
        </p>
    </div>

    <div class="info-box">
        <h3>Concurrent Increment Test</h3>
        <p>Runs multiple threads that each increment the counter multiple times.</p>
        <p>With proper locking, the final counter should equal <code>threads × iterations</code>.</p>
        <p>Without locking, race conditions cause lost updates.</p>

        <form method="get">
            <input type="hidden" name="action" value="runTest"/>
            <label>Threads: <input type="number" name="threads" value="10" min="1" max="50"/></label>
            <label>Iterations per thread: <input type="number" name="iterations" value="5" min="1" max="100"/></label>
            <button type="submit">Run Concurrent Test</button>
        </form>
    </div>

    <cfif arrayLen(session.testResults)>
        <div class="info-box">
            <h3>Test Results</h3>
            <table>
                <tr>
                    <th>Time</th>
                    <th>Threads</th>
                    <th>Iterations</th>
                    <th>Expected</th>
                    <th>Actual</th>
                    <th>Duration</th>
                    <th>Result</th>
                </tr>
                <cfoutput>
                <cfloop array="#session.testResults#" item="result">
                    <tr class="#result.success ? 'success' : 'failure'#">
                        <td>#timeFormat(result.timestamp, "HH:mm:ss")#</td>
                        <td>#result.threads#</td>
                        <td>#result.iterations#</td>
                        <td>#result.expected#</td>
                        <td>#result.actual#</td>
                        <td>#result.duration#ms</td>
                        <td>#result.success ? '✓ PASS' : '✗ FAIL (lost ' & (result.expected - result.actual) & ' updates)'#</td>
                    </tr>
                </cfloop>
                </cfoutput>
            </table>
        </div>
    </cfif>

    <div class="info-box">
        <h3>Understanding the Test</h3>
        <ul>
            <li><strong>Without locking:</strong> Multiple threads read the same value, increment locally, and write back - causing lost updates.</li>
            <li><strong>With session locking enabled:</strong> Each session modification acquires a distributed lock first, ensuring serialized access.</li>
            <li><strong>Expected behavior:</strong> All tests should PASS with <code>sessionLockingEnabled: true</code>.</li>
        </ul>

        <h4>Configuration (in Application.cfc)</h4>
        <pre>
sessionLockingEnabled: true,    // Enable distributed locking
sessionLockExpiration: 30,      // Lock auto-expires after 30 seconds
sessionLockTimeout: 5000        // Wait up to 5 seconds to acquire lock
        </pre>
    </div>
</body>
</html>
