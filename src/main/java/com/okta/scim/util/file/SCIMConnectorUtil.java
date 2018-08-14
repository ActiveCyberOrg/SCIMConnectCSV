/*******************************************************************************
 * Copyright (c) 2017, Okta, Inc. and/or its affiliates. All rights reserved.
 ******************************************************************************/
package com.okta.scim.util.file;

import com.okta.scim.util.exception.SCIMSerializationException;
import com.okta.scim.util.model.Email;
import com.okta.scim.util.model.Name;
import com.okta.scim.util.model.SCIMUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.io.FileReader;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class SCIMConnectorUtil {
		
    private static final Logger LOGGER = LoggerFactory.getLogger(SCIMConnectorUtil.class);
    private static final String key_id = "id";
    private static final String key_userName = "userName";
    private static final String key_familyName = "familyName";
    private static final String key_givenName = "givenName";
    private static final String key_email = "email";
    private static final String key_user_active = "active";
    private static final String key_user_password = "password";
    
    /**
     * Read the users from a file into a users map
     *
     * @param userMap
     * @param usersFilePath
     * @throws java.io.IOException
     */
    public static void readUsersFromCSV(Map<String, SCIMUser> userMap, String usersFilePath) throws IOException, SCIMSerializationException {
   	
    	FileReader fileReader = null;
		
		CSVParser csvFileParser = null;
		        
        CSVFormat csvFileFormat = CSVFormat.RFC4180.withFirstRecordAsHeader();
        
        // Load CSV Mapping
        Properties csvColumnMappingProperties = loadProperties("CSVColumnMapping");
        // Load Application properties
        Properties applicationProperties = loadProperties("application");
     
        try {
        	LOGGER.debug("In 'readUsersFromCSV'. Reading the CSV. usersFilePath: " + usersFilePath);
        	
            //initialize FileReader object
        	File uploadedCSV = new File(usersFilePath);
        	
        	
        	LOGGER.debug("In 'readUsersFromCSV'. CSV file: " + uploadedCSV.getName());
        	fileReader = new FileReader(uploadedCSV);
            
            //initialize CSVParser object
            csvFileParser = new CSVParser(fileReader, csvFileFormat);
            
            //Get a list of CSV file records
            @SuppressWarnings("rawtypes")
			List csvRecords = csvFileParser.getRecords();
            
            if(csvRecords.isEmpty())
            {
            	return;
            }
            
            LOGGER.info("Number of CSV records: " + csvRecords.size());
            
            //Read the CSV file records starting from the second record to skip the header
            for (int i = 0; i < csvRecords.size(); i++) {
            	CSVRecord record = (CSVRecord)csvRecords.get(i);
            	
            	// Loading Custom attributes
                Enumeration e = csvColumnMappingProperties.propertyNames();
                
                // Check if the user is active. Only load the user into the map if it they are an active user.
                Boolean isUserActive = !(record.get(csvColumnMappingProperties.getProperty(key_user_active).split(",")[0]).equals(applicationProperties.getProperty("userInactiveValueInCSV")));
                Boolean mandatoryFieldsPresent = checkIfAllMandatoryFieldsArePopulated(csvColumnMappingProperties, record);
                
                if(isUserActive && mandatoryFieldsPresent){
                	SCIMUser user = new SCIMUser();
                    // Set UserName (SCIM Required)
                    String userName = record.get(csvColumnMappingProperties.getProperty(key_userName).split(",")[0]);
//                    LOGGER.info("Setting user's username.: " + csvColumnMappingProperties.getProperty(key_userName).split(",")[0]);
                    LOGGER.debug("Setting user's username.: " + userName);
                    user.setUserName(userName);
                    LOGGER.debug("Finished setting username");
                    
                    // Set ID (SCIM Required)
                    user.setId(record.get(csvColumnMappingProperties.getProperty(key_id).split(",")[0]));
                    LOGGER.debug("Finished setting user's ID");
                    
                    // Set user 'Active' status - If the value in the Column for 'Active' matches the value defined in the application.properties 'userActiveValueInCSV' value
                    // user.setActive(Boolean.parseBoolean(record.get(csvColumnMappingProperties.getProperty(key_user_active).split(",")[0])));
                     
                    user.setActive(isUserActive);
                    LOGGER.debug("Finished setting user's Active status to: " + isUserActive);
                    
                    // Set user 'Name' status                
                    Name name = new Name(record.get(csvColumnMappingProperties.getProperty(key_familyName).split(",")[0]) + " " + record.get(csvColumnMappingProperties.getProperty(key_givenName).split(",")[0]), 
                    		record.get(csvColumnMappingProperties.getProperty(key_familyName).split(",")[0]), record.get(csvColumnMappingProperties.getProperty(key_givenName).split(",")[0]));
                    user.setName(name);
                    LOGGER.debug("Finished setting user's 'Name'");
                    
                    // Set user 'Email' status
                    Email email = new Email(record.get(csvColumnMappingProperties.getProperty(key_email).split(",")[0]), "work", true);
                    Collection<Email> emails = new ArrayList<Email>();
                    emails.add(email);
                    user.setEmails(emails);
                    LOGGER.debug("Finished setting user's 'Email'");
                                   
                    // Set user 'Password'
                    if((csvColumnMappingProperties.getProperty(key_user_password) != null) && (!csvColumnMappingProperties.getProperty(key_user_password).isEmpty())){
                    	user.setPassword(record.get(csvColumnMappingProperties.getProperty(key_user_password).split(",")[0]));
                        LOGGER.debug("Finished setting user's password");                	
                    }
                    
                    
                    
                    while (e.hasMoreElements()) {
                        String key = (String) e.nextElement();
                        LOGGER.debug("Enumerating Custom elements. e: " + key);
                        LOGGER.debug("SCIMVariable? : " + csvColumnMappingProperties.getProperty(key).split(",")[2]);
                        if((!csvColumnMappingProperties.getProperty(key).isEmpty()) 
                        		&& (csvColumnMappingProperties.getProperty(key).split(",")[2].toLowerCase().equals("isNotSCIMVariable".toLowerCase()))){
                        	LOGGER.debug("Found a Custom column. e: " + key);
                        	if(csvColumnMappingProperties.getProperty(key).split(",")[1].equals("String")){
                        		user.setCustomStringValue(applicationProperties.getProperty("customSchemaName"), key,
                        				record.get(csvColumnMappingProperties.getProperty(key).split(",")[0]));
                        	}
                        	else if(csvColumnMappingProperties.getProperty(key).split(",")[1].equals("Boolean")) {
                        		user.setCustomBooleanValue(applicationProperties.getProperty("customSchemaName"), key,
                        				Boolean.parseBoolean(record.get(csvColumnMappingProperties.getProperty(key).split(",")[0])));
                        	}
                        	else if(csvColumnMappingProperties.getProperty(key).split(",")[1].equals("Integer")){
                        		user.setCustomIntValue(applicationProperties.getProperty("customSchemaName"), key,
                        				Integer.parseInt(record.get(csvColumnMappingProperties.getProperty(key).split(",")[0])));
                        	}
                        	else if(csvColumnMappingProperties.getProperty(key).split(",")[1].equals("Double")){
                        		user.setCustomDoubleValue(applicationProperties.getProperty("customSchemaName"), key,
                        				Double.parseDouble(record.get(csvColumnMappingProperties.getProperty(key).split(",")[0])));
                        	}
                        }
                      }
                    userMap.put(user.getId(), user);
                }
            	
                
			}
            saveProcessedFile(uploadedCSV);
        } 
        catch (Exception e) {
        	LOGGER.error("Error in CsvFileReader !!!", e);
            e.printStackTrace();
            throw new SCIMSerializationException(e);
        } finally {
            try {
                fileReader.close();
                csvFileParser.close();
            } catch (IOException e) {
            	LOGGER.error("Error while closing fileReader/csvFileParser !!!");
                e.printStackTrace();
            }
        }
    }
    
    // All SCIM Variables and columns marked: 'isMandatory' are mandatory
    private static Boolean checkIfAllMandatoryFieldsArePopulated(Properties csvColumnMappingProperties,
			CSVRecord record) {
    	LOGGER.debug("In 'checkIfAllMandatoryFieldsArePopulated'");
    	 Enumeration e = csvColumnMappingProperties.propertyNames();
    	 while (e.hasMoreElements()){
    		 String key = (String) e.nextElement();
             LOGGER.debug("Enumerating elements. e: " + key);
             
             // If The property is not empty AND (It is a SCIM Variable OR It is a mandatory field)
             Boolean isPropertyEmpty = csvColumnMappingProperties.getProperty(key).isEmpty();
             Boolean isSCIMVariable = csvColumnMappingProperties.getProperty(key).split(",")[2].toLowerCase().equals("isSCIMVariable".toLowerCase());
             Boolean isMandatory = csvColumnMappingProperties.getProperty(key).split(",")[3].toLowerCase().equals("isMandatory".toLowerCase());
             
             
             if((!isPropertyEmpty) && (isSCIMVariable || isMandatory)){
            	 // Check that all the fields are not empty
            	 String value = record.get(csvColumnMappingProperties.getProperty(key).split(",")[0]);
            	 LOGGER.debug("Check to see if the field is empty. Value: " + value);
            	 
            	 if (value.trim().isEmpty()){
            		 return false;
            	 }
             }
    	 }
		return true;
	}

	private static void saveProcessedFile(File uploadedCSV) {
    	Properties applicationProperties = loadProperties("application");
    	
    	DateFormat df = new SimpleDateFormat("dd_MM_yy__HH_mm_ss");
    	Calendar calobj = Calendar.getInstance();
    	
		String destination = applicationProperties.getProperty("csvProcessedFolder") + File.separator + "users_" + df.format(calobj.getTime()) + ".csv";
    	File destFile = new File(destination);
		
	    InputStream is = null;
	    OutputStream os = null;
	    try {
	        is = new FileInputStream(uploadedCSV);
	        os = new FileOutputStream(destFile);
	        byte[] buffer = new byte[1024];
	        int length;
	        while ((length = is.read(buffer)) > 0) {
	            os.write(buffer, 0, length);
	        }
	    }
	    catch (Exception ex)
	    {
	    	
	    }
	    finally {
	        try {
				is.close();
				os.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	}

	public static File lastFileModified(String dir) {
        File fl = new File(dir);
        File[] files = fl.listFiles(new FileFilter() {          
            public boolean accept(File file) {
                return file.isFile();
            }
        });
        long lastMod = Long.MIN_VALUE;
        File choice = null;
        for (File file : files) {
            if (file.lastModified() > lastMod) {
                choice = file;
                lastMod = file.lastModified();
            }
        }
        return choice;
    }

    public static Properties loadProperties(String property){
    	Properties prop = new Properties();
    	InputStream input = null;
    	//CSVColumnMapping

    	try {
//    		// What's the best way to have this as a property variable? 
//    		input = new FileInputStream("C:\\Servers\\" + property + ".properties");
//    		
    		File configDir = new File(System.getProperty("catalina.base"), "conf");
    		File configFile = new File(configDir, property + ".properties");
    		input = new FileInputStream(configFile);
    		LOGGER.info("Config file: " + configFile.getPath());

    		// load a properties file
    		prop.load(input);
    		
    		return prop;

    	} catch (IOException ex) {
    		ex.printStackTrace();
    	} finally {
    		if (input != null) {
    			try {
    				input.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    		}
    	}
    	return null;
    }    
}
