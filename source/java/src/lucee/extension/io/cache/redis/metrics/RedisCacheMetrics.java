package lucee.extension.io.cache.redis.metrics;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import lucee.extension.io.cache.redis.RedisCache;
import lucee.runtime.type.Struct;

/**
 * Provides Prometheus-compatible metrics for Redis cache monitoring.
 * Exports cache statistics in a format suitable for scraping by Prometheus.
 */
public class RedisCacheMetrics {

	private static final DecimalFormat DECIMAL_FORMAT;

	static {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
		DECIMAL_FORMAT = new DecimalFormat("0.######", symbols);
	}

	private final RedisCache cache;
	private final String cacheName;
	private final String prefix;

	/**
	 * Create a metrics exporter for the given cache.
	 *
	 * @param cache The Redis cache to export metrics from
	 * @param cacheName A name for this cache instance (used in metric labels)
	 */
	public RedisCacheMetrics(RedisCache cache, String cacheName) {
		this(cache, cacheName, "lucee_redis_cache");
	}

	/**
	 * Create a metrics exporter with custom prefix.
	 *
	 * @param cache The Redis cache to export metrics from
	 * @param cacheName A name for this cache instance (used in metric labels)
	 * @param prefix The prefix for all metrics
	 */
	public RedisCacheMetrics(RedisCache cache, String cacheName, String prefix) {
		this.cache = cache;
		this.cacheName = sanitizeLabel(cacheName);
		this.prefix = prefix;
	}

	/**
	 * Export all metrics in Prometheus text format.
	 *
	 * @return Prometheus-formatted metrics string
	 */
	public String exportPrometheusMetrics() {
		StringBuilder sb = new StringBuilder();

		// Cache statistics
		appendMetric(sb, "hits_total", "counter", "Total number of cache hits", cache.hitCount());
		appendMetric(sb, "misses_total", "counter", "Total number of cache misses", cache.missCount());
		appendMetric(sb, "puts_total", "counter", "Total number of cache puts", cache.putCount());
		appendMetric(sb, "removes_total", "counter", "Total number of cache removes", cache.removeCount());

		// Hit ratio
		long total = cache.hitCount() + cache.missCount();
		double hitRatio = total > 0 ? (double) cache.hitCount() / total : 0.0;
		appendMetric(sb, "hit_ratio", "gauge", "Cache hit ratio", hitRatio);

		// Pool statistics
		try {
			Struct poolInfo = cache.getPoolInfo();
			if (poolInfo != null) {
				appendPoolMetric(sb, poolInfo, "NumActive", "active_connections", "gauge", "Number of active connections");
				appendPoolMetric(sb, poolInfo, "NumIdle", "idle_connections", "gauge", "Number of idle connections");
				appendPoolMetric(sb, poolInfo, "MaxTotal", "max_connections", "gauge", "Maximum total connections");
				appendPoolMetric(sb, poolInfo, "BorrowedCount", "borrowed_total", "counter", "Total connections borrowed");
				appendPoolMetric(sb, poolInfo, "ReturnedCount", "returned_total", "counter", "Total connections returned");
				appendPoolMetric(sb, poolInfo, "CreatedCount", "created_total", "counter", "Total connections created");
				appendPoolMetric(sb, poolInfo, "DestroyedCount", "destroyed_total", "counter", "Total connections destroyed");
				appendPoolMetric(sb, poolInfo, "MeanBorrowWaitTimeMillis", "borrow_wait_ms", "gauge", "Mean wait time for connection borrow");
			}
		}
		catch (Exception e) {
			// Ignore pool info errors
		}

		return sb.toString();
	}

	/**
	 * Export metrics as a structured object for programmatic access.
	 * Uses RedisCache.getCustomInfo() which already includes cache statistics.
	 *
	 * @return A Struct containing all metrics
	 * @throws IOException If metrics cannot be retrieved
	 */
	public Struct exportMetricsStruct() throws IOException {
		return cache.getCustomInfo();
	}

	/**
	 * Export metrics in JSON format.
	 *
	 * @return JSON string of metrics
	 */
	public String exportJsonMetrics() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");

		// Cache statistics
		sb.append("\"cache_name\":\"").append(escapeJson(cacheName)).append("\",");
		sb.append("\"hits\":").append(cache.hitCount()).append(",");
		sb.append("\"misses\":").append(cache.missCount()).append(",");
		sb.append("\"puts\":").append(cache.putCount()).append(",");
		sb.append("\"removes\":").append(cache.removeCount()).append(",");

		long total = cache.hitCount() + cache.missCount();
		double hitRatio = total > 0 ? (double) cache.hitCount() / total : 0.0;
		sb.append("\"hit_ratio\":").append(formatDouble(hitRatio)).append(",");

		// Pool statistics
		sb.append("\"pool\":{");
		try {
			Struct poolInfo = cache.getPoolInfo();
			if (poolInfo != null) {
				sb.append("\"active\":").append(poolInfo.get("NumActive", 0)).append(",");
				sb.append("\"idle\":").append(poolInfo.get("NumIdle", 0)).append(",");
				sb.append("\"max\":").append(poolInfo.get("MaxTotal", 0)).append(",");
				sb.append("\"borrowed\":").append(poolInfo.get("BorrowedCount", 0)).append(",");
				sb.append("\"returned\":").append(poolInfo.get("ReturnedCount", 0));
			}
		}
		catch (Exception e) {
			sb.append("\"error\":\"").append(escapeJson(e.getMessage())).append("\"");
		}
		sb.append("}");

		sb.append("}");
		return sb.toString();
	}

	private void appendMetric(StringBuilder sb, String name, String type, String help, long value) {
		appendMetric(sb, name, type, help, (double) value);
	}

	private void appendMetric(StringBuilder sb, String name, String type, String help, double value) {
		String fullName = prefix + "_" + name;
		sb.append("# HELP ").append(fullName).append(" ").append(help).append("\n");
		sb.append("# TYPE ").append(fullName).append(" ").append(type).append("\n");
		sb.append(fullName).append("{cache=\"").append(cacheName).append("\"} ").append(formatDouble(value)).append("\n");
	}

	private void appendPoolMetric(StringBuilder sb, Struct poolInfo, String key, String name, String type, String help) {
		Object value = poolInfo.get(key, null);
		if (value != null) {
			String fullName = prefix + "_pool_" + name;
			sb.append("# HELP ").append(fullName).append(" ").append(help).append("\n");
			sb.append("# TYPE ").append(fullName).append(" ").append(type).append("\n");
			sb.append(fullName).append("{cache=\"").append(cacheName).append("\"} ");
			if (value instanceof Number) {
				sb.append(formatDouble(((Number) value).doubleValue()));
			}
			else {
				sb.append(value);
			}
			sb.append("\n");
		}
	}

	private String sanitizeLabel(String label) {
		if (label == null) return "unknown";
		return label.replaceAll("[^a-zA-Z0-9_]", "_");
	}

	private String escapeJson(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	private String formatDouble(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return "0";
		}
		return DECIMAL_FORMAT.format(value);
	}
}
