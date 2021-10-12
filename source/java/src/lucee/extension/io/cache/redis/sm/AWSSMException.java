package lucee.extension.io.cache.redis.sm;

import java.io.IOException;

public class AWSSMException extends IOException {

	private static final long serialVersionUID = 4351652364895546887L;

	public AWSSMException(String msg) {
		super(msg);
	}

	public AWSSMException(Exception e) {
		super(e);
	}
}
