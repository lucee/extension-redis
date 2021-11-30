package lucee.extension.io.cache.pool;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import lucee.extension.io.cache.redis.Redis;

public class RedisPoolConfig extends GenericObjectPoolConfig<Redis> {

	private int maxLowPrio;

	public void setMaxLowPriority(int maxLowPrio) {
		this.maxLowPrio = maxLowPrio;
	}

	public int getMaxLowPriority() {
		return this.maxLowPrio;
	}

}
