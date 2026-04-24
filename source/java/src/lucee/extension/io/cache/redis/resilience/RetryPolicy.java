package lucee.extension.io.cache.redis.resilience;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;

/**
 * Retry policy with exponential backoff for Redis operations.
 * Provides resilient execution of operations that may fail transiently.
 */
public class RetryPolicy {

	private final int maxRetries;
	private final long initialDelayMs;
	private final long maxDelayMs;
	private final double backoffMultiplier;
	private final CircuitBreaker circuitBreaker;

	/**
	 * Create a retry policy with default settings.
	 * - 3 max retries
	 * - 100ms initial delay
	 * - 5 second max delay
	 * - 2x backoff multiplier
	 */
	public RetryPolicy() {
		this(3, 100, 5000, 2.0, null);
	}

	/**
	 * Create a retry policy with circuit breaker.
	 */
	public RetryPolicy(CircuitBreaker circuitBreaker) {
		this(3, 100, 5000, 2.0, circuitBreaker);
	}

	/**
	 * Create a retry policy with custom settings.
	 *
	 * @param maxRetries Maximum number of retry attempts
	 * @param initialDelayMs Initial delay before first retry
	 * @param maxDelayMs Maximum delay between retries
	 * @param backoffMultiplier Multiplier for exponential backoff
	 * @param circuitBreaker Optional circuit breaker to integrate with
	 */
	public RetryPolicy(int maxRetries, long initialDelayMs, long maxDelayMs, double backoffMultiplier, CircuitBreaker circuitBreaker) {
		this.maxRetries = maxRetries;
		this.initialDelayMs = initialDelayMs;
		this.maxDelayMs = maxDelayMs;
		this.backoffMultiplier = backoffMultiplier;
		this.circuitBreaker = circuitBreaker;
	}

	/**
	 * Execute an operation with retry logic.
	 *
	 * @param operation The operation to execute
	 * @param <T> Return type
	 * @return Result of the operation
	 * @throws IOException If all retries fail
	 */
	public <T> T execute(Callable<T> operation) throws IOException {
		return execute(operation, null);
	}

	/**
	 * Execute an operation with retry logic and logging.
	 *
	 * @param operation The operation to execute
	 * @param operationName Name for logging purposes
	 * @param <T> Return type
	 * @return Result of the operation
	 * @throws IOException If all retries fail
	 */
	public <T> T execute(Callable<T> operation, String operationName) throws IOException {
		// Check circuit breaker first
		if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
			throw new IOException("Circuit breaker is open - Redis operations temporarily disabled" + (operationName != null ? " for: " + operationName : ""));
		}

		IOException lastException = null;
		int attempt = 0;

		while (attempt <= maxRetries) {
			try {
				T result = operation.call();

				// Record success with circuit breaker
				if (circuitBreaker != null) {
					circuitBreaker.recordSuccess();
				}

				return result;
			}
			catch (Exception e) {
				lastException = wrapException(e);

				// Check if this exception is retryable
				if (!isRetryable(e)) {
					if (circuitBreaker != null) {
						circuitBreaker.recordFailure();
					}
					throw lastException;
				}

				attempt++;

				if (attempt > maxRetries) {
					// Max retries exceeded
					if (circuitBreaker != null) {
						circuitBreaker.recordFailure();
					}
					break;
				}

				// Calculate delay with exponential backoff and jitter
				long delay = calculateDelay(attempt);

				try {
					Thread.sleep(delay);
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new IOException("Retry interrupted", ie);
				}
			}
		}

		throw new IOException("Operation failed after " + maxRetries + " retries" + (operationName != null ? ": " + operationName : ""), lastException);
	}

	/**
	 * Execute a void operation with retry logic.
	 *
	 * @param operation The operation to execute
	 * @throws IOException If all retries fail
	 */
	public void executeVoid(VoidCallable operation) throws IOException {
		executeVoid(operation, null);
	}

	/**
	 * Execute a void operation with retry logic and logging.
	 *
	 * @param operation The operation to execute
	 * @param operationName Name for logging purposes
	 * @throws IOException If all retries fail
	 */
	public void executeVoid(VoidCallable operation, String operationName) throws IOException {
		execute(() -> {
			operation.call();
			return null;
		}, operationName);
	}

	/**
	 * Check if an exception is retryable.
	 */
	public boolean isRetryable(Throwable e) {
		if (e == null) return false;

		// Network-related exceptions are retryable
		if (e instanceof SocketException) return true;
		if (e instanceof SocketTimeoutException) return true;

		// Check for "connection reset" type messages
		String message = e.getMessage();
		if (message != null) {
			message = message.toLowerCase();
			if (message.contains("connection reset") || message.contains("broken pipe") || message.contains("connection refused")
					|| message.contains("connection timed out") || message.contains("no route to host") || message.contains("network is unreachable")) {
				return true;
			}
		}

		// Check cause
		if (e.getCause() != null && e.getCause() != e) {
			return isRetryable(e.getCause());
		}

		return false;
	}

	/**
	 * Calculate delay for retry attempt using exponential backoff with jitter.
	 */
	private long calculateDelay(int attempt) {
		// Exponential backoff: initialDelay * multiplier^(attempt-1)
		double delay = initialDelayMs * Math.pow(backoffMultiplier, attempt - 1);

		// Cap at max delay
		delay = Math.min(delay, maxDelayMs);

		// Add jitter (±25%)
		double jitter = delay * 0.25 * (Math.random() * 2 - 1);
		delay = delay + jitter;

		return Math.max(1, (long) delay);
	}

	/**
	 * Wrap an exception as IOException.
	 */
	private IOException wrapException(Exception e) {
		if (e instanceof IOException) {
			return (IOException) e;
		}
		return new IOException(e.getMessage(), e);
	}

	/**
	 * Functional interface for void operations.
	 */
	@FunctionalInterface
	public interface VoidCallable {
		void call() throws Exception;
	}

	/**
	 * Builder for creating RetryPolicy instances.
	 */
	public static class Builder {
		private int maxRetries = 3;
		private long initialDelayMs = 100;
		private long maxDelayMs = 5000;
		private double backoffMultiplier = 2.0;
		private CircuitBreaker circuitBreaker = null;

		public Builder maxRetries(int maxRetries) {
			this.maxRetries = maxRetries;
			return this;
		}

		public Builder initialDelay(long delayMs) {
			this.initialDelayMs = delayMs;
			return this;
		}

		public Builder maxDelay(long delayMs) {
			this.maxDelayMs = delayMs;
			return this;
		}

		public Builder backoffMultiplier(double multiplier) {
			this.backoffMultiplier = multiplier;
			return this;
		}

		public Builder circuitBreaker(CircuitBreaker circuitBreaker) {
			this.circuitBreaker = circuitBreaker;
			return this;
		}

		public RetryPolicy build() {
			return new RetryPolicy(maxRetries, initialDelayMs, maxDelayMs, backoffMultiplier, circuitBreaker);
		}
	}

	public static Builder builder() {
		return new Builder();
	}
}
