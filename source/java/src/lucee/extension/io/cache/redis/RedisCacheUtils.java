package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import redis.clients.jedis.Jedis;

public class RedisCacheUtils {

	private RedisCacheUtils() {}

	public static String removeNamespace(String nameSpace, String key) {
		if (nameSpace != null && key.startsWith(nameSpace)) return key.replace(nameSpace + ":", "");
		return key;

	}

	public static String addNamespace(String nameSpace, String key) throws IOException { // TODO rethink this
		if (nameSpace == null) return key.toLowerCase();

		if (key.startsWith(nameSpace + ":")) {
			return key;
		}
		String res = nameSpace + ':' + key;
		return res.toLowerCase();// setting case sensitive
	}

	public static void close(Jedis jedis) {
		if (jedis != null) jedis.close();
	}

	public static Set<String> toSet(String[] arr) {
		HashSet<String> set = new HashSet<String>();
		for (String str: arr) {
			set.add(str.trim());
		}
		return set;
	}

}
