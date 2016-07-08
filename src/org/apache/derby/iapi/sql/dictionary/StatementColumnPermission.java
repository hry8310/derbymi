/*

   Derby - Class org.apache.derby.iapi.sql.dictionary.StatementColumnPermission

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
import org.apache.derby.catalog.UUID;
import org.apache.derby.iapi.sql.conn.Authorizer;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.Activation; 
import org.apache.derby.iapi.services.context.ContextManager;

/**
 * This class describes a column permission used (required) by a statement.
 */

public class StatementColumnPermission extends StatementTablePermission
{
	private FormatableBitSet columns;

	/**
	 * Constructor for StatementColumnPermission. Creates an instance of column permission requested
	 * for the given access.
	 * 
	 * @param tableUUID	UUID of the table
	 * @param privType	Access privilege requested
	 * @param columns	List of columns
	 *
	 */
	public StatementColumnPermission(UUID tableUUID, int privType, FormatableBitSet columns)
	{
		super( tableUUID, privType);
		this.columns = columns;
	}

	/**
	 * Return list of columns that need access
	 *
	 * @return	FormatableBitSet of columns
	 */
	public FormatableBitSet getColumns()
	{
		return columns;
	}

	/**
	 * Method to check if another instance of column access descriptor matches this.
	 * Used to ensure only one access descriptor for a table/columns of given privilege is created.
	 *
	 * @param obj	Another instance of StatementPermission
	 *
	 * @return	true if match
	 */
	public boolean equals( Object obj)
	{
		if( obj instanceof StatementColumnPermission)
		{
			StatementColumnPermission other = (StatementColumnPermission) obj;
			if( ! columns.equals( other.columns))
				return false;
			return super.equals( obj);
		}
		return false;
	}
	
	/**
	 * @see StatementPermission#check
	 */
	public void check( LanguageConnectionContext lcc,
					   boolean forGrant,
					   Activation activation)
		throws StandardException
	{
		 

	} // end of check

	/**
	 * Add one user's set of permitted columns to a list of permitted columns.
	 */
	private FormatableBitSet addPermittedColumns( DataDictionary dd,
												  boolean forGrant,
												  String authorizationId,
												  FormatableBitSet permittedColumns)
		throws StandardException
	{
		if( permittedColumns != null && permittedColumns.getNumBitsSet() == permittedColumns.size())
			return permittedColumns;
		ColPermsDescriptor perms = dd.getColumnPermissions( tableUUID, privType, false, authorizationId);
		if( perms != null)
		{
			if( permittedColumns == null)
				return perms.getColumns();
			permittedColumns.or( perms.getColumns());
		}
		return permittedColumns;
	} // end of addPermittedColumns

	/**
	 * @see StatementPermission#getPermissionDescriptor
	 */
	public PermissionsDescriptor getPermissionDescriptor(String authid, DataDictionary dd)
	throws StandardException
	{
		//If table permission found for authorizationid, then simply return that
		if (oneAuthHasPermissionOnTable( dd, authid, false))
			return dd.getTablePermissions(tableUUID, authid);
		//If table permission found for PUBLIC, then simply return that
		if (oneAuthHasPermissionOnTable( dd, Authorizer.PUBLIC_AUTHORIZATION_ID, false))
			return dd.getTablePermissions(tableUUID, Authorizer.PUBLIC_AUTHORIZATION_ID);
		
		//If table level permission not found, then we have to find permissions 
		//at column level. Look for column level permission for the passed 
		//authorizer. If found any of the required column level permissions,
		//return the permission descriptor for it.
		ColPermsDescriptor colsPermsDesc = dd.getColumnPermissions(tableUUID, privType, false, authid);
		if( colsPermsDesc != null)
		{
			if( colsPermsDesc.getColumns() != null){
				FormatableBitSet permittedColumns = colsPermsDesc.getColumns();
				for( int i = columns.anySetBit(); i >= 0; i = columns.anySetBit( i))
				{
					if(permittedColumns.get(i))
						return colsPermsDesc;
				}
			}
		}
		return null;
	}
	
	/**
	 * This method gets called in execution phase after it is established that 
	 * all the required privileges exist for the given sql. This method gets 
	 * called by create view/trigger/constraint to record their dependency on 
	 * various privileges.
	 * Special code is required to track column level privileges.
	 * It is possible that some column level privileges are available to the
	 * passed authorizer id but the rest required column level privileges
	 * are available at PUBLIC level. In this method, we check if all the
	 * required column level privileges are found for the passed authorizer.
	 * If yes, then simply return null, indicating that no dependency is 
	 * required at PUBLIC level, because all the required privileges were found
	 * at the user level. But if some column level privileges are not
	 * available at user level, then they have to exist at the PUBLIC
	 * level when this method gets called.  
	 */
	public PermissionsDescriptor getPUBLIClevelColPermsDescriptor(String authid, DataDictionary dd)
	throws StandardException
	{
		ColPermsDescriptor colsPermsDesc = dd.getColumnPermissions(tableUUID, privType, false, authid);
		FormatableBitSet permittedColumns = colsPermsDesc.getColumns();
		boolean allColumnsCoveredByUserLevelPrivilege = true;
		for( int i = columns.anySetBit(); i >= 0 && allColumnsCoveredByUserLevelPrivilege; i = columns.anySetBit( i))
		{
			if(permittedColumns.get(i))
				continue;
			else
				allColumnsCoveredByUserLevelPrivilege = false;
		}
		if (allColumnsCoveredByUserLevelPrivilege)
			return null;
		else
			return (dd.getColumnPermissions(tableUUID, privType, false, Authorizer.PUBLIC_AUTHORIZATION_ID));	
	}

	/**
	 * Returns false if the current role is necessary to cover
	 * the necessary permission(s).
	 * @param authid authentication id of the current user
	 * @param dd data dictionary
	 *
	 * @return false if the current role is required
	 */
	public boolean allColumnsCoveredByUserOrPUBLIC(String authid,
												   DataDictionary dd)
			throws StandardException {

		ColPermsDescriptor colsPermsDesc =
			dd.getColumnPermissions(tableUUID, privType, false, authid);
		FormatableBitSet permittedColumns = colsPermsDesc.getColumns();
		FormatableBitSet unresolvedColumns = (FormatableBitSet)columns.clone();
		boolean result = true;

		if (permittedColumns != null) { // else none at user level
			for(int i = unresolvedColumns.anySetBit();
				i >= 0;
				i = unresolvedColumns.anySetBit(i)) {

				if(permittedColumns.get(i)) {
					unresolvedColumns.clear(i);
				}
			}
		}


		if (unresolvedColumns.anySetBit() >= 0) {
			colsPermsDesc =
				dd.getColumnPermissions(
					tableUUID, privType, false,
					Authorizer.PUBLIC_AUTHORIZATION_ID);
			permittedColumns = colsPermsDesc.getColumns();

			if (permittedColumns != null) { // else none at public level
				for(int i = unresolvedColumns.anySetBit();
					i >= 0;
					i = unresolvedColumns.anySetBit(i)) {

					if(permittedColumns.get(i)) {
						unresolvedColumns.clear(i);
					}
				}
			}

			if (unresolvedColumns.anySetBit() >= 0) {
				// even after trying all grants to user and public there
				// are unresolved columns so role must have been used.
				result = false;
			}
		}

		return result;
	}


	/**
	 * Try to use the supplied role r to see what column privileges are we 
	 * entitled to. 
	 *
	 * @param lcc language connection context
	 * @param dd  data dictionary
	 * @param forGrant true of a GRANTable permission is sought
	 * @param r the role to inspect to see if it can supply the required
	 *          privileges
	 * return the set of columns on which we have privileges through this role
	 */
	private FormatableBitSet tryRole(LanguageConnectionContext lcc,
									 DataDictionary dd,
									 boolean forGrant,
									 String r)
			throws StandardException {

		FormatableBitSet permittedColumns = null;

		if (! forGrant) {
			// This is a weaker permission than GRANTable, so only applicable
			// if grantable is not required.
			permittedColumns = addPermittedColumns(dd, false, r, null);
		}

		// if grantable is given, applicable in both cases, so use union
		permittedColumns = addPermittedColumns(dd, true, r, permittedColumns);
		return permittedColumns;
	}


	public String toString()
	{
		return "StatementColumnPermission: " + getPrivName() + " " +
			tableUUID + " columns: " + columns;
	}
}
