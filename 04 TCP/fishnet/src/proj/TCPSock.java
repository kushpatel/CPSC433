package proj;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;

import lib.*;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

public class TCPSock {

	private int addr;
	private int localPort;
	private Manager manager;
	private TCPManager tcpMan;
	private final long retransmitTimeout = 1000; // 1s
	private final long shutdownTimeout = 1000;	// 1s

	private TCPSockBuffer sockBuffer;
	private int BUFFER_SIZE = 16000;	// ~16kB

	private int seqNo = 0;	// Can't be negative!
	private int ackNo = -1;
	private int finNo = -2;	// FIN flag .. terminate connection when finNo == ackNo

	// Server specific variables
	private int backlog;
	private LinkedList<TCPSock> requestQueue;

	// Client specific variables 
	private int rwnd;		// Receiver's window size
	// Also used for an active server connection
	private int connAddr;
	private int connPort;

	// TCP socket states
	enum State {
		// protocol states
		CLOSED,
		LISTEN,
		SYN_SENT,
		ESTABLISHED,
		SHUTDOWN // close requested, FIN not sent (due to unsent data in queue)
	}
	private State state;

	// Congestion control variables
	private final int DEFAULT_CONGESTION_WINDOW = Transport.MAX_PAYLOAD_SIZE;	// Default Congestion window size
	private final double ALPHA = 0.125;
	private final double BETA = 0.25;
	private double estimatedRTT = 0;
	private double devRTT = 0;
	private long timeoutInterval = 1000;	// starts at 1s
	private long samplePktTimestamp = -1;
	private int expectedSampleAckNo = -1;
	private int winEndSeqNo = Integer.MAX_VALUE;
	// FSM for TCP Congestion control as described in section 3.7 of Computer Networking - Kurose | Ross
	enum CongestionState {
		SLOW_START,
		CONGESTION_AVOIDANCE,
		FAST_RECOVERY
	}
	private CongestionState congState;
	private int dupAckCount = 0;
	private int DUP_ACK_THRESHOLD = 3;
	private int ssthresh = BUFFER_SIZE;


	/*
	 * Constructors
	 ==================================================================================================================*/

	public TCPSock(Manager manager, TCPManager tcpMan) {
		this.manager = manager;
		this.tcpMan = tcpMan;
		this.addr = this.tcpMan.getLocalAddress();
		this.state = State.CLOSED;
		requestQueue = new LinkedList<TCPSock>();
		sockBuffer = new TCPSockBuffer(BUFFER_SIZE, DEFAULT_CONGESTION_WINDOW, seqNo);
		congState = CongestionState.SLOW_START;
	}

	// Handy constructor for creating connection sockets with a specific port
	public TCPSock(Manager manager, TCPManager tcpMan, int sockPort, int connAddr, int connPort, 
			State initState, int initAckNo) {
		this(manager, tcpMan);
		this.localPort = sockPort;
		this.connAddr = connAddr;
		this.connPort = connPort;
		this.state = initState;
		this.ackNo = initAckNo;
	}

	/*
	 * The following are the socket APIs of TCP transport service.
	 * All APIs are NON-BLOCKING.
	 ==================================================================================================================*/

	/**
	 * Bind a socket to a local port
	 *
	 * @param localPort int local port number to bind the socket to
	 * @return int 0 on success, -1 otherwise
	 */
	public int bind(int localPort) {
		if (tcpMan.canBindSocket(localPort)) {
			this.localPort = localPort;
			return 0;
		}
		return -1;
	}

	/**
	 * Listen for connections on a socket
	 * @param backlog int Maximum number of pending connections
	 * @return int 0 on success, -1 otherwise
	 */
	public int listen(int backlog) {
		if (state == State.CLOSED) {
			tcpMan.addListeningSock(this);
			state = State.LISTEN;
			this.backlog = backlog;
			return 0;
		}
		return -1;
	}

	/**
	 * Accept a connection on a socket
	 *
	 * @return TCPSock The first established connection on the request queue
	 */
	public TCPSock accept() {
		if (state == State.LISTEN && requestQueue.size() > 0) {
			// Get first connection from request queue
			return requestQueue.removeFirst();
		}
		return null;
	}

	public boolean isConnectionPending() {
		return (state == State.SYN_SENT);
	}

	public boolean isClosed() {
		return (state == State.CLOSED);
	}

	public boolean isConnected() {
		return (state == State.ESTABLISHED);
	}

	public boolean isClosurePending() {
		return (state == State.SHUTDOWN);
	}

	/**
	 * Initiate connection to a remote socket
	 *
	 * @param destAddr int Destination node address
	 * @param destPort int Destination port
	 * @return int 0 on success, -1 otherwise
	 */
	public int connect(int destAddr, int destPort) {
		sendSyn(destAddr, destPort);
		tcpMan.addConnection(this, destAddr, destPort);
		state = State.SYN_SENT;
		return 0;
	}

	/**
	 * Initiate closure of a connection (graceful shutdown)
	 */
	public void close() {
		// Set the finNo flag to indicate when to stop i.e. buffer empty
		state = State.SHUTDOWN;
		finNo = seqNo + sockBuffer.bufferSpaceUsed();
		addTimer(shutdownTimeout, "closeSock", null, null);
	}

	/**
	 * Release a connection immediately (abortive shutdown)
	 */
	public void release() {
		state = State.CLOSED;
		if (!tcpMan.isListeningPort(localPort)) tcpMan.unbindSocket(localPort);
		tcpMan.closeConnection(this, connAddr, connPort);
	}

	/**
	 * Write to the socket up to len bytes from the buffer buf starting at
	 * position pos.
	 *
	 * @param buf byte[] the buffer to write from
	 * @param pos int starting position in buffer
	 * @param len int number of bytes to write
	 * @return int on success, the number of bytes written, which may be smaller
	 *             than len; on failure, -1
	 */
	public int write(byte[] buf, int pos, int len) {
		if (state == State.ESTABLISHED) {
			int bytesWritten = sockBuffer.write(buf, pos, len);
			// Send all available data in buffer
			if (bytesWritten > 0) sendCurrentWindow(connAddr, connPort, seqNo);
			//sendNextDataPacket(connAddr, connPort, seqNo);
			return bytesWritten;
		}
		return 0;
	}

	/**
	 * Read from the socket up to len bytes into the buffer buf starting at
	 * position pos.
	 *
	 * @param buf byte[] the buffer
	 * @param pos int starting position in buffer
	 * @param len int number of bytes to read
	 * @return int on success, the number of bytes read, which may be smaller
	 *             than len; on failure, -1
	 */
	public int read(byte[] buf, int pos, int len) {
		return sockBuffer.read(buf, pos, len);
	}

	public int getSockPort() {
		return localPort;
	}

	/*
	 * Received packet handling
	 ==================================================================================================================*/

	// Handle packet accordingly depending on what type it is
	public void onPacketReceived(Packet packet) {		
		Transport segment = Transport.unpack(packet.getPayload());
		switch(segment.getType()) {

		case Transport.SYN:
			receivedSyn(packet);
			break;

		case Transport.ACK:
			receivedAck(packet);
			break;

		case Transport.DATA:
			receivedData(packet);
			break;

		case Transport.FIN:
			receivedFin(packet);
			break;
		}
	}

	private void receivedSyn(Packet request) {
		System.out.print("S");
		if (requestQueue.size() < backlog) {
			// Send an ACK immediately on receipt of a request
			int initAckNo = sendSynAck(request);

			// Create a connection socket and add it to the requests queue
			int connectionAddr = request.getSrc();
			int connectionPort = Transport.unpack(request.getPayload()).getSrcPort();
			TCPSock connectionSocket = new TCPSock(manager, tcpMan, localPort, 
					connectionAddr, connectionPort, State.ESTABLISHED, initAckNo);
			tcpMan.addConnection(connectionSocket, connectionAddr, connectionPort);			
			requestQueue.add(connectionSocket);
		}
	}

	private void receivedAck(Packet packet) {
		// ACK number with a value lower than seqNo is ignored
		Transport segment = Transport.unpack(packet.getPayload());
		if (segment.getSeqNum() < seqNo) {
			System.out.print("?");
			// Congestion control function for when a duplicate ACK is received
			duplicateAckReceived();
			return;
		}

		System.out.print(":");
		// Congestion control function for when a new ACK is received
		newAckReceived();

		// Need to update our seqNo, buffer base and receiver's window size first!
		seqNo = segment.getSeqNum() - 1;
		sockBuffer.updateBufferBase(seqNo+1);
		rwnd = segment.getWindow();

		// Need to appropriately compute sampleRTT if ACK for the sampled packet is received
		if (segment.getSeqNum() == expectedSampleAckNo) {
			updateTimeoutInterval();
		}

		// If ACK is response to a SYN, set state as ESTABLISHED and initialize receiver's window size
		if (state == State.SYN_SENT) {
			state = State.ESTABLISHED;
			connAddr = packet.getSrc();
			connPort = Transport.unpack(packet.getPayload()).getSrcPort();
		}

		// Sends the next window from buffer if available
		if (winEndSeqNo == seqNo && !sockBuffer.isBufferEmpty()) {
			sendCurrentWindow(connAddr, connPort, seqNo);
		}
	}

	private void receivedData(Packet packet) {
		Transport dataSegment = Transport.unpack(packet.getPayload());

		// Drop the packet and send old ACK if it arrived out of sequence
		if (ackNo != dataSegment.getSeqNum()) {
			System.out.print("!");
			sendDataAck(ackNo);
			return;
		}

		System.out.print(".");
		// Write received data to buffer
		int bytesWritten = sockBuffer.writeSegment(dataSegment);
		// Drop packet without sending ACK if it can't fit in the buffer
		if (bytesWritten == -1) return;

		// Update our ackNo
		ackNo += bytesWritten;

		// Send ACK
		sendDataAck(ackNo);
	}

	private void receivedFin(Packet packet) {
		System.out.print("F");
		// Initiate closure of the connection
		seqNo = finNo; 	// Hack so that closeSock callback function can be reused without modification
		state = State.SHUTDOWN;
		addTimer(shutdownTimeout, "closeSock", null, null);
	}


	/*
	 * Handling packet sending
	 ==================================================================================================================*/

	// Send a SYN package for initiating a connection
	private void sendSyn(int destAddr, int destPort) {
		int cwnd = sockBuffer.getCongestionWinSize();
		Transport synSegment = new Transport(localPort, destPort, Transport.SYN, cwnd, seqNo, new byte[0]);
		Packet synPacket = new Packet(destAddr, addr, Packet.MAX_TTL, Protocol.TRANSPORT_PKT, seqNo, synSegment.pack());
		tcpMan.sendNetworkPacket(synPacket);
		System.out.print("S");

		// Register callback on timeout for retransmission of SYN
		String[] paramTypes = {"java.lang.Integer", "java.lang.Integer"};
		Object[] params = {destAddr, destPort};
		addTimer(retransmitTimeout, "resendSyn", paramTypes, params);
	}

	// Send an acknowledgment for the received SYN packet
	// Returns the ACK number to which our ackNo should be set to
	private int sendSynAck(Packet receivedPkt) {
		Transport receivedSegment = Transport.unpack(receivedPkt.getPayload());
		int ourAckNo = receivedSegment.getSeqNum();
		int freeBufferSpace = sockBuffer.bufferSpaceAvailable();
		Transport ackSegment = new Transport(localPort, receivedSegment.getSrcPort(),
				Transport.ACK, freeBufferSpace, ourAckNo, new byte[0]);
		Packet ackPkt = new Packet(receivedPkt.getSrc(), addr, Packet.MAX_TTL, 
				Protocol.TRANSPORT_PKT, ourAckNo, ackSegment.pack());
		tcpMan.sendNetworkPacket(ackPkt);
		System.out.print(":");

		// Register callback on timeout for retransmission of SYN ACK
		String[] paramTypes = {"[B"};
		Object[] params = {receivedPkt.pack()};
		addTimer(retransmitTimeout, "resendSynAck", paramTypes, params);

		return ourAckNo;
	}

	private void sendCurrentWindow(int destAddr, int destPort, int sequenceNo) {
		ArrayList<Transport> segWindow = sockBuffer.readCurrentWindow(localPort, destPort, sequenceNo, rwnd);
		// buffer is empty
		if (segWindow.size() == 0) return;
		
		// Flag for checking whether we are retransmitting
		boolean retransmitting = false;
		if (winEndSeqNo > sequenceNo) retransmitting = true;

		for (int i = 0; i < segWindow.size(); i++) {
			Transport dataSegment = segWindow.get(i);
			// Set samplePktTimestamp and samplePktSeqNo for estimation of sampleRTT for the first packet
			if (i == 0) {
				samplePktTimestamp = tcpMan.getManager().now();
				expectedSampleAckNo = dataSegment.getSeqNum() + dataSegment.getPayload().length;
			}

			Packet dataPkt = new Packet(destAddr, addr, Packet.MAX_TTL, Protocol.TRANSPORT_PKT, sequenceNo, dataSegment.pack());
			tcpMan.sendNetworkPacket(dataPkt);
			sequenceNo += Transport.MAX_PAYLOAD_SIZE;
			if (retransmitting) System.out.print("!"); else System.out.print(".");
		}
		// Correct the sequenceNo in case the last packet size was not Transport.MAX_PAYLOAD_SIZE
		int lastPktSize = segWindow.get(segWindow.size() - 1).getPayload().length;
		winEndSeqNo = sequenceNo + lastPktSize - Transport.MAX_PAYLOAD_SIZE;
		
		//System.out.println("Current ssthresh = " + ssthresh);

		// Register callback on timeout for retransmission of the current window
		addTimer(timeoutInterval * segWindow.size(), "onTimeout", null, null);
	}

	// Sends an acknowledgment for the received DATA packet
	private void sendDataAck(int newAckNo) {
		int freeBufferSpace = sockBuffer.bufferSpaceAvailable();
		Transport ackSegment = new Transport(localPort, connPort, Transport.ACK, freeBufferSpace, newAckNo, new byte[0]);
		Packet ackPkt = new Packet(connAddr, addr, Packet.MAX_TTL, Protocol.TRANSPORT_PKT, newAckNo, ackSegment.pack());
		tcpMan.sendNetworkPacket(ackPkt);
		System.out.print(":");
	}


	/*
	 * Retransmission timer and Callback functions
	 ==================================================================================================================*/

	// Timer to execute the given function with the provided params after the specified time interval
	private void addTimer(long deltaT, String methodName,  String[] paramTypes, Object[] params) {
		try {
			Method method = Callback.getMethod(methodName, this, paramTypes);
			Callback cb = new Callback(method, this, params);
			manager.addTimer(this.addr, deltaT, cb);
		}catch(Exception e) {
			System.err.println("Failed to add timer callback. Method Name: " + methodName +
					"\nException: " + e);
		}
	}

	// Callback method to execute when re-send SYN timer fires
	public void resendSyn(Integer destAddr, Integer destPort) {
		// Resend only if our current state in not yet ESTABLISHED
		if (state == State.SYN_SENT) {
			//System.out.println("Resending SYN");
			System.out.print("!");
			sendSyn(destAddr, destPort);
		}
	}

	// Callback method to execute when re-send SYN ACK timer fires
	public void resendSynAck(byte[] receivedPkt) {
		Packet receivedPacket = Packet.unpack(receivedPkt);
		// Resend only if our current ackNo is the same as the receivedPkt's seqNo
		if (state == State.ESTABLISHED && ackNo == receivedPacket.getSeq() + 1) {
			//System.out.println("Resending SYN ACK");
			System.out.print("!");
			sendSynAck(receivedPacket);
		}
	}

	// Callback method that initiates socket closure only when all data from buffer has been sent
	public void closeSock() {
		if (seqNo == finNo && sockBuffer.isBufferEmpty()) {
			state = State.CLOSED;
			tcpMan.sendFin(addr, localPort, connAddr, connPort);
			System.out.print("F");
		} else {
			addTimer(shutdownTimeout, "closeSock", null, null);
		}
	}

	// Callback method executed when timeoutInterval expires
	public void onTimeout() {
		if (!sockBuffer.isBufferEmpty()) sendCurrentWindow(connAddr, connPort, seqNo);
		// Need to check if actually timed out based on current expected seqNo
		if (seqNo < winEndSeqNo) {
			timedOut();
		}
	}

	/*
	 * Congestion control functions
	 ==================================================================================================================*/

	// Computes sampleRTT if ACK for the sampled packet is received and updates timeoutInterval
	private void updateTimeoutInterval() {
		if (samplePktTimestamp == -1) return;

		// formulae from section 3.5.3 of Computer Networking by Kurose | Ross
		long sampleRTT = tcpMan.getManager().now() - samplePktTimestamp;
		estimatedRTT = ((1 - ALPHA) * estimatedRTT) + (ALPHA * sampleRTT);
		devRTT = ((1 - BETA) * devRTT) + (BETA * Math.abs(sampleRTT - estimatedRTT));
		timeoutInterval = (long) (estimatedRTT + 4 * devRTT);

		// Need to reset variables!
		samplePktTimestamp = -1;
		expectedSampleAckNo = -1;
		
		//System.out.println("Timeout interval is now = " + timeoutInterval);
	}

	// State machine actions for when timeout occurs
	private void timedOut() {
		// First need to double the value of timeoutInterval
		timeoutInterval *= 2;
		
		switch(congState) {

		case SLOW_START:
			timeoutOccurred();
			break;

		case CONGESTION_AVOIDANCE:
			timeoutOccurred();
			congState = CongestionState.SLOW_START;
			break;

		case FAST_RECOVERY:
			timeoutOccurred();
			congState = CongestionState.SLOW_START;
			break;

		}
	}
	
	private void timeoutOccurred() {
		ssthresh = (int) (sockBuffer.getCongestionWinSize()/2.0);
		sockBuffer.setCongestionWinSize(Transport.MAX_PAYLOAD_SIZE);
		dupAckCount = 0;
		//if (!sockBuffer.isBufferEmpty()) sendCurrentWindow(connAddr, connPort, seqNo);
	}

	// State machine actions for when duplicate ACK is received
	private void duplicateAckReceived() {
		switch(congState) {

		case SLOW_START:
			dupAckCount++;
			if (dupAckCount == DUP_ACK_THRESHOLD) dupAckThresholdReached();
			break;

		case CONGESTION_AVOIDANCE:
			dupAckCount++;
			if (dupAckCount == DUP_ACK_THRESHOLD) dupAckThresholdReached();
			break;

		case FAST_RECOVERY:
			int cwnd = sockBuffer.getCongestionWinSize() + Transport.MAX_PAYLOAD_SIZE;
			sockBuffer.setCongestionWinSize(cwnd);
			break;

		}
	}
	
	private void dupAckThresholdReached() {
		ssthresh = (int) (sockBuffer.getCongestionWinSize()/2.0);
		int cwnd = ssthresh + DUP_ACK_THRESHOLD * Transport.MAX_PAYLOAD_SIZE;
		sockBuffer.setCongestionWinSize(cwnd);
		congState = CongestionState.FAST_RECOVERY;
		//if (!sockBuffer.isBufferEmpty()) sendCurrentWindow(connAddr, connPort, seqNo);
	}

	// State machine actions for when new (non duplicate) ACK is received
	private void newAckReceived() {
		switch(congState) {
		
		case SLOW_START:
			int cwnd = sockBuffer.getCongestionWinSize() + Transport.MAX_PAYLOAD_SIZE;
			sockBuffer.setCongestionWinSize(cwnd);
			dupAckCount = 0;
			if (cwnd >= ssthresh) congState = CongestionState.CONGESTION_AVOIDANCE;
			break;

		case CONGESTION_AVOIDANCE:
			int mss = Transport.MAX_PAYLOAD_SIZE;
			cwnd = sockBuffer.getCongestionWinSize();
			cwnd = cwnd + mss * (mss/cwnd);
			sockBuffer.setCongestionWinSize(cwnd);
			dupAckCount = 0;
			break;

		case FAST_RECOVERY:
			sockBuffer.setCongestionWinSize(ssthresh);
			dupAckCount = 0;
			congState = CongestionState.CONGESTION_AVOIDANCE;
			break;

		}
	}
}
