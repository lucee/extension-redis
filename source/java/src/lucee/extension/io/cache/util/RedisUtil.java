package lucee.extension.io.cache.util;

import java.lang.reflect.Method;

import org.apache.commons.pool2.ObjectPool;

import lucee.commons.io.cache.Cache;
import lucee.extension.io.cache.pool.RedisPool;
import lucee.extension.io.cache.redis.Redis;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;

public class RedisUtil {
	private static final Class[] GET_CACHE_PARAMS = new Class[] { PageContext.class, String.class, int.class };
	private static Method getCache;
	private ObjectPool<Redis> pool;
	private boolean debug;

	public RedisUtil(ObjectPool<Redis> pool, boolean debug) {
		this.pool = pool;
		this.debug = debug;
	}

	// return socket
	public void releaseConnection(Redis redis) throws Exception {
		if (redis == null) return;
		int actives = pool.getNumActive();
		int idle = pool.getNumIdle();
		if (debug) System.out.println("SocketUtil.releaseConnection before now actives : " + actives + ", idle : " + idle);

		if (debug) System.out.println(">>>>> returnObject start");
		pool.returnObject(redis);
		if (debug) System.out.println(">>>>> returnObject end");

		actives = pool.getNumActive();
		idle = pool.getNumIdle();
		if (debug) System.out.println("SocketUtil.releaseConnection after now actives : " + actives + ", idle : " + idle);

	}

	public static void invalidateObjectEL(RedisPool pool, Redis conn) {
		try {
			if (conn != null) pool.invalidateObject(conn);
		}
		catch (Exception e) {
		}
	}

	public static Cache getCache(PageContext pc, String cacheName, int type) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		try {
			ClassLoader cl = pc.getClass().getClassLoader();
			if (getCache == null || !getCache.getDeclaringClass().getClassLoader().equals(cl)) {
				Class<?> cacheUtil = eng.getClassUtil().loadClass(cl, "lucee.runtime.cache.CacheUtil");
				getCache = cacheUtil.getMethod("getCache", GET_CACHE_PARAMS);
			}
			return (Cache) getCache.invoke(null, new Object[] { pc, cacheName, type });
		}
		catch (Exception e) {
			throw eng.getCastUtil().toPageException(e);
		}

	}
}
