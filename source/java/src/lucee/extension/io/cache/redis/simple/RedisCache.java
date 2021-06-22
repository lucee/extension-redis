package lucee.extension.io.cache.redis.simple;

public class RedisCache extends lucee.extension.io.cache.redis.RedisCache {
	@Override
	public boolean isObjectSerialisationSupported() {
		return true;
	}
}
