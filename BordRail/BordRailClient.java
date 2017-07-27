/* BordRail Client - send commands to server to get responses
 * Display reults in a GUI
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import javax.swing.*;
import java.awt.event.*;

public class BordRailClient extends JFrame implements ActionListener {
    private DataOutputStream output;
    private DataInputStream input;
    private String host;
    private int port;
    private boolean clientRunning;
    public static final int BUFFSZ = 80;
    public static final String fieldSep = "#";
    public static final String endMkr = ">";
    //setting up the buttons and text area
    public JButton allBtn, logOutBtn, daysBtn, timesBtn, 
    costBtn, logInBtn, trmBtn, dwnBtn, ticketBtn, saverBtn;
    public JTextArea screen;

    public static void main(String args[])   {
        BordRailClient app;
        if (args.length < 2)
            System.out.println(
                "Usage: java BordRailClient <host> <port>");
        else {
            app = new BordRailClient(args[0], Integer.parseInt(args[1]));
            app.runClient();
        }
    } 

    public BordRailClient(String host, int port) {
        super("BordRail Client");
        this.host = host;   this.port = port;
        clientRunning = true;
        //setup the buttons and add event listeners
        allBtn = new JButton("All Routes");             allBtn.addActionListener(this); 
        daysBtn = new JButton("See route days");        daysBtn.addActionListener(this); 
        timesBtn = new JButton("See route times");      timesBtn.addActionListener(this); 
        costBtn = new JButton("See route cost");        costBtn.addActionListener(this); 
        logOutBtn = new JButton("Log out");             logOutBtn.addActionListener(this); 
        logInBtn = new JButton("Log in");               logInBtn.addActionListener(this); 
        trmBtn = new JButton("End session");            trmBtn.addActionListener(this); 
        dwnBtn = new JButton("Down server");            dwnBtn.addActionListener(this);
        ticketBtn = new JButton("Book Ticket");         ticketBtn.addActionListener(this);
        saverBtn = new JButton("Book Saver");           saverBtn.addActionListener(this);
        screen = new JTextArea(20,50);                 screen.setEditable(false);
        //setup the panel to hold the buttons
        JPanel p = new JPanel();
        p.setLayout(new GridLayout(2,4));
        p.add(allBtn); p.add(daysBtn); p.add(timesBtn); p.add(costBtn); p.add(logOutBtn); 
        p.add(logInBtn); p.add(trmBtn); p.add(dwnBtn); p.add(ticketBtn); p.add(saverBtn);
        //anchor to top of display
        add(p, BorderLayout.NORTH);
        //fill the rest of the screen
        add(new JScrollPane(screen), BorderLayout.CENTER);
        //render the application on the screen
        setSize(800, 600);
        setVisible(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        runClient(); //debug code for running from a debugger
    }
    
    public void runClient()    {
        Socket clientSocket;
        try {
            // Creating the socket
            screen.setText(String.format("Attempting connection to %s\n", host));
            clientSocket = new Socket(InetAddress.getByName(host), port);
            screen.append("Connected to: " + clientSocket.getInetAddress().getHostName() + "\n\n");
            // Setup the input and output streams
            output = new DataOutputStream(clientSocket.getOutputStream());
            output.flush();
            input = new DataInputStream(clientSocket.getInputStream());
            // Process incoming data while client running
            byte[] buff = new byte[BUFFSZ];
            int len;
            do {
                len = input.read(buff);
                if (len > 0){
                    screen.append(new String(buff, 0, len));
                    screen.setCaretPosition(screen.getText().length());
                }
            } while (clientRunning);

            //if client sessions ends close all connections
            screen.append("\nClosing connection.");
            output.close();
            input.close();
            clientSocket.close();
            dispose(); // close main window
        }
        catch (EOFException eof) {
            System.out.println("Server terminated connection");
        }
        catch (Exception e) { //IOException or ConnectException
            System.err.println(e);
        }
    }

    //Process commands from button clicks 
    public void actionPerformed(ActionEvent evt) {
        String cmdStr = null, inputStr = null, inputStr2 = null, inputStr3 = null;
        Object src = evt.getSource();
        if (src == allBtn) {
            cmdStr = "ALLTK"+endMkr;
        } 
        else if (src ==  daysBtn) {
            inputStr = JOptionPane.showInputDialog("Route ID:");
            if (inputStr == null) return;
            cmdStr = "TRVL"+fieldSep+inputStr+endMkr;
        } 
        else if (src ==  timesBtn) {
            inputStr = JOptionPane.showInputDialog("Route ID:");
            if (inputStr == null) return;
            inputStr2 = JOptionPane.showInputDialog("Day:");
            if (inputStr2 == null) return;
            cmdStr = "RUN"+fieldSep+inputStr+fieldSep+inputStr2+endMkr;
        } 
        else if (src ==  costBtn) {
            inputStr = JOptionPane.showInputDialog("Route ID:");
            if (inputStr == null) return;
            cmdStr = "COST"+fieldSep+inputStr+endMkr;
        } 
        else if (src ==  logOutBtn) {
            cmdStr = "LOGOUT"+endMkr;
        } 
        else if (src == logInBtn) {
            inputStr = JOptionPane.showInputDialog("User ID:");
            if (inputStr == null) return;
            inputStr2 = JOptionPane.showInputDialog("Password:");
            if (inputStr2 == null) return;
            cmdStr = "LOGIN"+fieldSep+inputStr+fieldSep+inputStr2+endMkr;
        } 
        else if (src == trmBtn) {
            cmdStr = "TERM"+endMkr;
            clientRunning = false;
        } 
        else if (src == dwnBtn) {
            cmdStr = "DOWN"+endMkr;
        }
        else if (src == ticketBtn) {
            inputStr = JOptionPane.showInputDialog("Route ID:");
            if (inputStr == null) return;
            inputStr2 = JOptionPane.showInputDialog("Day:");
            if (inputStr2 == null) return;
            cmdStr = "BKD"+fieldSep+inputStr+fieldSep+inputStr2+endMkr;
        }
        else if (src == saverBtn) {
            inputStr = JOptionPane.showInputDialog("Route ID:");
            if (inputStr == null) return;
            inputStr2 = JOptionPane.showInputDialog("Day:");
            if (inputStr2 == null) return;
            inputStr3 = JOptionPane.showInputDialog("Time:");
            if (inputStr3 == null) return;
            cmdStr = "BKDT"+fieldSep+inputStr+fieldSep+inputStr2+fieldSep+inputStr3+endMkr;
        }
        if (cmdStr == null) return;
        try {
            output.writeBytes(cmdStr);  //send
            System.out.printf("%d bytes sent\n", cmdStr.length());
        } 
        catch (IOException ex) {
            System.err.println("Error writing object");
        }
        screen.setCaretPosition(screen.getText().length());
    }

} 
