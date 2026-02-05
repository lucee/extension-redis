package lucee.extension.io.cache.redis.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker implementation for Redis connections.
 * Prevents cascade failures by temporarily stopping requests when failures exceed threshold.
 *
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Circuit is open, requests fail fast without trying Redis
 * - HALF_OPEN: Testing if service recovered, allows limited requests
 */
public class CircuitBreaker {

	public enum State {
		CLOSED, OPEN, HALF_OPEN
	}

	private volatile State state = State.CLOSED;
	private final AtomicInteger failureCount = new AtomicInteger(0);
	private final AtomicInteger successCount = new AtomicInteger(0);
	private final AtomicLong lastFailureTime = new AtomicLong(0);
	private final AtomicLong stateChangedTime = new AtomicLong(System.currentTimeMillis());

	// Configuration
	private final int failureThreshold;
	private final long resetTimeoutMs;
	private final int halfOpenMaxAttempts;

	/**
	 * Create a circuit breaker with default settings.
	 * - 5 failures to open circuit
	 * - 30 second reset timeout
	 * - 3 successful requests to close from half-open
	 */
	public CircuitBreaker() {
		this(5, 30000, 3);
	}

	/**
	 * Create a circuit breaker with custom settings.
	 *
	 * @param failureThreshold Number of failures before opening circuit
	 * @param resetTimeoutMs Time in ms before trying to close circuit
	 * @param halfOpenMaxAttempts Successful attempts needed to close from half-open
	 */
	public CircuitBreaker(int failureThreshold, long resetTimeoutMs, int halfOpenMaxAttempts) {
		this.failureThreshold = failureThreshold;
		this.resetTimeoutMs = resetTimeoutMs;
		this.halfOpenMaxAttempts = halfOpenMaxAttempts;
	}

	/**
	 * Check if request should be allowed through.
	 *
	 * @return true if request can proceed, false if circuit is open
	 */
	public boolean allowRequest() {
		State currentState = getState();

		switch (currentState) {
			case CLOSED:
				return true;

			case OPEN:
				// Check if reset timeout has passed
				if (System.currentTimeMillis() - stateChangedTime.get() >= resetTimeoutMs) {
					transitionTo(State.HALF_OPEN);
					return true;
				}
				return false;

			case HALF_OPEN:
				// Allow limited requests in half-open state
				return true;

			default:
				return true;
		}
	}

	/**
	 * Record a successful operation.
	 */
	public void recordSuccess() {
		State currentState = getState();

		if (currentState == State.HALF_OPEN) {
			int successes = successCount.incrementAndGet();
			if (successes >= halfOpenMaxAttempts) {
				transitionTo(State.CLOSED);
			}
		}
		else if (currentState == State.CLOSED) {
			// Reset failure count on success
			failureCount.set(0);
		}
	}

	/**
	 * Record a failed operation.
	 */
	public void recordFailure() {
		lastFailureTime.set(System.currentTimeMillis());
		State currentState = getState();

		if (currentState == State.HALF_OPEN) {
			// Any failure in half-open goes back to open
			transitionTo(State.OPEN);
		}
		else if (currentState == State.CLOSED) {
			int failures = failureCount.incrementAndGet();
			if (failures >= failureThreshold) {
				transitionTo(State.OPEN);
			}
		}
	}

	/**
	 * Get current circuit state.
	 */
	public State getState() {
		return state;
	}

	/**
	 * Check if circuit is open (failing fast).
	 */
	public boolean isOpen() {
		return getState() == State.OPEN && !allowRequest();
	}

	/**
	 * Force the circuit to close (for testing or manual recovery).
	 */
	public void reset() {
		transitionTo(State.CLOSED);
	}

	/**
	 * Get failure count since last reset.
	 */
	public int getFailureCount() {
		return failureCount.get();
	}

	/**
	 * Get time since last state change.
	 */
	public long getTimeSinceStateChange() {
		return System.currentTimeMillis() - stateChangedTime.get();
	}

	private synchronized void transitionTo(State newState) {
		if (state != newState) {
			state = newState;
			stateChangedTime.set(System.currentTimeMillis());
			failureCount.set(0);
			successCount.set(0);
		}
	}

	@Override
	public String toString() {
		return "CircuitBreaker[state=" + state + ", failures=" + failureCount.get() + ", threshold=" + failureThreshold + "]";
	}
}
