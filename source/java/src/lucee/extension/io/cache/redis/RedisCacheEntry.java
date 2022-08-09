package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.util.Date;

import lucee.commons.io.cache.CacheEntry;
import lucee.extension.io.cache.redis.InfoParser.DebugObject;
import lucee.extension.io.cache.util.Coder;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;

public class RedisCacheEntry implements CacheEntry {

	private final RedisCache cache;
	private final byte[] bkey;
	private final Object value;
	private final long size;
	private DebugObject debObj;

	public RedisCacheEntry(RedisCache cache, byte[] bkey, Object value, long size) {
		this.cache = cache;
		this.bkey = bkey;
		this.value = value;
		this.size = size;
	}

	@Override
	public Date lastHit() {
		return null;
	}

	@Override
	public Date lastModified() {
		DebugObject deObj = getDebugObject();
		return deObj == null ? null : deObj.getLRUTime();
	}

	private DebugObject getDebugObject() {
		if (debObj != null) return debObj;
		synchronized (cache) {
			if (debObj == null) {
				try {
					debObj = cache.getDebugObject(cache.getConnection(), bkey);
				}
				catch (IOException e) {
				}
			}
		}
		return debObj;
	}

	@Override
	public Date created() {
		return null; // To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public int hitCount() {
		return 0;
	}

	@Override
	public String getKey() {
		return Coder.toKey(bkey);
	}

	@Override
	public Object getValue() {
		return value;
	}

	@Override
	public long size() {
		DebugObject deObj = getDebugObject();
		return deObj == null ? size : deObj.serializedLength; // To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public long liveTimeSpan() {
		return 0; // To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public long idleTimeSpan() {
		return 0; // To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public Struct getCustomInfo() {
		Struct metadata = CFMLEngineFactory.getInstance().getCreationUtil().createStruct();
		return metadata;
	}

}
