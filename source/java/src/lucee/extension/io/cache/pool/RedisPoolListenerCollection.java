package lucee.extension.io.cache.pool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lucee.extension.io.cache.redis.Redis;

public class RedisPoolListenerCollection implements RedisPoolListener {

	private final Map<RedisPoolListener, String> listeners = new ConcurrentHashMap<>();

	public RedisPoolListenerCollection() {

	}

	public RedisPoolListenerCollection(RedisPoolListener listener) {
		this.listeners.put(listener, "");
	}

	public RedisPoolListenerCollection(List<RedisPoolListener> listeners) {
		for (RedisPoolListener listener: listeners) {
			this.listeners.put(listener, "");
		}
	}

	public void addListener(RedisPoolListener listener) {
		this.listeners.put(listener, "");
	}

	public void removeListener(RedisPoolListener listener) {
		this.listeners.remove(listener);
	}

	@Override
	public void doAddObject(RedisPool redisPool) throws RedisPoolListenerException {
		if (!listeners.isEmpty()) {
			for (RedisPoolListener listener: listeners.keySet()) {
				listener.doAddObject(redisPool);
			}
		}
	}

	@Override
	public void doBorrowObject(RedisPool redisPool, long borrowMaxWaitMillis) throws RedisPoolListenerException {
		if (!listeners.isEmpty()) {
			for (RedisPoolListener listener: listeners.keySet()) {
				listener.doBorrowObject(redisPool, borrowMaxWaitMillis);
			}
		}
	}

	@Override
	public void doClear(RedisPool redisPool) {
		if (!listeners.isEmpty()) {
			for (RedisPoolListener listener: listeners.keySet()) {
				listener.doClear(redisPool);
			}
		}
	}

	@Override
	public void doClose(RedisPool redisPool) {
		if (!listeners.isEmpty()) {
			for (RedisPoolListener listener: listeners.keySet()) {
				listener.doClose(redisPool);
			}
		}
	}

	@Override
	public void doEvict(RedisPool redisPool) throws RedisPoolListenerException {
		if (!listeners.isEmpty()) {
			for (RedisPoolListener listener: listeners.keySet()) {
				listener.doEvict(redisPool);
			}
		}
	}

	@Override
	public void returnObject(RedisPool redisPool, Redis redis) {
		if (!listeners.isEmpty()) {
			for (RedisPoolListener listener: listeners.keySet()) {
				listener.returnObject(redisPool, redis);
			}
		}
	}

}
