import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries
import java.util.HashMap;
import java.util.ArrayList;

class JokeWorker extends Thread {
    // socket with connection to client
    Socket socket;

    //construct worker
    public JokeWorker (Socket s) {
        socket = s;
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
                System.out.println(input.getMethod());

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
    // is mapped clientId to another hashmap of joke / proverb key to an array of the jokes / proverbs that client has already heard
    // JSON structure equal to
    // {
    //     "{clientId}": {
    //         "joke": [],
    //         "proverb": []
    //     }, 
    //     ...
    // }
    private static HashMap<String, HashMap<String, ArrayList<String>>> clientState = new HashMap<String, HashMap<String, ArrayList<String>>>();

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

    public static ArrayList<String> getClientStateByClientId(String clientId) {
        if(clientState.containsKey(clientId)) {
            return clientState.get(clientId).get(mode);
        } else {
            clientState.put(clientId, new HashMap<String, ArrayList<String>>() {{ 
                put("joke", new ArrayList<String>());
                put("proverb", new ArrayList<String>());
            }});

            return clientState.get(clientId).get(mode);
        }
    }

    public static void appendClientState(String clientId) {

    }
}

public class JokeServer {

    public static void main(String a[]) throws IOException {
        int maxQueueLength = 6;
        int port = 4545;
        Socket socket;

        AdminLooper adminLooper = new AdminLooper(); // create a DIFFERENT thread
        Thread thread = new Thread(adminLooper);
        thread.start();  // ...and start it, waiting for administration input

        // construct server socket to set up connection with clients
        ServerSocket serverSocket = new ServerSocket(port, maxQueueLength);

        System.out.println("Michael Crabtree's Joke server 1.8 starting up, listening at port 4545.\n");
        while (true) {
            // wait for the next client connection
            socket = serverSocket.accept(); 
            //construct the worker that handles the request from the client
            new JokeWorker(socket).start(); 
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
  
    public void run() {
        System.out.println("In the admin looper thread");
        
        int maxQueueLength = 6; /* Number of requests for OpSys to queue */
        int port = 5050;  // We are listening at a different port for Admin clients
        Socket socket;

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

class Request implements Serializable{
    public String method = "GET";
    public String endpoint = "/user";
    public String userId = "qqqq";

    public String getMethod() {
        return this.method;
    }
}