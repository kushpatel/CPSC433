import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class PerRequestThreadServer {

	public static final String serverName = "PerRequestThreadServer";
	
	public static void main(String[] args) {

		// Setup SHTTP server
		SHTTPServer shttpServer = new SHTTPServer(serverName, args);
		ServerSocket serverSocket = shttpServer.getServerSocket();
		if (serverSocket == null) return;

		while (true){
			try {
				// accept connection from connection queue
				Socket connectionSocket = serverSocket.accept();
				//System.out.println("accepted connection from " + connectionSocket);

				// Create a thread that will serve the request in back ground
				ServeRequestThread requestThread = new ServeRequestThread(shttpServer, connectionSocket);
				requestThread.start();

			} catch (IOException e) {
				System.err.println("IOException in accepting connection: " + e.getMessage());
			}
		}

	}


	private static class ServeRequestThread extends Thread {

		private Socket connectionSocket;
		private SHTTPServer shttpServer;

		public ServeRequestThread(SHTTPServer shttpServer, Socket connectionSocket) {

			this.connectionSocket = connectionSocket;
			this.shttpServer = shttpServer;
		}

		@Override
		public void run() {

			shttpServer.serveRequest(connectionSocket);
		}

	}
}
