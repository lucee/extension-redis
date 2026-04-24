package lucee.extension.io.cache.redis.resilience;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for CircuitBreaker.
 */
public class CircuitBreakerTest {

	private CircuitBreaker breaker;

	@Before
	public void setUp() {
		// Create circuit breaker with low threshold for testing
		// 3 failures to open, 100ms reset timeout, 2 successes to close
		breaker = new CircuitBreaker(3, 100, 2);
	}

	@Test
	public void testInitialState() {
		assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
		assertTrue(breaker.allowRequest());
		assertEquals(0, breaker.getFailureCount());
	}

	@Test
	public void testSuccessDoesNotOpenCircuit() {
		breaker.recordSuccess();
		breaker.recordSuccess();
		breaker.recordSuccess();

		assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
		assertTrue(breaker.allowRequest());
	}

	@Test
	public void testFailuresOpenCircuit() {
		// Record failures up to threshold
		breaker.recordFailure();
		assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
		assertEquals(1, breaker.getFailureCount());

		breaker.recordFailure();
		assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
		assertEquals(2, breaker.getFailureCount());

		// Third failure should open the circuit
		breaker.recordFailure();
		assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
		assertFalse(breaker.allowRequest());
	}

	@Test
	public void testSuccessResetsFailureCount() {
		breaker.recordFailure();
		breaker.recordFailure();
		assertEquals(2, breaker.getFailureCount());

		// Success should reset the count
		breaker.recordSuccess();
		assertEquals(0, breaker.getFailureCount());

		// Now failures start over
		breaker.recordFailure();
		assertEquals(1, breaker.getFailureCount());
	}

	@Test
	public void testCircuitOpensAfterReset() throws InterruptedException {
		// Open the circuit
		breaker.recordFailure();
		breaker.recordFailure();
		breaker.recordFailure();
		assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
		assertFalse(breaker.allowRequest());

		// Wait for reset timeout
		Thread.sleep(150);

		// Now allowRequest should transition to HALF_OPEN
		assertTrue(breaker.allowRequest());
		assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());
	}

	@Test
	public void testHalfOpenToClosedOnSuccess() throws InterruptedException {
		// Open the circuit
		breaker.recordFailure();
		breaker.recordFailure();
		breaker.recordFailure();

		// Wait for reset timeout
		Thread.sleep(150);
		assertTrue(breaker.allowRequest()); // Transition to HALF_OPEN

		// Record successes to close
		breaker.recordSuccess();
		assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState()); // Still half-open, need 2 successes

		breaker.recordSuccess();
		assertEquals(CircuitBreaker.State.CLOSED, breaker.getState()); // Now closed
	}

	@Test
	public void testHalfOpenToOpenOnFailure() throws InterruptedException {
		// Open the circuit
		breaker.recordFailure();
		breaker.recordFailure();
		breaker.recordFailure();

		// Wait for reset timeout
		Thread.sleep(150);
		assertTrue(breaker.allowRequest()); // Transition to HALF_OPEN
		assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

		// Any failure in half-open goes back to open
		breaker.recordFailure();
		assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
		assertFalse(breaker.allowRequest());
	}

	@Test
	public void testManualReset() {
		// Open the circuit
		breaker.recordFailure();
		breaker.recordFailure();
		breaker.recordFailure();
		assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

		// Manually reset
		breaker.reset();
		assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
		assertTrue(breaker.allowRequest());
		assertEquals(0, breaker.getFailureCount());
	}

	@Test
	public void testIsOpen() {
		assertFalse(breaker.isOpen());

		// Open the circuit
		breaker.recordFailure();
		breaker.recordFailure();
		breaker.recordFailure();

		assertTrue(breaker.isOpen());
	}

	@Test
	public void testToString() {
		String str = breaker.toString();
		assertTrue(str.contains("CircuitBreaker"));
		assertTrue(str.contains("state=CLOSED"));
		assertTrue(str.contains("failures=0"));
	}

	@Test
	public void testDefaultConstructor() {
		CircuitBreaker defaultBreaker = new CircuitBreaker();
		assertEquals(CircuitBreaker.State.CLOSED, defaultBreaker.getState());

		// Default threshold is 5
		for (int i = 0; i < 4; i++) {
			defaultBreaker.recordFailure();
		}
		assertEquals(CircuitBreaker.State.CLOSED, defaultBreaker.getState());

		defaultBreaker.recordFailure();
		assertEquals(CircuitBreaker.State.OPEN, defaultBreaker.getState());
	}
}
