//===================================================================
// Preferences
//
// this class loads the pservlets with their names that are installed in pserver
//
//===================================================================
package pserver.utilities;

import java.io.*;
import java.util.*;

import pserver.*;
import pserver.algorithms.graphs.GraphClustering;
import pserver.algorithms.metrics.VectorMetric;
import pserver.pservlets.*;

public class PBeansLoader {

    private String file;  //the full pathname of file
    private String header;  //header in file
    //program options that persist
    private Properties pref = new Properties();  //user defined values
    private HashMap<String, PService> pservlets = null;
    private HashMap<String, VectorMetric> vMetrics = null;
    private HashMap<String, GraphClustering> gClustering = null;

    public PBeansLoader( String file, String header ) {        
        this.file = (new File( file )).getAbsolutePath();  //convert to absolute path
        this.header = header;
        pref.clear();
        load();
        Enumeration e = pref.propertyNames();        
        pservlets = new HashMap<String, PService>();
        vMetrics = new HashMap<String, VectorMetric>();
        gClustering = new HashMap<String, GraphClustering>();

        while ( e.hasMoreElements() ) {
            String pBeanName = (String) e.nextElement();
            String paramName = (String) pref.getProperty( pBeanName );
            int pos = ((String) pref.getProperty( pBeanName )).indexOf( '(' );
            String className;
            if ( pos != -1 ) {
                className = paramName.substring( 0, pos );
            } else {
                className = paramName;
            }
            //WebServer.win.log.forceReport( "" + pServletName + " == " + className );
            try {
                Class serviceClass = Class.forName( className );
                Class[] interfaces = serviceClass.getInterfaces();
                try {
                    for ( int j = 0; j < interfaces.length; j++ ) {
                        //System.out.println( " class is " + interfaces[j].getName() );
                        if ( interfaces[j].getName().endsWith( "pserver.pservlets.PService" ) ) { //loads the pservlet beans
                            PService service = (PService) serviceClass.newInstance();
                            if ( pos != -1 ) {
                                StringTokenizer params = new StringTokenizer( paramName.substring( pos + 1, paramName.length() - 1 ), "," );
                                String[] parameters = new String[ params.countTokens() ];
                                int i = 0;
                                while ( params.hasMoreTokens() ) {
                                    parameters[i] = params.nextToken();
                                    i++;
                                }
                                service.init( parameters );
                            } else {
                                service.init( null );
                            }
                            WebServer.win.log.forceReport( "loaded PServlet: " + className + " with name " + pBeanName );
                            pservlets.put( pBeanName.toLowerCase(), service );
                        } else if( interfaces[j].getName().endsWith( "pserver.algorithms.metrics.VectorMetric" ) ) { //loads the metric beans
                            VectorMetric metric = (VectorMetric) serviceClass.newInstance();
                            WebServer.win.log.forceReport( "loaded Vector Metric: " + className + " with name " + pBeanName );
                            vMetrics.put( pBeanName.toLowerCase(), metric );
                        } else if( interfaces[j].getName().endsWith( "pserver.algorithms.graphs.GraphClustering" ) ) { //loads the metric beans
                            GraphClustering clustering = (GraphClustering) serviceClass.newInstance();
                            WebServer.win.log.forceReport( "loaded Graph Clustering algorithm: " + className + " with name " + pBeanName );
                            gClustering.put( pBeanName.toLowerCase(), clustering );
                        }
                    }
                } catch ( Exception ex ) {
                    WebServer.win.log.debug( ex.toString() );
                    pservlets = null;
                    return;
                }
            } catch ( ClassNotFoundException ex ) {
                WebServer.win.log.debug( ex.toString() );
                pservlets = null;
                return;
            }
        }
    }

    public HashMap<String, PService> getPServlets() {
        return this.pservlets;
    }

    public String getPref( String name ) {
        return pref.getProperty( name );  //null if not there
    }

    public String[] getProperties() {
        Enumeration e = pref.propertyNames();
        Vector<String> elements = new Vector<String>();
        while ( e.hasMoreElements() ) {
            elements.addElement( (String)e.nextElement() );
        }
        return (String[]) elements.toArray( new String[ 0 ] );
    }

    //load and save
    public void load() {
        try {
            FileInputStream in = new FileInputStream( file );
            pref.load( in );
            String[] properties = getProperties();
        } catch ( Exception e ) {
            WebServer.win.log.forceReport( "Configuration file had bad entries, a new one will be created" );
        }
    }

    /**
     * @return the vMetrics
     */
    public HashMap<String, VectorMetric> getVMetrics() {
        return vMetrics;
    }

    /**
     * @return the gClustering
     */
    public HashMap<String, GraphClustering> getGClustering() {
        return gClustering;
    }
    
}