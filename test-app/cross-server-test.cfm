<cfscript>
// Cross-Server Session Sharing Test
param name="url.action" default="write";
param name="url.key" default="cross_server_test_session";
param name="url.format" default="json";

result = {
    server: server.coldfusion.productname,
    serverId: createUUID().left(8),
    timestamp: now(),
    action: url.action,
    key: url.key
};

try {
    if (url.action == "write") {
        // Write session data
        sessionData = {
            createdBy: result.serverId,
            createdAt: now(),
            data: {
                userId: 12345,
                username: "testuser",
                roles: ["admin", "user"],
                cart: [{item: "product1", qty: 2}]
            },
            accessLog: [{
                server: result.serverId,
                time: now(),
                action: "created"
            }]
        };

        cachePut(key: url.key, value: sessionData, cacheName: "redisSession");

        result.success = true;
        result.message = "Session created successfully";
        result.sessionData = sessionData;

    } else if (url.action == "read") {
        // Read session data
        sessionData = cacheGet(key: url.key, cacheName: "redisSession", throwWhenNotExist: false);

        if (!isNull(sessionData)) {
            result.success = true;
            result.message = "Session read successfully";
            result.sessionData = sessionData;
            result.createdByServer = sessionData.createdBy;
            result.readByServer = result.serverId;
            result.crossServerSuccess = sessionData.createdBy != result.serverId;
        } else {
            result.success = false;
            result.message = "Session not found";
        }

    } else if (url.action == "update") {
        // Update session data (simulate another server updating)
        sessionData = cacheGet(key: url.key, cacheName: "redisSession", throwWhenNotExist: false);

        if (!isNull(sessionData)) {
            // Add to access log
            sessionData.accessLog.append({
                server: result.serverId,
                time: now(),
                action: "updated"
            });

            // Modify cart
            sessionData.data.cart.append({item: "product2", qty: 1});
            sessionData.lastModifiedBy = result.serverId;
            sessionData.lastModifiedAt = now();

            cachePut(key: url.key, value: sessionData, cacheName: "redisSession");

            result.success = true;
            result.message = "Session updated successfully";
            result.sessionData = sessionData;
        } else {
            result.success = false;
            result.message = "Session not found for update";
        }

    } else if (url.action == "delete") {
        cacheRemove(ids: url.key, cacheName: "redisSession");
        result.success = true;
        result.message = "Session deleted";

    } else if (url.action == "verify") {
        // Full cross-server verification
        sessionData = cacheGet(key: url.key, cacheName: "redisSession", throwWhenNotExist: false);

        if (!isNull(sessionData)) {
            result.success = true;
            result.sessionData = sessionData;
            result.serversInvolved = [];

            for (log in sessionData.accessLog) {
                if (!arrayFind(result.serversInvolved, log.server)) {
                    result.serversInvolved.append(log.server);
                }
            }

            result.multiServerAccess = arrayLen(result.serversInvolved) > 1;
            result.message = "Session accessed by #arrayLen(result.serversInvolved)# server(s): #arrayToList(result.serversInvolved)#";
        } else {
            result.success = false;
            result.message = "Session not found";
        }
    }
} catch (any e) {
    result.success = false;
    result.error = e.message;
}

setting showdebugoutput="false";
content type="application/json";
writeOutput(serializeJSON(result));
</cfscript>
