package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.util.UUID;

import lucee.extension.io.cache.util.Coder;

/**
 * Manages distributed locks for session data using Redis.
 * Uses the SET NX EX pattern for atomic lock acquisition.
 * Provides safe lock release using Lua script to ensure only the lock owner can release.
 */
public class SessionLockManager {

	// Lua script for safe lock release - only releases if the value matches
	private static final String RELEASE_LOCK_SCRIPT =
		"if redis.call('get', KEYS[1]) == ARGV[1] then " +
		"    return redis.call('del', KEYS[1]) " +
		"else " +
		"    return 0 " +
		"end";

	private static final byte[] RELEASE_LOCK_SCRIPT_BYTES = RELEASE_LOCK_SCRIPT.getBytes(Coder.UTF8);

	// Default lock prefix
	private static final String LOCK_PREFIX = "_lock:";

	// Default lock expiration in seconds (prevents orphaned locks)
	private static final int DEFAULT_LOCK_EXPIRATION_SECONDS = 30;

	// Default timeout for acquiring a lock in milliseconds
	private static final long DEFAULT_ACQUIRE_TIMEOUT_MS = 5000;

	// Retry interval when trying to acquire a lock
	private static final long RETRY_INTERVAL_MS = 50;

	private final RedisCache cache;
	private final int lockExpirationSeconds;
	private final long acquireTimeoutMs;
	private final String lockPrefix;

	/**
	 * Create a SessionLockManager with default settings.
	 */
	public SessionLockManager(RedisCache cache) {
		this(cache, DEFAULT_LOCK_EXPIRATION_SECONDS, DEFAULT_ACQUIRE_TIMEOUT_MS, LOCK_PREFIX);
	}

	/**
	 * Create a SessionLockManager with custom settings.
	 *
	 * @param cache The RedisCache instance to use
	 * @param lockExpirationSeconds How long the lock lives before auto-expiring
	 * @param acquireTimeoutMs How long to wait when trying to acquire a lock
	 * @param lockPrefix Prefix for lock keys
	 */
	public SessionLockManager(RedisCache cache, int lockExpirationSeconds, long acquireTimeoutMs, String lockPrefix) {
		this.cache = cache;
		this.lockExpirationSeconds = lockExpirationSeconds;
		this.acquireTimeoutMs = acquireTimeoutMs;
		this.lockPrefix = lockPrefix;
	}

	/**
	 * Acquire a lock for the given session key.
	 * Blocks until the lock is acquired or timeout occurs.
	 *
	 * @param sessionKey The session key to lock
	 * @return A Lock object that must be released when done, or null if lock could not be acquired
	 * @throws IOException If a Redis error occurs
	 */
	public Lock acquireLock(String sessionKey) throws IOException {
		return acquireLock(sessionKey, acquireTimeoutMs);
	}

	/**
	 * Acquire a lock for the given session key with custom timeout.
	 *
	 * @param sessionKey The session key to lock
	 * @param timeoutMs Maximum time to wait for the lock
	 * @return A Lock object that must be released when done, or null if lock could not be acquired
	 * @throws IOException If a Redis error occurs
	 */
	public Lock acquireLock(String sessionKey, long timeoutMs) throws IOException {
		String lockKey = lockPrefix + sessionKey;
		String lockValue = generateLockValue();
		byte[] bLockKey = Coder.toKey(lockKey);
		byte[] bLockValue = Coder.toBytes(lockValue);

		long startTime = System.currentTimeMillis();
		long deadline = startTime + timeoutMs;

		Redis conn = cache.getConnection();
		try {
			while (System.currentTimeMillis() < deadline) {
				// Try to acquire lock with SET NX EX (atomic operation)
				Object result = conn.call("SET", bLockKey, bLockValue, "NX", "EX", String.valueOf(lockExpirationSeconds));

				if (result != null && "OK".equals(new String((byte[]) result, Coder.UTF8))) {
					// Lock acquired successfully
					return new Lock(lockKey, lockValue, this);
				}

				// Lock not acquired, wait and retry
				try {
					Thread.sleep(RETRY_INTERVAL_MS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return null;
				}
			}

			// Timeout reached, could not acquire lock
			return null;
		}
		finally {
			cache.releaseConnectionEL(conn);
		}
	}

	/**
	 * Try to acquire a lock without blocking.
	 *
	 * @param sessionKey The session key to lock
	 * @return A Lock object if acquired, or null if the lock is already held
	 * @throws IOException If a Redis error occurs
	 */
	public Lock tryAcquireLock(String sessionKey) throws IOException {
		String lockKey = lockPrefix + sessionKey;
		String lockValue = generateLockValue();
		byte[] bLockKey = Coder.toKey(lockKey);
		byte[] bLockValue = Coder.toBytes(lockValue);

		Redis conn = cache.getConnection();
		try {
			Object result = conn.call("SET", bLockKey, bLockValue, "NX", "EX", String.valueOf(lockExpirationSeconds));

			if (result != null && "OK".equals(new String((byte[]) result, Coder.UTF8))) {
				return new Lock(lockKey, lockValue, this);
			}

			return null;
		}
		finally {
			cache.releaseConnectionEL(conn);
		}
	}

	/**
	 * Release a lock. Only releases if the lock value matches (prevents releasing another process's lock).
	 *
	 * @param lockKey The lock key
	 * @param lockValue The lock value (must match what was used to acquire)
	 * @return true if the lock was released, false if it wasn't held or already expired
	 * @throws IOException If a Redis error occurs
	 */
	protected boolean releaseLock(String lockKey, String lockValue) throws IOException {
		byte[] bLockKey = Coder.toKey(lockKey);
		byte[] bLockValue = Coder.toBytes(lockValue);

		Redis conn = cache.getConnection();
		try {
			// Use Lua script for atomic check-and-delete
			Object result = conn.call("EVAL", RELEASE_LOCK_SCRIPT_BYTES, "1", bLockKey, bLockValue);

			if (result instanceof Long) {
				return ((Long) result) == 1L;
			}
			return false;
		}
		finally {
			cache.releaseConnectionEL(conn);
		}
	}

	/**
	 * Extend the expiration of an existing lock.
	 *
	 * @param lockKey The lock key
	 * @param lockValue The lock value (must match)
	 * @param additionalSeconds Seconds to add to the expiration
	 * @return true if the lock was extended, false if it wasn't held
	 * @throws IOException If a Redis error occurs
	 */
	public boolean extendLock(String lockKey, String lockValue, int additionalSeconds) throws IOException {
		// Lua script to extend lock only if we still own it
		String extendScript =
			"if redis.call('get', KEYS[1]) == ARGV[1] then " +
			"    return redis.call('expire', KEYS[1], ARGV[2]) " +
			"else " +
			"    return 0 " +
			"end";

		byte[] bLockKey = Coder.toKey(lockKey);
		byte[] bLockValue = Coder.toBytes(lockValue);
		byte[] bExpire = Coder.toBytes(String.valueOf(additionalSeconds));

		Redis conn = cache.getConnection();
		try {
			Object result = conn.call("EVAL", Coder.toBytes(extendScript), "1", bLockKey, bLockValue, bExpire);

			if (result instanceof Long) {
				return ((Long) result) == 1L;
			}
			return false;
		}
		finally {
			cache.releaseConnectionEL(conn);
		}
	}

	/**
	 * Generate a unique value for this lock instance.
	 * Uses UUID combined with thread ID for uniqueness.
	 */
	private String generateLockValue() {
		return UUID.randomUUID().toString() + ":" + Thread.currentThread().getId();
	}

	/**
	 * Represents a held lock. Must be released when done.
	 */
	public static class Lock implements AutoCloseable {
		private final String key;
		private final String value;
		private final SessionLockManager manager;
		private volatile boolean released = false;

		Lock(String key, String value, SessionLockManager manager) {
			this.key = key;
			this.value = value;
			this.manager = manager;
		}

		/**
		 * Get the lock key.
		 */
		public String getKey() {
			return key;
		}

		/**
		 * Check if this lock has been released.
		 */
		public boolean isReleased() {
			return released;
		}

		/**
		 * Release the lock.
		 *
		 * @return true if the lock was successfully released
		 * @throws IOException If a Redis error occurs
		 */
		public boolean release() throws IOException {
			if (released) {
				return false;
			}
			released = true;
			return manager.releaseLock(key, value);
		}

		/**
		 * Extend the lock's expiration.
		 *
		 * @param additionalSeconds Seconds to add
		 * @return true if successful
		 * @throws IOException If a Redis error occurs
		 */
		public boolean extend(int additionalSeconds) throws IOException {
			if (released) {
				return false;
			}
			return manager.extendLock(key, value, additionalSeconds);
		}

		/**
		 * AutoCloseable implementation - releases the lock.
		 */
		@Override
		public void close() {
			if (!released) {
				try {
					release();
				}
				catch (IOException e) {
					// Log but don't throw - we're in close()
				}
			}
		}
	}
}
