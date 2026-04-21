package lucee.extension.io.cache.redis.tags;

import lucee.commons.io.log.Log;
import lucee.extension.io.cache.redis.udf.RedisCommand;
import lucee.extension.io.cache.redis.udf.RedisCommandLowPriority;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Array;

public class RedLock {

	private static final Integer ONE = Integer.valueOf(1);

	private CFMLEngine engine;

	private String cacheName;

	private String name;

	private long timeout;

	private boolean throwontimeout;

	private boolean logontimeout;

	private int expires;

	private int amount;

	private boolean release;

	private String lockNamePrefix;

	private String lockNameOpen;

	private String lockNameClose;

	private String logName;

	private Log log;

	public RedLock(String name, String cacheName, int amount, long timeout, boolean throwontimeout, boolean logontimeout, String logName, int expires) throws PageException {
		engine = CFMLEngineFactory.getInstance();

		if (Util.isEmpty(name, true)) throw engine.getExceptionUtil().createApplicationException("name is required and cannot be empty!");
		this.name = name.trim();

		if (Util.isEmpty(cacheName, true)) throw engine.getExceptionUtil().createApplicationException("cache is required and cannot be empty!");
		this.cacheName = cacheName.trim();

		if (timeout < 10L) throw engine.getExceptionUtil().createApplicationException("timeout must be at least 0.01 seconds (10ms)");
		else this.timeout = timeout;
		this.throwontimeout = throwontimeout;
		this.logontimeout = logontimeout;

		this.expires = expires;
		if (this.expires <= 0) this.expires = 600;

		this.amount = amount;
		if (this.amount < 1) throw engine.getExceptionUtil().createApplicationException("amount need to be at least 1, now it is " + amount);

		this.release = false;

		this.lockNamePrefix = "dilo:" + this.name + ":";
		this.lockNameOpen = this.lockNamePrefix + "open";
		this.lockNameClose = this.lockNamePrefix + "close";

		// log
		this.logName = Util.isEmpty(logName, true) ? null : logName;

		log(CFMLEngineFactory.getInstance().getThreadConfig(), Log.LEVEL_INFO, "RedLock initialised: name=[" + this.name + "] cache=[" + this.cacheName + "] amount=[" + this.amount
				+ "] timeout=[" + timeoutInSeconds() + "s] expires=[" + this.expires + "s]", null);
	}

	private void log(Config config, int level, String msg, Exception ex) {
		if (config == null) return;

		if (log == null) {
			if (logName != null) {
				log = config.getLog(logName);
			}
			// fallback or not defined
			if (log == null) {
				log = config.getLog("distributedlock");
				if (log == null) log = config.getLog("redis");
				if (log == null) log = config.getLog("application");
			}
		}
		if (ex != null) {
			log.log(level, "DistributedLock", msg, ex);
		}
		else {
			log.log(level, "DistributedLock", msg);
		}
	}

	/**
	 * Try to acquire a lock for this resource
	 * 
	 * @param resource Resource
	 * @return LockResult containing resource, value, validityTime if lock is acquired successfully or
	 *         else null
	 * @throws PageException
	 */
	public boolean lock(PageContext pc) throws PageException {
		release = false;

		log(pc.getConfig(), Log.LEVEL_INFO, "Attempting to acquire lock [" + name + "] (amount=" + amount + ", timeout=" + timeoutInSeconds() + "s, expires=" + expires + "s)",
				null);

		long acquireStart = System.currentTimeMillis();

		Array commands = engine.getCreationUtil().createArray();
		Array cmd1 = engine.getCreationUtil().createArray();
		cmd1.append("eval");
		cmd1.append("if redis.call('TTL', ARGV[1]) == -1 then "

				+ " redis.call('expire', ARGV[1], '" + expires + 60 + "') end "

				+ " local time=redis.call('time')[1];"

				+ " local open_len = redis.call('llen', KEYS[1]);"

				+ " local close_len = redis.call('llen', ARGV[1]);"

				+ " if open_len	+ close_len < " + amount + " then redis.call('LPUSH', KEYS[1], time) "

				+ " elseif open_len + close_len > " + amount + " then redis.call('DEL', KEYS[1]) "

				+ " elseif open_len > 0 then redis.call('LSET', KEYS[1],-1,time) end");

		cmd1.append("1");
		cmd1.append(lockNameOpen);
		cmd1.append(lockNameClose);
		commands.append(cmd1);

		Array cmd2 = engine.getCreationUtil().createArray();
		cmd2.append("BRPOPLPUSH");
		cmd2.append(lockNameOpen);
		cmd2.append(lockNameClose);
		cmd2.append(timeoutInSeconds());
		commands.append(cmd2);

		log(pc.getConfig(), Log.LEVEL_INFO, "Sending BRPOPLPUSH to Redis: open=[" + lockNameOpen + "] close=[" + lockNameClose + "] blocking up to " + timeoutInSeconds() + "s",
				null);

		Array resArr = engine.getCastUtil().toArray(new RedisCommandLowPriority().invoke(pc, engine, commands, false, null, cacheName), null);
		release = false;
		final boolean hasResult = resArr != null && resArr.get(2, null) != null;

		long elapsed = System.currentTimeMillis() - acquireStart;

		// we could aquire a lock
		if (hasResult) {
			log(pc.getConfig(), Log.LEVEL_INFO, "Lock [" + name + "] acquired after " + elapsed + "ms, setting TTL on close list to " + expires + "s", null);

			Array cmd = engine.getCreationUtil().createArray();
			cmd.append("expire");
			cmd.append(lockNameClose);
			cmd.append(expires + "");

			if (ONE.equals(engine.getCastUtil().toInteger(new RedisCommand().invoke(pc, engine, cmd, false, null, cacheName), null))) {
				release = true;
				log(pc.getConfig(), Log.LEVEL_INFO, "TTL set successfully on close list [" + lockNameClose + "], lock is fully established", null);
			}
			else {
				log(pc.getConfig(), Log.LEVEL_WARN, "could not set TTL on close list [" + lockNameClose + "] — lock may expire unexpectedly, release flag not set", null);
			}

			return true;
		}

		log(pc.getConfig(), Log.LEVEL_INFO,
				"Lock [" + name + "] not acquired after " + elapsed + "ms (BRPOPLPUSH returned no result, open=[" + lockNameOpen + "] close=[" + lockNameClose + "])", null);

		if (logontimeout) {
			log(pc.getConfig(), Log.LEVEL_WARN, "reached timeout [" + timeoutInSeconds() + "] for lock [" + name + "]", null);
		}
		if (throwontimeout) {
			throw engine.getExceptionUtil().createApplicationException("Could not aquire a distributed lock for the name [" + name + "] in [" + timeoutInSeconds() + "] seconds.");
		}
		return false;

	}

	String timeoutInSeconds() {
		return engine.getCastUtil().toString((((int) (timeout / 10L)) / 100d));
	}

	/**
	 * Release the lock for this resource from all instances
	 * 
	 * @param resource Resource
	 * @param value Value
	 * @throws PageException
	 */
	public void release(PageContext pc) throws PageException {

		if (release) {
			log(pc.getConfig(), Log.LEVEL_INFO, "Releasing lock [" + name + "]: moving token from close=[" + lockNameClose + "] back to open=[" + lockNameOpen + "]", null);

			// slide to unlock
			Array cmd = engine.getCreationUtil().createArray();
			cmd.append("eval");
			cmd.append(" local time=redis.call('time')[1];"

					+ " redis.call('LSET', ARGV[1],-1,time); "

					+ " redis.call('RPOPLPUSH', ARGV[1],KEYS[1]); "

			);
			cmd.append("1");
			cmd.append(lockNameOpen);
			cmd.append(lockNameClose);

			try {
				Array res = engine.getCastUtil().toArray(new RedisCommand().invoke(pc, engine, cmd, false, null, cacheName), null);
				if (res == null || res.get(2, null) == null) {
					log(pc.getConfig(), Log.LEVEL_INFO, "Could not release the lock [" + name + "], as the lock was not found, maybe it had already expired", null);
				}
				else {
					log(pc.getConfig(), Log.LEVEL_INFO, "Lock [" + name + "] released successfully: token returned to open list [" + lockNameOpen + "]", null);
				}
			}
			catch (Exception e) {
				log(pc.getConfig(), Log.LEVEL_ERROR, "Could not release the lock [" + name + "], as lock was not found, maybe it had already expired", e);
			}
		}
		else {
			log(pc.getConfig(), Log.LEVEL_INFO, "Release called for lock [" + name + "] but release flag is false — lock was never acquired or TTL set failed, skipping Redis call",
					null);
		}
	}
}