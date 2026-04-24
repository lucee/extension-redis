package lucee.extension.io.cache.redis.connection;

import java.util.ArrayList;
import java.util.List;

import lucee.commons.io.log.Log;
import lucee.extension.io.cache.pool.RedisPoolConfig;

/**
 * Configuration holder for Redis connection strategies.
 * Supports Standalone, Sentinel, and Cluster configurations.
 */
public class ConnectionConfig {

	// Common configuration
	private ClassLoader classLoader;
	private String connectionMode = ConnectionStrategy.MODE_STANDALONE;
	private String username;
	private String password;
	private boolean ssl;
	private int socketTimeout = 2000;
	private long idleTimeout = 300000;
	private long liveTimeout = 3600000;
	private int databaseIndex = -1;
	private Log log;
	private RedisPoolConfig poolConfig;

	// Standalone configuration
	private String host = "localhost";
	private int port = 6379;

	// Sentinel configuration
	private String sentinelMasterName;
	private List<HostPort> sentinelNodes = new ArrayList<>();

	// Cluster configuration
	private List<HostPort> clusterNodes = new ArrayList<>();
	private boolean clusterFollowRedirects = true;
	private int clusterMaxRedirects = 5;

	/**
	 * Represents a host:port pair.
	 */
	public static class HostPort {
		public final String host;
		public final int port;

		public HostPort(String host, int port) {
			this.host = host;
			this.port = port;
		}

		@Override
		public String toString() {
			return host + ":" + port;
		}

		/**
		 * Parse a host:port string.
		 */
		public static HostPort parse(String hostPort) {
			String[] parts = hostPort.trim().split(":");
			if (parts.length == 2) {
				return new HostPort(parts[0].trim(), Integer.parseInt(parts[1].trim()));
			}
			else if (parts.length == 1) {
				return new HostPort(parts[0].trim(), 6379);
			}
			throw new IllegalArgumentException("Invalid host:port format: " + hostPort);
		}

		/**
		 * Parse a comma-separated list of host:port pairs.
		 */
		public static List<HostPort> parseList(String hostPorts) {
			List<HostPort> result = new ArrayList<>();
			if (hostPorts != null && !hostPorts.trim().isEmpty()) {
				for (String hostPort : hostPorts.split(",")) {
					if (!hostPort.trim().isEmpty()) {
						result.add(parse(hostPort));
					}
				}
			}
			return result;
		}
	}

	// Getters and Setters

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	public ConnectionConfig setClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
		return this;
	}

	public String getConnectionMode() {
		return connectionMode;
	}

	public ConnectionConfig setConnectionMode(String connectionMode) {
		this.connectionMode = connectionMode != null ? connectionMode.toLowerCase() : ConnectionStrategy.MODE_STANDALONE;
		return this;
	}

	public String getUsername() {
		return username;
	}

	public ConnectionConfig setUsername(String username) {
		this.username = username;
		return this;
	}

	public String getPassword() {
		return password;
	}

	public ConnectionConfig setPassword(String password) {
		this.password = password;
		return this;
	}

	public boolean isSsl() {
		return ssl;
	}

	public ConnectionConfig setSsl(boolean ssl) {
		this.ssl = ssl;
		return this;
	}

	public int getSocketTimeout() {
		return socketTimeout;
	}

	public ConnectionConfig setSocketTimeout(int socketTimeout) {
		this.socketTimeout = socketTimeout;
		return this;
	}

	public long getIdleTimeout() {
		return idleTimeout;
	}

	public ConnectionConfig setIdleTimeout(long idleTimeout) {
		this.idleTimeout = idleTimeout;
		return this;
	}

	public long getLiveTimeout() {
		return liveTimeout;
	}

	public ConnectionConfig setLiveTimeout(long liveTimeout) {
		this.liveTimeout = liveTimeout;
		return this;
	}

	public int getDatabaseIndex() {
		return databaseIndex;
	}

	public ConnectionConfig setDatabaseIndex(int databaseIndex) {
		this.databaseIndex = databaseIndex;
		return this;
	}

	public Log getLog() {
		return log;
	}

	public ConnectionConfig setLog(Log log) {
		this.log = log;
		return this;
	}

	public RedisPoolConfig getPoolConfig() {
		return poolConfig;
	}

	public ConnectionConfig setPoolConfig(RedisPoolConfig poolConfig) {
		this.poolConfig = poolConfig;
		return this;
	}

	public String getHost() {
		return host;
	}

	public ConnectionConfig setHost(String host) {
		this.host = host;
		return this;
	}

	public int getPort() {
		return port;
	}

	public ConnectionConfig setPort(int port) {
		this.port = port;
		return this;
	}

	public String getSentinelMasterName() {
		return sentinelMasterName;
	}

	public ConnectionConfig setSentinelMasterName(String sentinelMasterName) {
		this.sentinelMasterName = sentinelMasterName;
		return this;
	}

	public List<HostPort> getSentinelNodes() {
		return sentinelNodes;
	}

	public ConnectionConfig setSentinelNodes(List<HostPort> sentinelNodes) {
		this.sentinelNodes = sentinelNodes != null ? sentinelNodes : new ArrayList<>();
		return this;
	}

	public ConnectionConfig setSentinelNodesString(String nodes) {
		this.sentinelNodes = HostPort.parseList(nodes);
		return this;
	}

	public List<HostPort> getClusterNodes() {
		return clusterNodes;
	}

	public ConnectionConfig setClusterNodes(List<HostPort> clusterNodes) {
		this.clusterNodes = clusterNodes != null ? clusterNodes : new ArrayList<>();
		return this;
	}

	public ConnectionConfig setClusterNodesString(String nodes) {
		this.clusterNodes = HostPort.parseList(nodes);
		return this;
	}

	public boolean isClusterFollowRedirects() {
		return clusterFollowRedirects;
	}

	public ConnectionConfig setClusterFollowRedirects(boolean clusterFollowRedirects) {
		this.clusterFollowRedirects = clusterFollowRedirects;
		return this;
	}

	public int getClusterMaxRedirects() {
		return clusterMaxRedirects;
	}

	public ConnectionConfig setClusterMaxRedirects(int clusterMaxRedirects) {
		this.clusterMaxRedirects = clusterMaxRedirects;
		return this;
	}

	/**
	 * Create a ConnectionStrategy based on this configuration.
	 */
	public ConnectionStrategy createStrategy() throws java.io.IOException {
		ConnectionStrategy strategy;
		switch (connectionMode) {
			case ConnectionStrategy.MODE_SENTINEL:
				strategy = new SentinelConnectionStrategy();
				break;
			case ConnectionStrategy.MODE_CLUSTER:
				strategy = new ClusterConnectionStrategy();
				break;
			case ConnectionStrategy.MODE_STANDALONE:
			default:
				strategy = new StandaloneConnectionStrategy();
				break;
		}
		strategy.init(this);
		return strategy;
	}
}
