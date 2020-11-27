package lucee.extension.io.cache.redis.udf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lucee.commons.io.cache.Cache;
import lucee.extension.io.cache.redis.Command;
import lucee.extension.io.cache.redis.RedisCache;
import lucee.extension.io.cache.redis.RedisCacheProxy;
import lucee.extension.io.cache.util.Coder;
import lucee.extension.io.cache.util.RedisUtil;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.Component;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.ext.function.Function;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;
import lucee.runtime.util.Cast;

public class RedisCommand extends BIF implements Function {

	private static final long serialVersionUID = 4148274035501838063L;
	private static ExecutorService executor = Executors.newFixedThreadPool(10);

	private static final Key ON_SUCCESS;
	private static final Key ON_ERROR;

	static {
		ON_SUCCESS = CFMLEngineFactory.getInstance().getCreationUtil().createKey("onSuccess");
		ON_ERROR = CFMLEngineFactory.getInstance().getCreationUtil().createKey("onError");
	}

	@Override
	public Object invoke(PageContext pc, Object[] args) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		if (args.length < 1 || args.length > 4) throw eng.getExceptionUtil().createFunctionException(pc, "RedisCommand", 1, 4, args.length);
		Cast cast = eng.getCastUtil();

		byte[][] _args = toBytesArray(eng, args[0]);
		boolean async = args.length >= 2 && args[1] != null ? cast.toBooleanValue(args[1]) : false;
		Object listener = args.length >= 3 && args[2] != null ? args[2] : null;
		String cacheName = args.length >= 4 && args[3] != null ? cast.toString(args[3]).toUpperCase() : null;

		// is the cache a Redis Cache?
		Command rc = null;
		Cache cache = RedisUtil.getCache(pc, cacheName, Config.CACHE_TYPE_OBJECT);
		if (!(cache instanceof Command)) {
			if (!cache.getClass().getName().equals(RedisCache.class.getName()))
				throw eng.getExceptionUtil().createApplicationException("cache [" + cacheName + "; class:" + cache.getClass().getName() + "] is not a redis cache");
			rc = new RedisCacheProxy(cache);
		}
		else rc = (Command) cache;
		try {
			if (async) {
				executor.execute(new Executable(eng, pc, rc, listener, _args));
				return null;
			}
			return evalResult(pc.getClass().getClassLoader(), rc.command(_args));

		}
		catch (IOException e) {
			throw eng.getCastUtil().toPageException(e);
		}
	}

	private static final Object evalResult(ClassLoader cl, Object res) throws IOException {
		if (res instanceof CharSequence) return res.toString();
		else if (res instanceof byte[]) return Coder.evaluate(cl, (byte[]) res);
		else if (res instanceof Number) return Double.valueOf(((Number) res).doubleValue());
		else if (res instanceof List) return RedisCache.toArray((List) res);
		return res;
	}

	private byte[][] toBytesArray(CFMLEngine eng, Object args) throws PageException {
		List listArgs = eng.getCastUtil().toList(args);
		try {
			byte[][] arguments = new byte[listArgs.size()][];
			Iterator it = listArgs.iterator();
			int idx = 0;
			while (it.hasNext()) {
				arguments[idx++] = Coder.serialize(it.next());
			}
			return arguments;
		}
		catch (IOException ioe) {
			throw eng.getCastUtil().toPageException(ioe);
		}
	}

	private static class Executable implements Runnable {
		// clonePageContext(PageContext pc, OutputStream os, boolean stateless, boolean register2Thread,
		// boolean register2RunningThreads)
		private Method clonePageContext;
		private static final Class[] ARGS = new Class[] { PageContext.class, OutputStream.class, boolean.class, boolean.class, boolean.class };

		private CFMLEngine eng;
		private Config config;
		private Command rc;
		private Object listener;
		private byte[][] args;
		private PageContext pc;

		public Executable(CFMLEngine eng, PageContext parent, Command rc, Object listener, byte[][] args) throws PageException {
			this.eng = eng;
			this.config = parent.getConfig();
			this.pc = clonePageContext(parent);
			this.rc = rc;
			this.listener = listener;
			this.args = args;
		}

		@Override
		public void run() {
			try {
				if (pc != null) eng.registerThreadPageContext(pc);
				Object res = evalResult(config.getClass().getClassLoader(), rc.command(args));
				if (has(pc, ON_SUCCESS)) {
					eng.registerThreadPageContext(pc);
					call(pc, ON_SUCCESS, new Object[] { res });
				}
			}
			catch (Exception e) {
				if (has(pc, ON_ERROR)) {
					try {
						call(pc, ON_ERROR, new Object[] { eng.getCastUtil().toPageException(e).getCatchBlock(config) });
					}
					catch (Exception ee) {
						ee.printStackTrace();// TODO remove this line
						config.getLog("application").error("redisCommand", ee);
					}
				}
			}
			finally {
				if (pc != null) eng.releasePageContext(pc, true);
				// TODO release PC
			}
		}

		private PageContext clonePageContext(PageContext parent) throws PageException {
			try {
				if (clonePageContext == null || clonePageContext.getDeclaringClass().getClassLoader() != parent.getClass().getClassLoader()) {
					Class<?> clazz = eng.getClassUtil().loadClass(config.getClass().getClassLoader(), "lucee.runtime.thread.ThreadUtil");
					clonePageContext = clazz.getMethod("clonePageContext", ARGS);
				}
				// clonePageContext(PageContext pc, OutputStream os, boolean stateless, boolean
				// register2Thread,boolean register2RunningThreads) {
				return (PageContext) clonePageContext.invoke(null, new Object[] { parent, new ByteArrayOutputStream(), false, false, false });
			}
			catch (Exception e) {
				throw eng.getCastUtil().toPageException(e);
			}
		}

		public boolean has(PageContext pc, Collection.Key functionName) {
			if (listener != null) {
				if (listener instanceof Component) return ((Component) listener).contains(pc, functionName);
				if (listener instanceof Struct) {
					return ((Struct) listener).get(functionName, null) instanceof UDF;
				}
			}
			return false;
		}

		private Object call(PageContext pc, Key key, Object[] args) throws PageException {
			if (listener instanceof Component) {
				return ((Component) listener).call(pc, key, args);
			}
			return ((UDF) eng.getCastUtil().toStruct(listener).get(key)).call(pc, key, args, false);
		}
	}
}
