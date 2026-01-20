package lucee.extension.io.cache.redis.resilience;

import java.io.IOException;
import java.util.concurrent.Callable;

import lucee.commons.io.log.Log;

/**
 * Combines circuit breaker, retry policy, and timeout enforcement for resilient Redis operations.
 * This class orchestrates all resilience features in a single, easy-to-use wrapper.
 */
public class ResilientOperation {

	private final CircuitBreaker circuitBreaker;
	private final RetryPolicy retryPolicy;
	private final OperationTimeout operationTimeout;
	private final Log log;

	/**
	 * Create a resilient operation handler with all features.
	 *
	 * @param circuitBreaker Optional circuit breaker (can be null to disable)
	 * @param retryPolicy Optional retry policy (can be null to disable)
	 * @param operationTimeout Optional timeout handler (can be null to disable)
	 * @param log Optional logger for debug output
	 */
	public ResilientOperation(CircuitBreaker circuitBreaker, RetryPolicy retryPolicy, OperationTimeout operationTimeout, Log log) {
		this.circuitBreaker = circuitBreaker;
		this.retryPolicy = retryPolicy;
		this.operationTimeout = operationTimeout;
		this.log = log;
	}

	/**
	 * Create from a ResilienceConfig.
	 */
	public ResilientOperation(ResilienceConfig config, Log log) {
		this.circuitBreaker = config.createCircuitBreaker();
		this.retryPolicy = config.createRetryPolicy(this.circuitBreaker);
		this.operationTimeout = config.createOperationTimeout();
		this.log = log;
	}

	/**
	 * Execute an operation with all configured resilience features.
	 * Order of wrapping: timeout -> retry -> circuit breaker
	 *
	 * @param operation The operation to execute
	 * @param operationName Name for logging (optional)
	 * @param <T> Return type
	 * @return Result of the operation
	 * @throws IOException If operation fails after all resilience measures
	 */
	public <T> T execute(Callable<T> operation, String operationName) throws IOException {
		// Check circuit breaker first (fast fail)
		if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
			if (log != null) {
				log.warn("redis-cache", "Circuit breaker OPEN, failing fast for: " + (operationName != null ? operationName : "operation"));
			}
			throw new IOException("Circuit breaker is open - Redis operations temporarily disabled" + (operationName != null ? " for: " + operationName : ""));
		}

		try {
			T result;

			// If we have retry policy, use it (which may also include timeout per attempt)
			if (retryPolicy != null) {
				result = retryPolicy.execute(() -> executeWithTimeout(operation, operationName), operationName);
			}
			else {
				// No retry, just execute with optional timeout
				result = executeWithTimeout(operation, operationName);
			}

			// Record success
			if (circuitBreaker != null) {
				circuitBreaker.recordSuccess();
			}

			return result;
		}
		catch (IOException e) {
			// Record failure (if not already recorded by retry policy)
			if (circuitBreaker != null && retryPolicy == null) {
				circuitBreaker.recordFailure();
			}
			throw e;
		}
	}

	/**
	 * Execute an operation with just timeout enforcement.
	 */
	private <T> T executeWithTimeout(Callable<T> operation, String operationName) throws IOException {
		if (operationTimeout != null) {
			return operationTimeout.execute(operation, operationTimeout.getDefaultTimeoutMs(), operationName);
		}
		else {
			try {
				return operation.call();
			}
			catch (IOException e) {
				throw e;
			}
			catch (Exception e) {
				throw new IOException(e.getMessage(), e);
			}
		}
	}

	/**
	 * Execute a void operation with all resilience features.
	 *
	 * @param operation The operation to execute
	 * @param operationName Name for logging (optional)
	 * @throws IOException If operation fails
	 */
	public void executeVoid(VoidCallable operation, String operationName) throws IOException {
		execute(() -> {
			operation.call();
			return null;
		}, operationName);
	}

	/**
	 * Execute without retry (single attempt with timeout and circuit breaker).
	 * Useful for operations that shouldn't be retried.
	 *
	 * @param operation The operation to execute
	 * @param operationName Name for logging
	 * @param <T> Return type
	 * @return Result of the operation
	 * @throws IOException If operation fails
	 */
	public <T> T executeOnce(Callable<T> operation, String operationName) throws IOException {
		// Check circuit breaker first
		if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
			throw new IOException("Circuit breaker is open" + (operationName != null ? " for: " + operationName : ""));
		}

		try {
			T result = executeWithTimeout(operation, operationName);
			if (circuitBreaker != null) {
				circuitBreaker.recordSuccess();
			}
			return result;
		}
		catch (IOException e) {
			if (circuitBreaker != null) {
				circuitBreaker.recordFailure();
			}
			throw e;
		}
	}

	/**
	 * Get the circuit breaker state.
	 */
	public CircuitBreaker.State getCircuitBreakerState() {
		return circuitBreaker != null ? circuitBreaker.getState() : null;
	}

	/**
	 * Check if operations are currently allowed (circuit breaker not open).
	 */
	public boolean isOperationAllowed() {
		return circuitBreaker == null || circuitBreaker.allowRequest();
	}

	/**
	 * Reset the circuit breaker (for manual recovery).
	 */
	public void resetCircuitBreaker() {
		if (circuitBreaker != null) {
			circuitBreaker.reset();
		}
	}

	/**
	 * Get the failure count from circuit breaker.
	 */
	public int getFailureCount() {
		return circuitBreaker != null ? circuitBreaker.getFailureCount() : 0;
	}

	/**
	 * Shutdown resources (call when cache is being destroyed).
	 */
	public void shutdown() {
		if (operationTimeout != null) {
			operationTimeout.shutdown();
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
