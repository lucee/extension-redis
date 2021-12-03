package lucee.extension.io.cache.util;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.pool2.ObjectPool;

import lucee.commons.io.cache.Cache;
import lucee.extension.io.cache.pool.RedisPool;
import lucee.extension.io.cache.redis.Redis;
import lucee.extension.io.cache.redis.RedisCache;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.cache.CacheConnection;
import lucee.runtime.config.Config;
import lucee.runtime.db.ClassDefinition;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Struct;

public class RedisUtil {
	private static final Class[] GET_CACHE_PARAMS = new Class[] { PageContext.class, String.class, int.class };
	private static final Class[] GET_CACHE_CONN_PARAMS = new Class[] { PageContext.class, String.class };
	private static final Class[] CONSTR_CACHE_CONN = new Class[] { Config.class, String.class, ClassDefinition.class, Struct.class, boolean.class, boolean.class };
	private static final Class[] CONSTR_CLASS_DEF = new Class[] { Class.class };

	private static final ConcurrentHashMap<String, String> tokens = new ConcurrentHashMap<String, String>();
	private static Method getCache;
	private static Method getCacheConn;
	private static Class<?> cacheConn;
	private static Class<?> classDef;
	private static Constructor<?> cacheConnConstr;
	private static Constructor<?> classDefConstr;

	private ObjectPool<Redis> pool;
	private boolean debug;

	private static Map<String, CacheConnection> connections = new ConcurrentHashMap<>();
	private static Object token = new Object();

	public RedisUtil(ObjectPool<Redis> pool, boolean debug) {
		this.pool = pool;
		this.debug = debug;
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

	public static CacheConnection getCacheConnection(PageContext pc, String cacheName) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();

		// public static CacheConnection getCacheConnection(PageContext pc, String cacheName) throws
		// IOException {

		try {
			ClassLoader cl = pc.getClass().getClassLoader();
			if (getCacheConn == null || !getCacheConn.getDeclaringClass().getClassLoader().equals(cl)) {
				Class<?> cacheUtil = eng.getClassUtil().loadClass(cl, "lucee.runtime.cache.CacheUtil");
				getCacheConn = cacheUtil.getMethod("getCacheConnection", GET_CACHE_CONN_PARAMS);
			}
			return (CacheConnection) getCacheConn.invoke(null, new Object[] { pc, cacheName });
		}
		catch (Exception e) {
			throw eng.getCastUtil().toPageException(e);
		}

	}

	public static CacheConnection cloneCacheConnection(PageContext pc, CacheConnection cc) throws IOException, PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();

		CacheConnection ccc = connections.get(cc.getName());
		if (ccc != null) return ccc;

		try {
			ClassLoader cl = pc.getClass().getClassLoader();
			synchronized (getToken(cc.getName())) {
				if (ccc == null) {
					if (cacheConn == null) {
						cacheConn = eng.getClassUtil().loadClass("lucee.runtime.cache.CacheConnectionImpl");
						cacheConnConstr = cacheConn.getConstructor(CONSTR_CACHE_CONN);
					}

					if (classDef == null) {
						classDef = eng.getClassUtil().loadClass("lucee.transformer.library.ClassDefinitionImpl");
						classDefConstr = classDef.getConstructor(CONSTR_CLASS_DEF);
					}
					ccc = (CacheConnection) cacheConnConstr.newInstance(
							new Object[] { pc.getConfig(), cc.getName(), classDefConstr.newInstance(new Object[] { RedisCache.class }), cc.getCustom(), false, false });

					connections.put(cc.getName(), ccc);
				}
			}
			return ccc;
		}
		catch (Exception e) {
			throw eng.getCastUtil().toPageException(e);
		}
	}

	private static String getToken(String name) {
		String token = tokens.putIfAbsent(name, name);
		if (token == null) {
			token = name;
		}
		return token;
	}

}
