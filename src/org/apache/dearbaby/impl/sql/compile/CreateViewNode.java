/*

   Derby - Class org.apache.derby.impl.sql.compile.CreateViewNode

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

import org.apache.derby.catalog.UUID;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.depend.ProviderInfo;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.impl.sql.execute.ColumnInfo;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A CreateViewNode is the root of a QueryTree that represents a CREATE VIEW
 * statement.
 *
 */

class CreateViewNode extends DDLStatementNode
{
    private ResultColumnList resultColumns;
    private ResultSetNode    queryExpression;
    private String           qeText;
    private int              checkOption;
    private ProviderInfo[]   providerInfos;
    private ColumnInfo[]     colInfos;
	private OrderByList orderByList;
    private ValueNode   offset;
    private ValueNode   fetchFirst;
    private boolean hasJDBClimitClause; // true if using JDBC limit/offset escape syntax

	/**
     * Constructor for a CreateViewNode
	 *
     * @param viewName          The name of the table to be created
	 * @param resultColumns		The column list from the view definition, 
	 *							if specified
	 * @param queryExpression	The query expression for the view
	 * @param checkOption		The type of WITH CHECK OPTION that was specified
	 *							(NONE for now)
	 * @param qeText			The text for the queryExpression
	 * @param orderCols         ORDER BY list
     * @param offset            OFFSET if any, or null
     * @param fetchFirst        FETCH FIRST if any, or null
	 * @param hasJDBClimitClause True if the offset/fetchFirst clauses come from JDBC limit/offset escape syntax
     * @param cm                Context manager
	 * @exception StandardException		Thrown on error
	 */
    CreateViewNode(TableName viewName,
                   ResultColumnList resultColumns,
                   ResultSetNode queryExpression,
                   int checkOption,
                   String qeText,
                   OrderByList orderCols,
                   ValueNode offset,
                   ValueNode fetchFirst,
                   boolean hasJDBClimitClause,
                   ContextManager cm)
		throws StandardException
	{
        super(viewName, cm);
        this.resultColumns = resultColumns;
        this.queryExpression = queryExpression;
        this.checkOption = checkOption;
        this.qeText = qeText.trim();
        this.orderByList = orderCols;
        this.offset = offset;
        this.fetchFirst = fetchFirst;
        this.hasJDBClimitClause = hasJDBClimitClause;
        this.implicitCreateSchema = true;
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
				"checkOption: " + checkOption + "\n" +
				"qeText: " + qeText + "\n";
		}
		else
		{
			return "";
		}
	}

    String statementToString()
	{
		return "CREATE VIEW";
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

			if (resultColumns != null)
			{
				printLabel(depth, "resultColumns: ");
				resultColumns.treePrint(depth + 1);
			}

			printLabel(depth, "queryExpression: ");
			queryExpression.treePrint(depth + 1);
		}
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
		//If create view is part of create statement and the view references SESSION schema tables, then it will
		//get caught in the bind phase of the view and exception will be thrown by the view bind. 
		return (queryExpression.referencesSessionSchema());
	}

	/**
	 * Create the Constant information that will drive the guts of Execution.
	 *
	 * @exception StandardException		Thrown on failure
	 */
    @Override
    public ConstantAction makeConstantAction() throws StandardException
	{
		/* RESOLVE - need to build up dependendencies and store them away through
		 * the constant action.
		 */
		return	getGenericConstantActionFactory().getCreateViewConstantAction(getSchemaDescriptor().getSchemaName(),
											  getRelativeName(),
											  TableDescriptor.VIEW_TYPE,
											  qeText,
											  checkOption,
											  colInfos,
											  providerInfos,
											  (UUID)null); 	// compilation schema, filled
															// in when we create the view
	}

	 

	/*
	 * class interface
	 */

	/**
	  *	Get the parsed query expression (the SELECT statement).
	  *
	  *	@return	the parsed query expression.
	  */
	ResultSetNode	getParsedQueryExpression() { return queryExpression; }


	/*
	 * These methods are used by execution
	 * to get information for storing into
	 * the system catalogs.
	 */


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

		if (queryExpression != null)
		{
			queryExpression = (ResultSetNode)queryExpression.accept(v);
		}
	}

    public OrderByList getOrderByList() {
        return orderByList;
    }

    public ValueNode getOffset() {
        return offset;
    }

    public ValueNode getFetchFirst() {
        return fetchFirst;
    }
    
    public boolean hasJDBClimitClause() { return hasJDBClimitClause; }

}
