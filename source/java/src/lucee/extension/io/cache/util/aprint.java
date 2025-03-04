package lucee.extension.io.cache.util;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import lucee.loader.engine.CFMLEngineFactory;

/**
 *  
 */
public class aprint {

	public static void date(String value) {
		long millis = System.currentTimeMillis();
		o(new Date(millis) + "-" + (millis - (millis / 1000 * 1000)) + " " + value);
	}

	public static void ds(boolean useOutStream) {
		new Exception("Stack trace").printStackTrace(useOutStream ? System.out : System.err);
	}

	public static void ds(Object label, boolean useOutStream) {
		_eo(useOutStream ? System.out : System.err, label);
		ds(useOutStream);
	}

	public static void ds() {
		ds(false);
	}

	public static void ds(Object label) {
		ds(label, false);
	}

	public static void dumpStack() {
		ds(false);
	}

	public static void dumpStack(boolean useOutStream) {
		ds(useOutStream);
	}

	public static void dumpStack(String label) {
		ds(label, false);
	}

	public static void dumpStack(String label, boolean useOutStream) {
		ds(label, useOutStream);
	}

	public static void err(boolean o) {
		System.err.println(o);
	}

	public static void err(double d) {
		System.err.println(d);
	}

	public static void err(long d) {
		System.err.println(d);
	}

	public static void err(float d) {
		System.err.println(d);
	}

	public static void err(int d) {
		System.err.println(d);
	}

	public static void err(short d) {
		System.err.println(d);
	}

	public static void out(Object o1, Object o2, Object o3) {
		System.out.print(o1);
		System.out.print(o2);
		System.out.println(o3);
	}

	public static void out(Object o1, Object o2) {
		System.out.print(o1);
		System.out.println(o2);
	}

	public static void out(Object o, long l) {
		System.out.print(o);
		System.out.println(l);
	}

	public static void out(Object o, double d) {
		System.out.print(o);
		System.out.println(d);
	}

	public static void out(byte[] arr, int offset, int len) {
		System.out.print("byte[]{");
		for (int i = offset; i < len + offset; i++) {
			if (i > 0) System.out.print(',');
			System.out.print(arr[i]);
		}
		System.out.println("}");
	}

	public static void out(double o) {
		System.out.println(o);
	}

	public static void out(float o) {
		System.out.println(o);
	}

	public static void out(long o) {
		System.out.println(o);
	}

	public static void out(int o) {
		System.out.println(o);
	}

	public static void out(char o) {
		System.out.println(o);
	}

	public static void out(boolean o) {
		System.out.println(o);
	}

	public static void out() {
		System.out.println();
	}

	public static void printST(Throwable t) {
		if (t instanceof InvocationTargetException) {
			t = ((InvocationTargetException) t).getTargetException();
		}
		err(t.getClass().getName());
		t.printStackTrace();

	}

	public static void printST(Throwable t, PrintStream ps) {
		if (t instanceof InvocationTargetException) {
			t = ((InvocationTargetException) t).getTargetException();
		}
		err(t.getClass().getName());
		t.printStackTrace(ps);

	}

	public static void out(Object o) {
		_eo(System.out, o);
	}

	public static void err(Object o) {
		_eo(System.err, o);
	}

	public static void o(Object o) {
		_eo(System.out, o);
	}

	public static void e(Object o) {
		_eo(System.err, o);
	}

	public static void oe(Object o, boolean valid) {
		_eo(valid ? System.out : System.err, o);
	}

	public static void dateO(String value) {
		_date(System.out, value);
	}

	public static void dateE(String value) {
		_date(System.err, value);
	}

	private static void _date(PrintStream ps, String value) {
		long millis = System.currentTimeMillis();
		_eo(ps, new Date(millis) + "-" + (millis - (millis / 1000 * 1000)) + " " + value);
	}

	private static void _eo(PrintStream ps, Object o) {
		if (o instanceof Enumeration) _eo(ps, (Enumeration) o);
		else if (o instanceof Object[]) _eo(ps, (Object[]) o);
		else if (o instanceof boolean[]) _eo(ps, (boolean[]) o);
		else if (o instanceof byte[]) _eo(ps, (byte[]) o);
		else if (o instanceof int[]) _eo(ps, (int[]) o);
		else if (o instanceof float[]) _eo(ps, (float[]) o);
		else if (o instanceof long[]) _eo(ps, (long[]) o);
		else if (o instanceof double[]) _eo(ps, (double[]) o);
		else if (o instanceof char[]) _eo(ps, (char[]) o);
		else if (o instanceof short[]) _eo(ps, (short[]) o);
		else if (o instanceof Set) _eo(ps, (Set) o);
		else if (o instanceof List) _eo(ps, (List) o);
		else if (o instanceof Map) _eo(ps, (Map) o);
		else if (o instanceof Iterator) _eo(ps, (Iterator) o);
		else if (o instanceof NamedNodeMap) _eo(ps, (NamedNodeMap) o);
		else if (o instanceof Node) _eo(ps, (Node) o);
		else if (o instanceof Throwable) _eo(ps, (Throwable) o);

		else ps.println(o);
	}

	private static void _eo(PrintStream ps, Object[] arr) {
		if (arr == null) {
			ps.println("null");
			return;
		}
		ps.print(arr.getClass().getComponentType().getName() + "[]{");
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) {
				ps.print("\t,");
			}
			_eo(ps, arr[i]);
		}
		ps.println("}");
	}

	private static void _eo(PrintStream ps, int[] arr) {
		ps.print("int[]{");
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) ps.print(',');
			ps.print(arr[i]);
		}
		ps.println("}");
	}

	private static void _eo(PrintStream ps, byte[] arr) {
		ps.print("byte[]{");
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) ps.print(',');
			ps.print(arr[i]);
		}
		ps.println("}");
	}

	private static void _eo(PrintStream ps, boolean[] arr) {
		ps.print("boolean[]{");
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) ps.print(',');
			ps.print(arr[i]);
		}
		ps.println("}");
	}

	private static void _eo(PrintStream ps, char[] arr) {
		ps.print("char[]{");
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) ps.print(',');
			ps.print(arr[i]);
		}
		ps.println("}");
	}

	private static void _eo(PrintStream ps, short[] arr) {
		ps.print("short[]{");
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) ps.print(',');
			ps.print(arr[i]);
		}
		ps.println("}");
	}

	private static void _eo(PrintStream ps, float[] arr) {
		ps.print("float[]{");
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) ps.print(',');
			ps.print(arr[i]);
		}
		ps.println("}");
	}

	private static void _eo(PrintStream ps, long[] arr) {
		ps.print("long[]{");
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) ps.print(',');
			ps.print(arr[i]);
		}
		ps.println("}");
	}

	private static void _eo(PrintStream ps, double[] arr) {
		ps.print("double[]{");
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) ps.print(',');
			ps.print(arr[i]);
		}
		ps.println("}");
	}

	private static void _eo(PrintStream ps, Node n) {
		ps.print(CFMLEngineFactory.getInstance().getCastUtil().toString(n, null));
	}

	private static void _eo(PrintStream ps, Throwable t) {
		t.printStackTrace(ps);
	}

	private static void _eo(PrintStream ps, Enumeration en) {

		_eo(ps, en.getClass().getName() + " [");
		while (en.hasMoreElements()) {
			ps.print(en.nextElement());
			ps.println(",");
		}
		_eo(ps, "]");
	}

	private static void _eo(PrintStream ps, List list) {
		ListIterator it = list.listIterator();
		_eo(ps, list.getClass().getName() + " {");
		while (it.hasNext()) {
			int index = it.nextIndex();
			it.next();
			ps.print(index);
			ps.print(":");
			ps.print(list.get(index));
			ps.println(";");
		}
		_eo(ps, "}");
	}

	private static void _eo(PrintStream ps, Iterator it) {

		_eo(ps, it.getClass().getName() + " {");
		while (it.hasNext()) {
			ps.print(it.next());
			ps.println(";");
		}
		_eo(ps, "}");
	}

	private static void _eo(PrintStream ps, Set set) {
		Iterator it = set.iterator();
		ps.println(set.getClass().getName() + " {");
		while (it.hasNext()) {
			_eo(ps, it.next());
			ps.println(",");
		}
		_eo(ps, "}");
	}

	private static void _eo(PrintStream ps, Map map) {
		if (map == null) {
			ps.println("null");
			return;
		}
		Iterator it = map.keySet().iterator();

		if (map.size() < 2) {
			ps.print(map.getClass().getName() + " {");
			while (it.hasNext()) {
				Object key = it.next();

				ps.print(key);
				ps.print(":");
				ps.print(map.get(key));
			}
			ps.println("}");
		}
		else {
			ps.println(map.getClass().getName() + " {");
			while (it.hasNext()) {
				Object key = it.next();
				ps.print("	");
				ps.print(key);
				ps.print(":");
				ps.print(map.get(key));
				ps.println(";");
			}
			ps.println("}");
		}
	}

	private static void _eo(PrintStream ps, NamedNodeMap map) {
		if (map == null) {
			ps.println("null");
			return;
		}
		int len = map.getLength();
		ps.print(map.getClass().getName() + " {");
		Attr attr;
		for (int i = 0; i < len; i++) {
			attr = (Attr) map.item(i);

			ps.print(attr.getName());
			ps.print(":");
			ps.print(attr.getValue());
			ps.println(";");
		}
		ps.println("}");
	}

}