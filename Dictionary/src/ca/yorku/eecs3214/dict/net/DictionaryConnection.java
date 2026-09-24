package ca.yorku.eecs3214.dict.net;

import ca.yorku.eecs3214.dict.model.Database;
import ca.yorku.eecs3214.dict.model.Definition;
import ca.yorku.eecs3214.dict.model.MatchingStrategy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;


public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    Socket socket;
    BufferedReader in;
    PrintWriter out;

    /**
     * Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages. This constructor does not send any request for additional data.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the welcome
     *                                 messages are not successful.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

        // TODO Add your code here
        try{
            this.socket = new Socket(host, port);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(this.socket.getOutputStream(), true);

            String welcomeMessage; 
            if((welcomeMessage = in.readLine()) == null){
                throw new DictConnectionException("Welcome message is empty");
            }
            if(!welcomeMessage.startsWith("220")){
                throw new DictConnectionException("Welcome message not successful: " + welcomeMessage);
            }
            
            
        }catch(Exception e){
            throw new DictConnectionException("Connection failed: " + e.getMessage());
            //check if something needs to be thrown later
        }
    }

    /**
     * Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the welcome
     *                                 messages are not successful.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /**
     * Sends the final QUIT message, waits for its reply, and closes the connection with the server. This function
     * ignores any exception that may happen while sending the message, receiving its reply, or closing the connection.
     */
    public synchronized void close() {

        // TODO Add your code here
        try {
            this.out.println("QUIT");
            this.in.readLine();
            this.socket.close();

            //221 closing connection
            this.in.readLine();
        } catch (Exception e) {

        }
        
    }

    /**
     * Requests and retrieves a map of database name to an equivalent database object for all valid databases used in
     * the server.
     *
     * @return A map linking database names to Database objects for all databases supported by the server, or an empty
     * map if no databases are available.
     * @throws DictConnectionException If the connection is interrupted or the messages don't match their expected
     *                                 value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        // TODO Add your code here
        try {
            //doing command
            this.out.println("SHOW DB");
            //checking for status
            String statusCode = this.in.readLine();
            
            if (statusCode == null || statusCode.startsWith("554")) {
                return databaseMap;
            }
            if (statusCode.startsWith("551")) {
                throw new DictConnectionException("Error");
            }

            String[] storedDatabase;
            String databaseInfo;
            String databaseDesc;

            if (statusCode.startsWith("110")) {
                databaseInfo = this.in.readLine();
                while (!(databaseInfo.equals("."))) {
                    storedDatabase = databaseInfo.split(" ", 2);
                    databaseDesc = storedDatabase[1];
                    
                    //checks if the description even has quotations
                    if (databaseDesc.startsWith("\"") && databaseDesc.endsWith("\"")) {
                        databaseDesc = storedDatabase[1].substring(1, storedDatabase[1].length()-1);
                    
                    }
                    databaseMap.put(storedDatabase[0], new Database(storedDatabase[0],databaseDesc)); 
                    databaseInfo = this.in.readLine();
                }
                this.in.readLine();
            }
            
            //for final status code -> should be 250
            //this.in.readLine();
        } catch (Exception e) {
            throw new DictConnectionException("No databases retrieved");
        }

        return databaseMap;
    }

    /**
     * Requests and retrieves a list of all valid matching strategies supported by the server. Matching strategies are
     * used in getMatchList() to identify how to suggest words that match a specific pattern. For example, the "prefix"
     * strategy suggests words that start with a specific pattern.
     *
     * @return A set of MatchingStrategy objects supported by the server, or an empty set if no strategies are
     * supported.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected
     *                                 value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        // TODO Add your code here
        try {
            this.out.println("SHOW STRAT");

            String statusCode = this.in.readLine();

            if (statusCode == null || statusCode.startsWith("555")) {
                return set;
            }

            if (statusCode.startsWith("551")) {
                throw new DictConnectionException("No strategies available");
            }

            String keyword;
            String[] matches;
            String secondHalf;

            if (statusCode.startsWith("111")) {
                keyword = this.in.readLine();
                while (!(keyword.equals("."))) {
                    matches = keyword.split(" ", 2);
                    secondHalf = matches[1];
                    if (secondHalf.startsWith("\"") && secondHalf.endsWith("\"")) {
                        secondHalf = secondHalf.substring(1, secondHalf.length()-1);
                    }
                    set.add(new MatchingStrategy(matches[0], secondHalf));
                    keyword = this.in.readLine();
                }
            }

            //250 command complete
            this.in.readLine();
        } catch (Exception e) {
            throw new DictConnectionException("NO strategies retreived");
        }

        return set;
    }

    /**
     * Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param pattern  The pattern to use to identify word matches.
     * @param strategy The strategy to be used to compare the list of matches.
     * @param database The database where matches are to be found. Special databases like Database.DATABASE_ANY or
     *                 Database.DATABASE_FIRST_MATCH are supported.
     * @return A set of word matches returned by the server based on the word pattern, or an empty set if no matches
     * were found.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected value,
     *                                 or the database or strategy are not supported by the server.
     */
    public synchronized Set<String> getMatchList(String pattern, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        // TODO Add your code here
        try{
            this.out.println(String.format("MATCH %s %s \"%s\"", database.getName(), strategy.getName(), pattern));

            String statusCode = this.in.readLine();

            if (statusCode == null || statusCode.startsWith("552")) {
                return set;
            }

            if (statusCode.startsWith("551") || statusCode.startsWith("550")) {
                throw new DictConnectionException("No strategies or databases available");
            }

            String matches;
            String[] split;
            String match;

            if (statusCode.startsWith("152")) {
                matches = this.in.readLine();
                while (!(matches.equals("."))) {
                    split = matches.split(" ", 2);
                    match = split[1];
                    if (match.startsWith("\"") && match.endsWith("\"")) {
                        match = match.substring(1, match.length()-1);
                    }
                    set.add(match);
                    matches = this.in.readLine();
                }
            }
            this.in.readLine();
        }catch(Exception e){
            throw new DictConnectionException("No matches retrieved");
        }
        return set;
    }

    /**
     * Requests and retrieves all definitions for a specific word.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. Special databases like Database.DATABASE_ANY
     *                 or Database.DATABASE_FIRST_MATCH are supported.
     * @return A collection of Definition objects containing all definitions returned by the server, or an empty
     * collection if no definitions were available.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected value,
     *                                 or the database is not supported by the server.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        // TODO Add your code here
        try{
            this.out.println(String.format("DEFINE %s \"%s\"", database.getName(), word));

            String statusCode = this.in.readLine();

            if (statusCode == null || statusCode.startsWith("552")) {
                return set;
            }

            if (statusCode.startsWith("550")) {
                throw new DictConnectionException("No databases available");
            }

            String line;
            String[] split;
            String name;
            String db;
            String[] infoPieces = statusCode.split(" ", 3);
            int numDef = Integer.parseInt(infoPieces[1]);
            Definition def;

            if (statusCode.startsWith("150")) {
                line = this.in.readLine();
                for (int n = 0; n < numDef; n++) {
                    if(line.startsWith("151")){
                        split = line.split(" ");
                        name = split[1];
                        db = split[2];
                        def = new Definition(name, db);
                        this.in.readLine();
                        while (!(line.equals("."))) {
                            line = this.in.readLine();
                            if (name.startsWith("\"") && name.endsWith("\"")) {
                                name = name.substring(1, name.length()-1);
                            }
                            def.appendDefinition(line);
                        }
                        set.add(def);
                        line = this.in.readLine();
                    }

                }
            }
            this.in.readLine();
        }catch(Exception e){
            throw new DictConnectionException("No definitions retrieved");
        }

        return set;
    }

}
