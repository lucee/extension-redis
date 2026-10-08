package lucee.extension.io.cache.pool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;

/**
 * A {@link Socket} backed by a Unix domain socket channel (Java 16+), so the Redis protocol code
 * and the pool can treat it like a regular TCP socket.
 * <p>
 * {@code SocketChannel.socket()} is not supported for Unix domain sockets, so this class adapts
 * the channel to the {@link Socket} methods the extension uses (streams, isConnected, isClosed,
 * close).
 * <p>
 * The extension is compiled for Java 11, so the Java 16+ API ({@code UnixDomainSocketAddress},
 * {@code StandardProtocolFamily.UNIX}, {@code SocketChannel.open(ProtocolFamily)}) is accessed via
 * reflection.
 */
public final class UnixDomainSocket extends Socket {

	private final String path;
	private final SocketChannel channel;
	private InputStream in;
	private OutputStream out;

	private UnixDomainSocket(String path, SocketChannel channel) {
		this.path = path;
		this.channel = channel;
	}

	/**
	 * Opens a connection to the Unix domain socket at the given path.
	 *
	 * @param path absolute path to the socket file, e.g. /var/run/redis/redis.sock
	 * @return the connected socket
	 * @throws IOException if the socket cannot be connected or the JVM does not support Unix domain
	 *             sockets (Java 16+ required)
	 */
	public static UnixDomainSocket connect(String path) throws IOException {
		SocketAddress address;
		ProtocolFamily unix;
		Method open;
		try {
			unix = StandardProtocolFamily.valueOf("UNIX");
			address = (SocketAddress) Class.forName("java.net.UnixDomainSocketAddress").getMethod("of", String.class).invoke(null, path);
			open = SocketChannel.class.getMethod("open", ProtocolFamily.class);
		}
		catch (InvocationTargetException e) {
			// e.g. InvalidPathException
			throw new IOException("Invalid Redis socket path [" + path + "]", e.getCause());
		}
		catch (Exception e) {
			throw new IOException("Connecting to Redis via a Unix domain socket requires Java 16 or newer (running on Java " + System.getProperty("java.version") + ")", e);
		}

		SocketChannel channel;
		try {
			channel = (SocketChannel) open.invoke(null, unix);
		}
		catch (InvocationTargetException e) {
			Throwable t = e.getCause();
			if (t instanceof IOException) throw (IOException) t;
			throw new IOException(t);
		}
		catch (IllegalAccessException e) {
			throw new IOException(e);
		}

		try {
			channel.connect(address);
		}
		catch (IOException | RuntimeException e) {
			try {
				channel.close();
			}
			catch (IOException ioe) {
				// ignore
			}
			throw e;
		}
		return new UnixDomainSocket(path, channel);
	}

	public String getPath() {
		return path;
	}

	@Override
	public synchronized InputStream getInputStream() throws IOException {
		if (!channel.isOpen()) throw new IOException("Socket is closed");
		if (in == null) in = Channels.newInputStream(channel);
		return in;
	}

	@Override
	public synchronized OutputStream getOutputStream() throws IOException {
		if (!channel.isOpen()) throw new IOException("Socket is closed");
		if (out == null) out = Channels.newOutputStream(channel);
		return out;
	}

	@Override
	public boolean isConnected() {
		return channel.isConnected();
	}

	@Override
	public boolean isClosed() {
		return !channel.isOpen();
	}

	@Override
	public boolean isBound() {
		return channel.isOpen();
	}

	@Override
	public boolean isInputShutdown() {
		return !channel.isOpen();
	}

	@Override
	public boolean isOutputShutdown() {
		return !channel.isOpen();
	}

	@Override
	public void shutdownInput() throws IOException {
		channel.shutdownInput();
	}

	@Override
	public void shutdownOutput() throws IOException {
		channel.shutdownOutput();
	}

	@Override
	public synchronized void close() throws IOException {
		channel.close();
	}

	@Override
	public String toString() {
		return "UnixDomainSocket[path=" + path + ",connected=" + isConnected() + "]";
	}
}
