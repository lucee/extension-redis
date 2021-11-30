package lucee.extension.io.cache.pool;

import lucee.extension.io.cache.redis.Redis;

public interface RedisPoolListener {

	public void doAddObject(RedisPool redisPool) throws RedisPoolListenerException;

	public void doBorrowObject(RedisPool redisPool, long borrowMaxWaitMillis) throws RedisPoolListenerException;

	public void doClear(RedisPool redisPool);

	public void doClose(RedisPool redisPool);

	public void doEvict(RedisPool redisPool) throws RedisPoolListenerException;

	public void returnObject(RedisPool redisPool, Redis redis);
}
