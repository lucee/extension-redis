package lucee.extension.io.cache.pool;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;

import lucee.extension.io.cache.redis.Redis;

public class RedisPool extends GenericObjectPool<Redis> {

	private RedisPoolListener listener;
	private RedisPoolConfig config;

	public RedisPool(PooledObjectFactory<Redis> factory, RedisPoolConfig config, RedisPoolListener listener) {
		super(factory, config);
		this.listener = listener;
		this.config = config;
	}

	@Override
	public void addObject() throws Exception {
		if (listener != null) listener.doAddObject(this);
		super.addObject();
	}

	@Override
	public Redis borrowObject() throws Exception {
		return borrowObject(-1);
	}

	@Override
	public Redis borrowObject(long borrowMaxWaitMillis) throws Exception {
		if (listener != null) listener.doBorrowObject(this, borrowMaxWaitMillis);
		return super.borrowObject(borrowMaxWaitMillis);
	}

	@Override
	public void clear() {
		if (listener != null) listener.doClear(this);
		super.clear();
	}

	@Override
	public void close() {
		if (listener != null) listener.doClose(this);
		super.close();
	}

	@Override
	public void evict() throws Exception {
		if (listener != null) listener.doEvict(this);
		super.evict();
	}

	@Override
	public void returnObject(Redis redis) {
		if (listener != null) listener.returnObject(this, redis);
		super.returnObject(redis);
	}

	public int getMaxLowPriority() {
		return config.getMaxLowPriority();
	}

}
