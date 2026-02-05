package lucee.extension.io.cache.redis.resilience;

/**
 * Configuration for resilience features in Redis cache.
 * Provides unified settings for circuit breaker, retry policy, and timeouts.
 */
public class ResilienceConfig {

	// Circuit breaker defaults
	private int circuitBreakerFailureThreshold = 5;
	private long circuitBreakerResetTimeoutMs = 30000;
	private int circuitBreakerHalfOpenAttempts = 3;
	private boolean circuitBreakerEnabled = true;

	// Retry policy defaults
	private int maxRetries = 3;
	private long retryInitialDelayMs = 100;
	private long retryMaxDelayMs = 5000;
	private double retryBackoffMultiplier = 2.0;
	private boolean retryEnabled = true;

	// Operation timeout defaults
	private long defaultOperationTimeoutMs = 30000;
	private long connectionTimeoutMs = 5000;
	private boolean timeoutEnabled = true;

	public ResilienceConfig() {
	}

	// Circuit Breaker getters/setters

	public int getCircuitBreakerFailureThreshold() {
		return circuitBreakerFailureThreshold;
	}

	public ResilienceConfig setCircuitBreakerFailureThreshold(int threshold) {
		this.circuitBreakerFailureThreshold = threshold;
		return this;
	}

	public long getCircuitBreakerResetTimeoutMs() {
		return circuitBreakerResetTimeoutMs;
	}

	public ResilienceConfig setCircuitBreakerResetTimeoutMs(long timeoutMs) {
		this.circuitBreakerResetTimeoutMs = timeoutMs;
		return this;
	}

	public int getCircuitBreakerHalfOpenAttempts() {
		return circuitBreakerHalfOpenAttempts;
	}

	public ResilienceConfig setCircuitBreakerHalfOpenAttempts(int attempts) {
		this.circuitBreakerHalfOpenAttempts = attempts;
		return this;
	}

	public boolean isCircuitBreakerEnabled() {
		return circuitBreakerEnabled;
	}

	public ResilienceConfig setCircuitBreakerEnabled(boolean enabled) {
		this.circuitBreakerEnabled = enabled;
		return this;
	}

	// Retry Policy getters/setters

	public int getMaxRetries() {
		return maxRetries;
	}

	public ResilienceConfig setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
		return this;
	}

	public long getRetryInitialDelayMs() {
		return retryInitialDelayMs;
	}

	public ResilienceConfig setRetryInitialDelayMs(long delayMs) {
		this.retryInitialDelayMs = delayMs;
		return this;
	}

	public long getRetryMaxDelayMs() {
		return retryMaxDelayMs;
	}

	public ResilienceConfig setRetryMaxDelayMs(long delayMs) {
		this.retryMaxDelayMs = delayMs;
		return this;
	}

	public double getRetryBackoffMultiplier() {
		return retryBackoffMultiplier;
	}

	public ResilienceConfig setRetryBackoffMultiplier(double multiplier) {
		this.retryBackoffMultiplier = multiplier;
		return this;
	}

	public boolean isRetryEnabled() {
		return retryEnabled;
	}

	public ResilienceConfig setRetryEnabled(boolean enabled) {
		this.retryEnabled = enabled;
		return this;
	}

	// Timeout getters/setters

	public long getDefaultOperationTimeoutMs() {
		return defaultOperationTimeoutMs;
	}

	public ResilienceConfig setDefaultOperationTimeoutMs(long timeoutMs) {
		this.defaultOperationTimeoutMs = timeoutMs;
		return this;
	}

	public long getConnectionTimeoutMs() {
		return connectionTimeoutMs;
	}

	public ResilienceConfig setConnectionTimeoutMs(long timeoutMs) {
		this.connectionTimeoutMs = timeoutMs;
		return this;
	}

	public boolean isTimeoutEnabled() {
		return timeoutEnabled;
	}

	public ResilienceConfig setTimeoutEnabled(boolean enabled) {
		this.timeoutEnabled = enabled;
		return this;
	}

	/**
	 * Create a CircuitBreaker from this configuration.
	 */
	public CircuitBreaker createCircuitBreaker() {
		if (!circuitBreakerEnabled) {
			return null;
		}
		return new CircuitBreaker(circuitBreakerFailureThreshold, circuitBreakerResetTimeoutMs, circuitBreakerHalfOpenAttempts);
	}

	/**
	 * Create a RetryPolicy from this configuration.
	 */
	public RetryPolicy createRetryPolicy(CircuitBreaker circuitBreaker) {
		if (!retryEnabled) {
			return null;
		}
		return new RetryPolicy(maxRetries, retryInitialDelayMs, retryMaxDelayMs, retryBackoffMultiplier, circuitBreaker);
	}

	/**
	 * Create an OperationTimeout from this configuration.
	 */
	public OperationTimeout createOperationTimeout() {
		if (!timeoutEnabled) {
			return null;
		}
		return new OperationTimeout(defaultOperationTimeoutMs);
	}

	/**
	 * Create a default configuration suitable for production use.
	 */
	public static ResilienceConfig defaultConfig() {
		return new ResilienceConfig();
	}

	/**
	 * Create a configuration with resilience features disabled (for testing or simple deployments).
	 */
	public static ResilienceConfig disabled() {
		return new ResilienceConfig().setCircuitBreakerEnabled(false).setRetryEnabled(false).setTimeoutEnabled(false);
	}
}
