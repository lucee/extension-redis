package lucee.extension.io.cache.redis.tags.jakarta;

import jakarta.servlet.jsp.tagext.TryCatchFinally;
import lucee.runtime.exp.PageServletException;

/**
 * extends Body Support Tag eith TryCatchFinally Functionality
 */
public abstract class BodyTagTryCatchFinallyImpl extends BodyTagImpl implements TryCatchFinally {

	@Override
	public void doCatch(Throwable t) throws Throwable {
		if (t instanceof ThreadDeath) throw t; // never catch a ThreadDeath

		if (t instanceof PageServletException) {
			PageServletException pse = (PageServletException) t;
			t = pse.getPageException();
		}
		if (bodyContent != null) {
			if ("lucee.runtime.exp.AbortException".equals(t.getClass().getName())) {
				bodyContent.writeOut(bodyContent.getEnclosingWriter());
			}
			bodyContent.clearBuffer();
		}
		throw t;
	}

}