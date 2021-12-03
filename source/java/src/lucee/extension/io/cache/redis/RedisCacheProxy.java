package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import lucee.commons.io.cache.Cache;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;

/**
 * used in cache an old version of the cache is used
 */
public class RedisCacheProxy implements Command {

	private static final Class[] CLASS_ARGS = new Class[] { byte[][].class, boolean.class };
	private static final Class[] CLASS_ARGS2 = new Class[] { List.class, boolean.class };
	private Cache cache;
	private final Method command;
	private final Method command2;

	public RedisCacheProxy(Cache cache) throws NoSuchMethodException, SecurityException {
		this.cache = cache;
		command = cache.getClass().getMethod("command", CLASS_ARGS);
		command2 = cache.getClass().getMethod("command", CLASS_ARGS2);
	}

	@Override
	public Object command(byte[][] arguments, boolean lowPrio) throws IOException {
		try {
			return command.invoke(cache, new Object[] { arguments, lowPrio });
		}
		catch (Exception e) {
			CFMLEngine eng = CFMLEngineFactory.getInstance();
			throw eng.getExceptionUtil().toIOException(e);
		}
	}

	@Override
	public List<Object> command(List<byte[][]> arguments, boolean lowPrio) throws IOException {
		try {

			return (List<Object>) command2.invoke(cache, new Object[] { arguments, lowPrio });
		}
		catch (Exception e) {
			CFMLEngine eng = CFMLEngineFactory.getInstance();
			throw eng.getExceptionUtil().toIOException(e);
		}
	}
}
