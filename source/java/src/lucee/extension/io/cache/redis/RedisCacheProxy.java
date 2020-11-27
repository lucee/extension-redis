package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.lang.reflect.Method;

import lucee.commons.io.cache.Cache;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;

/**
 * used in cache an old version of the cache is used
 */
public class RedisCacheProxy implements Command {

	private static final Class[] CLASS_ARGS = new Class[] { byte[][].class };
	private Cache cache;
	private Method command;

	public RedisCacheProxy(Cache cache) {
		this.cache = cache;
	}

	@Override
	public Object command(byte[]... arguments) throws IOException {
		try {
			if (command == null || command.getDeclaringClass().getClassLoader() != cache.getClass().getClassLoader()) {
				command = cache.getClass().getMethod("command", CLASS_ARGS);
			}
			return command.invoke(cache, new Object[] { arguments });
		}
		catch (Exception e) {
			CFMLEngine eng = CFMLEngineFactory.getInstance();
			throw eng.getExceptionUtil().toIOException(e);
		}
	}
}
