package lucee.extension.io.cache.redis.udf;

import java.lang.reflect.Method;

import lucee.commons.io.cache.Cache;
import lucee.extension.io.cache.redis.RedisCache;
import lucee.extension.io.cache.util.RedisUtil;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.ext.function.Function;
import lucee.runtime.util.Cast;

public class RedisConnectionPoolInfo extends BIF implements Function {

	private static final long serialVersionUID = -3526608508485057586L;

	@Override
	public Object invoke(PageContext pc, Object[] args) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		if (args.length > 1) throw eng.getExceptionUtil().createFunctionException(pc, "RedisConnectionPoolInfo", 0, 1, args.length);
		Cast cast = eng.getCastUtil();
		String cacheName = args.length == 1 && args[0] != null ? cast.toString(args[0]).toUpperCase() : null;

		// is the cache a Redis Cache?
		Cache cache = RedisUtil.getCache(pc, cacheName, Config.CACHE_TYPE_OBJECT);

		if (cache instanceof RedisCache) return ((RedisCache) cache).getPoolInfo();

		try {
			Method m = cache.getClass().getMethod("getPoolInfo", new Class[] {});
			return cast.toStruct(m.invoke(cache, new Object[] {}));
		}
		catch (Exception e) {
			throw cast.toPageException(e);
		}

	}

}
