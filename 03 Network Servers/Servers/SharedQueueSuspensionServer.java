import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class SharedQueueSuspensionServer {

	public static final String serverName = "SharedQueueSuspensionServer";
	
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
					connSocketsList.notifyAll();
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
				Socket connectionSocket;
				synchronized (connSocketsList) {         
					while (connSocketsList.isEmpty()) {
						try {
							//System.out.println("Thread " + this + " sees empty pool.");
							connSocketsList.wait();
						}
						catch (InterruptedException ex) {
							System.err.println("Waiting for pool interrupted.");
						}
					}
					connectionSocket = connSocketsList.remove(0);
					//System.out.println("accepted connection from " + connectionSocket);
				}
				// Process the request sequentially
				shttpServer.serveRequest(connectionSocket);
			}
		}
	}
}
