package lucee.extension.io.cache.redis.udf;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.util.Cast;

public class RedisCommand extends AbstrRedisCommand {

	private static final long serialVersionUID = 4792638153025545116L;

	@Override
	public Object invoke(PageContext pc, Object[] args) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		if (args.length < 1 || args.length > 4) throw eng.getExceptionUtil().createFunctionException(pc, "RedisCommand", 1, 4, args.length);
		Cast cast = eng.getCastUtil();

		Object _args = args[0];
		boolean async = args.length >= 2 && args[1] != null ? cast.toBooleanValue(args[1]) : false;
		Object listener = args.length >= 3 && args[2] != null ? args[2] : null;
		String cacheName = args.length >= 4 && args[3] != null ? cast.toString(args[3]).toUpperCase() : null;

		return invoke(pc, eng, _args, async, listener, cacheName);
	}

	@Override
	public boolean isLowPrio() {
		return false;
	}
}