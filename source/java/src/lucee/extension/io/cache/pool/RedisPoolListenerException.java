package lucee.extension.io.cache.pool;

import java.io.IOException;

public class RedisPoolListenerException extends IOException {

	private static final long serialVersionUID = -8838738660018096624L;

	public RedisPoolListenerException(String message, Throwable cause) {
		super(message, cause);
	}

	public RedisPoolListenerException(String message) {
		super(message);
	}

}
