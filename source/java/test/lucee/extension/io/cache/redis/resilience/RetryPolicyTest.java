package lucee.extension.io.cache.redis.resilience;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for RetryPolicy.
 */
public class RetryPolicyTest {

	private RetryPolicy policy;

	@Before
	public void setUp() {
		// Create policy with 3 retries, 10ms initial delay, 100ms max delay
		policy = new RetryPolicy(3, 10, 100, 2.0, null);
	}

	@Test
	public void testSuccessfulExecution() throws IOException {
		AtomicInteger attempts = new AtomicInteger(0);

		String result = policy.execute(() -> {
			attempts.incrementAndGet();
			return "success";
		});

		assertEquals("success", result);
		assertEquals(1, attempts.get());
	}

	@Test
	public void testRetryOnRetryableException() throws IOException {
		AtomicInteger attempts = new AtomicInteger(0);

		String result = policy.execute(() -> {
			int attempt = attempts.incrementAndGet();
			if (attempt < 3) {
				throw new SocketException("Connection reset");
			}
			return "success after retries";
		});

		assertEquals("success after retries", result);
		assertEquals(3, attempts.get());
	}

	@Test(expected = IOException.class)
	public void testExhaustedRetries() throws IOException {
		AtomicInteger attempts = new AtomicInteger(0);

		policy.execute(() -> {
			attempts.incrementAndGet();
			throw new SocketException("Connection reset");
		});

		// Should have tried maxRetries + 1 times (initial + retries)
		assertEquals(4, attempts.get());
	}

	@Test(expected = IOException.class)
	public void testNonRetryableException() throws IOException {
		AtomicInteger attempts = new AtomicInteger(0);

		policy.execute(() -> {
			attempts.incrementAndGet();
			throw new IOException("Invalid data");
		});

		// Should not retry for non-retryable exception
		assertEquals(1, attempts.get());
	}

	@Test
	public void testIsRetryableSocketException() {
		assertTrue(policy.isRetryable(new SocketException()));
		assertTrue(policy.isRetryable(new SocketTimeoutException()));
	}

	@Test
	public void testIsRetryableByMessage() {
		assertTrue(policy.isRetryable(new IOException("Connection reset by peer")));
		assertTrue(policy.isRetryable(new IOException("Broken pipe")));
		assertTrue(policy.isRetryable(new IOException("Connection refused")));
		assertTrue(policy.isRetryable(new IOException("Connection timed out")));
		assertTrue(policy.isRetryable(new IOException("No route to host")));
		assertTrue(policy.isRetryable(new IOException("Network is unreachable")));
	}

	@Test
	public void testNotRetryable() {
		assertFalse(policy.isRetryable(new IOException("Invalid data")));
		assertFalse(policy.isRetryable(new RuntimeException("Unknown error")));
		assertFalse(policy.isRetryable(null));
	}

	@Test
	public void testIsRetryableCause() {
		// Nested exception with retryable cause
		IOException wrapper = new IOException("Wrapped", new SocketException("Connection reset"));
		assertTrue(policy.isRetryable(wrapper));
	}

	@Test
	public void testVoidExecution() throws IOException {
		AtomicInteger counter = new AtomicInteger(0);

		policy.executeVoid(() -> {
			counter.incrementAndGet();
		});

		assertEquals(1, counter.get());
	}

	@Test
	public void testVoidExecutionWithRetry() throws IOException {
		AtomicInteger attempts = new AtomicInteger(0);

		policy.executeVoid(() -> {
			int attempt = attempts.incrementAndGet();
			if (attempt < 2) {
				throw new SocketException("Connection reset");
			}
		});

		assertEquals(2, attempts.get());
	}

	@Test
	public void testWithCircuitBreaker() throws IOException {
		CircuitBreaker breaker = new CircuitBreaker(5, 1000, 3);
		RetryPolicy policyWithBreaker = new RetryPolicy(breaker);

		// Successful execution should record success
		policyWithBreaker.execute(() -> "success");
		assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
	}

	@Test
	public void testCircuitBreakerOpens() throws IOException {
		CircuitBreaker breaker = new CircuitBreaker(3, 1000, 3);
		RetryPolicy policyWithBreaker = new RetryPolicy(2, 1, 10, 2.0, breaker);

		// Exhaust retries repeatedly to trigger circuit breaker
		for (int i = 0; i < 3; i++) {
			try {
				policyWithBreaker.execute(() -> {
					throw new SocketException("Connection failed");
				});
			}
			catch (IOException e) {
				// Expected
			}
		}

		assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
	}

	@Test
	public void testBuilderPattern() {
		RetryPolicy builtPolicy = RetryPolicy.builder().maxRetries(5).initialDelay(50).maxDelay(1000).backoffMultiplier(1.5).build();

		assertNotNull(builtPolicy);
	}

	@Test
	public void testBuilderWithCircuitBreaker() {
		CircuitBreaker breaker = new CircuitBreaker();
		RetryPolicy builtPolicy = RetryPolicy.builder().maxRetries(5).circuitBreaker(breaker).build();

		assertNotNull(builtPolicy);
	}

	@Test
	public void testDefaultPolicy() {
		RetryPolicy defaultPolicy = new RetryPolicy();
		assertNotNull(defaultPolicy);

		// Should be able to execute
		try {
			String result = defaultPolicy.execute(() -> "test");
			assertEquals("test", result);
		}
		catch (IOException e) {
			fail("Should not throw exception");
		}
	}
}
