import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

class JokeWorker extends Thread {
    // socket with connection to client
    Socket socket;
    boolean isSecondary;
    //construct worker
    public JokeWorker (Socket s, boolean secondary) {
        socket = s;
        isSecondary = secondary;
    } 

    public void run() {
        // init stream vars for input/output
        PrintStream outputStream = null;
        ObjectInputStream inputStream = null;

        try {
            inputStream =  new ObjectInputStream(socket.getInputStream());
            outputStream = new PrintStream(socket.getOutputStream());

            try {
                // get input from client
                Request input = (Request)inputStream.readObject();
                // get the data out of the request
                String message = input.getMessage();
                String userId = input.getUserId();
                String mode = ServerState.getMode();
                System.out.println(message);
                System.out.println(userId);

                // basic switch block
                // wont scale well but for what were doing right now it works fine
                switch(message) {
                    case "postUser":
                        ServerState.newUser(userId);
                        break;
                    case "getContent":
                        String ret = getContent(userId);
                        sendResponseToClient(ret, outputStream);
                        break;
                }
            } catch (IOException e) {
                System.out.println("Server read error");
                e.printStackTrace ();
            } catch (ClassNotFoundException e){
                System.out.println("Server read error");
                e.printStackTrace ();
            }
            socket.close();
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    // output data back top client
    public void sendResponseToClient(String response, PrintStream outputStream) {
        if(isSecondary) response = "<S2> " + response;

        outputStream.println(response);
    }

    public String getContent(String userId) {
        String mode = ServerState.getMode();
        ArrayList<String> content = ServerState.getContent();
        ArrayList<String> doneContent = ServerState.getUserStateByUserId(userId);
        Random rand = new Random();
        String ret;

        // filter out content that has already been done
        ArrayList<String> potentialContent = new ArrayList<String>();
        content.forEach((cont) -> {
            if(!doneContent.contains(cont)) {
                potentialContent.add(cont);
            }
        });

        int index = rand.nextInt(potentialContent.size());

        ret = potentialContent.get(index);
        ServerState.appendContentToUserState(userId, ret);
        return ret;
    }


    // Not interesting to us:
    static String toText (byte ip[]) { /* Make portable for 128 bit format */
        StringBuffer result = new StringBuffer ();
        for (int i = 0; i < ip.length; ++ i) {
            if (i > 0) {
                result.append (".");
            }
            result.append (0xff & ip[i]);
        }

        return result.toString ();
    }
}

class ServerState {
    //var to hold state of mode
    private static String mode = "joke";
    // map of client state
    // is mapped userId to another hashmap of joke / proverb key to an array of the jokes / proverbs that client has already heard
    // JSON structure equal to
    // {
    //     "{userId}": {
    //         "joke": [],
    //         "proverb": []
    //     }, 
    //     ...
    // }
    private static HashMap<String, HashMap<String, ArrayList<String>>> userState = new HashMap<String, HashMap<String, ArrayList<String>>>();

    //jokes from https://www.reddit.com/r/AskReddit/comments/5z4b1u/whats_a_short_clean_joke_that_gets_a_laugh_every/
    final static ArrayList<String> jokes = new ArrayList<String>(Arrays.asList(
        "JA {user}: A woman walks into a library and asked if they had any books about paranoia. Librarian: They're right behind you!!",
        "JB {user}: My wife told me I had to stop acting like a flamingo. So I had to put my foot down.",
        "JC {user}: What's the difference between a good joke and a bad joke timing.",
        "JD {user}: It's hard to explain puns to kleptomaniacs because they always take things literally."
    ));

    //proverbs from https://www.reddit.com/r/AskReddit/comments/2nyrld/hey_reddit_what_is_your_favorite_proverbsaying/
    final static ArrayList<String> proverbs = new ArrayList<String>(Arrays.asList(
        "PA {user}: Chop your own wood, it'll warm you twice.",
        "PB {user}: Comparison is the thief of joy.",
        "PC {user}: You can easily judge the character of a man by how he treats those who can do nothing for him.",
        "PD {user}: I never learned from a man who agreed with me."
    ));

    public static void toggleMode() {
        if(mode.equals("joke")) {
            mode = "proverb";
        } else {
            mode = "joke";
        }
    }

    public static String getMode() {
        return mode;
    }

    public static ArrayList<String> getContent() {
        if(mode.equals("joke")) {
            return jokes;
        } else {
            return proverbs;
        }
    }

    public static ArrayList<String> getUserStateByUserId(String userId) {
        if(userState.get(userId) == null) {
            newUser(userId);
        }

        return userState.get(userId).get(mode);
    }

    public static void newUser(String userId) {
        userState.put(userId, new HashMap<String, ArrayList<String>>() {{ 
            put("joke", new ArrayList<String>());
            put("proverb", new ArrayList<String>());
        }});
    }

    public static void appendContentToUserState(String userId, String content) {
        int contentSize = getContent().size();
        if(userState.get(userId).get(mode).size() == contentSize - 1) {
            userState.get(userId).get(mode).clear();
        } else {
            userState.get(userId).get(mode).add(content);
        }
        
    }
}

public class JokeServer {

    public static void main(String args[]) throws IOException {
        int maxQueueLength = 6;
        int port = 4545;
        Socket socket;
        boolean secondary = false;

        if (args.length > 0 && args[0].equals("secondary")) {
            port = 4546;
            secondary = true;          
        }

        AdminLooper adminLooper = new AdminLooper(secondary); // create a DIFFERENT thread
        Thread thread = new Thread(adminLooper);
        thread.start();  // ...and start it, waiting for administration input

        // construct server socket to set up connection with clients
        ServerSocket serverSocket = new ServerSocket(port, maxQueueLength);

        System.out.println("Michael Crabtree's Joke server 1.8 starting up, listening at port " + port + ".\n");
        while (true) {
            // wait for the next client connection
            socket = serverSocket.accept(); 
            //construct the worker that handles the request from the client
            new JokeWorker(socket, secondary).start();
        }
    }
}

class AdminWorker extends Thread {
    // socket with connection to client
    Socket socket; 
    
    //construct worker
    public AdminWorker(Socket s) {
        socket = s;
    } 

    public void run() {
        // init stream vars for input/output
        PrintStream outputStream = null;
        BufferedReader inputStream = null;

        try {
            inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputStream = new PrintStream(socket.getOutputStream());
            
            try {
                String method;
                // get request method of client
                method = inputStream.readLine();

                if(method.equals("GET")) {
                    outputStream.println("Server is currently in " + ServerState.getMode() + " mode");
                } else if(method.equals("PUT")) {
                    ServerState.toggleMode();
                    outputStream.println("Server has been changed to " + ServerState.getMode() + " mode");
                }
            } catch (IOException e) {
                System.out.println("Server read error");
                e.printStackTrace ();
            }
            socket.close();
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    // Not interesting to us:
    static String toText (byte ip[]) { /* Make portable for 128 bit format */
        StringBuffer result = new StringBuffer ();
        for (int i = 0; i < ip.length; ++ i) {
            if (i > 0) {
                result.append (".");
            }
            result.append (0xff & ip[i]);
        }

        return result.toString ();
    }
}


class AdminLooper implements Runnable {
    public static boolean adminControlSwitch = true;
    public static boolean isSecondary;

    public AdminLooper(boolean secondary) {
        isSecondary = secondary;
    }

    public void run() {
        System.out.println("In the admin looper thread");
        
        int maxQueueLength = 6; /* Number of requests for OpSys to queue */
        int port = 5050;  // We are listening at a different port for Admin clients
        Socket socket;

        if (isSecondary) {
            port = 5051;
        }

        try{
            ServerSocket serverSocket = new ServerSocket(port, maxQueueLength);
            while (adminControlSwitch) {
                // wait for the next ADMIN client connection:
                socket = serverSocket.accept();
                new AdminWorker(socket).start();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
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