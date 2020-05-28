package lucee.extension.io.cache.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

public class ObjectInputStreamImpl extends ObjectInputStream {

	private ClassLoader cl;

	public ObjectInputStreamImpl(ClassLoader cl, InputStream in) throws IOException {
		super(in);
		this.cl = cl;
	}

	@Override
	protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
		if (cl == null)
			return super.resolveClass(desc);

		String name = desc.getName();
		try {
			return Class.forName(name, false, cl);
		} catch (ClassNotFoundException ex) {
			return super.resolveClass(desc);
		}
	}

}
