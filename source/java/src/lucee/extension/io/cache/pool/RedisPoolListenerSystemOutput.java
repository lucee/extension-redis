package lucee.extension.io.cache.pool;

import lucee.extension.io.cache.redis.Redis;
import lucee.extension.io.cache.util.aprint;

public class RedisPoolListenerSystemOutput implements RedisPoolListener {

	@Override
	public void doAddObject(RedisPool redisPool) throws RedisPoolListenerException {
		aprint.e(Thread.currentThread().getName() + " addObject -> NumActive:" + redisPool.getNumActive() + ";MaxTotal:" + redisPool.getMaxTotal());
	}

	@Override
	public void doBorrowObject(RedisPool redisPool, long borrowMaxWaitMillis) throws RedisPoolListenerException {
		aprint.e(Thread.currentThread().getName() + " doBorrowObject -> NumActive:" + redisPool.getNumActive() + ";MaxTotal:" + redisPool.getMaxTotal() + ";borrowMaxWaitMillis:"
				+ borrowMaxWaitMillis);

	}

	@Override
	public void doClear(RedisPool redisPool) {
		aprint.e(Thread.currentThread().getName() + " doClear -> NumActive:" + redisPool.getNumActive() + ";MaxTotal:" + redisPool.getMaxTotal());
	}

	@Override
	public void doClose(RedisPool redisPool) {
		aprint.e(Thread.currentThread().getName() + " doClose -> NumActive:" + redisPool.getNumActive() + ";MaxTotal:" + redisPool.getMaxTotal());
	}

	@Override
	public void doEvict(RedisPool redisPool) throws RedisPoolListenerException {
		aprint.e(Thread.currentThread().getName() + " doEvict -> NumActive:" + redisPool.getNumActive() + ";MaxTotal:" + redisPool.getMaxTotal());
	}

	@Override
	public void returnObject(RedisPool redisPool, Redis redis) {
		aprint.e(Thread.currentThread().getName() + " returnObject -> NumActive:" + redisPool.getNumActive() + ";MaxTotal:" + redisPool.getMaxTotal());
	}

}
