import java.nio.channels.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

public class Acceptor implements IAcceptHandler {

	private Dispatcher dispatcher;
	private ServerSocketChannel server;
	private ISocketReadWriteHandlerFactory srwf;
	private SHTTPServer shttpServer;
	
	ScheduledExecutorService scheduledExecutorService;

	public Acceptor(ServerSocketChannel server, 
			Dispatcher d, 
			ISocketReadWriteHandlerFactory srwf,
			SHTTPServer shttpServer) {
		this.dispatcher = d;
		this.server = server;
		this.srwf = srwf;
		this.shttpServer = shttpServer;
		
		scheduledExecutorService = Executors.newScheduledThreadPool(1);
	}

	public void handleException() {
		System.out.println("handleException(): of Acceptor");
	}

	public void handleAccept(SelectionKey key) throws IOException {
		// ServerSocketChannel server = (ServerSocketChannel ) key.channel();
		// ASSERT: this.server == server

		// extract the ready connection
		SocketChannel client = server.accept();
		Debug.DEBUG("handleAccept: Accepted connection from " + client);

		// configure the connection to be non-blocking
		client.configureBlocking(false);

		/* register the new connection with *read* events/operations
	   SelectionKey clientKey = 
	   client.register(
	   selector, SelectionKey.OP_READ);// | SelectionKey.OP_WRITE);
		 */

		AsyncServerReadWriteHandler rwH = (AsyncServerReadWriteHandler) srwf.createHandler(dispatcher, client, shttpServer);
		int ops = rwH.getInitOps();

		SelectionKey clientKey = dispatcher.registerNewSelection(client, rwH, ops);

		// Start timeout scheduler in the scheduled executor service
		TimeoutScheduler timeoutScheduler = new TimeoutScheduler(dispatcher, client);
		ScheduledFuture<?> scheduledFuture = scheduledExecutorService.schedule(timeoutScheduler, AsyncServer.WAIT_TIMEOUT, TimeUnit.SECONDS);
		rwH.setScheduledFuture(scheduledFuture);

		// Dummy print to remove warnings of unused variable
		Debug.DEBUG(clientKey.toString());
	} // end of handleAccept

} // end of class