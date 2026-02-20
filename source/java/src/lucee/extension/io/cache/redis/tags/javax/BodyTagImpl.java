package lucee.extension.io.cache.redis.tags.javax;

import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.BodyTag;

public abstract class BodyTagImpl extends TagImpl implements BodyTag {

	protected BodyContent bodyContent = null;

	@Override
	public void setBodyContent(BodyContent bodyContent) {
		this.bodyContent = bodyContent;
	}

	@Override
	public void doInitBody() {

	}

	@Override
	public int doAfterBody() {
		return SKIP_BODY;
	}

	@Override
	public void release() {
		super.release();
		bodyContent = null;
	}
}