import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

/* Class that extends TimerTask for running a client thread that executes requests till stopTime specified
	 ====================================================================================================================*/

public class ClientRequestThread extends Thread {

	public static final int CLIENT_TIMEOUT = 3000;		// 3 s

	private ArrayList<String> filesToRequest;
	private InetAddress serverAddress;
	private int serverPort;

	// Local thread statistics
	private static long threadTransactions = 0;
	private static long threadBytesReceived = 0;
	private static long threadWaitTime = 0;

	public ClientRequestThread(ArrayList<String> filesToRequest, 
			InetAddress serverAddress, int serverPort) {

		//this.stopTime = stopTime;
		this.filesToRequest = filesToRequest;
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
	}

	@Override
	public void run() {

		while (true) {
			try {
				for (int reqIdx = 0; reqIdx < filesToRequest.size(); reqIdx++) {
					if (this.isInterrupted()) throw new InterruptedException();
					sendRequest(filesToRequest.get(reqIdx), serverAddress, serverPort);
				}
			} catch (InterruptedException e) {
				SHTTPTestClient.gatherStatisticsFromThread(threadTransactions, threadWaitTime, threadBytesReceived);
				break;
			}
		}
	}

	private void sendRequest(String fileToRequest, InetAddress serverAddress, int serverPort) {

		try {

			// Create a socket that connects to the server
			Socket clientSocket = new Socket(serverAddress, serverPort);
			clientSocket.setSoTimeout(CLIENT_TIMEOUT);

			// Construct a new message to send in a byte array
			byte[] sendData = createRequestMessage(fileToRequest);

			// Write data to server output stream
			DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
			outToServer.write(sendData);
			long requestSendTime = System.currentTimeMillis();

			// Break from the loop if the thread is interrupted
			if (this.isInterrupted()) {
				clientSocket.close();
				return;
			}

			// create read stream and receive from server
			BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));	

			// Validate reply form server				
			String statusLine = inFromServer.readLine();
			if(!isServerReplyValid(statusLine)) {
				System.err.println("Invalid message from server.");
				clientSocket.close();
				return;
			}

			HashMap<String, String> receivedMessage = readInReceivedMessage(inFromServer);

			String dateLine = receivedMessage.get("Date");
			String serverLine = receivedMessage.get("Server");
			String contentTypeLine = receivedMessage.get("Content-Type");
			String contentLengthLine = receivedMessage.get("Content-Length");
			int headerNumBytes = statusLine.length() + dateLine.length() + serverLine.length() +
					contentTypeLine.length() + contentLengthLine.length();
			long replyReceiveTime = System.currentTimeMillis();

			// 1) Record transaction as complete
			threadTransactions++;

			// 2) Get round trip time of request
			int roundTripTime = (int) (replyReceiveTime - requestSendTime);
			threadWaitTime += roundTripTime;

			// 3) Record number of bytes received
			int numBytesInReplyFile = getNumBytesInReplyFile(inFromServer);
			int numBytesReceived = headerNumBytes + numBytesInReplyFile;
			threadBytesReceived += numBytesReceived;

			clientSocket.close();

		} catch (IOException e) {
			//System.err.println("IOException in sending request: " + e.getMessage());
		}
	}

	/* Creates a new GET request message to with the URL string provided
	 * Format : GET <URL> HTTP/1.0
	 * 			CRLF
	 */
	private byte[] createRequestMessage(String url) {

		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

		try {
			String get = "GET";
			String http = "HTTP/1.0";
			String crlf = "\r\n";
			byteBuffer.write(get.getBytes());
			byteBuffer.write((byte) ' ');
			byteBuffer.write(url.getBytes());
			byteBuffer.write((byte) ' ');
			byteBuffer.write(http.getBytes());
			byteBuffer.write(crlf.getBytes());
			byteBuffer.write(crlf.getBytes());
		} catch (Exception e) {
			System.err.println("Error in creating request message: " + e.getMessage());
		}

		return byteBuffer.toByteArray();
	}

	private boolean isServerReplyValid(String serverResponseLine) {

		// The first line has to be of the form: HTTP/1.0 <StatusCode> <message>
		if (serverResponseLine == null || serverResponseLine.isEmpty()) return false;
		//String[] splitResponse = serverResponseLine.split(" ");
		//if (splitResponse.length != 3) return false;
		//if (!splitResponse[0].equals("HTTP/1.0")) return false;	

		return true;
	}

	private HashMap<String, String> readInReceivedMessage(BufferedReader inFromServer) {
		/*  HTTP/1.0 <StatusCode> <message>  ... This was already read! Starts from next line
			Date: <date>
			Server: <your server name>
			Content-Type: text/html
			Content-Length: <LengthOfFile>
			CRLF
			<file content>
		 */
		HashMap<String, String> messageMap = new HashMap<String, String>();		
		try {
			String line = inFromServer.readLine();
			while (line != null && !line.equals("")) {
				String[] splitMessageField = line.split(": ");
				if (splitMessageField.length == 2) {
					messageMap.put(splitMessageField[0], splitMessageField[1]);
				}
				line = inFromServer.readLine();
			}
		} catch (IOException e) {
			System.err.println("IOException in reading reply message: " + e.getMessage());
		}

		return messageMap;		
	}

	private int getNumBytesInReplyFile(BufferedReader inFromServer) throws IOException {

		int fileLength = 0;
		while(inFromServer.ready()) {
			String line = inFromServer.readLine();
			//System.out.println(line);
			fileLength += line.length();
		}
		return fileLength;
	}

}