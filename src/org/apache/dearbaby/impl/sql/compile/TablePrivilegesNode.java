/*

   Derby - Class org.apache.derby.impl.sql.compile.TablePrivilegesNode

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

package	org.apache.dearbaby.impl.sql.compile;

import java.util.ArrayList;
import java.util.List;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext; 
import org.apache.derby.iapi.sql.dictionary.AliasDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.dictionary.ViewDescriptor; 

/**
 * This class represents a set of privileges on one table.
 */
class TablePrivilegesNode extends QueryTreeNode
{
	private boolean[] actionAllowed = new boolean[ 1];
	private ResultColumnList[] columnLists = new ResultColumnList[ 1];
	private FormatableBitSet[] columnBitSets = new FormatableBitSet[1];
	private TableDescriptor td;   

    TablePrivilegesNode(ContextManager cm) {
        super(cm);
    }

	/**
	 * Add all actions
	 */
    void addAll()
	{
		 
	} // end of addAll

	/**
	 * Add one action to the privileges for this table
	 *
	 * @param action The action type
	 * @param privilegeColumnList The set of privilege columns. Null for all columns
	 *
	 * @exception StandardException standard error policy.
	 */
    void addAction( int action, ResultColumnList privilegeColumnList)
	{
		actionAllowed[ action] = true;
		if( privilegeColumnList == null)
			columnLists[ action] = null;
		else if( columnLists[ action] == null)
			columnLists[ action] = privilegeColumnList;
		else
			columnLists[ action].appendResultColumns( privilegeColumnList, false);
	} // end of addAction

	/**
	 * Bind.
	 *
	 * @param td The table descriptor
	 * @param isGrant grant if true; revoke if false
	 */
    void bind( TableDescriptor td, boolean isGrant) throws StandardException
	{
		this.td = td;
			
		 
		
		if (isGrant && td.getTableType() == TableDescriptor.VIEW_TYPE)
		{
			bindPrivilegesForView(td);
		}
	}
	
	 
	
	/**
	 *  Retrieve all the underlying stored dependencies such as table(s), 
	 *  view(s) and routine(s) descriptors which the view depends on.
	 *  This information is then passed to the runtime to determine if
	 *  the privilege is grantable to the grantees by this grantor at
	 *  execution time.
	 *  
	 *  Go through the providers regardless who the grantor is since 
	 *  the statement cache may be in effect.
	 *  
	 * @param td the TableDescriptor to check
	 *
	 * @exception StandardException standard error policy.
	 */
	private void bindPrivilegesForView ( TableDescriptor td) 
		throws StandardException
	{ }
	
}
	
