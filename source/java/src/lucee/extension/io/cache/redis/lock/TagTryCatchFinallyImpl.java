package lucee.extension.io.cache.redis.lock;

import jakarta.servlet.jsp.tagext.TryCatchFinally;
import lucee.runtime.exp.PageServletException;

public abstract class TagTryCatchFinallyImpl extends TagImpl implements TryCatchFinally {

	@Override
	public void doCatch(Throwable t) throws Throwable {
		if (t instanceof ThreadDeath) throw t; // never catch a ThreadDeath

		if (t instanceof PageServletException) {
			PageServletException pse = (PageServletException) t;
			t = pse.getPageException();
		}
		throw t;
	}

}