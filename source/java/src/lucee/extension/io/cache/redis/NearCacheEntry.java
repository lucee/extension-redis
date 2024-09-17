package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.util.Date;

import lucee.commons.io.cache.CacheEntry;
import lucee.extension.io.cache.util.Coder;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;

public class NearCacheEntry implements CacheEntry {

	private byte[] key;
	private Object val;
	private int exp;
	private long created;
	private byte[] serialized;
	private long count;

	public NearCacheEntry(byte[] key, Object val, int exp, long count) {
		this.key = key;
		this.val = val;
		this.exp = exp;
		this.created = System.currentTimeMillis();
		this.count = count;
	}

	private NearCacheEntry(byte[] key, Object val, int exp, long count, byte[] serialized) {
		this.key = key;
		this.val = val;
		this.exp = exp;
		this.created = System.currentTimeMillis();
		this.count = count;
		this.serialized = serialized;
	}

	/**
	 * copy this object, also copying the underlying object as-if it had been materialized from cache (in particular, the underlying object
	 * is copied such that is no longer a reference to the original underlying object). Note that the serialized byte[] is shared across instances of
	 * copied NearCacheEntries.
	 */
	public NearCacheEntry copy(ClassLoader cl) throws IOException {
		byte[] bytes = serialized();
		return new NearCacheEntry(key, Coder.evaluate(cl, bytes), exp, count, bytes);
	}

	@Override
	public Date created() {
		return new Date(created);
	}

	public long createdTime() {
		return created;
	}

	@Override
	public Struct getCustomInfo() {
		Struct metadata = CFMLEngineFactory.getInstance().getCreationUtil().createStruct();
		return metadata;
	}

	@Override
	public String getKey() {
		return Coder.toKey(key);
	}

	public byte[] getByteKey() {
		return key;
	}

	@Override
	public Object getValue() {
		return val;
	}

	@Override
	public int hitCount() {
		return 0;
	}

	@Override
	public long idleTimeSpan() {
		return 0;
	}

	@Override
	public Date lastHit() {
		return created();
	}

	@Override
	public Date lastModified() {
		return created();
	}

	@Override
	public long liveTimeSpan() {
		return exp * 1000;
	}

	@Override
	public long size() {
		try {
			return serialized().length;
		}
		catch (IOException e) {
			return 0;
		}
	}

	public byte[] serialized() throws IOException {
		if (serialized == null) {
			synchronized (this) {
				if (serialized == null) {
					serialized = Coder.serialize(val);
				}

			}
		}
		return serialized;
	}

	public int getExpires() {
		return exp;
	}

	public long count() {
		return count;
	}
}
