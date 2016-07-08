/*

   Derby - Class org.apache.derby.iapi.sql.dictionary.SPSDescriptor

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

package org.apache.derby.iapi.sql.dictionary;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.derby.catalog.Dependable;
import org.apache.derby.catalog.DependableFinder;
import org.apache.derby.catalog.UUID;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.context.ContextService; 
import org.apache.derby.iapi.services.io.StoredFormatIds; 
import org.apache.derby.shared.common.sanity.SanityManager;
import org.apache.derby.iapi.services.uuid.UUIDFactory;
import org.apache.derby.iapi.sql.Statement;
import org.apache.derby.iapi.sql.StorablePreparedStatement;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.conn.LanguageConnectionFactory;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.DataTypeUtilities;
import org.apache.derby.iapi.types.DataValueDescriptor;

/**
 * A SPSDescriptor describes a Stored Prepared Statement.
 * It correlates to a row in SYS.SYSSTATEMENTS.
 *
 * <B>SYNCHRONIZATION</B>: Stored prepared statements
 * may be cached.  Thus they may be shared by multiple
 * threads.  It is very hard for two threads to try
 * to muck with an sps simultaeously because all ddl
 * (including sps recompilation) clears out the sps
 * cache and invalidates whatever statement held a
 * cached sps.  But it is possible for two statements
 * to do a prepare execute statment <x> at the exact
 * same time, so both try to do an sps.prepare() at the 
 * same time during code generation, so we synchronize
 * most everything except getters on immutable objects
 * just to be on the safe side.
 *
 *
 */
public class SPSDescriptor extends UniqueSQLObjectDescriptor
{
	/**
	 * Statement types.  
	 * <UL>
     * <LI> SPS_TYPE_TRIGGER    - trigger</LI>
	 * <LI> SPS_TYPE_EXPLAIN	- explain (<B>NOT IMPLEMENTED</B>) </LI>
	 * <LI> SPS_TYPE_REGULAR	- catchall</LI>
	 * </UL>
	 */
	public static   char SPS_TYPE_TRIGGER	= 'T';
	public static   char SPS_TYPE_REGULAR	= 'S';
	public static   char SPS_TYPE_EXPLAIN	= 'X';

	/**
	   interface to this class is:
	   <ol>
	   <li>public void prepare() throws StandardException;
	   <li>public void prepareAndRelease(LanguageConnectionContext lcc) 
	   throws StandardException;
	   <li>public void prepareAndRelease(...);
	   <li>public String	getQualifiedName();
	   <li>public char	getType();
	   <li>public String getTypeAsString();
	   <li>public boolean isValid();
	   <li>public boolean initiallyCompilable();
	   <li>public java.sql.Timestamp getCompileTime();
	   <li>public void setCompileTime();
	   <li>public String getText();
	   <li>public String getUsingText();
	   <li>public void setUsingText(String usingText);
	   <li>public void	setUUID(UUID uuid);
	   <li>public DataTypeDescriptor[] getParams() throws StandardException;
	   <li>public void setParams(DataTypeDescriptor[] params);
	   <li>Object[] getParameterDefaults()	throws StandardException;
	   <li>void setParameterDefaults(Object[] values);
	   <li>public UUID getCompSchemaId();
	   <li>public ExecPreparedStatement getPreparedStatement()
	   throws StandardException;
	   <li>public ExecPreparedStatement getPreparedStatement(boolean recompIfInvalid)
	   throws StandardException;
	   <li>public void revalidate(LanguageConnectionContext lcc)
			throws StandardException;
			</ol>
	*/

	private static   int RECOMPILE = 1;
	private static   int INVALIDATE = 0;

		
	// Class contents
    private   SchemaDescriptor sd;
    private   String name;
    private   UUID compSchemaId;
    private   char type;
    private String text;
    private   String usingText;
    private   UUID uuid;

	private	boolean					valid;
	private	DataTypeDescriptor		params[];
	private	Timestamp				compileTime;
	/**
	 * Old code - never used.
	 */
	private Object			paramDefaults[];
    private   boolean   initiallyCompilable;
	private	boolean					lookedUpParams;
	
	private UUIDFactory				uuidFactory;


	// constructors
	/**
	 * Constructor for a SPS Descriptor
	 *
	 * @param dataDictionary		The data dictionary that this descriptor lives in
	 * @param 	name 	the SPS name
	 * @param 	uuid	the UUID
	 * @param 	suuid	the schema UUID
	 * @param 	compSchemaUUID	the schema UUID at compilation time
	 * @param	type	type
	 * @param 	valid	is the sps valid
	 * @param 	text	the text for this statement
	 * @param	initiallyCompilable	is the statement initially compilable?
	 *
	 * @exception StandardException on error
	 */
	public SPSDescriptor
				(DataDictionary		dataDictionary,
				String				name,
			   	UUID				uuid,
			   	UUID				suuid,
				UUID				compSchemaUUID,
				char				type,
			   	boolean				valid,
			   	String				text,
				boolean				initiallyCompilable ) throws StandardException
	{
	 
	}

	/**
	 * Constructor for a SPS Descriptor.  Used when
	 * constructing an SPS descriptor from a row
	 * in SYSSTATEMENTS.
	 *
	 * @param	dataDictionary		The data dictionary that this descriptor lives in
	 * @param 	name 	the SPS name
	 * @param 	uuid	the UUID
	 * @param 	suuid	the schema UUID
	 * @param 	compSchemaUUID	the schema UUID at compilation time
	 * @param	type	type
	 * @param 	valid	is the sps valid
	 * @param 	text	the text for this statement
	 * @param 	usingText	the text for the USING clause supplied to
	 *					CREATE or ALTER STATEMENT
	 * @param 	compileTime	the time this was compiled
	 * @param 	preparedStatement	the PreparedStatement
	 * @param	initiallyCompilable	is the statement initially compilable?
	 *
	 * @exception StandardException on error
	 */
	public SPSDescriptor
				(DataDictionary	dataDictionary,
				String		name,
			   	UUID		uuid,
				UUID		suuid,
				UUID		compSchemaUUID,
				char		type,
			   	boolean		valid,
			   	String		text,
				String 		usingText,
			   	Timestamp	compileTime, 
				boolean		initiallyCompilable ) throws StandardException
	{
		super( dataDictionary );

        // Added this check when setUUID was removed, see DERBY-4918.
        if (uuid == null) {
            throw new IllegalArgumentException("UUID is null");
        }
		this.name = name;
		this.uuid = uuid; 
		this.type = type;
		this.text = text;
		this.usingText = usingText;
		this.valid = valid;
		this.compileTime = DataTypeUtilities.clone( compileTime ); 
		this.compSchemaId = compSchemaUUID;
		this.initiallyCompilable = initiallyCompilable;
	}

	 
	
	/**
	 * FOR TRIGGERS ONLY
	 * <p>
	 * Generate the class for this SPS and immediately
	 * release it.  This is useful for cases where we
	 * don't want to immediately execute the statement 
	 * corresponding to this sps (e.g. CREATE STATEMENT).
 	 * <p>
	 * <I>SIDE EFFECTS</I>: will update and SYSDEPENDS 
	 * with the prepared statement dependency info.
 	 * 
	 * @param lcc the language connection context
	 * @param triggerTable the table descriptor to bind against.  Had
	 * 	better be null if this isn't a trigger sps.
	 *
	 * @exception StandardException on error
	 */
	public final synchronized void prepareAndRelease
	(
		LanguageConnectionContext	lcc, 
		TableDescriptor				triggerTable
	) throws StandardException
	{
		 
	}

	/**
	 * Generate the class for this SPS and immediately
	 * release it.  This is useful for cases where we
	 * don't want to immediately execute the statement 
	 * corresponding to this sps (e.g. CREATE STATEMENT).
 	 * <p>
	 * <I>SIDE EFFECTS</I>: will update and SYSDEPENDS 
	 * with the prepared statement dependency info.
	 *
	 * @param lcc the language connection context
 	 * 
	 * @exception StandardException on error
	 */
	public final synchronized void prepareAndRelease(LanguageConnectionContext lcc) throws StandardException
	{
	 
	}

    

	/**
	 * Gets the name of the sps.
	 *
	 * @return	A String containing the name of the statement.
	 */
	public final String	getName()
	{
		return name;
	}

	/**
	 * Gets the full, qualified name of the statement.
	 *
	 * @return	A String containing the name of the statement.
	 */
	public final String	getQualifiedName()
	{
		return sd.getSchemaName() + "." + name;
	}

	/**
	 * Gets the SchemaDescriptor for this SPS Descriptor.
	 *
	 * @return SchemaDescriptor	The SchemaDescriptor.
	 */
	public final SchemaDescriptor getSchemaDescriptor()
	{
		return sd;
	}

	/**
	 * Gets an identifier telling what type of table this is.
	 * Types match final ints in this interface.  Currently
	 * returns SPS_TYPE_REGULAR or SPS_TYPE_TRIGGER.
	 *
	 * @return	An identifier telling what type of statement
 	 * we are.
	 */
	public final char getType()
	{
		return type;
	}	

	/**
	 * Simple little helper function to convert your type
	 * to a string, which is easier to use.
	 *
	 * @return type as a string
	 */	
    public final String getTypeAsString() {
        return String.valueOf(type);
    }

	/**
	 * Is the statement initially compilable?  
	 *
	 * @return	false if statement was created with the NOCOMPILE flag
	 *			true otherwise
	 */
	public boolean initiallyCompilable() { return initiallyCompilable; }
	
	/**
	 * Validate the type. <B>NOTE</B>: Only SPS_TYPE_REGULAR
	 * and SPS_TYPE_TRIGGER are currently valid.
	 *
	 * @param type the type
	 *
	 * @return true/false	
	 */
    public static boolean validType(char type)
	{
		return (type == SPSDescriptor.SPS_TYPE_REGULAR) || 
				(type == SPSDescriptor.SPS_TYPE_TRIGGER);
	}

	/**
	 * The time this prepared statement was compiled
	 *
	 * @return the time this class was last compiled
	 */
	public final synchronized Timestamp getCompileTime()
	{
		return DataTypeUtilities.clone( compileTime );
	}

	/**
	 * Set the compile time to now
	 *
	 */
	public final synchronized void setCompileTime()
	{
		compileTime = new Timestamp(System.currentTimeMillis());
	}
	 
	/**
	 * Get the text used to create this statement.
	 * Returns original text in a cleartext string.
	 *
	 * @return The text
	 */
	public final synchronized String getText()
	{
		return text;
	}

	/**
	 * It is possible that when a trigger is invalidated, the generated trigger
	 * action sql associated with it needs to be regenerated. One example
	 * of such a case would be when ALTER TABLE on the trigger table
	 * changes the length of a column. The need for this code was found
	 * as part of DERBY-4874 where the Alter table had changed the length 
	 * of a varchar column from varchar(30) to varchar(64) but the generated 
	 * trigger action plan continued to use varchar(30). To fix varchar(30) in
	 * in trigger action sql to varchar(64), we need to regenerate the 
	 * trigger action sql which is saved as stored prepared statement. This 
	 * new trigger action sql will then get updated into SYSSTATEMENTS table.
	 * DERBY-4874
	 * 
	 * @param newText
	 */
	public final synchronized void setText(String newText)
	{
		text = newText;
	}
	/**
	 * Get the text of the USING clause used on CREATE
	 * or ALTER statement.
	 *
	 * @return The text
	 */
    public final String getUsingText()
	{
		return usingText;
	}

    /**
     * Gets the UUID of the SPS.
     *
     * @return The UUID.
     */
    public final UUID getUUID() {
		return uuid;
	}
	
	/**
	 * Get the array of date type descriptors for
	 * this statement.  Currently, we do a lookup
	 * if we don't already have the parameters saved.
	 * When SPSes are cached, the parameters should
	 * be set up when the sps is constructed.
	 *
	 * @return the array of data type descriptors
	 *
	 * @exception StandardException on error
	 */
	public final synchronized DataTypeDescriptor[] getParams()
		throws StandardException
	{
        if (params == null && !lookedUpParams) {
            List<DataValueDescriptor> tmpDefaults = new ArrayList<DataValueDescriptor>();
            params = getDataDictionary().getSPSParams(this, tmpDefaults);
            paramDefaults = tmpDefaults.toArray();
            lookedUpParams = true;
        }

        return null;
	}

	/**
	 * Set the list of parameters for this statement
	 *
	 * @param params	the parameter list
	 */
	public final synchronized void setParams(DataTypeDescriptor params[])
	{
        
	}

	/**
	 * Get the default parameter values for this 
	 * statement.  Default parameter values are
	 * supplied by a USING clause on either a
	 * CREATE or ALTER STATEMENT statement.
	 *
	 * @return the default parameter values
 	 *
	 * @exception StandardException on error
	 */
	public final synchronized Object[] getParameterDefaults()
		throws StandardException
	{
		if (paramDefaults == null)
        {
			getParams();
        }

		return null;
	}

	/**
	 * Set the parameter defaults for this statement.
	 *
	 * @param values	the parameter defaults
	 */
	public final synchronized void setParameterDefaults(Object[] values)
	{
		 
	}
	
	  

	/**
	 * Get the compilation type schema id when this view
	 * was first bound.
	 *
	 * @return the schema UUID
	 */
	public final UUID getCompSchemaId()
	{
		return compSchemaId;
	}

	/**
	 * Prints the contents of the TableDescriptor
	 *
	 * @return The contents as a String
	 */
    @Override
	public final String toString()
	{
		 
		{
			return "";
		}
	}

	//////////////////////////////////////////////////////
	//
	// PROVIDER INTERFACE
	//
	//////////////////////////////////////////////////////

	/**		
	 * Return the stored form of this provider
	 *
	 * @see Dependable#getDependableFinder
	 */
	public final DependableFinder getDependableFinder()
	{
	    return	getDependableFinder(StoredFormatIds.SPS_DESCRIPTOR_FINDER_V01_ID);
	}

	/**
	 * Return the name of this Provider.  (Useful for errors.)
	 *
	 * @return String	The name of this provider.
	 */
	public final String getObjectName()
	{
		return name;
	}

	/**
	 * Get the provider's UUID 
	 *
	 * @return String	The provider's UUID
	 */
	public final UUID getObjectID()
	{
		return uuid;
	}

	/**
	 * Get the provider's type.
	 *
	 * @return String The provider's type.
	 */
	public final String getClassType()
	{
		return Dependable.STORED_PREPARED_STATEMENT;
	}

	//////////////////////////////////////////////////////
	//
	// DEPENDENT INTERFACE
	//
	//////////////////////////////////////////////////////
	/**
	 * Check that all of the dependent's dependencies are valid.
	 *
	 * @return true if the dependent is currently valid
	 */
	public final synchronized boolean isValid()
	{
		return valid;
	}

	 

	/**
	 * Mark the dependent as invalid (due to at least one of
	 * its dependencies being invalid).
	 *
	 * @param	action	The action causing the invalidation
	 *
	 * @exception StandardException thrown if unable to make it invalid
	 */
	public final synchronized void makeInvalid(int action,
											   LanguageConnectionContext lcc) 
		throws StandardException
	{
		 

	}

	/**
	 * Invalidate and revalidate.  The functional equivalent
	 * of calling makeInvalid() and makeValid(), except it
	 * is optimized.
	 *
	 * @exception StandardException on error
	 */
	public final synchronized void revalidate(LanguageConnectionContext lcc)
		throws StandardException
	{
		 
	}

	/**
	 * Load the underlying generatd class.  This is not expected
	 * to be used outside of the datadictionary package.  It
	 * is used for optimizing class loading for sps
	 * cacheing.
	 *
	 * @exception StandardException on error
	 */
	public void loadGeneratedClass() throws StandardException
	{
		 
	}
 

	/**
	 * Get the UUID for the given string
	 *
	 * @param idString	the string
	 *
	 * @return the UUID
	 */
	private UUID recreateUUID(String idString)
	{
		 
		return uuidFactory.recreateUUID(idString);
	}

	/** @see TupleDescriptor#getDescriptorType */
    @Override
	public String getDescriptorType() { return "Statement"; }

	/** @see TupleDescriptor#getDescriptorName */
	// RESOLVE: some descriptors have getName.  some descriptors have
	// getTableName, getColumnName whatever! try and unify all of this to one
	// getDescriptorName! 
    @Override
	public String getDescriptorName() { return name; }
	
}

