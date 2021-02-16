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
	private int socketTimeout;

	public RedisFactory(ClassLoader cl, String host, int port, String username, String password, int socketTimeout, int databaseIndex, boolean debug) {
		this.cl = cl;
		this.username = Util.isEmpty(username) ? null : username;
		this.password = Util.isEmpty(password) ? null : password;
		serverInfo = new InetSocketAddress(host, port);
		this.socketTimeout = socketTimeout;
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
		if (debug) System.out.println(">>>>> SocketFactory.wrap...");
		return new DefaultPooledObject<Redis>(redis);
	}

	@Override
	public void passivateObject(PooledObject<Redis> pooledObject) {
		if (debug) System.out.println(">>>>> SocketFactory.passivateObject...");
	}

	@Override
	public PooledObject<Redis> makeObject() throws Exception {
		if (debug) System.out.println(">>>>> SocketFactory.makeObject...");
		return super.makeObject();
	}

	@Override
	public void destroyObject(PooledObject<Redis> p) throws Exception {
		Socket socket = p.getObject().getSocket();
		if (socket != null) {
			if (debug) {
				System.out.println(">>>>> SocketFactory.destroyObject...");
				System.out.println(">>>>> A isClosed: " + socket.isClosed());
				System.out.println(">>>>> A isConnected: " + socket.isConnected());
			}
			socket.close();
		}
	}

	@Override
	public boolean validateObject(PooledObject<Redis> p) {
		if (debug) System.out.println(">>>>> SocketFactory.validateObject...");
		Socket socket = p.getObject().getSocket();
		if (socket == null) return false;

		if (!socket.isConnected()) {
			if (debug) System.out.println(">>>>> A isConnected: false");
			return false;
		}
		if (socket.isClosed()) {
			if (debug) System.out.println(">>>>> A isClosed: true");
			return false;
		}
		if (debug) {
			System.out.println(">>>>> A isConnected: true");
			System.out.println(">>>>> A isClosed: false");
		}
		return true;
	}

	@Override
	public void activateObject(PooledObject<Redis> p) throws Exception {
		if (debug) System.out.println(">>>>> SocketFactory.activateObject...");
		super.activateObject(p);
	}
}
