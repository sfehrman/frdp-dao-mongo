#!/bin/bash

M2="/Users/scott.fehrman/.m2/repository"

CP="target/classes"
CP="${CP}:${M2}/com/forgerock/frdp/frdp-framework/1.0.0/frdp-framework-1.0.0.jar"
CP="${CP}:${M2}/javax/ws/rs/javax.ws.rs-api/2.0/javax.ws.rs-api-2.0.jar"
CP="${CP}:${M2}/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"
CP="${CP}:${M2}/junit/junit/4.10/junit-4.10.jar"
CP="${CP}:${M2}/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"
CP="${CP}:${M2}/javax/servlet/servlet-api/2.3/servlet-api-2.3.jar"
CP="${CP}:${M2}/org/mongodb/mongodb-driver/3.7.1/mongodb-driver-3.7.1.jar"
CP="${CP}:${M2}/org/mongodb/bson/3.7.1/bson-3.7.1.jar"
CP="${CP}:${M2}/org/mongodb/mongodb-driver-core/3.7.1/mongodb-driver-core-3.7.1.jar"
CP="${CP}:${M2}/org/mongodb/mongo-java-driver/3.4.1/mongo-java-driver-3.4.1.jar"

java -cp "${CP}" com.forgerock.frdp.dao.mongo.TestMongoDataAccess

exit