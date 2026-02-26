package lucee.extension.io.cache.redis.resilience;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides timeout enforcement for Redis operations.
 * Ensures that no operation can hang indefinitely.
 */
public class OperationTimeout {

	private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

	private final ExecutorService executor;
	private final long defaultTimeoutMs;
	private final boolean ownExecutor;

	/**
	 * Create with default timeout of 30 seconds.
	 */
	public OperationTimeout() {
		this(30000);
	}

	/**
	 * Create with specified default timeout.
	 *
	 * @param defaultTimeoutMs Default timeout in milliseconds
	 */
	public OperationTimeout(long defaultTimeoutMs) {
		this.defaultTimeoutMs = defaultTimeoutMs;
		this.executor = Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "redis-timeout-" + THREAD_COUNTER.incrementAndGet());
			t.setDaemon(true);
			return t;
		});
		this.ownExecutor = true;
	}

	/**
	 * Create with external executor.
	 *
	 * @param executor Executor to use
	 * @param defaultTimeoutMs Default timeout in milliseconds
	 */
	public OperationTimeout(ExecutorService executor, long defaultTimeoutMs) {
		this.executor = executor;
		this.defaultTimeoutMs = defaultTimeoutMs;
		this.ownExecutor = false;
	}

	/**
	 * Execute an operation with the default timeout.
	 *
	 * @param operation The operation to execute
	 * @param <T> Return type
	 * @return Result of the operation
	 * @throws IOException If operation fails or times out
	 */
	public <T> T execute(Callable<T> operation) throws IOException {
		return execute(operation, defaultTimeoutMs);
	}

	/**
	 * Execute an operation with a specific timeout.
	 *
	 * @param operation The operation to execute
	 * @param timeoutMs Timeout in milliseconds
	 * @param <T> Return type
	 * @return Result of the operation
	 * @throws IOException If operation fails or times out
	 */
	public <T> T execute(Callable<T> operation, long timeoutMs) throws IOException {
		return execute(operation, timeoutMs, null);
	}

	/**
	 * Execute an operation with a specific timeout and operation name for logging.
	 *
	 * @param operation The operation to execute
	 * @param timeoutMs Timeout in milliseconds
	 * @param operationName Name for error messages
	 * @param <T> Return type
	 * @return Result of the operation
	 * @throws IOException If operation fails or times out
	 */
	public <T> T execute(Callable<T> operation, long timeoutMs, String operationName) throws IOException {
		Future<T> future = executor.submit(operation);

		try {
			return future.get(timeoutMs, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException e) {
			future.cancel(true);
			String msg = "Redis operation timed out after " + timeoutMs + "ms";
			if (operationName != null) {
				msg += ": " + operationName;
			}
			throw new IOException(msg, e);
		}
		catch (InterruptedException e) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw new IOException("Redis operation interrupted", e);
		}
		catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof IOException) {
				throw (IOException) cause;
			}
			throw new IOException("Redis operation failed", cause != null ? cause : e);
		}
	}

	/**
	 * Execute a void operation with the default timeout.
	 *
	 * @param operation The operation to execute
	 * @throws IOException If operation fails or times out
	 */
	public void executeVoid(VoidCallable operation) throws IOException {
		executeVoid(operation, defaultTimeoutMs, null);
	}

	/**
	 * Execute a void operation with a specific timeout.
	 *
	 * @param operation The operation to execute
	 * @param timeoutMs Timeout in milliseconds
	 * @param operationName Name for error messages
	 * @throws IOException If operation fails or times out
	 */
	public void executeVoid(VoidCallable operation, long timeoutMs, String operationName) throws IOException {
		execute(() -> {
			operation.call();
			return null;
		}, timeoutMs, operationName);
	}

	/**
	 * Get the default timeout.
	 */
	public long getDefaultTimeoutMs() {
		return defaultTimeoutMs;
	}

	/**
	 * Shutdown the executor if we own it.
	 */
	public void shutdown() {
		if (ownExecutor) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			}
			catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Functional interface for void operations.
	 */
	@FunctionalInterface
	public interface VoidCallable {
		void call() throws Exception;
	}
}
