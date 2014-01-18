import java.nio.channels.SocketChannel;

public class AsyncServerReadWriteHandlerFactory 
implements ISocketReadWriteHandlerFactory {
	public IReadWriteHandler createHandler(Dispatcher d, SocketChannel client, SHTTPServer shttpServer) {
		return new AsyncServerReadWriteHandler(d, client, shttpServer);
	}
}
