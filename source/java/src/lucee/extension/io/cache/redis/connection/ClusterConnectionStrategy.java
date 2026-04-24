package lucee.extension.io.cache.redis.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocketFactory;

import lucee.commons.io.log.Log;
import lucee.extension.io.cache.pool.RedisFactory;
import lucee.extension.io.cache.pool.RedisPool;
import lucee.extension.io.cache.pool.RedisPoolListener;
import lucee.extension.io.cache.pool.RedisPoolListenerNotifyOnReturn;
import lucee.extension.io.cache.redis.Redis;
import lucee.extension.io.cache.redis.connection.ConnectionConfig.HostPort;
import lucee.extension.io.cache.util.Coder;

/**
 * Connection strategy for Redis Cluster.
 * Handles slot-based routing and MOVED/ASK redirections.
 *
 * Note: This is a simplified implementation that uses a single pool
 * for the "main" node and handles redirections. A full implementation
 * would maintain pools per node for optimal performance.
 */
public class ClusterConnectionStrategy implements ConnectionStrategy {

	// Redis Cluster has 16384 slots
	private static final int CLUSTER_SLOTS = 16384;

	// CRC16 lookup table for slot calculation
	private static final int[] CRC16_TAB = new int[256];

	static {
		// Initialize CRC16 table
		for (int i = 0; i < 256; i++) {
			int crc = i;
			for (int j = 0; j < 8; j++) {
				if ((crc & 1) == 1) {
					crc = (crc >>> 1) ^ 0xA001;
				}
				else {
					crc = crc >>> 1;
				}
			}
			CRC16_TAB[i] = crc;
		}
	}

	private ConnectionConfig config;
	private final Object poolLock = new Object();
	private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
	private final Object token = new Object();

	// Main pool (first available node)
	private RedisPool mainPool;
	private String mainHost;
	private int mainPort;

	// Slot to node mapping (slot -> host:port)
	private final ConcurrentHashMap<Integer, String> slotToNode = new ConcurrentHashMap<>();

	// Node pools (host:port -> pool)
	private final ConcurrentHashMap<String, RedisPool> nodePools = new ConcurrentHashMap<>();

	@Override
	public void init(ConnectionConfig config) throws IOException {
		this.config = config;

		if (config.getClusterNodes().isEmpty()) {
			throw new IOException("Cluster nodes must be configured for Cluster mode");
		}

		// Discover cluster topology
		discoverClusterTopology();
	}

	/**
	 * Discover the cluster topology by querying CLUSTER SLOTS.
	 */
	private void discoverClusterTopology() throws IOException {
		Log log = config.getLog();
		List<HostPort> nodes = config.getClusterNodes();

		for (HostPort node : nodes) {
			try {
				if (log != null) {
					log.debug("redis-cache", "Querying cluster topology from " + node);
				}

				Map<Integer, String> slots = getClusterSlots(node.host, node.port);
				if (slots != null && !slots.isEmpty()) {
					slotToNode.clear();
					slotToNode.putAll(slots);

					// Use this node as the main node
					mainHost = node.host;
					mainPort = node.port;

					if (log != null) {
						log.info("redis-cache", "Discovered cluster topology from " + node + ", " + slots.size() + " slot mappings");
					}
					return;
				}
			}
			catch (Exception e) {
				if (log != null) {
					log.warn("redis-cache", "Failed to query cluster topology from " + node + ": " + e.getMessage());
				}
			}
		}

		// Fall back to first node if topology discovery fails
		HostPort firstNode = nodes.get(0);
		mainHost = firstNode.host;
		mainPort = firstNode.port;

		if (log != null) {
			log.warn("redis-cache", "Could not discover cluster topology, using first node: " + mainHost + ":" + mainPort);
		}
	}

	/**
	 * Query CLUSTER SLOTS from a node.
	 */
	private Map<Integer, String> getClusterSlots(String host, int port) throws IOException {
		Socket socket = null;
		try {
			socket = config.isSsl() ? SSLSocketFactory.getDefault().createSocket() : new Socket();
			socket.connect(new InetSocketAddress(host, port), config.getSocketTimeout());

			Redis redis = new Redis(config.getClassLoader(), socket);

			// Authenticate if needed
			if (config.getPassword() != null) {
				if (config.getUsername() != null) {
					redis.call("AUTH", config.getUsername(), config.getPassword());
				}
				else {
					redis.call("AUTH", config.getPassword());
				}
			}

			Object result = redis.call("CLUSTER", "SLOTS");

			if (result instanceof List) {
				Map<Integer, String> slots = new HashMap<>();
				for (Object slotRange : (List<?>) result) {
					if (slotRange instanceof List) {
						List<?> range = (List<?>) slotRange;
						if (range.size() >= 3) {
							long start = (Long) range.get(0);
							long end = (Long) range.get(1);
							List<?> master = (List<?>) range.get(2);
							if (master.size() >= 2) {
								String nodeHost = new String((byte[]) master.get(0), Coder.UTF8);
								long nodePort = (Long) master.get(1);
								String nodeAddr = nodeHost + ":" + nodePort;

								// Map all slots in this range to the master node
								for (long slot = start; slot <= end; slot++) {
									slots.put((int) slot, nodeAddr);
								}
							}
						}
					}
				}
				return slots;
			}

			return null;
		}
		finally {
			if (socket != null) {
				try {
					socket.close();
				}
				catch (Exception e) {
					// Ignore
				}
			}
		}
	}

	@Override
	public RedisPool createPool() throws IOException {
		synchronized (poolLock) {
			if (mainPool != null) {
				return mainPool;
			}

			Log log = config.getLog();
			if (log != null) {
				log.debug("redis-cache", "Creating cluster connection pool to " + mainHost + ":" + mainPort);
			}

			RedisPoolListener listener = new RedisPoolListenerNotifyOnReturn(token);

			RedisFactory factory = new RedisFactory(
				config.getClassLoader(),
				mainHost,
				mainPort,
				config.getUsername(),
				config.getPassword(),
				config.isSsl(),
				config.getSocketTimeout(),
				config.getIdleTimeout(),
				config.getLiveTimeout(),
				-1, // Don't use SELECT in cluster mode
				log
			);

			mainPool = new RedisPool(factory, config.getPoolConfig(), listener);
			nodePools.put(mainHost + ":" + mainPort, mainPool);

			return mainPool;
		}
	}

	/**
	 * Calculate the Redis Cluster slot for a key.
	 * If the key contains {...}, uses the content between braces (hash tag).
	 */
	public static int getSlot(byte[] key) {
		int start = -1;
		int end = -1;

		// Look for hash tag {...}
		for (int i = 0; i < key.length; i++) {
			if (key[i] == '{') {
				start = i;
				break;
			}
		}

		if (start >= 0) {
			for (int i = start + 1; i < key.length; i++) {
				if (key[i] == '}') {
					end = i;
					break;
				}
			}
		}

		// If valid hash tag found, use that portion
		if (start >= 0 && end > start + 1) {
			return crc16(key, start + 1, end) & (CLUSTER_SLOTS - 1);
		}

		// Otherwise use whole key
		return crc16(key, 0, key.length) & (CLUSTER_SLOTS - 1);
	}

	/**
	 * Calculate CRC16 for slot determination.
	 */
	private static int crc16(byte[] bytes, int start, int end) {
		int crc = 0;
		for (int i = start; i < end; i++) {
			crc = ((crc >>> 8) ^ CRC16_TAB[(crc ^ bytes[i]) & 0xFF]) & 0xFFFF;
		}
		return crc;
	}

	/**
	 * Get the node address for a given slot.
	 */
	public String getNodeForSlot(int slot) {
		String node = slotToNode.get(slot);
		return node != null ? node : mainHost + ":" + mainPort;
	}

	/**
	 * Get the node address for a given key.
	 */
	public String getNodeForKey(byte[] key) {
		int slot = getSlot(key);
		return getNodeForSlot(slot);
	}

	/**
	 * Handle a MOVED redirection by updating slot mapping.
	 *
	 * @param slot The slot that was moved
	 * @param host The new host
	 * @param port The new port
	 */
	public void handleMoved(int slot, String host, int port) {
		String nodeAddr = host + ":" + port;
		slotToNode.put(slot, nodeAddr);

		Log log = config.getLog();
		if (log != null) {
			log.debug("redis-cache", "MOVED: slot " + slot + " -> " + nodeAddr);
		}
	}

	/**
	 * Refresh cluster topology.
	 */
	public void refreshTopology() throws IOException {
		if (isRefreshing.compareAndSet(false, true)) {
			try {
				discoverClusterTopology();
			}
			finally {
				isRefreshing.set(false);
			}
		}
	}

	@Override
	public String getMasterHost() throws IOException {
		return mainHost;
	}

	@Override
	public int getMasterPort() throws IOException {
		return mainPort;
	}

	@Override
	public boolean supportsReadReplicas() {
		return true; // Cluster nodes can have replicas
	}

	@Override
	public void onConnectionFailure(String host, int port) throws IOException {
		Log log = config.getLog();
		if (log != null) {
			log.warn("redis-cache", "Connection failure to cluster node at " + host + ":" + port);
		}

		// Refresh cluster topology
		refreshTopology();
	}

	@Override
	public void shutdown() {
		synchronized (poolLock) {
			for (RedisPool pool : nodePools.values()) {
				try {
					pool.close();
				}
				catch (Exception e) {
					Log log = config.getLog();
					if (log != null) {
						log.error("redis-cache", e);
					}
				}
			}
			nodePools.clear();
			mainPool = null;
		}
	}

	@Override
	public String getModeName() {
		return MODE_CLUSTER;
	}

	@Override
	public void verify() throws IOException {
		discoverClusterTopology();
		if (mainPool == null) {
			createPool();
		}
	}

	/**
	 * Get the main connection pool.
	 */
	public RedisPool getPool() {
		return mainPool;
	}

	/**
	 * Get the slot count (for testing/diagnostics).
	 */
	public int getSlotMappingCount() {
		return slotToNode.size();
	}
}
