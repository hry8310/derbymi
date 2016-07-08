/*

   Derby - Class org.apache.derby.impl.sql.catalog.DataDictionaryImpl

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.impl.sql.catalog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ParameterMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;

import org.apache.dearbaby.impl.sql.compile.ColumnReference;
import org.apache.dearbaby.impl.sql.compile.QueryTreeNode;
import org.apache.dearbaby.impl.sql.compile.TableName;
import org.apache.dearbaby.sj.DearSelector;
import org.apache.derby.catalog.AliasInfo;
import org.apache.derby.catalog.DefaultInfo;
import org.apache.derby.catalog.DependableFinder;
import org.apache.derby.catalog.TypeDescriptor;
import org.apache.derby.catalog.UUID;
import org.apache.derby.catalog.types.BaseTypeIdImpl;
import org.apache.derby.catalog.types.RoutineAliasInfo;
import org.apache.derby.ext.DearContext;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.Attribute;
import org.apache.derby.iapi.reference.EngineType;
import org.apache.derby.iapi.reference.Limits;
import org.apache.derby.iapi.reference.Property;
import org.apache.derby.iapi.reference.SQLState; 
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.context.ContextService; 
import org.apache.derby.iapi.services.io.FormatableBitSet;
 
 
import org.apache.derby.iapi.services.uuid.UUIDFactory;
import org.apache.derby.iapi.sql.compile.Visitable;
import org.apache.derby.iapi.sql.conn.Authorizer;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.conn.LanguageConnectionFactory; 
import org.apache.derby.iapi.sql.dictionary.AliasDescriptor;
import org.apache.derby.iapi.sql.dictionary.BulkInsertCounter;
import org.apache.derby.iapi.sql.dictionary.CatalogRowFactory;
import org.apache.derby.iapi.sql.dictionary.CheckConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.ColPermsDescriptor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptorList;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptorList;
import org.apache.derby.iapi.sql.dictionary.ConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.ConstraintDescriptorList;
import org.apache.derby.iapi.sql.dictionary.DataDescriptorGenerator;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.DefaultDescriptor;
import org.apache.derby.iapi.sql.dictionary.DependencyDescriptor;
import org.apache.derby.iapi.sql.dictionary.FileInfoDescriptor;
import org.apache.derby.iapi.sql.dictionary.ForeignKeyConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.IndexRowGenerator;
import org.apache.derby.iapi.sql.dictionary.KeyConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.PasswordHasher;
import org.apache.derby.iapi.sql.dictionary.PermDescriptor;
import org.apache.derby.iapi.sql.dictionary.PermissionsDescriptor;
import org.apache.derby.iapi.sql.dictionary.ReferencedKeyConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.RoleClosureIterator;
import org.apache.derby.iapi.sql.dictionary.RoleGrantDescriptor;
import org.apache.derby.iapi.sql.dictionary.RoutinePermsDescriptor;
import org.apache.derby.iapi.sql.dictionary.SPSDescriptor;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.SequenceDescriptor;
import org.apache.derby.iapi.sql.dictionary.StatisticsDescriptor;
import org.apache.derby.iapi.sql.dictionary.SubCheckConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.SubConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.SubKeyConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.SystemColumn;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.dictionary.TablePermsDescriptor;
import org.apache.derby.iapi.sql.dictionary.TriggerDescriptor;
import org.apache.derby.iapi.sql.dictionary.TriggerDescriptorList;
import org.apache.derby.iapi.sql.dictionary.TupleDescriptor;
import org.apache.derby.iapi.sql.dictionary.UserDescriptor;
import org.apache.derby.iapi.sql.dictionary.ViewDescriptor;
  
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.DataValueFactory;
import org.apache.derby.iapi.types.NumberDataValue;
import org.apache.derby.iapi.types.Orderable;
import org.apache.derby.iapi.types.RowLocation;
import org.apache.derby.iapi.types.SQLBoolean;
import org.apache.derby.iapi.types.SQLChar;
import org.apache.derby.iapi.types.SQLLongint;
import org.apache.derby.iapi.types.SQLVarchar;
import org.apache.derby.iapi.types.StringDataValue;
import org.apache.derby.iapi.types.TypeId;
import org.apache.derby.iapi.types.UserType;
import org.apache.derby.iapi.util.IdUtil;  
import org.apache.derby.impl.services.uuid.BasicUUID; 
 
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * Standard database implementation of the data dictionary that stores the
 * information in the system catalogs.
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
public final class DataDictionaryImpl implements DataDictionary 
		  {

	private static final String CFG_SYSTABLES_ID = "SystablesIdentifier";
	private static final String CFG_SYSTABLES_INDEX1_ID = "SystablesIndex1Identifier";
	private static final String CFG_SYSTABLES_INDEX2_ID = "SystablesIndex2Identifier";
	private static final String CFG_SYSCOLUMNS_ID = "SyscolumnsIdentifier";
	private static final String CFG_SYSCOLUMNS_INDEX1_ID = "SyscolumnsIndex1Identifier";
	private static final String CFG_SYSCOLUMNS_INDEX2_ID = "SyscolumnsIndex2Identifier";
	private static final String CFG_SYSCONGLOMERATES_ID = "SysconglomeratesIdentifier";
	private static final String CFG_SYSCONGLOMERATES_INDEX1_ID = "SysconglomeratesIndex1Identifier";
	private static final String CFG_SYSCONGLOMERATES_INDEX2_ID = "SysconglomeratesIndex2Identifier";
	private static final String CFG_SYSCONGLOMERATES_INDEX3_ID = "SysconglomeratesIndex3Identifier";
	private static final String CFG_SYSSCHEMAS_ID = "SysschemasIdentifier";
	private static final String CFG_SYSSCHEMAS_INDEX1_ID = "SysschemasIndex1Identifier";
	private static final String CFG_SYSSCHEMAS_INDEX2_ID = "SysschemasIndex2Identifier";

	private static final int SYSCONGLOMERATES_CORE_NUM = 0;
	private static final int SYSTABLES_CORE_NUM = 1;
	private static final int SYSCOLUMNS_CORE_NUM = 2;
	private static final int SYSSCHEMAS_CORE_NUM = 3;
	private static final int NUM_CORE = 4;

	/**
	 * SYSFUN functions. Table of functions that automatically appear in the
	 * SYSFUN schema. These functions are resolved to directly if no schema name
	 * is given, e.g.
	 * 
	 * <code>
	 * SELECT COS(angle) FROM ROOM_WALLS
	 * </code>
	 * 
	 * Adding a function here is suitable when the function defintion can have a
	 * single return type and fixed parameter types.
	 * 
	 * Functions that need to have a return type based upon the input type(s)
	 * are not supported here. Typically those are added into the parser and
	 * methods added into the DataValueDescriptor interface. Examples are
	 * character based functions whose return type length is based upon the
	 * passed in type, e.g. passed a CHAR(10) returns a CHAR(10).
	 * 
	 * 
	 * This simple table can handle an arbitrary number of arguments and RETURNS
	 * NULL ON NULL INPUT. The scheme could be expanded to handle other function
	 * options such as other parameters if needed. [0] = FUNCTION name [1] =
	 * RETURNS type [2] = Java class [3] = method name and signature [4] =
	 * "true" or "false" depending on whether the function is DETERMINSTIC [5] =
	 * "true" or "false" depending on whether the function has VARARGS [6..N] =
	 * arguments (optional, if not present zero arguments is assumed)
	 *
	 */
	private static final String[][] SYSFUN_FUNCTIONS = {
			{ "ACOS", "DOUBLE", "java.lang.StrictMath", "acos(double)", "true",
					"false", "DOUBLE" },
			{ "ASIN", "DOUBLE", "java.lang.StrictMath", "asin(double)", "true",
					"false", "DOUBLE" },
			{ "ATAN", "DOUBLE", "java.lang.StrictMath", "atan(double)", "true",
					"false", "DOUBLE" },
			{ "ATAN2", "DOUBLE", "java.lang.StrictMath",
					"atan2(double,double)", "true", "false", "DOUBLE", "DOUBLE" },
			{ "COS", "DOUBLE", "java.lang.StrictMath", "cos(double)", "true",
					"false", "DOUBLE" },
			{ "SIN", "DOUBLE", "java.lang.StrictMath", "sin(double)", "true",
					"false", "DOUBLE" },
			{ "TAN", "DOUBLE", "java.lang.StrictMath", "tan(double)", "true",
					"false", "DOUBLE" },
			{ "PI", "DOUBLE", "org.apache.derby.catalog.SystemProcedures",
					"PI()", "true", "false" },
			{ "DEGREES", "DOUBLE", "java.lang.StrictMath", "toDegrees(double)",
					"true", "false", "DOUBLE" },
			{ "RADIANS", "DOUBLE", "java.lang.StrictMath", "toRadians(double)",
					"true", "false", "DOUBLE" },
			{ "LN", "DOUBLE", "java.lang.StrictMath", "log(double)", "true",
					"false", "DOUBLE" },
			{ "LOG", "DOUBLE", "java.lang.StrictMath", "log(double)", "true",
					"false", "DOUBLE" }, // Same as LN
			{ "LOG10", "DOUBLE", "java.lang.StrictMath", "log10(double)",
					"true", "false", "DOUBLE" },
			{ "EXP", "DOUBLE", "java.lang.StrictMath", "exp(double)", "true",
					"false", "DOUBLE" },
			{ "CEIL", "DOUBLE", "java.lang.StrictMath", "ceil(double)", "true",
					"false", "DOUBLE" },
			{ "CEILING", "DOUBLE", "java.lang.StrictMath", "ceil(double)",
					"true", "false", "DOUBLE" }, // Same as CEIL
			{ "FLOOR", "DOUBLE", "java.lang.StrictMath", "floor(double)",
					"true", "false", "DOUBLE" },
			{ "SIGN", "INTEGER", "org.apache.derby.catalog.SystemProcedures",
					"SIGN(double)", "true", "false", "DOUBLE" },
			{ "RANDOM", "DOUBLE", "java.lang.StrictMath", "random()", "false",
					"false" },
			{ "RAND", "DOUBLE", "org.apache.derby.catalog.SystemProcedures",
					"RAND(int)", "false", "false", "INTEGER" }, // Escape
																// function
																// spec.
			{ "COT", "DOUBLE", "org.apache.derby.catalog.SystemProcedures",
					"COT(double)", "true", "false", "DOUBLE" },
			{ "COSH", "DOUBLE", "java.lang.StrictMath", "cosh(double)", "true",
					"false", "DOUBLE" },
			{ "SINH", "DOUBLE", "java.lang.StrictMath", "sinh(double)", "true",
					"false", "DOUBLE" },
			{ "TANH", "DOUBLE", "java.lang.StrictMath", "tanh(double)", "true",
					"false", "DOUBLE" } };

	/**
	 * Index into SYSFUN_FUNCTIONS of the DETERMINISTIC indicator. Used to
	 * determine whether the system function is DETERMINISTIC
	 */
	private static final int SYSFUN_DETERMINISTIC_INDEX = 4;

	/**
	 * Index into SYSFUN_FUNCTIONS of the VARARGS indicator. Used to determine
	 * whether the system function has VARARGS
	 */
	private static final int SYSFUN_VARARGS_INDEX = 5;

	/**
	 * The index of the first parameter in entries in the SYSFUN_FUNCTIONS
	 * table. Used to determine the parameter count (zero to many).
	 */
	private static final int SYSFUN_FIRST_PARAMETER_INDEX = 6;

	/**
	 * Runtime definition of the functions from SYSFUN_FUNCTIONS. Populated
	 * dynamically as functions are called.
	 */
	private final AliasDescriptor[] sysfunDescriptors = new AliasDescriptor[SYSFUN_FUNCTIONS.length];
 

	/*
	 * * SchemaDescriptors for system and app schemas. Both* are canonical. We
	 * cache them for fast lookup.
	 */
	private SchemaDescriptor systemSchemaDesc;
	private SchemaDescriptor sysIBMSchemaDesc;
	private SchemaDescriptor declaredGlobalTemporaryTablesSchemaDesc;
	private SchemaDescriptor systemUtilSchemaDesc;

	// This array of non-core table names *MUST* be in the same order
	// as the non-core table numbers in DataDictionary.
	private static final String[] nonCoreNames = { "SYSCONSTRAINTS", "SYSKEYS",
			"SYSDEPENDS", "SYSALIASES", "SYSVIEWS", "SYSCHECKS",
			"SYSFOREIGNKEYS", "SYSSTATEMENTS", "SYSFILES", "SYSTRIGGERS",
			"SYSSTATISTICS", "SYSDUMMY1", "SYSTABLEPERMS", "SYSCOLPERMS",
			"SYSROUTINEPERMS", "SYSROLES", "SYSSEQUENCES", "SYSPERMS",
			"SYSUSERS" };

	private static final int NUM_NONCORE = nonCoreNames.length;

	/**
	 * List of all "system" schemas
	 * <p>
	 * This list should contain all schema's used by the system and are created
	 * when the database is created. Users should not be able to create or drop
	 * these schema's and should not be able to create or drop objects in these
	 * schema's. This list is used by code that needs to check if a particular
	 * schema is a "system" schema.
	 **/
	private static final String[] systemSchemaNames = {
			SchemaDescriptor.IBM_SYSTEM_CAT_SCHEMA_NAME,
			SchemaDescriptor.IBM_SYSTEM_FUN_SCHEMA_NAME,
			SchemaDescriptor.IBM_SYSTEM_PROC_SCHEMA_NAME,
			SchemaDescriptor.IBM_SYSTEM_STAT_SCHEMA_NAME,
			SchemaDescriptor.IBM_SYSTEM_NULLID_SCHEMA_NAME,
			SchemaDescriptor.STD_SYSTEM_DIAG_SCHEMA_NAME,
			SchemaDescriptor.STD_SYSTEM_UTIL_SCHEMA_NAME,
			SchemaDescriptor.IBM_SYSTEM_SCHEMA_NAME,
			SchemaDescriptor.STD_SQLJ_SCHEMA_NAME,
			SchemaDescriptor.STD_SYSTEM_SCHEMA_NAME };
 

	private String authorizationDatabaseOwner;
	private boolean usesSqlAuthorization;

	/*
	 * * This property and value are written into the database properties* when
	 * the database is created, and are used to determine whether* the system
	 * catalogs need to be upgraded.
	 */
 

	// no other system tables have id's in the configuration.

	public DataDescriptorGenerator dataDescriptorGenerator;
	private DataValueFactory dvf;
 
 
	protected UUIDFactory uuidFactory;
	/** Daemon creating and refreshing index cardinality statistics. */
 

	Properties startupParameters;
	int engineType;

	/* Information about whether or not we are at boot time */
	protected boolean booting;
 

	 
	private Hashtable<UUID, SPSDescriptor> spsIdHash;
	// private Hashtable spsTextHash;
	int tdCacheSize;
	int stmtCacheSize;
	private int seqgenCacheSize;
 
	int permissionsCacheSize;

	/*
	 * * Lockable object for synchronizing transition from caching to
	 * non-caching
	 */
 

	volatile int cacheMode = DataDictionary.COMPILE_ONLY_MODE;

	/* Number of DDL users */
	volatile int ddlUsers;
	/* Number of readers that start in DDL_MODE */
	volatile int readersInDDLMode;

	private HashMap<String, HashMap<String, String>> sequenceIDs;

	/**
	 * True if the database is read only and requires some form of upgrade, that
	 * makes the stored prepared statements invalid. With this case the engine
	 * is running at a different version to the underlying stored database. This
	 * can happen in 5.1 if the database is read only and a different point
	 * release (later than 5.1.25?) to the one that created it, has been booted.
	 * (Beetle 5170).
	 * 
	 * <P>
	 * In 5.2 and newer this will be the case if the engine booting the database
	 * is newer than the engine that created it.
	 */
	private boolean readOnlyUpgrade;

	/**
	 * Tells if the automatic index statistics refresher has been disabled.
	 * <p>
	 * The refresher can be disabled explicitly by the user by setting a
	 * property (system wide or database property), or if the daemon encounters
	 * an exception it doesn't know how to recover from.
	 */
	private boolean indexStatsUpdateDisabled;
	private boolean indexStatsUpdateLogging;
	private String indexStatsUpdateTracing;

	// systemSQLNameNumber is the number used as the last digit during the
	// previous call to getSystemSQLName.
	// If it is 9 for a given calendarForLastSystemSQLName, we will restart the
	// counter to 0
	// and increment the calendarForLastSystemSQLName by 10ms.
	private int systemSQLNameNumber;
	private GregorianCalendar calendarForLastSystemSQLName = new GregorianCalendar();
	private long timeForLastSystemSQLName;

	/**
	 * List of procedures in SYSCS_UTIL schema with PUBLIC access
	 */
	private static final String[] sysUtilProceduresWithPublicAccess = {
			"SYSCS_SET_RUNTIMESTATISTICS", "SYSCS_SET_STATISTICS_TIMING",
			"SYSCS_INPLACE_COMPRESS_TABLE", "SYSCS_COMPRESS_TABLE",
			"SYSCS_UPDATE_STATISTICS", "SYSCS_MODIFY_PASSWORD",
			"SYSCS_DROP_STATISTICS", };

	/**
	 * List of functions in SYSCS_UTIL schema with PUBLIC access
	 */
	private static final String[] sysUtilFunctionsWithPublicAccess = {
			"SYSCS_GET_RUNTIMESTATISTICS", "SYSCS_PEEK_AT_SEQUENCE",
			"SYSCS_PEEK_AT_IDENTITY", };

	/**
	 * Collation Type for SYSTEM schemas. In Derby 10.3, this will always be
	 * UCS_BASIC
	 */
	private int collationTypeOfSystemSchemas;

	/**
	 * Collation Type for user schemas. In Derby 10.3, this is either UCS_BASIC
	 * or TERRITORY_BASED. The exact value is decided by what has user asked for
	 * through JDBC url optional attribute COLLATION. If that atrribute is set
	 * to UCS_BASIC, the collation type for user schemas will be UCS_BASIC. If
	 * that attribute is set to TERRITORY_BASED, the collation type for user
	 * schemas will be TERRITORY_BASED. If the user has not provide COLLATION
	 * attribute value in the JDBC url at database create time, then collation
	 * type of user schemas will default to UCS_BASIC. Pre-10.3 databases after
	 * upgrade to Derby 10.3 will also use UCS_BASIC for collation type of user
	 * schemas.
	 */
	private int collationTypeOfUserSchemas;

	/*
	 * * Constructor
	 */

	public DataDictionaryImpl() {

	}

	/**
	 * This is the data dictionary implementation for the standard database
	 * engine.
	 * 
	 * @return true if this service requested is for a database engine.
	 */

	public boolean canSupport(Properties startParams) {
		return false;
	}

	/**
	 * Start-up method for this instance of the data dictionary.
	 *
	 * @param startParams
	 *            The start-up parameters
	 *
	 * @exception StandardException
	 *                Thrown if the module fails to start
	 */
	public void boot(boolean create, Properties startParams)
			throws StandardException { 
		
	}
	
	
	
	
	///dddff

	/**
	 * Clear the DataDictionary caches, including the sequence caches if requested..
	 *
	 * @exception StandardException Standard Derby error policy
	 */
	public void clearCaches( boolean clearSequenceCaches ) throws StandardException{}

	/**
	 * Clear all of the DataDictionary caches.
	 *
	 * @exception StandardException Standard Derby error policy
	 */
	public void clearCaches() throws StandardException{}

	/**
	 * Clear all of the sequence number generators.
	 *
	 * @exception StandardException Standard Derby error policy
	 */
	public void clearSequenceCaches() throws StandardException{}

	/**
	 * Inform this DataDictionary that we are about to start reading it.  This
	 * means using the various get methods in the DataDictionary.
	 * Generally, this is done during query compilation.
	 *
	 * @param lcc	The LanguageConnectionContext to use.
	 *
	 * @return	The mode that the reader will use, to be passed to doneReading()
	 *			Either COMPILE_ONLY_MODE or DDL_MODE.
	 *
	 * @exception StandardException		Thrown on error
	 */
	public int startReading(LanguageConnectionContext lcc) throws StandardException{
    	return 0;
    }

	/**
	 * Inform this DataDictionary that we have finished reading it.  This
	 * typically happens at the end of compilation.
	 *
	 * @param mode	The mode that was returned by startReading().
	 * @param lcc	The LanguageConnectionContext to use.
	 *
	 * @exception StandardException		Thrown on error
	 */
	public void doneReading(int mode,
			LanguageConnectionContext lcc) 
		throws StandardException{}

	/**
	 * Inform this DataDictionary that we are about to start writing to it.
	 * This means using the various add and drop methods in the DataDictionary.
	 * Generally, this is done during execution of DDL.
	 *
	 * @param lcc	The LanguageConnectionContext to use.
	 *
	 * @exception StandardException		Thrown on error
	 */
	public void startWriting(LanguageConnectionContext lcc) 
		throws StandardException{
        	return  ;
        }

	/**
	 * Inform this DataDictionary that the transaction in which writes have
	 * been done (or may have been done) has been committed or rolled back.
	 *
	 * @exception StandardException		Thrown on error
	 */
	public void transactionFinished() throws StandardException{
    	return  ;
    }

 

	/**
	 * Get the DataValueFactory associated with this database.
	 *
	 * @return	The ExecutionFactory
	 */
	public DataValueFactory	getDataValueFactory(){
    	return null;
    }

	/**
	 * Get a DataDescriptorGenerator, through which we can create
	 * objects to be stored in the DataDictionary.
	 *
	 * @return	A DataDescriptorGenerator
	 *
	 */
	public DataDescriptorGenerator	getDataDescriptorGenerator(){
    	return null;
    }

	/**
	 * Get authorizationID of Database Owner
	 *
	 * @return	authorizationID
	 */
	public String getAuthorizationDatabaseOwner(){
    	return null;
    }

	/**
	 * Get authorization model in force, SqlStandard or legacy mode
	 *
	 * @return	Whether sqlAuthorization is being used
	 */
	public boolean usesSqlAuthorization(){
    	return false;
    }
	
	/**
	 * Return the collation type for SYSTEM schemas. In Derby 10.3, this will 
	 * always be UCS_BASIC 
	 * 
	 * @return the collation type for SYSTEM schemas
	 */
	public int getCollationTypeOfSystemSchemas(){
    	return 0;
    }

	/**
	 * Return the collation type for user schemas. In Derby 10.3, this is either 
	 * UCS_BASIC or TERRITORY_BASED. The exact value is decided by what has 
	 * user asked for through JDBC url optional attribute COLLATION. If that
	 * atrribute is set to UCS_BASIC, the collation type for user schemas
	 * will be UCS_BASIC. If that attribute is set to TERRITORY_BASED, the 
	 * collation type for user schemas will be TERRITORY_BASED. If the user
	 * has not provided COLLATION attribute value in the JDBC url at database
	 * create time, then collation type of user schemas will default to 
	 * UCS_BASIC. Pre-10.3 databases after upgrade to Derby 10.3 will also
	 * use UCS_BASIC for collation type of user schemas. 
	 * 
	 * @return the collation type for user schemas
	 */
	public int getCollationTypeOfUserSchemas(){
    	return 0;
    }
	
	/**
	 * Get the descriptor for the named schema.
	   Schema descriptors include authorization ids and schema ids.
	 * SQL92 allows a schema to specify a default character set - we will
	 * not support this.  Will check default schema for a match
	 * before scanning a system table.
	 * 
	 * @param schemaName	The name of the schema we're interested in. Must not be null.
	 * @param tc			TransactionController
	 *
	 * @param raiseError    whether an exception should be thrown if the schema does not exist.
	 *
	 * @return	The descriptor for the schema. Can be null (not found) if raiseError is false.
	 *
	 * @exception StandardException		Thrown on error
	 */

	public SchemaDescriptor	getSchemaDescriptor(String schemaName, 
												boolean raiseError)
						throws StandardException{
		return null;
	}

	  
	/**
	 * Get the default password hasher for this database level. Returns null
     * if the system is at rev level 10.5 or earlier.
	 *
	 * @param props   The persistent properties used to configure password hashing.
	 */
    public  PasswordHasher  makePasswordHasher( Dictionary<?,?> props )
        throws StandardException{
        	return null;
        }
    
	/**
	 * Get the descriptor for the system schema. Schema descriptors include 
     * authorization ids and schema ids.
     *
	 * SQL92 allows a schema to specify a default character set - we will
	 * not support this.
	 *
	 * @return	The descriptor for the schema.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public SchemaDescriptor	getSystemSchemaDescriptor( )
						throws StandardException{
					    	return null;
					    }

	/**
	 * Get the descriptor for the SYSIBM schema. Schema descriptors include 
     * authorization ids and schema ids.
     *
	 * SQL92 allows a schema to specify a default character set - we will
	 * not support this.
	 *
	 * @return	The descriptor for the schema.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public SchemaDescriptor	getSysIBMSchemaDescriptor( )
						throws StandardException{
					    	return null;
					    }

	/**
	 * Get the descriptor for the declared global temporary table schema which is always named "SESSION".
	 *
	 * SQL92 allows a schema to specify a default character set - we will
	 * not support this.
	 *
	 * @return	The descriptor for the schema.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public SchemaDescriptor	getDeclaredGlobalTemporaryTablesSchemaDescriptor()
        throws StandardException{
        	return null;
        }

    /**
     * Determine whether a string is the name of the system schema.
     *
     * @param name
     * @return	true or false
	 *
	 * @exception StandardException		Thrown on failure
     */
    public boolean isSystemSchemaName( String name)
        throws StandardException{
        	return false;
        }
    

	        
	/**
	 * Get the descriptor for the table with the given UUID.
	 *
	 * NOTE: I'm assuming that the object store will define an UUID for
	 * persistent objects. I'm also assuming that UUIDs are unique across
	 * schemas, and that the object store will be able to do efficient
	 * lookups across schemas (i.e. that no schema descriptor parameter
	 * is needed).
	 *
	 * @param tableID	The UUID of the table to get the descriptor for
	 *
	 * @return	The descriptor for the table, null if the table does
	 *		not exist.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public TableDescriptor		getTableDescriptor(UUID tableID)
						throws StandardException{
					    	return null;
					    }
  
	/**
	 * Drop all table descriptors for a schema.
	 *
	 * @param schema	A descriptor for the schema to drop the tables
	 *			from.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	/*
	public void dropAllTableDescriptors(SchemaDescriptor schema)
						throws StandardException;
	*/

	/**
	 * Get a ColumnDescriptor given its Default ID.
	 *
	 * @param uuid	The UUID of the default
	 *
	 * @return The ColumnDescriptor for the column.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ColumnDescriptor getColumnDescriptorByDefaultId(UUID uuid)
				throws StandardException{
			    	return null;
			    }

	  

	/**
	 * Gets the viewDescriptor for the view with the given UUID.
	 *
	 * @param uuid	The UUID for the view
	 *
	 * @return  A descriptor for the view
	 *
	 * @exception StandardException		Thrown on error
	 */
	public ViewDescriptor	getViewDescriptor(UUID uuid)
		throws StandardException{
	    	return null;
	    }

	/**
	 * Gets the viewDescriptor for the view given its TableDescriptor.
	 *
	 * @param td	The TableDescriptor for the view.
	 *
	 * @return	A descriptor for the view
	 *
	 * @exception StandardException		Thrown on error
	 */
	public ViewDescriptor	getViewDescriptor(TableDescriptor td)
 						throws StandardException{
 					    	return null;
 					    }

 

	/**
	 * Get a ConstraintDescriptor given its UUID.
	 *
	 * @param uuid	The UUID
	 *
	 * @return The ConstraintDescriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConstraintDescriptor getConstraintDescriptor(UUID uuid)
				throws StandardException{
			    	return null;
			    }

	/**
	 * Get a ConstraintDescriptor given its name and schema ID.
	 *
	 * @param constraintName	Constraint name.
	 * @param schemaID			The schema UUID
	 *
	 * @return The ConstraintDescriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConstraintDescriptor getConstraintDescriptor
	(
		String	constraintName,
		UUID	schemaID
    )
		throws StandardException{
	    	return null;
	    }

	/**
	 * Load up the constraint descriptor list for this table
     * descriptor (or all) and return it.  If the descriptor list
     * is already loaded up, it is returned without further
	 * ado.
	 *
     * @param td The table descriptor.
     * @return   The ConstraintDescriptorList for the table. If null, return
     *           a list of all the constraint descriptors in all schemas.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConstraintDescriptorList getConstraintDescriptors(TableDescriptor td)
		throws StandardException{
	    	return null;
	    }

	/**
	 * Convert a constraint descriptor list into a list
	 * of active constraints, that is, constraints which
	 * must be enforced. For the Core product, these
	 * are just the constraints on the original list.
	 * However, during REFRESH we may have deferred some
	 * constraints until statement end. This method returns
	 * the corresponding list of constraints which AREN'T
	 * deferred.
	 *
	 * @param cdl	The constraint descriptor list to wrap with
	 *				an Active constraint descriptor list.
	 *
	 * @return The corresponding Active ConstraintDescriptorList
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConstraintDescriptorList getActiveConstraintDescriptors(ConstraintDescriptorList cdl)
		throws StandardException{
	    	return null;
	    }

	/**
	 * Reports whether an individual constraint must be
	 * enforced. For the Core product, this routine always
	 * returns true.
	 *
	 * However, during REFRESH we may have deferred some
	 * constraints until statement end. This method returns
	 * false if the constraint deferred
	 *
	 * @param constraint	the constraint to check
	 *
	 *
	 * @return The corresponding Active ConstraintDescriptorList
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public boolean activeConstraint( ConstraintDescriptor constraint )
		throws StandardException{
	    	return false;
	    }

	/** 
	 * Get the constraint descriptor given a table and the UUID String
	 * of the backing index.
	 *
	 * @param td			The table descriptor.
	 * @param uuid			The UUID  for the backing index.
	 *
	 * @return The ConstraintDescriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConstraintDescriptor getConstraintDescriptor(TableDescriptor td, 
														UUID uuid)
				throws StandardException{
			    	return null;
			    }


	/**
	 * Get the constraint descriptor given a table and the UUID String
	 * of the constraint
	 *
	 * @param td			The table descriptor.
	 * @param uuid			The UUID for the constraint
	 *
	 * @return The ConstraintDescriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConstraintDescriptor getConstraintDescriptorById
	(
		TableDescriptor	td,
		UUID			uuid
    )
		throws StandardException{
	    	return null;
	    }

	/** 
	 * Get the constraint descriptor given a TableDescriptor and the constraint name.
	 *
	 * @param td				The table descriptor.
	 * @param sd				The schema descriptor for the constraint
	 * @param constraintName	The constraint name.
	 * @param forUpdate			Whether or not access is for update
	 *
	 * @return The ConstraintDescriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConstraintDescriptor getConstraintDescriptorByName(TableDescriptor td, 
															  SchemaDescriptor sd,
															  String constraintName,
															  boolean forUpdate)
				throws StandardException{
			    	return null;
			    }

	/**
	 * Return a table descriptor corresponding to the TABLEID
	 * field in SYSCONSTRAINTS where CONSTRAINTID matches
	 * the constraintId passed in.
	 *
	 * @param constraintId	The id of the constraint
	 *
	 * @return	the corresponding table descriptor
	 *
	 * @exception StandardException		Thrown on error
	 */
	public TableDescriptor getConstraintTableDescriptor(UUID constraintId)
			throws StandardException{
		    	return null;
		    }

	/**
	 * Return a list of foreign keys constraints referencing
	 * this constraint.  Returns both enabled and disabled
	 * constraints.  
	 *
	 * @param constraintId	The id of the referenced constraint
	 *
	 * @return	list of constraints
	 *
	 * @exception StandardException		Thrown on error
	 */
	public ConstraintDescriptorList getForeignKeys(UUID constraintId)
			throws StandardException{
		    	return null;
		    }

	  

	/**
	 * Get a SubKeyConstraintDescriptor from syskeys or sysforeignkeys for
	 * the specified constraint id.  For primary foreign and and unique
	 * key constraints.
	 *
	 * @param constraintId	The UUID for the constraint.
	 * @param type	The type of the constraint 
	 *		(e.g. DataDictionary.FOREIGNKEY_CONSTRAINT)
	 *
	 * @return SubKeyConstraintDescriptor	The Sub descriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public SubKeyConstraintDescriptor getSubKeyConstraint(UUID constraintId, int type)
		throws StandardException{
	    	return null;
	    }

	/**
	 * Get a SPSDescriptor given its UUID.
	 *
	 * @param uuid	The UUID
	 *
	 *
	 * @return The SPSDescriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public SPSDescriptor getSPSDescriptor(UUID uuid)
				throws StandardException{
			    	return null;
			    }

	/** 
	 * Get the stored prepared statement descriptor given 
	 * a sps name.
	 *
	 * @param name	The sps name.
	 * @param sd	The schema descriptor.
	 *
	 * @return The SPSDescriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public SPSDescriptor getSPSDescriptor(String name, SchemaDescriptor sd)
				throws StandardException{
			    	return null;
			    }

	/**
	 * Get every statement in this database.
	 * Return the SPSDescriptors in an list.
	 *
	 * @return the list of descriptors
	 *
	 * @exception StandardException		Thrown on failure
	 */
    public List<SPSDescriptor> getAllSPSDescriptors() throws StandardException{
    	return null;
    }

	/**
	 * Get all the parameter descriptors for an SPS.
	 * Look up the params in SYSCOLUMNS and turn them
	 * into parameter descriptors.  
	 *
	 * @param spsd	sps descriptor
	 * @param defaults the parameter defaults.  If not null,
	 *					all the parameter defaults will be stuffed
	 *					in here.
	 *
	 * @return array of data type descriptors
	 *
	 * @exception StandardException		Thrown on error
	 */
    public DataTypeDescriptor[] getSPSParams(SPSDescriptor spsd, List<DataValueDescriptor> defaults)
            throws StandardException{
            	return null;
            }

	 
   
	/**
	 * Invalidate all the stored plans in SYS.SYSSTATEMENTS for
	 *  the given language connection context.
	 * @exception StandardException		Thrown on error
	 */
	public void invalidateAllSPSPlans(LanguageConnectionContext lcc) throws StandardException{
    	return  ;
    }

	/**
	 * Invalidate all the stored plans in SYS.SYSSTATEMENTS. 
	 * @exception StandardException		Thrown on error
	 */
	public void invalidateAllSPSPlans() throws StandardException{
    	return  ;
    }
						
	/**
	 * Get a TriggerDescriptor given its UUID.
	 *
	 * @param uuid	The UUID
	 *
	 *
	 * @return The TriggerDescriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public TriggerDescriptor getTriggerDescriptor(UUID uuid)
				throws StandardException{
			    	return null;
			    }

	/** 
	 * Get the stored prepared statement descriptor given 
	 * a sps name.
	 *
	 * @param name	The sps name.
	 * @param sd	The schema descriptor.
	 *
	 * @return The TriggerDescriptor for the constraint.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public TriggerDescriptor getTriggerDescriptor(String name, SchemaDescriptor sd)
				throws StandardException{
			    	return null;
			    }

	/**
	 * This method does the job of transforming the trigger action plan text
	 * as shown below. 
	 * 	DELETE FROM t WHERE c = old.c
	 * turns into
	 *  DELETE FROM t WHERE c = org.apache.derby.iapi.db.Factory::
	 *  	getTriggerExecutionContext().getOldRow().
	 *      getInt(columnNumberFor'C'inRuntimeResultset);
	 * In addition to that, for CREATE TRIGGER time, it does the job of 
	 * collecting the column positions of columns referenced in trigger 
	 * action plan through REFERENCEs clause. This information will get
	 * saved in SYSTRIGGERS table by the caller in CREATE TRIGGER case.
	 *  
	 * It gets called either 
	 * 1)at the trigger creation time for row level triggers or
	 * 2)if the trigger got invalidated by some other sql earlier and the 
	 * current sql needs that trigger to fire. For such a trigger firing 
	 * case, this method will get called only if it is row level trigger 
	 * with REFERENCES clause. This work was done as part of DERBY-4874. 
	 * Before DERBY-4874, once the stored prepared statement for trigger 
	 * action plan was generated, it was never updated ever again. But, 
	 * one case where the trigger action plan needs to be regenerated is say
	 * when the column length is changed for a column which is REFERENCEd as
	 * old or new column value. eg of such a case would be say the Alter
	 * table has changed the length of a varchar column from varchar(30) to
	 * varchar(64) but the stored prepared statement associated with the 
	 * trigger action plan continued to use varchar(30). To fix varchar(30) 
	 * in stored prepared statement for trigger action sql to varchar(64), 
	 * we need to regenerate the trigger action sql. This new trigger 
	 * action sql will then get updated into SYSSTATEMENTS table.
	 * 
	 * If we are here for case 1) above, then we will collect all column 
	 * references in trigger action through new/old transition variables. 
	 * Information about them will be saved in SYSTRIGGERS table DERBY-1482
	 * (if we are dealing with pre-10.7 db, then we will not put any 
	 * information about trigger action columns in the system table to ensure 
	 * backward compatibility). This information along with the trigger 
	 * columns will decide what columns from the trigger table will be
	 * fetched into memory during trigger execution.
	 * 
	 * If we are here for case 2) above, then all the information about 
	 * column references in trigger action has already been collected during
	 * CREATE TRIGGER time and hence we can use that available information 
	 * about column positions to do the transformation of OLD/NEW transient 
	 * references.
	 * 
	 * More information on case 1) above. 
	 * DERBY-1482 One of the work done by this method for row level triggers
	 * is to find the columns which are referenced in the trigger action 
	 * through the REFERENCES clause ie thro old/new transition variables.
	 * This information will be saved in SYSTRIGGERS so it can be retrieved
	 * during the trigger execution time. The purpose of this is to recognize
	 * what columns from the trigger table should be read in during trigger
	 * execution. Before these code changes, during trigger execution, Derby
	 * was opting to read all the columns from the trigger table even if they
	 * were not all referenced during the trigger execution. This caused Derby
	 * to run into OOM at times when it could really be avoided.
	 *
	 * We go through the trigger action text and collect the column positions
	 * of all the REFERENCEd columns through new/old transition variables. We
	 * keep that information in SYSTRIGGERS. At runtime, when the trigger is
	 * fired, we will look at this information along with trigger columns from
	 * the trigger definition and only fetch those columns into memory rather
	 * than all the columns from the trigger table.
	 * This is especially useful when the table has LOB columns and those
	 * columns are not referenced in the trigger action and are not recognized
	 * as trigger columns. For such cases, we can avoid reading large values of
	 * LOB columns into memory and thus avoiding possibly running into OOM
	 * errors.
	 * 
	 * If there are no trigger columns defined on the trigger, we will read all
	 * the columns from the trigger table when the trigger fires because no
	 * specific columns were identified as trigger column by the user. The
	 * other case where we will opt to read all the columns are when trigger
	 * columns and REFERENCING clause is identified for the trigger but there
	 * is no trigger action column information in SYSTRIGGERS. This can happen
	 * for triggers created prior to 10.7 release and later that database got
	 * hard/soft-upgraded to 10.7 or higher release.
	 *
	 * @param actionStmt This is needed to get access to the various nodes
	 * 	generated by the Parser for the trigger action sql. These nodes will be
	 * 	used to find REFERENCEs column nodes.
	 * 
	 * @param oldReferencingName The name specified by the user for REFERENCEs
	 * 	to old row columns
	 * 
	 * @param newReferencingName The name specified by the user for REFERENCEs
	 * 	to new row columns
	 * 
	 * @param triggerDefinition The original trigger action text provided by
	 * 	the user during CREATE TRIGGER time.
	 * 
	 * @param referencedCols Trigger is defined on these columns (will be null
	 *   in case of INSERT AND DELETE Triggers. Can also be null for DELETE
	 *   Triggers if UPDATE trigger is not defined on specific column(s))
	 *   
	 * @param referencedColsInTriggerAction	what columns does the trigger 
	 * 	action reference through old/new transition variables (may be null)
	 * 
	 * @param actionOffset offset of start of action clause
	 * 
	 * @param triggerTableDescriptor Table descriptor for trigger table
	 * 
	 * @param triggerEventMask TriggerDescriptor.TRIGGER_EVENT_XXX
	 * 
	 * @param createTriggerTime True if here for CREATE TRIGGER,
	 * 	false if here because an invalidated row level trigger with 
	 *  REFERENCEd columns has been fired and hence trigger action
	 *  sql associated with SPSDescriptor may be invalid too.
     *
     * @param replacements a list that will be populated with objects that
     *  describe how {@code triggerDefinition} has been transformed into
     *  the returned SQL text. Each element in the list will contain four
     *  integers. The first two describe the begin and end offset of the
     *  replaced text in the {@code triggerDefinition}. The last two describe
     *  the begin and end offset of the replacement text in the returned
     *  string. The begin offsets are inclusive, whereas the end offsets are
     *  exclusive. The list can be {@code null} if the caller does not care
     *  about this information.
	 * 
	 * @return Transformed trigger action sql
	 * @throws StandardException
	 */
	public String getTriggerActionString(
			Visitable actionStmt,
			String oldReferencingName,
			String newReferencingName,
			String triggerDefinition,
			int[] referencedCols,
			int[] referencedColsInTriggerAction,
			int actionOffset,
			TableDescriptor triggerTableDescriptor,
			int triggerEventMask,
            boolean createTriggerTime,
            List<int[]> replacements)
	throws StandardException{
    	return null;
    }
	

	/**
	 * Load up the trigger descriptor list for this table
	 * descriptor and return it.  If the descriptor list
     * is already loaded up, it is returned without further
     * ado. The descriptors are returned in the order in
     * which the triggers were created, with the oldest first.
	 *
	 * @param td			The table descriptor.
	 *
	 * @return The ConstraintDescriptorList for the table
	 *
	 * @exception StandardException		Thrown on failure
	 */
    public TriggerDescriptorList getTriggerDescriptors(TableDescriptor td)
		throws StandardException{
	    	return null;
	    }

	 
   

	/**
	 * Get a ConglomerateDescriptor given its UUID.  If it is an index
	 * conglomerate shared by at least another duplicate index, this returns
	 * one of the ConglomerateDescriptors for those indexes. 
	 *
	 * @param uuid	The UUID
	 *
	 *
	 * @return A ConglomerateDescriptor for the conglomerate.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConglomerateDescriptor getConglomerateDescriptor(UUID uuid)
				throws StandardException{
			    	return null;
			    }

	/**
	 * Get an array of ConglomerateDescriptors given the UUID.  If it is a
	 * heap conglomerate or an index conglomerate not shared by a duplicate
	 * index, the size of the return array is 1. If the uuid argument is null, then
     * this method retrieves descriptors for all of the conglomerates in the database.
	 *
	 * @param uuid	The UUID
	 *
	 *
	 * @return An array of ConglomerateDescriptors for the conglomerate.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConglomerateDescriptor[] getConglomerateDescriptors(UUID uuid)
				throws StandardException{
			    	return null;
			    }

	/**
	 * Get a ConglomerateDescriptor given its conglomerate number.  If it is an
	 * index conglomerate shared by at least another duplicate index, this
	 * returns one of the ConglomerateDescriptors for those indexes. 
	 *
	 * @param conglomerateNumber	The conglomerate number.
	 *
	 *
	 * @return A ConglomerateDescriptor for the conglomerate.  Returns NULL if
	 *				no such conglomerate.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConglomerateDescriptor	getConglomerateDescriptor(
						long conglomerateNumber)
						throws StandardException{
					    	return null;
					    }

	/**
	 * Get an array of conglomerate descriptors for the given conglomerate
	 * number.  If it is a heap conglomerate or an index conglomerate not
	 * shared by a duplicate index, the size of the return array is 1.
	 *
	 * @param conglomerateNumber	The number for the conglomerate
	 *				we're interested in
	 *
	 * @return	An array of ConglomerateDescriptors that share the requested
	 *		conglomerate. Returns size 0 array if no such conglomerate.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConglomerateDescriptor[]	getConglomerateDescriptors(
						long conglomerateNumber)
						throws StandardException{
					    	return null;
					    }

	/**
	 * Gets a conglomerate descriptor for the named index in the given schema,
	 * getting an exclusive row lock on the matching row in 
	 * sys.sysconglomerates (for DDL concurrency) if requested.
	 *
	 * @param indexName	The name of the index we're looking for
	 * @param sd		The schema descriptor
	 * @param forUpdate	Whether or not to get an exclusive row 
	 *					lock on the row in sys.sysconglomerates.
	 *
	 * @return	A ConglomerateDescriptor describing the requested
	 *		conglomerate. Returns NULL if no such conglomerate.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConglomerateDescriptor	getConglomerateDescriptor(
						String indexName,
						SchemaDescriptor sd,
						boolean forUpdate)
						throws StandardException{
					    	return null;
					    }

	  

	/**
	 * Gets a list of the dependency descriptors for the given dependent's id.
	 *
	 * @param dependentID		The ID of the dependent we're interested in
	 *
	 * @return	List			Returns a list of DependencyDescriptors. 
	 *							Returns an empty list if no stored dependencies for the
	 *							dependent's ID.
	 *
	 * @exception StandardException		Thrown on failure
	 */
   public List<DependencyDescriptor> getDependentsDescriptorList(String dependentID)
		throws StandardException{
	    	return null;
	    }

	/**
	 * Gets a list of the dependency descriptors for the given provider's id.
	 *
	 * @param providerID		The ID of the provider we're interested in
	 *
	 * @return	List			Returns a list of DependencyDescriptors. 
	 *							Returns an empty List if no stored dependencies for the
	 *							provider's ID.
	 *
	 * @exception StandardException		Thrown on failure
	 */
   public List<DependencyDescriptor> getProvidersDescriptorList(String providerID)
		throws StandardException{
	    	return null;
	    }

	/**
	 * Build and return an List with DependencyDescriptors for
	 * all of the stored dependencies.  
	 * This is useful for consistency checking.
	 *
	 * @return List		List of all DependencyDescriptors.
	 *
	 * @exception StandardException		Thrown on failure
	 */
    public List<TupleDescriptor> getAllDependencyDescriptorsList()
				throws StandardException{
			    	return null;
			    }

	/**
	 * Get the UUID Factory.  (No need to make the UUIDFactory a module.)
	 *
	 * @return UUIDFactory	The UUID Factory for this DataDictionary.
	 */
	public UUIDFactory getUUIDFactory(){
    	return null ;
    }
   
	/**
	 * Get an AliasDescriptor given its UUID.
	 *
	 * @param uuid	The UUID
	 *
	 *
	 * @return The AliasDescriptor for method alias.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public AliasDescriptor getAliasDescriptor(UUID uuid)
				throws StandardException{
			    	return null;
			    }

	/**
	 * Get a AliasDescriptor by alias name and name space.
	 * NOTE: caller responsible for handling no match.
	 *
	   @param schemaID		schema identifier
	 * @param aliasName		The alias name.
	 * @param nameSpace		The alias name space.
	 *
	 * @return AliasDescriptor	AliasDescriptor for the alias name and name space
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public AliasDescriptor getAliasDescriptor(String schemaID, String aliasName, char nameSpace)
			throws StandardException{
		    	return null;
		    }

	/**
		Get the list of routines matching the schema and routine name.
	 */
    public List<AliasDescriptor> getRoutineList(
        String schemaID,
        String routineName,
        char nameSpace) throws StandardException{
    	return null;
    }
	
	 
	/**
	 * Return the credentials descriptor for the named user.
	 *
	 * @param userName      Name of the user whose credentials we want.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public UserDescriptor getUser( String userName )
		throws StandardException{
	    	return null;
	    }

	 
    
	public	int	getEngineType(){
    	return 0;
    }

	/**
	 * Get a FileInfoDescriptor given its id.
	 *
	 * @param id The descriptor's id.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public FileInfoDescriptor getFileInfoDescriptor(UUID id)
				throws StandardException{
			    	return null;
			    }

	/**
	 * Get a FileInfoDescriptor given its SQL name and
	 * schema name.  
	 *
	 * @param sd        the schema that holds the FileInfoDescriptor.
	 * @param name		SQL name of file.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public FileInfoDescriptor getFileInfoDescriptor(SchemaDescriptor sd, String name)
				throws StandardException{
			    	return null;
			    }

	/**
	 * Drop a FileDescriptor from the datadictionary.
	 *
	 * @exception StandardException Oops
	 */
	public void dropFileInfoDescriptor(FileInfoDescriptor fid)
				throws StandardException{
			    	return  ;
			    }

 

        /* Returns a row location template for a table */
	public RowLocation getRowLocationTemplate( LanguageConnectionContext lcc, TableDescriptor td)
		throws StandardException{
	    	return null;
	    }

	    
    
	/**
	 * Get the next number from an ANSI/ISO sequence generator
     * which was created with the CREATE SEQUENCE statement. May
     * raise an exception if the sequence was defined as NO CYCLE and
     * the range of the sequence is exhausted. May allocate a range of
     * sequence numbers and update the CURRENTVALUE column of the
     * corresponding row in SYSSEQUENCES. This work is done in the
     * execution transaction of the current session.
	 * 
	 * @param sequenceUUIDstring String value of the UUID which identifies the sequence
	 * @param returnValue This is a data value to be stuffed with the next sequence number.
     *
     * @throws StandardException if the sequence does not cycle and its range is exhausted
	 */
    public void getCurrentValueAndAdvance
        ( String sequenceUUIDstring, NumberDataValue returnValue )
        throws StandardException{
    	
    }

    /**
     * <p>
     * Peek at the next value which will be returned by an identity generator.
     * </p>
     */
    public Long peekAtIdentity( String schemaName, String tableName )
        throws StandardException{
        	return null;
        }

    /**
     * <p>
     * Peek at the next value which will be returned by a sequence generator.
     * </p>
     */
    public Long peekAtSequence( String schemaName, String sequenceName )
        throws StandardException{
        	return null;
        }

	/**
	 * Gets all statistics Descriptors for a given table.
	 */
	public List<StatisticsDescriptor> getStatisticsDescriptors(TableDescriptor td)
		throws StandardException{
	    	return null;
	    }

 


	/**
	 * Returns the cache mode of the data dictionary.
	 */
	public int getCacheMode(){
    	return 0;
    }

	/**
	  *	Returns a unique system generated name of the form SQLyymmddhhmmssxxn
	  *	  yy - year, mm - month, dd - day of month, hh - hour, mm - minute, ss - second,
	  *	  xx - the first 2 digits of millisec because we don't have enough space to keep the exact millisec value,
	  *	  n - number between 0-9
	  *
	  *	@return	system generated unique name
	  */
	public String getSystemSQLName(){
    	return null;
    }

	  
	/**
		Check to see if a database has been upgraded to the required
		level in order to use a langauge feature that is.
		<P>
		This is used to ensure new functionality that would lead on disk
		information not understood by a previous release is not executed
		while in soft upgrade mode. Ideally this is called at compile time
		and the parser has a utility method to enable easy use at parse time.
		<P>
		To use this method, a feature implemented in a certain release (DataDictionary version)
		would call it with the constant matching the release. E.g. for a new feature added
		in 10.1, a call such as 
		<PRE>
		// check and throw an exception if the database is not at 10.1
		dd.checkVersion(DataDictionary.DD_VERSION_DERBY_10_1, "NEW FEATURE NAME");
		
		</PRE>
		This call would occur during the compile time, usually indirectly through
		the parser utility method, but direct calls can be made during QueryNode initialization,
		or even at bind time.
		<BR>
		It is not expected that this method would be called at execution time.

		@param majorVersion Data Dictionary major version (DataDictionary.DD_ constant)
		@param feature Non-null to throw an error, null to return the state of the version match.

		@return True if the database has been upgraded to the required level, false otherwise.
	*/
	public boolean checkVersion(int majorVersion, String feature) throws StandardException{
    	return false;
    }

    /**
     * Check if the database is read only and requires some form of upgrade
     * that makes the stored prepared statements invalid.
     *
     * @return {@code true} if the stored prepared statements are invalid
     * because of an upgrade and the database is read only, {@code false}
     * otherwise
     */
    public boolean isReadOnlyUpgrade(){
    	return false;
    }
	 

    /**
     * Get one user's privileges on a table using tableUUID and authorizationid
     *
     * @param tableUUID
     * @param authorizationId The user name
     *
     * @return a TablePermsDescriptor or null if the user has no permissions on the table.
     *
     * @exception StandardException
     */
    public TablePermsDescriptor getTablePermissions( UUID tableUUID, String authorizationId)
        throws StandardException{
        	return null;
        }

    /**
     * Get one user's privileges on a table using tablePermsUUID
     *
     * @param tablePermsUUID
     *
     * @return a TablePermsDescriptor
     *
     * @exception StandardException
     */
    public TablePermsDescriptor getTablePermissions( UUID tablePermsUUID)
    throws StandardException{
    	return null;
    }

    /**
     * Get one user's column privileges for a table.
     *
     * @param tableUUID
     * @param privType Authorizer.SELECT_PRIV, Authorizer.UPDATE_PRIV, or Authorizer.REFERENCES_PRIV
     * @param forGrant
     * @param authorizationId The user name
     *
     * @return a ColPermsDescriptor or null if the user has no separate column
     *         permissions of the specified type on the table. Note that the user may have been granted
     *         permission on all the columns of the table (no column list), in which case this routine
     *         will return null. You must also call getTablePermissions to see if the user has permission
     *         on a set of columns.
     *
     * @exception StandardException
     */
    public ColPermsDescriptor getColumnPermissions( UUID tableUUID,
                                                    int privType,
                                                    boolean forGrant,
                                                    String authorizationId)
        throws StandardException{
        	return null;
        }


    /**
     * Get one user's column privileges for a table. This routine gets called 
     * during revoke privilege processing
     *
     * @param tableUUID
     * @param privTypeStr (as String) Authorizer.SELECT_PRIV, Authorizer.UPDATE_PRIV, or Authorizer.REFERENCES_PRIV
     * @param forGrant
     * @param authorizationId The user name
     *
     * @return a ColPermsDescriptor or null if the user has no separate column
     *         permissions of the specified type on the table. Note that the user may have been granted
     *         permission on all the columns of the table (no column list), in which case this routine
     *         will return null. You must also call getTablePermissions to see if the user has permission
     *         on a set of columns.
     *
     * @exception StandardException
     */
    public ColPermsDescriptor getColumnPermissions( UUID tableUUID,
            String privTypeStr,
            boolean forGrant,
            String authorizationId)
    throws StandardException{
    	return null;
    }
    /**
     * Get one user's column privileges on a table using colPermsUUID
     *
     * @param colPermsUUID
     *
     * @return a ColPermsDescriptor
     *
     * @exception StandardException
     */
    public ColPermsDescriptor getColumnPermissions( UUID colPermsUUID)
    throws StandardException{
    	return null;
    }

    /**
     * Get one user's permissions for a routine (function or procedure).
     *
     * @param routineUUID
     * @param authorizationId The user's name
     *
     * @return The descriptor of the users permissions for the routine.
     *
     * @exception StandardException
     */
    public RoutinePermsDescriptor getRoutinePermissions( UUID routineUUID, String authorizationId)
        throws StandardException{
        	return null;
        }
    
    /**
     * Get one user's privileges for a routine using routinePermsUUID
     *
     * @param routinePermsUUID
     *
     * @return a RoutinePermsDescriptor
     *
     * @exception StandardException
     */
    public RoutinePermsDescriptor getRoutinePermissions( UUID routinePermsUUID)
    throws StandardException{
    	return null;
    }

	/**
	 * Return the Java class to use for the VTI to which the received
	 * table descriptor maps.
	 *
	 * There are two kinds of VTI mappings that we do: the first is for
	 * "table names", the second is for "table function names".  Table
	 * names can only be mapped to VTIs that do not accept any arguments;
	 * any VTI that has at least one constructor which accepts one or more
	 * arguments must be mapped from a table *function* name.
	 *
	 * An example of a VTI "table name" is the following:
	 *
	 *   select * from SYSCS_DIAG.LOCK_TABLE
	 *
	 * In this case "SYSCS_DIAG.LOCK_TABLE" is the table name that we want
	 * to map.  Since the corresonding VTI does not accept any arguments,
	 * this VTI table name can be used anywhere a normal base table name
	 * can be used.
	 *
	 * An example of a VTI "table function name" is the following:
	 *
	 *   select * from TABLE(SYSCS_DIAG.SPACE_TABLE(?)) x
	 *
	 * In this case "SYSCS_DIAG.SPACE_TABLE" is the table function name that
	 * we want to map.  Since the corresponding VTI can take either one or
	 * two arguments we have to use the TABLE constructor syntax to pass the
	 * argument(s) in as if we were making a function call.  Hence the term
	 * "table function".
	 *
	 * @param td Table descriptor used for the VTI look-up.
	 * @param asTableFunction If false then treat td's descriptor name as a
	 *  VTI "table name"; if true, treat the descriptor name as a VTI "table
	 *  function name".
	 * @return Java class name to which "td" maps, or null if no mapping
	 *  is found.
	 */
	public String getVTIClass(TableDescriptor td, boolean asTableFunction)
		throws StandardException{
	    	return null;
	    }

	/**
	 * Return the Java class to use for a builtin VTI to which the received
	 * table descriptor maps.
	 *
	 *
	 * @param td Table descriptor used for the VTI look-up.
	 * @param asTableFunction If false then treat td's descriptor name as a
	 *  VTI "table name"; if true, treat the descriptor name as a VTI "table
	 *  function name".
	 * @return Java class name of builtin VTI to which "td" maps, or null if no mapping
	 *  is found.
	 */
	public String getBuiltinVTIClass(TableDescriptor td, boolean asTableFunction)
		throws StandardException{
	    	return null;
	    }


	/**
	 * Get a role grant descriptor for a role definition.
	 *
	 * @param roleName The name of the role whose definition we seek
	 *
	 * @throws StandardException error
	 */
	public RoleGrantDescriptor getRoleDefinitionDescriptor(String roleName)
			throws StandardException{
		    	return null;
		    }

	/**
	 * Get the role grant descriptor corresponding to the uuid provided
	 *
	 * @param uuid
	 *
	 * @return The descriptor for the role grant descriptor
	 *
	 * @exception StandardException  Thrown on error
	 */
	public RoleGrantDescriptor getRoleGrantDescriptor(UUID uuid)
			throws StandardException{
		    	return null;
		    }

	/**
	 * Get a descriptor for a role grant
	 *
	 * @param roleName The name of the role whose definition we seek
	 * @param grantee  The grantee
	 * @param grantor  The grantor
	 *
	 * @throws StandardException error
	 */
	public RoleGrantDescriptor getRoleGrantDescriptor(String roleName,
													  String grantee,
													  String grantor)
		throws StandardException{
	    	return null;
	    }

 
    /**
     * get a descriptor for a Sequence by uuid
     * @param uuid uuid of the sequence
     * @return the SequenceDescriptor
     * @throws StandardException error
     */
    public SequenceDescriptor getSequenceDescriptor(UUID uuid) throws StandardException{
    	return null;
    }

    /**
     * get a descriptor for a Sequence by sequence name
     * @param sequenceName Name of the sequence
     * @param sd The scemadescriptor teh sequence belongs to
     * @return The SequenceDescriptor
     * @throws StandardException error
     */
    public SequenceDescriptor getSequenceDescriptor(SchemaDescriptor sd, String sequenceName)
            throws StandardException{
            	return null;
            }

    /**
     * Get permissions granted to one user for an object using the object's Id
     * and the user's authorization Id.
     *
     * @param objectUUID ID of the object being protected
     * @param objectType Type of the object (e.g., PermDescriptor.SEQUENCE_TYPE)
     * @param privilege The kind of privilege needed (e.g., PermDescriptor.USAGE_PRIV)
     * @param granteeAuthId The user who needs the permission
     *
     * @return The descriptor of the permissions for the object
     *
     * @exception StandardException
     */
    public PermDescriptor getGenericPermissions(UUID objectUUID, String objectType, String privilege, String granteeAuthId)
        throws StandardException{
        	return null;
        }

    /**
     * Get one user's privileges for an object using the permUUID
     *
     * @param permUUID
     *
     * @return a PermDescriptor
     *
     * @exception StandardException
     */
    public PermDescriptor getGenericPermissions(UUID permUUID)
    throws StandardException{
    	return null;
    }

     

    /**
     * Tells if an index statistics refresher should be created for this
     * database.
     * <p>
     * The only reason not to create an index statistics refresher is if one
     * already exists.
     *
     * @return {@code true} if an index statistics refresher should be created,
     *      {@code false} if one already exists.
     */
    public boolean doCreateIndexStatsRefresher(){
    	return false;
    }
 

    /**
     * Returns the index statistics refresher.
     *
     * @param asDaemon whether the usage is automatic ({@code true}) or
     *      explicit ({@code false})
     * @return The index statistics refresher instance, or {@code null} if
     *      disabled. If {@code asDaemon} is {@code false}, an instance will
     *      always be returned.
     */
    

    /**
     * Disables automatic refresh/creation of index statistics at runtime.
     * <p>
     * If the daemon is disabled, it can only be enabled again by rebooting
     * the database. Note that this method concerns diabling the daemon at
     * runtime, and only the automatic updates of statistics. If wanted, the
     * user would disable the daemon at boot-time by setting a property
     * (system-wide or database property).
     * <p>
     * <em>Usage note:</em> This method was added to allow the index refresher
     * itself to notify the data dictionary that it should be disabled. This
     * only happens if the refresher/daemon experiences severe errors, or a
     * large amount of errors. It would then disable itself to avoid eating up
     * system resources and potentially cause side-effects due to the errors.
     */
    public void disableIndexStatsRefresher(){
    	return  ;
    }

    /**
     * Get a {@code DependableFinder} instance.
     *
     * @param formatId the format id
     * @return an instance capable of finding {@code Dependable}s with the
     * specified format id
     */
    public DependableFinder getDependableFinder(int formatId){
    	return null;
    }

    /**
     * Get a {@code DependableFinder} instance for referenced columns in
     * a table.
     *
     * @param formatId the format id
     * @param columnBitMap byte array encoding the bitmap of referenced columns
     * @return an instance capable of finding {@code Dependable}s with the
     * specified format id
     */
    public DependableFinder getColumnDependableFinder(
            int formatId, byte[] columnBitMap){
    	return null;
    }

    /**
     * Get the identity generator used to support the bulk-insert optimization
     * in InsertResultSet.
     *
     * @param sequenceUUIDString UUID of the sequence which backs the identity column.
     * @param restart   True if the counter should be re-initialized to its start position.
     */
    public  BulkInsertCounter   getBulkInsertCounter
        ( String sequenceUUIDString, boolean restart )
        throws StandardException{
    	return null;
    }

    /**
     * Flush the updated values of the BulkInsertCounter to disk and to the original, cached
     * SequenceUpdater. This is used for the bulk-insert optimization in InsertResultSet.
     *
     * @param sequenceUUIDString UUID of the sequence which backs the identity column.
     * @param bic   the BulkInsertCounter which generates identities for bulk insert
     */
    public  void   flushBulkInsertCounter
        ( String sequenceUUIDString, BulkInsertCounter bic )
        throws StandardException{
        	return ;
        }
	

}
