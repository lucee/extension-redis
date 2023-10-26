package lucee.extension.io.cache.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;

public class Coder {

	private static final byte GZIP0 = (byte) 0x1f;
	private static final byte GZIP1 = (byte) 0x8b;

	private static byte[] OBJECT_STREAM_HEADER = new byte[] { -84, -19, 0, 5 };

	public static final Charset UTF8 = Charset.forName("UTF-8");

	public static byte[] toKey(String key) {
		return key.trim().toLowerCase().getBytes(UTF8);
	}

	public static byte[] toBytes(String val) {
		return val.getBytes(UTF8);
	}

	public static byte[][] toBytesArrays(String[] values) {
		byte[][] results = new byte[values.length][];
		for (int i = 0; i < results.length; i++) {
			results[i] = Coder.toBytes(values[i]);
		}
		return results;
	}

	public static String toKey(byte[] bkey) {
		return new String(bkey, UTF8);
	}

	public static String toString(byte[] bkey) {
		return new String(bkey, UTF8);
	}

	public static String toStringKey(String key) {
		return key.trim().toLowerCase();
	}

	public static byte[][] toKeys(String[] keys) {
		byte[][] arr = new byte[keys == null ? 0 : keys.length][];
		for (int i = 0; i < keys.length; i++) {
			arr[i] = keys[i].trim().toLowerCase().getBytes(UTF8);
		}
		return arr;
	}

	public static Object evaluate(ClassLoader cl, byte[] data) throws IOException {
		if (data == null) return null;
		if (isGzip(data)) {
			return decompress(cl, data);
		}

		if (!isObjectStream(data)) {
			BsonDocument doc = BSON.toBsonDocument(data, null);
			if (doc != null) {
				if (doc.getFirstKey().equals(BSON.IK_STORAGEVALUE_KEY)) {
					Iterator<Entry<String, BsonValue>> it = doc.entrySet().iterator();
					Entry<String, BsonValue> first = it.next();
					BsonValue v = first.getValue();
					if (v.isInt64() && first.getKey().equals(BSON.IK_STORAGEVALUE_KEY)) {
						return BSON.toIKStorageValue(v.asInt64().longValue(), it, CFMLEngineFactory.getInstance());
					}
				}
				return BSON.toStruct(doc, CFMLEngineFactory.getInstance());
			}
			return toString(data);
		}

		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStreamImpl(cl, bais);
			return ois.readObject();
		}
		catch (ClassNotFoundException cnfe) {
			String className = cnfe.getMessage();
			if (!Util.isEmpty(className, true)) {
				Class<?> clazz = CFMLEngineFactory.getInstance().getClassUtil().loadClass(className.trim());
				bais = new ByteArrayInputStream(data);
				ois = new ObjectInputStreamImpl(clazz.getClassLoader(), bais);
				try {
					return ois.readObject();
				}
				catch (ClassNotFoundException e) {
					throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
				}
			}
			try {
				return toString(data);
			}
			catch (Exception ee) {
				throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(cnfe);
			}
		}
		// happens when the object is not ObjectOutputstream serialized
		catch (Exception e) {
			try {
				return toString(data);
			}
			catch (Exception ee) {
				throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
			}
		}
		finally {
			Util.closeEL(ois);
		}
	}

	private static boolean isObjectStream(byte[] data) {
		if (data == null || data.length < OBJECT_STREAM_HEADER.length) return false;
		for (int i = 0; i < OBJECT_STREAM_HEADER.length; i++) {
			if (data[i] != OBJECT_STREAM_HEADER[i]) return false;
		}
		return true;
	}

	public static byte[] serialize(Object value) throws IOException {
		if (value instanceof CharSequence) {
			return toBytes(value.toString());
		}
		if (value instanceof Number) {
			return toBytes(value.toString());
		}

		BsonDocument doc = BSON.toBsonDocument(value, false, null);
		if (doc != null) {
			return BSON.toBytes(doc);
		}

		return compress(value);
	}

	public static byte[] compress(Object val) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(baos));
		oos.writeObject(val);
		oos.close();
		return baos.toByteArray();
	}

	public static Object decompress(ClassLoader cl, byte[] bytes) throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		ObjectInputStreamImpl objectIn = new ObjectInputStreamImpl(cl, new GZIPInputStream(bais));
		try {
			Object val = objectIn.readObject();
			objectIn.close();
			return val;
		}
		catch (ClassNotFoundException cnfe) {
			String className = cnfe.getMessage();
			if (!Util.isEmpty(className, true)) {
				Class<?> clazz = CFMLEngineFactory.getInstance().getClassUtil().loadClass(className.trim());
				bais = new ByteArrayInputStream(bytes);
				objectIn = new ObjectInputStreamImpl(clazz.getClassLoader(), bais);
				try {
					return objectIn.readObject();
				}
				catch (ClassNotFoundException e) {
					throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
				}
			}

			throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(cnfe);
		}
	}

	public static boolean isGzip(byte[] barr) throws IOException {
		return barr != null && barr.length > 1 && barr[0] == GZIP0 && barr[1] == GZIP1;
	}

}
