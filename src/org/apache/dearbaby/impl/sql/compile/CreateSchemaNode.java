/*

   Derby - Class org.apache.derby.impl.sql.compile.CreateSchemaNode

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
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A CreateSchemaNode is the root of a QueryTree that 
 * represents a CREATE SCHEMA statement.
 *
 */

class CreateSchemaNode extends DDLStatementNode
{
	private String 	name;
	private String	aid;
	
	/**
     * Constructor for a CreateSchemaNode
	 *
	 * @param schemaName	The name of the new schema
	 * @param aid		 	The authorization id
     * @param cm            The context manager
	 *
	 * @exception StandardException		Thrown on error
	 */
    CreateSchemaNode(
            String schemaName,
            String aid,
            ContextManager cm) throws StandardException
	{
		/*
		** DDLStatementNode expects tables, null out
		** objectName explicitly to clarify that we
		** can't hang with schema.object specifiers.
		*/
        super(null, cm);
        this.name = schemaName;
        this.aid = aid;
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
			return super.toString() +
				"schemaName: " + "\n" + name + "\n" +
				"authorizationId: " + "\n" + aid + "\n";
		}
		else
		{
			return "";
		}
	}
 
    String statementToString()
	{
		return "CREATE SCHEMA";
	}

	// We inherit the generate() method from DDLStatementNode.

	/**
	 * Create the Constant information that will drive the guts of Execution.
	 *
	 * @exception StandardException		Thrown on failure
	 */
    @Override
    public ConstantAction makeConstantAction()
	{
		return	getGenericConstantActionFactory().getCreateSchemaConstantAction(name, aid);
	}
}
