/*

   Derby - Class org.apache.derby.impl.sql.compile.LockTableNode

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

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A LockTableNode is the root of a QueryTree that represents a LOCK TABLE command:
 *	LOCK TABLE <TableName> IN SHARE/EXCLUSIVE MODE
 *
 */

class LockTableNode extends MiscellaneousStatementNode
{
	private TableName	tableName;
	private boolean		exclusiveMode;
	private long		conglomerateNumber;
	private TableDescriptor			lockTableDescriptor;

    /**
     * @param tableName The table to lock
     * @param exclusiveMode Whether or not to get an exclusive lock.
     * @param cm Context manager
     */
    LockTableNode(
            TableName tableName,
            boolean exclusiveMode,
            ContextManager cm) {
        super(cm);
        this.tableName = tableName;
        this.exclusiveMode = exclusiveMode;
    }

    /**
	 * Convert this object to a String.  See comments in QueryTreeNode.java
	 * for how this should be done for tree printing.
	 *
	 * @return	This object as a String
	 */
    @Override
	public String toString()
	{
		if (SanityManager.DEBUG)
		{
			return "tableName: " + tableName + "\n" +
				"exclusiveMode: " + exclusiveMode + "\n" +
				"conglomerateNumber: " + conglomerateNumber + "\n" +
				super.toString();
		}
		else
		{
			return "";
		}
	}

    String statementToString()
	{
		return "LOCK TABLE";
	}
 

	/**
	 * Return true if the node references SESSION schema tables (temporary or permanent)
	 *
	 * @return	true if references SESSION schema tables, else false
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
	public boolean referencesSessionSchema()
		throws StandardException
	{
		//If lock table is on a SESSION schema table, then return true. 
		return isSessionSchema(lockTableDescriptor.getSchemaName());
	}

	/**
	 * Create the Constant information that will drive the guts of Execution.
	 *
	 * @exception StandardException		Thrown on failure
	 */
    @Override
    public ConstantAction makeConstantAction() throws StandardException
	{
		return getGenericConstantActionFactory().getLockTableConstantAction(
						tableName.getFullTableName(),
						conglomerateNumber,
						exclusiveMode);
	}

    @Override
    void acceptChildren(Visitor v) throws StandardException {
        super.acceptChildren(v);

        if (tableName != null) {
            tableName = (TableName) tableName.accept(v);
        }
    }
}
