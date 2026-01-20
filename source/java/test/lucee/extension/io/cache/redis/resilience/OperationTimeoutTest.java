package lucee.extension.io.cache.redis.resilience;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for OperationTimeout.
 */
public class OperationTimeoutTest {

	private OperationTimeout timeout;

	@Before
	public void setUp() {
		timeout = new OperationTimeout(500); // 500ms default timeout
	}

	@After
	public void tearDown() {
		if (timeout != null) {
			timeout.shutdown();
		}
	}

	@Test
	public void testSuccessfulExecution() throws IOException {
		String result = timeout.execute(() -> "success");
		assertEquals("success", result);
	}

	@Test
	public void testSuccessfulExecutionWithTimeout() throws IOException {
		String result = timeout.execute(() -> {
			Thread.sleep(50); // Short delay
			return "completed";
		}, 1000);

		assertEquals("completed", result);
	}

	@Test(expected = IOException.class)
	public void testTimeout() throws IOException {
		timeout.execute(() -> {
			Thread.sleep(10000); // Sleep longer than timeout
			return "should not reach here";
		}, 100); // 100ms timeout
	}

	@Test
	public void testTimeoutMessage() {
		try {
			timeout.execute(() -> {
				Thread.sleep(10000);
				return null;
			}, 100, "test operation");
			fail("Should have thrown IOException");
		}
		catch (IOException e) {
			assertTrue(e.getMessage().contains("timed out"));
			assertTrue(e.getMessage().contains("test operation"));
			assertTrue(e.getMessage().contains("100ms"));
		}
	}

	@Test
	public void testExceptionPropagation() {
		try {
			timeout.execute(() -> {
				throw new IOException("Custom error");
			});
			fail("Should have thrown IOException");
		}
		catch (IOException e) {
			assertEquals("Custom error", e.getMessage());
		}
	}

	@Test
	public void testRuntimeExceptionWrapping() {
		try {
			timeout.execute(() -> {
				throw new RuntimeException("Runtime error");
			});
			fail("Should have thrown IOException");
		}
		catch (IOException e) {
			assertTrue(e.getCause() instanceof RuntimeException);
		}
	}

	@Test
	public void testVoidExecution() throws IOException {
		AtomicBoolean executed = new AtomicBoolean(false);

		timeout.executeVoid(() -> {
			executed.set(true);
		});

		assertTrue(executed.get());
	}

	@Test(expected = IOException.class)
	public void testVoidTimeout() throws IOException {
		timeout.executeVoid(() -> {
			Thread.sleep(10000);
		}, 100, "void operation");
	}

	@Test
	public void testInterruptedExecution() {
		Thread testThread = Thread.currentThread();

		// Schedule an interrupt
		new Thread(() -> {
			try {
				Thread.sleep(50);
				testThread.interrupt();
			}
			catch (InterruptedException e) {
				// Ignore
			}
		}).start();

		try {
			timeout.execute(() -> {
				Thread.sleep(10000);
				return "should not reach";
			}, 5000);
			fail("Should have thrown IOException");
		}
		catch (IOException e) {
			assertTrue(e.getMessage().contains("interrupted"));
		}
	}

	@Test
	public void testGetDefaultTimeout() {
		assertEquals(500, timeout.getDefaultTimeoutMs());

		OperationTimeout custom = new OperationTimeout(1000);
		assertEquals(1000, custom.getDefaultTimeoutMs());
		custom.shutdown();
	}

	@Test
	public void testDefaultConstructor() {
		OperationTimeout defaultTimeout = new OperationTimeout();
		assertEquals(30000, defaultTimeout.getDefaultTimeoutMs()); // 30 seconds default
		defaultTimeout.shutdown();
	}

	@Test
	public void testShutdown() throws IOException {
		OperationTimeout localTimeout = new OperationTimeout(1000);

		// Should work before shutdown
		String result = localTimeout.execute(() -> "before shutdown");
		assertEquals("before shutdown", result);

		// Shutdown
		localTimeout.shutdown();

		// Note: After shutdown, the executor is closed but execute() might
		// still work if tasks are submitted before shutdown completes
	}

	@Test
	public void testConcurrentExecutions() throws IOException, InterruptedException {
		final int numThreads = 10;
		Thread[] threads = new Thread[numThreads];
		final AtomicBoolean[] results = new AtomicBoolean[numThreads];

		for (int i = 0; i < numThreads; i++) {
			final int index = i;
			results[i] = new AtomicBoolean(false);
			threads[i] = new Thread(() -> {
				try {
					String result = timeout.execute(() -> {
						Thread.sleep(10);
						return "result-" + index;
					});
					results[index].set(result.equals("result-" + index));
				}
				catch (Exception e) {
					// Failed
				}
			});
		}

		// Start all threads
		for (Thread t : threads) {
			t.start();
		}

		// Wait for all threads
		for (Thread t : threads) {
			t.join();
		}

		// Verify all succeeded
		for (int i = 0; i < numThreads; i++) {
			assertTrue("Thread " + i + " failed", results[i].get());
		}
	}

	@Test
	public void testExecuteWithDefaultTimeout() throws IOException {
		// This should use the default 500ms timeout
		String result = timeout.execute(() -> {
			Thread.sleep(50);
			return "quick operation";
		});
		assertEquals("quick operation", result);
	}
}
