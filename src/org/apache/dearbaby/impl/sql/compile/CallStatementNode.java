/*

   Derby - Class org.apache.derby.impl.sql.compile.CallStatementNode

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

import org.apache.derby.catalog.types.RoutineAliasInfo;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.ResultDescription;
import org.apache.derby.iapi.sql.compile.CompilerContext;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.conn.Authorizer;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * An CallStatementNode represents a CALL <procedure> statement.
 * It is the top node of the query tree for that statement.
 * A procedure call is very simple.
 * 
 * CALL [<schema>.]<procedure>(<args>)
 * 
 * <args> are either constants or parameter markers.
 * This implementation assumes that no subqueries or aggregates
 * can be in the argument list.
 * 
 * A procedure is always represented by a MethodCallNode.
 *
 */
class CallStatementNode extends DMLStatementNode
{	
	/**
	 * The method call for the Java procedure. Guaranteed to be
	 * a JavaToSQLValueNode wrapping a MethodCallNode by checks
	 * in the parser.
	 */
	private JavaToSQLValueNode	methodCall;


	/**
     * Constructor for a CallStatementNode.
	 *
	 * @param methodCall		The expression to "call"
     * @param cm                The context manager
	 */

    CallStatementNode(JavaToSQLValueNode methodCall, ContextManager cm)
	{
        super(null, cm);
        this.methodCall = methodCall;
		this.methodCall.getJavaValueNode().markForCallStatement();
	}


    String statementToString()
	{
		return "CALL";
	}

	/**
	 * Prints the sub-nodes of this object.  See QueryTreeNode.java for
	 * how tree printing is supposed to work.
	 *
	 * @param depth		The depth of this node in the tree
	 */
    @Override
    void printSubNodes(int depth)
	{
		if (SanityManager.DEBUG)
		{
			super.printSubNodes(depth);

			if (methodCall != null)
			{
				printLabel(depth, "methodCall: ");
				methodCall.treePrint(depth + 1);
			}
		}
	}

	 
	 
	 

    @Override
	public ResultDescription makeResultDescription()
	{
		return null;
	}

	/**
	 * Accept the visitor for all visitable children of this node.
	 * 
	 * @param v the visitor
	 *
	 * @exception StandardException on error
	 */
    @Override
	void acceptChildren(Visitor v)
		throws StandardException
	{
		super.acceptChildren(v);

		if (methodCall != null)
		{
			methodCall = (JavaToSQLValueNode) methodCall.accept(v);
		}
	}

	/**
	 * Set default privilege of EXECUTE for this node. 
	 */
    @Override
	int getPrivType()
	{
		return Authorizer.EXECUTE_PRIV;
	}
	
	/**
	 * This method checks if the called procedure allows modification of SQL 
	 * data. If yes, it cannot be compiled if the reliability is 
	 * <code>CompilerContext.MODIFIES_SQL_DATA_PROCEDURE_ILLEGAL</code>. This 
	 * reliability is set for BEFORE triggers in the create trigger node. This 
	 * check thus disallows creation of BEFORE triggers which contain calls to 
	 * procedures that modify SQL data in the trigger action statement.  
	 * 
	 * @throws StandardException
	 */
	private void checkReliability() throws StandardException {
		if(getSQLAllowedInProcedure() == RoutineAliasInfo.MODIFIES_SQL_DATA &&
				getCompilerContext().getReliability() == CompilerContext.MODIFIES_SQL_DATA_PROCEDURE_ILLEGAL) 
			throw StandardException.newException(SQLState.LANG_UNSUPPORTED_TRIGGER_PROC);
	}
	
	/**
	 * This method checks the SQL allowed by the called procedure. This method 
	 * should be called only after the procedure has been resolved.
	 * 
	 * @return	SQL allowed by the procedure
	 */
	private short getSQLAllowedInProcedure() {
		RoutineAliasInfo routineInfo = ((MethodCallNode)methodCall.getJavaValueNode()).routineInfo;
		
		// If this method is called before the routine has been resolved, routineInfo will be null 
		if (SanityManager.DEBUG)
			SanityManager.ASSERT((routineInfo != null), "Failed to get routineInfo");

		return routineInfo.getSQLAllowed();
	}
}
