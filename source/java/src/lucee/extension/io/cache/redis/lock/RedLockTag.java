package lucee.extension.io.cache.redis.lock;

import javax.servlet.jsp.JspException;

import lucee.loader.util.Util;
import lucee.runtime.exp.PageException;

/**
 * Transaction class
 */
public final class RedLockTag extends BodyTagTryCatchFinallyImpl {

	private String name;
	private String cache;
	private boolean bypass;
	private int amount = 1;
	private int expires = 1;
	private long timeout = 0L;
	private boolean throwontimeout;
	private boolean logontimeout = true;
	private RedLock lock;

	@Override
	public void release() {
		name = null;
		cache = null;
		bypass = false;
		amount = 1;
		expires = 1;
		timeout = 0L;
		this.throwontimeout = false;
		this.logontimeout = true;
		lock = null;
		super.release();
	}

	public void setName(String name) throws PageException {
		if (Util.isEmpty(name, true)) throw engine.getExceptionUtil().createApplicationException("name is required and cannot be empty!");
		this.name = name;
	}

	public void setCache(String cache) throws PageException {
		if (Util.isEmpty(cache, true)) throw engine.getExceptionUtil().createApplicationException("the value for cache cannot be empty!");
		this.cache = cache.trim();
	}

	public void setBypass(boolean bypass) {
		this.bypass = bypass;
	}

	public void setAmount(double amount) throws PageException {
		this.amount = (int) amount;
		if (amount < 1) throw engine.getExceptionUtil().createApplicationException("the value for amount must be at least 1!");
	}

	public void setTimeout(double timeout) throws PageException {
		if (timeout < 0) timeout = 0;
		timeout = ((int) (timeout * 100)) / 100D;
		if (timeout < 0.01) throw engine.getExceptionUtil().createApplicationException("timeout must be at least 0.01, now it is [" + timeout + "]");

		this.timeout = (long) (timeout * 1000D);
	}

	public void setExpires(double expires) throws PageException {
		this.expires = (int) expires;
		if (this.expires <= 0) this.expires = 600;
	}

	@Override
	public int doStartTag() throws PageException {
		if (bypass) return EVAL_BODY_INCLUDE;
		if (Util.isEmpty(name)) throw engine.getExceptionUtil().createApplicationException("Name attribute cannot be empty!");

		lock = new RedLock(name, cache, amount, timeout, throwontimeout, logontimeout, expires);
		if (lock.lock(pageContext)) {
			return EVAL_BODY_INCLUDE;
		}

		lock = null;
		return SKIP_BODY;
	}

	@Override
	public void doCatch(Throwable t) throws Throwable {
		// print.e("doCatch");
		throw t;
	}

	@Override
	public int doEndTag() throws JspException {
		// print.e("doEndTag");
		return EVAL_PAGE;
	}

	/**
	 * @param hasBody
	 */
	public void hasBody(boolean hasBody) {// print.out("hasBody"+hasBody);
		// this.hasBody=hasBody;
	}

	@Override
	public void doFinally() {
		if (lock != null) {
			try {
				lock.release(pageContext);
			}
			catch (PageException e) {
				throw engine.getCastUtil().toPageRuntimeException(e);
			}

		}
	}

	@Override
	public int doAfterBody() throws JspException {
		return super.doAfterBody();
	}
}
