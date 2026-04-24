package lucee.extension.io.cache.redis.connection;

import java.io.IOException;

import lucee.extension.io.cache.pool.RedisPool;

/**
 * Interface for different Redis connection strategies.
 * Implementations handle connection creation and management for:
 * - Standalone: Single Redis server
 * - Sentinel: Redis Sentinel for high availability
 * - Cluster: Redis Cluster for horizontal scaling
 */
public interface ConnectionStrategy {

	/**
	 * Connection mode constants.
	 */
	String MODE_STANDALONE = "standalone";
	String MODE_SENTINEL = "sentinel";
	String MODE_CLUSTER = "cluster";

	/**
	 * Initialize the connection strategy with configuration.
	 *
	 * @param config Configuration for this strategy
	 * @throws IOException If initialization fails
	 */
	void init(ConnectionConfig config) throws IOException;

	/**
	 * Create and return a connection pool.
	 *
	 * @return The configured Redis connection pool
	 * @throws IOException If pool creation fails
	 */
	RedisPool createPool() throws IOException;

	/**
	 * Get the current master host for write operations.
	 * For Standalone, this returns the configured host.
	 * For Sentinel, this returns the current master.
	 * For Cluster, this may vary based on key slot.
	 *
	 * @return The master host address
	 * @throws IOException If master cannot be determined
	 */
	String getMasterHost() throws IOException;

	/**
	 * Get the current master port for write operations.
	 *
	 * @return The master port
	 * @throws IOException If master cannot be determined
	 */
	int getMasterPort() throws IOException;

	/**
	 * Check if this strategy supports read replicas.
	 *
	 * @return true if read replicas are available
	 */
	boolean supportsReadReplicas();

	/**
	 * Handle a connection failure, potentially triggering failover.
	 *
	 * @param host The host that failed
	 * @param port The port that failed
	 * @throws IOException If failover handling fails
	 */
	void onConnectionFailure(String host, int port) throws IOException;

	/**
	 * Shutdown and cleanup resources.
	 */
	void shutdown();

	/**
	 * Get the connection mode name.
	 *
	 * @return The mode name (standalone, sentinel, or cluster)
	 */
	String getModeName();

	/**
	 * Verify the connection is working.
	 *
	 * @throws IOException If verification fails
	 */
	void verify() throws IOException;
}
