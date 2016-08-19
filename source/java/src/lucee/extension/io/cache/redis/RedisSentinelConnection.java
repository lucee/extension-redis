package lucee.extension.io.cache.redis;

import java.util.*;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;
import lucee.runtime.exp.PageException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisSentinelConnection {

	private static final Hashtable<String, JedisSentinelPool> instance = new Hashtable<String, JedisSentinelPool>();
	private static final Hashtable<String, String> namespace = new Hashtable<String, String>();

	private RedisSentinelConnection() {}

	public static JedisSentinelPool init(String cacheName, Struct arguments){

		CFMLEngine engine = CFMLEngineFactory.getInstance();
		Cast caster = engine.getCastUtil();

		if(instance != null && instance.contains(cacheName)){
			return getInstance(cacheName);
		}

		try{
			namespace.put(cacheName, caster.toString(arguments.get("namespace")));

			String masterName = caster.toString(arguments.get("masterName"));
			Set<String> sentinels = new HashSet<String>(Arrays.asList(caster.toString(arguments.get("sentinels")).split("\\r?\\n")));

			JedisPoolConfig config = new JedisPoolConfig();
			config.setTestOnBorrow(true);
			// config.setTestOnReturn(true);

			instance.put(cacheName, new JedisSentinelPool(masterName, sentinels, config));

		} catch (PageException e) {
			e.printStackTrace();
		}

		return instance.get(cacheName);
	}

	public static JedisSentinelPool getInstance(String cacheName){
		return instance.get(cacheName);
	}

	public static String getNamespace(String cacheName){
		return namespace.get(cacheName);
	}

}
