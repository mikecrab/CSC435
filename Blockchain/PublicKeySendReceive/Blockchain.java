/* 2018-01-14:
Blockchain.java for BlockChain 
Dr. Clark Elliott for CSC435

This is some quick sample code giving a simple framework for coordinating multiple processes in a blockchain group.

INSTRUCTIONS:

Set the numProceses class variable (e.g., 1,2,3), and use a batch file to match

AllStart.bat:

REM for three procesess:
start java Blockchain. 0
start java Blockchain. 1
java Blockchain. 2

You might want to start with just one process to see how it works.

Thanks: http://www.javacodex.com/Concurrency/PriorityBlockingQueue-Example

Notes to CDE: 
Optional: send public key as Base64 XML along with a signed string.
Verfy the signature with public key that has been restored.

*/

import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.Object;
import java.util.concurrent.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.security.spec.*;
import java.security.*;

// Port interface for each type of port to implement
interface Port {
	// each port implements this function
	// change what number to add to the process id in each implementation
	public int getPortByProcessId(int processId);
}

class PublicKeyPort implements Port {
	public int getPortByProcessId(int processId) {
		return 4710 + processId;
	}
}

class UnverifiedBlockPort implements Port {
	public int getPortByProcessId(int processId) {
		return 4820 + processId;
	}
}

class UpdatedBlockchainPort implements Port {
	public int getPortByProcessId(int processId) {
		return 4930 + processId;
	}
}

class Connection {
	private String serverName;
	private Port portStrategy;
	private int sendingProcessId;

	// init connection object
	public Connection(String serverName, Port portStrategy, int sendingProcessId) {
		this.serverName = serverName;
		this.portStrategy = portStrategy;
		this.sendingProcessId = sendingProcessId;
	}

	// send message to all processes
	public void multicastData(String message) {
		Request request = new Request(message, this.sendingProcessId);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Socket socket;
		PrintStream toServer;

		// Convert the Java object to a JSON String:
		String jsonRequest = gson.toJson(request);
		System.out.print(jsonRequest);
		try {
			for(int processNum = 0; processNum <= 2; processNum++) {
					int port =  portStrategy.getPortByProcessId(processNum);
					System.out.println(port);
					socket = new Socket(this.serverName, port);
					toServer = new PrintStream(socket.getOutputStream());
					toServer.println(jsonRequest);
					toServer.flush();
					socket.close();
			}
		} catch (IOException e){
			e.printStackTrace();
		}
	}
}

// basic request object to get what the server needs to the server
class Request implements Serializable {
    // message to tell the server what to do
    public String message;
    // process id to know where request is coming from
    public int processId;

    public Request(String message, int processId) {
        this.message = message;
        this.processId = processId;
    }
}

class PublicKeyState {
	private static PublicKey[] publicKeys = new PublicKey[3];

	public static void setProcessPublicKey(int processId, PublicKey publicKey) {
		publicKeys[processId] = publicKey;
	}

	public static PublicKey getProcessPublicKey(int processId) {
		return publicKeys[processId];
	}

	public static PublicKey[] getPublicKeys() {
		return publicKeys;
	}
}

class PublicKeyServer implements Runnable {
	//public ProcessBlock[] PBlock = new ProcessBlock[3]; // One block to store info for each process.
		
	public void run(){
		int q_len = 6;
		Socket socket;
		int port = new PublicKeyPort().getPortByProcessId(Blockchain.pid);
		System.out.println("Starting Key Server input thread using " + Integer.toString(port));
		try{
			ServerSocket serverSocket = new ServerSocket(port, q_len);
			while (true) {
				socket = serverSocket.accept();
				new PublicKeyWorker(socket).start(); 
			}
		} catch (IOException e) {
			System.out.println(e);
		}
	}
}

class PublicKeyWorker extends Thread {
	Socket socket;
	PublicKeyWorker (Socket s) {
		socket = s;
	}
	public void run(){
		try{
			BufferedReader inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			Gson gson = new Gson();
			String data = "";
			String dataLine = "";
			do {
				data = data + dataLine;
				dataLine = inputReader.readLine();
			} while (dataLine != null);
			
			Request requestData = gson.fromJson(data, Request.class);
			System.out.println("Got key: " + requestData.message);

			byte[] publicKeyBytes = Base64.getMimeDecoder().decode(requestData.message);
			X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);

			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

			PublicKeyState.setProcessPublicKey(requestData.processId, publicKey);

			socket.close(); 
		} catch (IOException e){
			e.printStackTrace();
		} catch (NoSuchAlgorithmException nsae) {
			nsae.printStackTrace();
		} catch (InvalidKeySpecException ikse) {
			ikse.printStackTrace();
		}
	}
  }

class UnverifiedBlockServer implements Runnable {
	BlockingQueue<String> queue;
	UnverifiedBlockServer(BlockingQueue<String> queue){
	  this.queue = queue; // Constructor binds our prioirty queue to the local variable.
	}
  
	/* Inner class to share priority queue. We are going to place the unverified blocks into this queue in the order we get
	   them, but they will be retrieved by a consumer process sorted by blockID. */ 
  
	class UnverifiedBlockWorker extends Thread { // Class definition
	  Socket sock; // Class member, socket, local to Worker.
	  UnverifiedBlockWorker (Socket s) {sock = s;} // Constructor, assign arg s to local sock
	  public void run(){
		try{
	  BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
	  String data = in.readLine ();
	  System.out.println("Put in priority queue: " + data + "\n");
	  queue.put(data);
	  sock.close(); 
		} catch (Exception x){x.printStackTrace();}
	  }
	}
	
	public void run(){
		int q_len = 6;
		Socket socket;
		int port = new UnverifiedBlockPort().getPortByProcessId(Blockchain.pid);
		System.out.println("Starting the Unverified Block Server input thread using " + Integer.toString(port));
		try{
			ServerSocket serverSocket = new ServerSocket(port, q_len);
			while (true) {
				socket = serverSocket.accept(); // Got a new unverified block
				socket.close();
				//new UnverifiedBlockWorker(sock).start(); // So start a thread to process it.
			}
		} catch (IOException e) {
			System.out.println(e);
		}
	}
}

class UnverifiedBlockConsumer implements Runnable {
	BlockingQueue<String> queue;
	int pid;
	UnverifiedBlockConsumer(BlockingQueue<String> queue){
	  	this.queue = queue; // Constructor binds our prioirty queue to the local variable.
	}
  
	public void run(){
		// String data;
		// PrintStream toServer;
		// Socket sock;
		// String newblockchain;
		// String fakeVerifiedBlock;
	
		// System.out.println("Starting the Unverified Block Priority Queue Consumer thread.\n");
		// try{
		// 	while(true){ // Consume from the incoming queue. Do the work to verify. Mulitcast new blockchain
		// 		data = queue.take(); // Will blocked-wait on empty queue
		// 		System.out.println("Consumer got unverified: " + data);
				
		// 		// Ordindarily we would do real work here, based on the incoming data.
		// 		int j; // Here we fake doing some work (That is, here we could cheat, so not ACTUAL work..)
		// 		for(int i=0; i< 100; i++){ // put a limit on the fake work for this example
		// 			j = ThreadLocalRandom.current().nextInt(0,10);
		// 			try{Thread.sleep(500);}catch(Exception e){e.printStackTrace();}
		// 			if (j < 3) break; // <- how hard our fake work is; about 1.5 seconds.
		// 		}
			
		// 		/* With duplicate blocks that have been verified by different procs ordinarily we would keep only the one with
		// 				the lowest verification timestamp. For the exmple we use a crude filter, which also may let some dups through */
		// 		if(Blockchain.blockchain.indexOf(data.substring(1, 9)) < 0) { // Crude, but excludes most duplicates. 
		// 			fakeVerifiedBlock = "[" + data + " verified by P" + Blockchain.pid + " at time " 
		// 			+ Integer.toString(ThreadLocalRandom.current().nextInt(100,1000)) + "]\n";
		// 			System.out.println(fakeVerifiedBlock);
		// 			String tempblockchain = fakeVerifiedBlock + Blockchain.blockchain; // add the verified block to the chain
		// 			for(int i=0; i < Blockchain.numProcesses; i++){ // send to each process in group, including us:
		// 				sock = new Socket(Blockchain.serverName, Connection.getUpdatedBlockchainPortByProcessId(Blockchain.pid) + (i * 1000));
		// 				toServer = new PrintStream(sock.getOutputStream());
		// 				toServer.println(tempblockchain); toServer.flush(); // make the multicast
		// 				sock.close();
		// 			}
		// 		}
		// 		Thread.sleep(1500); // For the example, wait for our blockchain to be updated before processing a new block
		// 	}
		// } catch (Exception e) {
		// 	System.out.println(e);
		// }
	}
}

class BlockchainServer implements Runnable {
	public void run(){
		int q_len = 6;
		Socket socket;
		int port = new UpdatedBlockchainPort().getPortByProcessId(Blockchain.pid);

		System.out.println("Starting the blockchain server input thread using " + Integer.toString(port));
		try{
			ServerSocket serverSocket = new ServerSocket(port, q_len);
			while (true) {
				socket = serverSocket.accept();
				socket.close();
				//new BlockchainWorker (sock).start(); 
			}
		}	catch (IOException e) {
				System.out.println(e);
			}
	}
}

class Json {
	public static Gson gsonBuilder = new GsonBuilder().setPrettyPrinting().create();
	public static Gson gson = new Gson();

	public static String toJson(Object data) {
		return gsonBuilder.toJson(data);
	}

	public static <T> T fromJson(String data, Class<T> c) {
		return gson.fromJson(data, c);
	}
}

// Class Blockchain. for BlockChain
public class Blockchain {
    static String serverName = "localhost";
    static String blockchain = "[First block]";
	static int numProcesses = 3; // Set this to match your batch execution file that starts N processes with args 0,1,2,..N
	static KeyPair keys;
	static int pid = 0; // Our process ID
	static Connection publicKeyConnection;
	static Connection unverifiedBlockConnection;
	static Connection updatedBlockchainConnection;
  
    // public void MultiSend (){ // Multicast some data to each of the processes.
	// 	Socket sock;
	// 	PrintStream toServer;
	
	// 	try{
	// 		for(int i=0; i< numProcesses; i++) {// Send our key to all servers.
	// 			sock = new Socket(serverName, Connection.getPublicKeyPortByProcessId(Blockchain.pid) + (i * 1000));
	// 			toServer = new PrintStream(sock.getOutputStream());
	// 			toServer.println("FakeKeyProcess" + Blockchain.pid); toServer.flush();
	// 			sock.close();
	// 		} 
	// 		Thread.sleep(1000); // wait for keys to settle, normally would wait for an ack
	// 		//Fancy arithmetic is just to generate identifiable blockIDs out of numerical sort order:
	// 		String fakeBlockA = "(Block#" + Integer.toString(((Blockchain.pid+1)*10)+4) + " from P"+ Blockchain.pid + ")";
	// 		String fakeBlockB = "(Block#" + Integer.toString(((Blockchain.pid+1)*10)+3) + " from P"+ Blockchain.pid + ")";
	// 		for(int i=0; i< numProcesses; i++) {// Send a sample unverified block A to each server
	// 			sock = new Socket(serverName, Connection.getUnverifiedBlockPortByProcessId(Blockchain.pid) + (i * 1000));
	// 			toServer = new PrintStream(sock.getOutputStream());
	// 			toServer.println(fakeBlockA);
	// 			toServer.flush();
	// 			sock.close();
	// 		}
	// 		for(int i=0; i< numProcesses; i++){// Send a sample unverified block B to each server
	// 			sock = new Socket(serverName, Connection.getUnverifiedBlockPortByProcessId(Blockchain.pid) + (i * 1000));
	// 			toServer = new PrintStream(sock.getOutputStream());
	// 			toServer.println(fakeBlockB);
	// 			toServer.flush();
	// 			sock.close();
	// 		}
	// 	}catch (Exception x) {x.printStackTrace ();}
	// }
	
	//https://docs.oracle.com/javase/tutorial/security/apisign/step2.html#:~:text=A%20key%20pair%20is%20generated,with%20a%201024%2Dbit%20length.&text=The%20first%20step%20is%20to,for%20the%20DSA%20signature%20algorithm.
	public static void generateRandomKeypair() {
		try {
			KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
			SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
			keyGenerator.initialize(1024, random);

			keys = keyGenerator.generateKeyPair();
			System.out.println("Our public key: " + keys.getPublic() + "!\n");
		} catch (Exception e) {
			System.out.println(e);
		}
		
	}

	public static void sendPublicKey() {
		try{
			// convert public key to string to send over ssocket
			byte[] publicKeyBytes = keys.getPublic().getEncoded();
			String publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes);
			if (pid == 2) {
				// start the multicast when pid 2 starts
				publicKeyConnection.multicastData(publicKeyString);
			} else {
				// have process 0 and 1 wait for 2 to start sending public keys
				while (PublicKeyState.getProcessPublicKey(2) == null) {
					Thread.sleep(100);
				}
				publicKeyConnection.multicastData(publicKeyString);
			}

			// wait til all public keys have been received
			while(Arrays.asList(PublicKeyState.getPublicKeys()).contains(null)) {
				Thread.sleep(100);
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		
	}

    public static void main(String args[]){
		int q_len = 6;
		// if no arg default to 0
		if (args.length < 1) {
			pid = 0;
		} else {
			try {
				// parse arg to set pid
				pid = Integer.parseInt(args[0]);
			} catch(Exception e) {
				// if error parsing the int default to 0
				pid = 0;
			}
		}
		System.out.println("Michael Crabtree's BlockFramework control-c to quit.\n");
		System.out.println("Using processID " + pid + "\n");
		
		// new queue for incoming blocks
		final BlockingQueue<String> queue = new PriorityBlockingQueue<>();


		publicKeyConnection = new Connection(serverName, new PublicKeyPort(), pid);
		unverifiedBlockConnection = new Connection(serverName, new UnverifiedBlockPort(), pid);
		updatedBlockchainConnection = new Connection(serverName, new UpdatedBlockchainPort(), pid);

		generateRandomKeypair();


		new Thread(new PublicKeyServer()).start();
		// wait for all public keys to be exchanged before continuing
		sendPublicKey();

		// new Thread(new UnverifiedBlockServer(queue)).start(); // New thread to process incoming unverified blocks
		// new Thread(new BlockchainServer()).start(); // New thread to process incomming new blockchains
		// try{Thread.sleep(1000);}catch(Exception e){} // Wait for servers to start.
		// new Blockchain().MultiSend(); // Multicast some new unverified blocks out to all servers as data
		// try{Thread.sleep(1000);}catch(Exception e){} // Wait for multicast to fill incoming queue for our example.
		System.out.println("DONE");
		// new Thread(new UnverifiedBlockConsumer(queue)).start(); // Start consuming the queued-up unverified blocks
    }
}

