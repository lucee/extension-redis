package lucee.extension.io.cache.redis;

import java.io.IOException;

public interface Command {
	public Object command(byte[]... arguments) throws IOException;
}
