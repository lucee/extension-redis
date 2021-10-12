package lucee.extension.io.cache.redis.sm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;

import lucee.extension.io.cache.util.Functions;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Strings;

public class SecretReciever {

	public static final String AWSPENDING = "AWSPENDING";
	public static final String AWSCURRENT = "AWSCURRENT";
	public static final String AWSPREVIOUS = "AWSPREVIOUS";

	private static Map<String, CredDat> credentials = new ConcurrentHashMap<String, CredDat>();

	private static final ConcurrentHashMap<String, String> tokens = new ConcurrentHashMap<String, String>();

	public static String getSecret(String secretName, String staging, String region, String accessKeyId, String secretKey) throws PageException {
		AWSSecretsManagerClientBuilder builder = AWSSecretsManagerClientBuilder.standard();

		// aws credetials (optional when within EC2)
		if (!Util.isEmpty(accessKeyId, true)) {
			builder.withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretKey)));
		}
		// region
		if (!Util.isEmpty(region, true)) {
			builder.withRegion(region);
		}
		AWSSecretsManager client = builder.build();

		GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest().withSecretId(secretName).withVersionStage(staging);
		GetSecretValueResult getSecretValueResult = client.getSecretValue(getSecretValueRequest);

		String str = getSecretValueResult.getSecretString();
		if (Util.isEmpty(str, true)) str = CFMLEngineFactory.getInstance().getCastUtil().toBase64(getSecretValueResult.getSecretBinary());

		return str;
	}

	public static CredDat getCredential(String secretName, String region, String accessKeyId, String secretKey, boolean force, boolean checkRotation) throws AWSSMException {
		CFMLEngine eng = CFMLEngineFactory.getInstance();

		String key = new StringBuilder(secretName).append(':').append(region).append(':').append(accessKeyId).append(':').append(secretKey).toString();
		CredDat existing = credentials.get(key);

		if (!force && existing != null) return existing;

		String strSecret = null;
		Struct secret = null;
		String user = null, pass = null;

		CredDat c;
		synchronized (getToken(key)) {
			// if there was none before or it has changed, it was already done by an other thread
			c = credentials.get(key);
			if (c != null) {
				if (existing == null || !c.equals(existing)) return c;
			}

			// are we in the middle of a rotation?
			if (checkRotation) {
				try {
					strSecret = getSecret(secretName, AWSPENDING, region, accessKeyId, secretKey);
					secret = eng.getCastUtil().toStruct(Functions.deserializeJSON(eng.getThreadPageContext(), strSecret));
				}
				catch (Exception pe) {
				}
			}

			if (secret == null) {
				try {
					strSecret = getSecret(secretName, AWSCURRENT, region, accessKeyId, secretKey);
					secret = eng.getCastUtil().toStruct(Functions.deserializeJSON(eng.getThreadPageContext(), strSecret));
				}
				catch (Exception pe) {
					throw new AWSSMException(pe);
				}
			}

			// user
			try {
				user = eng.getCastUtil().toString(secret.get("username"));
			}
			catch (PageException pe) {
				throw new AWSSMException("cannot extract username from given secret [" + strSecret + "]");
			}

			// pass
			try {
				pass = eng.getCastUtil().toString(secret.get("password"));
			}
			catch (PageException pe) {
				throw new AWSSMException("cannot extract password from given secret [" + strSecret + "]");
			}

			c = new CredDat(user, pass, eng.getCastUtil().toString(secret.get("host", null), null), eng.getCastUtil().toIntValue(secret.get("port", null), 0));

			credentials.put(key, c);
		}
		return c;
	}

	private static String getToken(String str) {
		String lock = tokens.putIfAbsent(str, str);
		if (lock == null) {
			lock = str;
		}
		return lock;
	}

	public static class CredDat {

		public final long created;
		public final String user;
		public final String pass;
		public final String host;
		public final int port;

		public CredDat(String user, String pass, String host, int port) {
			this.created = System.currentTimeMillis();
			this.user = user;
			this.pass = pass;
			this.host = host;
			this.port = port;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) return true;
			if (!(other instanceof CredDat)) return false;
			CredDat o = (CredDat) other;
			Strings util = CFMLEngineFactory.getInstance().getStringUtil();

			return created == o.created && port == o.port && util.emptyIfNull(user).equals(util.emptyIfNull(o.user)) && util.emptyIfNull(pass).equals(util.emptyIfNull(o.pass))
					&& util.emptyIfNull(host).equals(util.emptyIfNull(o.host));
		}
	}

}
