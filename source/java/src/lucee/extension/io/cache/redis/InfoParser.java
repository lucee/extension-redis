package lucee.extension.io.cache.redis;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;
import lucee.runtime.type.dt.DateTime;
import lucee.runtime.util.Cast;

public class InfoParser {

	public static DebugObject parseDebugObject(Struct root, String str) {
		// Value at:0x7fca3cd03ed0 refcount:1 encoding:embstr serializedlength:13 lru:6575389
		// lru_seconds_idle:0
		if (str == null) return null;

		DebugObject debug = new DebugObject();

		String marker = "serializedlength:";
		int begin = str.indexOf(marker);
		if (begin != -1) {
			begin += marker.length();
			int end = str.indexOf(" ", begin + 1);
			Integer val = Integer.valueOf(str.substring(begin, end == -1 ? str.length() : end));
			debug.serializedLength = val.intValue();
		}

		marker = "lru:";
		begin = str.indexOf(marker);
		if (begin != -1) {
			begin += marker.length();
			int end = str.indexOf(" ", begin + 1);
			Integer val = Integer.valueOf(str.substring(begin, end == -1 ? str.length() : end));
			debug.lru = val.intValue();
		}

		marker = "lru_seconds_idle:";
		begin = str.indexOf(marker);
		if (begin != -1) {
			begin += marker.length();
			int end = str.indexOf(" ", begin + 1);
			Integer val = Integer.valueOf(str.substring(begin, end == -1 ? str.length() : end));
			debug.lruSecondsIdle = val.intValue();
		}
		return debug;
	}

	public static Struct parse(Struct root, String str) {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Cast cast = eng.getCastUtil();
		Struct cat = null;

		String[] arr = str.split("\\r?\\n");
		String k, v;
		Double d;
		int index;
		for (String line: arr) {
			line = line.trim();
			if (line.isEmpty()) continue;

			// category?
			if (line.startsWith("#")) {
				line = line.substring(1).trim();
				cat = eng.getCreationUtil().createStruct();
				root.setEL(line, cat);
			}
			else {
				index = line.indexOf(':');
				k = line.substring(0, index).trim();
				v = line.substring(index + 1).trim();
				d = cast.toDouble(v, null);
				if (d != null) cat.setEL(k, d);
				else cat.setEL(k, v);
			}
		}
		return root;
	}

	public static class DebugObject {
		public int serializedLength;
		public int lru;
		public int lruSecondsIdle;

		public DateTime getLRUTime() {

			return CFMLEngineFactory.getInstance().getCreationUtil().createDateTime(System.currentTimeMillis() - (lruSecondsIdle * 1000));
		}
	}

}
