package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

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

	private final Object token = new Object();

	/**
	 * Boxed type is to support representing the null case
	 */
	private Integer __test__writeCommitDelay_ms = null;
	public Integer get__test__writeCommitDelay_ms() {
		return __test__writeCommitDelay_ms;
	}

	public RedisCache() {
		if (async) {
			// storage = new Storage(this);
			storage.start();
		}
	}

	public static void init(Config config, String[] cacheName, Struct[] arguments) {
		// Not used at the moment
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

		__test__writeCommitDelay_ms = caster.toIntValue(arguments.get("__test__writeCommitDelay_ms", null), 0);
		__test__writeCommitDelay_ms = __test__writeCommitDelay_ms <= 0 ? null : __test__writeCommitDelay_ms;

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
		byte[] bkey = Coder.toKey(skey);
		if (async) {
			NearCacheEntry val = storage.get(bkey);
			if (val != null) {
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
			if (val == null) throw new IOException("Cache key [" + skey + "] does not exists");

			return new RedisCacheEntry(this, bkey, Coder.evaluate(cl, val), val.length);
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
		byte[] bkey = Coder.toKey(skey);
		if (async) {
			NearCacheEntry val = storage.get(bkey);
			if (val != null) {
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
			if (val == null) return defaultValue;

			return new RedisCacheEntry(this, bkey, Coder.evaluate(cl, val), val.length);
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
		byte[] bkey = Coder.toKey(key);

		if (async) {
			storage.put(bkey, val, exp, cnt);
		}
		else put(bkey, val, exp);
	}

	private void put(byte[] bkey, Object val, int exp) throws IOException {

		Redis conn = getConnection();
		try {
			if (exp > 0) {
				conn.pipeline().call("SET", bkey, Coder.serialize(val)).call("EXPIRE", bkey, Integer.toString(exp)).read();
			}
			else {
				conn.call("SET", bkey, Coder.serialize(val));
			}
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
		byte[] bkey = Coder.toKey(key);
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
			return engine.getCastUtil().toBooleanValue(conn.call("DEL", Coder.toKey(key)));
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
			return engine.getCastUtil().toBooleanValue(conn.call("DEL", Coder.toKeys(keys)));
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
			return toList((List<byte[]>) conn.call("KEYS", "*"));
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
		List<byte[]> skeys = (List<byte[]>) conn.call("KEYS", isWildCardFilter ? filter.toPattern() : "*");
		List<byte[]> list = new ArrayList<byte[]>();
		if (skeys == null || skeys.size() == 0) return list;

		Iterator<byte[]> it = skeys.iterator();
		byte[] key;
		while (it.hasNext()) {
			key = it.next();
			if (all || filter.accept(new String(key, UTF8))) list.add(key);
		}
		return list;
	}

	private List<String> _skeys(Redis conn, CacheKeyFilter filter) throws IOException {
		boolean isWildCardFilter = CacheUtil.isWildCardFiler(filter);
		boolean all = isWildCardFilter || CacheUtil.allowAll(filter);
		List<byte[]> skeys = (List<byte[]>) conn.call("KEYS", isWildCardFilter ? filter.toPattern() : "*");
		List<String> list = new ArrayList<String>();
		Iterator<byte[]> it = skeys.iterator();
		if (skeys == null || skeys.size() == 0) return list;

		byte[] key;
		while (it.hasNext()) {
			key = it.next();
			if (all || filter.accept(new String(key, UTF8))) list.add(new String(key, UTF8));
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
					list.add(new RedisCacheEntry(this, k, val == null ? null : Coder.evaluate(cl, val), val == null ? 0 : val.length));
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
					if (val != null) list.add(new RedisCacheEntry(this, key, Coder.evaluate(cl, val), val.length));
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
			for (byte[] val: values) {
				list.add(Coder.evaluate(cl, val));
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
		return 0; // TODO To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public long missCount() {
		return 0; // TODO To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public Struct getCustomInfo() throws IOException {
		if (async) storage.doJoin(counter(), false);
		Redis conn = getConnection();
		try {
			byte[] barr = (byte[]) conn.call("INFO");
			Struct data = barr == null ? engine.getCreationUtil().createStruct() : InfoParser.parse(CacheUtil.getInfo(this), new String((byte[]) conn.call("INFO"), Coder.UTF8));
			data.set("connectionPool", getPoolInfo());
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
			List<byte[]> bkeys = (List<byte[]>) conn.call("KEYS", "*");
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
		try {
			Redis redis;
			if (timeout > 0) {
				redis = pool.borrowObject(timeout);
				if (redis == null) throw new IOException("could not aquire a connection within the given time (connection timeout " + timeout + "ms).");
				return redis;
			}
			redis = pool.borrowObject();
			if (redis == null) throw new IOException("could not aquire a lock.");
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

		public Storage(RedisCache cache) {
			this.engine = CFMLEngineFactory.getInstance();
			this.cache = cache;
			this.entries = new ConcurrentLinkedDeque<>();
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

			long startCurr = getCurrent();
			if (startCurr > count || entries.isEmpty()) {
				return;
			}

			// wait until it is done, but not to long in case of a constant stream
			int max = 100;
			long curr;
			// if we have entries, we wait until there are no entries anymore or they are newer than the request
			while (true) {
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
			while (true) {
				NearCacheEntry entry;
				try {
					while ((entry = entries.poll()) != null) {
						current = entry.count();
						cache.put(entry.getByteKey(), entry.getValue(), entry.getExpires());
						synchronized (tokenAddToCache) {
							tokenAddToCache.notifyAll();
						}
					}
					synchronized (tokenAddToNear) {
						if (entries.isEmpty()) tokenAddToNear.wait();
					}

					if (cache.get__test__writeCommitDelay_ms() != null) {
						Thread.sleep(cache.get__test__writeCommitDelay_ms());
					}
				}
				catch (Throwable e) {
					if (cache.log != null) cache.log.error("redis-cache", e);
					synchronized (this) {
						try {
							this.wait(1000); // slow down in case of an issue
						}
						catch (Exception ie) {
							if (cache.log != null) cache.log.error("redis-cache", ie);
						}
					}
				}
			}
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

}