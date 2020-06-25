import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries

public class JokeClientAdmin {
    public static void main (String args[]) {
        String serverName;
        // check for cmd line arguments
        // if none default to localhost
        if (args.length < 1) {
            serverName = "localhost";
        } else { // if cmd line arg exists, use it for server name
            serverName = args[0];
        }

        System.out.println("Michael Crabtree's Joke Client, 1.8.\n");
        System.out.println("Using server: " + serverName + ", Port: 5050");

        //set up buffer for reading input
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));

        try {
            //first get the current state of the server mode
            sendInputToServer("GET", serverName);
            String input;
            do {
                // output instruction for user
                System.out.print("Press enter to change the mode of the Server: ");
                System.out.flush();
                // wait for user to press enter
                input = inputReader.readLine();
                //change server state as long as input wasnt to exit
                if(input != null) {
                    // change the state of the server mode
                    sendInputToServer("PUT", serverName);
                }
                
            } while (input != null);
        } catch (IOException e) {
            //print error
            e.printStackTrace();
        }
    }

    /*
     *   @param method string to send to server that determines whether the server will get the cuurent state or change the state
     *   @param address location of our server
    */
    static void sendInputToServer (String method, String serverName) {
        Socket socket;
        BufferedReader fromServer;
        PrintStream toServer;
        String result;

        try{
            //open socket to serverName at port 5050
            socket = new Socket(serverName, 5050);

            // set up buffers to get and send data to server 
            fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            toServer = new PrintStream(socket.getOutputStream());

            // Send machine name or IP address to server:
            toServer.println(method);
            toServer.flush();

            // print  server results as they return
            result = fromServer.readLine();
            if (result != null) {
                System.out.println(result);
            }
            // close socket after done with getting input
            socket.close();
        } catch (IOException e) {
            System.out.println ("Socket error.");
            e.printStackTrace();
        }
    }
}