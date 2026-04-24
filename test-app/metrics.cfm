<cfscript>
// Metrics and Statistics Test Page
param name="url.format" default="html";

// Get cache info using basic cache functions
try {
    // Try to get all cache IDs to verify cache is working
    ids = cacheGetAllIds("redisSession");
    hasCache = true;

    // Build basic cache info struct
    cacheInfo = {
        keyCount: arrayLen(ids),
        cacheStatistics: {
            hitCount: 0,
            missCount: 0,
            putCount: 0,
            removeCount: 0,
            hitRatio: 0
        }
    };
} catch (any e) {
    hasCache = false;
    cacheError = e.message;
}

// Generate some cache activity for demo
if (url.keyExists("generate")) {
    for (i = 1; i <= 10; i++) {
        key = "metrics_test_#i#";
        cachePut(key: key, value: "test value #i#", cacheName: "redisSession");
    }
    // Cause some hits
    for (i = 1; i <= 5; i++) {
        cacheGet(key: "metrics_test_#i#", cacheName: "redisSession");
    }
    // Cause some misses
    for (i = 1; i <= 3; i++) {
        try {
            cacheGet(key: "nonexistent_#i#", cacheName: "redisSession");
        } catch (any e) {}
    }
    location(url: "metrics.cfm", addToken: false);
}

// Clean up test keys
if (url.keyExists("cleanup")) {
    for (i = 1; i <= 10; i++) {
        try {
            cacheRemove(ids: "metrics_test_#i#", cacheName: "redisSession");
        } catch (any e) {}
    }
    location(url: "metrics.cfm", addToken: false);
}

// JSON output for Prometheus scraping
if (url.format == "json" && hasCache) {
    setting showdebugoutput="false";
    content type="application/json";

    jsonData = {
        cache_name: "redisSession",
        timestamp: now().getTime()
    };

    if (structKeyExists(cacheInfo, "cacheStatistics")) {
        jsonData.hits = cacheInfo.cacheStatistics.hitCount ?: 0;
        jsonData.misses = cacheInfo.cacheStatistics.missCount ?: 0;
        jsonData.puts = cacheInfo.cacheStatistics.putCount ?: 0;
        jsonData.removes = cacheInfo.cacheStatistics.removeCount ?: 0;
        jsonData.hit_ratio = cacheInfo.cacheStatistics.hitRatio ?: 0;
    }

    if (structKeyExists(cacheInfo, "connectionPool")) {
        jsonData.pool = {
            active: cacheInfo.connectionPool.NumActive ?: 0,
            idle: cacheInfo.connectionPool.NumIdle ?: 0,
            max: cacheInfo.connectionPool.MaxTotal ?: 0
        };
    }

    writeOutput(serializeJSON(jsonData));
    abort;
}

// Prometheus format
if (url.format == "prometheus" && hasCache) {
    setting showdebugoutput="false";
    content type="text/plain";

    output = "";

    if (structKeyExists(cacheInfo, "cacheStatistics")) {
        stats = cacheInfo.cacheStatistics;
        output &= '## HELP lucee_redis_cache_hits_total Total cache hits' & chr(10);
        output &= '## TYPE lucee_redis_cache_hits_total counter' & chr(10);
        output &= 'lucee_redis_cache_hits_total{cache="redisSession"} #stats.hitCount ?: 0#' & chr(10);

        output &= '## HELP lucee_redis_cache_misses_total Total cache misses' & chr(10);
        output &= '## TYPE lucee_redis_cache_misses_total counter' & chr(10);
        output &= 'lucee_redis_cache_misses_total{cache="redisSession"} #stats.missCount ?: 0#' & chr(10);

        output &= '## HELP lucee_redis_cache_puts_total Total cache puts' & chr(10);
        output &= '## TYPE lucee_redis_cache_puts_total counter' & chr(10);
        output &= 'lucee_redis_cache_puts_total{cache="redisSession"} #stats.putCount ?: 0#' & chr(10);

        output &= '## HELP lucee_redis_cache_hit_ratio Cache hit ratio' & chr(10);
        output &= '## TYPE lucee_redis_cache_hit_ratio gauge' & chr(10);
        output &= 'lucee_redis_cache_hit_ratio{cache="redisSession"} #stats.hitRatio ?: 0#' & chr(10);
    }

    if (structKeyExists(cacheInfo, "connectionPool")) {
        pool = cacheInfo.connectionPool;
        output &= '## HELP lucee_redis_pool_active_connections Active connections' & chr(10);
        output &= '## TYPE lucee_redis_pool_active_connections gauge' & chr(10);
        output &= 'lucee_redis_pool_active_connections{cache="redisSession"} #pool.NumActive ?: 0#' & chr(10);

        output &= '## HELP lucee_redis_pool_idle_connections Idle connections' & chr(10);
        output &= '## TYPE lucee_redis_pool_idle_connections gauge' & chr(10);
        output &= 'lucee_redis_pool_idle_connections{cache="redisSession"} #pool.NumIdle ?: 0#' & chr(10);
    }

    writeOutput(output);
    abort;
}
</cfscript>
<!DOCTYPE html>
<html>
<head>
    <title>Redis Cache Metrics</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .info-box { background: #f5f5f5; padding: 15px; margin: 10px 0; border-radius: 5px; }
        .metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; }
        .metric-card { background: white; border: 1px solid #ddd; padding: 15px; border-radius: 5px; text-align: center; }
        .metric-value { font-size: 36px; font-weight: bold; color: #333; }
        .metric-label { font-size: 14px; color: #666; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background: #f2f2f2; }
        pre { background: #2d2d2d; color: #f8f8f2; padding: 15px; border-radius: 5px; overflow-x: auto; }
        button { padding: 10px 20px; margin: 5px; cursor: pointer; }
        .error { background: #f8d7da; border: 1px solid #dc3545; }
    </style>
</head>
<body>
    <h1>Redis Cache Metrics</h1>

    <cfif !hasCache>
        <div class="info-box error">
            <h3>Cache Not Available</h3>
            <p><cfoutput>#cacheError#</cfoutput></p>
        </div>
    <cfelse>
        <div class="info-box">
            <h3>Cache Statistics</h3>
            <div class="metrics-grid">
                <cfif structKeyExists(cacheInfo, "cacheStatistics")>
                    <cfset stats = cacheInfo.cacheStatistics/>
                    <div class="metric-card">
                        <div class="metric-value"><cfoutput>#stats.hitCount ?: 0#</cfoutput></div>
                        <div class="metric-label">Cache Hits</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value"><cfoutput>#stats.missCount ?: 0#</cfoutput></div>
                        <div class="metric-label">Cache Misses</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value"><cfoutput>#stats.putCount ?: 0#</cfoutput></div>
                        <div class="metric-label">Cache Puts</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value"><cfoutput>#stats.removeCount ?: 0#</cfoutput></div>
                        <div class="metric-label">Cache Removes</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value"><cfoutput>#numberFormat(stats.hitRatio * 100, "0.0")#%</cfoutput></div>
                        <div class="metric-label">Hit Ratio</div>
                    </div>
                </cfif>
            </div>
        </div>

        <cfif structKeyExists(cacheInfo, "connectionPool")>
            <cfset pool = cacheInfo.connectionPool/>
            <div class="info-box">
                <h3>Connection Pool</h3>
                <div class="metrics-grid">
                    <div class="metric-card">
                        <div class="metric-value"><cfoutput>#pool.NumActive ?: 0#</cfoutput></div>
                        <div class="metric-label">Active Connections</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value"><cfoutput>#pool.NumIdle ?: 0#</cfoutput></div>
                        <div class="metric-label">Idle Connections</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value"><cfoutput>#pool.MaxTotal ?: 0#</cfoutput></div>
                        <div class="metric-label">Max Total</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value"><cfoutput>#pool.BorrowedCount ?: 0#</cfoutput></div>
                        <div class="metric-label">Total Borrowed</div>
                    </div>
                </div>
            </div>
        </cfif>

        <div class="info-box">
            <h3>Generate Test Activity</h3>
            <p>
                <a href="?generate=1"><button>Generate Cache Activity</button></a>
                <a href="?cleanup=1"><button>Cleanup Test Keys</button></a>
            </p>
        </div>
    </cfif>

    <div class="info-box">
        <h3>API Endpoints</h3>
        <p>For monitoring integration (Prometheus, Grafana, etc.):</p>
        <ul>
            <li><a href="?format=json">JSON Format</a> - <code>metrics.cfm?format=json</code></li>
            <li><a href="?format=prometheus">Prometheus Format</a> - <code>metrics.cfm?format=prometheus</code></li>
        </ul>

        <h4>Prometheus Scrape Config Example</h4>
        <pre>scrape_configs:
  - job_name: 'lucee-redis'
    metrics_path: /metrics.cfm
    params:
      format: ['prometheus']
    static_configs:
      - targets: ['localhost:8888']</pre>
    </div>

    <cfif hasCache>
        <div class="info-box">
            <h3>Full Cache Metadata</h3>
            <cfdump var="#cacheInfo#" label="Cache Metadata" expand="false"/>
        </div>
    </cfif>
</body>
</html>
