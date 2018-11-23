package lucee.extension.util;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;

public class FunctionFactory {
    public static Functions getInstance() {
	CFMLEngine engine = CFMLEngineFactory.getInstance();
	try {
	    engine.getInfo(); // Lucee 4.5 does not support that method
	    return new FunctionsModern();
	}
	catch (NoSuchMethodError e) {
	    return new FunctionsClassic();
	}

    }
}
