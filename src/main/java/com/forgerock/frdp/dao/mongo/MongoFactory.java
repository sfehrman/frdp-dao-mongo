/*
 * Copyright (c) 2018-2019, ForgeRock, Inc., All rights reserved
 * Use subject to license terms.
 */

package com.forgerock.frdp.dao.mongo;

import com.forgerock.frdp.dao.DataAccessIF;
import java.util.Map;

/**
 * Factory class for getting singleton instance of MongoDB Data Access Object
 * 
 * @author Scott Fehrman, ForgeRock, Inc.
 */
public class MongoFactory {

   private static DataAccessIF _instance = null;
   private static String _className = "com.forgerock.frdp.dao.mongo.MongoDataAccess";

   /**
    * Disable default constructor, this class can not be initiated
    */
   private MongoFactory() {
   }

   /**
    * Get instance of singleton MongoDB DataAccessIF object
    * 
    * @param params Map<String, String> of initialization parameters
    * @return DataAccessIF singleton instance
    * @throws Exception
    */
   public static synchronized DataAccessIF getInstance(final Map<String, String> params) throws Exception {
      ClassLoader loader = null;

      if (_instance == null) {
         loader = Thread.currentThread().getContextClassLoader();
         if (loader == null) {
            loader = MongoFactory.class.getClassLoader();
         }

         _instance = (MongoDataAccess) loader.loadClass(_className).newInstance();
         _instance.setParams(params);
      }
      return _instance;
   }

}
