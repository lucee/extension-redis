package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import lucee.commons.io.cache.Cache;
import lucee.commons.io.cache.CacheEntry;
import lucee.commons.io.cache.exp.CacheException;
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

    public Functions func;
    protected CFMLEngine engine = CFMLEngineFactory.getInstance();
    protected Cast caster = engine.getCastUtil();

    protected int timeout;
    protected String password;

    private int maxTotal;
    private int maxIdle;
    private int minIdle;
    private int defaultExpire;

    public void init(Config config, String[] cacheName, Struct[] arguments) {
	// Not used at the moment
    }

    public void init(Struct arguments) throws IOException {
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
    public CacheEntry getCacheEntry(String key) throws IOException {
	Jedis conn = jedisSilent();
	try {
	    key = validateKey(key);
	    String val = null;
	    try {
		val = conn.get(key);
	    }
	    catch (JedisDataException jde) {
		String msg = jde.getMessage() + "";
		if (msg.startsWith("WRONGTYPE")) val = conn.lpop(key);
	    }
	    if (val == null) throw new IOException("Cache key [" + key + "] does not exists");
	    return new RedisCacheEntry(this, key, evaluate(val), val.length());
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
	    // String value = func.serialize(val);
	    // HashMap<String, String> fields = new HashMap<String, String>();
	    // fields.put("value", value);
	    // fields.put("hitCount", "0");
	    // conn.hmset(key, fields);
	    conn.set(validateKey(key), serialize(val));
	    // TODO different to default?
	    // System.err.println("idle:" + idle);
	    // System.err.println("expire:" + expire);
	    // System.err.println("defaultExpire:" + defaultExpire);

	    int ex = 0;
	    if (expire != null) {
		ex = (int) (expire / 1000);
	    }
	    else {
		ex = defaultExpire;
	    }

	    if (ex > 0) {
		// System.err.println(key + ":" + ex);
		conn.expire(validateKey(key), ex);
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
	    return conn.exists(validateKey(key));
	}
	finally {
	    RedisCacheUtils.close(conn);
	}
    }

    @Override
    public boolean remove(String key) throws IOException {
	Jedis conn = jedis();
	try {
	    return conn.del(validateKey(key)) > 0;
	}
	finally {
	    RedisCacheUtils.close(conn);
	}
    }

    @Override
    public List<String> keys() throws IOException {
	Jedis conn = jedis();
	try {
	    return toList(conn.keys("*"));
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
	return InfoParser.parse(CacheUtil.getInfo(this), jedisSilent().info());// not throwing IOException because Lucee 4.5
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
	Jedis conn = jedis();
	Set<String> set = conn.keys("*");
	String[] keys = engine.getListUtil().toStringArray(set);
	return engine.getCastUtil().toIntValue(conn.del(keys), 0);
    }

    private String validateKey(String key) {
	return key.trim().toLowerCase();
    }

    private String serialize(Object val) throws PageException {
	if (val instanceof String) return (String) val;
	if (val instanceof CharSequence) return val.toString();
	if (val instanceof Character) return engine.getCastUtil().toString(val);

	if (val instanceof Number) return "nbr(" + engine.getCastUtil().toString(val) + ")";
	if (val instanceof Boolean) return "bool(" + engine.getCastUtil().toString(val) + ")";
	if (val instanceof Date) return "date(" + ((Date) val).getTime() + ")";

	return "eval(" + func.serialize(val) + ")"; // MUST
    }

    private Object evaluate(String val) throws PageException {
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

    protected abstract Jedis jedis() throws IOException;

    protected Jedis jedisSilent() {
	try {
	    return jedis();
	}
	catch (Exception e) {
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