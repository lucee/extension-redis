package lucee.extension.io.cache.redis;

public class RedisCacheUtils {

	private RedisCacheUtils() {
	}

	public static String formatKey(String cacheName, String key){
		if(key.contains(RedisSentinelConnection.getNamespace(cacheName))){
			return key;
		}
		String res = RedisSentinelConnection.getNamespace(cacheName) + ':' + key;
		return res.toLowerCase();
	}

	public static  String removeNamespace(String cacheName, String key){
		return key.replace(RedisSentinelConnection.getNamespace(cacheName) + ":", "");

	}

}
