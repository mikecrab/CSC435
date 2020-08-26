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
import java.util.concurrent.*;

// Set static ports for specific process ID
class Ports{
	private static int publicKeyPort = 4710;
	private static int unverifiedBlockPort = 4820;
	private static int updatedBlockchainPort = 4930;
  
	public void setPorts(){
		publicKeyPort = publicKeyPort + Blockchain.pid;
		unverifiedBlockPort = unverifiedBlockPort + Blockchain.pid;
		updatedBlockchainPort = updatedBlockchainPort + Blockchain.pid;
	}

	public static int getPublicKeyPort() {
		return publicKeyPort;
	}

	public static int getUnverifiedBlockPort() {
		return unverifiedBlockPort;
	}

	public static int getUpdatedBlockchainPort() {
		return updatedBlockchainPort;
	}
}

class PublicKeyServer implements Runnable {
	//public ProcessBlock[] PBlock = new ProcessBlock[3]; // One block to store info for each process.
		
	public void run(){
		int q_len = 6;
		Socket sock;
		System.out.println("Starting Key Server input thread using " + Integer.toString(Ports.getPublicKeyPort()));
		try{
			ServerSocket servsock = new ServerSocket(Ports.getPublicKeyPort(), q_len);
			while (true) {
				sock = servsock.accept();
				//new PublicKeyWorker (sock).start(); 
			}
		} catch (IOException e) {
			System.out.println(e);
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
		Socket sock;
		System.out.println("Starting the Unverified Block Server input thread using " + Integer.toString(Ports.getUnverifiedBlockPort()));
		try{
			ServerSocket servsock = new ServerSocket(Ports.getUnverifiedBlockPort(), q_len);
			while (true) {
				sock = servsock.accept(); // Got a new unverified block
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
		String data;
		PrintStream toServer;
		Socket sock;
		String newblockchain;
		String fakeVerifiedBlock;
	
		System.out.println("Starting the Unverified Block Priority Queue Consumer thread.\n");
		try{
			while(true){ // Consume from the incoming queue. Do the work to verify. Mulitcast new blockchain
				data = queue.take(); // Will blocked-wait on empty queue
				System.out.println("Consumer got unverified: " + data);
				
				// Ordindarily we would do real work here, based on the incoming data.
				int j; // Here we fake doing some work (That is, here we could cheat, so not ACTUAL work..)
				for(int i=0; i< 100; i++){ // put a limit on the fake work for this example
					j = ThreadLocalRandom.current().nextInt(0,10);
					try{Thread.sleep(500);}catch(Exception e){e.printStackTrace();}
					if (j < 3) break; // <- how hard our fake work is; about 1.5 seconds.
				}
			
				/* With duplicate blocks that have been verified by different procs ordinarily we would keep only the one with
						the lowest verification timestamp. For the exmple we use a crude filter, which also may let some dups through */
				if(Blockchain.blockchain.indexOf(data.substring(1, 9)) < 0) { // Crude, but excludes most duplicates. 
					fakeVerifiedBlock = "[" + data + " verified by P" + Blockchain.pid + " at time " 
					+ Integer.toString(ThreadLocalRandom.current().nextInt(100,1000)) + "]\n";
					System.out.println(fakeVerifiedBlock);
					String tempblockchain = fakeVerifiedBlock + Blockchain.blockchain; // add the verified block to the chain
					for(int i=0; i < Blockchain.numProcesses; i++){ // send to each process in group, including us:
						sock = new Socket(Blockchain.serverName, Ports.getUpdatedBlockchainPort() + (i * 1000));
						toServer = new PrintStream(sock.getOutputStream());
						toServer.println(tempblockchain); toServer.flush(); // make the multicast
						sock.close();
					}
				}
				Thread.sleep(1500); // For the example, wait for our blockchain to be updated before processing a new block
			}
		} catch (Exception e) {
			System.out.println(e);
		}
	}
}

class BlockchainServer implements Runnable {
	public void run(){
		int q_len = 6;
		Socket sock;
		System.out.println("Starting the blockchain server input thread using " + Integer.toString(Ports.getUpdatedBlockchainPort()));
		try{
			ServerSocket servsock = new ServerSocket(Ports.getUpdatedBlockchainPort(), q_len);
			while (true) {
				sock = servsock.accept();
				//new BlockchainWorker (sock).start(); 
			}
		}	catch (IOException e) {
				System.out.println(e);
			}
	}
}

// Class Blockchain. for BlockChain
public class Blockchain {
    static String serverName = "localhost";
    static String blockchain = "[First block]";
    static int numProcesses = 3; // Set this to match your batch execution file that starts N processes with args 0,1,2,..N
    static int pid = 0; // Our process ID
  
    public void MultiSend (){ // Multicast some data to each of the processes.
		Socket sock;
		PrintStream toServer;
	
		try{
			for(int i=0; i< numProcesses; i++) {// Send our key to all servers.
				sock = new Socket(serverName, Ports.getPublicKeyPort() + (i * 1000));
				toServer = new PrintStream(sock.getOutputStream());
				toServer.println("FakeKeyProcess" + Blockchain.pid); toServer.flush();
				sock.close();
			} 
			Thread.sleep(1000); // wait for keys to settle, normally would wait for an ack
			//Fancy arithmetic is just to generate identifiable blockIDs out of numerical sort order:
			String fakeBlockA = "(Block#" + Integer.toString(((Blockchain.pid+1)*10)+4) + " from P"+ Blockchain.pid + ")";
			String fakeBlockB = "(Block#" + Integer.toString(((Blockchain.pid+1)*10)+3) + " from P"+ Blockchain.pid + ")";
			for(int i=0; i< numProcesses; i++) {// Send a sample unverified block A to each server
				sock = new Socket(serverName, Ports.getUnverifiedBlockPort() + (i * 1000));
				toServer = new PrintStream(sock.getOutputStream());
				toServer.println(fakeBlockA);
				toServer.flush();
				sock.close();
			}
			for(int i=0; i< numProcesses; i++){// Send a sample unverified block B to each server
				sock = new Socket(serverName, Ports.getUnverifiedBlockPort() + (i * 1000));
				toServer = new PrintStream(sock.getOutputStream());
				toServer.println(fakeBlockB);
				toServer.flush();
				sock.close();
			}
		}catch (Exception x) {x.printStackTrace ();}
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
		// set port based on process id
		new Ports().setPorts();
		
		new Thread(new PublicKeyServer()).start(); // New thread to process incoming public keys
		new Thread(new UnverifiedBlockServer(queue)).start(); // New thread to process incoming unverified blocks
		new Thread(new BlockchainServer()).start(); // New thread to process incomming new blockchains
		try{Thread.sleep(1000);}catch(Exception e){} // Wait for servers to start.
		new Blockchain().MultiSend(); // Multicast some new unverified blocks out to all servers as data
		try{Thread.sleep(1000);}catch(Exception e){} // Wait for multicast to fill incoming queue for our example.
		
		new Thread(new UnverifiedBlockConsumer(queue)).start(); // Start consuming the queued-up unverified blocks
    }
}