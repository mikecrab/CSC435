import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries
import java.util.UUID;

public class JokeClient {
    public static void main (String args[]) {
        String serverName;
        User user = new User(UUID.randomUUID());
        // check for cmd line arguments
        // if none default to localhost
        if (args.length < 1) {
            serverName = "localhost";
        } else { // if cmd line arg exists, use it for server name
            serverName = args[0];
        }

        System.out.println("Michael Crabtree's Joke Client, 1.8.\n");
        System.out.println("Using server: " + serverName + ", Port: 4545");

        
        // send input to server
        sendInputToServer("get", user.getUserId(), serverName);

        //set up buffer for reading input
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));

        try {
            // var to hold user input
            String userName;
            System.out.print("Enter your name: ");
            System.out.flush();
            // get user input from reader
            userName = inputReader.readLine();
            String input;
            do {
                // output instruction for user
                System.out.print("Enter a hostname or an IP address, (quit) to end: ");
                System.out.flush();
                // get user input from reader
                input = inputReader.readLine();

                // send input to server
                sendInputToServer("get", user.getUserId(), serverName);
            } while (!input.equals("quit") || input != null); // exit if user input is 'quit' 

            System.out.println ("Cancelled by user request.");
        } catch (IOException e) {
            //print error
            e.printStackTrace();
        }
    }

    /*
     *   @param searchInput string to send to server to use for looking up Host IP / Name pairs
     *   @param address location of our server
    */
    static void sendInputToServer (String method, UUID userId, String serverName) {
        Socket socket;
        BufferedReader fromServer;
        ObjectOutputStream toServer;
        String result;

        try{
            //open socket to serverName at port 4545
            socket = new Socket(serverName, 4545);

            // set up buffers to get and send data to server 
            fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            toServer = new ObjectOutputStream(socket.getOutputStream());
            Request request = new Request();
            // Send machine name or IP address to server:
            toServer.writeObject(request);
            toServer.flush();

            // print  server results as they return
            // will be 1-3 lines
            for (int i = 1; i <=3; i++){
                result = fromServer.readLine();
                if (result != null) {
                    System.out.println(result);
                }
            }
            // close socket after done with getting input
            socket.close();
        } catch (IOException e) {
            System.out.println ("Socket error.");
            e.printStackTrace();
        }
    }
}

// class to store data on user
class User {
    //store username and userId
    //only storing userName on client to modify jokes after they are returned
    private String userName;
    //will also store userId on server to get user state
    private UUID userId;

    //set uuid on construct
    public User(UUID userId) {
        this.userId = userId;
    }

    //get userId
    public UUID getUserId() {
        return this.userId;
    }

    //get username
    public String getUserName() {
        return userName;
    }

    //set username
    public void setUserName(String userName) {
        this.userName = userName;
    }
}

class Request implements Serializable{
    public String method = "GET";
    public String endpoint = "/user";
    public String userId = "qqqq";

    public String getMethod() {
        return this.method;
    }
}