package lucee.extension.io.cache.pool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import lucee.commons.io.log.Log;
import lucee.extension.io.cache.redis.Redis;
import lucee.loader.util.Util;

public class RedisFactory extends BasePooledObjectFactory<Redis> {
	private final ClassLoader cl;
	private final String host;
	private final int port;
	private final String username;
	private final String password;
	private final int databaseIndex;
	private final int socketTimeout;
	private final long idleTimeout;
	private final long liveTimeout;
	private final Log log;
	private final boolean ssl;
	private final String socketPath;
	private final String address;

	public RedisFactory(ClassLoader cl, String host, int port, String username, String password, boolean ssl, int socketTimeout, long idleTimeout, long liveTimeout,
			int databaseIndex, Log log) {
		this(cl, host, port, null, username, password, ssl, socketTimeout, idleTimeout, liveTimeout, databaseIndex, log);
	}

	/**
	 * @param socketPath path to a Unix domain socket file; when set, host, port and ssl are ignored
	 *            and the connection is made via the socket file (Java 16+)
	 */
	public RedisFactory(ClassLoader cl, String host, int port, String socketPath, String username, String password, boolean ssl, int socketTimeout, long idleTimeout,
			long liveTimeout, int databaseIndex, Log log) {
		this.cl = cl;
		this.username = Util.isEmpty(username) ? null : username;
		this.password = Util.isEmpty(password) ? null : password;
		this.host = host;
		this.port = port;
		this.socketPath = Util.isEmpty(socketPath, true) ? null : socketPath.trim();
		this.ssl = ssl;
		this.address = this.socketPath != null ? "unix:" + this.socketPath : host + ":" + port;
		if (this.socketPath != null && ssl && log != null) {
			log.warn("redis-cache", "ssl is ignored for connections via Unix domain socket [" + this.socketPath + "]");
		}

		this.socketTimeout = socketTimeout;
		this.idleTimeout = idleTimeout;
		this.liveTimeout = liveTimeout;
		this.log = log;
		this.databaseIndex = databaseIndex;
	}

	@Override
	public Redis create() throws IOException {
		if (log != null) log.debug("redis-cache", "create connection to " + address);
		Socket socket;
		if (socketPath != null) {
			// Unix domain socket: connecting is local and does not block like TCP, so socketTimeout does not apply
			try {
				socket = UnixDomainSocket.connect(socketPath);
			}
			catch (Exception e) {
				throw new IOException("The Redis client was not able to create a connection to [" + address + "]", e);
			}
		}
		else {
			socket = getSocket();
			InetSocketAddress serverInfo = new InetSocketAddress(host, port);
			try {
				if (socketTimeout > 0) socket.connect(serverInfo, socketTimeout);
				else socket.connect(serverInfo);
			}
			catch (Exception e) {
				throw new IOException("The Redis client was not able to create a connection to [" + host + ":" + port + "]", e);
			}
		}
		Redis redis;
		try {
			redis = new Redis(cl, socket);

			if (password != null) {
				if (username != null) redis.call("AUTH", username, password);
				else redis.call("AUTH", password);
			}
			if (databaseIndex > -1) {
				redis.call("SELECT", String.valueOf(databaseIndex));
			}
		}
		catch (IOException | RuntimeException e) {
			try {
				socket.close();
			}
			catch (Exception ex) {
				// ignore
			}
			throw e;
		}
		return redis;
	}

	private Socket getSocket() throws IOException {
		if (ssl) {

			SocketFactory factory = SSLSocketFactory.getDefault();
			return factory.createSocket();

		}
		else {
			return new Socket();
		}
	}

	/**
	 * Use the default PooledObject implementation.
	 */
	@Override
	public PooledObject<Redis> wrap(Redis redis) {
		return new DefaultPooledObject<Redis>(redis);
	}

	@Override
	public boolean validateObject(PooledObject<Redis> p) {
		Redis redis = p.getObject();
		// check timeout
		long now = System.currentTimeMillis();
		if (liveTimeout > 0 && redis.created + liveTimeout < now) {
			if (log != null) log.debug("redis-cache", "validateObject(reached live timeout:" + liveTimeout + ") " + address);
			return false;
		}
		if (idleTimeout > 0 && redis.lastUsed + idleTimeout < now) {
			if (log != null) log.debug("redis-cache", "validateObject(reached idle timeout:" + idleTimeout + ") " + address);
			return false;
		}

		// check socket
		Socket socket = redis.getSocket();
		if (socket == null) {
			if (log != null) log.debug("redis-cache", "validateObject(socket null) " + address);
			return false;
		}

		if (!socket.isConnected()) {
			if (log != null) log.debug("redis-cache", "validateObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ") " + address);
			return false;
		}
		if (socket.isClosed()) {
			if (log != null) log.debug("redis-cache", "validateObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ") " + address);
			return false;
		}
		if (log != null) log.debug("redis-cache", "validateObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ") " + address);

		return true;
	}

	@Override
	public void passivateObject(PooledObject<Redis> p) throws Exception {

		if (log != null) log.debug("redis-cache", "passivateObject");
		p.getObject().lastUsed = System.currentTimeMillis();
		super.passivateObject(p);
	}

	@Override
	public void destroyObject(PooledObject<Redis> p) throws Exception {
		Socket socket = p.getObject().getSocket();
		if (socket != null) {
			if (log != null) log.debug("redis-cache", "destroyObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ") " + address);
			socket.close();
		}
	}
}
