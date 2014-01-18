// PingServer.java
import java.io.*;
import java.net.*;
import java.util.*;

/* 
 * Server to process ping requests over UDP.
 */

public class PingServer
{
	private static double LOSS_RATE = 0.3;
	private static int AVERAGE_DELAY = 100; // milliseconds
	private static String password;

	public static void main(String[] args) throws Exception
	{
		// Get command line argument.
		if (args.length < 2) {
			System.out.println("Usage: java PingServer port passwd [-delay delay] [-loss loss]");
			return;
		}
		
		try {
			if (args.length == 4) {
				parseExtraArgs(args[2], args[3]);
			} else if (args.length == 6) {
				parseExtraArgs(args[2], args[3]);
				parseExtraArgs(args[4], args[5]);
			}
		} catch (Exception e) {
			System.out.println("Incorrect arguments, usage: java PingServer port passwd [-delay delay] [-loss loss]");
			return;
		}

		int port = Integer.parseInt(args[0]);
		password = args[1];

		// Create random number generator for use in simulating
		// packet loss and network delay.
		Random random = new Random();

		// Create a datagram socket for receiving and sending
		// UDP packets through the port specified on the
		// command line.
		DatagramSocket socket = new DatagramSocket(port);

		// Processing loop.
		while (true) {

			// Create a datagram packet to hold incoming UDP packet.
			DatagramPacket
			request = new DatagramPacket(new byte[1024], 1024);

			// Block until receives a UDP packet.
			socket.receive(request);

			// Get data received in a byte array
			byte[] receivedData = getReceivedDataBytes(request);
			
			// Validate the received data format
			if (!isReceivedDataValid(receivedData)) {
				System.out.println("ERROR: Incorrect ping message format.");
				continue;
			}
			
			// Print the received data, for debugging
			printData(receivedData);
			
			// Validate password in the ping request
			if (!doesPasswordMatch(receivedData)) {
				System.out.println("ERROR: Password validation failed.");
				continue;
			}

			// Decide whether to reply, or simulate packet loss.
			if (random.nextDouble() < LOSS_RATE) {
				System.out.println("Reply not sent.");
				continue;
			}

			// Simulate propagation delay.
			Thread.sleep((int) (random.nextDouble() * 2 * AVERAGE_DELAY));

			// Send reply.
			InetAddress clientHost = request.getAddress();
			int clientPort = request.getPort();	
			// Create echo response data
			byte[] responseData = getEchoByteData(receivedData);
			DatagramPacket reply = new DatagramPacket(responseData, responseData.length,
														clientHost, clientPort);

			socket.send(reply);

			System.out.println("Reply sent.");
		} // end of while
	} // end of main
	
	private static void parseExtraArgs(String argType, String argValue) throws Exception {
		if (argType.equals("-delay")) {
			AVERAGE_DELAY = Integer.parseInt(argValue);
		} else if (argType.equals("-loss")) {
			LOSS_RATE = Double.parseDouble(argValue);
		}
	}

	private static byte[] getReceivedDataBytes(DatagramPacket request) {

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
	
	private static boolean isReceivedDataValid(byte[] receivedData) {
		
		// 'PING' is 4 bytes, seqNo is 2 bytes, time stamp is 8 bytes followed by password
		int size = 4 + 2 + 8 + password.length();
		if (receivedData.length != size) return false;
		
		// The first four bytes should be 'PING'
		if (receivedData[0] != (byte) 'P') return false;
		if (receivedData[1] != (byte) 'I') return false;
		if (receivedData[2] != (byte) 'N') return false;
		if (receivedData[3] != (byte) 'G') return false;
		
		return true;		
	}
	
	private static byte[] getEchoByteData(byte[] receivedData) {
		
		// Need to stick in four extra bytes for 'ECHO' and two for CRLF at end
		int dataLength = receivedData.length + 6;
		byte[] responseData = new byte[dataLength];
		
		// Copy 'PING' from receivedData
		for (int i = 0; i < 4; i++) {
			responseData[i] = receivedData[i];
		}

		// Stick in 'ECHO' bytes
		responseData[4] = (byte) 'E';
		responseData[5] = (byte) 'C';
		responseData[6] = (byte) 'H';
		responseData[7] = (byte) 'O';
		
		// Copy the rest of receivedData
		for (int i = 4; i < receivedData.length; i++) {
			responseData[i+4] = receivedData[i];
		}
		
		// Append CRLF at the end
		responseData[dataLength - 2] = (byte) '\r';
		responseData[dataLength - 1] = (byte) '\n';
		
		return responseData;
	}
	
	private static boolean doesPasswordMatch(byte[] receivedData) {
		
		String receivedPassword = "";
		// 'PING' is 4 bytes, seqNo is 2 bytes, time stamp is 8 bytes followed by password
		int pwStartIdx = 4 + 2 + 8;
		for (int i = pwStartIdx; i < receivedData.length; i++) {
			receivedPassword += (char) receivedData[i];
		}
		
		return receivedPassword.equals(password);
	}

	/* 
	 * Print ping data to the standard output stream.
	 */
	private static void printData(byte[] receivedData) {
		
		// Print the first four bytes as char .. should be 'PING'
		int idx = 0;
		for (idx = 0; idx < 4; idx++) {
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
}

