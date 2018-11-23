package lucee.extension.io.cache.redis;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;

public class InfoParser {

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

}
