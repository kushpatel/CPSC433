import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.ScheduledFuture;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class AsyncServerReadWriteHandler implements IReadWriteHandler {

	private ByteBuffer inBuffer;
	private ByteBuffer outBuffer;

	private Dispatcher dispatcher;
	private SocketChannel client;
	private SHTTPServer shttpServer;

	private boolean requestComplete;
	private boolean responseReady;
	private boolean responseSent;
	private boolean channelClosed;

	private StringBuffer request;
	
	private ScheduledFuture<?> scheduledFuture;
	
	private static final int IN_BUFFER_SIZE = 1024;
	private static final int OUT_BUFFER_SIZE = 2000000;

	public AsyncServerReadWriteHandler(Dispatcher dispatcher, SocketChannel client, SHTTPServer shttpServer) {
		inBuffer = ByteBuffer.allocate(IN_BUFFER_SIZE);
		outBuffer = ByteBuffer.allocate(OUT_BUFFER_SIZE);

		this.dispatcher = dispatcher;
		this.client = client;
		this.shttpServer = shttpServer;

		// initial state
		requestComplete = false;
		responseReady = false;
		responseSent = false;
		channelClosed = false;

		request = new StringBuffer(IN_BUFFER_SIZE);
	}

	public int getInitOps() {
		return SelectionKey.OP_READ;
	}

	public void handleException() {
	}
	
	public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
		this.scheduledFuture = scheduledFuture;
	}

	public void handleRead(SelectionKey key) throws IOException {

		// Turn off the timer that is waiting for client's request
		//scheduledFuture.cancel(true);
		
		// assert: t
		// a connection is ready to be read
		Debug.DEBUG("->handleRead");

		if (requestComplete) { // this call should not happen, ignore
			return;
		}

		// process data
		processInBuffer();

		// update state
		updateDispatcher();

		Debug.DEBUG("handleRead->");

	} // end of handleRead

	private void updateDispatcher() throws IOException {

		Debug.DEBUG("->Update dispatcher.");

		if (channelClosed) 
			return;

		// get registration key; as an optimization, may save it locally
		SelectionKey sk = dispatcher.keyFor(client);

		if (responseSent) {
			Debug.DEBUG("***Response sent; connection closed");
			outBuffer.clear();
			dispatcher.deregisterSelection(sk);
			client.close();
			channelClosed = true;
			return;
		}

		int nextState = 0; //sk.interestOps();
		if (requestComplete) {
			nextState = nextState & ~SelectionKey.OP_READ;
			Debug.DEBUG("New state: -Read since request parsed complete");
		} else {
			nextState = nextState | SelectionKey.OP_READ;
			Debug.DEBUG("New state: +Read to continue to read");
		}

		if (responseReady) {
			nextState = SelectionKey.OP_WRITE;
			Debug.DEBUG("New state: +Write since response ready but not done sent");
		} 

		dispatcher.updateInterests(sk, nextState);
	}

	public void handleWrite(SelectionKey key) throws IOException {
		Debug.DEBUG("->handleWrite");

		// process data
		//SocketChannel client = (SocketChannel) key.channel();
		Debug.DEBUG("handleWrite: Write data to connection " + client  
				+ "; from buffer " + outBuffer);
		
		int writeBytes = client.write(outBuffer);
		Debug.DEBUG("handleWrite: after write " + outBuffer + " written bytes = " + writeBytes);

		if ( responseReady && (outBuffer.remaining() == 0) )
			responseSent = true;

		// update state
		updateDispatcher();

		//try {Thread.sleep(5000);} catch (InterruptedException e) {}
		Debug.DEBUG("handleWrite->");
	} // end of handleWrite

	private void processInBuffer() throws IOException {
		Debug.DEBUG("processInBuffer");
		int readBytes = client.read(inBuffer);
		Debug.DEBUG("handleRead: Read data from connection " + client 
				+ " for " + readBytes
				+ " byte(s); to buffer " + inBuffer);

		if (readBytes == -1) { // end of stream
			requestComplete = true;
			Debug.DEBUG("handleRead: readBytes == -1");
		} else {
			inBuffer.flip(); // read input
			//outBuffer = ByteBuffer.allocate( inBuffer.remaining() );
			while ( !requestComplete
					&& inBuffer.hasRemaining()
					&& request.length() < request.capacity() ) {
				char ch = (char) inBuffer.get();
				Debug.DEBUG("Ch: " + ch);
				request.append(ch);
				// Search for a CRLF CRLF for end of request
				if (ch == '\r') {
					ch = (char) inBuffer.get();
					request.append(ch);
					if (ch == '\n') {
						ch = (char) inBuffer.get();
						request.append(ch);
						if (ch == '\r') {
							ch = (char) inBuffer.get();
							if (ch == '\n') {
								requestComplete = true;
								scheduledFuture.cancel(true);
							}
						}
					}
					Debug.DEBUG("handleRead: find terminating chars");
				} // end if
			} // end of while
		}

		inBuffer.clear(); // we do not keep things in the inBuffer

		if (requestComplete) {
			generateResponse();
		}

	} // end of process input

	private void generateResponse() {
		
		BufferedReader inFromClient = 
				new BufferedReader(new InputStreamReader(new ByteArrayInputStream(request.toString().getBytes())));
		byte[] response = shttpServer.serveRequest(inFromClient, client.socket().getInetAddress(), client.socket().getPort());
		
		if (response != null) outBuffer.put(response);
		outBuffer.flip(); 
		responseReady = true;
	} // end of generate response

}