package lucee.extension.io.cache.redis;

import java.util.Date;

import lucee.commons.io.cache.CacheEntry;
import lucee.extension.io.cache.redis.InfoParser.DebugObject;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;

public class RedisCacheEntry implements CacheEntry {

	private final RedisCache cache;
	private final byte[] bkey;
	private final Object value;
	private final long size;
	private final DebugObject deObj;

	public RedisCacheEntry(RedisCache cache, byte[] bkey, Object value, long size, DebugObject deObj) {
		this.cache = cache;
		this.bkey = bkey;
		this.value = value;
		this.size = size;
		this.deObj = deObj;
	}

	@Override
	public Date lastHit() {
		return null;
	}

	@Override
	public Date lastModified() {
		return deObj == null ? null : deObj.getLRUTime();
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
		return new String(bkey, CacheSupport.UTF8);
	}

	@Override
	public Object getValue() {
		return value;
	}

	@Override
	public long size() {
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
		/*
		 * try { metadata.set("hits", hitCount()); } catch (PageException e) { e.printStackTrace(); }
		 */
		return metadata;
	}

}
