package lucee.extension.io.cache.redis.udf;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.ext.function.Function;
import lucee.runtime.type.Struct;

public class RedisInfo extends BIF implements Function {

	private static final long serialVersionUID = 1L;

	@Override
	public Object invoke(PageContext pc, Object[] args) throws PageException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		if (args.length > 0) throw eng.getExceptionUtil().createFunctionException(pc, "RedisInfo", 0, 0, args.length);
		Struct info = eng.getCreationUtil().createStruct();
		return info;
	}

}
