package proj;
import lib.*;

import java.util.HashSet;
import java.util.Hashtable;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */
public class TCPManager {
	private Node node;
	private int addr;
	private Manager manager;

	//private static final byte dummy[] = new byte[0];
	
	private HashSet<Integer> boundSockets;
	private Hashtable<String, TCPSock> connectionsTable;
	private Hashtable<Integer, TCPSock> listeningSocks;

	public TCPManager(Node node, int addr, Manager manager) {
		this.node = node;
		this.addr = addr;
		this.manager = manager;
		
		boundSockets = new HashSet<Integer>();
		connectionsTable = new Hashtable<String, TCPSock>();
		listeningSocks = new Hashtable<Integer, TCPSock>();
	}
	
	public int getLocalAddress() {
		return addr;
	}

	/**
	 * Start this TCP manager
	 */
	public void start() {

	}

	/*
	 * Begin socket API
	 */

	/**
	 * Create a socket
	 *
	 * @return TCPSock the newly created socket, which is not yet bound to
	 *                 a local port
	 */
	public TCPSock socket() {
		return new TCPSock(manager, this);
	}

	/*
	 * End Socket API
	 */
	
	public Manager getManager() {
		return this.manager;
	}
	
	/* Interactions with network layer
	 ==================================================================================================================*/
	
//    private String printType(int type) {
//    	switch(type) {
//    	case 0: return "SYN";
//    	case 1: return "ACK";
//    	case 2: return "FIN";
//    	case 3: return "DATA";
//    	}
//    	return "";
//    }
	
	public void receiveNetworkPacket(Packet packet) {
		Transport seg = Transport.unpack(packet.getPayload());
//		System.out.println("Received packet: src[" + packet.getSrc() + ":" + seg.getSrcPort() + "], dest[" +
//				packet.getDest() + ":" + seg.getDestPort() + "], seq = " + packet.getSeq() + ", payload size = " + 
//				seg.getPayload().length + ", " + printType(seg.getType()));
		
		Transport segment = Transport.unpack(packet.getPayload());	
		int srcAddr = packet.getDest();
		int srcPort = segment.getDestPort();
		// Handle the special case of first SYN packet to establish connection separately
		if (segment.getType() == Transport.SYN) {
			// Look for the requested listening server in listeningSocks
			TCPSock listeningSock = listeningSocks.get(srcPort);
			if (listeningSock != null) listeningSock.onPacketReceived(packet);
			else System.out.print("Listening sock is null!");
		} else {
			// Channel the packet to the correct connection
			int destAddr = packet.getSrc();
			int destPort = segment.getSrcPort();
			String hash = connectionHash(srcPort, destAddr, destPort);
			TCPSock connectionSock = connectionsTable.get(hash);
			if (connectionSock != null) connectionSock.onPacketReceived(packet);
			// If connection doesn't exist, send FIN
			else if (seg.getType() != Transport.FIN) sendFin(srcAddr, srcPort, destAddr, destPort);
		}
	}
	
	public void sendNetworkPacket(Packet packet) {
//		Transport seg = Transport.unpack(packet.getPayload());
//		System.out.println("Sent packet: src[" + packet.getSrc() + ":" + seg.getSrcPort() + "], dest[" +
//				packet.getDest() + ":" + seg.getDestPort() + "], seq = " + packet.getSeq() + ", payload size = " + 
//				seg.getPayload().length + ", " + printType(seg.getType()));
		
		node.send(packet.getDest(), packet);
	}
	
	public void sendFin(int srcAddr, int srcPort, int destAddr, int destPort) {
		// FIN sent with a default seqNo = 0, cwnd = 0 .. these fields shouldn't matter in FIN
		Transport finSegment = new Transport(srcPort, destPort, Transport.FIN, 0, 0, new byte[0]);
		Packet finPacket = new Packet(destAddr, srcAddr, Packet.MAX_TTL, Protocol.TRANSPORT_PKT, 0, finSegment.pack());
		sendNetworkPacket(finPacket);
	}
	
	
	/* Connections management
	 ==================================================================================================================*/
	
	public boolean canBindSocket(int port) {
		if (!boundSockets.contains(port)) {
			boundSockets.add(port);
			return true;
		}
		return false;
	}
	
	public void unbindSocket(int sockPort) {
		boundSockets.remove(sockPort);
		if (listeningSocks.containsKey(sockPort)) {
			listeningSocks.remove(sockPort);
		}
	}
	
	public void addListeningSock(TCPSock sock) {
		listeningSocks.put(sock.getSockPort(), sock);
	}
	
	public boolean isListeningPort(int port) {
		return listeningSocks.containsKey(port);
	}
	
	private String connectionHash(int srcPort, int destAddr, int destPort) {
		// srcAddr is always addr
		return addr + ":" + srcPort + "," + destAddr + ":" + destPort;
	}
	
	public void addConnection(TCPSock sock, int destAddr, int destPort) {
		String hash = connectionHash(sock.getSockPort(), destAddr, destPort);
		connectionsTable.put(hash, sock);
	}
	
	public void closeConnection(TCPSock sock, int destAddr, int destPort) {
		String hash = connectionHash(sock.getSockPort(), destAddr, destPort);
		connectionsTable.remove(hash);
	}
	
}
