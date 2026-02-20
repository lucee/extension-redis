package lucee.extension.io.cache.redis.tags.javax;

import javax.servlet.jsp.tagext.Tag;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.exp.PageException;

/**
 * Implementation of the Tag
 */
public abstract class TagImpl implements Tag {

	protected final CFMLEngine engine;
	protected PageContext pageContext;
	private Tag parent;

	public TagImpl() {
		engine = CFMLEngineFactory.getInstance();
	}

	public void setPageContext(PageContext pageContext) {
		this.pageContext = pageContext;
	}

	@Override
	public void setPageContext(javax.servlet.jsp.PageContext pageContext) {
		this.pageContext = CFMLEngineFactory.getInstance().getThreadPageContext();
	}

	public void setPageContext(jakarta.servlet.jsp.PageContext pageContext) {
		this.pageContext = CFMLEngineFactory.getInstance().getThreadPageContext();
	}

	@Override
	public void setParent(Tag parent) {
		this.parent = parent;
	}

	@Override
	public Tag getParent() {
		return parent;
	}

	@Override
	public int doStartTag() {
		return SKIP_BODY;
	}

	@Override
	public int doEndTag() {
		return EVAL_PAGE;
	}

	@Override
	public void release() {
		pageContext = null;
		parent = null;
	}

	/**
	 * check if value is not empty
	 * 
	 * @param tagName
	 * @param attributeName
	 * @param attribute
	 * @throws PageException
	 */
	public void required(String tagName, String actionName, String attributeName, Object attribute) throws PageException {
		if (attribute == null) throw engine.getExceptionUtil()
				.createApplicationException("Attribute [" + attributeName + "] for tag [" + tagName + "] is required if attribute action has the value [" + actionName + "]");

	}

	public void required(String tagName, String attributeName, Object attribute) throws PageException {
		if (attribute == null) throw engine.getExceptionUtil().createApplicationException("Attribute [" + attributeName + "] for tag [" + tagName + "] is required");

	}

	public void required(String tagName, String actionName, String attributeName, String attribute, boolean trim) throws PageException {
		if (Util.isEmpty(attribute, trim)) throw engine.getExceptionUtil()
				.createApplicationException("Attribute [" + attributeName + "] for tag [" + tagName + "] is required if attribute action has the value [" + actionName + "]");
	}

	public void required(String tagName, String actionName, String attributeName, double attributeValue, double nullValue) throws PageException {
		if (attributeValue == nullValue) throw engine.getExceptionUtil()
				.createApplicationException("Attribute [" + attributeName + "] for tag [" + tagName + "] is required if attribute action has the value [" + actionName + "]");
	}
}