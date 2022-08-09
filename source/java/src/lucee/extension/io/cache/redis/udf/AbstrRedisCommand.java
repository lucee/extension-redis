package lucee.extension.io.cache.redis.udf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lucee.commons.io.cache.Cache;
import lucee.extension.io.cache.redis.Command;
import lucee.extension.io.cache.redis.RedisCache;
import lucee.extension.io.cache.util.Coder;
import lucee.extension.io.cache.util.RedisUtil;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.Component;
import lucee.runtime.PageContext;
import lucee.runtime.cache.CacheConnection;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.ext.function.Function;
import lucee.runtime.type.Array;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Struct;
import lucee.runtime.type.UDF;

public abstract class AbstrRedisCommand extends BIF implements Function {

	private static final long serialVersionUID = 4148274035501838063L;
	private static ExecutorService executor = Executors.newFixedThreadPool(10);

	private static final Key ON_SUCCESS;
	private static final Key ON_ERROR;

	static {
		ON_SUCCESS = CFMLEngineFactory.getInstance().getCreationUtil().createKey("onSuccess");
		ON_ERROR = CFMLEngineFactory.getInstance().getCreationUtil().createKey("onError");
	}

	public Object invoke(PageContext pc, CFMLEngine eng, Object _args, boolean async, Object listener, String cacheName) throws PageException {
		if (eng == null) eng = CFMLEngineFactory.getInstance();

		_args = toBytesArray(eng, _args);

		try {
			Command rc = getCommand(pc, eng, cacheName);
			if (async) {
				executor.execute(new Executable(eng, pc, rc, listener, _args));
				return null;
			}
			if (_args instanceof byte[][]) return evalResult(pc.getClass().getClassLoader(), rc.command((byte[][]) _args, isLowPrio()));
			return evalResult(pc.getClass().getClassLoader(), rc.command((List<byte[][]>) _args, isLowPrio()));
		}
		catch (IOException e) {
			throw eng.getCastUtil().toPageException(e);
		}
	}

	protected Command getCommand(PageContext pc, CFMLEngine eng, String cacheName) throws PageException, IOException {

		CacheConnection cc = RedisUtil.getCacheConnection(pc, cacheName);
		Cache cache = cc.getInstance(pc.getConfig());
		if (cache instanceof Command) return (Command) cache;

		if (!cache.getClass().getName().equals(RedisCache.class.getName())
				&& !cache.getClass().getName().equals(lucee.extension.io.cache.redis.simple.RedisCache.class.getName())) {
			throw eng.getExceptionUtil().createApplicationException("cache [" + cacheName + "; class:" + cache.getClass().getName() + "] is not a redis cache");
		}

		cc = RedisUtil.cloneCacheConnection(pc, cc);
		return (Command) cc.getInstance(pc.getConfig());

	}

	private static final Object evalResult(ClassLoader cl, Object res) throws IOException {
		if (res instanceof CharSequence) return res.toString();
		else if (res instanceof byte[]) return Coder.evaluate(cl, (byte[]) res);
		else if (res instanceof Number) return Double.valueOf(((Number) res).doubleValue());
		else if (res instanceof java.util.Collection) return toArray(cl, (java.util.Collection<byte[]>) res);
		return res;
	}

	public static Array toArray(ClassLoader cl, java.util.Collection<byte[]> keys) throws IOException {
		Array array = CFMLEngineFactory.getInstance().getCreationUtil().createArray();
		if (keys != null) {
			Iterator<byte[]> it = keys.iterator();
			while (it.hasNext()) {
				array.appendEL(evalResult(cl, it.next()));
			}
		}
		return array;
	}

	protected Object toBytesArray(CFMLEngine eng, Object args) throws PageException {
		List listArgs = eng.getCastUtil().toList(args);
		if (listArgs.size() == 0) return new byte[0][];

		// do we multiple arguments record
		Object obj = listArgs.iterator().next();
		if (eng.getDecisionUtil().isSimpleValue(obj)) {
			return _toBytesArray(eng, listArgs);
		}

		List<byte[][]> listRtn = new ArrayList<>();
		Iterator it = listArgs.iterator();
		while (it.hasNext()) {
			listRtn.add(_toBytesArray(eng, eng.getCastUtil().toList(it.next())));
		}
		return listRtn;
	}

	private byte[][] _toBytesArray(CFMLEngine eng, List listArgs) throws PageException {
		if (listArgs.size() == 0) return new byte[0][];
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
		private Object args;
		private PageContext pc;

		public Executable(CFMLEngine eng, PageContext parent, Command rc, Object listener, Object args) throws PageException {
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
				Object res;
				if (args instanceof byte[][]) res = evalResult(config.getClass().getClassLoader(), rc.command((byte[][]) args, false));
				else res = evalResult(config.getClass().getClassLoader(), rc.command((List<byte[][]>) args, false));

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
						config.getLog("application").error("redisCommand", ee);
					}
				}
			}
			finally {
				if (pc != null) eng.releasePageContext(pc, true);
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

	public abstract boolean isLowPrio();
}
