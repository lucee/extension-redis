package lucee.extension.io.cache.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;

import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;

public class Coder {

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
		if (!isObjectStream(data)) return toString(data);

		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStreamImpl(cl, bais);
			return ois.readObject();
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
		if (value instanceof CharSequence) return toBytes(value.toString());
		// if (value instanceof Number) return toBytes(value.toString());
		// if (value instanceof Boolean) return toBytes(value.toString());

		ByteArrayOutputStream os = new ByteArrayOutputStream(); // returns
		ObjectOutputStream oos = new ObjectOutputStream(os);
		oos.writeObject(value);
		oos.flush();
		return os.toByteArray();
	}

	/*
	 * private static Object evaluateLegacy(String val) throws IOException { try { // number if
	 * (val.startsWith("nbr(") && val.endsWith(")")) { // System.err.println("nbr:" + val + ":" +
	 * func.getClass().getName()); return
	 * CFMLEngineFactory.getInstance().getCastUtil().toDouble(val.substring(4, val.length() - 1)); } //
	 * boolean else if (val.startsWith("bool(") && val.endsWith(")")) { // System.err.println("bool:" +
	 * val + ":" + func.getClass().getName()); return
	 * CFMLEngineFactory.getInstance().getCastUtil().toBoolean(val.substring(5, val.length() - 1)); } //
	 * date else if (val.startsWith("date(") && val.endsWith(")")) { CFMLEngine e =
	 * CFMLEngineFactory.getInstance(); return
	 * e.getCreationUtil().createDate(e.getCastUtil().toLongValue(val.substring(5, val.length() - 1)));
	 * } // eval else if (val.startsWith("eval(") && val.endsWith(")")) { // System.err.println("eval:"
	 * + val + ":" + func.getClass().getName()); return Functions.evaluate(val.substring(5, val.length()
	 * - 1)); } } catch (PageException pe) {
	 * CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(pe); } return val; }
	 */

	/*
	 * public static void main(String[] args) throws IOException { ClassLoader cl =
	 * Coder.class.getClassLoader(); Object res = evaluate(cl, serialize("abc".getBytes()));
	 * print.e(res.getClass().getName()); print.e(res);
	 * 
	 * print.e(evaluate(cl, serialize("abc"))); print.e(evaluate(cl, serialize(new ArrayList<>())));
	 * print.e(evaluate(cl, serialize(new HashMap<>()))); }
	 */

}
