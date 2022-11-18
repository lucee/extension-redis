package lucee.extension.io.cache.redis;

import java.lang.reflect.Method;

import lucee.commons.io.cache.Cache;
import lucee.commons.io.cache.CacheEntry;
import lucee.commons.io.cache.CacheFilter;
import lucee.commons.io.cache.CacheKeyFilter;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Struct;
import lucee.runtime.type.dt.TimeSpan;

public class CacheUtil {

	public static Struct getInfo(CacheEntry ce) {
		Struct info = CFMLEngineFactory.getInstance().getCreationUtil().createStruct();
		info.setEL("key", ce.getKey());
		info.setEL("created", ce.created());
		info.setEL("last_hit", ce.lastHit());
		info.setEL("last_modified", ce.lastModified());

		info.setEL("hit_count", Double.valueOf(ce.hitCount()));
		info.setEL("size", Double.valueOf(ce.size()));

		info.setEL("idle_time_span", toTimespan(ce.idleTimeSpan()));
		info.setEL("live_time_span", toTimespan(ce.liveTimeSpan()));

		return info;
	}

	public static Struct getInfo(Cache c) {
		Struct info = CFMLEngineFactory.getInstance().getCreationUtil().createStruct();
		try {
			long value = c.hitCount();
			if (value >= 0) info.setEL("hit_count", Double.valueOf(value));
		}
		catch (Exception ioe) {
			// simply ignore
		}

		try {
			long value = c.missCount();
			if (value >= 0) info.setEL("miss_count", Double.valueOf(value));
		}
		catch (Exception ioe) {
			// simply ignore
		}

		return info;
	}

	public static Object toTimespan(long timespan) {
		if (timespan == 0) return "";

		TimeSpan ts = null;
		try {
			ts = CFMLEngineFactory.getInstance().getCastUtil().toTimespan(timespan);
		}
		catch (Exception e) {} // Exception because Lucee 4.5

		if (ts == null) return "";
		return ts;
	}

	public static String toString(CacheEntry ce) {

		return "created:	" + ce.created() + "\nlast-hit:	" + ce.lastHit() + "\nlast-modified:	" + ce.lastModified()

				+ "\nidle-time:	" + ce.idleTimeSpan() + "\nlive-time	:" + ce.liveTimeSpan()

				+ "\nhit-count:	" + ce.hitCount() + "\nsize:		" + ce.size();
	}

	public static boolean allowAll(CacheFilter filter) {
		if (filter == null) return true;

		String p = filter.toPattern();
		if (p == null) p = "";
		else p = p.trim();

		return p.equals("*") || p.equals("");
	}

	public static ClassLoader getClassLoaderEnv(Config config) throws PageException {
		try {
			Method m = config.getClass().getMethod("getClassLoaderEnv", new Class[0]);
			return (ClassLoader) m.invoke(config, new Object[0]);
		}
		catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(e);
		}
	}

	public static boolean isWildCardFiler(CacheKeyFilter filter) {
		if (filter == null) return false;
		return filter.getClass().getName().contains(".WildCardFilter");
	}
}