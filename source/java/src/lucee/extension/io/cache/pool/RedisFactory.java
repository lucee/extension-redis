package lucee.extension.io.cache.pool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import lucee.extension.io.cache.redis.Redis;
import lucee.loader.util.Util;

public class RedisFactory extends BasePooledObjectFactory<Redis> {
	private final ClassLoader cl;
	private final InetSocketAddress serverInfo;
	private final boolean debug;
	private final String username;
	private final String password;
	private final int databaseIndex;
	private final int socketTimeout;
	private final long idleTimeout;
	private final long liveTimeout;

	public RedisFactory(ClassLoader cl, String host, int port, String username, String password, int socketTimeout, long idleTimeout, long liveTimeout, int databaseIndex,
			boolean debug) {
		this.cl = cl;
		this.username = Util.isEmpty(username) ? null : username;
		this.password = Util.isEmpty(password) ? null : password;
		serverInfo = new InetSocketAddress(host, port);
		this.socketTimeout = socketTimeout;
		this.idleTimeout = idleTimeout;
		this.liveTimeout = liveTimeout;
		this.debug = debug;
		this.databaseIndex = databaseIndex;

	}

	@Override
	public Redis create() throws IOException {
		if (debug) System.out.println(">>>>> SocketFactory.create...");
		Socket socket = new Socket();
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
			if (debug) System.out.println(">>>>> validateObject(reached live timeout:" + liveTimeout + ")");
			return false;
		}
		if (idleTimeout > 0 && redis.lastUsed + idleTimeout < now) {
			if (debug) System.out.println(">>>>> validateObject(reached idle timeout:" + idleTimeout + ")");
			return false;
		}

		// check socket
		Socket socket = redis.getSocket();
		if (socket == null) {
			if (debug) System.out.println(">>>>> validateObject(socket null)");
			return false;
		}

		if (!socket.isConnected()) {
			if (debug) System.out.println(">>>>> validateObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ")");
			return false;
		}
		if (socket.isClosed()) {
			if (debug) System.out.println(">>>>> validateObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ")");
			return false;
		}
		if (debug) System.out.println(">>>>> validateObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ")");

		return true;
	}

	@Override
	public void passivateObject(PooledObject<Redis> p) throws Exception {
		if (debug) {
			System.out.println(">>>>> passivateObject");
		}
		p.getObject().lastUsed = System.currentTimeMillis();
		super.passivateObject(p);
	}

	@Override
	public void destroyObject(PooledObject<Redis> p) throws Exception {
		Socket socket = p.getObject().getSocket();
		if (socket != null) {
			if (debug) {
				System.out.println(">>>>> destroyObject(closed:" + socket.isClosed() + ";conn:" + socket.isConnected() + ")");
			}
			socket.close();
		}
	}
}
