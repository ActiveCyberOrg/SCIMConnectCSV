#-------------------------------------------------------------------------------
# Copyright (c) 2017, Okta, Inc. and/or its affiliates. All rights reserved.
#-------------------------------------------------------------------------------
SCIM Server for CSV’s
========

This provides a working SCIM connector example where users and groups are from a CSV file.


Deployment instructions
========

1.
To deploy the war file to a tomcat server using Maven, edit the pom.xml file, search for ‘tomcat7-maven-plugin’, change the URL for the Tomcat Manager endpoint.

Make sure Maven is configured to send the admin username and password. Instructions here: http://tomcat.apache.org/maven-plugin-2.0/tomcat7-maven-plugin/usage.html

Then run the following Maven command:
mvn -e tomcat7:deploy

2.
Copy the files in the ‘resources’ folder into tomcat’s con folder. Edit the values in the files as required.

application.properties

customSchemaName=urn:okta:oktaTenantName_Appname_1:1.0:user:custom
userInactiveValueInCSV=F - This is the value in the ‘Active’ field which decides if the user is inactive. The ‘Active’ column is mapped in the CSVColumnMapping.properties file
usersFilePath=\\\\WIN-O4K1PA0V44F\\CSVUploads\\users.csv - Location from where CSV’s will be read from
csvProcessedFolder=\\\\WIN-O4K1PA0V44F\\CSVUploadsProcessed\\ - A copy of the processed file will be placed here


CSVColumnMapping.properties

userName=detemailad,String,isSCIMVariable,isMandatory
id=detnumber,String,isSCIMVariable,isMandatory
familyName=detsurname,String,isSCIMVariable,isMandatory
givenName=detprefnm,String,isSCIMVariable,isMandatory
email=detemailad,String,isSCIMVariable,isMandatory
active=active,Boolean,isSCIMVariable,isMandatory
scimNameA=detsurname,String,isNotSCIMVariable,isMandatory
scimNameB=detg1name1,Boolean,isNotSCIMVariable,isNotMandatory


// All mandatory fields are mandatory :)
// ‘scimNameA’ is an example of a custom field that exists in Okta. It is mapped to the ‘detsurname’ field in the CSV.
// 
