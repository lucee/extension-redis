package lucee.extension.io.cache.redis;

import java.util.Date;

import lucee.commons.io.cache.CacheEntry;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;

public class RedisCacheEntry implements CacheEntry {

    private AbstractRedisCache cache;
    private String key;
    private Object value;
    private long size;

    public RedisCacheEntry(AbstractRedisCache cache, String key, Object value, long size) {
	this.cache = cache;
	this.key = key;
	this.value = value;
	this.size = size;
    }

    @Override
    public Date lastHit() {
	return null; // TODO To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Date lastModified() {
	return null; // TODO To change body of implemented methods use File | Settings | File Templates.
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
	return key;
    }

    @Override
    public Object getValue() {
	return value;
    }

    @Override
    public long size() {
	return size; // To change body of implemented methods use File | Settings | File Templates.
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
