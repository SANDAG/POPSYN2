/*   
 * Copyright 2014 Parsons Brinckerhoff

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License
   *
   */

package org.sandag.popsyn.sql;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.log4j.Logger;
import org.sandag.popsyn.testXmlParse.Constraint;
import org.sandag.popsyn.testXmlParse.Marginal;

public class ListBalancingSqlHelper implements Serializable
{

	private static final long versionNumber = 1L;
	
	private static final long serialVersionUID = versionNumber;
	
    private transient Logger logger = Logger.getLogger(ListBalancingSqlHelper.class);
    
    private static final String SYNPOP_TEMP_TABLE_NAME = "popsyn_staging.temp";

    private String HOUSEHOLD_IDS_TABLE_NAME;
    private String INPUT_PUMS_HHTABLE_META_NAME;
    private String INPUT_PUMS_HHTABLE_PUMA_NAME;
    private String INPUT_PUMS_HHTABLE_WEIGHT_NAME;

    private String dbServer;
    private String dbHost;
    private String dbName;
    private String user;
    private String password;
    
    
    public ListBalancingSqlHelper( String dbType, String dbName, String user, String password, String ipAddress ) {              
        this.dbServer = dbType;
        this.dbHost = ipAddress;
        this.dbName = dbName;
        this.user = user;
        this.password = password;

        ConnectionHelper.getInstance( dbServer );
    }
        
    
    public void createHouseholdIdTable( String tempHhIdsTableName, String pumaGeogName, String metaGeogName, String pumsWeightFieldName ) {

    	// assign values to class attributes used by other methods
    	HOUSEHOLD_IDS_TABLE_NAME = tempHhIdsTableName;
    	INPUT_PUMS_HHTABLE_PUMA_NAME = pumaGeogName;
    	INPUT_PUMS_HHTABLE_META_NAME = metaGeogName;
    	INPUT_PUMS_HHTABLE_WEIGHT_NAME = pumsWeightFieldName;
    	
        clearTableFromDatabase( HOUSEHOLD_IDS_TABLE_NAME );

         String idFieldName = "tempId";
         
        // create a temporary incidence table with final balanced weights
        String createQuery = "";
        if ( dbServer.equalsIgnoreCase( ConnectionHelper.MYSQL_SERVER_NAME ) )
        	createQuery = "CREATE TABLE " + HOUSEHOLD_IDS_TABLE_NAME + " ( " + idFieldName + " INT NOT NULL AUTO_INCREMENT, " + INPUT_PUMS_HHTABLE_META_NAME + " INT, " + INPUT_PUMS_HHTABLE_PUMA_NAME + " INT, taz INT, maz INT, " + INPUT_PUMS_HHTABLE_WEIGHT_NAME + " FLOAT, finalPumsId INT, finalweight INT, KEY(" + idFieldName + ") )";
        else if ( dbServer.equalsIgnoreCase( ConnectionHelper.MS_SQL_SERVER_NAME ) )
        	createQuery = "CREATE TABLE " + HOUSEHOLD_IDS_TABLE_NAME + " ( " + idFieldName + " INT NOT NULL PRIMARY KEY IDENTITY, " + INPUT_PUMS_HHTABLE_META_NAME + " INT, " + INPUT_PUMS_HHTABLE_PUMA_NAME + " INT, taz INT, maz INT, " + INPUT_PUMS_HHTABLE_WEIGHT_NAME + " FLOAT, finalPumsId INT, finalweight INT )";
        
        submitExecuteUpdate( createQuery );
        
        String tempQuery = "CREATE INDEX finalPumsId ON " + HOUSEHOLD_IDS_TABLE_NAME + "(finalPumsId)";
        submitExecuteUpdate( tempQuery );

    }


    public void createSyntheticPopulationTables( String idVariable, String outputHhTableName, String pumsHhTableName, String[] pumsHhFieldNames, String outputPersonTableName, String pumsPersTableName, String[] pumsPersFieldNames, int id, int dataSource, int gqflag) {

    	long start = System.currentTimeMillis();   
    	String tempHhTable=SYNPOP_TEMP_TABLE_NAME+"hh_"+id;
        clearTableFromDatabase(tempHhTable);
          
        String aliasTableName = "t1";       
        String tempQuery = "";
        if ( dbServer.equalsIgnoreCase( ConnectionHelper.MYSQL_SERVER_NAME ) ) {
        	tempQuery = "CREATE TABLE " + tempHhTable + " SELECT " + idVariable  + ", " + INPUT_PUMS_HHTABLE_PUMA_NAME;
	        for ( int j=0; j < pumsHhFieldNames.length; j++ )
	        	tempQuery += ", " + pumsHhFieldNames[j];         
	        tempQuery += " FROM " + pumsHhTableName;
        }
        else if ( dbServer.equalsIgnoreCase( ConnectionHelper.MS_SQL_SERVER_NAME ) ) {
        	tempQuery = "SELECT " + idVariable  + ", " + INPUT_PUMS_HHTABLE_PUMA_NAME;
	        for ( int j=0; j < pumsHhFieldNames.length; j++ )
	        	tempQuery += ", " + pumsHhFieldNames[j];         
	        tempQuery += " INTO " + tempHhTable + " FROM " + pumsHhTableName;
        }
        submitExecuteUpdate( tempQuery );

        tempQuery = "CREATE INDEX " + idVariable + " ON " + tempHhTable + "(" + idVariable + ")";
        submitExecuteUpdate( tempQuery );
         	        	                      
        // Wu modified, instead of creating a new table, now only insert records to an existing hh table
        String insertQuery = "";
        if ( dbServer.equalsIgnoreCase( ConnectionHelper.MYSQL_SERVER_NAME ) ) {                                
                insertQuery = "INSERT INTO " + outputHhTableName + " ([popsyn_run_id], [synpop_hh_id], [hh_id], [final_weight], [mgra])";                              
                            insertQuery += " SELECT "+id+","+HOUSEHOLD_IDS_TABLE_NAME + ".tempId, " + aliasTableName + ".hh_id, "+HOUSEHOLD_IDS_TABLE_NAME + ".finalweight, "+HOUSEHOLD_IDS_TABLE_NAME + ".maz";;
                            insertQuery += " FROM " + HOUSEHOLD_IDS_TABLE_NAME +" LEFT JOIN " + tempHhTable + " " + aliasTableName +
                                                        " ON " + HOUSEHOLD_IDS_TABLE_NAME + ".finalPumsId" + "=" + aliasTableName + "." + idVariable;
        }
        else if ( dbServer.equalsIgnoreCase( ConnectionHelper.MS_SQL_SERVER_NAME ) ) {                    
                        insertQuery = "INSERT INTO " + outputHhTableName + " ([popsyn_run_id], [synpop_hh_id], [hh_id], [final_weight], [mgra])";       
                insertQuery += " SELECT "+id+","+HOUSEHOLD_IDS_TABLE_NAME + ".tempId, " + aliasTableName + ".hh_id, "+HOUSEHOLD_IDS_TABLE_NAME + ".finalweight, "+HOUSEHOLD_IDS_TABLE_NAME + ".maz";
                insertQuery += " FROM " + HOUSEHOLD_IDS_TABLE_NAME +" LEFT JOIN " + tempHhTable + " " + aliasTableName +
                                        " ON " + HOUSEHOLD_IDS_TABLE_NAME + ".finalPumsId" + "=" + aliasTableName + "." + idVariable;
        }

        
        submitExecuteUpdate( insertQuery );
        logger.info( "query for creating output synthetic households table = " + insertQuery );
        logger.info( "Time for creating output synthetic households table = " + ( System.currentTimeMillis() - start )/60000.0 + " minutes." );
        
        start = System.currentTimeMillis();
       
        String tempPersonTable=SYNPOP_TEMP_TABLE_NAME+"pers_"+id;
        clearTableFromDatabase(tempPersonTable);
        
        if ( dbServer.equalsIgnoreCase( ConnectionHelper.MYSQL_SERVER_NAME ) ) {
	        tempQuery = "CREATE TABLE " + tempPersonTable + " SELECT " + idVariable  + ", " + INPUT_PUMS_HHTABLE_PUMA_NAME;
	        for ( int j=0; j < pumsPersFieldNames.length; j++ )
	        	tempQuery += ", " + pumsPersFieldNames[j];         
	        tempQuery += " FROM " + pumsPersTableName;
        }
        else if ( dbServer.equalsIgnoreCase( ConnectionHelper.MS_SQL_SERVER_NAME ) ) {
        	tempQuery = "SELECT " + idVariable  + ", " + INPUT_PUMS_HHTABLE_PUMA_NAME;
	        for ( int j=0; j < pumsPersFieldNames.length; j++ )
	        	tempQuery += ", " + pumsPersFieldNames[j];         
	        tempQuery += " INTO " + tempPersonTable + " FROM " + pumsPersTableName;
        }
        submitExecuteUpdate( tempQuery );

        tempQuery = "CREATE INDEX " + idVariable + " ON " + tempPersonTable + "(" + idVariable + ")";
        submitExecuteUpdate( tempQuery );
      
        // Wu modified, instead of creating a new table, now only insert records to an existing person table.   
        String aliasTableName2="t2";
        insertQuery = "";
        if ( dbServer.equalsIgnoreCase( ConnectionHelper.MYSQL_SERVER_NAME ) ) {
                        insertQuery = "INSERT INTO " + outputPersonTableName + " ([popsyn_run_id], [synpop_hh_id], [person_id])";       
                insertQuery += " SELECT "+id+","+HOUSEHOLD_IDS_TABLE_NAME + ".tempId, person_id";
                insertQuery += " FROM " + HOUSEHOLD_IDS_TABLE_NAME +" LEFT JOIN " + tempPersonTable + " " + aliasTableName +
                                        " ON " + HOUSEHOLD_IDS_TABLE_NAME + ".finalPumsId" + "=" + aliasTableName + "." + idVariable; 
                insertQuery +=" LEFT JOIN " + tempHhTable + " " + aliasTableName2 +
                        " ON " + aliasTableName2 + "." +idVariable+ "=" + aliasTableName + "." + idVariable;
        }
        else if ( dbServer.equalsIgnoreCase( ConnectionHelper.MS_SQL_SERVER_NAME ) ) {
                        insertQuery = "INSERT INTO " + outputPersonTableName + " ([popsyn_run_id], [synpop_hh_id], [person_id])";       
                insertQuery += " SELECT "+id+","+HOUSEHOLD_IDS_TABLE_NAME + ".tempId, person_id";
                insertQuery += " FROM " + HOUSEHOLD_IDS_TABLE_NAME +" LEFT JOIN " + tempPersonTable + " " + aliasTableName +
                                        " ON " + HOUSEHOLD_IDS_TABLE_NAME + ".finalPumsId" + "=" + aliasTableName + "." + idVariable;  
                insertQuery +=" LEFT JOIN " + tempHhTable + " " + aliasTableName2 +
                        " ON " + aliasTableName2 + "." +idVariable+ "=" + aliasTableName + "." + idVariable;
        }
        
        submitExecuteUpdate( insertQuery );
        logger.info( "query for creating output synthetic persons table = " + insertQuery );
        logger.info( "Time for creating output synthetic persons table = " + ( System.currentTimeMillis() - start )/60000.0 + " minutes." );
        
        dropStagingTables(tempHhTable);
        dropStagingTables(tempPersonTable);
    }
    
    //Wu added to resolve primary key issues in synpop_hh and sypop_person caused be running general pop and GQ as two separate steps
    public void reseedTempId(String tableName, int runId){
    	String query="SELECT MAX(hh_id)+1 FROM " + tableName + " WHERE [popsyn_run_id]="+runId; 
    	int maxTempId=getIdentity(query);
        String reseedQuery="DBCC CHECKIDENT ('popsyn_staging.hhids_"+runId+"',RESEED, "+maxTempId+")";
        System.out.println("reseed query="+reseedQuery);
        	submitExecuteUpdate( reseedQuery );
    }  
    
    //Wu addeed, reseed person table for each run
    public void reseedPersonTable(String tableName){
        String reseedQuery="DBCC CHECKIDENT ('"+tableName+"',RESEED, 0)";
        System.out.println("reseed query="+reseedQuery);
        	submitExecuteUpdate( reseedQuery );
    }  
     
    //Wu added to drop staging tables
    public void dropStagingTables(String table){
    	String query = "DROP TABLE " + table;
        submitExecuteUpdate(query);
    }

    public void insertIntoHouseholdIdTable( int[][] mazIntegerWeights, int[][] mazPumsRecordIds, int taz, int[] mazIds, int[] mazPumas, int[] mazMetas, double[][] pumsWts ) {

    	// insert values into the new table
    	String insertQuery = "";
    	String valuesString = "";
    	
    	if ( dbServer.equalsIgnoreCase( ConnectionHelper.MYSQL_SERVER_NAME ) ) {
        
    		insertQuery = "INSERT INTO " + HOUSEHOLD_IDS_TABLE_NAME + " ( " + INPUT_PUMS_HHTABLE_META_NAME + ", " + INPUT_PUMS_HHTABLE_PUMA_NAME + " ," + INPUT_PUMS_HHTABLE_WEIGHT_NAME + ", taz, maz, finalPumsId, finalweight ) VALUES\n";
        	
            for ( int m=0; m < mazIntegerWeights.length; m++ ) {
            	if ( mazIntegerWeights[m] == null )
            		continue;

            	int maz = mazIds[m];
                for ( int j=0; j < mazIntegerWeights[m].length; j++ )
                	valuesString += "( " + mazMetas[m] + ", " + mazPumas[m] + ", " + pumsWts[m][j] + ", " + taz + ", " + maz + ", " + mazPumsRecordIds[m][j] + ", " + mazIntegerWeights[m][j] + " ),\n";
                }
    		
        } else if ( dbServer.equalsIgnoreCase( ConnectionHelper.MS_SQL_SERVER_NAME ) ) {
    	   	
        	insertQuery = "INSERT INTO " + HOUSEHOLD_IDS_TABLE_NAME + " ( " + INPUT_PUMS_HHTABLE_META_NAME + ", " + INPUT_PUMS_HHTABLE_PUMA_NAME + " ," + INPUT_PUMS_HHTABLE_WEIGHT_NAME + ", taz, maz, finalPumsId, finalweight )\n";
        	valuesString = "EXEC('";
        	
            for ( int m=0; m < mazIntegerWeights.length; m++ ) {
            	if ( mazIntegerWeights[m] == null )
            		continue;

            	int maz = mazIds[m];
                for ( int j=0; j < mazIntegerWeights[m].length; j++ )
                	valuesString += "SELECT " + mazMetas[m] + ", " + mazPumas[m] + ", " + pumsWts[m][j] + ", " + taz + ", " + maz + ", " + mazPumsRecordIds[m][j] + ", " + mazIntegerWeights[m][j] + " \n";
                }
            
            valuesString += "')\n";
            
        }
        
    	// if no MAZs for this TAZ have non-zero weights, then there is no query to execute.
        if ( valuesString.length() == 0 )
        	return;

	    int index = (dbServer.equalsIgnoreCase( ConnectionHelper.MS_SQL_SERVER_NAME ) ? valuesString.lastIndexOf( "\n" ) : valuesString.lastIndexOf( ",\n" ));
	    insertQuery += valuesString.substring(0, index);
	    
    	//submit query
	    //System.out.println("insert query="+insertQuery);
        submitExecuteUpdate( insertQuery );
        
    }

    public void createSimpleIncidenceTable( String idVariable, String tableName, HashMap<Integer, Constraint> constraints, int puma, int gqflag ) {

        clearTableFromDatabase( tableName );
        
        String createQuery = "CREATE TABLE " + tableName + " (" + idVariable + " INT";
        String insertQuery = "INSERT INTO " + tableName + " (" + idVariable + ", ";
        String updateQuery = "UPDATE " + tableName + " SET ";
        

        int count = 1;
        for ( int id=1; id <= constraints.size(); id++ ) {
        	
        	if ( ! constraints.containsKey(id) )
        		continue;

        	Constraint constraint = constraints.get( id );

            if ( count == 1 ) {
                createQuery += ( ", " + constraint.getField() + " FLOAT" ); 
                insertQuery += ( constraint.getField() + ") SELECT " + idVariable + ", "
                        + constraint.getField() + " FROM " + ((Marginal)constraint.getParentObject()).getTable()
                        + " WHERE " + INPUT_PUMS_HHTABLE_PUMA_NAME + "=" + puma 
                        +" AND ([gq_type_id] = CASE WHEN " + gqflag + " = 0 THEN 0 ELSE NULL END OR [gq_type_id] > CASE WHEN " + gqflag + " = 1 THEN 0 ELSE NULL END)"); 
            }
            
            createQuery += ( ", " + constraint.getField() + constraint.getId() + " FLOAT" ); 
            
            String separater = ", ";

            if ( constraint.getIntervalType().equals( "equality" ) ) {
            	updateQuery += ( constraint.getField() + constraint.getId() + "=" +
           			"CASE WHEN " + constraint.getField() + "=" + constraint.getValue() + " THEN 1 ELSE 0 END" + separater );
            }
            else if ( constraint.getIntervalType().equals( "interval" ) ) {
                updateQuery += ( constraint.getField() + constraint.getId() + "=" +
                    "CASE WHEN " + constraint.getField() + " >" + ( constraint.getLoType().equals("closed") ? "= " : " " ) + getIntValue( "lo_value", constraint.getLoValue() ) +
                    " AND " + constraint.getField() + " <" + ( constraint.getHiType().equals("closed") ? "= " : " " ) + getIntValue( "hi_value", constraint.getHiValue() ) + " THEN 1 ELSE 0 END" + separater );
            }
         
            count++;
            
        }

        createQuery += ")"; 
            
        // remove "," from end of string
        int index = updateQuery.lastIndexOf( "," );
        updateQuery = updateQuery.substring(0, index);
        
        // create the incidence table and populate it
        submitExecuteUpdate( createQuery );
        submitExecuteUpdate( insertQuery );
        submitExecuteUpdate( updateQuery );
        
    }


    public void createCountIncidenceTable( String idVariable, String tableName, HashMap<Integer, Constraint> constraints, int puma, int gqflag ) {

        clearTableFromDatabase( tableName );
        
        String createQuery = "CREATE TABLE " + tableName + " (" + idVariable + " INT";
        String insertQuery = "INSERT INTO " + tableName + " (" + idVariable + ", ";
        String updateQuery = "UPDATE " + tableName + " SET ";
        
        
        int count = 1;
        for ( int id=1; id <= constraints.size(); id++ ) {

        	if ( ! constraints.containsKey(id) )
        		continue;
        	
            Constraint constraint = constraints.get( id );

            if ( count == 1 ) {
                createQuery += ( ", " + constraint.getField() + " FLOAT" ); 
                insertQuery += ( constraint.getField() + ") SELECT " + idVariable + ", "
                    + constraint.getField() + " FROM " + ((Marginal)constraint.getParentObject()).getTable()
                    + " WHERE " + INPUT_PUMS_HHTABLE_PUMA_NAME + "=" + puma
                    + " AND ([gq_type_id] = CASE WHEN " + gqflag + " = 0 THEN 0 ELSE NULL END OR [gq_type_id] > CASE WHEN " + gqflag + " = 1 THEN 0 ELSE NULL END)"); 
            }
            
            createQuery += ( ", " + constraint.getField() + constraint.getId() + " FLOAT" ); 
            
            String separater = ", ";

            if ( constraint.getIntervalType().equals( "equality" ) ) {
                updateQuery += ( constraint.getField() + constraint.getId() + "=" +
                    "CASE WHEN " + constraint.getField() + "=" + constraint.getValue() + " THEN 1 ELSE 0 END" + separater );
            }
            else if ( constraint.getIntervalType().equals( "interval" ) ) {
                updateQuery += ( constraint.getField() + constraint.getId() + "=" +
                    "CASE WHEN " + constraint.getField() + " >" + ( constraint.getLoType().equals("closed") ? "= " : " " ) + getIntValue( "lo_value", constraint.getLoValue() ) +
                    " AND " + constraint.getField() + " <" + ( constraint.getHiType().equals("closed") ? "= " : " " ) + getIntValue( "hi_value", constraint.getHiValue() ) + " THEN 1 ELSE 0 END"  + separater);
            }
            
            count++;
            
        }

        createQuery += ")"; 
        

        // remove "," from end of string
        int index = updateQuery.lastIndexOf( "," );
        updateQuery = updateQuery.substring(0, index);
        
        //System.out.println("createQ="+createQuery);
        //System.out.println("insertQ="+insertQuery);
        //System.out.println("updateQ="+updateQuery);
        
        
        // create the incidence table and populate it
        submitExecuteUpdate( createQuery );
        submitExecuteUpdate( insertQuery );
        submitExecuteUpdate( updateQuery );
        
    }


    /**
     * Form and submit the query to get the one field from a table in the database
     * 
     * @param fieldName name of the table field
     * @param tableName name of table from which the field is retrieved
     * @return double[] of values
     * 
     */
    public double[] submitGetTableFieldQuery( String fieldName, String tableName, String idFieldName, int puma ) {

        // define the query to get the specified column.
        String query = "SELECT " + fieldName + " FROM " + tableName
        	+ " WHERE " + INPUT_PUMS_HHTABLE_PUMA_NAME + "=" + puma + " ORDER BY " + idFieldName; 
        
        double[] result;
        ArrayList<Double> rsList = new ArrayList<Double>();
        
        Connection conn = null;
        try
        {

            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
            PreparedStatement ps = conn.prepareStatement( query );
            ResultSet rs = ps.executeQuery();
            while ( rs.next() )
                rsList.add( rs.getDouble(1) );

            result = new double[rsList.size()];
            for ( int i=0; i < rsList.size(); i++ )
                result[i] = rsList.get(i);
            
        }
        catch (SQLException e)
        {
            logger.error( "error executing SQL query:" );
            logger.error( "SQL:  " + query, e );
            throw new DAOException(e.getMessage());
        }
        finally
        {
        	ConnectionHelper.closeConnection(conn);
        }
        
        return result;
        
    }
    
    //Wu added to get identity id
    public int getIdentity( String query ) {        
        Connection conn = null;
        int result=-1;
        try
        {
            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
            PreparedStatement ps = conn.prepareStatement( query );
            ResultSet rs = ps.executeQuery();
            while ( rs.next() )
                result=rs.getInt(1);
        }
        catch (SQLException e)
        {
            logger.error( "error executing SQL query:" );
            logger.error( "SQL:  " + query, e );
            throw new DAOException(e.getMessage());
        }
        finally
        {
        	ConnectionHelper.closeConnection(conn);
        }
        
        return result;
        
    }   

    /**
     * Form and submit the query to get the one household id field from the table to be balanced
     * 
     * @param idFieldName name of the id field
     * @param tableName name of table from which id field is retrieved
     * @return int[] of ids
     * 
     */
    //Wu modified to query hhids by HH or GQ
    public int[] submitGetIdsQuery( String idFieldName, String tableName, int puma, int gqflag ) {

        // define the query to get the weight column.
    	String query = "SELECT " + idFieldName + " FROM " + tableName
    			+ " WHERE " + INPUT_PUMS_HHTABLE_PUMA_NAME + "=" + puma 
    			+ " AND ([gq_type_id] = CASE WHEN " + gqflag + " = 0 THEN 0 ELSE NULL END OR [gq_type_id] > CASE WHEN " + gqflag + " = 1 THEN 0 ELSE NULL END) ORDER BY " 
    			+ idFieldName;
    
        String hhtype;
        if(gqflag==0){
        	hhtype="hhids";
        }else{
        	hhtype="gqids";
        }
        
        System.out.println("Getting "+hhtype+" from "+INPUT_PUMS_HHTABLE_PUMA_NAME+" for PUMA "+puma+" to be balanced.");
        System.out.println("---Query="+query);
        
        int[] result;
        ArrayList<Integer> rsList = new ArrayList<Integer>();
        
        Connection conn = null;
        try
        {

            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
            PreparedStatement ps = conn.prepareStatement( query );
            ResultSet rs = ps.executeQuery();
            while ( rs.next() )
                rsList.add( rs.getInt(1) );

            result = new int[rsList.size()];
            for ( int i=0; i < rsList.size(); i++ )
                result[i] = rsList.get(i);
            
        }
        catch (SQLException e)
        {
            logger.error( "error executing SQL query:" );
            logger.error( "SQL:  " + query, e );
            throw new DAOException(e.getMessage());
        }
        finally
        {
            ConnectionHelper.closeConnection(conn);
        }
        
        return result;
        
    }

    /**
     * Form and submit the query to get the simple incidence table for the control set
     * 
     * @param incidenceTableName name of the incidence table for the control set
     * @param constraints control set that defines incidence conditions for a variable
     * @return int[][] of the incidence table values.  Rows are the elements to be balanced,
     *         most likely households, and columns are the number of conditions defined
     *         for the control set.
     */
    public int[][] submitGetSimpleIncidenceTableQuery( String incidenceTableName, String hhidFieldName, HashMap<Integer, Constraint> constraints, int[] hhIdIndex ) {

        int[][] result;

        // define the query to retrieve the incidence table
        String query = "SELECT " + hhidFieldName + ", ";
        int count = 1;
        String separater = ", ";
        for ( int id=1; id <= constraints.size(); id++ ) {
        	if ( constraints.containsKey(id) ) {
	            Constraint constraint = constraints.get( id );
	            query += ( constraint.getField() + constraint.getId() );
	            if ( count++ == constraints.values().size() )
	                separater = " ";
	            query += separater; 
        	}
        	else {
        		count++;
        	}
        }
        query += " FROM " + incidenceTableName; 
        
        
        // execute the query, and save the result set as a 2 dimensional int array.
        HashMap<Integer, int[]> rsMap = new HashMap<Integer, int[]>();
        Connection conn = null;
        try
        {

            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
            PreparedStatement ps = conn.prepareStatement( query );
            ResultSet rs = ps.executeQuery();
            
            int numColumns = rs.getMetaData().getColumnCount();
            while ( rs.next() ) {
                int[] row = new int[numColumns-1];
                for ( int i=0; i < numColumns-1; i++ )
                    row[i] = rs.getInt( i+1+1 );
                int id = rs.getInt( 1 );
                int k = hhIdIndex[id];
                rsMap.put( k, row );
            }

            result = new int[rsMap.size()][];
            for ( int k : rsMap.keySet() ) {
            	int[] values = rsMap.get(k);
                result[k] = values;
            }
            
        }
        catch (SQLException e)
        {
            logger.error( "error executing SQL query:" );
            logger.error( "SQL:  " + query, e );
            throw new DAOException(e.getMessage());
        }
        finally
        {
            ConnectionHelper.closeConnection(conn);
        }
        
        return result;
        
    }

    
    /**
     * Form and submit the query to get an array of values from a table for the fields in the control set
     * 
     * @param tableName name of the table associated with the control set
     * @param constraints control set that defines incidence conditions for a variable
     * @return int[][] of the table values.  Rows are the table rows and columns are the conditions defined
     *         for the control set.
     */
    public int[][] submitGetControlsTableFieldsQuery( String tableName, String geogName, HashMap<Integer, Constraint> constraints ) {

        int[][] result;

        // define the query to retrieve the incidence table
        String query = "SELECT " + geogName + ", ";
        int count = 1;
        String separater = ", ";
        for ( int id=1; id <= constraints.size(); id++ ) {
        	if ( constraints.containsKey(id) ) {
                Constraint constraint = constraints.get( id );
                query += ( constraint.getControlField() ); 
                if ( count++ == constraints.values().size() )
                    separater = " ";
                query += separater; 
        	}
        	else {
        		count++;
        	}
        }
        query += " FROM " + tableName; 

        
        // execute the query, and save the result set as a 2 dimensional int array.
        ArrayList<int[]> rsList = new ArrayList<int[]>();
        Connection conn = null;
        try
        {

            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
            PreparedStatement ps = conn.prepareStatement( query );
            ResultSet rs = ps.executeQuery();
            while ( rs.next() ) {
                int numColumns = rs.getMetaData().getColumnCount();
                int[] row = new int[numColumns];
                for ( int i=0; i < numColumns; i++ )
                    row[i] = rs.getInt( i+1 );
                rsList.add( row );
            }

            result = new int[rsList.size()][];
            for ( int i=0; i < rsList.size(); i++ ) {
                result[i] = rsList.get(i);
            }
            
        }
        catch (SQLException e)
        {
            logger.error( "error executing SQL query:" );
            logger.error( "SQL:  " + query, e );
            throw new DAOException(e.getMessage());
        }
        finally
        {
            ConnectionHelper.closeConnection(conn);
        }
        
        return result;
        
    }

    
    /**
     * Form and submit the query to get an array of values from a table for the fields in the control set summarized for the geogName
     * 
     * @param tableName name of the table associated with the control set
     * @param geoName name of the table field by which values should be summarized
     * @param constraints control set that defines incidence conditions for a variable
     * @return int[][] of the table values.  Rows are the table rows and columns are the conditions defined
     *         for the control set.
     */
    public int[][] submitGetAggregateControlsTableFieldsQuery( String tableName, String geogName, HashMap<Integer, Constraint> constraints ) {

        int[][] result;

        // define the query to retrieve the incidence table
        String query = "SELECT " + geogName + ", ";
        int count = 1;
        String separater = ", ";
        for ( int id=1; id <= constraints.size(); id++ ) {
        	if ( constraints.containsKey(id) ) {
                Constraint constraint = constraints.get( id );
                query += ( "SUM(" + constraint.getControlField() + ")" ); 
                if ( count++ == constraints.values().size() )
                    separater = " ";
                query += separater; 
        	}
        	else {
        		count++;
        	}
        }
        query += " FROM " + tableName;
        query += " GROUP BY " + geogName;
        //query += " ORDER BY " + geogName;

        
        // execute the query, and save the result set as a 2 dimensional int array.
        ArrayList<int[]> rsList = new ArrayList<int[]>();
        Connection conn = null;
        try
        {

            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
            PreparedStatement ps = conn.prepareStatement( query );
            ResultSet rs = ps.executeQuery();
            while ( rs.next() ) {
                int numColumns = rs.getMetaData().getColumnCount();
                int[] row = new int[numColumns];
                for ( int i=0; i < numColumns; i++ )
                    row[i] = rs.getInt( i+1 );
                rsList.add( row );
            }

            result = new int[rsList.size()][];
            for ( int i=0; i < rsList.size(); i++ ) {
                result[i] = rsList.get(i);
            }
            
        }
        catch (SQLException e)
        {
            logger.error( "error executing SQL query:" );
            logger.error( "SQL:  " + query, e );
            throw new DAOException(e.getMessage());
        }
        finally
        {
            ConnectionHelper.closeConnection(conn);
        }
        
        return result;
        
    }

    
    /**
     * Form and submit the query to get an array of values from a table for the fields in the fieldNames array
     * 
     * @param tableName name of the table associated with the control set
     * @param fieldNames array of field names for which columns of data should be returned in the result set
     * @return int[][] of the table values.  Rows are the table rows and columns are the fields specified.
     */
    public int[][] submitGetTableFieldsQuery( String tableName, String orderByField, String[] fieldNames ) {

        int[][] result;

        // define the query to retrieve the incidence table
        String query = "SELECT ";
        int count = 1;
        String separater = ", ";
        for ( String name : fieldNames ) {
            query += name; 
            if ( count++ == fieldNames.length )
                separater = " ";
            query += separater; 
        }
        query += " FROM " + tableName; 
        //query += " ORDER BY " + orderByField; 

        
        // execute the query, and save the result set as a 2 dimensional int array.
        ArrayList<int[]> rsList = new ArrayList<int[]>();
        Connection conn = null;
        try
        {

            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
            PreparedStatement ps = conn.prepareStatement( query );
            ResultSet rs = ps.executeQuery();
            while ( rs.next() ) {
                int numColumns = rs.getMetaData().getColumnCount();
                int[] row = new int[numColumns];
                for ( int i=0; i < numColumns; i++ )
                    row[i] = rs.getInt( i+1 );
                rsList.add( row );
            }

            result = new int[rsList.size()][];
            for ( int i=0; i < rsList.size(); i++ ) {
                result[i] = rsList.get(i);
            }
            
        }
        catch (SQLException e)
        {
            logger.error( "error executing SQL query:" );
            logger.error( "SQL:  " + query, e );
            throw new DAOException(e.getMessage());
        }
        finally
        {
            ConnectionHelper.closeConnection(conn);
        }
        
        return result;
        
    }

    
    /**
     * Form and submit the query to get the count incidence table for the control set
     * 
     * @param incidenceTableName name of the incidence table for the control set
     * @param constraints control set that defines incidence conditions for a variable
     * @return int[][] of the incidence table values.  Rows are the elements to be balnced,
     *         most likely households, and columns are the number of conditions defined
     *         for the control set.
     */
    public int[][] submitGetCountIncidenceTableQuery( String incidenceTableName, String countByVariable, HashMap<Integer, Constraint> constraints, int[] hhIdIndex ) {

        int[][] result;

        // define the query to retrieve the incidence table
        String query = "SELECT " + countByVariable + ", ";
        int count = 1;
        String separater = ", ";
        for ( int id=1; id <= constraints.size(); id++ ) {
        	if ( constraints.containsKey(id) ) {
                Constraint constraint = constraints.get( id );
                query += ( "sum(" + constraint.getField() + constraint.getId() + ")" ); 
                if ( count++ == constraints.values().size() )
                    separater = " ";
                query += separater; 
        	}
        	else {
        		count++;
        	}
        }
        query += " FROM " + incidenceTableName + " GROUP BY " + countByVariable;
        System.out.println("---query="+query);


        // execute the query, and save the result set as a 2 dimensional int array.
        HashMap<Integer, int[]> rsMap = new HashMap<Integer, int[]>();
        Connection conn = null;
        try
        {
        	
            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
            PreparedStatement ps = conn.prepareStatement( query );
            ResultSet rs = ps.executeQuery();

            int numColumns = rs.getMetaData().getColumnCount();
            int maxK=0;
            while ( rs.next() ) {
                int[] row = new int[numColumns-1];
                for ( int i=0; i < numColumns-1; i++ )
                    row[i] = rs.getInt( i+1+1 );
                int id = rs.getInt( 1 );
                int k = hhIdIndex[id];             
                rsMap.put( k, row );
                if(k>maxK) maxK=k;
            }
            
            if(rsMap.size()!=(maxK+1)){            
            	System.out.println("---Number of hhs in person input table="+rsMap.size());
            	System.out.println("---Number of hhs in hh input table="+(maxK+1));
            	System.out.println("---Input PUMS data not consistent in person and hh tables, quitting...");
            	System.exit(-1);            	
            }
                                
            result = new int[rsMap.size()][];
            
            for ( int k : rsMap.keySet() ) {
            	int[] values = rsMap.get(k);     
                result[k] = values;
            }
            
        }
        catch (SQLException e)
        {
            logger.error( "error executing SQL query:" );
            logger.error( "SQL:  " + query, e );
            throw new DAOException(e.getMessage());
        }
        finally
        {
            ConnectionHelper.closeConnection(conn);
        }
        
        return result;
        
    }

    
    /**
     * Check the tableName for existence.  If it exists, submit a 'DROP TABLE tableName' query.
     * @param tableName for which to check existence, and then delete if it exists;
     */
    private String clearTableFromDatabase( String tableName ) {
        
        String resultString = "";

        Connection conn = null;
        try
        {

            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
        	submitExecuteUpdateQuery( conn, "USE " + dbName );
        	submitExecuteUpdateQuery( conn, "DROP TABLE " + tableName );
        	
            resultString = tableName + " table was deleted from database " + dbName;            
            
        }
        catch (SQLException e)
        {
            resultString = tableName + " table did not exist in database " + dbName;
        }
        finally
        {
            ConnectionHelper.closeConnection(conn);
        }
        
        
        return resultString;

    }

    //Wu chanaged this to public to make it visible to other PopGenerator
    public void submitExecuteUpdate( String query ) {
        
        Connection conn = null;
        try
        {
            conn = ConnectionHelper.getConnection( dbServer, dbName, dbHost, user, password );
            PreparedStatement ps = conn.prepareStatement( query );
            ps.executeUpdate();
        }
        catch (SQLException e)
        {
            logger.error( "error executing SQL query:" );
            logger.error( "SQL:  " + query, e );
            throw new DAOException(e.getMessage());
        }
        finally
        {
            ConnectionHelper.closeConnection(conn);
        }
        
    }

    private void submitExecuteUpdateQuery( Connection conn, String query ) throws SQLException {
        PreparedStatement ps = conn.prepareStatement( query );
        ps.executeUpdate();
    }

    
    private int getIntValue( String name, String valueString ) {
        
        if ( valueString.equalsIgnoreCase("infinity") )
            return Integer.MAX_VALUE;
        
        int value = Integer.MIN_VALUE;
        try {
            value = Integer.parseInt( valueString );
        }
        catch( NumberFormatException e ) {
            System.out.println( "Expecting an integer value for name: " + name + 
                    " while parsing the constraint definition, but got: " + valueString + " instead." );
            e.printStackTrace();
        }
        return value;
    }    

    
}
