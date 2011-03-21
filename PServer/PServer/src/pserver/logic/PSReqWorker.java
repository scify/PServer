//===================================================================
// PSReqWorker
//
// Personalization Server Request Worker 'PSReqWorker' class is a
// subclass of 'ReqWorker'. It extends the functionality of a basic
// web server by overriding two superclass methods, and implements
// a special Pers. Server protocol.
//
// Two main modes are offered: Personal ,Stereotypes and Communities. Each mode
// supports a number of operations that can be performed by issuing
// suitable HTTP requests to the Personalization Server. The
// modes are indepe=dent from each other. They share the same
// database, however they are supported by a separate set of DB
// tables.
//
// The syntax of those special requests are as follows:
//     http://server:port/<mode_id>?<query_string>
// For Personal mode the mode ID is "pers",for Stereotypes
// mode the mode ID is "ster" and for Communities the mode ID
// is "commu". The query string identifies the
// operation and its parameters, and is described separetely for
// each operation. Reserved words in the query string are case
// independent.
//
// All names in the query string must conform to the syntax defined
// on URLs, that is, they must not contain spaces, +, =, &, ?, etc.
// In addition, to avoid confusion with XML syntax, the characters
// < and > are also not allowed.
//
// The answer is formatted as XML. The XML answer must have a single
// element called <result>, encompassing a number of <row> elements,
// each containing the data elements corresponding to the specific
// request. By having such a standard format it becomes easier for
// the applications to parse all XML answers in a uniform way.
//
// For error handling, a number of HTTP error codes are used to
// denote success, client error (wrong request), or server error.
//===================================================================
package pserver.logic;

import pserver.data.MD5;
import java.net.*;
import java.sql.*;
import java.util.*;
//import javax.swing.*;
import java.security.*;
import java.io.*;

import pserver.*;
import pserver.data.DBAccess;
import pserver.pservlets.*;

public class PSReqWorker extends ReqWorker {
    //modes of response specify process of responding.
    //declare additional (regarding base class) response modes
    //static public final int ADMIN_MODE = 2;  //personal mode
    static public final int SERVICE_MODE = 2;  //personal mode
    //static public final int PERS_MODE = 3;  //personal mode
    //static public final int STER_MODE = 4;  //stereotype mode
    //static public final int COMMU_MODE = 5;  //communities mode
    //variables relevant to response body
    private StringBuffer response = null;  //the body of the response
    private String format = null;          //format, in form of file extension
    //database JDBC connection parameters
    private String url;         //JDBC string specifying the data source
    private String user;
    private String pass;
    //private int perm;           //permissions for client
    private String clientName;  //the name of the client that did the request
    private String administrator_name;//login name for administrator
    private String administrator_pass;//login password for administrator
    //anonymous ristriction
    //private boolean allowAnonymous;
    //private String db;        //eg. "ACCESS", "MySQL", etc.
    //initializers
    public PSReqWorker( Socket sock ) {
        super( sock );
        url = ( ( PersServer ) PersServer.pObj ).dbUrl;  //casting to subclass necessary
        user = ( ( PersServer ) PersServer.pObj ).dbUser;
        pass = ( ( PersServer ) PersServer.pObj ).dbPass;
        //allowAnonymous=((PersServer)PersServer.obj).allowAnonymous;
        administrator_name = ( ( PersServer ) PersServer.pObj ).administrator_name;
        administrator_pass = ( ( PersServer ) PersServer.pObj ).administrator_pass;
    //db = ((PersServer)PersServer.obj).dbType;
    }

    //overriden base class methods
    public void switchRespMode() {
        //overriden method, extends modes of operation (respMode).
        //initiates a sequence of methods in which the actions
        //appropriate to client request are performed and the
        //body of the response (if any) is decided.
        //if an error occurs, the 'respCode' can be set accordingly
        if ( PersServer.pObj.pservlets.containsKey( resURI.toLowerCase().substring( 1 ) ) ) {
            respMode = SERVICE_MODE;
            analyzeServiceMode( PersServer.pObj.pservlets.get( resURI.toLowerCase().substring( 1 ) ) );
            return;
        }
        //if the following line is removed, the server
        //will not return requested files in FILE_MODE.
        //the response will consist only of http header
        super.switchRespMode();  //mode set by base class
    }

    @Override
    public void switchRespBody() {
        //overriden method, "plugs-in" response body (if any), length,
        //and MIME type, depending on the mode of operation (respMode).
        //this method is called only if no error has occured ('respCode'
        //is 'NORMAL', which corresponds to HTTP '200 OK').
        switch ( respMode ) {
            case SERVICE_MODE:
                if ( response != null ) {
                    rbString = response.substring( 0 );
                    //rbLength = rbString.length();
                    try {
                        //rbLength = rbString.length();
                        rbLength = rbString.getBytes( "UTF-8" ).length;
                    } catch ( UnsupportedEncodingException ex ) {
                        ex.printStackTrace();
                    }
                }
                //else response variables (rbString etc.) remain null
                break;
            //else delegate to base class
            default:
                super.switchRespBody();
                break;
        }
    }

    //method for manipulate pservices
    private void analyzeServiceMode( PService servlet ) {
        //Connection conn = connDB(url, user, pass);
        DBAccess dbAccess = new DBAccess( url, user, pass );

        mimeType = servlet.getMimeType();
        response = new StringBuffer();
        respCode = servlet.service( queryParam, response, dbAccess );
    }    

    

    //database connection methods
    private Connection connDB( String DBUrl, String DBUser, String DBPass ) {
        //connect to database, return null if unable
        Connection conn = null;
        try {
            //Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
            conn = DriverManager.getConnection( DBUrl, DBUser, DBPass );
        //} catch(ClassNotFoundException e) {  //no connection established, stop
        //  WebServer.win.log.error(port + "-Problem connecting to DB: " + e);
        //return null;
        } catch ( SQLException e ) {
            WebServer.win.log.error( "-Problem connecting to DB: " + e );
            return null;
        }
        return conn;
    }

    private void disconnDB( Connection conn ) {
        //disconnect from database
        try {
            conn.close();
        } catch ( SQLException e ) {
            WebServer.win.log.error( "-Problem disconnecting from DB: " + e );
        }
    }

    //check to see if the request came from a valid pserver user
    private boolean isValidUser() {
        int clntIdx = queryParam.qpIndexOfKeyNoCase( "clnt" );
        /*if( this.allowAnonymous == true ){
        if( clntIdx != -1){
        queryParam.remove( clntIdx );
        }
        clientName=null;
        return true;
        }*/
        if ( clntIdx == -1 ) {
            return false;
        }
        //the query must start with the name/password
        /*if(((String)queryParam.getKey(0)).compareToIgnoreCase("clnt")!=0){
        if(this.allowAnonymous==false)
        return INVALID_CLIENT;
        else
        return FULL_GRANTED_CLIENT;
        }*/
        //client attibutes demactrate with the "|" character
        String userAndPass = ( String ) queryParam.getVal( clntIdx );
        StringTokenizer tokenizer = new StringTokenizer( userAndPass, "|" );
        String client = tokenizer.nextToken();//first comes the client name
        String password = "";
        try {
            password = tokenizer.nextToken();//second comes the unencrypted password
        } catch ( NoSuchElementException e ) {
            return false;
        }
        if ( client.equals( administrator_name ) == true && password.equals( administrator_pass ) == true ) {
            queryParam.remove( clntIdx );//removes client attrributes from the query
            return true;
        }
        try {
            Connection conn = connDB( url, user, pass );
            Statement stmt = conn.createStatement();
            //check pserver_user existence in the data base
            ResultSet rs = stmt.executeQuery( "SELECT * FROM pserver_clients WHERE name='" + client + "';" );
            if ( rs.next() == false ) {
                return false;
            }
            String storedPass = rs.getString( "password" );//gets the encrypted password
            try {
                String encodedPass = MD5.encrypt( password );
                if ( encodedPass.equals( storedPass ) == false )//compares given password with the storedpassword for validation
                {
                    return false;
                }
            } catch ( NoSuchAlgorithmException e ) {
                return false;
            }
            clientName = client;
            queryParam.remove( clntIdx );//removes client attrributes from the query
            return true;
        } catch ( SQLException e ) {
            return false;
        }
    }
}
