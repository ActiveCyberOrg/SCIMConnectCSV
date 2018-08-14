/*******************************************************************************
 * Copyright (c) 2017, Okta, Inc. and/or its affiliates. All rights reserved.
 ******************************************************************************/
package com.okta.scim.util.file;

import com.okta.scim.server.capabilities.UserManagementCapabilities;
import com.okta.scim.server.exception.DuplicateGroupException;
import com.okta.scim.server.exception.EntityNotFoundException;
import com.okta.scim.server.exception.OnPremUserManagementException;
import com.okta.scim.server.service.SCIMService;
import com.okta.scim.util.model.Email;
import com.okta.scim.util.model.Name;
import com.okta.scim.util.model.PaginationProperties;
import com.okta.scim.util.model.SCIMFilter;
import com.okta.scim.util.model.SCIMFilterType;
import com.okta.scim.util.model.SCIMGroup;
import com.okta.scim.util.model.SCIMGroupQueryResponse;
import com.okta.scim.util.model.SCIMUser;
import com.okta.scim.util.model.SCIMUserQueryResponse;
import org.codehaus.jackson.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class SCIMServiceImpl implements SCIMService {
    //Absolute path for users.json set in the dispatcher-servlet.xml
    private String usersFilePath;
    //Absolute path for groups.json set in the dispatcher-servlet.xml
    private String groupsFilePath;
    private Map<String, SCIMUser> userMap = new HashMap<String, SCIMUser>();
    private Map<String, SCIMGroup> groupMap = new HashMap<String, SCIMGroup>();
    private String userCustomUrn;
    private boolean useFilePersistence = true;

    private static final Logger LOGGER = LoggerFactory.getLogger(SCIMServiceImpl.class);

    @PostConstruct
    public void afterCreation() throws Exception {
    	LOGGER.debug("In 'afterCreation'");
    	Properties applicationProperties = SCIMConnectorUtil.loadProperties("application");
        userCustomUrn = applicationProperties.getProperty("customSchemaName");
        String tmpUserFilePath = applicationProperties.getProperty("usersFilePath");
        if(!tmpUserFilePath.isEmpty()){
        	setUsersFilePath(tmpUserFilePath);
        }
        LOGGER.debug("userCustomUrn: " + userCustomUrn);
        initPersistence();
        if (useFilePersistence) {
            updateCache();
            return;
        }
    }

    private void initPersistence() throws Exception {
        //Both the usersFilePath and groupsFilePath should be present to consider to use the files to read/write.
        if (!StringUtils.isEmpty(usersFilePath) && !StringUtils.isEmpty(groupsFilePath)) {
            File userFile = new File(usersFilePath);
            if (!userFile.exists()) {
                LOGGER.error("Cannot find the users file [" + usersFilePath + "]");
                return;
            }
            //Make sure the customer did not provide a relative path, in which case the WEB-INF/classes/... json file is loaded.
            if (!userFile.getAbsolutePath().equals(usersFilePath)) {
                LOGGER.error("The absolute path of the users file is not [" + usersFilePath + "]");
                return;
            }


            File groupFile = new File(groupsFilePath);
            if (!groupFile.exists()) {
                LOGGER.error("Cannot find the groups file [" + groupsFilePath + "]");
                return;
            }
            //Make sure the customer did not provide a relative path, in which case the WEB-INF/classes/... json file is loaded.
            if (!groupFile.getAbsolutePath().equals(groupsFilePath)) {
                LOGGER.error("The absolute path of the groups file is not [" + groupsFilePath + "]");
                return;
            }

            //If there are valid users.json and groups.json
            useFilePersistence = true;
        }
    }

    public String getUsersFilePath() {
        return usersFilePath;
    }

    public void setUsersFilePath(String usersFilePath) {
        this.usersFilePath = usersFilePath;
    }

    public String getGroupsFilePath() {
        return groupsFilePath;
    }

    public void setGroupsFilePath(String groupsFilePath) {
        this.groupsFilePath = groupsFilePath;
    }

    /**
     * Get all the users.
     * <p>
     * This method is invoked when a GET is made to /Users
     * In order to support pagination (So that the client and the server are not overwhelmed), this method supports querying based on a start index and the
     * maximum number of results expected by the client. The implementation is responsible for maintaining indices for the SCIM Users.
     *
     * @param pageProperties denotes the pagination properties
     * @param filter         denotes the filter
     * @return the response from the server, which contains a list of  users along with the total number of results, start index and the items per page
     * @throws com.okta.scim.server.exception.OnPremUserManagementException
     *
     */
    @Override
    public SCIMUserQueryResponse getUsers(PaginationProperties pageProperties, SCIMFilter filter) throws OnPremUserManagementException {
    	LOGGER.info("In 'SCIMUserQueryResponse - First 1'.");
        List<SCIMUser> users = null;
        // Update the cache if the file hasn't been modified
        //updateCache();
        if (filter != null) {
        	LOGGER.info("Filter value: " + filter.getFilterValue());
            //Get users based on a filter
            users = getUserByFilter(filter);
            //Example to show how to construct a SCIMUserQueryResponse and how to set stuff.
            SCIMUserQueryResponse response = new SCIMUserQueryResponse();
            //The total results in this case is set to the number of users. But it may be possible that
            //there are more results than what is being returned => totalResults > users.size();
            response.setTotalResults(users.size());
            //Actual results which need to be returned
            response.setScimUsers(users);
            //The input has some page properties => Set the start index.
            if (pageProperties != null) {
                response.setStartIndex(pageProperties.getStartIndex());
                LOGGER.info("pageProperties Start Index: " + pageProperties.getStartIndex());
            }
            return response;
        } else {
            return getUsers(pageProperties);
        }
    }
    
    private SCIMUserQueryResponse getUsers(PaginationProperties pageProperties) {
    	LOGGER.info("In 'SCIMUserQueryResponse - Second'");
    	
    	// Update the cache if the file hasn't been modified
        updateCache();
    	
        SCIMUserQueryResponse response = new SCIMUserQueryResponse();
        /**
         * Below is an example to show how to deal with exceptional conditions while writing the connector.
         * If you cannot complete the UserManagement operation on the on premises
         * application because of any error/exception, you should throw the OnPremUserManagementException as shown below.
         * <b>Note:</b> You can throw this exception from all the CRUD (Create/Retrieve/Update/Delete) operations defined on
         * Users/Groups in the SCIM interface.
         */
        if (userMap == null) {
            //Note that the Error Code "o34567" is arbitrary - You can use any code that you want to.
            throw new OnPremUserManagementException("o34567", "Cannot get the users. The userMap is null");
        }

        int totalResults = userMap.size();
        response.setTotalResults(totalResults);
        List<SCIMUser> users = new ArrayList<SCIMUser>();
        if (pageProperties != null) {
            //Set the start index to the response.
            response.setStartIndex(pageProperties.getStartIndex());
            LOGGER.info("pageProperties Start Index: " + pageProperties.getStartIndex() + "\nCount: " + pageProperties.getCount());
           
            int startCount = (int)pageProperties.getStartIndex();
            int endCount = startCount + pageProperties.getCount();
            int count = 1;
            for (String key : userMap.keySet()) {
            	LOGGER.info(key + " count: " + count + " startCount: " + startCount + " endCount: " + endCount);
            	if(count >= startCount && count < endCount){
            		users.add(userMap.get(key));
            		LOGGER.info("Adding this person: " +key);
            	}
            	else if (count > endCount){
            		break;
            	}
            	count++;
            }
        }
        else
        {
        	LOGGER.info("pageProperties is null");
            for (String key : userMap.keySet()) {
            	users.add(userMap.get(key));
        		LOGGER.info("Adding this person: " +key);
            }
        }
        
        
        //Set the actual results
        response.setScimUsers(users);
        return response;
    }
    
    /**
     * A simple example of how to use <code>SCIMFilter</code> to return a list of users which match the filter criteria.
     * <p/>
     * An Admin who configures the UM would specify a SCIM field name as the UniqueId field name. This field and its value would be sent by Okta in the filter.
     * While implementing the connector, the below points should be noted about the filters.
     * <p/>
     * If you choose a single valued attribute as the UserId field name while configuring the App Instance on Okta,
     * you would get an equality filter here.
     * For example, if you choose userName, the Filter object below may represent an equality filter like "userName eq "someUserName""
     * If you choose the name.familyName as the UserId field name, the filter object may represent an equality filter like
     * "name.familyName eq "someLastName""
     * If you choose a multivalued attribute (email, for example), the <code>SCIMFilter</code> object below may represent an OR filter consisting of two sub-filters like
     * "email eq "abc@def.com" OR email eq "def@abc.com""
     * Of the few multi valued attributes part of the SCIM Core Schema (Like email, address, phone number), only email would be supported as a UserIdField name on Okta.
     * So, you would have to deal with OR filters only if you choose email.
     * <p/>
     * When you get a <code>SCIMFilter</code>, you should check the filter field name (And make sure it is the same field which was configured with Okta), value, condition, etc. as shown in the examples below.
     *
     * @param filter the SCIM filter
     * @return list of users that match the filter
     */
    private List<SCIMUser> getUserByFilter(SCIMFilter filter) {
        List<SCIMUser> users = new ArrayList<SCIMUser>();

        SCIMFilterType filterType = filter.getFilterType();

        if (filterType.equals(SCIMFilterType.EQUALS)) {
            //Example to show how to deal with an Equality filter
            users = getUsersByEqualityFilter(filter);
        } else if (filterType.equals(SCIMFilterType.OR)) {
            //Example to show how to deal with an OR filter containing multiple sub-filters.
            users = getUsersByOrFilter(filter);
        } else {
            LOGGER.error("The Filter " + filter + " contains a condition that is not supported");
        }
        return users;
    }
    
    /**
     * This is an example of how to deal with an equality filter.<p>
     * If you choose a custom field/complex field (name.familyName) or any other singular field (userName/externalId), you should get an equality filter here.
     *
     * @param filter the EQUALS filter
     * @return list of users that match the filter
     */
    private List<SCIMUser> getUsersByEqualityFilter(SCIMFilter filter) {
        String fieldName = filter.getFilterAttribute().getAttributeName();
        String value = filter.getFilterValue();
        LOGGER.info("Equality Filter : Field Name [ " + fieldName + " ]. Value [ " + value + " ]");
        List<SCIMUser> users = new ArrayList<SCIMUser>();

        //A basic example of how to return users that match the criteria
        for (Map.Entry<String, SCIMUser> entry : userMap.entrySet()) {
            SCIMUser user = entry.getValue();
            boolean userFound = false;
            //Ex : "userName eq "someUserName""
            if (fieldName.equalsIgnoreCase("userName")) {
                String userName = user.getUserName();
                if (userName != null && userName.equals(value)) {
                    userFound = true;
                }
            } else if (fieldName.equalsIgnoreCase("id")) {
                //"id eq "someId""
                String id = user.getId();
                if (id != null && id.equals(value)) {
                    userFound = true;
                }
            } else if (fieldName.equalsIgnoreCase("name")) {
                String subFieldName = filter.getFilterAttribute().getSubAttributeName();
                Name name = user.getName();
                if (name == null || subFieldName == null) {
                    continue;
                }
                if (subFieldName.equalsIgnoreCase("familyName")) {
                    //"name.familyName eq "someFamilyName""
                    String familyName = name.getLastName();
                    if (familyName != null && familyName.equals(value)) {
                        userFound = true;
                    }
                } else if (subFieldName.equalsIgnoreCase("givenName")) {
                    //"name.givenName eq "someGivenName""
                    String givenName = name.getFirstName();
                    if (givenName != null && givenName.equals(value)) {
                        userFound = true;
                    }
                }
            } else if (filter.getFilterAttribute().getSchema().equalsIgnoreCase(userCustomUrn)) { //Check that the Schema name is the Custom Schema name to process the filter for custom fields
                /**
                 * The example below shows one of the two ways to get a custom property.<p>
                 * The other way is to use the getter directly to get the value - user.getCustomStringProperty("urn:okta:onprem_app:1.0:user:custom", fieldName, null) will get the value
                 * if the fieldName is a root element. If fieldName is a child of any other field, user.getCustomStringProperty("urn:okta:onprem_app:1.0:user:custom", fieldName, parentName)
                 * will get the value.
                 */
                //"urn:okta:onprem_app:1.0:user:custom:departmentName eq "someValue""
                Map<String, JsonNode> customPropertiesMap = user.getCustomPropertiesMap();
                //Get the custom properties map (SchemaName -> JsonNode)
                if (customPropertiesMap == null || !customPropertiesMap.containsKey(userCustomUrn)) {
                    continue;
                }
                //Get the JsonNode having all the custom properties for this schema
                JsonNode customNode = customPropertiesMap.get(userCustomUrn);
                //Check if the node has that custom field
                if (customNode.has(fieldName) && customNode.get(fieldName).asText().equalsIgnoreCase(value)) {
                    userFound = true;
                }
            }

            if (userFound) {
                users.add(user);
            }
        }
        return users;
    }
    
    /**
     * This is an example for how to deal with an OR filter. An OR filter consists of multiple sub equality filters.
     *
     * @param filter the OR filter with a set of sub filters expressions
     * @return list of users that match any of the filters
     */
    private List<SCIMUser> getUsersByOrFilter(SCIMFilter filter) {
        //An OR filter would contain a list of filter expression. Each expression is a SCIMFilter by itself.
        //Ex : "email eq "abc@def.com" OR email eq "def@abc.com""
        List<SCIMFilter> subFilters = filter.getFilterExpressions();
        LOGGER.info("OR Filter : " + subFilters);
        List<SCIMUser> users = new ArrayList<SCIMUser>();
        //Loop through the sub filters to evaluate each of them.
        //Ex : "email eq "abc@def.com""
        for (SCIMFilter subFilter : subFilters) {
            //Name of the sub filter (email)
            String fieldName = subFilter.getFilterAttribute().getAttributeName();
            //Value (abc@def.com)
            String value = subFilter.getFilterValue();
            //For all the users, check if any of them have this email
            for (Map.Entry<String, SCIMUser> entry : userMap.entrySet()) {
                boolean userFound = false;
                SCIMUser user = entry.getValue();
                //In this example, since we assume that the field name configured with Okta is "email", checking if we got the field name as "email" here
                if (fieldName.equalsIgnoreCase("email")) {
                    //Get the user's emails and check if the value is the same as in the filter
                    Collection<Email> emails = user.getEmails();
                    if (emails != null) {
                        for (Email email : emails) {
                            if (email.getValue().equalsIgnoreCase(value)) {
                                userFound = true;
                                break;
                            }
                        }
                    }
                }
                if (userFound) {
                    users.add(user);
                }
            }
        }
        return users;
    }
    
    /**
     * Get all the groups.
     * <p>
     * This method is invoked when a GET is made to /Groups
     * In order to support pagination (So that the client and the server) are not overwhelmed, this method supports querying based on a start index and the
     * maximum number of results expected by the client. The implementation is responsible for maintaining indices for the SCIM groups.
     *
     * @param pageProperties @see com.okta.scim.util.model.PaginationProperties An object holding the properties needed for pagination - startindex and the count.
     * @return SCIMGroupQueryResponse the response from the server containing the total number of results, start index and the items per page along with a list of groups
     * @throws com.okta.scim.server.exception.OnPremUserManagementException
     *
     */
    @Override
    public SCIMGroupQueryResponse getGroups(PaginationProperties pageProperties) throws OnPremUserManagementException {
        SCIMGroupQueryResponse response = new SCIMGroupQueryResponse();
        int totalResults = groupMap.size();
        if (pageProperties != null) {
            //Set the start index
            response.setStartIndex(pageProperties.getStartIndex());
        }
        //In this example we are setting the total results to the number of results in this page. If there are more
        //results than the number the client asked for (pageProperties.getCount()), then you need to set the total results correctly
        response.setTotalResults(totalResults);
        List<SCIMGroup> groups = new ArrayList<SCIMGroup>();
        for (String key : groupMap.keySet()) {
            groups.add(groupMap.get(key));
        }
        //Set the actual results
        response.setScimGroups(groups);
        return response;
    }

    /**
     * Get all the Okta User Management capabilities that this SCIM Service has implemented.
     * <p>
     * This method is invoked when a GET is made to /ServiceProviderConfigs. It is called only when you are testing
     * or modifying your connector configuration from the Okta Application instance UM UI. If you change the return values
     * at a later time please re-test and re-save your connector settings to have your new return values respected.
     * <p>
     * These User Management capabilities help customize the UI features available to your app instance and tells Okta
     * all the possible commands that can be sent to your connector.
     *
     * @return all the implemented User Management capabilities.
     */
    @Override
    public UserManagementCapabilities[] getImplementedUserManagementCapabilities() {
        return UserManagementCapabilities.values();
    }

    /**
     * Update the cache based on the data stored in the files
     */
    private synchronized void updateCache() {
    	LOGGER.debug("In 'updateCache' function");
        //Nothing to update if persistence is not enabled
        if (!useFilePersistence) {
            return;
        }

        try {
        	LOGGER.debug("About to call the 'readUsersFromCSV' function. usersFilePath: " + usersFilePath);
        	userMap.clear();
        	SCIMConnectorUtil.readUsersFromCSV(userMap, usersFilePath);
        } catch (Exception e) {
            throw new OnPremUserManagementException("Exception in building the user cache from the file [" + usersFilePath + "]", e);
        }
    }

	@Override
	public SCIMGroup createGroup(SCIMGroup arg0) throws OnPremUserManagementException, DuplicateGroupException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SCIMUser createUser(SCIMUser arg0) throws OnPremUserManagementException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void deleteGroup(String arg0) throws OnPremUserManagementException, EntityNotFoundException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SCIMGroup getGroup(String arg0) throws OnPremUserManagementException, EntityNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SCIMGroup updateGroup(String arg0, SCIMGroup arg1)
			throws OnPremUserManagementException, EntityNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SCIMUser updateUser(String arg0, SCIMUser arg1)
			throws OnPremUserManagementException, EntityNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SCIMUser getUser(String arg0) throws OnPremUserManagementException, EntityNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}
}

