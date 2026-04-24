<cfscript>
// Multi-Server Session Test Page
// Access this from different Lucee instances to verify session sharing

session.accessCount = (session.accessCount ?: 0) + 1;
session.lastAccess = now();
session.lastServer = application.serverID;

// Store history of which servers handled requests
if (!structKeyExists(session, "serverHistory")) {
    session.serverHistory = [];
}
arrayAppend(session.serverHistory, {
    serverID: application.serverID,
    timestamp: now()
});

// Keep only last 10 entries
if (arrayLen(session.serverHistory) > 10) {
    session.serverHistory = session.serverHistory.slice(-10);
}
</cfscript>
<!DOCTYPE html>
<html>
<head>
    <title>Redis Session Test</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .info-box { background: #f5f5f5; padding: 15px; margin: 10px 0; border-radius: 5px; }
        .success { background: #d4edda; border: 1px solid #28a745; }
        .warning { background: #fff3cd; border: 1px solid #ffc107; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background: #f2f2f2; }
        h1 { color: #333; }
        .server-id { font-family: monospace; font-size: 0.9em; }
    </style>
</head>
<body>
    <h1>Redis Session Sharing Test</h1>

    <div class="info-box success">
        <h3>Current Request</h3>
        <p><strong>Handled by Server:</strong> <span class="server-id"><cfoutput>#application.serverID#</cfoutput></span></p>
        <p><strong>Session ID:</strong> <span class="server-id"><cfoutput>#session.sessionid#</cfoutput></span></p>
        <p><strong>Access Count:</strong> <cfoutput>#session.accessCount#</cfoutput></p>
        <p><strong>Current Time:</strong> <cfoutput>#dateTimeFormat(now(), "yyyy-mm-dd HH:nn:ss")#</cfoutput></p>
    </div>

    <div class="info-box">
        <h3>Session Data</h3>
        <p><strong>Session Created:</strong> <cfoutput>#dateTimeFormat(session.created, "yyyy-mm-dd HH:nn:ss")#</cfoutput></p>
        <p><strong>Original Server:</strong> <span class="server-id"><cfoutput>#session.serverID#</cfoutput></span></p>
        <p><strong>Last Access:</strong> <cfoutput>#dateTimeFormat(session.lastAccess, "yyyy-mm-dd HH:nn:ss")#</cfoutput></p>
    </div>

    <div class="info-box">
        <h3>Server History (Last 10 Requests)</h3>
        <table>
            <tr>
                <th>#</th>
                <th>Server ID</th>
                <th>Timestamp</th>
            </tr>
            <cfoutput>
            <cfloop array="#session.serverHistory#" index="i" item="entry">
                <tr>
                    <td>#i#</td>
                    <td class="server-id">#entry.serverID#</td>
                    <td>#dateTimeFormat(entry.timestamp, "yyyy-mm-dd HH:nn:ss")#</td>
                </tr>
            </cfloop>
            </cfoutput>
        </table>
    </div>

    <cfset uniqueServers = {}/>
    <cfloop array="#session.serverHistory#" item="entry">
        <cfset uniqueServers[entry.serverID] = true/>
    </cfloop>

    <cfif structCount(uniqueServers) GT 1>
        <div class="info-box success">
            <h3>✓ Multi-Server Session Sharing Working!</h3>
            <p>Session has been handled by <strong>#structCount(uniqueServers)#</strong> different servers.</p>
            <p>This confirms Redis session storage is working correctly across multiple Lucee instances.</p>
        </div>
    <cfelse>
        <div class="info-box warning">
            <h3>⚠ Single Server So Far</h3>
            <p>All requests have been handled by the same server.</p>
            <p>Try accessing this page from a different Lucee instance to test session sharing:</p>
            <ul>
                <li><a href="http://localhost:8881/">Lucee Server 1 (port 8881)</a></li>
                <li><a href="http://localhost:8882/">Lucee Server 2 (port 8882)</a></li>
            </ul>
        </div>
    </cfif>

    <div class="info-box">
        <h3>Test Instructions</h3>
        <ol>
            <li>Start the multi-Lucee setup: <code>docker-compose --profile multi-lucee up -d</code></li>
            <li>Access this page on Server 1: <a href="http://localhost:8881/">http://localhost:8881/</a></li>
            <li>Copy the CFID/CFTOKEN cookies (or use the same browser)</li>
            <li>Access on Server 2: <a href="http://localhost:8882/">http://localhost:8882/</a></li>
            <li>Verify the session data persists and access count increases</li>
            <li>Check the Server History table shows both servers</li>
        </ol>
    </div>

    <p><a href="<cfoutput>#cgi.SCRIPT_NAME#</cfoutput>">Refresh Page</a></p>
</body>
</html>
