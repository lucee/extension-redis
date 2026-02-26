# Testing the Lucee Redis Extension

This guide covers how to test the Redis extension in single-server and multi-server configurations.

## Prerequisites

- Docker and Docker Compose
- Java 11+ (for building)
- Maven (for building)

## Building the Extension

```bash
# Build the extension
mvn clean package

# The .lex file will be in target/
ls target/*.lex
```

## Test Configurations

### 1. Single Server (Standalone Redis)

The simplest setup for basic testing.

```bash
# Start standalone Redis
docker-compose up -d redis

# Verify Redis is running
docker exec redis-standalone redis-cli ping
# Should return: PONG

# Stop when done
docker-compose down
```

**Connect from Lucee Admin:**
- Host: `localhost`
- Port: `6379`
- Connection Mode: `standalone`

### 2. Redis Sentinel (High Availability)

Tests automatic failover when the master fails.

```bash
# Start Sentinel cluster (1 master, 2 replicas, 3 sentinels)
docker-compose --profile sentinel up -d

# Verify setup
docker exec sentinel-1 redis-cli -p 26379 sentinel master mymaster

# Test failover by stopping the master
docker stop redis-master

# Wait a few seconds, then check new master
docker exec sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name mymaster

# Restart original master (becomes replica)
docker start redis-master

# Stop when done
docker-compose --profile sentinel down
```

**Connect from Lucee Admin:**
- Connection Mode: `sentinel`
- Sentinel Master Name: `mymaster`
- Sentinel Nodes: `localhost:26379,localhost:26380,localhost:26381`

### 3. Redis Cluster (Sharding)

Tests distributed key storage across multiple nodes.

```bash
# Start cluster nodes
docker-compose --profile cluster up -d

# Initialize the cluster (one-time setup)
docker exec redis-cluster-1 redis-cli --cluster create \
  redis-cluster-1:6379 \
  redis-cluster-2:6379 \
  redis-cluster-3:6379 \
  --cluster-replicas 0 --cluster-yes

# Verify cluster
docker exec redis-cluster-1 redis-cli cluster info

# Stop when done
docker-compose --profile cluster down
```

**Connect from Lucee Admin:**
- Connection Mode: `cluster`
- Cluster Nodes: `localhost:7001,localhost:7002,localhost:7003`

### 4. Multi-Lucee Session Sharing

Tests session storage across multiple Lucee instances.

```bash
# Start Redis + 2 Lucee servers
docker-compose --profile multi-lucee up -d

# Wait for Lucee to start (check logs)
docker-compose logs -f lucee-1 lucee-2

# Access the test application
# Server 1: http://localhost:8881/
# Server 2: http://localhost:8882/

# Stop when done
docker-compose --profile multi-lucee down
```

**Test Steps:**
1. Open http://localhost:8881/ in your browser
2. Note the Session ID and Server ID
3. Open http://localhost:8882/ in the same browser (or copy cookies)
4. Verify:
   - Same Session ID
   - Different Server ID
   - Access count increases
   - Session data persists

## Test Pages

The `test-app/` directory contains test pages:

| Page | URL | Purpose |
|------|-----|---------|
| `index.cfm` | `/` | Session sharing test - shows which server handled each request |
| `concurrency-test.cfm` | `/concurrency-test.cfm` | Tests distributed locking with concurrent modifications |
| `metrics.cfm` | `/metrics.cfm` | Displays cache statistics, supports JSON/Prometheus formats |

## Running Unit Tests

The unit tests require a running Redis instance and Lucee.

```bash
# Start Redis
docker-compose up -d redis

# Run tests via Maven (uses GitHub Actions locally)
mvn test

# Or run specific test files in Lucee Admin:
# 1. Install the extension
# 2. Navigate to /tests/RedisTest.cfc
# 3. Click "Run Tests"
```

### Test Files

| File | Tests |
|------|-------|
| `RedisTest.cfc` | Basic cache operations (put, get, delete) |
| `KeyPrefixTest.cfc` | Key namespace isolation |
| `MetricsTest.cfc` | Hit/miss counters, statistics |
| `IdleTimeoutTest.cfc` | Touch-on-access, sliding expiration |
| `DistributedLockTest.cfc` | cfDistributedLock functionality |
| `RedisCommandTypes.cfc` | Raw Redis command type handling |

## Testing New Features

### Key Prefix (Namespace Isolation)

```cfml
// Configure two caches with different prefixes
this.cache = {
    "app1Cache": {
        class: "lucee.extension.io.cache.redis.RedisCache",
        custom: { keyPrefix: "app1:", /* ... */ }
    },
    "app2Cache": {
        class: "lucee.extension.io.cache.redis.RedisCache",
        custom: { keyPrefix: "app2:", /* ... */ }
    }
};

// Same key name, isolated storage
cachePut(key: "user:123", value: "App1 User", cacheName: "app1Cache");
cachePut(key: "user:123", value: "App2 User", cacheName: "app2Cache");

// In Redis, these are stored as:
// app1:user:123 -> "App1 User"
// app2:user:123 -> "App2 User"
```

### Session Locking

```cfml
// Enable in Application.cfc cache config
custom: {
    sessionLockingEnabled: true,
    sessionLockExpiration: 30,  // seconds
    sessionLockTimeout: 5000    // milliseconds
}

// Test with concurrent requests to /concurrency-test.cfm
// With locking: All increments succeed
// Without locking: Race conditions cause lost updates
```

### Touch on Access (Sliding Expiration)

```cfml
// Enable in Application.cfc cache config
custom: {
    touchOnAccess: true,
    idleTimeoutSeconds: 300  // 5 minutes
}

// Each cache read resets TTL to 300 seconds
// Entry expires 5 minutes after LAST access, not first
```

### Metrics

```cfml
// Get cache statistics
info = cacheGetMetadata(cacheName: "myCache");
writeOutput("Hits: " & info.cacheStatistics.hitCount);
writeOutput("Hit Ratio: " & numberFormat(info.cacheStatistics.hitRatio * 100, "0.0") & "%");

// Or use the metrics endpoint
// JSON: /metrics.cfm?format=json
// Prometheus: /metrics.cfm?format=prometheus
```

## Troubleshooting

### Connection Issues

```bash
# Check Redis is running
docker-compose ps

# Check Redis logs
docker-compose logs redis

# Test connection manually
docker exec redis-standalone redis-cli ping

# Check Lucee logs
docker-compose logs lucee-1
```

### Sentinel Failover Not Working

```bash
# Check sentinel status
docker exec sentinel-1 redis-cli -p 26379 sentinel master mymaster

# Check quorum (need 2 of 3 sentinels to agree)
docker exec sentinel-1 redis-cli -p 26379 sentinel ckquorum mymaster

# View sentinel logs
docker logs sentinel-1
```

### Session Not Sharing

1. Verify same Redis instance is configured on both Lucee servers
2. Check session cookies are being sent (same domain/path)
3. Verify `sessionCluster = true` in Application.cfc
4. Check `keyPrefix` is the same on both servers

### Performance Issues

```bash
# Check Redis memory usage
docker exec redis-standalone redis-cli info memory

# Check connection pool stats
# Access /metrics.cfm and check "Connection Pool" section

# Monitor Redis commands in real-time
docker exec redis-standalone redis-cli monitor
```

## CI/CD Integration

The project uses GitHub Actions for CI. See `.github/workflows/main.yml`.

To run tests locally similar to CI:

```bash
# Start Redis service
docker-compose up -d redis

# Build and test
mvn clean install

# The workflow also tests against multiple Lucee versions
```
