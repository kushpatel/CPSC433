import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.sql.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;


public class SHTTPServer {

	private static final String ARG_CONFIG = "-config";

	private static final String CONFIG_LISTEN_PORT = "Listen";
	private static final String CONFIG_DOC_ROOT = "DocumentRoot";
	private static final String CONFIG_THREAD_POOL_SIZE = "ThreadPoolSize";
	private static final String CONFIG_CACHE_SIZE = "CacheSize";
	private static final String CONFIG_INCOMPLETE_TIMEOUT = "IncompleteTimeout";
	private static final String CONFIG_LOAD_BALANCER = "LoadBalancer";

	private final String STATUS_OK = "200 OK";
	private final String STATUS_ERROR = "400 ERROR";
	private final String STATUS_UNMODIFIED = "304 UNMODIFIED";
	private final String STATUS_ACCEPTING = "200 ACCEPTING";
	private final String STATUS_OVERLOADED = "503 OVERLOADED";
	
	private LoadBalancer loadBalancer = null;

	private int serverPort = 0;
	private String documentRoot = null;
	private int maxCacheSize = 0;		// in bytes
	private int numThreads = 0;
	private int incompleteTimeout = 3000;	// 3 s = default
	private ServerSocket serverSocket = null;
	private String SERVER_NAME;
	private int currentCacheSize = 0;
	private Hashtable<String, byte[]> cache;


	public SHTTPServer(String serverName, String[] commandLineArgs) {

		this.SERVER_NAME = serverName;
		boolean configSetup = setupConfigurations(commandLineArgs);
		if (!configSetup) System.err.println("WARNING: Configurations may not have been setup correctly.");

		// Initialize an empty cache
		cache = new Hashtable<String, byte[]>();
		
		if (serverName.equals("AsyncServer")) return; // AsyncServer doesn't need to be setup with a port
		
		// create server socket
		try {
			serverSocket = new ServerSocket(serverPort);
			System.out.println("Server started; listening at " + serverPort);
		} catch (Exception e) {
			serverSocket = null;
			System.err.println("Exception in binding to port: " + e.getMessage());
		}

	}

	public int getServerPort() {
		return serverPort;
	}

	public int getNumThreads() {
		return numThreads;
	}

	public int getMaxCacheSize() {
		// Return in kiloBytes
		return maxCacheSize / 1024;
	}
	
	public int getIncompletTimeout() {
		return incompleteTimeout;
	}

	public ServerSocket getServerSocket() {
		return serverSocket;
	}

	private boolean setupConfigurations(String[] commandLineArgs) {

		if (commandLineArgs.length == 0 || (commandLineArgs.length % 2) != 0) {
			System.err.println("Usage: java <ServerName> -config <config_file_name> [-configParam value]");
			return false;
		}

		HashMap<String, String> argsList = parseCommandLineArgs(commandLineArgs);

		String configFileName = argsList.get(ARG_CONFIG);
		if (configFileName != null && !configFileName.isEmpty()) parseConfigFile(configFileName);

		// Command line args overwrite config file parameters
		String argListenPortValue = argsList.get(CONFIG_LISTEN_PORT);
		if (argListenPortValue != null && !argListenPortValue.isEmpty())
			serverPort = Integer.parseInt(argListenPortValue);
		String argDocRoot = argsList.get(CONFIG_DOC_ROOT);
		if (argDocRoot != null && !argDocRoot.isEmpty()) 
			documentRoot = argDocRoot;
		String argThreadPoolSize = argsList.get(CONFIG_THREAD_POOL_SIZE);
		if (argThreadPoolSize != null && !argThreadPoolSize.isEmpty())
			numThreads = Integer.parseInt(argThreadPoolSize);
		String argCacheSize = argsList.get(CONFIG_CACHE_SIZE);
		if (argCacheSize != null && !argCacheSize.isEmpty())
			maxCacheSize = (Integer.parseInt(argCacheSize)) * 1024; // in bytes
		String argIncompleteTimeout = argsList.get(CONFIG_INCOMPLETE_TIMEOUT);
		if (argIncompleteTimeout != null && !argIncompleteTimeout.isEmpty()) 
			incompleteTimeout = (Integer.parseInt(argIncompleteTimeout)) * 1000; // in ms
		String argLoadBalancer = argsList.get(CONFIG_LOAD_BALANCER);
		if (argLoadBalancer != null && !argLoadBalancer.isEmpty()) {
			try {
				loadBalancer = (LoadBalancer) Class.forName(argLoadBalancer).newInstance();
			} catch (Exception e) {
				System.err.println("Error in load LoadBalancer class: " + argLoadBalancer + ": " + e.getMessage());
			}
		}
		
		// Document root and server port parameters have to be set!
		return (documentRoot != null && serverPort != 0);

	}

	private HashMap<String, String> parseCommandLineArgs(String[] args) {

		HashMap<String, String> argsMap = new HashMap<String, String>();
		for (int i = 0; i < args.length; i+=2) {
			argsMap.put(args[i], args[i+1]);
		}		
		return argsMap;
	}

	private void parseConfigFile(String configFileName) {

		File configFile = new File(configFileName);
		BufferedReader fileReader = null;
		try {
			fileReader = new BufferedReader(new FileReader(configFile));
			String line;
			while ((line = fileReader.readLine()) != null) {
				String[] splitString = line.split(" ");
				if (splitString[0].equals("Listen")) {
					serverPort = Integer.parseInt(splitString[1]);
				} else if (splitString[0].equals("DocumentRoot")) {
					documentRoot = splitString[1];
				} else if (splitString[0].equals("CacheSize")) {
					maxCacheSize = (Integer.parseInt(splitString[1])) * 1024; // in bytes
				} else if (splitString[0].equals("ThreadPoolSize")) {
					numThreads = Integer.parseInt(splitString[1]);
				} else if (splitString[0].equals("IncompleteTimeout")) {
					incompleteTimeout = Integer.parseInt(splitString[1]);
				} else if (splitString[0].equals("LoadBalancer")) {
					try {
						loadBalancer = (LoadBalancer) Class.forName(splitString[1]).newInstance();
					} catch (Exception e) {
						System.err.println("Error in load LoadBalancer class: " + splitString[1] + ": " + e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error in parsing config file: " + e.getMessage());
		}finally {
			try {
				if (fileReader != null) {
					fileReader.close();
				}
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		}

	}

	public void serveRequest(Socket connectionSocket) {
		try {
			// create read stream to get input
			BufferedReader inFromClient = 
					new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
			InetAddress clientAddress = connectionSocket.getLocalAddress();
			int clientPort = connectionSocket.getLocalPort();

			// send reply
			byte[] replyMessage = serveRequest(inFromClient, clientAddress, clientPort);
			if (replyMessage != null) {
				DataOutputStream outToClient = 	new DataOutputStream(connectionSocket.getOutputStream());
				outToClient.write(replyMessage);
			}

			connectionSocket.close();
			
		} catch (IOException e) {
			//System.err.println("IOException in serving connection socket request: " + e.getMessage());
			//e.printStackTrace();
		}
	}

	public byte[] serveRequest(BufferedReader inFromClient, InetAddress clientAddress, int clientPort) {

		byte[] replyMessage = null;
		try {
			// Read in a valid get request
			String clientGetRequest = inFromClient.readLine();
			if (!isClientGetRequestValid(clientGetRequest)) {
				System.err.println("Invalid get request: " + clientGetRequest);
				return null;
			}
			
			// URL might contain characters like %20 for space
			String urlRequested = URLDecoder.decode(getUrlRequested(clientGetRequest), "UTF-8");

			// Get current date
			DateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
			Calendar cal = Calendar.getInstance();
			String date = dateFormat.format(cal.getTime());
			
			// Handle load monitoring request separately .. virtual URL = "/load"
			if (urlRequested.equals("/load") && loadBalancer != null) {

				String statusCode = (loadBalancer.isSystemOverloaded()) ? STATUS_OVERLOADED : STATUS_ACCEPTING;
				
				// return reply to be sent
				replyMessage = 	createReplyMessage(statusCode, date, "EMPTY", "0", null);
				return replyMessage;
			}

			// Read in any extra fields present in the header in a HashMap
			HashMap<String, String> requestFields = getRequestExtrasMap(inFromClient);

			// Handle User-Agent appropriately if specified in client request
			String userAgentValue = requestFields.get("User-Agent");
			boolean iPhoneUserAgent = false;
			if (userAgentValue != null) iPhoneUserAgent = true;

			// Fetch requested file from document root if present
			if (urlRequested.length() > 0 && urlRequested.charAt(urlRequested.length() - 1) == '/') {
				if (iPhoneUserAgent) {
					// Return index_m.html if it exists
					byte[] iphoneIndexFile = lookupRequestedFile(urlRequested + "index_m.html");
					if (iphoneIndexFile != null) urlRequested += "index_m.html";
					else urlRequested += "index.html";
				} else {
					urlRequested += "index.html";
				}
			}

			// Fetches file with the associated url .. handles caching too! .. returns null if file is executable
			byte[] requestedFile = lookupRequestedFile(urlRequested);

			// Handle executable file case
			boolean fileIsExecutable = false;
			String execFileName = urlRequested;
			if (requestedFile == null) {
				// Need to first strip out the search query parameters passed
				int queryIdentifierIdx = urlRequested.indexOf('?');	
				if (queryIdentifierIdx >= 0) {
					execFileName = urlRequested.substring(0, queryIdentifierIdx);
				}
				File executableFile = new File(documentRoot + execFileName);
				if (!executableFile.isDirectory() && executableFile.canExecute()) {
					fileIsExecutable = true;
				}
			}

			String statusCode, contentType, lengthOfFile;
			if (requestedFile != null) {
				statusCode = STATUS_OK;
				lengthOfFile = "" + requestedFile.length;
				contentType = getContentTypeFromExtension(urlRequested);
			} else if (fileIsExecutable) {
				// Output of the process goes to requestedFile
				requestedFile = runProcess(urlRequested, execFileName, clientAddress, clientPort);			
				statusCode = STATUS_OK;
				lengthOfFile = "" + requestedFile.length;
				contentType = "text/plain"; // ??
			} else {
				statusCode = STATUS_ERROR;
				lengthOfFile = "0";
				contentType = "EMPTY";
			}

			// Handle If-Modified-Since appropriately if specified in client request
			// e.g. If-Unmodified-Since: Sat, 29 Oct 1994 19:43:31 GMT
			String ifModifiedSince = requestFields.get("If-Modified-Since");
			if (ifModifiedSince != null && !ifModifiedSince.equals("")) {
				Date oldDate = (Date) dateFormat.parse(ifModifiedSince);
				if (requestedFile != null && oldDate != null) {
					File requestedFileFromDisk = new File(documentRoot + urlRequested);
					long fileLastModifiedTime = requestedFileFromDisk.lastModified();
					long ifModifiedSinceTime = oldDate.getTime();
					if (fileLastModifiedTime <= ifModifiedSinceTime) {
						statusCode = STATUS_UNMODIFIED;
					}
				}
			}

			replyMessage = 	createReplyMessage(statusCode, date, contentType, lengthOfFile, requestedFile);

		} catch (IOException e) {
			//System.err.println("IOException in processing request: " + e.getMessage());
		} catch (ParseException e) {
			System.err.println("ParseException in parsing date from If-Modified-Since: " + e.getMessage());
		}

		return replyMessage;
	}

	private boolean isClientGetRequestValid(String clientGetRequest) {

		// clientRequest should be: GET <URL> HTTP/1.0 .. where URL has to start with "/"		
		if (clientGetRequest == null || clientGetRequest.isEmpty()) return false;
		String[] splitGetRequest = clientGetRequest.split(" ");
		if (splitGetRequest.length != 3) return false;
		if (!splitGetRequest[0].equals("GET")) return false;
		if (splitGetRequest[1].charAt(0) != '/') return false;
		//if (!splitGetRequest[2].equals("HTTP/1.0")) return false;		

		return true;
	}

	private String getUrlRequested(String clientRequest) {

		// clientRequest is of form: GET <URL> HTTP/1.0
		String[] splitRequest = clientRequest.split(" ");
		return splitRequest[1];
	}

	private String getContentTypeFromExtension(String urlRequested) {

		// Return the extension of file .. if none present, returns empty string
		String extension = "";
		int lastDotIdx = urlRequested.lastIndexOf('.');
		if (lastDotIdx >= 0) {
			extension = urlRequested.substring(lastDotIdx + 1);
		}
		return extension;
	}

	private HashMap<String, String> getRequestExtrasMap(BufferedReader inFromClient) {

		HashMap<String, String> extrasMap = new HashMap<String, String>();		
		try {
			String line = inFromClient.readLine();
			while (line != null && !line.equals("")) {
				String[] splitExtra = line.split(" ");
				if (splitExtra.length == 2) {
					extrasMap.put(splitExtra[0], splitExtra[1]);
				}
				line = inFromClient.readLine();
			}
		} catch (IOException e) {
			System.err.println("IOException in reading client request: " + e.getMessage());
		}

		return extrasMap;
	}

	/* Looks up the requested file (in form of url) in the cache or disk
	 * NOTE: Returns null if the file is executable! (Without adding to cache)
	 */
	private byte[] lookupRequestedFile(String urlRequested) {

		String fileFullPath = documentRoot + urlRequested;
		byte[] toReturn = null;

		// First check if the file exists in the cache
		if (cache.containsKey(fileFullPath)) {
			byte[] cachedFileArray = cache.get(fileFullPath);
			toReturn = cachedFileArray;			
		} else {
			// Need to fetch file from disk
			File fetchedFile = new File(fileFullPath);
			boolean isExecutable = fetchedFile.canExecute();
			// Check if file exists on the disk && is NOT a directory && is NOT executable
			if (fetchedFile.exists() && !fetchedFile.isDirectory() && !isExecutable) {
				try {
					// Read the whole file into a byte array
					toReturn = new byte[(int) fetchedFile.length()];
					FileInputStream in = new FileInputStream(fetchedFile);
					int idx = 0;
					int b;
					while ((b = in.read()) != -1) {
						toReturn[idx] = (byte) b;
						idx++;
					}
					in.close();

					// Add the file in cache if maxCacheSize has not been exceeded
					int newCacheSize = currentCacheSize + toReturn.length;
					if (newCacheSize <= maxCacheSize) {
						cache.put(fileFullPath, toReturn);
						incrementCacheSize(newCacheSize);
					}
				} catch (IOException e) {
					System.err.println("IOException in reading file from disk: " + e.getMessage());
				}
			}
		}
		return toReturn;
	}
	
	private synchronized void incrementCacheSize(int newCacheSize) {
		currentCacheSize = newCacheSize;
	}

	private byte[] runProcess(String urlRequested, String execFileName, InetAddress clientAddress, int clientPort) {

		ByteArrayOutputStream processOutputBuffer = new ByteArrayOutputStream();
		try {
			ProcessBuilder processBuilder = new ProcessBuilder(documentRoot + execFileName);
			Map<String, String> environmentMap = processBuilder.environment();

			// Query url of form: /search?q=Yale&sourceid=chrome
			int queryStringIdx = urlRequested.indexOf('?');
			if (queryStringIdx >= 0 && queryStringIdx + 1 < urlRequested.length()) {
				String queryString = urlRequested.substring(queryStringIdx + 1);
				environmentMap.put("QUERY_STRING", URLDecoder.decode(queryString, "UTF-8"));
				environmentMap.put("REQUEST_METHOD", "GET");	// we're just handling GET
				environmentMap.put("SERVER_NAME", SERVER_NAME);
				environmentMap.put("SERVER_PORT", "" + serverPort);
				environmentMap.put("SERVER_ADMIN", "admin@shttpserver.com");
				environmentMap.put("SERVER_SOFTWARE", "Simple HTTP Server");
				environmentMap.put("REMOTE_ADDR", clientAddress.getHostAddress());
				environmentMap.put("REMOTE_HOST", clientAddress.getHostName());
				environmentMap.put("REMOTE_PORT", "" + clientPort);
				environmentMap.put("REMOTE_USER", "username");
				
//				String[] splitQuery = queryString.split("&");
//				for (int i = 0; i < splitQuery.length; i++) {
//					String[] splitVar = splitQuery[i].split("=");
//					if (splitVar.length == 2) {
//						environmentMap.put(URLDecoder.decode(splitVar[0], "UTF-8"), URLDecoder.decode(splitVar[1], "UTF-8"));
//					}
//				}
			}
			processBuilder.directory(new File(documentRoot));
			Process process = processBuilder.start();
			InputStream processInputStream = process.getInputStream();
			int b = processInputStream.read();
			while(b != -1) {
				processOutputBuffer.write((byte) b);
				b = processInputStream.read();
			}
		} catch (IOException e) {
			System.err.println("Error in running process " + urlRequested + ": " + e.getMessage());
		}
		return processOutputBuffer.toByteArray();
	}

	/* Reply message format: 
	 *  HTTP/1.0 <StatusCode> <message>
	 *	Date: <date>
	 *	Server: <your server name>
	 *	Content-Type: text/html
	 *	Content-Length: <LengthOfFile>
	 *	CRLF
	 *	<file content> 
	 */
	private byte[] createReplyMessage(String statusCode, String date, String contentType, String lengthOfFile, byte[] file) {

		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

		try {

			String http = "HTTP/1.0 ";
			String dateLabel = "Date: ";
			String serverLabel = "Server: ";
			String contentLabel = "Content-Type: ";
			String lengthLabel = "Content-Length: ";
			String crlf = "\r\n";

			// Write header bytes in the byte buffer
			byteBuffer.write(http.getBytes());
			byteBuffer.write(statusCode.getBytes());
			byteBuffer.write(crlf.getBytes());
			byteBuffer.write(dateLabel.getBytes());
			byteBuffer.write(date.getBytes());
			byteBuffer.write(crlf.getBytes());
			byteBuffer.write(serverLabel.getBytes());
			byteBuffer.write(SERVER_NAME.getBytes());
			byteBuffer.write(crlf.getBytes());
			byteBuffer.write(contentLabel.getBytes());
			byteBuffer.write(contentType.getBytes());
			byteBuffer.write(crlf.getBytes());
			byteBuffer.write(lengthLabel.getBytes());
			byteBuffer.write(lengthOfFile.getBytes());
			byteBuffer.write(crlf.getBytes());
			byteBuffer.write(crlf.getBytes());

			// Read in the whole file in the byte buffer
			if (statusCode == STATUS_OK) {
				byteBuffer.write(file);
			}

		} catch (Exception e) {
			System.err.println("Error in creating reply message: " + e.getMessage());
		}

		return byteBuffer.toByteArray();
	}

}
