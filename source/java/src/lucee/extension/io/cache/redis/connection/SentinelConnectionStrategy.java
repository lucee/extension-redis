package lucee.extension.io.cache.redis.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;
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
 * Connection strategy for Redis Sentinel.
 * Discovers the current master from Sentinel nodes and handles automatic failover.
 */
public class SentinelConnectionStrategy implements ConnectionStrategy {

	private ConnectionConfig config;
	private RedisPool pool;
	private final Object token = new Object();
	private final Object poolLock = new Object();

	// Current master information
	private final AtomicReference<String> masterHost = new AtomicReference<>();
	private final AtomicReference<Integer> masterPort = new AtomicReference<>();
	private final AtomicBoolean isRefreshing = new AtomicBoolean(false);

	// Sentinel subscription thread for failover notifications
	private Thread sentinelSubscriber;
	private volatile boolean running = true;

	@Override
	public void init(ConnectionConfig config) throws IOException {
		this.config = config;

		if (config.getSentinelNodes().isEmpty()) {
			throw new IOException("Sentinel nodes must be configured for Sentinel mode");
		}

		if (config.getSentinelMasterName() == null || config.getSentinelMasterName().isEmpty()) {
			throw new IOException("Sentinel master name must be configured for Sentinel mode");
		}

		// Discover the current master
		discoverMaster();
	}

	@Override
	public RedisPool createPool() throws IOException {
		synchronized (poolLock) {
			if (pool != null) {
				return pool;
			}

			String host = masterHost.get();
			int port = masterPort.get();

			if (host == null || port == 0) {
				discoverMaster();
				host = masterHost.get();
				port = masterPort.get();
			}

			Log log = config.getLog();
			if (log != null) {
				log.debug("redis-cache", "Creating Sentinel connection pool to master at " + host + ":" + port);
			}

			RedisPoolListener listener = new RedisPoolListenerNotifyOnReturn(token);

			RedisFactory factory = new RedisFactory(
				config.getClassLoader(),
				host,
				port,
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

			// Start sentinel subscription for failover notifications
			startSentinelSubscription();

			return pool;
		}
	}

	/**
	 * Discover the current master from Sentinel nodes.
	 */
	private void discoverMaster() throws IOException {
		Log log = config.getLog();
		List<HostPort> sentinels = config.getSentinelNodes();
		String masterName = config.getSentinelMasterName();

		for (HostPort sentinel : sentinels) {
			try {
				if (log != null) {
					log.debug("redis-cache", "Querying Sentinel at " + sentinel + " for master " + masterName);
				}

				String[] masterInfo = getMasterFromSentinel(sentinel.host, sentinel.port, masterName);
				if (masterInfo != null && masterInfo.length == 2) {
					masterHost.set(masterInfo[0]);
					masterPort.set(Integer.parseInt(masterInfo[1]));

					if (log != null) {
						log.info("redis-cache", "Discovered master at " + masterInfo[0] + ":" + masterInfo[1] + " from Sentinel");
					}
					return;
				}
			}
			catch (Exception e) {
				if (log != null) {
					log.warn("redis-cache", "Failed to query Sentinel at " + sentinel + ": " + e.getMessage());
				}
			}
		}

		throw new IOException("Could not discover Redis master from any Sentinel node");
	}

	/**
	 * Query a Sentinel node for the master address.
	 */
	private String[] getMasterFromSentinel(String sentinelHost, int sentinelPort, String masterName) throws IOException {
		Socket socket = null;
		try {
			socket = config.isSsl() ? SSLSocketFactory.getDefault().createSocket() : new Socket();
			socket.connect(new InetSocketAddress(sentinelHost, sentinelPort), config.getSocketTimeout());

			Redis redis = new Redis(config.getClassLoader(), socket);

			// SENTINEL GET-MASTER-ADDR-BY-NAME <master-name>
			Object result = redis.call("SENTINEL", "GET-MASTER-ADDR-BY-NAME", masterName);

			if (result instanceof List) {
				List<?> list = (List<?>) result;
				if (list.size() == 2) {
					String host = new String((byte[]) list.get(0), Coder.UTF8);
					String port = new String((byte[]) list.get(1), Coder.UTF8);
					return new String[] { host, port };
				}
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

	/**
	 * Start a background thread that subscribes to Sentinel failover notifications.
	 */
	private void startSentinelSubscription() {
		if (sentinelSubscriber != null && sentinelSubscriber.isAlive()) {
			return;
		}

		sentinelSubscriber = new Thread(() -> {
			Log log = config.getLog();
			while (running) {
				for (HostPort sentinel : config.getSentinelNodes()) {
					if (!running) break;

					Socket socket = null;
					try {
						socket = config.isSsl() ? SSLSocketFactory.getDefault().createSocket() : new Socket();
						socket.connect(new InetSocketAddress(sentinel.host, sentinel.port), config.getSocketTimeout());
						socket.setSoTimeout(0); // Blocking read for subscription

						Redis redis = new Redis(config.getClassLoader(), socket);

						// Subscribe to +switch-master channel
						redis.call("SUBSCRIBE", "+switch-master");

						if (log != null) {
							log.debug("redis-cache", "Subscribed to Sentinel failover notifications at " + sentinel);
						}

						// Read messages until disconnected
						while (running) {
							Object message = redis.read();
							if (message instanceof List) {
								List<?> msg = (List<?>) message;
								if (msg.size() >= 3) {
									String type = new String((byte[]) msg.get(0), Coder.UTF8);
									if ("message".equals(type)) {
										String channel = new String((byte[]) msg.get(1), Coder.UTF8);
										String data = new String((byte[]) msg.get(2), Coder.UTF8);

										if ("+switch-master".equals(channel)) {
											handleMasterSwitch(data);
										}
									}
								}
							}
						}
					}
					catch (Exception e) {
						if (running && log != null) {
							log.warn("redis-cache", "Sentinel subscription error at " + sentinel + ": " + e.getMessage());
						}
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

					// Wait before trying next sentinel
					if (running) {
						try {
							Thread.sleep(1000);
						}
						catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
			}
		}, "Redis-Sentinel-Subscriber");

		sentinelSubscriber.setDaemon(true);
		sentinelSubscriber.start();
	}

	/**
	 * Handle a master switch notification from Sentinel.
	 * Format: <master-name> <old-ip> <old-port> <new-ip> <new-port>
	 */
	private void handleMasterSwitch(String data) {
		Log log = config.getLog();
		String[] parts = data.split(" ");
		if (parts.length >= 5) {
			String masterName = parts[0];
			String newHost = parts[3];
			int newPort = Integer.parseInt(parts[4]);

			if (masterName.equals(config.getSentinelMasterName())) {
				if (log != null) {
					log.info("redis-cache", "Sentinel detected master switch: " + masterHost.get() + ":" + masterPort.get() +
						" -> " + newHost + ":" + newPort);
				}

				// Update master information
				masterHost.set(newHost);
				masterPort.set(newPort);

				// Recreate the pool with new master
				recreatePool();
			}
		}
	}

	/**
	 * Recreate the connection pool after a failover.
	 */
	private void recreatePool() {
		if (isRefreshing.compareAndSet(false, true)) {
			try {
				synchronized (poolLock) {
					RedisPool oldPool = pool;
					pool = null;

					// Close old pool
					if (oldPool != null) {
						try {
							oldPool.close();
						}
						catch (Exception e) {
							Log log = config.getLog();
							if (log != null) {
								log.error("redis-cache", e);
							}
						}
					}

					// Create new pool with updated master
					createPool();
				}
			}
			catch (IOException e) {
				Log log = config.getLog();
				if (log != null) {
					log.error("redis-cache", "Failed to recreate pool after failover: " + e.getMessage());
				}
			}
			finally {
				isRefreshing.set(false);
			}
		}
	}

	@Override
	public String getMasterHost() throws IOException {
		String host = masterHost.get();
		if (host == null) {
			discoverMaster();
			host = masterHost.get();
		}
		return host;
	}

	@Override
	public int getMasterPort() throws IOException {
		Integer port = masterPort.get();
		if (port == null || port == 0) {
			discoverMaster();
			port = masterPort.get();
		}
		return port;
	}

	@Override
	public boolean supportsReadReplicas() {
		return true; // Sentinel can have replicas
	}

	@Override
	public void onConnectionFailure(String host, int port) throws IOException {
		Log log = config.getLog();
		if (log != null) {
			log.warn("redis-cache", "Connection failure to Redis at " + host + ":" + port + ", checking Sentinel for failover");
		}

		// Check if this was the master
		String currentMasterHost = masterHost.get();
		Integer currentMasterPort = masterPort.get();

		if (host.equals(currentMasterHost) && port == currentMasterPort) {
			// Master failed, try to discover new master
			try {
				discoverMaster();
				if (!host.equals(masterHost.get()) || port != masterPort.get()) {
					// Master changed, recreate pool
					recreatePool();
				}
			}
			catch (IOException e) {
				if (log != null) {
					log.error("redis-cache", "Failed to discover new master after connection failure: " + e.getMessage());
				}
				throw e;
			}
		}
	}

	@Override
	public void shutdown() {
		running = false;

		if (sentinelSubscriber != null) {
			sentinelSubscriber.interrupt();
			try {
				sentinelSubscriber.join(5000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		synchronized (poolLock) {
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
	}

	@Override
	public String getModeName() {
		return MODE_SENTINEL;
	}

	@Override
	public void verify() throws IOException {
		discoverMaster();
		if (pool == null) {
			createPool();
		}
	}

	/**
	 * Get the connection pool.
	 */
	public RedisPool getPool() {
		return pool;
	}
}
