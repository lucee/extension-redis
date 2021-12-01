package lucee.extension.io.cache.redis;

import java.io.IOException;
import java.util.List;

public interface Command {

	public Object command(byte[][] arguments, boolean lowPrio) throws IOException;

	public List<Object> command(List<byte[][]> arguments, boolean lowPrio) throws IOException;

}
