package lucee.extension.io.cache.redis.connection;

import java.io.IOException;

import lucee.commons.io.log.Log;
import lucee.extension.io.cache.pool.RedisFactory;
import lucee.extension.io.cache.pool.RedisPool;
import lucee.extension.io.cache.pool.RedisPoolListener;
import lucee.extension.io.cache.pool.RedisPoolListenerNotifyOnReturn;

/**
 * Connection strategy for standalone Redis server.
 * This is the simplest strategy, connecting to a single Redis instance.
 */
public class StandaloneConnectionStrategy implements ConnectionStrategy {

	private ConnectionConfig config;
	private RedisPool pool;
	private final Object token = new Object();

	@Override
	public void init(ConnectionConfig config) throws IOException {
		this.config = config;
	}

	@Override
	public RedisPool createPool() throws IOException {
		if (pool != null) {
			return pool;
		}

		Log log = config.getLog();
		if (log != null) {
			log.debug("redis-cache", "Creating standalone connection pool to " + config.getHost() + ":" + config.getPort());
		}

		RedisPoolListener listener = new RedisPoolListenerNotifyOnReturn(token);

		RedisFactory factory = new RedisFactory(
			config.getClassLoader(),
			config.getHost(),
			config.getPort(),
			config.getUsername(),
			config.getPassword(),
			config.isSsl(),
			config.getSocketTimeout(),
			config.getIdleTimeout(),
			config.getLiveTimeout(),
			config.getDatabaseIndex(),
			log
		);

		pool = new RedisPool(factory, config.getPoolConfig(), listener);
		return pool;
	}

	@Override
	public String getMasterHost() throws IOException {
		return config.getHost();
	}

	@Override
	public int getMasterPort() throws IOException {
		return config.getPort();
	}

	@Override
	public boolean supportsReadReplicas() {
		return false;
	}

	@Override
	public void onConnectionFailure(String host, int port) throws IOException {
		// For standalone, we just log the failure
		// No automatic failover available
		Log log = config.getLog();
		if (log != null) {
			log.warn("redis-cache", "Connection failure to standalone Redis at " + host + ":" + port);
		}
	}

	@Override
	public void shutdown() {
		if (pool != null) {
			try {
				pool.close();
			}
			catch (Exception e) {
				Log log = config.getLog();
				if (log != null) {
					log.error("redis-cache", e);
				}
			}
			pool = null;
		}
	}

	@Override
	public String getModeName() {
		return MODE_STANDALONE;
	}

	@Override
	public void verify() throws IOException {
		if (pool == null) {
			createPool();
		}
		// The pool will validate connections on borrow
	}

	/**
	 * Get the connection pool.
	 */
	public RedisPool getPool() {
		return pool;
	}
}
