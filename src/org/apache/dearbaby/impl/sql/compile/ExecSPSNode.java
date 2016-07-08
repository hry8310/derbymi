/*

   Derby - Class org.apache.derby.impl.sql.compile.ExecSPSNode

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
import org.apache.derby.iapi.sql.ResultDescription;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.SPSDescriptor;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.iapi.sql.execute.ExecPreparedStatement;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.shared.common.sanity.SanityManager;


/**
 * A ExecSPSNode is the root of a QueryTree 
 * that represents an EXECUTE STATEMENT
 * statement.  It is a tad abnormal.  During a
 * bind, it locates and retrieves the SPSDescriptor
 * for the particular statement.  At generate time,
 * it generates the prepared statement for the 
 * stored prepared statement and returns it (i.e.
 * it effectively replaces itself with the appropriate
 * prepared statement).
 *
 */

class ExecSPSNode extends StatementNode
{
	private TableName			name;
	private SPSDescriptor		spsd;
	private ExecPreparedStatement ps;

	/**
     * Constructor for a ExecSPSNode
	 *
	 * @param newObjectName		The name of the table to be created
     * @param cm                The context manager
	 *
	 * @exception StandardException		Thrown on error
	 */
    ExecSPSNode(TableName newObjectName,
                ContextManager cm) {
        super(cm);
        this.name = newObjectName;
	}
 
	/**
	 * SPSes are atomic if its underlying statement is
	 * atomic.
	 *
	 * @return true if the statement is atomic
	 */	
    @Override
	public boolean isAtomic()
	{

		if (SanityManager.DEBUG)
		{
			SanityManager.ASSERT(ps != null, 
				"statement expected to be bound before calling isAtomic()");
		}

		return ps.isAtomic();
	}

	 
	/**
	 * Make the result description.  Really, we are just
	 * copying it from the stored prepared statement.
	 *
	 * @return	the description
	 */
    @Override
	public ResultDescription makeResultDescription()
	{
		return ps.getResultDescription();
	}

	/**
	 * Get information about this cursor.  For sps,
	 * this is info saved off of the original query
	 * tree (the one for the underlying query).
	 *
	 * @return	the cursor info
	 */
    @Override
	public Object getCursorInfo()
	{
		return ps.getCursorInfo();
	}

	/**
	 * Return a description of the ? parameters for the statement
	 * represented by this query tree.  Just return the params
	 * stored with the prepared statement.
	 *
	 * @return	An array of DataTypeDescriptors describing the
	 *		? parameters for this statement.  It returns null
	 *		if there are no parameters.
	 *
	 * @exception StandardException on error
	 */
    @Override
	public DataTypeDescriptor[]	getParameterTypes() throws StandardException
	{
		return spsd.getParams();
	}


	/**
	 * Create the Constant information that will drive the guts of Execution.
	 * This is assumed to be the first action on this node.
	 *
	 */
    @Override
    public ConstantAction makeConstantAction()
	{
		return ps.getConstantAction();
	}

	/**
	 * We need a savepoint if we will do transactional work.
	 * We'll ask the underlying statement if it needs
	 * a savepoint and pass that back.  We have to do this
	 * after generation because getting the PS now might
	 * cause us to basically do DDL (for a stmt recompilation)
	 * which is explicitly banned during binding.  So the
	 * caller can only call this after generate() has retrieved
	 * the target PS.  
	 *
	 * @return boolean	always true.
	 */
    @Override
	public boolean needsSavepoint()
	{
		if (SanityManager.DEBUG)
		{
			SanityManager.ASSERT(ps != null, 
				"statement expected to be bound before calling needsSavepoint()");
		}

		return ps.needsSavepoint();
	}

	/** @see StatementNode#executeStatementName */
    @Override
	public String executeStatementName()
	{
		return name.getTableName();
	}

	/** @see StatementNode#executeSchemaName */
    @Override
	public String executeSchemaName()
	{
		return name.getSchemaName();
	}

	/**
	 * Get the name of the SPS that is used
	 * to execute this statement.  Only relevant
	 * for an ExecSPSNode -- otherwise, returns null.
	 *
	 * @return the name of the underlying sps
	 */
    @Override
	public String getSPSName()
	{
		return spsd.getQualifiedName();
	}
		
	/*
	 * Shouldn't be called
	 */
	int activationKind()
	{
		if (SanityManager.DEBUG)
		{
			SanityManager.THROWASSERT("activationKind not expected "+
				"to be called for a stored prepared statement");
		}
	   return StatementNode.NEED_PARAM_ACTIVATION;
	}
	/////////////////////////////////////////////////////////////////////
	//
	// PRIVATE
	//
	/////////////////////////////////////////////////////////////////////

		
	/////////////////////////////////////////////////////////////////////
	//
	// MISC
	//
	/////////////////////////////////////////////////////////////////////
    String statementToString()
	{
		return "EXECUTE STATEMENT";
	}

    @Override
    void acceptChildren(Visitor v) throws StandardException {
        super.acceptChildren(v);

        if (name != null) {
            name = (TableName) name.accept(v);
        }
    }
}
