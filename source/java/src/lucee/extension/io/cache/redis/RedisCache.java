package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.pool2.impl.BaseObjectPoolConfig;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import lucee.commons.io.cache.Cache;
import lucee.commons.io.cache.CacheEntry;
import lucee.commons.io.cache.CacheKeyFilter;
import lucee.commons.io.cache.exp.CacheException;
import lucee.commons.io.log.Log;
import lucee.extension.io.cache.pool.RedisFactory;
import lucee.extension.io.cache.pool.RedisPool;
import lucee.extension.io.cache.pool.RedisPoolConfig;
import lucee.extension.io.cache.pool.RedisPoolListener;
import lucee.extension.io.cache.pool.RedisPoolListenerNotifyOnReturn;
import lucee.extension.io.cache.redis.InfoParser.DebugObject;
import lucee.extension.io.cache.redis.Redis.Pipeline;
import lucee.extension.io.cache.redis.metrics.RedisCacheMetrics;
import lucee.extension.io.cache.redis.resilience.CircuitBreaker;
import lucee.extension.io.cache.redis.resilience.ResilientOperation;
import lucee.extension.io.cache.redis.resilience.RetryPolicy;
import lucee.extension.io.cache.redis.sm.SecretReciever;
import lucee.extension.io.cache.redis.sm.SecretReciever.CredDat;
import lucee.extension.io.cache.util.Coder;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.config.Config;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;

public class RedisCache extends CacheSupport implements Command {

	private static long counter = Long.MIN_VALUE;

	// Cache statistics counters
	private final AtomicLong cacheHitCount = new AtomicLong(0);
	private final AtomicLong cacheMissCount = new AtomicLong(0);
	private final AtomicLong cachePutCount = new AtomicLong(0);
	private final AtomicLong cacheRemoveCount = new AtomicLong(0);

	protected final Object TOKEN = new Object();

	protected CFMLEngine engine = CFMLEngineFactory.getInstance();
	protected Cast caster = engine.getCastUtil();

	protected int socketTimeout;
	protected long liveTimeout;
	protected long idleTimeout;
	protected long connTimeout;
	protected String password;

	private ClassLoader cl;

	// config pool
	private int defaultExpire;

	private int databaseIndex;

	private RedisPool pool;

	private String host;
	private int port;

	private String username;
	private final boolean async = true;

	private Storage storage = new Storage(this);

	private Log log;

	// secret manager
	private String secretName;
	private String region;
	private String accessKeyId;
	private String secretKey;
	private boolean ssl;

	// Key prefix for namespace isolation
	private String keyPrefix;
	private byte[] keyPrefixBytes;

	// Session locking configuration
	private boolean sessionLockingEnabled;
	private int sessionLockExpiration;
	private long sessionLockTimeout;
	private SessionLockManager sessionLockManager;

	// Object reference handling - when true, always clone objects on get to prevent reference sharing
	private boolean alwaysClone;

	// Idle timeout touch behavior - when true and idleTimeoutSeconds > 0, reset TTL on each read
	private boolean touchOnAccess;
	private int idleTimeoutSeconds;

	// Resilience components
	private CircuitBreaker circuitBreaker;
	private RetryPolicy retryPolicy;
	private ResilientOperation resilientOps;
	private boolean resilienceEnabled;
	private int resilienceMaxRetries;
	private long resilienceRetryDelayMs;
	private int resilienceCircuitBreakerThreshold;
	private long resilienceCircuitBreakerResetMs;

	private final Object token = new Object();

	public RedisCache() {
		if (async) {
			// storage = new Storage(this);
			storage.start();
		}
	}

	@Override
	public void init(Config config, String cacheName, Struct arguments) throws IOException {
		init(config, arguments);
	}

	public void init(Struct arguments) throws IOException {
		init(null, arguments);
	}

	public void init(Config config, Struct arguments) throws IOException {
		this.cl = arguments.getClass().getClassLoader();
		if (config == null) config = CFMLEngineFactory.getInstance().getThreadConfig();

		host = caster.toString(arguments.get("host", "localhost"), "localhost");
		port = caster.toIntValue(arguments.get("port", null), 6379);

		socketTimeout = caster.toIntValue(arguments.get("timeout", null), -1);
		if (socketTimeout == -1) socketTimeout = caster.toIntValue(arguments.get("socketTimeout", null), 2000);

		liveTimeout = caster.toLongValue(arguments.get("liveTimeout", null), 3600000L);
		idleTimeout = caster.toLongValue(arguments.get("idleTimeout", null), -1L);
		connTimeout = caster.toLongValue(arguments.get("connectionTimeout", null), -1);
		if (connTimeout == -1) connTimeout = caster.toLongValue(arguments.get("connectionAcquireTimeout", null), 5000L);

		username = caster.toString(arguments.get("username", null), null);
		if (Util.isEmpty(username)) username = null;

		password = caster.toString(arguments.get("password", null), null);
		if (Util.isEmpty(password)) password = null;

		ssl = caster.toBooleanValue(arguments.get("ssl", null), false);

		// key prefix for namespace isolation
		keyPrefix = caster.toString(arguments.get("keyPrefix", null), null);
		if (Util.isEmpty(keyPrefix, true)) {
			keyPrefix = null;
			keyPrefixBytes = null;
		}
		else {
			keyPrefix = keyPrefix.trim();
			// Ensure prefix ends with a separator if it doesn't already
			if (!keyPrefix.endsWith(":") && !keyPrefix.endsWith("/") && !keyPrefix.endsWith(".")) {
				keyPrefix = keyPrefix + ":";
			}
			keyPrefixBytes = keyPrefix.getBytes(Coder.UTF8);
		}

		// session locking configuration (default: disabled for backward compatibility)
		sessionLockingEnabled = caster.toBooleanValue(arguments.get("sessionLockingEnabled", null), false);
		sessionLockExpiration = caster.toIntValue(arguments.get("sessionLockExpiration", null), 30);
		sessionLockTimeout = caster.toLongValue(arguments.get("sessionLockTimeout", null), 5000L);

		// object reference handling - when true, always re-deserialize objects to prevent reference sharing
		alwaysClone = caster.toBooleanValue(arguments.get("alwaysClone", null), false);

		// idle timeout touch behavior - reset TTL on each read to implement sliding expiration
		touchOnAccess = caster.toBooleanValue(arguments.get("touchOnAccess", null), false);
		idleTimeoutSeconds = caster.toIntValue(arguments.get("idleTimeoutSeconds", null), 0);

		// Resilience configuration
		resilienceEnabled = caster.toBooleanValue(arguments.get("resilienceEnabled", null), true);
		resilienceMaxRetries = caster.toIntValue(arguments.get("resilienceMaxRetries", null), 3);
		resilienceRetryDelayMs = caster.toLongValue(arguments.get("resilienceRetryDelayMs", null), 100L);
		resilienceCircuitBreakerThreshold = caster.toIntValue(arguments.get("resilienceCircuitBreakerThreshold", null), 5);
		resilienceCircuitBreakerResetMs = caster.toLongValue(arguments.get("resilienceCircuitBreakerResetMs", null), 30000L);

		// Initialize resilience components
		if (resilienceEnabled) {
			circuitBreaker = new CircuitBreaker(resilienceCircuitBreakerThreshold, resilienceCircuitBreakerResetMs, 3);
			retryPolicy = new RetryPolicy(resilienceMaxRetries, resilienceRetryDelayMs, 5000, 2.0, circuitBreaker);
			resilientOps = new ResilientOperation(circuitBreaker, retryPolicy, null, log);
		}

		// secret manager
		secretName = caster.toString(arguments.get("secretName", null), null);
		if (Util.isEmpty(secretName)) secretName = caster.toString(arguments.get("awsSecretName", null), null);
		if (Util.isEmpty(secretName)) secretName = null;
		// we only care about the following values in case we have a secret name
		if (secretName != null) {
			region = caster.toString(arguments.get("region", null), null);
			if (Util.isEmpty(region)) region = caster.toString(arguments.get("awsRegion", null), null);
			if (Util.isEmpty(region)) region = null;

			accessKeyId = caster.toString(arguments.get("accessKeyId", null), null);
			if (Util.isEmpty(accessKeyId)) accessKeyId = caster.toString(arguments.get("awsAccessKeyId", null), null);
			if (Util.isEmpty(accessKeyId)) accessKeyId = caster.toString(arguments.get("accessKey", null), null);
			if (Util.isEmpty(accessKeyId)) accessKeyId = null;

			secretKey = caster.toString(arguments.get("secretKey", null), null);
			if (Util.isEmpty(secretKey)) secretKey = caster.toString(arguments.get("awsSecretKey", null), null);
			if (Util.isEmpty(secretKey)) secretKey = caster.toString(arguments.get("secretKeyId", null), null);
			if (Util.isEmpty(secretKey)) secretKey = null;
		}

		defaultExpire = caster.toIntValue(arguments.get("timeToLiveSeconds", null), 0);

		databaseIndex = caster.toIntValue(arguments.get("databaseIndex", null), -1);
		String logName = caster.toString(arguments.get("log", null), null);
		if (!Util.isEmpty(logName, true) && config != null) {
			logName = logName.trim();
			this.log = config.getLog(logName);
		}

		if (log != null) {
			log.debug("redis-cache", "configuration: host:" + host + ";port:" + port + ";socketTimeout:" + socketTimeout + ";liveTimeout:" + liveTimeout + ";idleTimeout:"
					+ idleTimeout + ";username:" + username + ";password:" + password + ";defaultExpire:" + defaultExpire + ";databaseIndex:" + databaseIndex + ";");
		}

		RedisPoolListener listener = new RedisPoolListenerNotifyOnReturn(token);

		if (username == null && secretName != null) {
			CredDat cred = SecretReciever.getCredential(secretName, region, accessKeyId, secretKey, false, false);
			pool = new RedisPool(
					new RedisFactory(cl, choose(host, cred.host), choose(port, cred.port), cred.user, cred.pass, ssl, socketTimeout, idleTimeout, liveTimeout, databaseIndex, log),
					getPoolConfig(arguments), listener);

			// validate a connection
			Redis conn = null;
			try {
				conn = pool.borrowObject();
			}
			catch (Exception e) {
				// in case the connection does not work, we force an update on the credentials loaded from SM
				cred = SecretReciever.getCredential(secretName, region, accessKeyId, secretKey, true, true);
				pool = new RedisPool(new RedisFactory(cl, choose(host, cred.host), choose(port, cred.port), cred.user, cred.pass, ssl, socketTimeout, idleTimeout, liveTimeout,
						databaseIndex, log), getPoolConfig(arguments), listener);
			}
			finally {
				releaseConnection(conn);
			}

		}
		else pool = new RedisPool(new RedisFactory(cl, host, port, username, password, ssl, socketTimeout, idleTimeout, liveTimeout, databaseIndex, log), getPoolConfig(arguments),
				listener);

	}

	protected RedisPoolConfig getPoolConfig(Struct arguments) throws IOException {
		RedisPoolConfig config = new RedisPoolConfig();

		// TODO log pool config

		config.setBlockWhenExhausted(caster.toBooleanValue(arguments.get("blockWhenExhausted", null), BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED));
		String evictionPolicyClassName = caster.toString(arguments.get("evictionPolicyClassName", null), null);
		if (!Util.isEmpty(evictionPolicyClassName)) config.setEvictionPolicyClassName(evictionPolicyClassName);
		config.setFairness(caster.toBooleanValue(arguments.get("fairness", null), BaseObjectPoolConfig.DEFAULT_FAIRNESS));
		config.setLifo(caster.toBooleanValue(arguments.get("lifo", null), BaseObjectPoolConfig.DEFAULT_LIFO));
		config.setMaxIdle(caster.toIntValue(arguments.get("maxIdle", null), GenericObjectPoolConfig.DEFAULT_MAX_IDLE));
		int maxTotal = caster.toIntValue(arguments.get("maxTotal", null), GenericObjectPoolConfig.DEFAULT_MAX_TOTAL);
		config.setMaxTotal(maxTotal);
		config.setMaxWaitMillis(caster.toLongValue(arguments.get("maxWaitMillis", null), GenericObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS));
		config.setMinEvictableIdleTimeMillis(caster.toLongValue(arguments.get("minEvictableIdleTimeMillis", null), GenericObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS));
		config.setTimeBetweenEvictionRunsMillis(
				caster.toLongValue(arguments.get("timeBetweenEvictionRunsMillis", null), GenericObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS));
		config.setMinIdle(caster.toIntValue(arguments.get("minIdle", null), 8));
		config.setNumTestsPerEvictionRun(caster.toIntValue(arguments.get("numTestsPerEvictionRun", null), GenericObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN));
		config.setSoftMinEvictableIdleTimeMillis(
				caster.toLongValue(arguments.get("softMinEvictableIdleTimeMillis", null), GenericObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS));

		Integer maxLowPrio = caster.toInteger(arguments.get("maxLowPriority", null), null);
		if (maxLowPrio != null) {
			if (maxLowPrio < 0) maxLowPrio = maxTotal + maxLowPrio;
			if (maxLowPrio < 1) maxLowPrio = null;
		}
		if (maxLowPrio == null) {
			maxLowPrio = maxTotal / 10;
			if (maxLowPrio == 0) maxLowPrio = maxTotal - 1;
			else maxLowPrio = maxTotal - maxLowPrio;
		}

		config.setMaxLowPriority(maxLowPrio);

		config.setTestOnCreate(false);
		config.setTestOnBorrow(true);
		config.setTestOnReturn(false);
		config.setTestWhileIdle(true);
		// config.setTestOnBorrow(caster.toBooleanValue(arguments.get("testOnBorrow", null), true));
		// config.setTestOnCreate(caster.toBooleanValue(arguments.get("testOnCreate", null),
		// GenericObjectPoolConfig.DEFAULT_TEST_ON_CREATE));
		// config.setTestOnReturn(caster.toBooleanValue(arguments.get("testOnReturn", null),
		// GenericObjectPoolConfig.DEFAULT_TEST_ON_RETURN));
		// config.setTestWhileIdle(caster.toBooleanValue(arguments.get("testWhileIdle", null),
		// GenericObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE));
		return config;
	}

	@Override
	public CacheEntry getCacheEntry(String skey) throws IOException {
		long cnt = counter();
		byte[] bkey = toKeyWithPrefix(skey);
		if (async) {
			NearCacheEntry val = storage.get(bkey);
			if (val != null) {
				cacheHitCount.incrementAndGet();
				// Clone if needed to prevent reference sharing
				if (alwaysClone) {
					return new NearCacheEntry(val.getByteKey(), cloneValue(val.getValue()), val.getExpires(), val.count());
				}
				return val;
			}
			storage.doJoin(cnt, true);
		}
		Redis conn = getConnection();
		try {
			byte[] val = null;
			try {
				val = (byte[]) conn.call("GET", bkey);
			}
			catch (Exception e) {
				if (log != null) log.error("redis-cache", e);
				String msg = e.getMessage() + "";
				if (msg.startsWith("WRONGTYPE")) val = (byte[]) conn.call("LPOP", bkey);
			}
			if (val == null) {
				cacheMissCount.incrementAndGet();
				throw new IOException("Cache key [" + skey + "] does not exists");
			}

			cacheHitCount.incrementAndGet();

			// Touch on access - reset TTL to idle timeout value
			if (touchOnAccess && idleTimeoutSeconds > 0) {
				touchKey(conn, bkey);
			}

			Object evaluated = safeDeserialize(val, skey);
			// Clone if needed to prevent reference sharing
			if (alwaysClone) {
				evaluated = cloneValue(evaluated);
			}
			return new RedisCacheEntry(this, bkey, evaluated, val.length);
		}
		catch (SocketException se) {
			invalidateConnection(conn);
			conn = null;
			throw se;
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	public Struct getPoolInfo() {
		while (pool == null) {
			if (log != null) log.debug("redis-cache", "waiting for the pool");
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
				if (log != null) log.error("redis-cache", e);
			}
		}
		Struct data = engine.getCreationUtil().createStruct();

		data.setEL("BorrowedCount", pool.getBorrowedCount());
		data.setEL("BlockWhenExhausted", pool.getBlockWhenExhausted());
		data.setEL("CreatedCount", pool.getCreatedCount());
		data.setEL("DestroyedByBorrowValidationCount", pool.getDestroyedByBorrowValidationCount());
		data.setEL("DestroyedByEvictorCount", pool.getDestroyedByEvictorCount());
		data.setEL("DestroyedCount", pool.getDestroyedCount());
		data.setEL("Fairness", pool.getFairness());
		data.setEL("Lifo", pool.getLifo());
		data.setEL("MaxBorrowWaitTimeMillis", pool.getMaxBorrowWaitTimeMillis());
		data.setEL("MaxIdle", pool.getMaxIdle());
		data.setEL("MaxTotal", pool.getMaxTotal());
		data.setEL("MaxLowPrio", pool.getMaxLowPriority());
		data.setEL("MaxWaitMillis", pool.getMaxWaitMillis());
		data.setEL("MeanActiveTimeMillis", pool.getMeanActiveTimeMillis());
		data.setEL("MeanBorrowWaitTimeMillis", pool.getMeanBorrowWaitTimeMillis());
		data.setEL("MeanIdleTimeMillis", pool.getMeanIdleTimeMillis());
		data.setEL("MinEvictableIdleTimeMillis", pool.getMinEvictableIdleTimeMillis());
		data.setEL("MinIdle", pool.getMinIdle());
		data.setEL("NumActive", pool.getNumActive());
		data.setEL("NumIdle", pool.getNumIdle());
		data.setEL("NumTestsPerEvictionRun", pool.getNumTestsPerEvictionRun());
		data.setEL("NumWaiters", pool.getNumWaiters());
		data.setEL("RemoveAbandonedOnBorrow", pool.getRemoveAbandonedOnBorrow());
		data.setEL("RemoveAbandonedOnMaintenance", pool.getRemoveAbandonedOnMaintenance());
		data.setEL("RemoveAbandonedTimeout", pool.getRemoveAbandonedTimeout());
		data.setEL("ReturnedCount", pool.getReturnedCount());
		data.setEL("SoftMinEvictableIdleTimeMillis", pool.getSoftMinEvictableIdleTimeMillis());
		data.setEL("TimeBetweenEvictionRunsMillis", pool.getTimeBetweenEvictionRunsMillis());

		return data;
	}

	DebugObject getDebugObject(Redis conn, byte[] bkey) throws IOException {
		Object res = conn.call("DEBUG", "OBJECT", bkey);
		DebugObject deObj = null;
		if (res instanceof byte[]) {
			deObj = InfoParser.parseDebugObject(null, new String((byte[]) res));
		}
		return deObj;
	}

	@Override
	public CacheEntry getCacheEntry(String skey, CacheEntry defaultValue) {
		long cnt = counter();
		byte[] bkey = toKeyWithPrefix(skey);
		if (async) {
			NearCacheEntry val = storage.get(bkey);
			if (val != null) {
				cacheHitCount.incrementAndGet();
				// Clone if needed to prevent reference sharing
				if (alwaysClone) {
					try {
						return new NearCacheEntry(val.getByteKey(), cloneValue(val.getValue()), val.getExpires(), val.count());
					}
					catch (IOException e) {
						if (log != null) log.error("redis-cache", e);
						return defaultValue;
					}
				}
				return val;
			}
			storage.doJoin(cnt, true);
		}
		Redis conn = null;
		try {
			conn = getConnection();
		}
		catch (IOException e1) {
			if (log != null) log.error("redis-cache", e1);
			cacheMissCount.incrementAndGet();
			return defaultValue;
		}
		try {
			byte[] val = null;
			try {
				val = (byte[]) conn.call("GET", bkey);
			}
			catch (Exception e) {
				if (log != null) log.error("redis-cache", e);
				String msg = e.getMessage() + "";
				if (msg.startsWith("WRONGTYPE")) val = (byte[]) conn.call("LPOP", bkey);
			}
			if (val == null) {
				cacheMissCount.incrementAndGet();
				return defaultValue;
			}

			cacheHitCount.incrementAndGet();

			// Touch on access - reset TTL to idle timeout value
			if (touchOnAccess && idleTimeoutSeconds > 0) {
				touchKey(conn, bkey);
			}

			// Use safe deserialization with default fallback
			Object evaluated = safeDeserializeOrDefault(val, skey, null);
			if (evaluated == null) {
				// Deserialization failed, treat as a miss
				cacheMissCount.incrementAndGet();
				cacheHitCount.decrementAndGet();
				return defaultValue;
			}
			// Clone if needed to prevent reference sharing
			if (alwaysClone) {
				try {
					evaluated = cloneValue(evaluated);
				}
				catch (IOException e) {
					if (log != null) log.error("redis-cache", "Clone failed: " + e.getMessage());
					return defaultValue;
				}
			}
			return new RedisCacheEntry(this, bkey, evaluated, val.length);
		}
		catch (Exception e) {
			if (log != null) log.error("redis-cache", e);
			invalidateConnection(conn);
			conn = null;
			return defaultValue;
		}
		finally {
			releaseConnectionEL(conn);
		}
	}

	/**
	 * Touch a key - reset its TTL to the idle timeout value.
	 * This implements sliding expiration for cache entries.
	 *
	 * @param conn The Redis connection
	 * @param bkey The key to touch
	 */
	private void touchKey(Redis conn, byte[] bkey) {
		try {
			conn.call("EXPIRE", bkey, String.valueOf(idleTimeoutSeconds));
		}
		catch (Exception e) {
			// Log but don't fail the get operation
			if (log != null) log.warn("redis-cache", "Failed to touch key: " + e.getMessage());
		}
	}

	@Override
	public void put(String key, Object val, Long idle, Long live) throws IOException {
		long cnt = counter();
		// expires
		int exp;
		if (live != null && live.longValue() > 0) {
			exp = (int) (live.longValue() / 1000);
			if (exp < 1) exp = 1;
		}
		else if (idle != null && idle.longValue() > 0) {
			exp = (int) (idle.longValue() / 1000);
			if (exp < 1) exp = 1;
		}
		else {
			exp = defaultExpire;
		}
		byte[] bkey = toKeyWithPrefix(key);

		if (async) {
			storage.put(bkey, val, exp, cnt);
		}
		else put(bkey, val, exp);
	}

	private void put(byte[] bkey, Object val, int exp) throws IOException {

		Redis conn = getConnection();
		try {
			if (exp > 0) {
				// Use atomic SET with EX to avoid race condition between SET and EXPIRE
				conn.call("SET", bkey, Coder.serialize(val), "EX", String.valueOf(exp));
			}
			else {
				conn.call("SET", bkey, Coder.serialize(val));
			}
			cachePutCount.incrementAndGet();
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	@Override
	public boolean contains(String key) throws IOException {
		byte[] bkey = toKeyWithPrefix(key);
		if (async) {
			NearCacheEntry val = storage.get(bkey);
			if (val != null) return true;
		}
		Redis conn = getConnection();
		try {
			return engine.getCastUtil().toBooleanValue(conn.call("EXISTS", bkey));
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	@Override
	public boolean remove(String key) throws IOException {
		if (async) storage.doJoin(counter(), false);

		Redis conn = getConnection();
		try {
			boolean result = engine.getCastUtil().toBooleanValue(conn.call("DEL", toKeyWithPrefix(key)));
			if (result) cacheRemoveCount.incrementAndGet();
			return result;
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	public boolean remove(String[] keys) throws IOException {
		if (keys == null || keys.length == 0) return false;
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection();
		try {
			byte[][] prefixedKeys = new byte[keys.length][];
			for (int i = 0; i < keys.length; i++) {
				prefixedKeys[i] = toKeyWithPrefix(keys[i]);
			}
			Long removed = engine.getCastUtil().toLong(conn.call("DEL", prefixedKeys), null);
			if (removed != null && removed > 0) cacheRemoveCount.addAndGet(removed);
			return removed != null && removed > 0;
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	@Override
	public int remove(CacheKeyFilter filter) throws IOException {
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection();
		try {
			List<byte[]> lkeys = _bkeys(conn, filter);
			if (lkeys == null || lkeys.size() == 0) return 0;
			Long rtn = engine.getCastUtil().toLong(conn.call("DEL", lkeys), null);
			if (rtn == null) return 0;
			cacheRemoveCount.addAndGet(rtn);
			return rtn.intValue();
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	@Override
	public List<String> keys() throws IOException {
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection();
		try {
			String pattern = getPatternWithPrefix("*");
			List<byte[]> bkeys = (List<byte[]>) conn.call("KEYS", pattern);
			return toListStripPrefix(bkeys);
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	// private Set<String> _keys(Jedis conn) throws IOException {
	// return conn.keys("*");
	// }

	@Override
	public List<String> keys(CacheKeyFilter filter) throws IOException {
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection();
		try {
			return _skeys(conn, filter);
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	private List<byte[]> _bkeys(Redis conn, CacheKeyFilter filter) throws IOException {
		boolean isWildCardFilter = CacheUtil.isWildCardFiler(filter);
		boolean all = isWildCardFilter || CacheUtil.allowAll(filter);
		String pattern = isWildCardFilter ? getPatternWithPrefix(filter.toPattern()) : getPatternWithPrefix("*");
		List<byte[]> skeys = (List<byte[]>) conn.call("KEYS", pattern);
		List<byte[]> list = new ArrayList<byte[]>();
		if (skeys == null || skeys.size() == 0) return list;

		Iterator<byte[]> it = skeys.iterator();
		byte[] key;
		while (it.hasNext()) {
			key = it.next();
			// For filter comparison, use the key without prefix
			String strippedKey = stripPrefix(key);
			if (all || filter.accept(strippedKey)) list.add(key);
		}
		return list;
	}

	private List<String> _skeys(Redis conn, CacheKeyFilter filter) throws IOException {
		boolean isWildCardFilter = CacheUtil.isWildCardFiler(filter);
		boolean all = isWildCardFilter || CacheUtil.allowAll(filter);
		String pattern = isWildCardFilter ? getPatternWithPrefix(filter.toPattern()) : getPatternWithPrefix("*");
		List<byte[]> skeys = (List<byte[]>) conn.call("KEYS", pattern);
		List<String> list = new ArrayList<String>();
		if (skeys == null || skeys.size() == 0) return list;

		Iterator<byte[]> it = skeys.iterator();
		byte[] key;
		while (it.hasNext()) {
			key = it.next();
			// Return key without prefix
			String strippedKey = stripPrefix(key);
			if (all || filter.accept(strippedKey)) list.add(strippedKey);
		}
		return list;
	}

	@Override
	public List<CacheEntry> entries() throws IOException {
		return entries((CacheKeyFilter) null);
	}

	@Override
	public List<CacheEntry> entries(CacheKeyFilter filter) throws IOException {
		if (async) storage.doJoin(counter(), false);

		Redis conn = getConnection();
		try {
			List<byte[]> lkeys = _bkeys(conn, filter);
			List<CacheEntry> list = new ArrayList<CacheEntry>();

			if (lkeys == null || lkeys.size() == 0) return list;

			byte[][] keys = lkeys.toArray(new byte[lkeys.size()][]);

			List<byte[]> values = (List<byte[]>) conn.call("MGET", keys);
			if (keys.length == values.size()) { // because this is not atomar, it is possible that a key expired in meantime, but we try this way,
												// because it is much faster than the else solution
				int i = 0;
				byte[] k;
				for (byte[] val: values) {
					k = keys[i++];
					Object evaluated = null;
					if (val != null) {
						evaluated = safeDeserializeOrDefault(val, stripPrefix(k), null);
					}
					list.add(new RedisCacheEntry(this, k, evaluated, val == null ? 0 : val.length));
				}
			}
			else {
				byte[] val;
				for (byte[] key: keys) {
					val = null;
					try {
						val = (byte[]) conn.call("GET", key);
					}
					catch (Exception jde) {
						if (log != null) log.error("redis-cache", jde);
					}
					if (val != null) {
						Object evaluated = safeDeserializeOrDefault(val, stripPrefix(key), null);
						if (evaluated != null) {
							list.add(new RedisCacheEntry(this, key, evaluated, val.length));
						}
					}
				}
			}
			return list;
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	// there was the wrong generic type defined in the older interface, because of that we do not define
	// a generic type at all here, just to be sure
	@Override
	public List values() throws IOException {
		return values((CacheKeyFilter) null);
	}

	// there was the wrong generic type defined in the older interface, because of that we do not define
	// a generic type at all here, just to be sure
	@Override
	public List values(CacheKeyFilter filter) throws IOException {
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection();
		try {
			List<byte[]> lkeys = _bkeys(conn, filter);
			List<Object> list = new ArrayList<Object>();

			if (lkeys == null || lkeys.size() == 0) return list;

			List<byte[]> values = (List<byte[]>) conn.call("MGET", lkeys);
			for (int i = 0; i < values.size(); i++) {
				byte[] val = values.get(i);
				if (val != null) {
					String keyForLog = i < lkeys.size() ? stripPrefix(lkeys.get(i)) : "unknown";
					Object evaluated = safeDeserializeOrDefault(val, keyForLog, null);
					if (evaluated != null) {
						list.add(evaluated);
					}
				}
			}
			return list;
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	@Override
	public long hitCount() {
		return cacheHitCount.get();
	}

	@Override
	public long missCount() {
		return cacheMissCount.get();
	}

	/**
	 * Get the total number of put operations.
	 */
	public long putCount() {
		return cachePutCount.get();
	}

	/**
	 * Get the total number of remove operations.
	 */
	public long removeCount() {
		return cacheRemoveCount.get();
	}

	/**
	 * Reset all cache statistics counters.
	 */
	public void resetStats() {
		cacheHitCount.set(0);
		cacheMissCount.set(0);
		cachePutCount.set(0);
		cacheRemoveCount.set(0);
	}

	@Override
	public Struct getCustomInfo() throws IOException {
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection();
		try {
			byte[] barr = (byte[]) conn.call("INFO");
			Struct data = barr == null ? engine.getCreationUtil().createStruct() : InfoParser.parse(CacheUtil.getInfo(this), new String((byte[]) conn.call("INFO"), Coder.UTF8));
			data.set("connectionPool", getPoolInfo());

			// Add cache statistics
			Struct stats = engine.getCreationUtil().createStruct();
			stats.set("hitCount", cacheHitCount.get());
			stats.set("missCount", cacheMissCount.get());
			stats.set("putCount", cachePutCount.get());
			stats.set("removeCount", cacheRemoveCount.get());
			long total = cacheHitCount.get() + cacheMissCount.get();
			if (total > 0) {
				stats.set("hitRatio", (double) cacheHitCount.get() / total);
			}
			else {
				stats.set("hitRatio", 0.0);
			}
			data.set("cacheStatistics", stats);

			// Add configuration info
			if (keyPrefix != null) {
				data.set("keyPrefix", keyPrefix);
			}

			return data;
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	public static List<String> toList(Collection<byte[]> keys) throws IOException {
		List<String> list = new ArrayList<String>();
		if (keys != null) {
			Iterator<byte[]> it = keys.iterator();
			while (it.hasNext()) {
				list.add(new String(it.next(), Coder.UTF8));
			}
		}
		return list;
	}

	/**
	 * Convert byte array keys to string list, stripping prefix if configured.
	 */
	protected List<String> toListStripPrefix(Collection<byte[]> keys) throws IOException {
		List<String> list = new ArrayList<String>();
		if (keys != null) {
			Iterator<byte[]> it = keys.iterator();
			while (it.hasNext()) {
				list.add(stripPrefix(it.next()));
			}
		}
		return list;
	}

	/*
	 * public static Array toArray(ClassLoader cl, Collection<byte[]> keys) throws IOException { Array
	 * array = CFMLEngineFactory.getInstance().getCreationUtil().createArray(); if (keys != null) {
	 * Iterator<byte[]> it = keys.iterator(); while (it.hasNext()) { array.appendEL(evalResult(cl,
	 * it.next())); } } return array; }
	 */

	// CachePro interface @Override
	public Cache decouple() {
		return this;
	}

	@Override
	public CacheEntry getQuiet(String key, CacheEntry defaultValue) {
		// TODO
		return getCacheEntry(key, defaultValue);
	}

	@Override
	public int clear() throws IOException {
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection();
		try {
			// Only delete keys matching our prefix (or all keys if no prefix)
			String pattern = getPatternWithPrefix("*");
			List<byte[]> bkeys = (List<byte[]>) conn.call("KEYS", pattern);
			if (bkeys == null || bkeys.size() == 0) return 0;
			return engine.getCastUtil().toIntValue(conn.call("DEL", bkeys), 0);
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	protected Redis getConnection() throws IOException {
		return getConnection(false, 0);
	}

	protected Redis getConnection(boolean onlyIfSufficent, long timeout) throws IOException {
		// Check circuit breaker first for fast fail
		if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
			throw new IOException("Circuit breaker is open - Redis operations temporarily disabled. Failures: " + circuitBreaker.getFailureCount());
		}

		long start = timeout > 0 ? System.currentTimeMillis() : 0;
		while (pool == null) {
			if (log != null) log.debug("redis-cache", "waiting for the pool");
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
				if (log != null) log.error("redis-cache", e);
			}
		}

		if (log != null) {
			log.debug("redis-cache", "SocketUtil.getConnection before now actives : " + pool.getNumActive() + ", idle : " + pool.getNumIdle() + "; maxTotal: " + pool.getMaxTotal()
					+ "; MaxLowPriority: " + pool.getMaxLowPriority());
		}

		Redis redis = null;
		if (onlyIfSufficent) {
			synchronized (token) {
				do {
					if (redis != null) {
						try {
							pool.returnObject(redis);
						}
						catch (Exception e) {
							throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						}
					}
					while (pool.getMaxLowPriority() <= pool.getNumActive()) {
						try {
							if (timeout > 0 && log != null) {
								log.debug("redis-cache", "wait for a connection to get free for low priority. actives : " + pool.getNumActive() + ", idle : " + pool.getNumIdle()
										+ "; maxTotal: " + pool.getMaxTotal() + "; MaxLowPriority: " + pool.getMaxLowPriority());
							}
							token.wait(3000);
							if (timeout > 0 && timeout < (System.currentTimeMillis() - start)) {
								throw new IOException("could not aquire a connection within the given time (connection timeout " + timeout + "ms) with low priority.");
							}
						}
						catch (Exception e) {
							throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						}
					}
					redis = borrow(timeout);

				}
				while (pool.getMaxTotal() <= pool.getNumActive());

			}
		}
		else {
			redis = borrow(timeout);

		}

		if (log != null) {
			int actives = pool.getNumActive();
			int idle = pool.getNumIdle();
			log.debug("redis-cache", "SocketUtil.getConnection after now actives : " + actives + ", idle : " + idle);
		}

		return redis;
	}

	private Redis borrow(long timeout) throws IOException {
		// Use retry logic if resilience is enabled
		if (resilientOps != null && retryPolicy != null) {
			return retryPolicy.execute(() -> borrowOnce(timeout), "borrow connection");
		}
		return borrowOnce(timeout);
	}

	private Redis borrowOnce(long timeout) throws IOException {
		try {
			Redis redis;
			if (timeout > 0) {
				redis = pool.borrowObject(timeout);
				if (redis == null) throw new IOException("could not acquire a connection within the given time (connection timeout " + timeout + "ms).");
				return redis;
			}
			redis = pool.borrowObject();
			if (redis == null) throw new IOException("could not acquire a connection.");
			return redis;
		}
		catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
		}
	}

	protected void releaseConnection(Redis conn) throws IOException {
		if (conn == null) return;

		try {
			pool.returnObject(conn);
		}
		catch (Exception e) {
			if (log != null) log.error("redis-cache", e);
			Socket socket = conn.getSocket();
			if (socket != null) {
				try {
					socket.close();
				}
				catch (Exception ex) {
					if (log != null) log.error("redis-cache", ex);
				}
			}
			throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
		}
	}

	protected void releaseConnectionEL(Redis conn) {
		if (conn == null) return;
		try {
			pool.returnObject(conn);
		}
		catch (Exception e) {
			if (log != null) log.error("redis-cache", e);
			Socket socket = conn.getSocket();
			if (socket != null) {
				try {
					socket.close();
				}
				catch (Exception ex) {
					if (log != null) log.error("redis-cache", ex);
				}
			}
		}
	}

	// CachePro interface @Override
	public void verify() throws IOException {
		Redis conn = getConnection();
		try {
			String res = new String((byte[]) conn.call("PING"), Coder.UTF8);
			if ("PONG".equals(res)) return;

			throw new CacheException("Could connect to Redis, but Redis did not answer to the ping as expected (response:" + res + ")");
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	private static class Storage extends Thread {

		private ConcurrentLinkedDeque<NearCacheEntry> entries;
		private RedisCache cache;
		private CFMLEngine engine;
		private long current = Long.MIN_VALUE;
		private final Object tokenAddToNear = new Object();
		private final Object tokenAddToCache = new Object();
		private volatile boolean running = true;
		private volatile boolean shuttingDown = false;

		public Storage(RedisCache cache) {
			this.engine = CFMLEngineFactory.getInstance();
			this.cache = cache;
			this.entries = new ConcurrentLinkedDeque<>();
			setName("redis-storage-" + System.identityHashCode(cache));
			setDaemon(true);
		}

		/**
		 * Signal the thread to stop gracefully.
		 * Will finish processing any pending entries before stopping.
		 */
		public void shutdown() {
			shuttingDown = true;
			running = false;
			synchronized (tokenAddToNear) {
				tokenAddToNear.notifyAll();
			}
		}

		/**
		 * Check if shutdown has been requested.
		 */
		public boolean isShuttingDown() {
			return shuttingDown;
		}

		public NearCacheEntry get(byte[] bkey) {
			if (entries.isEmpty()) return null;
			Object[] arr = entries.toArray();
			NearCacheEntry e;
			for (Object obj: arr) {
				e = (NearCacheEntry) obj;
				if (equals(e.getByteKey(), bkey)) return e;
			}
			return null;
		}

		public long getCurrent() {
			return current;
		}

		public void doJoin(long count, boolean one) {
			if (shuttingDown) return;

			long startCurr = getCurrent();
			if (startCurr > count || entries.isEmpty()) {
				return;
			}

			// wait until it is done, but not to long in case of a constant stream
			int max = 100;
			long curr;
			// if we have entries, we wait until there are no entries anymore or they are newer than the request
			while (running && !shuttingDown) {
				if (--max <= 0) {
					break;
				}
				curr = getCurrent();
				if (one && startCurr < curr) {
					break;
				}
				if (curr > count || entries.isEmpty()) {
					break;
				}
				synchronized (tokenAddToCache) {
					try {
						tokenAddToCache.wait(1000);
					}
					catch (Exception e) {
						if (cache.log != null) cache.log.error("redis-cache", e);
						break;
					}
				}
			}
		}

		public void put(byte[] bkey, Object val, int exp, long count) {
			entries.add(new NearCacheEntry(bkey, val, exp, count));
			synchronized (tokenAddToNear) {
				tokenAddToNear.notifyAll();
			}
		}

		@Override
		public void run() {
			if (cache.log != null) cache.log.debug("redis-cache", "Storage thread started");

			while (running) {
				NearCacheEntry entry;
				try {
					// Process all pending entries
					while ((entry = entries.poll()) != null) {
						current = entry.count();
						try {
							cache.put(entry.getByteKey(), entry.getValue(), entry.getExpires());
						}
						catch (IOException e) {
							if (cache.log != null) cache.log.error("redis-cache", "Failed to store entry: " + e.getMessage());
							// Re-queue the entry for retry if not shutting down
							if (!shuttingDown) {
								entries.addFirst(entry);
								// Wait before retrying
								Thread.sleep(1000);
								break;
							}
						}
						synchronized (tokenAddToCache) {
							tokenAddToCache.notifyAll();
						}
					}

					// Wait for more entries (unless shutting down)
					if (running && !shuttingDown) {
						synchronized (tokenAddToNear) {
							if (entries.isEmpty()) {
								tokenAddToNear.wait(5000); // Use timeout to periodically check running flag
							}
						}
					}
				}
				catch (InterruptedException e) {
					if (cache.log != null) cache.log.debug("redis-cache", "Storage thread interrupted");
					Thread.currentThread().interrupt();
					break;
				}
				catch (Throwable e) {
					if (cache.log != null) cache.log.error("redis-cache", e);
					if (running && !shuttingDown) {
						try {
							Thread.sleep(1000); // slow down in case of an issue
						}
						catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
			}

			// Final flush - process any remaining entries
			if (cache.log != null) cache.log.debug("redis-cache", "Storage thread shutting down, flushing " + entries.size() + " remaining entries");
			NearCacheEntry entry;
			int remaining = entries.size();
			int flushed = 0;
			while ((entry = entries.poll()) != null && flushed < remaining + 10) { // Limit to prevent infinite loop
				try {
					cache.put(entry.getByteKey(), entry.getValue(), entry.getExpires());
					flushed++;
				}
				catch (IOException e) {
					if (cache.log != null) cache.log.error("redis-cache", "Failed to flush entry during shutdown: " + e.getMessage());
				}
			}

			if (cache.log != null) cache.log.debug("redis-cache", "Storage thread stopped, flushed " + flushed + " entries");
		}

		private static boolean equals(byte[] left, byte[] right) {
			if (left.length != right.length) return false;

			for (int i = 0; i < left.length; i++) {
				if (left[i] != right[i]) return false;
			}
			return true;
		}
	}

	public Object command(String... arguments) throws IOException {
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection();
		try {
			return conn.call(Coder.toBytesArrays(arguments));
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	@Override
	public Object command(byte[][] arguments, boolean lowPrio) throws IOException {
		if (async) storage.doJoin(counter(), false);

		Redis conn = getConnection(lowPrio, this.connTimeout);
		try {
			return conn.call(arguments);
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	@Override
	public List<Object> command(List<byte[][]> arguments, boolean lowPrio) throws IOException {
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection(lowPrio, this.connTimeout);
		try {
			Pipeline pl = conn.pipeline();
			for (byte[][] args: arguments) {
				pl.call(args);
			}
			return pl.read();
		}
		catch (Exception e) {
			invalidateConnection(conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			releaseConnection(conn);
		}
	}

	public void invalidateConnection(Redis conn) {
		// Record failure in circuit breaker
		if (circuitBreaker != null) {
			circuitBreaker.recordFailure();
			if (log != null && circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
				log.warn("redis-cache", "Circuit breaker OPENED after " + circuitBreaker.getFailureCount() + " failures");
			}
		}
		try {
			if (conn != null) pool.invalidateObject(conn);
		}
		catch (Exception e) {
			if (log != null) log.error("redis-cache", e);
		}
	}

	public static synchronized long counter() {
		return ++counter;
	}

	public boolean isObjectSerialisationSupported() {
		return true;
	}

	/**
	 * Shutdown the cache and release all resources.
	 * This method should be called when the cache is no longer needed.
	 */
	public void shutdown() {
		if (log != null) log.debug("redis-cache", "Shutting down Redis cache");

		// Shutdown the storage thread
		if (storage != null) {
			storage.shutdown();
			try {
				storage.join(5000); // Wait up to 5 seconds for thread to finish
			}
			catch (InterruptedException e) {
				if (log != null) log.warn("redis-cache", "Interrupted while waiting for storage thread to stop");
				Thread.currentThread().interrupt();
			}
		}

		// Shutdown resilience components
		if (resilientOps != null) {
			resilientOps.shutdown();
		}

		// Close the pool
		if (pool != null) {
			pool.close();
		}

		if (log != null) log.debug("redis-cache", "Redis cache shutdown complete");
	}

	/**
	 * Check if the circuit breaker is currently open (failing fast).
	 *
	 * @return true if circuit breaker is open
	 */
	public boolean isCircuitBreakerOpen() {
		return circuitBreaker != null && circuitBreaker.isOpen();
	}

	/**
	 * Get the current circuit breaker state.
	 *
	 * @return Circuit breaker state, or null if not enabled
	 */
	public CircuitBreaker.State getCircuitBreakerState() {
		return circuitBreaker != null ? circuitBreaker.getState() : null;
	}

	/**
	 * Reset the circuit breaker to allow operations again.
	 * Use this for manual recovery after fixing the underlying issue.
	 */
	public void resetCircuitBreaker() {
		if (circuitBreaker != null) {
			circuitBreaker.reset();
			if (log != null) log.info("redis-cache", "Circuit breaker manually reset");
		}
	}

	/**
	 * Check if resilience features are enabled.
	 */
	public boolean isResilienceEnabled() {
		return resilienceEnabled;
	}

	private String choose(String v1, String v2) {
		if (!Util.isEmpty(v1, true)) return v1.trim();
		if (!Util.isEmpty(v2, true)) return v2.trim();
		return null;
	}

	private int choose(int v1, int v2) {
		if (v1 != 0) return v1;
		if (v2 != 0) return v2;
		return 0;
	}

	/**
	 * Safely deserialize a byte array into an object with comprehensive error handling.
	 * This method provides better error messages and logging when deserialization fails.
	 *
	 * @param data The byte array to deserialize
	 * @param keyForLogging The key being accessed (for error logging)
	 * @return The deserialized object, or the raw data as string if deserialization fails
	 * @throws IOException If deserialization fails and cannot recover
	 */
	protected Object safeDeserialize(byte[] data, String keyForLogging) throws IOException {
		if (data == null) {
			return null;
		}

		try {
			return Coder.evaluate(cl, data);
		}
		catch (IOException e) {
			// Log the error with context
			if (log != null) {
				log.error("redis-cache", "Deserialization failed for key [" + keyForLogging + "]: " + e.getMessage() + ". Data length: " + data.length + " bytes.");
			}

			// Check if this looks like it might be a class loading issue
			String message = e.getMessage();
			if (message != null && (message.contains("ClassNotFoundException") || message.contains("class not found"))) {
				if (log != null) {
					log.warn("redis-cache", "Class not found during deserialization. This may indicate version mismatch between servers or missing classes.");
				}
			}

			// Re-throw with more context
			throw new IOException("Failed to deserialize cache entry for key [" + keyForLogging + "]: " + e.getMessage(), e);
		}
		catch (Exception e) {
			// Catch any unexpected exceptions
			if (log != null) {
				log.error("redis-cache", "Unexpected error during deserialization for key [" + keyForLogging + "]: " + e.getClass().getName() + " - " + e.getMessage());
			}
			throw new IOException("Unexpected deserialization error for key [" + keyForLogging + "]", e);
		}
	}

	/**
	 * Safely deserialize with a default value on failure.
	 * Use this variant when you want graceful degradation instead of exceptions.
	 *
	 * @param data The byte array to deserialize
	 * @param keyForLogging The key being accessed
	 * @param defaultValue Value to return if deserialization fails
	 * @return The deserialized object, or defaultValue if deserialization fails
	 */
	protected Object safeDeserializeOrDefault(byte[] data, String keyForLogging, Object defaultValue) {
		if (data == null) {
			return defaultValue;
		}

		try {
			return Coder.evaluate(cl, data);
		}
		catch (Exception e) {
			if (log != null) {
				log.warn("redis-cache", "Deserialization failed for key [" + keyForLogging + "], returning default value: " + e.getMessage());
			}
			return defaultValue;
		}
	}

	/**
	 * Clone an object by serializing and deserializing it.
	 * This ensures that the returned object is completely independent of the cached version.
	 *
	 * @param value The value to clone
	 * @return A cloned copy of the value
	 * @throws IOException If serialization/deserialization fails
	 */
	protected Object cloneValue(Object value) throws IOException {
		if (value == null) {
			return null;
		}
		// Simple types don't need cloning (immutable)
		if (value instanceof String || value instanceof Number || value instanceof Boolean) {
			return value;
		}
		// Re-serialize and deserialize to create a true clone
		byte[] serialized = Coder.serialize(value);
		return Coder.evaluate(cl, serialized);
	}

	/**
	 * Check if always clone mode is enabled.
	 *
	 * @return true if objects are always cloned on retrieval
	 */
	public boolean isAlwaysClone() {
		return alwaysClone;
	}

	/**
	 * Apply key prefix to a key for namespace isolation.
	 *
	 * @param key The original key
	 * @return The prefixed key as bytes
	 */
	protected byte[] toKeyWithPrefix(String key) {
		byte[] baseKey = Coder.toKey(key);
		return applyPrefix(baseKey);
	}

	/**
	 * Apply key prefix to byte array key.
	 *
	 * @param key The original key as bytes
	 * @return The prefixed key as bytes
	 */
	protected byte[] applyPrefix(byte[] key) {
		if (keyPrefixBytes == null || keyPrefixBytes.length == 0) {
			return key;
		}
		byte[] prefixedKey = new byte[keyPrefixBytes.length + key.length];
		System.arraycopy(keyPrefixBytes, 0, prefixedKey, 0, keyPrefixBytes.length);
		System.arraycopy(key, 0, prefixedKey, keyPrefixBytes.length, key.length);
		return prefixedKey;
	}

	/**
	 * Strip key prefix from a key.
	 *
	 * @param key The prefixed key as bytes
	 * @return The original key without prefix as string
	 */
	protected String stripPrefix(byte[] key) {
		if (keyPrefixBytes == null || keyPrefixBytes.length == 0) {
			return new String(key, Coder.UTF8);
		}
		if (key.length > keyPrefixBytes.length) {
			byte[] stripped = new byte[key.length - keyPrefixBytes.length];
			System.arraycopy(key, keyPrefixBytes.length, stripped, 0, stripped.length);
			return new String(stripped, Coder.UTF8);
		}
		return new String(key, Coder.UTF8);
	}

	/**
	 * Get the pattern for KEYS command with prefix applied.
	 *
	 * @param pattern The original pattern
	 * @return The prefixed pattern
	 */
	protected String getPatternWithPrefix(String pattern) {
		if (keyPrefix == null) {
			return pattern;
		}
		return keyPrefix + pattern;
	}

	/**
	 * Get the current key prefix.
	 *
	 * @return The key prefix or null if not set
	 */
	public String getKeyPrefix() {
		return keyPrefix;
	}

	/**
	 * Check if session locking is enabled.
	 *
	 * @return true if session locking is enabled
	 */
	public boolean isSessionLockingEnabled() {
		return sessionLockingEnabled;
	}

	/**
	 * Get the SessionLockManager for this cache.
	 * Creates the manager lazily on first access.
	 *
	 * @return The SessionLockManager instance
	 */
	public synchronized SessionLockManager getSessionLockManager() {
		if (sessionLockManager == null) {
			String lockPrefix = keyPrefix != null ? keyPrefix + "_lock:" : "_lock:";
			sessionLockManager = new SessionLockManager(this, sessionLockExpiration, sessionLockTimeout, lockPrefix);
		}
		return sessionLockManager;
	}

	/**
	 * Convenience method to acquire a session lock.
	 * Only works if session locking is enabled.
	 *
	 * @param sessionKey The session key to lock
	 * @return A Lock object, or null if locking is disabled or lock could not be acquired
	 * @throws IOException If a Redis error occurs
	 */
	public SessionLockManager.Lock acquireSessionLock(String sessionKey) throws IOException {
		if (!sessionLockingEnabled) {
			return null;
		}
		return getSessionLockManager().acquireLock(sessionKey);
	}

	/**
	 * Convenience method to try acquiring a session lock without blocking.
	 * Only works if session locking is enabled.
	 *
	 * @param sessionKey The session key to lock
	 * @return A Lock object, or null if locking is disabled or lock is already held
	 * @throws IOException If a Redis error occurs
	 */
	public SessionLockManager.Lock tryAcquireSessionLock(String sessionKey) throws IOException {
		if (!sessionLockingEnabled) {
			return null;
		}
		return getSessionLockManager().tryAcquireLock(sessionKey);
	}

	/**
	 * Get a metrics exporter for this cache.
	 *
	 * @param cacheName The name to use in metric labels
	 * @return A RedisCacheMetrics instance
	 */
	public RedisCacheMetrics getMetrics(String cacheName) {
		return new RedisCacheMetrics(this, cacheName);
	}

	/**
	 * Get Prometheus-formatted metrics for this cache.
	 *
	 * @param cacheName The name to use in metric labels
	 * @return Prometheus-formatted metrics string
	 */
	public String getPrometheusMetrics(String cacheName) {
		return new RedisCacheMetrics(this, cacheName).exportPrometheusMetrics();
	}

	/**
	 * Get JSON-formatted metrics for this cache.
	 *
	 * @param cacheName The name to use in metric labels
	 * @return JSON-formatted metrics string
	 */
	public String getJsonMetrics(String cacheName) {
		return new RedisCacheMetrics(this, cacheName).exportJsonMetrics();
	}

}