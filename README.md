# frdp-dao-mongo

ForgeRock Demonstration Platform : Data Access Object : MongoDB ... an implementation of the DAO interface using the MongoDB Java API

`git clone https://github.com/ForgeRock/frdp-dao-mongo.git`

## Requirements

The following items must be installed:

1. [Apache Maven](https://maven.apache.org/)
1. [Java Development Kit 8](https://openjdk.java.net/)
1. [MongoDB](https://www.mongodb.com) *(tested with 3.2)*

---
## Build

### Prerequisite:

The following items must be completed, in order:

1. [frdp-framework](https://github.com/ForgeRock/frdp-framework) ... clone / download then install using *Maven* (`mvn`)

### MongoDB Driver:

The `pom.xml` file is configured to install the **MongoDB Driver** with the *Maven* (`mvn`) process.

Reference [MongoDB Maven Repository](https://mvnrepository.com/artifact/org.mongodb/mongo-java-driver/3.7.1)

### Clean, Compile, Install:

Run *Maven* (`mvn`) processes to clean, compile and install the package:

```
mvn clean compile package install
```

Packages are added to the user's home folder: 

```
find ~/.m2/repository/com/forgerock/frdp/frdp-dao-mongo
/home/forgerock/.m2/repository/com/forgerock/frdp/frdp-dao-mongo
/home/forgerock/.m2/repository/com/forgerock/frdp/frdp-dao-mongo/maven-metadata-local.xml
/home/forgerock/.m2/repository/com/forgerock/frdp/frdp-dao-mongo/1.0.0
/home/forgerock/.m2/repository/com/forgerock/frdp/frdp-dao-mongo/1.0.0/frdp-dao-mongo-1.0.0.pom
/home/forgerock/.m2/repository/com/forgerock/frdp/frdp-dao-mongo/1.0.0/_remote.repositories
/home/forgerock/.m2/repository/com/forgerock/frdp/frdp-dao-mongo/1.0.0/maven-metadata-local.xml
/home/forgerock/.m2/repository/com/forgerock/frdp/frdp-dao-mongo/1.0.0/frdp-dao-mongo-1.0.0.jar
```

----
## Test

This section covers how to use the `TestMongoDataAccess.java` program which tests the MongoDB Data Access Object (`MongoDataAccess`) implementation.  A MongoDB installation must be configured to support a *test* `database` and `collection`.  The *test* program will perform `create, read, search, replace, delete` operations.

### Configure MongoDB to support the test program:

This *Test* procedure assumes that MongoDB has been installed. This example was tested on MacOS and CentoOS 7.x using the `test.sh` script.  The examples use the MongoDB admin user `root` with a password of `password`, replace usernames and passowrds as necessary.

1. Access MongoDB system \
`ssh root@hostname`
1. Connect as "root" user to create database and collection \
`mongo --username "root" --password "password" --authenticationDatabase "admin" admin`
1. Specify the database name \
`> use test-server;`
1. Drop existing database \
`> db.dropDatabase();`
1. Drop existing admin user \
`> db.dropUser("testadmin");`
1. Create admin user \
`> db.createUser({user:"testadmin",pwd:"password",roles:["readWrite","dbAdmin"]});`
1. Create collection \
`> db.createCollection("test");`
1. Logout as the "root" user \
`> quit();`
1. Connect as the "testadmin" user \
`mongo --username "testadmin" --password "password" --authenticationDatabase "test-server" test-server`
1. Create index in the collection for the "uid" attribute \
`> db.test.createIndex({"uid":1});`
1. Insert sample record into the collection \
`> db.test.insert({"comment": "This is a test document"});`
1. Display the sample record \
`> db.test.find();` \
`> db.test.find().pretty();`
1. Logout \
`> quit();`

### Update the `TestMongoDataAccess.java` sample program:

1. Edit the test program \
`vi src/main/java/com/forgerock/frdp/dao/mongo/TestMongoDataAccess.java`
1. Set the `MongoDataAccess.PARAM_HOST` parameter, change the value from `localhost` \
**Before:** \
`params.put(MongoDataAccess.PARAM_HOST, "localhost");` \
**After:** \
`params.put(MongoDataAccess.PARAM_HOST, "<HOST_NAME>");`
1. Build the project with *Maven* \
`mvn clean compile package install`

### Edit the `test.sh` script:

1. Set the `M2` variable to match your user folder name \
**Before:** \
`M2="/home/forgerock/.m2/repository"` \
**After:** \
`M2="/<USER_HOME_PATH>/.m2/repository"`
1. Run the `test.sh` script \
`sh ./test.sh` \
(sample test output below)

```
Dec 18, 2019 10:59:58 PM com.mongodb.diagnostics.logging.JULLogger log
INFO: Cluster created with settings {hosts=[localhost:27017], mode=SINGLE, requiredClusterType=UNKNOWN, serverSelectionTimeout='30000 ms', maxWaitQueueSize=500}
Dec 18, 2019 10:59:58 PM com.mongodb.diagnostics.logging.JULLogger log
INFO: Cluster description not yet available. Waiting for 30000 ms before timing out
Dec 18, 2019 10:59:59 PM com.mongodb.diagnostics.logging.JULLogger log
INFO: Opened connection [connectionId{localValue:1, serverValue:28}] to localhost:27017
Dec 18, 2019 10:59:59 PM com.mongodb.diagnostics.logging.JULLogger log
INFO: Monitor thread successfully connected to server with description ServerDescription{address=localhost:27017, type=STANDALONE, state=CONNECTED, ok=true, version=ServerVersion{versionList=[3, 2, 22]}, minWireVersion=0, maxWireVersion=4, maxDocumentSize=16777216, logicalSessionTimeoutMinutes=null, roundTripTimeNanos=1975504}
Dec 18, 2019 10:59:59 PM com.forgerock.frdp.dao.mongo.MongoDataAccess execute
WARNING: com.forgerock.frdp.dao.mongo.MongoDataAccess:execute: Operation object is null
Dec 18, 2019 10:59:59 PM com.mongodb.diagnostics.logging.JULLogger log
INFO: Opened connection [connectionId{localValue:2, serverValue:29}] to localhost:27017
====
==== Create output: error=false; state=SUCCESS; status='Created document'; params=none
==== Create json:   {"uid":"jdoe"}
====
Dec 18, 2019 10:59:59 PM com.forgerock.frdp.dao.mongo.MongoDataAccess create
WARNING: Document already exists: uid='jdoe'
====
==== Create output: error=true; state=ERROR; status='Document already exists: uid='jdoe''; params=none
==== Create json:   {}
====
====
==== Create output: error=false; state=SUCCESS; status='Created document'; params=none
==== Create json:   {"uid":"76e07605-af46-4dec-bb99-182600ab2354"}
====
Dec 18, 2019 10:59:59 PM com.forgerock.frdp.dao.mongo.MongoDataAccess read
WARNING: Document does not exist: uid='BadId123'
====
==== Read output: error=false; state=SUCCESS; status='Found document'; params=none
==== Read json:   {"uid":"76e07605-af46-4dec-bb99-182600ab2354","data":{"firstname":"Jack","organization":"CTU","title":"Agent","lastname":"Bauer","info":{"package":"com.forgerock.frdp.dao.mongo","filename":"TestMongoDataAccess.java","classname":"TestMongoDataAccess","language":"java"}},"timestamps":{"created":"2019-12-18T22:59:59.165-0600"}}
====
====
==== Replace output: error=false; state=NOTEXIST; status='Document does not exist: uid='NotExistUid''; params=none
==== Replace json:   {}
====
====
==== Replace output: error=false; state=SUCCESS; status='Replaced document'; params=none
==== Replace json:   {}
====
====
==== Read output: error=false; state=SUCCESS; status='Found document'; params=none
==== Read json:   {"uid":"76e07605-af46-4dec-bb99-182600ab2354","data":{"firstname":"Jack","organization":"CTU","comment":"Created from Test for MongoDataAccess class","title":"Agent","lastname":"Bauer","info":{"package":"com.forgerock.frdp.dao.mongo","filename":"TestMongoDataAccess.java","classname":"TestMongoDataAccess","language":"java"},"status":"Updated"},"timestamps":{"created":"2019-12-18T22:59:59.165-0600","updated":"2019-12-18T22:59:59.181-0600"}}
====
====
==== Delete output: error=false; state=SUCCESS; status='Deleted document'; params=none
==== Delete json:   {}
====
====
==== Search output: error=false; state=SUCCESS; status='Documents Found: 2'; params=none
==== Search json:   {"quantity":2,"results":[{"uid":"jdoe","data":{"firstname":"John","organization":"Acme","title":"Engineer","lastname":"Doe","info":{"package":"com.forgerock.frdp.dao.mongo","filename":"TestMongoDataAccess.java","classname":"TestMongoDataAccess","language":"java"}}},{"uid":"456983b2-f472-4f4a-9184-3244636c28d0","data":{"firstname":"John","organization":"Gov","title":"Leader","lastname":"Hancock","info":{"package":"com.forgerock.frdp.dao.mongo","filename":"TestMongoDataAccess.java","classname":"TestMongoDataAccess","language":"java"}}}]}
====
Dec 18, 2019 10:59:59 PM com.forgerock.frdp.dao.mongo.MongoDataAccess getResultsFromQuery
WARNING: Response uid is null: 5dfb034f980d5604337e4baa
====
==== Search output: error=false; state=SUCCESS; status='Documents Found: 4'; params=none
==== Search json:   {"quantity":4,"results":[{},{"uid":"jdoe","data":{"firstname":"John","organization":"Acme","title":"Engineer","lastname":"Doe","info":{"package":"com.forgerock.frdp.dao.mongo","filename":"TestMongoDataAccess.java","classname":"TestMongoDataAccess","language":"java"}}},{"uid":"456983b2-f472-4f4a-9184-3244636c28d0","data":{"firstname":"John","organization":"Gov","title":"Leader","lastname":"Hancock","info":{"package":"com.forgerock.frdp.dao.mongo","filename":"TestMongoDataAccess.java","classname":"TestMongoDataAccess","language":"java"}}},{"uid":"d1aa59a3-41e3-4b7d-99ca-96e999807add","data":{"firstname":"Jack","organization":"Trading","title":"Captain","lastname":"Sparro","info":{"package":"com.forgerock.frdp.dao.mongo","filename":"TestMongoDataAccess.java","classname":"TestMongoDataAccess","language":"java"}}}]}
====
Dec 18, 2019 10:59:59 PM com.mongodb.diagnostics.logging.JULLogger log
INFO: Closed connection [connectionId{localValue:2, serverValue:29}] to localhost:27017 because the pool has been closed.
```
