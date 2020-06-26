import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries
import java.util.UUID;

public class JokeClient {
    public static String serverName;
    public static void main (String args[]) {
        
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

        //set up buffer for reading input
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));

        try {
            System.out.print("Enter your name: ");
            System.out.flush();
            // get user input from reader
            user.setUserName(inputReader.readLine());
            //save user on server
            postUser(user);
            System.out.println("Hello " + user.getUserName() + "!");
            String input;
            do {
                // output instruction for user
                System.out.print("Press enter to get a Joke or Proverb: ");
                System.out.flush();
                // get user input from reader
                input = inputReader.readLine();

                // send input to server
                getContent(user);
            } while (!input.equals("quit") || input != null); // exit if user input is 'quit' 

            System.out.println ("Cancelled by user request.");
        } catch (IOException e) {
            //print error
            e.printStackTrace();
        }
    }

    // save new user on the server
    public static void postUser(User user) {
        Request request = new Request("postUser", user.getUserName());
        
        sendInputToServer(request, serverName);
    }

    // get content from the server
    // either joke or proverb, decided by state of server
    public static void getContent(User user) {
        Request request = new Request("getContent", user.getUserName());
        
        sendInputToServer(request, serverName);
    }

    /*
     *   @param searchInput string to send to server to use for looking up Host IP / Name pairs
     *   @param address location of our server
    */
    static void sendInputToServer (Request request, String serverName) {
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

// basic request object to get what the server needs to the server
class Request implements Serializable {
    // message to tell the server what to do
    public String message;
    //user id to tell the server who is making the request
    public String userId;

    public Request(String message, String userId) {
        this.message = message;
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public String getUserId() {
        return userId;
    }
}