package lucee.extension.io.cache.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.internal.Base64;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.Decimal128;

import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.op.Castable;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.ObjectWrap;
import lucee.runtime.type.Struct;

public class BSON {
	private static byte[] OBJECT_STREAM_HEADER = new byte[] { -84, -19, 0, 5 };

	private static Method getValue;

	private static Method lastModified;

	private static Method lastModifiedItem;

	private static Class<?> IKStorageValue;

	private static Constructor<?> IKStorageValueConstr;

	private static Class<?> IKStorageScopeItem;

	private static Constructor<?> IKStorageScopeItemConstr;

	public static final Charset UTF8 = Charset.forName("UTF-8");

	private static final byte LAST_BYTE_OF_A_BSON_BYTE_ARRAY = 0;

	private static final Class<?>[] EMPTY_CLASS = new Class[0];
	private static final Object[] EMPTY_OBJ = new Object[0];

	public static final String IK_STORAGEVALUE_KEY = "iksvk";

	private static final Class<?>[] IK_STOARE_VALUE_INIT = new Class[] { Map.class, byte[].class, long.class };
	private static final Class<?>[] IK_STOARE_ITEM_INIT = new Class[] { Object.class, long.class };

	public static Map<String, Object> toMap(byte[] raw) throws IOException {
		return toMap(toBsonDocument(raw));
	}

	public static Map<String, Object> toMap(BsonDocument bd) throws IOException {
		Map<String, Object> map = new HashMap<>();
		Iterator<Entry<String, BsonValue>> it = bd.entrySet().iterator();
		Entry<String, BsonValue> e;
		while (it.hasNext()) {
			e = it.next();
			map.put(e.getKey(), _toObject(e.getValue(), null));
		}
		return map;
	}

	public static boolean isBson(byte[] barr) {
		if (barr.length == 0 || barr[barr.length - 1] != LAST_BYTE_OF_A_BSON_BYTE_ARRAY) return false;

		try {
			BSON.toBsonDocument(barr);
			return true;
		}
		catch (Exception ex) {
			return false;
		}
	}

	public static BsonDocument toBsonDocument(byte[] raw, BsonDocument defaultValue) {
		if (raw.length == 0 || raw[raw.length - 1] != LAST_BYTE_OF_A_BSON_BYTE_ARRAY) return defaultValue;

		try {
			return toBsonDocument(raw);
		}
		catch (Exception e) {
		}
		return defaultValue;
	}

	public static BsonDocument toBsonDocument(byte[] raw) {
		BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(raw));
		try {

			return new BsonDocumentCodec().decode(reader, DecoderContext.builder().build());
		}
		finally {
			reader.close();
		}
	}

	public static Struct toStruct(String raw, CFMLEngine engine) throws IOException, ClassNotFoundException {
		return toStruct(toBsonDocument(Base64.decode(raw)), engine);
	}

	public static Struct toStruct(byte[] raw, CFMLEngine engine) throws IOException, ClassNotFoundException {
		return toStruct(toBsonDocument(raw), engine);
	}

	public static Struct toStruct(BsonDocument bd, CFMLEngine engine) throws IOException {
		Struct sct = engine.getCreationUtil().createStruct(Struct.TYPE_LINKED);
		Iterator<Entry<String, BsonValue>> it = bd.entrySet().iterator();
		Entry<String, BsonValue> e;
		while (it.hasNext()) {
			e = it.next();
			sct.setEL(e.getKey(), _toObject(e.getValue(), engine));
		}
		return sct;
	}

	public static Object toIKStorageValue(long lastModified, Iterator<Entry<String, BsonValue>> it, CFMLEngine engine) throws IOException {
		try {
			if (IKStorageValue == null) {
				IKStorageValue = engine.getClassUtil().loadClass("lucee.runtime.type.scope.storage.IKStorageValue");
				IKStorageValueConstr = IKStorageValue.getConstructor(IK_STOARE_VALUE_INIT);
				IKStorageScopeItem = engine.getClassUtil().loadClass("lucee.runtime.type.scope.storage.IKStorageScopeItem");
				IKStorageScopeItemConstr = IKStorageScopeItem.getConstructor(IK_STOARE_ITEM_INIT);
			}

			Map<Key, Object> map = new ConcurrentHashMap<>();

			// lastModified) {
			Entry<String, BsonValue> e;
			BsonArray arr;
			while (it.hasNext()) {
				e = it.next();
				arr = e.getValue().asArray();

				map.put(engine.getCastUtil().toKey(e.getKey()),
						IKStorageScopeItemConstr.newInstance(new Object[] { _toObject(arr.get(1), engine), arr.get(0).asInt64().longValue() }));
			}

			return IKStorageValueConstr.newInstance(new Object[] { map, null, lastModified });
		}
		catch (Exception e) {
			throw engine.getExceptionUtil().toIOException(e);
		}
	}

	private static Object _toObject(BsonValue bv, CFMLEngine engine) throws IOException {
		if (bv instanceof BsonDocument) {
			if (engine != null) return toStruct((BsonDocument) bv, engine);
			return toMap((BsonDocument) bv);
		}

		if (bv.isString()) {
			return bv.asString().getValue();
		}
		if (bv.isBoolean()) {
			return bv.asBoolean().getValue();
		}
		if (bv.isDouble()) {
			return bv.asDouble().getValue();
		}
		if (bv.isArray()) {
			Iterator<BsonValue> it = bv.asArray().iterator();
			List<Object> list = (engine != null) ? (List) engine.getCreationUtil().createArray() : new ArrayList<>();
			while (it.hasNext()) {
				list.add(_toObject(it.next(), engine));
			}
			return list;
		}
		if (bv.isInt32()) {
			return bv.asInt32().getValue();
		}
		if (bv.isInt64()) {
			return bv.asInt64().getValue();
		}
		if (bv.isDecimal128()) {
			return bv.asDecimal128().getValue().bigDecimalValue();
		}
		if (bv.isDateTime()) {
			return new Date(bv.asDateTime().getValue());
		}
		if (bv.isNull()) {
			return null;
		}
		if (bv.isBinary()) {
			byte[] data = bv.asBinary().getData();
			if (Coder.isGzip(data)) return Coder.decompress(CFMLEngineFactory.getInstance().getClass().getClassLoader(), data);
			if (isObjectStream(data)) return evaluate(CFMLEngineFactory.getInstance().getClass().getClassLoader(), data);
			return data;

		}
		throw new IOException("BSON type [" + bv.getBsonType().name() + "] is not supported yet!");
	}

	public static String toBase64String(Object o, boolean allowObjectSerialisation) throws IOException, PageException {
		return Base64.encode(toBytes(o, allowObjectSerialisation));
	}

	public static byte[] toBytes(Object o, boolean allowObjectSerialisation) throws IOException, PageException {
		return toBytes(toBsonDocument(o, allowObjectSerialisation));
	}

	public static byte[] toBytes(BsonDocument bd) throws IOException {
		BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
		BsonBinaryWriter writer = new BsonBinaryWriter(outputBuffer);
		try {

			new BsonDocumentCodec().encode(writer, bd, EncoderContext.builder().isEncodingCollectibleDocument(true).build());
			return outputBuffer.toByteArray();
		}
		finally {
			writer.close();
		}
	}

	public static BsonDocument toBsonDocument(Object o, boolean allowObjectSerialisation, BsonDocument defaultValue) {
		try {
			if (o instanceof Struct) return _toBsonDocumentStruct((Struct) o, new HashSet<>(), allowObjectSerialisation);
			if (o instanceof Map<?, ?>) return _toBsonDocumentMap((Map<?, ?>) o, new HashSet<>(), allowObjectSerialisation);
			if ("lucee.runtime.type.scope.storage.IKStorageValue".equals(o.getClass().getName()))
				return _toBsonDocumentIKStorageValue(o, new HashSet<>(), allowObjectSerialisation);
		}
		catch (Exception e) {
		}
		return defaultValue;
	}

	public static BsonDocument toBsonDocument(Object o, boolean allowObjectSerialisation) throws IOException, PageException {

		if (o instanceof Struct) return _toBsonDocumentStruct((Struct) o, new HashSet<>(), allowObjectSerialisation);
		if (o instanceof Map<?, ?>) return _toBsonDocumentMap((Map<?, ?>) o, new HashSet<>(), allowObjectSerialisation);
		if ("lucee.runtime.type.scope.storage.IKStorageValue".equals(o.getClass().getName())) return _toBsonDocumentIKStorageValue(o, new HashSet<>(), allowObjectSerialisation);
		if (o == null) throw new IOException("cannot convert null value to a BSON object");
		throw new IOException("cannot convert [" + o.getClass().getName() + "] to a BSON object, object must be a struct or a map");
	}

	private static BsonValue toBsonValue(Object o, Set<Object> inside, boolean allowObjectSerialisation) throws IOException, PageException {
		// simple values
		if (o == null) return BsonNull.VALUE;
		if (o instanceof CharSequence) return _toBsonValue(o.toString());
		if (o instanceof Boolean) return _toBsonValue(((Boolean) o).booleanValue());
		if (o instanceof Number) {
			if (o instanceof Double) return _toBsonValue(((Double) o).doubleValue());
			if (o instanceof Integer) return _toBsonValue(((Integer) o).intValue());
			if (o instanceof Long) return _toBsonValue(((Long) o).longValue());
			if (o instanceof BigDecimal) return _toBsonValue(((BigDecimal) o));
			return _toBsonValue(((Number) o).doubleValue());
		}
		if (o instanceof Date) return _toBsonValue(((Date) o));
		if (o instanceof byte[]) return _toBsonValue(((byte[]) o));

		// complex value

		if (o instanceof Collection) {
			try {

				if (inside.contains(o))
					throw new IOException("object cannot be serialized, betcause it has a internal relation to itself, one object is pointing to itself of one of its parents!");
				inside.add(o);
				return _toBsonValue(((Collection) o), inside, allowObjectSerialisation);
			}
			finally {
				inside.remove(o);
			}
		}
		if (o instanceof Map<?, ?>) {
			try {

				if (inside.contains(o))
					throw new IOException("object cannot be serialized, betcause it has a internal relation to itself, one object is pointing to itself of one of its parents!");
				inside.add(o);
				return _toBsonDocumentMap((Map<?, ?>) o, inside, allowObjectSerialisation);
			}
			finally {
				inside.remove(o);
			}
		}
		if (o instanceof java.util.Collection<?>) {
			try {

				if (inside.contains(o))
					throw new IOException("object cannot be serialized, betcause it has a internal relation to itself, one object is pointing to itself of one of its parents!");
				inside.add(o);
				return _toBsonValue(((java.util.Collection<?>) o), inside, allowObjectSerialisation);
			}
			finally {
				inside.remove(o);
			}
		}

		if (o instanceof ObjectWrap) {
			return toBsonValue(((ObjectWrap) o).getEmbededObject(), inside, allowObjectSerialisation);

		}
		if (o instanceof Castable) {
			return _toBsonValue(((Castable) o).castToString());
		}

		if (allowObjectSerialisation && o instanceof Serializable) {
			return _toBsonValue(Coder.compress(o));
		}

		// Decision dec = CFMLEngineFactory.getInstance().getDecisionUtil();

		throw new IOException("type [" + o.getClass().getName() + "] cannot be converted to BSON yet!");
	}

	private static BsonValue _toBsonValue(java.util.Collection<?> coll, Set<Object> inside, boolean allowObjectSerialisation) throws IOException, PageException {
		Iterator<?> it = coll.iterator();
		BsonArray arr = new BsonArray();
		while (it.hasNext()) {
			arr.add(toBsonValue(it.next(), inside, allowObjectSerialisation));
		}
		return arr;
	}

	private static BsonValue _toBsonValue(Collection coll, Set<Object> inside, boolean allowObjectSerialisation) throws IOException, PageException {
		if (coll instanceof Struct) return _toBsonDocumentStruct((Struct) coll, inside, allowObjectSerialisation);
		if (coll instanceof Map<?, ?>) return _toBsonDocumentMap((Map<?, ?>) coll, inside, allowObjectSerialisation);

		Iterator<?> it = coll.getIterator();
		BsonArray arr = new BsonArray();
		while (it.hasNext()) {
			arr.add(toBsonValue(it.next(), inside, allowObjectSerialisation));
		}
		return arr;
	}

	private static BsonDocument _toBsonDocumentStruct(Struct sct, Set<Object> inside, boolean allowObjectSerialisation) throws IOException, PageException {
		Iterator<Entry<Key, Object>> it = sct.entryIterator();
		Entry<Key, Object> e;
		BsonDocument doc = new BsonDocument();
		while (it.hasNext()) {
			e = it.next();
			doc.append(e.getKey().toString(), toBsonValue(e.getValue(), inside, allowObjectSerialisation));
		}
		return doc;
	}

	private static BsonDocument _toBsonDocumentMap(Map<?, ?> map, Set<Object> inside, boolean allowObjectSerialisation) throws IOException, PageException {
		Iterator<?> it = map.entrySet().iterator();
		Entry<?, ?> e;
		BsonDocument doc = new BsonDocument();
		while (it.hasNext()) {
			e = (Entry<?, ?>) it.next();
			doc.append(e.getKey().toString(), toBsonValue(e.getValue(), inside, allowObjectSerialisation)); // TODO do caster.toString here
		}
		return doc;
	}

	private static BsonDocument _toBsonDocumentIKStorageValue(Object obj, Set<Object> inside, boolean allowObjectSerialisation) throws IOException, RuntimeException {
		try {
			// init methods
			if (getValue == null || getValue.getDeclaringClass() != obj.getClass()) {
				getValue = obj.getClass().getMethod("getValue", EMPTY_CLASS);
				lastModified = obj.getClass().getMethod("lastModified", EMPTY_CLASS);
			}

			BsonDocument doc = new BsonDocument();
			doc.append(IK_STORAGEVALUE_KEY, new BsonInt64(((Long) lastModified.invoke(obj, EMPTY_OBJ)).longValue()));

			Iterator<?> it = ((Map<?, ?>) getValue.invoke(obj, EMPTY_OBJ)).entrySet().iterator();
			Entry<?, ?> e;
			ObjectWrap ow;
			BsonArray arr;
			boolean done = false;
			while (it.hasNext()) {
				e = (Entry<?, ?>) it.next();
				ow = (ObjectWrap) e.getValue();
				if (!done && (lastModifiedItem == null || lastModifiedItem.getDeclaringClass() != ow.getClass())) {
					lastModifiedItem = ow.getClass().getMethod("lastModified", EMPTY_CLASS);
					done = true;
				}
				arr = new BsonArray();
				arr.add(new BsonInt64(((Long) lastModifiedItem.invoke(ow, EMPTY_OBJ)).longValue()));
				arr.add(toBsonValue(ow.getEmbededObject(), inside, allowObjectSerialisation));
				doc.append(e.getKey().toString(), arr);

			}
			return doc;
		}
		catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
		}

	}

	private static BsonValue _toBsonValue(byte[] barr) {
		return new BsonBinary(barr);
	}

	private static BsonValue _toBsonValue(boolean b) {
		return BsonBoolean.valueOf(b);
	}

	private static BsonValue _toBsonValue(double d) {
		return new BsonDouble(d);
	}

	private static BsonValue _toBsonValue(Date d) {
		return new BsonDateTime(d.getTime());
	}

	private static BsonValue _toBsonValue(int i) {
		return new BsonInt32(i);
	}

	private static BsonValue _toBsonValue(long l) {
		return new BsonInt64(l);
	}

	private static BsonValue _toBsonValue(BigDecimal bd) {
		return new BsonDecimal128(new Decimal128(bd));
	}

	private static BsonValue _toBsonValue(String str) {
		return new BsonString(str);
	}

	private static Object evaluate(ClassLoader cl, byte[] data) throws IOException {
		if (data == null) return null;

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
			throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(cnfe);
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

	private static byte[] serialize(Serializable value) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream(); // returns
		ObjectOutputStream oos = new ObjectOutputStream(os);
		oos.writeObject(value);
		oos.flush();
		return os.toByteArray();
	}

	private static void log(Exception e) {
		Config c = CFMLEngineFactory.getInstance().getThreadConfig();
		if (c != null) {
			Log log = c.getLog("application");
			if (log != null) {
				log.error("redis", e);
				return;
			}
		}
		e.printStackTrace();
	}

	private static class ObjectInputStreamImpl extends ObjectInputStream {

		private ClassLoader cl;

		public ObjectInputStreamImpl(ClassLoader cl, InputStream in) throws IOException {
			super(in);
			this.cl = cl;
		}

		@Override
		protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
			if (cl == null) return super.resolveClass(desc);

			String name = desc.getName();
			try {
				return Class.forName(name, false, cl);
			}
			catch (ClassNotFoundException ex) {
				return super.resolveClass(desc);
			}
		}

	}

}
