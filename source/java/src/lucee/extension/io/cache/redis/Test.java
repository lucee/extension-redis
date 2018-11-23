package lucee.extension.io.cache.redis;

import lucee.runtime.exp.PageException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class Test {

    public static void main(String[] args) throws PageException {
	JedisPoolConfig poolConfig = new JedisPoolConfig();
	poolConfig.setMaxTotal(128);

	JedisPool pool = new JedisPool(poolConfig, "localhost", 6379, 10000);
	Jedis j = pool.getResource();
	Jedis j2 = pool.getResource();

	// Jedis j = new Jedis("localhost", 6379);
	System.err.println(j);
	System.err.println(j.ping());
	System.err.println(j2);
	System.err.println(j.ping());
	System.err.println(j.info());
	j.lpush("a", "AAA");
	System.err.println(j.rpop("a"));
	System.err.println(j.bitcount("a"));

	j.close();
	j2.close();

	j = pool.getResource();

	System.err.println(j);
	j.close();

	// JedisFactory.getInstance().getJedisPool().getResource();

	/*
	 * HashSet sentinels = new HashSet<>(); //
	 * sentinels.add("redis1-001.prnos2.0001.usw2.cache.amazonaws.com:6379"); //
	 * sentinels.add("redis1-002.prnos2.0001.usw2.cache.amazonaws.com:6379"); //
	 * sentinels.add("redis1.prnos2.ng.0001.usw2.cache.amazonaws.com:6379");
	 * sentinels.add("localhost:6379");
	 * 
	 * String masterName = "redis1"; String nameSpace = "us-west-2b";
	 * 
	 * Settings settings = new RedisSentinelCache.Settings(masterName, nameSpace, masterName,
	 * sentinels); RedisSentinelConnection.getConn(settings);
	 */
    }

}
