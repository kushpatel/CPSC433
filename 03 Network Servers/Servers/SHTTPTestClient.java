import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;


public class SHTTPTestClient {

	private static final String SERVER_ARG = "-server";
	private static final String PORT_ARG = "-port";
	private static final String PARALLEL_ARG = "-parallel";
	private static final String FILES_ARG = "-files";
	private static final String TIME_ARG = "-T";

	private static long totalTransactions = 0;
	private static long totalBytesReceived = 0;
	private static long totalWaitTime = 0;

	public static void main (String[] args) {

		if (args.length != 10) {
			System.err.println("Usage: -server <server> -port <server port> "
					+ "-parallel <# of threads> -files <file name> -T <time of test in seconds>");
			return;
		}

		HashMap<String, String> argsMap = parseArgs(args);
		InetAddress serverAddress;
		int serverPort;
		int numThreads;
		ArrayList<String> filesToRequest;
		int testTime;

		// Parse command line arguments
		try {
			serverAddress = InetAddress.getByName(argsMap.get(SERVER_ARG));
			serverPort = Integer.parseInt(argsMap.get(PORT_ARG));
			numThreads = Integer.parseInt(argsMap.get(PARALLEL_ARG));
			filesToRequest = getFilesToRequest(argsMap.get(FILES_ARG));
			testTime = Integer.parseInt(argsMap.get(TIME_ARG));

		} catch (Exception e) {
			System.err.print("Exception in parsing args: " + e.getMessage());
			return;
		}

		// Start sendRequests on numThreads timer tasks! All timers should be killed at end of testTime
		ArrayList<ClientRequestThread> clientThreadPool = new ArrayList<ClientRequestThread>();
		for (int i = 0; i < numThreads; i++) {
			ClientRequestThread requestThread = new ClientRequestThread(filesToRequest, serverAddress, serverPort);
			clientThreadPool.add(requestThread);
			requestThread.start();
		}
		
		try {
			Thread.sleep(testTime * 1000);
			for (int i = 0; i < clientThreadPool.size(); i++) {
				clientThreadPool.get(i).interrupt();
			}
			Thread.sleep(ClientRequestThread.CLIENT_TIMEOUT);
			printStatistics(testTime);
		} catch (InterruptedException e) {
			System.out.println("Main thread interrupted in sleep: " + e.getMessage());
		}

	}

	/* Takes in args from terminal and returns a HashMap of < arg name , arg value >
	 */
	private static HashMap<String, String> parseArgs(String[] args) {

		HashMap<String, String> argsMap = new HashMap<String, String>();
		for (int i = 0; i < args.length; i += 2) {
			argsMap.put(args[i], args[i+1]);
		}
		return argsMap;
	}

	/* Takes in a filename and returns a list of files to request by
	 * reading the file line by line
	 */
	private static ArrayList<String> getFilesToRequest(String fileName) throws Exception {

		ArrayList<String> filesList = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		String line;
		while ((line = br.readLine()) != null) {
			filesList.add(line);
		}
		br.close();
		return filesList;
	}

	private static void printStatistics(int testTime) {

		System.out.println("========== Client requests statistics ==========");
		System.out.println();

		System.out.println("Total transaction throughput (#transactions per second): " + totalTransactions/testTime * 1.0);
		System.out.println("Data rate throughput (#megabytes per second): " + (totalBytesReceived/testTime * 1.0) / 1048576);
		if (totalTransactions != 0) System.out.println("Average wait time (ms): " + totalWaitTime/totalTransactions * 1.0);
	}

	/* Thread safe methods to update statistics collected on client run
	 ====================================================================================================================*/

	public static synchronized void gatherStatisticsFromThread(long numTransactions, long waitTime, long bytesReceived) {

		totalTransactions += numTransactions;
		totalWaitTime += waitTime;
		totalBytesReceived += bytesReceived;
	}

}
