/*
 * Copyright (c) 2018-2021, ForgeRock, Inc., All rights reserved
 * Use subject to license terms.
 */
package com.forgerock.frdp.dao.mongo;

import com.forgerock.frdp.common.ConstantsIF;
import com.forgerock.frdp.common.CoreIF;
import com.forgerock.frdp.dao.DataAccess;
import com.forgerock.frdp.dao.Operation;
import com.forgerock.frdp.dao.OperationIF;
import com.forgerock.frdp.utils.JSON;
import com.forgerock.frdp.utils.STR;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 *
 * Implementation of the DataAccess interface and provides an implementation
 * that supports MongoDB. Each instance of this supports a single "collection"
 * in a specific "database".
 *
 * An authenticated connection is required with an admin user that can read and
 * write to the collection in the database.
 *
 * params Map<String, String> needs to contain the following:
 *
 * <pre>
 * host idp.frdpcloud.com
 * port 27017
 * authen.user csadmin
 * authen.password password
 * database uma-cs
 * collection content
 * </pre>
 *
 * @author Scott Fehrman, ForgeRock, Inc.
 */
public class MongoDataAccess extends DataAccess {

   public static final String PARAM_HOST = "host";
   public static final String PARAM_PORT = "port";
   public static final String PARAM_AUTHEN_USER = "authen.user";
   public static final String PARAM_AUTHEN_PASSWORD = "authen.password";
   public static final String PARAM_AUTHEN_DATABASE = "authen.database";
   public static final String PARAM_DATABASE = "database";
   public static final String PARAM_COLLECTION = "collection";
   private static final String _ID = "_id";
   private static final String TZ_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
   private final String CLASS = this.getClass().getName();

   private DateFormat _dateFormat = null;

   private MongoClient _client = null;
   private MongoDatabase _database = null;
   private MongoCollection _collection = null;
   private JSONParser _parser = null;

   public MongoDataAccess() {
      super();

      String METHOD = "MongoDataAccess()";

      _logger.entering(CLASS, METHOD);

      _dateFormat = new SimpleDateFormat(TZ_FORMAT);

      _logger.exiting(CLASS, METHOD);

      return;
   }

   /**
    * Do not allow the copying an instance of this class
    * @return
    */
   @Override
   public CoreIF copy() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   /**
    * Implement close interface
    */
   @Override
   public void close() {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();

      _logger.entering(CLASS, METHOD);

      if (_client != null) {
         _client.close();
      }

      _logger.exiting(CLASS, METHOD);

      return;
   }

   /**
    * Execute the input operation, returns output operation
    *
    * @param operInput OperationIF input data
    * @return OperationIF output data
    */
   @Override
   public synchronized final OperationIF execute(final OperationIF operInput) {
      boolean error = false;
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      StringBuilder msg = new StringBuilder(CLASS + ":" + METHOD + ": ");
      OperationIF operOutput = null;

      _logger.entering(CLASS, METHOD);

      try {
         this.init();
         this.validate(operInput);
         // set the "database" and the "collection"
         _database = _client.getDatabase(operInput.getParamNotEmpty(PARAM_DATABASE));
         _collection = _database.getCollection(operInput.getParamNotEmpty(PARAM_COLLECTION));
      } catch (Exception ex) {
         error = true;
         msg.append(ex.getMessage());
         if (operInput == null) {
            operOutput = new Operation(OperationIF.TYPE.NULL);
         } else {
            operOutput = new Operation(operInput.getType());
         }
         operOutput.setError(true);
         operOutput.setState(STATE.FAILED);
         operOutput.setStatus(msg.toString());
      }

      if (!error) {
         switch (operInput.getType()) {
            case CREATE: {
               operOutput = this.create(operInput);
               break;
            }
            case READ: {
               operOutput = this.read(operInput);
               break;
            }
            case REPLACE: {
               operOutput = this.replace(operInput);
               break;
            }
            case DELETE: {
               operOutput = this.delete(operInput);
               break;
            }
            case SEARCH: {
               operOutput = this.search(operInput);
               break;
            }
            default: {
               error = true;
               msg.append("Unsupported operation '")
                  .append(operInput.getType().toString())
                  .append("'");
               operOutput = new Operation(operInput.getType());
               operOutput.setError(true);
               operOutput.setState(STATE.FAILED);
               operOutput.setStatus(msg.toString());
               break;
            }
         }
      }

      if (error) {
         _logger.log(Level.WARNING,
            operOutput == null ? "dataOutput is null" : operOutput.getStatus());
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /*
    * =============== PRIVATE METHODS ===============
    */
   /**
    * Create MongoDB document from input. Get JSON from the input
    *
    * <pre>
    * JSON input:
    * {
    *   "uid": "...", (OPTIONAL)
    *   "data": {
    *      "attr": "value",
    *      ...
    *   }
    * }
    * get "data" JSON from input
    * create a document from JSON
    * add primary key ("uid")
    * insert document into the collection
    * JSON output:
    * {
    *    "uid": "..."
    * }
    * </pre>
    *
    * @param operInput OperatinIF input data
    * @return OperationIF output data
    */
   private OperationIF create(final OperationIF operInput) {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String uid = null;
      OperationIF operOutput = null;
      JSONObject jsonInput = null;
      JSONObject jsonOutput = null;
      JSONObject jsonData = null;
      Document doc = null;
      Document tstamps = null;
      UUID uuid = null;

      _logger.entering(CLASS, METHOD);

      operOutput = new Operation(operInput.getType());
      jsonOutput = new JSONObject();

      jsonInput = operInput.getJSON();

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "input=''{0}'', json=''{1}''",
            new Object[]{
               operInput != null ? operInput.toString() : NULL,
               jsonInput != null ? jsonInput.toString() : NULL
            });
      }

      jsonData = JSON.getObject(jsonInput, ConstantsIF.DATA);

      /*
       * check for optional provided "uid" and make sure it does NOT exist
       */
      uid = JSON.getString(jsonInput, ConstantsIF.UID);

      if (!STR.isEmpty(uid)) {
         doc = this.getDocumentFromUid(uid);
         if (doc != null) {
            operOutput.setError(true);
            operOutput.setState(STATE.ERROR);
            operOutput.setStatus("Document already exists: uid='" + uid + "'");
         }
      } else {
         uuid = UUID.randomUUID();
         uid = uuid.toString();
      }

      if (!operOutput.isError()) {
         tstamps = new Document();
         tstamps.put(ConstantsIF.CREATED, _dateFormat.format(new Date()));

         doc = new Document();
         doc.put(ConstantsIF.DATA, Document.parse(jsonData.toString()));
         doc.put(_ID, new ObjectId());
         doc.put(ConstantsIF.UID, uid);
         doc.put(ConstantsIF.TIMESTAMPS, tstamps);

         try {
            _collection.insertOne(doc);
         } catch (Exception ex) {
            operOutput.setError(true);
            operOutput.setState(STATE.FAILED);
            operOutput.setStatus(CLASS + ":" + METHOD + ": " + ex.getMessage());
         }
      }

      if (operOutput.isError()) {
         _logger.log(Level.WARNING, operOutput.getStatus());
      } else {
         operOutput.setState(STATE.SUCCESS);
         operOutput.setStatus("Created document");
         jsonOutput.put(ConstantsIF.UID, uid);
      }

      if (_logger.isLoggable(Level.FINE)) {
         _logger.log(Level.INFO, operOutput.getStatus());
      }

      operOutput.setJSON(jsonOutput);

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "output=''{0}'', json=''{1}''",
            new Object[]{
               operOutput != null ? operOutput.toString() : NULL,
               jsonOutput != null ? jsonOutput.toString() : NULL
            });
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Read MongoDB document for specified identifier. Get JSON from input
    *
    * <pre>
    * get primary key from JSON
    * JSON input:
    * {
    *   "uid": "..."
    * }
    * create query using key
    * find document in the collection
    * result is an iterator (should only have one result)
    * get document from result
    * create JSON from document
    * add JSON to output
    * JSON output:
    * {
    *    "uid": "...",
    *    "data": {
    *      "attr1": "...",
    *      "attrX": "..."
    *    }
    * }
    * </pre>
    *
    * @param operInput OperationIF input data
    * @return OperationIF output data
    */
   private OperationIF read(final OperationIF operInput) {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String uid = null;
      OperationIF operOutput = null;
      JSONObject jsonInput = null;
      JSONObject jsonOutput = null;
      Document doc = null;

      _logger.entering(CLASS, METHOD);

      operOutput = new Operation(operInput.getType());

      jsonInput = operInput.getJSON();

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "input=''{0}'', json=''{1}''",
            new Object[]{
               operInput != null ? operInput.toString() : NULL,
               jsonInput != null ? jsonInput.toString() : NULL
            });
      }

      uid = JSON.getString(jsonInput, ConstantsIF.UID);

      doc = this.getDocumentFromUid(uid);

      if (doc != null) {
         jsonOutput = this.getJSONFromDocument(doc);
         operOutput.setState(STATE.SUCCESS);
         operOutput.setStatus("Found document");
      } else {
         jsonOutput = new JSONObject();
         operOutput.setError(true);
         operOutput.setState(STATE.NOTEXIST);
         operOutput.setStatus("Document does not exist: uid='" + uid + "'");
      }

      if (_logger.isLoggable(Level.FINE)) {
         _logger.log(Level.INFO, operOutput.getStatus());
      }

      operOutput.setJSON(jsonOutput);

      if (operOutput.isError()) {
         _logger.log(Level.WARNING, operOutput.getStatus());
      }

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "output=''{0}'', json=''{1}''",
            new Object[]{
               operOutput != null ? operOutput.toString() : NULL,
               jsonOutput != null ? jsonOutput.toString() : NULL
            });
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Replace MongoDB document for the specified identifier. Get JSON from input
    *
    * <pre>
    * JSON input:
    * {
    *   "uid": "..."
    *   "data": {
    *      "attr": "value",
    *      ...
    *   }
    * }
    * get primary key ("_id") from JSON
    * create document from JSON, change "_id" from String to ObjectId
    * create query using _id
    * replace document in the collection, can not use "updateOne"
    * JSON output:
    * {
    * }
    * </pre>
    *
    * @param operInput OperationIF input data
    * @return OperationIF output data
    */
   private OperationIF replace(OperationIF operInput) {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String uid = null;
      OperationIF operOutput = null;
      JSONObject jsonData = null;
      JSONObject jsonInput = null;
      JSONObject jsonOutput = null;
      Document doc = null;
      Document query = null;
      Document tstamps = null;

      _logger.entering(CLASS, METHOD);

      operOutput = new Operation(operInput.getType());
      jsonOutput = new JSONObject();

      jsonInput = operInput.getJSON();

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "input=''{0}'', json=''{1}''",
            new Object[]{
               operInput != null ? operInput.toString() : NULL,
               jsonInput != null ? jsonInput.toString() : NULL
            });
      }

      jsonData = JSON.getObject(jsonInput, ConstantsIF.DATA);

      uid = JSON.getString(jsonInput, ConstantsIF.UID);

      doc = this.getDocumentFromUid(uid);

      if (doc != null) // document exists
      {
         if (doc.containsKey(ConstantsIF.TIMESTAMPS)) {
            tstamps = doc.get(ConstantsIF.TIMESTAMPS, Document.class);
         } else {
            tstamps = new Document();
            tstamps.put(ConstantsIF.CREATED, _dateFormat.format(new Date()));

         }
         tstamps.put(ConstantsIF.UPDATED, _dateFormat.format(new Date()));

         doc.put(ConstantsIF.TIMESTAMPS, tstamps);
         doc.put(ConstantsIF.DATA, Document.parse(jsonData.toString()));
         query = new Document(ConstantsIF.UID, uid);

         try {
            _collection.replaceOne(query, doc);
         } catch (Exception ex) {
            operOutput.setError(true);
            operOutput.setState(STATE.FAILED);
            operOutput.setStatus(CLASS + ":" + METHOD + ": " + ex.getMessage());
         }

         if (!operOutput.isError()) {
            operOutput.setStatus("Replaced document");
            operOutput.setState(STATE.SUCCESS);
         }
      } else // document does NOT exist
      {
         operOutput.setState(STATE.NOTEXIST);
         operOutput.setStatus("Document does not exist: uid='" + uid + "'");
      }

      if (operOutput.isError()) {
         _logger.log(Level.WARNING, operOutput.getStatus());
      }

      if (_logger.isLoggable(Level.FINE)) {
         _logger.log(Level.INFO, operOutput.getStatus());
      }

      operOutput.setJSON(jsonOutput);

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "output=''{0}'', json=''{1}''",
            new Object[]{
               operOutput != null ? operOutput.toString() : NULL,
               jsonOutput != null ? jsonOutput.toString() : NULL
            });
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Delete MongoDB document for the specified identifier. Get JSON from input
    *
    * <pre>
    * JSON input:
    * {
    *   "uid": "..."
    * }
    * get primary key ("_id") from JSON
    * create query using _id
    * delete document in the collection
    * JSON output:
    * {
    * }
    * </pre>
    *
    * @param operInput OperationIF input data
    * @return OperationIF output data
    */
   private OperationIF delete(OperationIF operInput) {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String uid = null;
      OperationIF operOutput = null;
      JSONObject jsonInput = null;
      JSONObject jsonOutput = null;
      Document doc = null;
      Document query = null;

      _logger.entering(CLASS, METHOD);

      operOutput = new Operation(operInput.getType());
      jsonOutput = new JSONObject();

      jsonInput = operInput.getJSON();

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "input=''{0}'', json=''{1}''",
            new Object[]{
               operInput != null ? operInput.toString() : NULL,
               jsonInput != null ? jsonInput.toString() : NULL
            });
      }

      uid = JSON.getString(jsonInput, ConstantsIF.UID);

      doc = this.getDocumentFromUid(uid);

      if (doc != null) {
         query = new Document(ConstantsIF.UID, uid);
         try {
            _collection.deleteOne(query);
         } catch (Exception ex) {
            operOutput.setError(true);
            operOutput.setState(STATE.FAILED);
            operOutput.setStatus(CLASS + ":" + METHOD + ": " + ex.getMessage());
         }
      }

      if (operOutput.isError()) {
         _logger.log(Level.WARNING, operOutput.getStatus());
      } else {
         operOutput.setState(STATE.SUCCESS);
         operOutput.setStatus("Deleted document");
      }

      if (_logger.isLoggable(Level.FINE)) {
         _logger.log(Level.INFO, operOutput.getStatus());
      }

      operOutput.setJSON(jsonOutput);

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "output=''{0}'', json=''{1}''",
            new Object[]{
               operOutput != null ? operOutput.toString() : NULL,
               jsonOutput != null ? jsonOutput.toString() : NULL
            });
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Search MongoDB documents, matching search criteria. Get JSON from input
    *
    * <pre>
    * {
    *   "query": {
    *     "operator": "eq",
    *     "attribute": "firstname",
    *     "value": "John"
    *   }
    * }
    * -or-
    * {
    *   "query": {
    *     "operator": "and",
    *     "queries": [
    *       {
    *         "operator": "equal",
    *         "attribute": "data.owner",
    *         "value": "amadmin"
    *       },
    *       {
    *         "operator": "equal",
    *         "attribute": "data.category",
    *         "value": "sso_session"
    *       }
    *     ]
    *   }
    * }
    * </pre>
    *
    * build query find documents in collection that match query response is an
    * iterator process each result into JSON object and add to JSON array add
    * array to output
    *
    * <pre>
    * JSON output:
    * {
    *    "quantity": x,
    *    "results": [
    *       {
    *         "uid": "...",
    *         "data": {
    *           "attr": "value"
    *         }
    *       },
    *       { ... }
    *    ]
    * }
    * </pre>
    *
    * @param operInput OperationIF input data
    * @return OperationIF output data
    */
   private OperationIF search(OperationIF operInput) {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      OperationIF operOutput = null;
      JSONObject jsonInput = null;
      JSONObject jsonQuery = null;
      JSONObject jsonOutput = null;
      JSONArray jsonResults = null;
      Bson query = null;
      FindIterable find = null;

      _logger.entering(CLASS, METHOD);

      operOutput = new Operation(operInput.getType());
      jsonOutput = new JSONObject();

      jsonInput = operInput.getJSON();

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "input=''{0}'', json=''{1}''",
            new Object[]{
               operInput != null ? operInput.toString() : NULL,
               jsonInput != null ? jsonInput.toString() : NULL
            });
      }

      jsonQuery = JSON.getObject(jsonInput, ConstantsIF.QUERY);

      try {
         query = this.getQueryFromJSON(jsonQuery);
         find = _collection.find(query);
      } catch (Exception ex) {
         operOutput.setError(true);
         operOutput.setState(STATE.FAILED);
         operOutput.setStatus(CLASS + ":" + METHOD + ": " + ex.getMessage());
      }

      if (!operOutput.isError()) {
         jsonResults = this.getResultsFromQuery(find);
      } else {
         jsonResults = new JSONArray();
      }

      jsonOutput.put(ConstantsIF.RESULTS, jsonResults);
      jsonOutput.put(ConstantsIF.QUANTITY, jsonResults.size());

      operOutput.setJSON(jsonOutput);

      if (operOutput.isError()) {
         _logger.log(Level.WARNING, operOutput.getStatus());
      } else {
         operOutput.setState(STATE.SUCCESS);
         operOutput.setStatus("Documents Found: " + jsonResults.size());
      }

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL,
            "output=''{0}'', json=''{1}''",
            new Object[]{
               operOutput != null ? operOutput.toString() : NULL,
               jsonOutput != null ? jsonOutput.toString() : NULL
            });
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Initialize class instance.
    *
    * @throws Exception
    */
   private void init() throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      StringBuilder buf = new StringBuilder();
      StringBuilder msg = new StringBuilder(CLASS + ":" + METHOD + ": ");
      MongoClientURI uri = null;
      ServerAddress address = null;

      _logger.entering(CLASS, METHOD);

      if (this.getState() != STATE.READY) {

         // build mongo connection uri
         // MongoClientURI("mongodb://superAdmin:admin123@idp.frdpcloud.com:27017/?authSource=admin");
         buf.append("mongodb://")
            .append(this.getParamNotEmpty(PARAM_AUTHEN_USER))
            .append(":")
            .append(this.getParamNotEmpty(PARAM_AUTHEN_PASSWORD))
            .append("@")
            .append(this.getParamNotEmpty(PARAM_HOST))
            .append(":")
            .append(this.getParamNotEmpty(PARAM_PORT))
            .append("/?authSource=")
            .append(this.getParamNotEmpty(PARAM_AUTHEN_DATABASE));

         try {
            uri = new MongoClientURI(buf.toString());
            _client = new MongoClient(uri);
            address = _client.getAddress(); // test client connection
         } catch (Exception ex) {
            msg.append(ex.getMessage());
            // msg.append(", URI='").append(buf.toString()).append("'"); // SHOWS PASSWORD
            _logger.log(Level.SEVERE, msg.toString());
            this.setError(true);
            this.setState(STATE.FAILED);
            this.setStatus(msg.toString());
            throw new Exception(msg.toString());
         }

         _parser = new JSONParser();

         if (_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.INFO,
               "{0}: Mongo Client address: {1}",
               new Object[]{
                  METHOD,
                  address == null ? NULL : address.toString()
               });
         }

         this.setState(STATE.READY);
         this.setStatus("Initialization complete");
      }

      _logger.exiting(CLASS, METHOD);

      return;
   }

   /**
    * Get a MongoDB query object from a JSON object
    *
    * <pre>
    * JSON formats ...
    * {
    *   "operator": "all"
    * }
    * -or-
    * {
    *   "operator": "eq",
    *   "attribute": "firstname",
    *   "value": "John"
    * }
    * -or-
    * {
    *   "operator": "and",
    *   "queries": [
    *     {
    *       "operator": "equal",
    *       "attribute": "data.owner",
    *       "value": "amadmin"
    *     },
    *     {
    *       "operator": "equal",
    *       "attribute": "data.category",
    *       "value": "sso_session"
    *     }
    *   ]
    * }
    * </pre>
    *
    * @param json JSONObject query object
    * @return Bson query object
    * @throws Exception
    */
   private Bson getQueryFromJSON(final JSONObject json) throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String oper = null;
      String attr = null;
      String value = null;
      Bson query = null;
      JSONArray jsonQueries = null;
      List<Bson> filters = null;

      /*
       */
      _logger.entering(CLASS, METHOD);

      oper = JSON.getString(json, ConstantsIF.OPERATOR);

      if (STR.isEmpty(oper)) {
         throw new Exception("Query Operator is empty");
      }

      switch (oper) {
         case ConstantsIF.ALL: // if oper == "all" ... return all the documents
         {
            query = new Document();

            break;
         }
         case ConstantsIF.EQUAL: {
            attr = JSON.getString(json, ConstantsIF.ATTRIBUTE);

            if (STR.isEmpty(attr)) {
               throw new Exception("Query Attribute is empty");
            }

            value = JSON.getString(json, ConstantsIF.VALUE);

            if (STR.isEmpty(value)) {
               throw new Exception("Query Value is empty");
            }
            query = new Document(attr, value);

            break;
         }
         case ConstantsIF.AND: {
            jsonQueries = JSON.getArray(json, ConstantsIF.QUERIES);

            if (jsonQueries == null || jsonQueries.size() < 2) {
               throw new Exception("Operator 'AND' requires at least two queries");
            }

            filters = new LinkedList<>();

            for (Object o : jsonQueries) {
               if (o != null && o instanceof JSONObject) {
                  oper = JSON.getString((JSONObject) o, ConstantsIF.OPERATOR);
                  attr = JSON.getString((JSONObject) o, ConstantsIF.ATTRIBUTE);
                  value = JSON.getString((JSONObject) o, ConstantsIF.VALUE);

                  if (!STR.isEmpty(oper) && !STR.isEmpty(attr) && !STR.isEmpty(value)) {
                     if (oper.equalsIgnoreCase(ConstantsIF.EQUAL)) {
                        filters.add(Filters.eq(attr, value));
                     } else {
                        throw new Exception("Queries has an unsupported Operator '" + oper + "'");
                     }
                  }
               }
            }

            if (filters.isEmpty()) {
               throw new Exception("No Filters were created from the Queries");
            }

            query = Filters.and(filters);

            break;
         }
         default: {
            throw new Exception("Unknown Query Operator '" + oper + "'");
         }
      }

      _logger.exiting(CLASS, METHOD);

      return query;
   }

   /**
    * Get MongoDB document for the specified identifier
    *
    * @param uid String document identifier
    * @return Document
    */
   private Document getDocumentFromUid(final String uid) {
      boolean error = false;
      Object obj = null;
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      Document doc = null;
      Bson query = null;
      FindIterable find = null;
      MongoCursor cursor = null;

      _logger.entering(CLASS, METHOD);

      query = new Document(ConstantsIF.UID, uid);

      try {
         find = _collection.find(query);
      } catch (Exception ex) {
         error = true;
         _logger.log(Level.WARNING, ex.getMessage());
      }

      if (!error && find != null) {
         cursor = find.iterator();
         while (cursor.hasNext()) {
            obj = cursor.next();
            if (obj != null && obj instanceof Document) {
               doc = (Document) obj;
            } else {
               _logger.log(Level.WARNING, "Object is null or not a Document");
            }
         }
      }

      _logger.exiting(CLASS, METHOD);

      return doc;
   }

   /**
    * Create a JSON object from the Document, only include "data" and "uid"
    *
    * <pre>
    * {
    *    "uid": "...",
    *    "data": {
    *       ...
    *    },
    *    "timestamps": {
    *       ...
    *    }
    * }
    * </pre>
    *
    * @param docInput Document
    * @return JSONObject
    */
   private JSONObject getJSONFromDocument(final Document docInput) {
      boolean error = false;
      Object obj = null;
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String msg = null;
      String uid = null;
      JSONObject jsonData = null;
      JSONObject jsonTimestamps = null;
      JSONObject jsonOutput = null;
      Document docData = null;
      Document docTimestamps = null;

      _logger.entering(CLASS, METHOD);

      if (docInput.containsKey(ConstantsIF.DATA)) {
         obj = docInput.get(ConstantsIF.DATA);

         if (obj != null && obj instanceof Document) {
            docData = (Document) obj;

            try {
               jsonData = (JSONObject) _parser.parse(docData.toJson());
            } catch (Exception ex) {
               error = true;
               msg = "Can not parse 'data' Document into JSON";
            }
         } else {
            msg = "Data document is null";
         }
      } else {
         msg = "Data document is missing";
      }

      if (!error) {
         if (docInput.containsKey(ConstantsIF.UID)) {
            obj = docInput.get(ConstantsIF.UID);

            if (obj != null && obj instanceof String && !STR.isEmpty((String) obj)) {
               uid = (String) obj;
            } else {
               msg = "Document attribute '" + ConstantsIF.UID + "' is null or not a String";
            }
         } else {
            msg = "Document attribute '" + ConstantsIF.UID + "' is missing";
         }
      }

      if (!error) {
         if (docInput.containsKey(ConstantsIF.TIMESTAMPS)) {
            obj = docInput.get(ConstantsIF.TIMESTAMPS);

            if (obj != null && obj instanceof Document) {
               docTimestamps = (Document) obj;

               try {
                  jsonTimestamps = (JSONObject) _parser.parse(docTimestamps.toJson());
               } catch (Exception ex) {
                  error = true;
                  msg = "Can not parse 'timestamps' Document into JSON";
               }
            } else {
               msg = "Timestamp document is null";
            }
         } else {
            msg = "Timestamp document is missing";
         }
      }

      jsonOutput = new JSONObject();

      if (!error) {
         jsonOutput.put(ConstantsIF.DATA, jsonData);
         jsonOutput.put(ConstantsIF.UID, uid);
         jsonOutput.put(ConstantsIF.TIMESTAMPS, jsonTimestamps);
      } else {
         _logger.log(Level.WARNING, msg);
      }

      _logger.exiting(CLASS, METHOD);

      return jsonOutput;
   }

   /**
    * Get JSON Results object from MongoDB query results
    *
    * @param find FindIterable Document search results
    * @return JSONArray results data as a JSON array
    */
   private JSONArray getResultsFromQuery(final FindIterable find) {
      Object obj = null;
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String id = null;
      String msg = null;
      String uid = null;
      JSONObject jsonData = null;
      JSONObject jsonResult = null;
      JSONArray jsonResults = null;
      Document docResponse = null;
      Document docData = null;
      ObjectId oid = null;
      MongoCursor cursor = null;

      _logger.entering(CLASS, METHOD);

      jsonResults = new JSONArray();

      cursor = find.iterator();

      while (cursor.hasNext()) {
         msg = null;
         obj = cursor.next();
         if (obj != null && obj instanceof Document) {
            docResponse = (Document) obj;
            oid = docResponse.getObjectId(_ID);
            id = oid.toString();

            jsonResult = new JSONObject();

            if (docResponse.containsKey(ConstantsIF.DATA)) {
               obj = docResponse.get(ConstantsIF.DATA);

               if (obj != null && obj instanceof Document) {
                  docData = (Document) obj;

                  try {
                     jsonData = (JSONObject) _parser.parse(docData.toJson());
                  } catch (Exception ex) {
                     msg = "Can not parse Document into JSON: " + id;
                  }
               } else {
                  msg = "Document data is null: " + id;
               }
            } else {
               msg = "Response document is null: " + id;
            }

            if (docResponse.containsKey(ConstantsIF.UID)) {
               obj = docResponse.get(ConstantsIF.UID);

               if (obj != null && obj instanceof String) {
                  uid = (String) obj;
               } else {
                  msg = "Document uid is null: " + id;
               }
            } else {
               msg = "Response uid is null: " + id;
            }

            if (msg == null) {
               if (_logger.isLoggable(Level.FINE)) {
                  _logger.log(Level.INFO, ": Found: " + id);
               }

               jsonResult.put(ConstantsIF.DATA, jsonData);
               jsonResult.put(ConstantsIF.UID, uid);
            }
            jsonResults.add(jsonResult);
         } else {
            msg = "Object is null or is not a Document";
         }

         if (msg != null) {
            _logger.log(Level.WARNING, msg);
         }
      }

      _logger.exiting(CLASS, METHOD);

      return jsonResults;
   }
}
