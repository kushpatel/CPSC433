import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class SharedQueueBusyWaitServer {

	private static final String serverName = "SharedQueueBusyWaitServer";
	
	public static void main(String[] args) {

		// Setup SHTTP server
		SHTTPServer shttpServer = new SHTTPServer(serverName, args);
		int numThreads = shttpServer.getNumThreads();
		ServerSocket serverSocket = shttpServer.getServerSocket();
		if (serverSocket == null) return;

		// Start all the threads that will share the Sockets queue
		ArrayList<Socket> connSocketsList = new ArrayList<Socket>();
		for (int i = 0; i < numThreads; i++) {
			SharedQueueThread multipleRequestsThread = new SharedQueueThread(shttpServer, connSocketsList);
			multipleRequestsThread.start();
		}

		while (true) {
			try {
				// accept connection from connection queue
				Socket connectionSocket = serverSocket.accept();
				//System.out.println("accepted connection from " + connectionSocket);

				synchronized (connSocketsList) {
					connSocketsList.add(connectionSocket);
				}

			} catch (IOException e) {
				System.err.println("IOException in accepting connection: " + e.getMessage());
			}
		}

	}

	private static class SharedQueueThread extends Thread {

		private ArrayList<Socket> connSocketsList;
		private SHTTPServer shttpServer;

		public SharedQueueThread(SHTTPServer shttpServer, ArrayList<Socket> connSocketsList) {

			this.shttpServer = shttpServer;
			this.connSocketsList = connSocketsList;
		}

		@Override
		public void run() {
			while (true) {
				// accept connection from connection queue
				Socket connectionSocket;
				synchronized (connSocketsList) {
					if (!connSocketsList.isEmpty()) {
						connectionSocket = connSocketsList.remove(0);
						//System.out.println("accepted connection from " + connectionSocket);

						// Process the request
						shttpServer.serveRequest(connectionSocket);
					}
				}
			}
		}
	}

}
