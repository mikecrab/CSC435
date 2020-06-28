import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries
import java.util.UUID;

public class JokeClient {
    public static Server currentServer;
    public static Server primaryServer;
    public static Server secondaryServer;
    public static void main (String args[]) {
        String primaryServerName;
        String secondaryServerName = null;
    
        User user = new User(UUID.randomUUID());
        // check for cmd line arguments
        // if none default to localhost
        if (args.length < 1) {
            primaryServerName = "localhost";
        } else { // if cmd line arg exists, use it for server name
            primaryServerName = args[0];
            if(args.length == 2) secondaryServerName = args[1];
        }
        primaryServer = new Server(primaryServerName, 4545);

        currentServer = primaryServer;
        
        System.out.println("Michael Crabtree's Joke Client, 1.8.\n");
        System.out.println("Server one: " + primaryServer.getName() + ", port " + primaryServer.getPort());
        if(secondaryServerName != null) {
            secondaryServer = new Server(secondaryServerName, 4546);
            System.out.println("Server two: " + secondaryServer.getName() + ", port " + secondaryServer.getPort());
        }
        System.out.println("Now communicating with: " + currentServer.getName() + ", port " + currentServer.getPort());
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

                //toggle server if there is a second server and the command is "s"
                if(secondaryServerName != null && input.equals("s")) {
                    toggleServer();
                } else { //else just get the content from the server
                    // send input to server
                    getContent(user);
                }
                
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
        String result;
        result = sendInputToServer(request);
        //System.out.println(result);
    }

    // get content from the server
    // either joke or proverb, decided by state of server
    public static void getContent(User user) {
        Request request = new Request("getContent", user.getUserName());
        String result;
        result = sendInputToServer(request);

        if(result.contains("{user}")) {
            result = result.replace("{user}", user.getUserName());
        }
        System.out.println(result);
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
     *   @param searchInput string to send to server to use for looking up Host IP / Name pairs
     *   @param address location of our server
    */
    static String sendInputToServer (Request request) {
        Socket socket;
        BufferedReader fromServer;
        ObjectOutputStream toServer;
        String result;

        try{
            //open socket to serverName at port 4545
            socket = new Socket(currentServer.getName(), currentServer.getPort());

            // set up buffers to get and send data to server 
            fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            toServer = new ObjectOutputStream(socket.getOutputStream());

            // Send machine name or IP address to server:
            toServer.writeObject(request);
            toServer.flush();

            //get result from server
            result = fromServer.readLine();
            // close socket after done with getting input
            socket.close();
        } catch (IOException e) {
            result = "Socket error.";
            e.printStackTrace();
        }
        return result;
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