package lucee.extension.io.cache.redis.simple;

import java.io.IOException;

import lucee.extension.io.cache.redis.AbstractRedisCache;
import lucee.runtime.config.Config;
import lucee.runtime.type.Struct;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisCache extends AbstractRedisCache {

    private String host;
    private int port;
    private JedisPool pool;

    @Override
    public void init(Config config, String cacheName, Struct arguments) throws IOException {
	super.init(arguments);
	new Throwable().printStackTrace();
	host = caster.toString(arguments.get("host", "localhost"), "localhost");
	port = caster.toIntValue(arguments.get("port", null), 6379);
    }

    @Override
    protected Jedis jedis() throws IOException {
	if (pool == null) {
	    pool = new JedisPool(getJedisPoolConfig(), host, port, timeout, password);
	}
	return pool.getResource();
    }
}
