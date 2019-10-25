package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import redis.clients.jedis.Jedis;

public class RedisCacheUtils {

    private RedisCacheUtils() {}

    public static String removeNamespace(String nameSpace, String key) {
	if (nameSpace != null && key.startsWith(nameSpace.toLowerCase())) return key.replace(nameSpace.toLowerCase() + ":", "");
	return key;

    }

    public static String addNamespace(String nameSpace, String key) {
	if (nameSpace == null) return key; // key is already lowercased due to validateKey call

	if (key.startsWith(nameSpace.toLowerCase() + ":")) {
	    return key;
	}
	String res = nameSpace.toLowerCase() + ':' + key; // setting case sensitive
	return res;
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
