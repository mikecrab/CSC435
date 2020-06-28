import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries

public class JokeClientAdmin {
    public static Server currentServer;
    public static Server primaryServer;
    public static Server secondaryServer;
    public static void main (String args[]) {
        String primaryServerName;
        String secondaryServerName = null;

        // check for cmd line arguments
        // if none default to localhost
        if (args.length < 1) {
            primaryServerName = "localhost";
        } else { // if cmd line arg exists, use it for server name
            primaryServerName = args[0];
            if(args.length == 2) secondaryServerName = args[1];
        }
        primaryServer = new Server(primaryServerName, 5050);

        currentServer = primaryServer;

        System.out.println("Michael Crabtree's Joke Client, 1.8.\n");
        System.out.println("Server one: " + primaryServer.getName() + ", port " + primaryServer.getPort());
        if(secondaryServerName != null) {
            secondaryServer = new Server(secondaryServerName, 5051);
            System.out.println("Server two: " + secondaryServer.getName() + ", port " + secondaryServer.getPort());
        }
        System.out.println("Now communicating with: " + currentServer.getName() + ", port " + currentServer.getPort());

        //set up buffer for reading input
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));

        try {
            //first get the current state of the server mode
            sendInputToServer("GET");
            String input;
            do {
                // output instruction for user
                System.out.print("Press enter to change the mode of the Server: ");
                System.out.flush();
                // wait for user to press enter
                input = inputReader.readLine();

                //toggle server if there is a second server and the command is "s"
                if(secondaryServerName != null && input.equals("s")) {
                    toggleServer();
                } else {
                    // change the state of the server mode
                    sendInputToServer("PUT");
                }
                
            } while (!input.equals("quit") || input != null);
        } catch (IOException e) {
            //print error
            e.printStackTrace();
        }
    }

   // change to the other server name
   public static void toggleServer() {
    if(currentServer == primaryServer) {
        currentServer = secondaryServer;
        System.out.println("Now communicating with: " + currentServer.getName() + ", port " + currentServer.getPort());
    } else {
        currentServer = primaryServer;
        System.out.println("Now communicating with: " + currentServer.getName() + ", port " + currentServer.getPort());
    }
}

    /*
     *   @param method string to send to server that determines whether the server will get the cuurent state or change the state
     *   @param address location of our server
    */
    static void sendInputToServer (String method) {
        Socket socket;
        BufferedReader fromServer;
        PrintStream toServer;
        String result;

        try{
            //open socket to serverName at port 5050
            socket = new Socket(currentServer.getName(), currentServer.getPort());

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

    // class to store server data to make it easy to switch servers
    class Server {
        // server name
        private String name;
        // server port number
        private Integer port;

        //set uuid on construct
        public Server(String name, Integer port) {
            this.name = name;
            this.port = port;
        }

        public String getName() {
            return name;
        }

        public Integer getPort() {
            return port;
        }
    }