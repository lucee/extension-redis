package lucee.extension.io.cache.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.exp.PageException;

public class Coder {

	public static final Charset UTF8 = Charset.forName("UTF-8");

	public static byte[] toKey(String key) {
		return key.trim().toLowerCase().getBytes(UTF8);
	}

	public static String toKey(byte[] bkey) {
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
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStreamImpl(cl, bais);
			return ois.readObject();
		}
		// happens when the object is not ObjectOutputstream serialized
		catch (StreamCorruptedException sce) {
			try {
				return evaluateLegacy(new String(data, "UTF-8"));
			}
			catch (UnsupportedEncodingException uee) {
				return evaluateLegacy(new String(data));
			}
		}
		catch (Exception e) {
			try {
				return evaluateLegacy(new String(data, "UDF-8"));
			}
			catch (UnsupportedEncodingException uee) {
				throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
			}
		}
		finally {
			Util.closeEL(ois);
		}
	}

	private static Object evaluateLegacy(String val) throws IOException {
		try {
			// number
			if (val.startsWith("nbr(") && val.endsWith(")")) {
				// System.err.println("nbr:" + val + ":" + func.getClass().getName());
				return CFMLEngineFactory.getInstance().getCastUtil().toDouble(val.substring(4, val.length() - 1));
			}
			// boolean
			else if (val.startsWith("bool(") && val.endsWith(")")) {
				// System.err.println("bool:" + val + ":" + func.getClass().getName());
				return CFMLEngineFactory.getInstance().getCastUtil().toBoolean(val.substring(5, val.length() - 1));
			}
			// date
			else if (val.startsWith("date(") && val.endsWith(")")) {
				CFMLEngine e = CFMLEngineFactory.getInstance();
				return e.getCreationUtil().createDate(e.getCastUtil().toLongValue(val.substring(5, val.length() - 1)));
			}
			// eval
			else if (val.startsWith("eval(") && val.endsWith(")")) {
				// System.err.println("eval:" + val + ":" + func.getClass().getName());
				return Functions.evaluate(val.substring(5, val.length() - 1));
			}
		}
		catch (PageException pe) {
			CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(pe);
		}
		return val;
	}

	public static byte[] serialize(Object value) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream(); // returns
		ObjectOutputStream oos = new ObjectOutputStream(os);
		oos.writeObject(value);
		oos.flush();
		return os.toByteArray();
	}

}
