import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class CompetingThreadsServer {
	
	private static final String serverName = "CompetingThreadsServer";

	public static void main(String[] args) {

		// Setup SHTTP server
		SHTTPServer shttpServer = new SHTTPServer(serverName, args);
		int numThreads = shttpServer.getNumThreads();
		ServerSocket serverSocket = shttpServer.getServerSocket();
		if (serverSocket == null) return;
		
		// Start all the threads to compete over requests received at server socket
		for (int i = 0; i < numThreads; i++) {
			
			ServeMultipleRequests multipleRequestsThread = new ServeMultipleRequests(shttpServer);
			multipleRequestsThread.start();
		}

	}

	private static class ServeMultipleRequests extends Thread {

		private SHTTPServer shttpServer;
		private ServerSocket serverSocket;

		public ServeMultipleRequests(SHTTPServer shttpServer) {

			this.shttpServer = shttpServer;
			serverSocket = shttpServer.getServerSocket();
		}

		@Override
		public void run() {
			while (true) {
				try {
					// accept connection from connection queue
					Socket connectionSocket;
					synchronized (serverSocket) {
						connectionSocket = serverSocket.accept();
					}
					//System.out.println("accepted connection from " + connectionSocket);

					// Process the request
					shttpServer.serveRequest(connectionSocket);

				} catch (IOException e) {
					System.err.println("IOException in accepting connection: " + e.getMessage());
				}
			}
		}
	}

}
