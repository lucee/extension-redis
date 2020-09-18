package lucee.extension.io.cache.util;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;

import lucee.extension.io.cache.redis.Redis;

public class RedisUtil {
	private ObjectPool<Redis> pool;
	private boolean debug;

	public RedisUtil(ObjectPool<Redis> pool, boolean debug) {
		this.pool = pool;
		this.debug = debug;
	}

	// get socket
	public Redis getConnection() throws Exception {

		int actives = pool.getNumActive();
		int idle = pool.getNumIdle();
		if (debug) System.out.println("SocketUtil.getConnection before now actives : " + actives + ", idle : " + idle);

		if (debug) System.out.println(">>>>> borrowObject start");
		Redis redis = pool.borrowObject();
		if (debug) System.out.println(">>>>> borrowObject end");

		actives = pool.getNumActive();
		idle = pool.getNumIdle();
		if (debug) System.out.println("SocketUtil.getConnection after now actives : " + actives + ", idle : " + idle);

		return redis;
	}

	// return socket
	public void releaseConnection(Redis redis) throws Exception {
		if (redis == null) return;
		int actives = pool.getNumActive();
		int idle = pool.getNumIdle();
		if (debug) System.out.println("SocketUtil.releaseConnection before now actives : " + actives + ", idle : " + idle);

		if (debug) System.out.println(">>>>> returnObject start");
		pool.returnObject(redis);
		if (debug) System.out.println(">>>>> returnObject end");

		actives = pool.getNumActive();
		idle = pool.getNumIdle();
		if (debug) System.out.println("SocketUtil.releaseConnection after now actives : " + actives + ", idle : " + idle);

	}

	public static void invalidateObjectEL(GenericObjectPool<Redis> pool, Redis conn) {
		try {
			if (conn != null) pool.invalidateObject(conn);
		}
		catch (Exception e) {}
	}
}
