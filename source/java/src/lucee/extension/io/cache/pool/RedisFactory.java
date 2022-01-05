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

	public RedisFactory(ClassLoader cl, String host, int port, String username, String password, boolean ssl, int socketTimeout, long idleTimeout, long liveTimeout,
			int databaseIndex, Log log) {
		this.cl = cl;
		this.username = Util.isEmpty(username) ? null : username;
		this.password = Util.isEmpty(password) ? null : password;
		this.host = host;
		this.port = port;
		this.ssl = ssl;

		this.socketTimeout = socketTimeout;
		this.idleTimeout = idleTimeout;
		this.liveTimeout = liveTimeout;
		this.log = log;
		this.databaseIndex = databaseIndex;
	}

	@Override
	public Redis create() throws IOException {
		if (log != null) log.debug("redis-cache", "create connection to " + host + ":" + port);
		Socket socket = getSocket();

		InetSocketAddress serverInfo = new InetSocketAddress(host, port);
		if (socketTimeout > 0) socket.connect(serverInfo, socketTimeout);
		else socket.connect(serverInfo);
		Redis redis = new Redis(cl, socket);

		if (password != null) {
			if (username != null) redis.call("AUTH", username, password);
			else redis.call("AUTH", password);
		}
		if (databaseIndex > -1) {
			redis.call("SELECT", String.valueOf(databaseIndex));
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
			if (log != null) log.debug("redis-cache", "validateObject(reached live timeout:" + liveTimeout + ") " + host + ":" + port);
			return false;
		}
		if (idleTimeout > 0 && redis.lastUsed + idleTimeout < now) {
			if (log != null) log.debug("redis-cache", "validateObject(reached idle timeout:" + idleTimeout + ") " + host + ":" + port);
			return false;
		}

		// check socket
		Socket socket = redis.getSocket();
		if (socket == null) {
			if (log != null) log.debug("redis-cache", "validateObject(socket null) " + host + ":" + port);
			return false;
		}

		if (!socket.isConnected()) {
			if (log != null) log.debug("redis-cache", "validateObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ") " + host + ":" + port);
			return false;
		}
		if (socket.isClosed()) {
			if (log != null) log.debug("redis-cache", "validateObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ") " + host + ":" + port);
			return false;
		}
		if (log != null) log.debug("redis-cache", "validateObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ") " + host + ":" + port);

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
			if (log != null) log.debug("redis-cache", "destroyObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ") " + host + ":" + port);
			socket.close();
		}
	}
}
