package lucee.extension.io.cache.pool;

import lucee.extension.io.cache.redis.Redis;

public class RedisPoolListenerNotifyOnReturn implements RedisPoolListener {

	private Object token;

	public RedisPoolListenerNotifyOnReturn(Object token) {
		this.token = token;
	}

	@Override
	public void doAddObject(RedisPool redisPool) throws RedisPoolListenerException {
	}

	@Override
	public void doBorrowObject(RedisPool redisPool, long borrowMaxWaitMillis) throws RedisPoolListenerException {
	}

	@Override
	public void doClear(RedisPool redisPool) {
	}

	@Override
	public void doClose(RedisPool redisPool) {

	}

	@Override
	public void doEvict(RedisPool redisPool) throws RedisPoolListenerException {

	}

	@Override
	public void returnObject(RedisPool redisPool, Redis redis) {
		synchronized (token) {
			token.notify();
		}
	}

}
