package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.pool2.impl.BaseObjectPoolConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import lucee.commons.io.cache.Cache;
import lucee.commons.io.cache.CacheEntry;
import lucee.commons.io.cache.CacheKeyFilter;
import lucee.commons.io.cache.exp.CacheException;
import lucee.extension.io.cache.pool.RedisFactory;
import lucee.extension.io.cache.redis.InfoParser.DebugObject;
import lucee.extension.io.cache.util.Coder;
import lucee.extension.io.cache.util.RedisUtil;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.config.Config;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;

public class RedisCache extends CacheSupport {
	protected final Object TOKEN = new Object();

	protected CFMLEngine engine = CFMLEngineFactory.getInstance();
	protected Cast caster = engine.getCastUtil();

	protected int timeout;
	protected String password;

	private ClassLoader cl;

	// config pool
	private int defaultExpire;

	private boolean debug;

	private GenericObjectPool<Redis> pool;

	private String host;
	private int port;

	private String username;

	public void init(Config config, String[] cacheName, Struct[] arguments) {
		// Not used at the moment
		this.cl = config.getClass().getClassLoader();
	}

	@Override
	public void init(Config config, String cacheName, Struct arguments) throws IOException {
		init(arguments);
	}

	public void init(Struct arguments) throws IOException {
		this.cl = arguments.getClass().getClassLoader();

		host = caster.toString(arguments.get("host", "localhost"), "localhost");
		port = caster.toIntValue(arguments.get("port", null), 6379);

		timeout = caster.toIntValue(arguments.get("timeout", null), 2000);
		username = caster.toString(arguments.get("username", null), null);
		if (Util.isEmpty(username)) username = null;

		password = caster.toString(arguments.get("password", null), null);
		if (Util.isEmpty(password)) password = null;

		defaultExpire = caster.toIntValue(arguments.get("timeToLiveSeconds", null), 0);

		debug = caster.toBooleanValue(arguments.get("debug", null), false);

		pool = new GenericObjectPool<Redis>(new RedisFactory(cl, host, port, username, password, debug), getPoolConfig(arguments));

	}

	protected GenericObjectPoolConfig getPoolConfig(Struct arguments) throws IOException {
		GenericObjectPoolConfig config = new GenericObjectPoolConfig();

		config.setBlockWhenExhausted(caster.toBooleanValue(arguments.get("blockWhenExhausted", null), BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED));
		String evictionPolicyClassName = caster.toString(arguments.get("evictionPolicyClassName", null), null);
		if (!Util.isEmpty(evictionPolicyClassName)) config.setEvictionPolicyClassName(evictionPolicyClassName);
		config.setFairness(caster.toBooleanValue(arguments.get("fairness", null), BaseObjectPoolConfig.DEFAULT_FAIRNESS));
		config.setLifo(caster.toBooleanValue(arguments.get("lifo", null), BaseObjectPoolConfig.DEFAULT_LIFO));
		config.setMaxIdle(caster.toIntValue(arguments.get("maxIdle", null), GenericObjectPoolConfig.DEFAULT_MAX_IDLE));
		config.setMaxTotal(caster.toIntValue(arguments.get("maxTotal", null), GenericObjectPoolConfig.DEFAULT_MAX_TOTAL));
		config.setMaxWaitMillis(caster.toLongValue(arguments.get("maxWaitMillis", null), GenericObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS));
		config.setMinEvictableIdleTimeMillis(caster.toLongValue(arguments.get("minEvictableIdleTimeMillis", null), GenericObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS));
		config.setMinIdle(caster.toIntValue(arguments.get("minIdle", null), GenericObjectPoolConfig.DEFAULT_MIN_IDLE));
		config.setNumTestsPerEvictionRun(caster.toIntValue(arguments.get("numTestsPerEvictionRun", null), GenericObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN));
		config.setSoftMinEvictableIdleTimeMillis(
				caster.toLongValue(arguments.get("softMinEvictableIdleTimeMillis", null), GenericObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS));
		config.setTestOnBorrow(caster.toBooleanValue(arguments.get("testOnBorrow", null), GenericObjectPoolConfig.DEFAULT_TEST_ON_BORROW));
		config.setTestOnCreate(caster.toBooleanValue(arguments.get("testOnCreate", null), GenericObjectPoolConfig.DEFAULT_TEST_ON_CREATE));
		config.setTestOnReturn(caster.toBooleanValue(arguments.get("testOnReturn", null), GenericObjectPoolConfig.DEFAULT_TEST_ON_RETURN));
		config.setTestWhileIdle(caster.toBooleanValue(arguments.get("testWhileIdle", null), GenericObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE));
		return config;
	}

	@Override
	public CacheEntry getCacheEntry(String skey) throws IOException {
		Redis conn = getConnection();
		byte[] bkey = Coder.toKey(skey);
		try {
			byte[] val = null;
			try {
				val = (byte[]) conn.call("GET", bkey);
			}
			catch (Exception e) {
				String msg = e.getMessage() + "";
				if (msg.startsWith("WRONGTYPE")) val = (byte[]) conn.call("LPOP", bkey);
			}
			if (val == null) throw new IOException("Cache key [" + skey + "] does not exists");

			return new RedisCacheEntry(this, bkey, Coder.evaluate(cl, val), val.length, getDebugObject(conn, bkey));
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	private DebugObject getDebugObject(Redis conn, byte[] bkey) throws IOException {
		Object res = conn.call("DEBUG", "OBJECT", bkey);
		DebugObject deObj = null;
		if (res instanceof byte[]) {
			deObj = InfoParser.parseDebugObject(null, new String((byte[]) res));
		}
		return deObj;
	}

	@Override
	public CacheEntry getCacheEntry(String key, CacheEntry defaultValue) {
		try {
			return getCacheEntry(key);
		}
		catch (IOException e) {
			return defaultValue;
		}
	}

	@Override
	public void put(String key, Object val, Long idle, Long expire) throws IOException {
		Redis conn = getConnection();
		try {
			byte[] bkey = Coder.toKey(key);
			conn.call("SET", bkey, Coder.serialize(val));
			int ex = 0;
			if (expire != null) {
				ex = (int) (expire / 1000);
			}
			else {
				ex = defaultExpire;
			}

			if (ex > 0) {
				conn.call("EXPIRE", bkey, Integer.toString(ex));
			}
		}
		catch (IOException ioe) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw ioe;
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	@Override
	public boolean contains(String key) throws IOException {
		Redis conn = getConnection();
		try {
			return engine.getCastUtil().toBooleanValue(conn.call("EXISTS", Coder.toKey(key)));
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	@Override
	public boolean remove(String key) throws IOException {
		Redis conn = getConnection();
		try {
			return engine.getCastUtil().toBooleanValue(conn.call("DEL", Coder.toKey(key)));
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	public boolean remove(String[] keys) throws IOException {
		Redis conn = getConnection();
		try {
			return engine.getCastUtil().toBooleanValue(conn.call("DEL", Coder.toKeys(keys)));
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	@Override
	public int remove(CacheKeyFilter filter) throws IOException {
		Redis conn = getConnection();
		try {
			List<byte[]> lkeys = _bkeys(conn, filter);
			if (lkeys == null || lkeys.size() == 0) return 0;
			Long rtn = engine.getCastUtil().toLong(conn.call("DEL", lkeys), null);
			if (rtn == null) return 0;
			return rtn.intValue();
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	@Override
	public List<String> keys() throws IOException {
		Redis conn = getConnection();
		try {
			return toList((List<byte[]>) conn.call("KEYS", "*"));
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	// private Set<String> _keys(Jedis conn) throws IOException {
	// return conn.keys("*");
	// }

	@Override
	public List<String> keys(CacheKeyFilter filter) throws IOException {
		Redis conn = getConnection();
		try {
			return _skeys(conn, filter);
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	private List<byte[]> _bkeys(Redis conn, CacheKeyFilter filter) throws IOException {
		boolean all = CacheUtil.allowAll(filter);
		List<byte[]> skeys = (List<byte[]>) conn.call("KEYS", "*");
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
		boolean all = CacheUtil.allowAll(filter);
		List<byte[]> skeys = (List<byte[]>) conn.call("KEYS", "*");
		List<String> list = new ArrayList<String>();
		Iterator<byte[]> it = skeys.iterator();
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
					list.add(new RedisCacheEntry(this, k, Coder.evaluate(cl, val), val.length, getDebugObject(conn, k)));
				}
			}
			else {
				byte[] val;
				for (byte[] key: keys) {
					val = null;
					try {
						val = (byte[]) conn.call("GET", key);
					}
					catch (Exception jde) {}
					if (val != null) list.add(new RedisCacheEntry(this, key, Coder.evaluate(cl, val), val.length, getDebugObject(conn, key)));
				}
			}
			return list;
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
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
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
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
		Redis conn = getConnection();
		try {
			return InfoParser.parse(CacheUtil.getInfo(this), new String((byte[]) conn.call("INFO"), Coder.UTF8));
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	private List<String> toList(Collection<byte[]> keys) throws IOException {
		List<String> list = new ArrayList<String>();
		Iterator<byte[]> it = keys.iterator();
		while (it.hasNext()) {
			list.add(new String(it.next(), Coder.UTF8));
		}
		return list;
	}

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
		Redis conn = getConnection();
		try {
			List<byte[]> bkeys = (List<byte[]>) conn.call("KEYS", "*");
			if (bkeys.size() == 0) return 0;
			return engine.getCastUtil().toIntValue(conn.call("DEL", bkeys), 0);
		}
		catch (Exception e) {
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}

	protected Redis getConnection() throws IOException {
		int actives = pool.getNumActive();
		int idle = pool.getNumIdle();
		if (debug) System.out.println("SocketUtil.getConnection before now actives : " + actives + ", idle : " + idle);

		if (debug) System.out.println(">>>>> borrowObject start");
		Redis redis;
		try {
			redis = pool.borrowObject();
		}
		catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
		}
		if (debug) System.out.println(">>>>> borrowObject end");

		actives = pool.getNumActive();
		idle = pool.getNumIdle();
		if (debug) System.out.println("SocketUtil.getConnection after now actives : " + actives + ", idle : " + idle);

		return redis;
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
			RedisUtil.invalidateObjectEL(pool, conn);
			conn = null;
			throw engine.getExceptionUtil().toIOException(e);
		}
		finally {
			if (conn != null) pool.returnObject(conn);
		}
	}
}