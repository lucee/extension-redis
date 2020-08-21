package lucee.extension.io.cache.redis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import lucee.commons.io.cache.Cache;
import lucee.commons.io.cache.CacheEntry;
import lucee.commons.io.cache.CacheKeyFilter;
import lucee.commons.io.cache.exp.CacheException;
import lucee.extension.io.cache.util.ObjectInputStreamImpl;
import lucee.extension.util.FunctionFactory;
import lucee.extension.util.Functions;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisDataException;

public abstract class AbstractRedisCache extends CacheSupport {
	protected final Object TOKEN = new Object();

	public Functions func;
	protected CFMLEngine engine = CFMLEngineFactory.getInstance();
	protected Cast caster = engine.getCastUtil();

	protected int timeout;
	protected String password;

	private int maxTotal;
	private int maxIdle;
	private int minIdle;
	private int defaultExpire;
	private ClassLoader cl;

	public void init(Config config, String[] cacheName, Struct[] arguments) {
		// Not used at the moment
		this.cl = config.getClass().getClassLoader();
	}

	public void init(Struct arguments) throws IOException {
		this.cl = arguments.getClass().getClassLoader();
		func = FunctionFactory.getInstance();
		timeout = caster.toIntValue(arguments.get("timeout", null), 2000);
		password = caster.toString(arguments.get("password", null), null);
		if (Util.isEmpty(password)) password = null;

		defaultExpire = caster.toIntValue(arguments.get("timeToLiveSeconds", null), 0);

		// for config
		maxTotal = caster.toIntValue(arguments.get("maxTotal", null), 0);
		maxIdle = caster.toIntValue(arguments.get("maxIdle", null), 0);
		minIdle = caster.toIntValue(arguments.get("minIdle", null), 0);

	}

	protected JedisPoolConfig getJedisPoolConfig() throws IOException {
		JedisPoolConfig config = new JedisPoolConfig();

		if (maxTotal > 0) config.setMaxTotal(maxTotal);
		if (maxIdle > 0) config.setMaxIdle(maxIdle);
		if (minIdle > 0) config.setMinIdle(minIdle);
		// config.setEvictionPolicyClassName();
		// config.setFairness();
		// config.setLifo();

		return config;
	}

	@Override
	public CacheEntry getCacheEntry(String skey) throws IOException {
		Jedis conn = jedisSilent();
		try {
			byte[] bkey = toKey(skey);
			byte[] val = null;
			try {
				val = conn.get(bkey);
			}
			catch (JedisDataException jde) {
				String msg = jde.getMessage() + "";
				if (msg.startsWith("WRONGTYPE")) val = conn.lpop(bkey);
			}
			if (val == null) throw new IOException("Cache key [" + skey + "] does not exists");
			return new RedisCacheEntry(this, bkey, evaluate(val), val.length);
		}
		catch (PageException e) {
			throw new RuntimeException(e);// not throwing IOException because Lucee 4.5
		}
		finally {
			RedisCacheUtils.close(conn);
		}
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
	public void put(String key, Object val, Long idle, Long expire) {
		Jedis conn = jedisSilent();
		try {
			byte[] bkey = toKey(key);
			conn.set(bkey, serialize(val));

			int ex = 0;
			if (expire != null) {
				ex = (int) (expire / 1000);
			}
			else {
				ex = defaultExpire;
			}

			if (ex > 0) {
				conn.expire(bkey, ex);
			}
		}
		catch (PageException e) {
			throw new RuntimeException(e);// not throwing IOException because Lucee 4.5
		}
		finally {
			RedisCacheUtils.close(conn);
		}
	}

	@Override
	public boolean contains(String key) {
		Jedis conn = jedisSilent();
		try {
			return conn.exists(toKey(key));
		}
		finally {
			RedisCacheUtils.close(conn);
		}
	}

	@Override
	public boolean remove(String key) throws IOException {
		Jedis conn = null;
		try {
			conn = jedis();
			return conn.del(toKey(key)) > 0;
		}
		finally {
			RedisCacheUtils.close(conn);
		}
	}

	public boolean remove(String[] keys) throws IOException {
		Jedis conn = null;
		try {
			conn = jedis();
			return conn.del(toKeys(keys)) > 0;
		}
		finally {
			RedisCacheUtils.close(conn);
		}
	}

	@Override
	public int remove(CacheKeyFilter filter) throws IOException {
		Jedis conn = jedisSilent();
		try {
			List<byte[]> lkeys = _bkeys(conn, filter);
			if (lkeys == null || lkeys.size() == 0) return 0;
			Long rtn = conn.del(lkeys.toArray(new byte[lkeys.size()][]));
			if (rtn == null) return 0;
			return rtn.intValue();
		}
		finally {
			RedisCacheUtils.close(conn);
		}
	}

	@Override
	public List<String> keys() throws IOException {
		Jedis conn = null;
		try {
			conn = jedis();
			return toList(conn.keys("*"));
		}
		finally {
			RedisCacheUtils.close(conn);
		}
	}

	// private Set<String> _keys(Jedis conn) throws IOException {
	// return conn.keys("*");
	// }

	@Override
	public List<String> keys(CacheKeyFilter filter) throws IOException {
		Jedis conn = null;
		try {
			conn = jedis();
			return _skeys(conn, filter);
		}
		finally {
			RedisCacheUtils.close(conn);
		}
	}

	private List<byte[]> _bkeys(Jedis conn, CacheKeyFilter filter) throws IOException {
		boolean all = CacheUtil.allowAll(filter);
		Set<byte[]> skeys = conn.keys("*".getBytes(UTF8));
		List<byte[]> list = new ArrayList<byte[]>();
		Iterator<byte[]> it = skeys.iterator();
		byte[] key;
		while (it.hasNext()) {
			key = it.next();
			if (all || filter.accept(new String(key, UTF8))) list.add(key);
		}
		return list;
	}

	private List<String> _skeys(Jedis conn, CacheKeyFilter filter) throws IOException {
		boolean all = CacheUtil.allowAll(filter);
		Set<byte[]> skeys = conn.keys("*".getBytes(UTF8));
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
		Jedis conn = jedisSilent();
		try {
			List<byte[]> lkeys = _bkeys(conn, filter);
			List<CacheEntry> list = new ArrayList<CacheEntry>();

			if (lkeys == null || lkeys.size() == 0) return list;

			byte[][] keys = lkeys.toArray(new byte[lkeys.size()][]);

			List<byte[]> values = conn.mget(keys);
			if (keys.length == values.size()) { // because this is not atomar, it is possible that a key expired in meantime, but we try this way,
												// because it is much faster than the else solution
				int i = 0;
				for (byte[] val: values) {
					list.add(new RedisCacheEntry(this, keys[i++], evaluate(val), val.length));
				}
			}
			else {
				byte[] val;
				for (byte[] key: keys) {
					val = null;
					try {
						val = conn.get(key);
					}
					catch (JedisDataException jde) {}
					if (val != null) list.add(new RedisCacheEntry(this, key, evaluate(val), val.length));
				}
			}
			return list;
		}
		catch (PageException e) {
			throw new RuntimeException(e);// not throwing IOException because Lucee 4.5
		}
		finally {
			RedisCacheUtils.close(conn);
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
		Jedis conn = jedisSilent();
		try {
			List<byte[]> lkeys = _bkeys(conn, filter);
			List<Object> list = new ArrayList<Object>();

			if (lkeys == null || lkeys.size() == 0) return list;

			List<byte[]> values = conn.mget(lkeys.toArray(new byte[lkeys.size()][]));
			for (byte[] val: values) {
				list.add(evaluate(val));
			}
			return list;
		}
		catch (PageException e) {
			throw new RuntimeException(e);// not throwing IOException because Lucee 4.5
		}
		finally {
			RedisCacheUtils.close(conn);
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
	public Struct getCustomInfo() {
		Jedis conn = jedisSilent();
		try {
			return InfoParser.parse(CacheUtil.getInfo(this), conn.info());// not throwing IOException because Lucee 4.5
		}

		finally {
			RedisCacheUtils.close(conn);
		}
	}

	/*
	 * private List entriesList(List keys) throws IOException { Jedis conn = jedis();
	 * ArrayList<RedisCacheEntry> res = null;
	 * 
	 * try { res = new ArrayList<RedisCacheEntry>(); Iterator<String> it = keys.iterator(); while
	 * (it.hasNext()) { String k = it.next(); res.add(new RedisCacheEntry(this, new RedisCacheItem(k,
	 * conn.get(k), settings.cacheName))); }
	 * 
	 * } finally { RedisCacheUtils.close(conn); } return res; }
	 */

	/*
	 * private List<String> sanitizeKeys(List<String> keys) throws IOException { for (int i = 0; i <
	 * keys.size(); i++) { keys.set(i, RedisCacheUtils.removeNamespace(settings.nameSpace,
	 * keys.get(i))); } return keys; }
	 */

	private List<String> toList(Set<String> keys) throws IOException {
		List<String> list = new ArrayList<String>();
		Iterator<String> it = keys.iterator();
		while (it.hasNext()) {
			list.add(it.next());
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
		Jedis conn = null;
		try {
			conn = jedis();
			Set<String> set = conn.keys("*");
			String[] keys = engine.getListUtil().toStringArray(set);
			if (keys.length == 0) return 0;
			return engine.getCastUtil().toIntValue(conn.del(keys), 0);
		}
		finally {
			RedisCacheUtils.close(conn);
		}
	}

	private byte[] toKey(String key) {
		return key.trim().toLowerCase().getBytes(UTF8);
	}

	private byte[][] toKeys(String[] keys) {
		byte[][] arr = new byte[keys.length][];
		for (int i = 0; i < keys.length; i++) {
			arr[i] = keys[i].trim().toLowerCase().getBytes(UTF8);
		}
		return arr;
	}

	private Object evaluate(byte[] data) throws PageException {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStreamImpl(cl, bais);
			return ois.readObject();
		}
		catch (StreamCorruptedException sce) {
			try {
				return evaluateLegacy(new String(data, "UTF-8"));
			}
			catch (UnsupportedEncodingException uee) {
				return evaluateLegacy(new String(data));
			}
		}
		catch (Exception e) {
			try {
				return evaluateLegacy(new String(data, "UDF-8"));
			}
			catch (UnsupportedEncodingException uee) {
				throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(e);
			}
		}
		finally {
			Util.closeEL(ois);
		}
	}

	private byte[] serialize(Object value) throws PageException {
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream(); // returns
			ObjectOutputStream oos = new ObjectOutputStream(os);
			oos.writeObject(value);
			oos.flush();
			return os.toByteArray();
		}
		catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(e);
		}
	}

	private Object evaluateLegacy(String val) throws PageException {
		// number
		if (val.startsWith("nbr(") && val.endsWith(")")) {
			// System.err.println("nbr:" + val + ":" + func.getClass().getName());
			return engine.getCastUtil().toDouble(val.substring(4, val.length() - 1));
		}
		// boolean
		else if (val.startsWith("bool(") && val.endsWith(")")) {
			// System.err.println("bool:" + val + ":" + func.getClass().getName());
			return engine.getCastUtil().toBoolean(val.substring(5, val.length() - 1));
		}
		// date
		else if (val.startsWith("date(") && val.endsWith(")")) {
			// System.err.println("date:" + val + ":" + func.getClass().getName());
			return engine.getCreationUtil().createDate(engine.getCastUtil().toLongValue(val.substring(5, val.length() - 1)));
		}
		// eval
		else if (val.startsWith("eval(") && val.endsWith(")")) {
			// System.err.println("eval:" + val + ":" + func.getClass().getName());
			return func.evaluate(val.substring(5, val.length() - 1));
		}
		// System.err.println("raw:" + val + ":" + func.getClass().getName());
		return val; // MUST
	}

	protected abstract Jedis _jedis() throws IOException;

	protected Jedis jedis() throws IOException {
		Jedis conn = _jedis();
		if (!conn.isConnected()) conn.connect();
		return conn;
	}

	protected Jedis jedisSilent() {
		Jedis conn = null;
		try {
			conn = jedis();
			return conn;
		}
		catch (Exception e) {
			RedisCacheUtils.close(conn);
			throw new RuntimeException(e);
		}
	}

	// CachePro interface @Override
	public void verify() throws CacheException {
		Jedis conn = null;
		try {
			conn = jedis();
			String res = conn.ping();
			if ("PONG".equals(res)) return;

			throw new CacheException("Could connect to Redis, but Redis did not answer to the ping as expected (response:" + res + ")");
		}
		catch (IOException e) {
			new CacheException(e.getClass().getName() + ":" + e.getMessage());
		}
		finally {
			RedisCacheUtils.close(conn);
		}
	}
}