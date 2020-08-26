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
import java.text.SimpleDateFormat;
import java.util.concurrent.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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
	private int localProcessId;

	// init connection object
	public Connection(String serverName, Port portStrategy, int localProcessId) {
		this.serverName = serverName;
		this.portStrategy = portStrategy;
		this.localProcessId = localProcessId;
	}

	// send message to all processes
	public void multicastData(String message) {
		Request request = new Request(message, this.localProcessId);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Socket socket;
		PrintStream toServer;

		// Convert the Java object to a JSON String:
		String jsonRequest = gson.toJson(request);
		try {
			for(int processNum = 0; processNum <= 2; processNum++) {
					int port =  portStrategy.getPortByProcessId(processNum);
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

	public int getLocalPort() {
		return this.portStrategy.getPortByProcessId(localProcessId);
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
		int port = Blockchain.publicKeyConnection.getLocalPort();
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
			System.out.println("Got Public key for Process " + requestData.processId + ": " + requestData.message + "\n");

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
	BlockingQueue<Block> queue;

	UnverifiedBlockServer(BlockingQueue<Block> queue){
	  this.queue = queue; // Constructor binds our prioirty queue to the local variable.
	}
  
	/* Inner class to share priority queue. We are going to place the unverified blocks into this queue in the order we get
	   them, but they will be retrieved by a consumer process sorted by blockID. */ 
  
	class UnverifiedBlockWorker extends Thread {
		Socket socket;
		UnverifiedBlockWorker (Socket s) {
			socket = s;
		}
		public void run(){
			try{
				BufferedReader inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				// get whole request string one line at a time
				String data = "";
				String dataLine = "";
				do {
					data = data + dataLine;
					dataLine = inputReader.readLine();
				} while (dataLine != null);

				// convvert request json string to request object
				Request request = Json.fromJson(data, Request.class);

				System.out.println("Put in priority queue: " + request.message + "\n");
				// parse block from request message
				Block block = Json.fromJson(request.message, Block.class);

				// put block in queue
				queue.put(block);
				// socket.close(); 
			} catch (Exception e){
				e.printStackTrace();
			}
		}
	}
	
	public void run(){
		int q_len = 6;
		Socket socket;
		int port = Blockchain.unverifiedBlockConnection.getLocalPort();
		System.out.println("Starting the Unverified Block Server input thread using " + Integer.toString(port));
		try{
			ServerSocket serverSocket = new ServerSocket(port, q_len);
			while (true) {
				socket = serverSocket.accept(); // Got a new unverified block
				new UnverifiedBlockWorker(socket).start(); // So start a thread to process it.
			}
		} catch (IOException e) {
			System.out.println(e);
		}
	}
}

class BlockVerifier implements Runnable {
	BlockingQueue<Block> queue;

	BlockVerifier(BlockingQueue<Block> queue){
	  	this.queue = queue; // Constructor binds our prioirty queue to the local variable.
	}

	// https://www.geeksforgeeks.org/generate-random-string-of-given-size-in-java/
	public static String generateRandomSeed(int length) {
		// char string to randomy get chars from
		String charList = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		StringBuilder builder = new StringBuilder(length);
		// run loop for the length inputted in this function
		for (int i = 0; i < length; i++) {
			// get random index from char string
			int index = (int)(Math.random()*charList.length());
			//add to builder
			builder.append(charList.charAt(index));
		}
		return builder.toString();
	}

	public void run(){
		Block block;
	
		System.out.println("Starting the Unverified Block Priority Queue Consumer thread.\n");
		try{
			while(true){
				block = queue.take();
				System.out.println("Consumer got unverified Block: " + block.getBlockId());
				// clone the current blockchain
				Ledger ledgerClone = Blockchain.ledger.clone();

				while(!Blockchain.ledger.blockIdExists(block.getBlockId())) {
					String blockString = Json.toJson(block);
					String previousBlockHash = Blockchain.hashString(Json.toJson(Blockchain.ledger.getMostRecentBlock()));
					String seed = generateRandomSeed(10);

					String solution = Blockchain.hashString(blockString + previousBlockHash + seed);
					int intSolution = Integer.parseInt(solution.substring(0,4),16);

					// puzzle is solved
					if(intSolution == 1) {
						// set after verification data
						block.setBlockNumber(Blockchain.ledger.getMostRecentBlock().getBlockNumber() + 1);
						block.setHash(solution);
						block.setRandomSeed(seed);
						block.setVerifyingProcessId(Blockchain.pid);

						// format date down to milliseconds to avoid collision
						SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd.hh:mm:ss.SSS");
						Date date = new Date();
						String time = dateFormat.format(date);
						block.setVerifiedAtTimestamp(time);
						System.out.print(intSolution);
						// if no changes have happened since the work started
						if(ledgerClone.getBlockchain().equals(Blockchain.ledger.getBlockchain())) {
							// prepend the block and multicast it out
							ledgerClone.addBlock(block);
							Blockchain.updatedBlockchainConnection.multicastData(Json.toJson(ledgerClone));
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println(e);
		}
	}
}

class BlockchainWorker extends Thread { // Class definition
	Socket socket;

	BlockchainWorker(Socket s) {
		socket = s;
	}
	public void run(){
		try{
			BufferedReader inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			// get whole request string one line at a time
			String data = "";
			String dataLine = "";
			do {
				data = data + dataLine;
				dataLine = inputReader.readLine();
			} while (dataLine != null);

			// convvert request json string to request object
			Request request = Json.fromJson(data, Request.class);

			Ledger newLedger = Json.gson.fromJson(request.message, Ledger.class);
			Blockchain.ledger.setBlockchain(newLedger.getBlockchain());
			System.out.println("NEW BLOCKCHAIN-\n" + Json.toJson(Blockchain.ledger.getBlockchain()) + "\n\n");

			// process 0 writes blockchain to file after receiving new chiain
			if(Blockchain.pid) {
				// create new file if it doesnt exist
				File file = new File("BlockchainLedger.json");
				file.createNewFile();
				// write the json to the file
				FileWriter fileWriter = new FileWriter("BlockchainLedger.json");
				fileWriter.write(Json.toJson(Blockchain.ledger.getBlockchain()));
				fileWriter.close();
			}

			socket.close(); 
		} catch (IOException ioe){
			ioe.printStackTrace();
		}
	}
  }

class BlockchainServer implements Runnable {
	public void run(){
		int q_len = 6;
		Socket socket;
		int port = Blockchain.updatedBlockchainConnection.getLocalPort();

		System.out.println("Starting the blockchain server input thread using " + Integer.toString(port));
		try{
			ServerSocket serverSocket = new ServerSocket(port, q_len);
			while (true) {
				socket = serverSocket.accept();
				new BlockchainWorker(socket).start(); 
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

class Block {
	private String blockId;
	private String signedBlockId;
	private String previousHash;
	private String firstName;
	private String lastName;
	private String birthDate;
	private String ssn;
	private String diagnosis;
	private String treatment;
	private String prescription;
	private String randomSeed;
	private String hash;
	private String createdAtTimestamp;
	private String verifiedAtTimestamp;
	private int blockNumber;
	private int creationProcessId;
	private int verifyingProcessId;


	public String getBlockId() {
		return blockId;
	}
	public void setBlockId(String blockId) {
		this.blockId = blockId;
	}

	public String getSignedBlockId() {
		return signedBlockId;
	}

	public void setSignedBlockId(String signedBlockId) {
		this.signedBlockId = signedBlockId;
	}

	public String getPreviousHash() {
		return previousHash;
	}

	public void setPreviousHash(String previousHash) {
		this.previousHash = previousHash;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getBirthDate() {
		return birthDate;
	}

	public void setBirthDate(String birthDate) {
		this.birthDate = birthDate;
	}

	public String getSsn() {
		return ssn;
	}

	public void setSsn(String ssn) {
		this.ssn = ssn;
	}

	public String getDiagnosis() {
		return diagnosis;
	}

	public void setDiagnosis(String diagnosis) {
		this.diagnosis = diagnosis;
	}

	public String getTreatment() {
		return treatment;
	}

	public void setTreatment(String treatment) {
		this.treatment = treatment;
	}

	public String getPrescription() {
		return prescription;
	}

	public void setPrescription(String prescription) {
		this.prescription = prescription;
	}

	public String getRandomSeed() {
		return randomSeed;
	}

	public void setRandomSeed(String randomSeed) {
		this.randomSeed = randomSeed;
	}

	public String getCreatedAtTimestamp() {
		return createdAtTimestamp;
	}

	public void setCreatedAtTimestamp(String createdAtTimestamp) {
		this.createdAtTimestamp = createdAtTimestamp;
	}

	public String getVerifiedAtTimestamp() {
		return verifiedAtTimestamp;
	}

	public void setVerifiedAtTimestamp(String verifiedAtTimestamp) {
		this.verifiedAtTimestamp = verifiedAtTimestamp;
	}

	public int getVerifyingProcessId() {
		return verifyingProcessId;
	}

	public void setVerifyingProcessId(int verifyingProcessId) {
		this.verifyingProcessId = verifyingProcessId;
	}

	public int getCreationProcessId() {
		return creationProcessId;
	}

	public void setCreationProcessId(int creationProcessId) {
		this.creationProcessId = creationProcessId;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public int getBlockNumber() {
		return blockNumber;
	}

	public void setBlockNumber(int blockNumber) {
		this.blockNumber = blockNumber;
	}
}

// class to work with the current chain of blocks
class Ledger implements Cloneable {
	private List<Block> blockchain = new ArrayList<>();

	public Ledger() {
		// add genesis block to chain
		Block genesisBlock = new Block();
		genesisBlock.setBlockNumber(0);
		genesisBlock.setCreatedAtTimestamp("0000-00-00.00:00.000.0");
		blockchain.add(genesisBlock);
	}

	public List<Block> getBlockchain() {
		return this.blockchain;
	}

	public void setBlockchain(List<Block> blockchain) {
		this.blockchain = blockchain;
	}

	public void addBlock(Block block) {
		this.blockchain.add(0, block);
	}

	// return true if blockchin already has block with id
	public boolean blockIdExists(String blockId) {
		Iterator<Block> blockIterator = blockchain.iterator();

		//loop through block chain
		while(blockIterator.hasNext()) {
			Block block = blockIterator.next();
			// if block has same id as input return true
			if (block.getBlockId() != null && block.getBlockId().equals(blockId)) return true;
		}

		// after loop is done we know there was no match for the id
		return false;
	}

	public Block getMostRecentBlock() {
		return blockchain.get(0);
	}

	public Ledger clone() throws
                   CloneNotSupportedException 
    { 
        return (Ledger)super.clone(); 
    } 

}

// Class Blockchain. for BlockChain
public class Blockchain {
    static String serverName = "localhost";
	static Ledger ledger = new Ledger();
	static int numProcesses = 3; // Set this to match your batch execution file that starts N processes with args 0,1,2,..N
	static KeyPair keys;
	static int pid = 0; // Our process ID
	static Connection publicKeyConnection;
	static Connection unverifiedBlockConnection;
	static Connection updatedBlockchainConnection;

	// read txt file for unverified blocks
	// TODO: make it not hardcoded to specific filenames
	// https://www.geeksforgeeks.org/different-ways-reading-text-file-java/
    public void generateBlocksFromTxt() {
		try {
			String fileName = "BlockInput" + Integer.toString(pid) + ".txt";
			System.out.println("Reading File " + fileName + " for blockchain input");
			File file = new File(fileName);
			BufferedReader reader = new BufferedReader(new FileReader(file)); 

			String line;
			Block block = new Block();
			// format date down to milliseconds to avoid collision
			SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd.hh:mm:ss.SSS");  

			while ((line = reader.readLine()) != null) {
				// https://stackoverflow.com/questions/4674850/converting-a-sentence-string-to-a-string-array-of-words-in-java
				// split line by spaces and add each to corresponding block data
				String[] splitLine = line.split("\\s+");
				block.setFirstName(splitLine[0]);
				block.setLastName(splitLine[1]);
				block.setBirthDate(splitLine[2]);
				block.setSsn(splitLine[3]);
				block.setDiagnosis(splitLine[4]);
				block.setTreatment(splitLine[5]);
				block.setPrescription(splitLine[6]);

				// generate uuid and sign it
				String uuid = UUID.randomUUID().toString();
				String signedUuid = Base64.getEncoder().encodeToString(signData(uuid.getBytes(), keys.getPrivate()));
				// set block id and 
				block.setBlockId(uuid);
				block.setSignedBlockId(signedUuid);
				block.setCreationProcessId(pid);
				// generate a timestamp
				Date date = new Date();
				String time = dateFormat.format(date);
				// add process id to the end so there can't be identical timestamps
				block.setCreatedAtTimestamp(time + "." + pid);

				// multicast the data to the unverified block ports
				unverifiedBlockConnection.multicastData(Json.toJson(block));
				
				// simulate all blocks not being entered at the same time
				Thread.sleep(100);
			}
			reader.close();
		} catch (FileNotFoundException fnfe) {
			fnfe.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}
	
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

	public static String hashString(String string) {
		StringBuffer stringBuffer = new StringBuffer();
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(string.getBytes());
			byte byteData[] = md.digest();
			
			
			for (int i = 0; i < byteData.length; i++) {
				stringBuffer.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
			}
		} catch(Exception e) {
			e.getStackTrace();
		}

		return stringBuffer.toString();
	}

	public static byte[] signData(byte[] data, PrivateKey key) throws Exception {
		Signature signer = Signature.getInstance("SHA1withRSA");
		signer.initSign(key);
		signer.update(data);
		return (signer.sign());
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

		// create a comparator to sort by the string timestamp
		Comparator<Block> timestampSorter = new Comparator<Block>(){
			public int compare(Block block1, Block block2) {
				// sort by the timestamp string
				return block1.getCreatedAtTimestamp().compareToIgnoreCase(block2.getCreatedAtTimestamp());
			}			
		};
		// new queue for incoming blocks
		final BlockingQueue<Block> queue = new PriorityBlockingQueue<Block>(12, timestampSorter);

		// set up connections to each type of server
		publicKeyConnection = new Connection(serverName, new PublicKeyPort(), pid);
		unverifiedBlockConnection = new Connection(serverName, new UnverifiedBlockPort(), pid);
		updatedBlockchainConnection = new Connection(serverName, new UpdatedBlockchainPort(), pid);

		// generate the keypair for the current process
		generateRandomKeypair();

		// start server to receive public keys
		new Thread(new PublicKeyServer()).start();
		// wait for all public keys to be exchanged before continuing
		sendPublicKey();

		// server to receive unverified blocks
		new Thread(new UnverifiedBlockServer(queue)).start();
		// server to receive blackchain updates
		new Thread(new BlockchainServer()).start();
		 // Hacky way to make sure servers are up
		try{Thread.sleep(1000);}catch(Exception e){}
		new Blockchain().generateBlocksFromTxt();
		 // Make sure queue has data before continuing
		try{Thread.sleep(1000);}catch(Exception e){}

		// start verifying blocks in the queue
		new Thread(new BlockVerifier(queue)).start();
		
		System.out.println("DONE");
    }
}

