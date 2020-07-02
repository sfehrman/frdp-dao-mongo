//
// Login as the default administrator using the default authentication database
// mongo --username "root" --password "password" --authenticationDatabase "admin" admin < mongodb.js
//
use test-server;
db.dropDatabase();
db.dropUser("testadmin");
db.createUser({user:"testadmin",pwd:"password",roles:["readWrite","dbAdmin"]});
db.createCollection("test");
db.test.createIndex({"uid":1});
db.test.insert({"comment": "This is a test document"});
//
// Login as the administrator for the application database
// mongo --username "testadmin" --password "password" --authenticationDatabase "test-server" test-server
//
// show the available databases
show dbs;
// show the collections in the current database
show collections;
// find all the documents in the collection
db.content.find();
db.content.find().pretty();
