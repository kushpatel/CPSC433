package proj;
import java.util.ArrayList;

import lib.Transport;

public class TCPSockBuffer {

	private byte[] buffer;	// Can be either receive or send buffer (NOT both)
	private int BUFFER_SIZE;
	private int windowSize;		// Congestion window size
	private int bufBase = 0; // Index for first byte which has not yet been transmitted
	private int bufLen = 0;	// Index for last byte which has not yet been transmitted
	private int origSeqNo;	//Keeps track of what the initial seqNo was for offsetting

	public TCPSockBuffer(int bufSize, int windowSize, int origSeqNo) {
		BUFFER_SIZE = bufSize;
		buffer = new byte[BUFFER_SIZE];
		this.windowSize = windowSize;
		this.origSeqNo = origSeqNo;
	}

	/**
	 * Write to the socket buffer up to len bytes from the buffer appBuf starting at
	 * position pos.
	 *
	 * @param appBuf byte[] the buffer to write from
	 * @param pos int starting position in buffer
	 * @param len int number of bytes to write
	 * @return int on success, the number of bytes written, which may be smaller
	 *             than len; on failure, -1
	 */
	public int write(byte[] appBuf, int pos, int len) {
		// Write bytes in the circular buffer till all bytes written OR buffer is full
		int bytesWritten = 0;
		for (int idx = 0; idx < len && !isBufferFull(); idx++) {
			buffer[bufLen] = appBuf[pos + idx];
			bufLen = (bufLen + 1) % BUFFER_SIZE;
			bytesWritten++;
		}
		//System.out.println("BytesWritten = " + bytesWritten);
		return bytesWritten;
	}

	/**
	 * Read from the socket buffer up to len bytes into the buffer appBuf starting at
	 * position pos.
	 *
	 * @param appBuf byte[] the buffer
	 * @param pos int starting position in buffer
	 * @param len int number of bytes to read
	 * @return int on success, the number of bytes read, which may be smaller
	 *             than len; on failure, -1
	 */
	public int read(byte[] appBuf, int pos, int len) {
		int bytesRead = 0;
		for (int idx = 0; idx < len && !isBufferEmpty(); idx++) {
			appBuf[pos + idx] = buffer[bufBase];
			bufBase = (bufBase + 1) % BUFFER_SIZE;
			bytesRead++;
		}
		return bytesRead;
	}
	
	/**
	 * Read data from the buffer beginning at bufBase and return
	 * a constructed Transport segment to be sent
	 * Returns the first segment to be sent and null if buffer is empty
	 */ 
	public Transport readSegment(int srcPort, int destPort, int seqNo) {
		// Return null if buffer is empty
		if (isBufferEmpty()) return null;

		int bufIdx = (seqNo - origSeqNo) % BUFFER_SIZE;
		ArrayList<Byte> readBytes = new ArrayList<Byte>();
		for (int idx = 0; idx < Transport.MAX_PAYLOAD_SIZE && !isEndOfBuffer(bufIdx); idx++) {
			readBytes.add(buffer[bufIdx]);
			bufIdx = (bufIdx + 1) % BUFFER_SIZE;
		}
		byte[] payload = new byte[readBytes.size()];
		for (int i = 0; i < payload.length; i++) {
			payload[i] = readBytes.get(i);
		}
		return new Transport(srcPort, destPort, Transport.DATA, windowSize, seqNo, payload);
	}

	/**
	 * Read data from the buffer beginning at bufBase and return
	 * a list of constructed Transport segments to be sent
	 * Returns all the segments in buffer that fit into current window
	 */ 
	public ArrayList<Transport> readCurrentWindow(int srcPort, int destPort, int seqNo, int receiverWindow) {
		// seqNo points to last byte sent so need to increment it first to point to the first byte to be sent
		seqNo++;
		
		ArrayList<Transport> segmentsWindow = new ArrayList<Transport>();
		
		// For flow control choose MIN{receiverWindow, windowSize} where windowSize is our congestion window
		int numBytesToSend = Math.min(receiverWindow, windowSpaceUsed());
		
		int numSegInWin = (int) Math.ceil(numBytesToSend *(1.0)/Transport.MAX_PAYLOAD_SIZE); 
		for (int i = 0; i < numSegInWin; i++) {
			Transport segment = readSegment(srcPort, destPort, seqNo);
			segmentsWindow.add(segment);
			seqNo += Transport.MAX_PAYLOAD_SIZE;
		}
		/*System.out.println("BufBase = " + bufBase + ", bufLen = " + bufLen + ", window space used = " + windowSpaceUsed() +
				", buffer space used = " + bufferSpaceUsed());
		System.out.println("Num segs = " + segmentsWindow.size());
		System.out.println("Num packets sent = " + numSegInWin);
		*/
		return segmentsWindow;
	}

	/**
	 * Write data from the given segment in the buffer
	 * Return number of bytes written
	 * Drops the packet if it cannot fit in the buffer and returns -1
	 */ 
	public int writeSegment(Transport segment) {
		byte[] data = segment.getPayload();
		// Drop packet if it can't fit in the buffer
		if (bufferSpaceAvailable() < data.length) return -1;
		else return write(data, 0, data.length);
	}

	/**
	 * Update bufBase when ACK has been received indicating that all previous
	 * packets have been successfully transmitted
	 */ 
	public void updateBufferBase(int ackNoReceived) {
		bufBase = (ackNoReceived - origSeqNo) % BUFFER_SIZE;
	}
	
	/**
	 * Returns the current size of congestion window
	 */ 
	public int getCongestionWinSize() {
		return this.windowSize;
	}
	
	/**
	 * Sets the size of congestion window to the new value specified
	 */ 
	public void setCongestionWinSize(int newSize) {
		this.windowSize = newSize;
	}

	// Define buffer to be full when addition of a byte will bring bufLen index at bufBase index
	private boolean isBufferFull() {
		return bufBase == (bufLen + 1) % BUFFER_SIZE;
	}

	// Define buffer to be empty when bufBase index is at bufLen index
	public boolean isBufferEmpty() {
		return bufBase == bufLen;
	}

	// Checks whether the provided index is at the end of buffer
	private boolean isEndOfBuffer(int idx) {
		return idx == bufLen;
	}

	// Computes free space available in the buffer
	public int bufferSpaceAvailable() {
		if (bufBase <= bufLen) {
			return BUFFER_SIZE - (bufLen - bufBase) - 1;
		} else {
			return (bufBase - bufLen) - 1;
		}
	}

	// Computes the number of currently buffered bytes
	public int bufferSpaceUsed() {
		return BUFFER_SIZE - bufferSpaceAvailable() - 1;
	}
	
	// Computes the number of currently buffered bytes in the window
	public int windowSpaceUsed() {
		return Math.min(bufferSpaceUsed(), windowSize);
	}
}
