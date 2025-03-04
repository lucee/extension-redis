package lucee.extension.io.cache.redis.lock;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.BodyContent;
import jakarta.servlet.jsp.tagext.BodyTag;

public abstract class BodyTagImpl extends TagImpl implements BodyTag {

	protected BodyContent bodyContent = null;

	@Override
	public void setBodyContent(BodyContent bodyContent) {
		this.bodyContent = bodyContent;
	}

	@Override
	public void doInitBody() throws JspException {

	}

	@Override
	public int doAfterBody() throws JspException {
		return SKIP_BODY;
	}

	@Override
	public void release() {
		super.release();
		bodyContent = null;
	}
}