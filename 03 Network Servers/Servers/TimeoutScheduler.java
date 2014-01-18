import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class TimeoutScheduler implements Runnable {

	private Dispatcher dispatcher;
	private SocketChannel client;

	public TimeoutScheduler(Dispatcher dispatcher, SocketChannel client) {
		this.dispatcher = dispatcher;
		this.client = client;
	}

	@Override
	public void run() {

		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					SelectionKey selectionKey = dispatcher.keyFor(client);
					dispatcher.deregisterSelection(selectionKey);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}				
		};
		dispatcher.addToDispatcherQueue(runnable);
	}
}