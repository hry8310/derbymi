/*

   Derby - Class org.apache.derby.impl.sql.GenericStorablePreparedStatement

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

package org.apache.derby.impl.sql;

 

import org.apache.derby.shared.common.sanity.SanityManager;

import org.apache.derby.catalog.UUID;
import org.apache.derby.iapi.services.uuid.UUIDFactory;
import org.apache.derby.iapi.util.ByteArray;

import org.apache.derby.iapi.sql.dictionary.DataDictionary;

import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.sql.ResultColumnDescriptor;
import org.apache.derby.iapi.sql.ResultDescription;
import org.apache.derby.iapi.sql.ResultSet;
import org.apache.derby.iapi.sql.PreparedStatement;
import org.apache.derby.iapi.sql.Statement;
import org.apache.derby.iapi.sql.StorablePreparedStatement;
 
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;

import org.apache.derby.iapi.error.StandardException;

import org.apache.derby.iapi.reference.SQLState;
 
import org.apache.derby.iapi.services.context.ContextService;

import org.apache.derby.iapi.services.io.StoredFormatIds;
import org.apache.derby.iapi.services.io.FormatIdUtil; 
import org.apache.derby.iapi.services.io.Formatable;
 

import java.sql.Timestamp;
import java.io.ObjectOutput;
import java.io.ObjectInput;
import java.io.IOException;
 
/**
 * Prepared statement that can be made persistent.
 */
public class GenericStorablePreparedStatement
	extends GenericPreparedStatement implements Formatable, StorablePreparedStatement
{

	// formatable
	private ByteArray 		byteCode;
	private String 			className;

	/**
	 * Niladic constructor, for formatable
	 * only.
	 */
	public GenericStorablePreparedStatement()
	{
		super();
	}

	GenericStorablePreparedStatement(Statement stmt)
	{
		super(stmt);
	}

	/**
	 * Get our byte code array.  Used
	 * by others to save off our byte
	 * code for us.
	 *
	 * @return the byte code saver
	 */
	ByteArray getByteCodeSaver()
	{
		if (byteCode == null) {
			byteCode = new ByteArray();
		}
		return byteCode;
	}

	 

	/////////////////////////////////////////////////////////////
	// 
	// STORABLEPREPAREDSTATEMENT INTERFACE
	// 
	/////////////////////////////////////////////////////////////

	/**
	 * Load up the class from the saved bytes.
	 *
	 * @exception StandardException on error
	 */
	public void loadGeneratedClass()
		throws StandardException
	{
		 
	}


	/////////////////////////////////////////////////////////////
	// 
	// EXTERNALIZABLE INTERFACE
	// 
	/////////////////////////////////////////////////////////////
	/** 
	 *
	 * @exception IOException on error
	 */
	public void writeExternal(ObjectOutput out) throws IOException
	{ 
	}

	 
	/** 
	 * @see java.io.Externalizable#readExternal 
	 *
	 * @exception IOException on error
	 * @exception ClassNotFoundException on error
	 */
	public void readExternal(ObjectInput in) 
		throws IOException, ClassNotFoundException
	{
		 
	}

	/////////////////////////////////////////////////////////////
	// 
	// FORMATABLE INTERFACE
	// 
	/////////////////////////////////////////////////////////////
	/**
	 * Get the formatID which corresponds to this class.
	 *
	 *	@return	the formatID of this class
	 */
	public	int getTypeFormatId()	{ return StoredFormatIds.STORABLE_PREPARED_STATEMENT_V01_ID; }

	/////////////////////////////////////////////////////////////
	// 
	// MISC
	// 
	/////////////////////////////////////////////////////////////
	public boolean isStorable() {
		return true;
	}
	public String toString()
	{
		 return "";
	} 
}
