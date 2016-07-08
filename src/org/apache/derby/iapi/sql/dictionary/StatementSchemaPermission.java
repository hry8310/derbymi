/*

   Derby - Class org.apache.derby.iapi.sql.dictionary.StatementSchemaPermission

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

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.sql.conn.Authorizer;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext; 
import org.apache.derby.shared.common.sanity.SanityManager;
import org.apache.derby.iapi.sql.Activation;

/**
 * This class describes a schema permission required by a statement.
 */

public class StatementSchemaPermission extends StatementPermission
{
	/**
	 * The schema name 
	 */
	private String schemaName;
	/**
	 * Authorization id
	 */
	private String aid;  
	/**	 
	 * One of Authorizer.CREATE_SCHEMA_PRIV, MODIFY_SCHEMA_PRIV,  
	 * DROP_SCHEMA_PRIV, etc.
	 */ 
	private int privType;  

	public StatementSchemaPermission(String schemaName, String aid, int privType)
	{
		this.schemaName = schemaName;
		this.aid 	= aid;
		this.privType	= privType;
	}

	/**
	 * @see StatementPermission#check
	 */
	public void check( LanguageConnectionContext lcc,
					   boolean forGrant,
					   Activation activation) throws StandardException
	{
		DataDictionary dd =	lcc.getDataDictionary(); 
         
	}

	/**
	 * Schema level permission is never required as list of privileges required
	 * for triggers/constraints/views and hence we don't do any work here, but
	 * simply return null
	 * 
	 * @see StatementPermission#check
	 */
	public PermissionsDescriptor getPermissionDescriptor(String authid, DataDictionary dd)
	throws StandardException
	{
		return null;
	}

    private String getPrivName( )
	{
		switch(privType) {
		case Authorizer.CREATE_SCHEMA_PRIV:
			return "CREATE_SCHEMA";
		case Authorizer.MODIFY_SCHEMA_PRIV:
			return "MODIFY_SCHEMA";
		case Authorizer.DROP_SCHEMA_PRIV:
			return "DROP_SCHEMA";
        default:
            return "?";
        }
    }

	public String toString() {
		return "StatementSchemaPermission: " + schemaName + " owner:" +
			aid + " " + getPrivName();
	}
}
