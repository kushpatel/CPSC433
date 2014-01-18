import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SequentialServer {

	private static final String serverName = "SequentialServer";
	
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

				// Process the request sequentially
				shttpServer.serveRequest(connectionSocket);

			} catch (IOException e) {
				System.err.println("IOException in accepting connection: " + e.getMessage());
			}
		}

	}

}
