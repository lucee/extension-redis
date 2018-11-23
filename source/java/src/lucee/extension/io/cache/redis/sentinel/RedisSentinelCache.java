package lucee.extension.io.cache.redis.sentinel;

import java.io.IOException;
import java.util.Set;

import lucee.extension.io.cache.redis.AbstractRedisCache;
import lucee.extension.io.cache.redis.RedisCacheUtils;
import lucee.runtime.config.Config;
import lucee.runtime.type.Struct;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;

public class RedisSentinelCache extends AbstractRedisCache {

    private String masterName;
    private Set<String> sentinels;

    private JedisSentinelPool pool;

    @Override
    public void init(Config config, String cacheName, Struct arguments) throws IOException {
	super.init(arguments);
	masterName = caster.toString(arguments.get("masterName", ""), "");
	sentinels = RedisCacheUtils.toSet(caster.toString(arguments.get("sentinels", ""), "").split("\\r?\\n")); // TODO better
    }

    @Override
    protected Jedis jedis() throws IOException {
	if (pool == null) {
	    pool = new JedisSentinelPool(masterName, sentinels, getJedisPoolConfig(), timeout, password);
	}
	return pool.getResource();
    }
}
