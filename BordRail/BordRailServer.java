/* BordRailServer.java - server program to manage a scoreboard in response to 
 *   commands from clients (see readme.txt). 
 *
 * Multithreaded - each client is handled by its own thread. 
 *   Methods which may concurrently modify shared data are declared 'synchronzed'
 */
import java.io.*;   
import java.net.*;  
import java.util.*; 

public class BordRailServer {

    /*********************** Data record definitions ************************/
    class UserRcd {
        int userID;
        String name;
        String address;
        String pwd;

        public UserRcd(int id, String n, String a, String p) {
            userID = id; name = n; address = a; pwd = p;
        }

        public String toString() { 
            return String.format("%d %s %s %s", userID, name, address, pwd); 
        }
    }

    class RouteRcd {
        int rID;
        float cost;
        String routeDesc, type, typeDesc;

        public RouteRcd(int r, String rd, float c, String td, String t) {
            rID = r; routeDesc = rd; cost = c; typeDesc = td; type = t;
        }

        public String toString() { 
            return String.format("%d %s %s (%s)", rID, routeDesc, typeDesc, type);
        }
    }

    class TimetableRcd {
        int rID;
        String day, time;

        public TimetableRcd (int r, String d, String t){
            rID = r; day = d; time = t;
        }

        public String toString() {
            return String.format("%d %s %s", rID, day, time);
        }
    }

    /************************* Buffer setup & data **************************/
    public static final int BUFFSZ = 80;
    public static final String fieldSep = "#";
    public static final String endMkr = ">";
    private ArrayList<UserRcd> userData = new ArrayList<UserRcd>();
    private ArrayList<RouteRcd> routeData = new ArrayList<RouteRcd>();
    private ArrayList<TimetableRcd> tTableData = new ArrayList<TimetableRcd>();

    /************************** Other global data ***************************/
    private ServerSocket servSocket = null;
    private ArrayList<Thread> serviceThreads = new ArrayList<Thread>();
    private int connCount = 0;
    private boolean serverUp = false;
    private int userID = 0;

    /**************************** MAIN ***************************/
    public static void main(String[] args) {
        int port;
        if (args.length == 0)  {
            System.err.println("Usage:  java BordRailServer <PORT>\n");
            return;
        }
        port = Integer.parseInt(args[0]);
        BordRailServer server = new BordRailServer(port);
    }

    /**************************** Constructor *****************************/
    public BordRailServer(int port) {
        loadUsers();
        System.out.printf("%d user records read\n", userData.size());
        for (UserRcd rcd: userData)
            System.out.println(rcd);
        System.out.println();

        loadRoutes();
        System.out.printf("%d route records read\n", routeData.size());
        for (RouteRcd rcd: routeData)
            System.out.println(rcd);
        System.out.println();

        loadTimetable();
        System.out.printf("%d timetable records read\n", tTableData.size());
        for (TimetableRcd rcd: tTableData)
            System.out.println(rcd);
        System.out.println();

        runServer(port);
    }

    /************************ Data helper functions *************************/
    // Populate ArrayList of user records from file
    private void loadUsers() {
        try {
            Scanner file = new Scanner(new BufferedReader(new FileReader("users.txt")));
            file.useDelimiter("[,\\n]");
            while (file.hasNext()) {
                int uID = file.nextInt();
                String name = file.next().trim(), addr = file.next().trim(), pwd = file.next().trim(); 
                userData.add(new UserRcd(uID, name, addr, pwd));
            }
            file.close();
        } catch(IOException ex) {
            System.err.println("Could not open users file for reading");
        }
    }

    // Populate ArrayList of route records from file
    private void loadRoutes() {
        try {
            Scanner file = new Scanner(new BufferedReader(
                        new FileReader("routes.txt")));
            file.useDelimiter("[,\\n]");
            while (file.hasNext()) {
                int rID = file.nextInt();
                String rDesc = file.next().trim();
                float cost = Float.parseFloat(file.next().trim());
                String tDesc = file.next().trim() , type = file.next().trim(); 
                routeData.add(new RouteRcd(rID, rDesc, cost, tDesc, type));
            }
            file.close();
        } catch(IOException ex) {
            System.err.println("Could not open routes file for reading");
        }
    }

    // Populate ArrayList of timetable entries from file
    private void loadTimetable() {
        try {
            Scanner file = new Scanner(new BufferedReader(new FileReader("timetable.txt")));
            file.useDelimiter("[,\\n]");
            while (file.hasNext()) {
                int rID = file.nextInt();
                String day = file.next().trim(), time = file.next().trim(); 
                tTableData.add(new TimetableRcd(rID, day, time));
            }
            file.close();
        } catch(IOException ex) {
            System.err.println("Could not open tiemtable file for reading");
        }
    }

    // Save ArrayList of score records to file
    private void saveScores() {
        int ct = 0;
        try {
            PrintWriter out = new PrintWriter(new FileWriter("test.txt"));
            for (RouteRcd rcd: routeData) {
                out.println(rcd.toString());
                ct++;
            }
            out.close();
        } catch(IOException ex) {
            System.err.println("Could not open scores file for writing");
        }
        System.out.printf("%d score records written to file\n", ct);
    }

    /************************** Run server ****************************/
    public void runServer(int port) {
        Thread newThread = null;
        serverUp = true;
        try {
            servSocket = new ServerSocket(port, 20);
            //this will loop untill all open connections terminated
            while (serverUp) {
                System.out.printf("Server waiting for connection request on port %d\n", port);
                //will block here until a client requests to connect 
                newThread = new ServiceThread(servSocket.accept(), connCount);
                //continues once a client has requested to connect
                connCount++;
                serviceThreads.add(newThread);
                System.out.printf("Now there are %d service threads\n", serviceThreads.size());
                newThread.start();
            }
            if (serviceThreads.size() > 0) {
                System.out.printf("WARNING: there are still %d service threads active\n", serviceThreads.size());
            }
            if (servSocket != null) {
                servSocket.close();
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }

        Scanner console = new Scanner(System.in);
        System.out.println("Once all service threads are finished, ENTER to confirm shutdown");
        console.nextLine();
    }

    // The server thread class
    class ServiceThread extends Thread {
        private Socket conn;
        private DataOutputStream output;
        private DataInputStream input;
        boolean loggedIn;

        ServiceThread(Socket c, int sID) { //constructor
            conn = c;
            setName("Conn_"+sID);
            loggedIn = false;
        }

        /******** The 'main' code to serve a particular client. ********* 
         * Runs in its own thread.                                      */
        public void run() {
            byte[] buffer = new byte[BUFFSZ];
            int len=0;
            String recStg;
            String[] recData;
            try {
                System.out.printf("Thread %s serving client %s\n",
                    getName(), conn.getInetAddress().getHostName());
                output = new DataOutputStream(conn.getOutputStream());
                input = new DataInputStream(conn.getInputStream());
                conn.setTcpNoDelay(true);
                System.out.printf("Thread %s has I-O streams\n", getName());

                boolean running = true;
                while(running) {
                    recStg = new String();
                    do {
                        len = input.read(buffer);
                        System.out.printf("%d bytes received\n", len);
                        recStg += new String(buffer, 0, len);
                    } while (!recStg.contains(endMkr) && recStg.length() < BUFFSZ);
                    recData = recStg.substring(0, recStg.indexOf(endMkr)).split(fieldSep);
                    String recMsg = recData[0];

                    switch (recMsg) {
                        case "LOGIN": {
                            try {
                                loggedIn = login(Integer.parseInt(recData[1]), recData[2], output);
                            } 
                            catch (ArrayIndexOutOfBoundsException ex) {
                                System.err.println("missing login details from client");
                            }
                            catch (NumberFormatException e) {
                                sendMsg(output,"Route ID should be a number");
                            }
                        }
                        break;
                        case "LOGOUT": {
                            if (loggedIn) {
                                loggedIn = false;
                                sendMsg(output,"You have been logged out");
                            }
                            else 
                                sendMsg(output,"Not currently logged in");
                        }
                        break;
                        case "ALLTK": {
                            sendAllTk(output);
                        }
                        break;
                        case "TRVL": {
                            try {
                                sendTimetable(output, Integer.parseInt(recData[1]));
                            } 
                            catch (ArrayIndexOutOfBoundsException ex) {
                                System.err.println("Client data missing route id");
                            }
                            catch (NumberFormatException e) {
                                sendMsg(output,"Route ID should be a number");
                            }
                        }
                        break;
                        case "RUN": {
                            try {
                                sendDayTime(output, Integer.parseInt(recData[1]),recData[2]);
                            } 
                            catch (ArrayIndexOutOfBoundsException ex) {
                                System.err.println("Client data missing either route id or day");
                            }
                            catch (NumberFormatException e) {
                                sendMsg(output,"Route ID should be a number");
                            }
                        }
                        break;
                        case "COST": {
                            try {
                                sendCost(output, Integer.parseInt(recData[1]));
                            } 
                            catch (ArrayIndexOutOfBoundsException ex) {
                                System.err.println("Client data missing route id");
                            }
                            catch (NumberFormatException e) {
                                sendMsg(output,"Route ID should be a number");
                            }
                        }
                        break;
                        case "BKD": {
                            if (loggedIn) {
                                try {
                                    bookTicket(output, Integer.parseInt(recData[1]),recData[2]);
                                }
                                catch (NumberFormatException e) {
                                    sendMsg(output,"Route ID should be a number");
                                }
                            }
                            else 
                                sendMsg(output, "You need to be logged in.");
                        }
                        break;
                        case "BKDT": {
                            if (loggedIn) {
                                try {
                                    bookSaverTicket(output, Integer.parseInt(recData[1]), recData[2], recData[3]);
                                }
                                catch (NumberFormatException e) {
                                    sendMsg(output,"Route ID should be a number");
                                }
                            }
                            else 
                                sendMsg(output, "You need to be logged in.");
                        }
                        break;
                        case "TERM": {
                            loggedIn = false;
                            running = false;
                            sendMsg(output, "Goodbye.");
                        }
                        break;
                        case "DOWN": {
                            if (loggedIn) {
                                serverUp = false;
                                loggedIn = false;
                                sendMsg(output, "Server going Down.");
                            } else {
                                sendMsg(output, "You need to be logged in.");
                            }
                        }
                        break;
                        default: {
                            System.err.println("Empty request!");
                        }
                        break;
                    }
                } 
                input.close(); 
                output.close(); 
                conn.close();
                System.out.printf("Connection %s done\n", getName());
                if (serviceThreads.remove(this))
                    System.out.println("Service thread deleted");
            }
            catch (EOFException ex) {
                System.err.printf("Unexpected EOF: %s", ex);
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    //function to authenticate a user
    private boolean login(int usrID, String pwd, DataOutputStream output) throws IOException {
        boolean loggedIn = false;
        int count = 0;
        String error = "";
        System.out.printf("Login %s: %s\n", usrID, pwd);
        for (UserRcd usr: userData) {
            if (usr.userID == usrID) {
                if (usr.pwd.equals(pwd)) {
                    output.writeBytes("\nLogged in as: " + usr.name);
                    loggedIn = true;
                    userID = usr.userID;
                    count++;
                }
                else
                    error = "\nIncorrect Password";
            }

        }
        if (count == 0) {
            error = "\nIncorrect user ID"; 
        }
        output.writeBytes(error + "\n");
        output.flush();
        return loggedIn;
    }

    /********************* Helper Methods to respond to client commands **********************/
    //General-purpose messaging function
    private void sendMsg(DataOutputStream output, String msg) throws IOException {
        output.writeBytes(String.format("%s\n\n", msg));
        output.flush();
    }

    /* Send all routes to a client
     * Must be synchronized because thread should obtain an exclusive
     * lock before accessing the array. */
    private synchronized void sendAllTk(DataOutputStream output) throws IOException {
        output.writeBytes("All Tickets\n");
        for (RouteRcd rcd: routeData) {
            output.writeBytes((rcd.toString()+"\n"));
        }
        output.writeBytes("\n");
        output.flush();
    }

    /* Send day(s) matching a route id to a client; send a message if no match. */
    private synchronized void sendTimetable(DataOutputStream output, int rID) throws IOException {
        int count = 0;
        List<String> days = new ArrayList<String>();
        output.writeBytes("Day Information for: " + rID + "\n");
        for (TimetableRcd rcd: tTableData) {
            if (rcd.rID == rID) {
                count++;
                if (!days.contains(rcd.day)) {
                    days.add(rcd.day);
                    output.writeBytes((rcd.day+"\n"));
                }
            }
        }
        if (count == 0)
            output.writeBytes(("route has no timetable information\n"));
        output.writeBytes("\n");
        output.flush();
    }

    /* Send cost information for a matching route id to a client */
    private synchronized void sendCost(DataOutputStream output, int rID) throws IOException {
        int count = 0;
        output.writeBytes("Cost Information for: "+rID+"\n");
        for (RouteRcd rcd: routeData) {
            if (rcd.rID == rID) {
                String cost = String.format("GBP: %.2f",rcd.cost);
                output.writeBytes((cost+"\n"));
                count++;
            }
        }
        if (count == 0)
            output.writeBytes(("route ID does not exist\n"));
        output.writeBytes("\n");
        output.flush();
    }

    /* Send time(s) matching a route id to a client */
    private synchronized void sendDayTime(DataOutputStream output, int rID, String day) throws IOException {
        int count = 0;
        output.writeBytes("Time Information for: "+rID+" on: "+day+"\n");
        for (TimetableRcd rcd: tTableData) {
            if (rcd.rID == rID  && rcd.day.equals(day)) {
                output.writeBytes((rcd.time+"\n"));
                count++;
            }
        }
        if (count == 0) {
            output.writeBytes(("route has no timetable information\n"));
        }
        output.writeBytes("\n");
        output.flush();
    }

    private synchronized void bookTicket (DataOutputStream output, int rID, String day) throws IOException {
        int count = 0;
        boolean saver = true, booked = false;
        output.writeBytes("\n");
        for (RouteRcd rcd: routeData) {
            if (rcd.rID == rID) {
                saver = rcd.type.equals("saver");
            }
        }
        for (TimetableRcd rcd: tTableData) {
            if (rcd.rID == rID  && rcd.day.equals(day) && !saver) {
                count++;
                if (!booked) {
                    booked = true;
                    String outTxt = String.format("%d, %d, %s\n",rID, userID, day);
                    try(FileWriter fw = new FileWriter("bookings.txt", true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    PrintWriter out = new PrintWriter(bw))
                    {
                        out.println(outTxt);
                        output.writeBytes(("ticket has been booked."));
                    } catch (IOException e) {
                        output.writeBytes(("Error occured trying to save the booking, please try again.\n"));
                    }
                }
            }

        }
        if (saver)
            output.writeBytes(("This is a saver ticket and must have a time to book.\n"));
        else if (count == 0)
            output.writeBytes(("This route is not available on that day\n"));
        output.writeBytes("\n");
        output.flush();
    }

    private synchronized void bookSaverTicket (DataOutputStream output, int rID, String day, String time) throws IOException {
        int count = 0;
        boolean saver = false;
        output.writeBytes("\n");
        for (RouteRcd rcd: routeData) {
            if (rcd.rID == rID) {
                saver = rcd.type.equals("saver");
            }
        }
        for (TimetableRcd rcd: tTableData) {
            if (rcd.rID == rID  && rcd.day.equals(day) && rcd.time.equals(time) && saver) {
                String outTxt = String.format("%d, %d, %s, %s \n",rID, userID, day, time);
                try(FileWriter fw = new FileWriter("bookings.txt", true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw))
                {
                    out.println(outTxt);
                    output.writeBytes(("Ticket has been booked.\n"));
                } catch (IOException e) {
                    output.writeBytes(("Error occured trying to save the booking, please try again.\n"));
                }
                count++;
            }

        }
        if (!saver)
            output.writeBytes(("This is not a saver ticket and cant be booked with this action.\n"));
        else if (count == 0)
            output.writeBytes(("This route is not available on that day\n"));
        output.writeBytes("\n");
        output.flush();
    }
}

