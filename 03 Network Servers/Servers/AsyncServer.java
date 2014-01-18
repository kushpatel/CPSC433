import java.nio.channels.*;
import java.net.*;
import java.io.IOException;

public class AsyncServer {

	public static int DEFAULT_PORT = 6789;
	public static int WAIT_TIMEOUT = 3;	// 3 s
	
	private static final String serverName = "AsyncServer";

	public static ServerSocketChannel openServerChannel(int port)  {
		ServerSocketChannel serverChannel=null;
		try {

			// open server socket for accept
			serverChannel = ServerSocketChannel.open();

			// extract server socket of the server channel and bind the port
			ServerSocket ss = serverChannel.socket();
			InetSocketAddress address = new InetSocketAddress(port);
			ss.bind(address);

			// configure it to be non blocking
			serverChannel.configureBlocking(false);

			System.out.println("Server started; listening at " + port);

		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);   
		} // end of catch

		return serverChannel;	
	} // end of open serverChannel

	public static void main(String[] args) {

		// get dispatcher/selector
		Dispatcher dispatcher = new Dispatcher();
		
		SHTTPServer shttpServer = new SHTTPServer(serverName, args);
		
		// open server socket channel
		int port = shttpServer.getServerPort();
		ServerSocketChannel sch = openServerChannel(port);

		// create server acceptor for AsyncServer ReadWrite Handler
		ISocketReadWriteHandlerFactory echoFactory = 
				new AsyncServerReadWriteHandlerFactory();
		Acceptor acceptor = new Acceptor(sch, dispatcher, echoFactory, shttpServer);

		Thread dispatcherThread;
		// register the server channel to a selector	
		try {
			SelectionKey key = dispatcher.registerNewSelection(sch, 
					acceptor, 
					SelectionKey.OP_ACCEPT);

			// Dummy print to remove warnings of unused variable
			Debug.DEBUG(key.toString());
			
			// start dispatcher
			dispatcherThread = new Thread(dispatcher);
			dispatcherThread.start();
		} catch (IOException ex) {
			System.out.println("Cannot register and start server");
			System.exit(1);
		}
		// may need to join the dispatcher thread

	} // end of main

} // end of class
