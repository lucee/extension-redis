package lucee.extension.io.cache.redis.lock;

import jakarta.servlet.jsp.tagext.BodyContent;
import jakarta.servlet.jsp.tagext.BodyTag;
import lucee.runtime.exp.PageException;

public abstract class BodyTagImpl extends TagImpl implements BodyTag {

	protected BodyContent bodyContent = null;

	@Override
	public void setBodyContent(BodyContent bodyContent) {
		this.bodyContent = bodyContent;
	}

	@Override
	public void doInitBody() throws PageException {

	}

	@Override
	public int doAfterBody() throws PageException {
		return SKIP_BODY;
	}

	@Override
	public void release() {
		super.release();
		bodyContent = null;
	}
}