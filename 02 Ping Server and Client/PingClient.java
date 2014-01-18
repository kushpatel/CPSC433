import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class PingClient {

	private static final int SOCKET_TIMEOUT = 1000;
	private static final int PING_REQUEST_DELAY = 1000;
	
	private static String password;
	
	private static ArrayList<Long> roundTripTimes = new ArrayList<Long>();
	
	public static void main (String[] args) {

		if (args.length < 3) {
			System.out.println("Usage: java PingClient host port passwd");
			return;
		}

		String serverHost = args[0];
		int serverPort = Integer.parseInt(args[1]);
		password = args[2];
		InetAddress serverAddress;
		
		try {
			
			// Get server's IP address from the host name provided at command line
			serverAddress = InetAddress.getByName(serverHost);

			// Create client socket with a timeout on receive requests
			DatagramSocket clientSocket = new DatagramSocket();
			clientSocket.setSoTimeout(SOCKET_TIMEOUT);

			// Start a timer task that sends 10 requests periodically to the server
			Timer timer = new Timer();
			PingRequestTask timedPingRequests = new PingRequestTask(timer, password, clientSocket, serverAddress, serverPort);
			timer.scheduleAtFixedRate(timedPingRequests, 0, PING_REQUEST_DELAY);
			
		} catch (UnknownHostException e) {
			System.out.println("Unknown host: " + e.getStackTrace());
		} catch (SocketException e) {
			System.out.println("Socket Exception: " + e.getStackTrace());
		}
	}

	private static void sendPingRequest(int seqNo, byte[] sendData, DatagramSocket clientSocket, 
										InetAddress serverAddress, int serverPort) {

		// Construct and send datagram from client's socket
		try {
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
			clientSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.println("IOException in sending packet: " + e.getStackTrace());
		}

		// Create a datagram packet to hold incoming UDP packet.
		DatagramPacket request = new DatagramPacket(new byte[1024], 1024);

		// Waits for a UDP packet receipt or timeout
		try {
			clientSocket.receive(request);

			// Get data received in a byte array
			byte[] receivedData = readReceivedDataBytes(request);

			// Validate received data
			if (!isReceivedDataValid(receivedData, seqNo)) {
				System.out.println("ERROR: Incorrect ping echo message format.");
				return;
			}
			
			long roundTripTime = getRoundTripTime(receivedData);
			if (roundTripTime > 0) {
				roundTripTimes.add(roundTripTime);
			}
			
			// Print the received data, for debugging
			printData(receivedData);

		} catch (SocketTimeoutException e) {
			System.out.println("Socket timeout: Server did not respond.");
		} catch (IOException e) {
			System.out.println("IOException in receiving data: " + e.getStackTrace());
		}
	}
	
	private static long getRoundTripTime(byte[] receivedData) {	
		
		// Print the time stamp from the following eight bytes
		long currentTime = System.currentTimeMillis();
		// 'PINGECHO' is 8 bytes and seqNo is 2 bytes before the time stamp
		int idx = 8 + 2;
		long timestamp = 0;
		for (int i = 0; i < 8; i++, idx++) {
			// Need to mask the bytes into a long first before shifting
			timestamp += ((long) receivedData[idx] & 0xFFL) << (i * 8);
		}
		
		return currentTime - timestamp;
	}

	private static byte[] createByteData(int seqNo, String password) {

		// Use a byte stream to store bytes
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

		// Append 'PING' bytes to the buffer (4 bytes)
		byteBuffer.write((byte) 'P');
		byteBuffer.write((byte) 'I');
		byteBuffer.write((byte) 'N');
		byteBuffer.write((byte) 'G');

		// Append seqNo with least significant bytes first (2 bytes)
		byteBuffer.write((byte) ((seqNo >> 0) & 0xFF));
		byteBuffer.write((byte) ((seqNo >> 8) & 0xFF));

		// Append timestamp with least significant bytes first (8 bytes)
		long timestamp = System.currentTimeMillis();
		for (int i = 0; i < 8; i++) {
			byteBuffer.write((byte) (timestamp >> (i * 8)) & 0xFF);
		}

		// Append password provided by user (unknown # of bytes)
		for (int i = 0; i < password.length(); i++) {
			byteBuffer.write((byte) password.charAt(i));
		}

		// Append CR and LF at the end
		byteBuffer.write('\r');
		byteBuffer.write('\n');

		return byteBuffer.toByteArray();
	}

	private static byte[] readReceivedDataBytes(DatagramPacket request) {

		// Obtain references to the packet's array of bytes.
		byte[] buf = request.getData();

		// Wrap the bytes in a byte array input stream,
		// so that you can read the data as a stream of bytes.
		ByteArrayInputStream bais = new ByteArrayInputStream(buf);

		// Read in bytes from buffer to byte output stream till CR
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		byte byteRead = (byte) bais.read();
		while (byteRead != '\r') {
			baos.write(byteRead);
			byteRead = (byte) bais.read();
		}

		return baos.toByteArray();
	}

	private static boolean isReceivedDataValid(byte[] receivedData, int seqNo) {

		// 'PINGECHO' is 8 bytes, seqNo is 2 bytes, time stamp is 8 bytes followed by password
		int size = 8 + 2 + 8 + password.length();
		if (receivedData.length != size) return false;

		// The first eight bytes should be 'PINGECHO'
		if (receivedData[0] != (byte) 'P') return false;
		if (receivedData[1] != (byte) 'I') return false;
		if (receivedData[2] != (byte) 'N') return false;
		if (receivedData[3] != (byte) 'G') return false;
		if (receivedData[4] != (byte) 'E') return false;
		if (receivedData[5] != (byte) 'C') return false;
		if (receivedData[6] != (byte) 'H') return false;
		if (receivedData[7] != (byte) 'O') return false;
		
		// The last bytes should match password
		for (int i = 0; i < password.length(); i++) {
			if (receivedData[receivedData.length - 1 - i] != 
					(byte) password.charAt(password.length() - 1 - i)) {
				return false;
			}
		}
		
		// seqNo (9th and 10th bytes) should also be as expected
		int receivedSeqNo = receivedData[8] | receivedData[9] << 8;
		if (receivedSeqNo != seqNo) return false;

		return true;		
	}

	/* 
	 * Print ping echo data to the standard output stream.
	 */
	private static void printData(byte[] receivedData) {

		// Print the first eight bytes as char .. should be 'PINGECHO'
		int idx = 0;
		for (idx = 0; idx < 8; idx++) {
			System.out.print((char) receivedData[idx]);
		}

		// Print the seqNo from the following two bytes
		int seqNo = receivedData[idx++] | receivedData[idx++] << 8;
		System.out.print(" " + seqNo);

		// Print the timestamp from the following eight bytes
		long timestamp = 0;
		for (int i = 0; i < 8; i++, idx++) {
			// Need to mask the bytes into a long first before shifting
			timestamp += ((long) receivedData[idx] & 0xFFL) << (i * 8);
		}
		System.out.print(" " + timestamp + " ");

		// Print the rest of the bytes (password) as char
		for (;idx < receivedData.length; idx++) {
			System.out.print((char) receivedData[idx]);
		}

		System.out.println();

	}
	
	private static void printRoundTripStatistics() {
		
		long minRTT = Long.MAX_VALUE;
		long maxRTT = Long.MIN_VALUE;
		long totalRTT = 0;
		for (int i = 0; i < roundTripTimes.size(); i++) {
			if (roundTripTimes.get(i) < minRTT) {
				minRTT = roundTripTimes.get(i);
			}
			if (roundTripTimes.get(i) > maxRTT) {
				maxRTT = roundTripTimes.get(i);
			}
			totalRTT += roundTripTimes.get(i);
		}
		
		if (roundTripTimes.size() > 0) {
			System.out.println("Round Trip Statistics:");
			System.out.println("Min: " + minRTT);
			System.out.println("Max: " + maxRTT);
			System.out.println("Average: " + totalRTT/roundTripTimes.size());
			System.out.println("Loss rate: " + (10 - roundTripTimes.size())*10 + "%");
		}
	}

	private static class PingRequestTask extends TimerTask {

		private Timer timer;
		private String password;
		private DatagramSocket clientSocket;
		private InetAddress serverAddress;
		private int serverPort;
		private int seqNo;

		public PingRequestTask(Timer timer, String password, DatagramSocket clientSocket, 
				InetAddress serverAddress, int serverPort) {
			this.timer = timer;
			this.password = password;
			this.clientSocket = clientSocket;
			this.serverAddress = serverAddress;
			this.serverPort = serverPort;
			seqNo = 0;
		}

		@Override
		public void run() {

			// Create a byte array containing data to be sent
			byte[] sendData = createByteData(seqNo, password);

			// Send ping request to server
			sendPingRequest(seqNo, sendData, clientSocket, serverAddress, serverPort);
			seqNo++;

			// Stop this timer task when 10 ping requests have been sent
			if (seqNo >= 10) {
				this.cancel();
				clientSocket.close();
				timer.cancel();
				
				// Print out the min, max and average round trip time statistics
				printRoundTripStatistics();
			}
		}

	}
}
