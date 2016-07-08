/*

   Derby - Class org.apache.derby.impl.sql.compile.DMLStatementNode

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

package org.apache.dearbaby.impl.sql.compile;

import java.util.List;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.ResultColumnDescriptor;
import org.apache.derby.iapi.sql.ResultDescription;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.conn.Authorizer;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A DMLStatementNode represents any type of DML statement: a cursor
 * declaration, an INSERT statement, and UPDATE statement, or a DELETE
 * statement. All DML statements have result sets, but they do different things
 * with them. A SELECT statement sends its result set to the client, an INSERT
 * statement inserts its result set into a table, a DELETE statement deletes
 * from a table the rows corresponding to the rows in its result set, and an
 * UPDATE statement updates the rows in a base table corresponding to the rows
 * in its result set.
 *
 */

abstract class DMLStatementNode extends StatementNode {

	/**
	 * The result set is the rows that result from running the statement. What
	 * this means for SELECT statements is fairly obvious. For a DELETE, there
	 * is one result column representing the key of the row to be deleted (most
	 * likely, the location of the row in the underlying heap). For an UPDATE,
	 * the row consists of the key of the row to be updated plus the updated
	 * columns. For an INSERT, the row consists of the new column values to be
	 * inserted, with no key (the system generates a key).
	 *
	 * The parser doesn't know anything about keys, so the columns representing
	 * the keys will be added after parsing (perhaps in the binding phase?).
	 *
	 */
	ResultSetNode resultSet;

	DMLStatementNode(ResultSetNode resultSet, ContextManager cm) {
		super(cm);
		this.resultSet = resultSet;
	}

	/**
	 * Prints the sub-nodes of this object. See QueryTreeNode.java for how tree
	 * printing is supposed to work.
	 *
	 * @param depth
	 *            The depth of this node in the tree
	 */
	@Override
	void printSubNodes(int depth) {
		if (SanityManager.DEBUG) {
			super.printSubNodes(depth);
			if (resultSet != null) {
				printLabel(depth, "resultSet: ");
				resultSet.treePrint(depth + 1);
			}
		}
	}

	/**
	 * Get the ResultSetNode from this DML Statement. (Useful for view
	 * resolution after parsing the view definition.)
	 *
	 * @return ResultSetNode The ResultSetNode from this DMLStatementNode.
	 */
	ResultSetNode getResultSetNode() {
		return resultSet;
	}

	/**
	 * Bind this DMLStatementNode. This means looking up tables and columns and
	 * getting their types, and figuring out the result types of all
	 * expressions, as well as doing view resolution, permissions checking, etc.
	 *
	 * @param dataDictionary
	 *            The DataDictionary to use to look up columns, tables, etc.
	 *
	 * @return The bound query tree
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */

	QueryTreeNode bind(DataDictionary dataDictionary) throws StandardException {
		// We just need select privilege on most columns and tables
		getCompilerContext().pushCurrentPrivType(getPrivType());
		try {
			/*
			 * * Bind the tables before binding the expressions, so we can* use
			 * the results of table binding to look up columns.
			 */
			bindTables(dataDictionary);

			/* Bind the expressions */
			bindExpressions();
		} finally {
			getCompilerContext().popCurrentPrivType();
		}

		return this;
	}

 

	/**
	 * Bind the tables in this DML statement.
	 *
	 * @param dataDictionary
	 *            The data dictionary to use to look up the tables
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */

	protected void bindTables(DataDictionary dataDictionary)
			throws StandardException {
		/*
		 * Bind the tables in the resultSet (DMLStatementNode is above all
		 * ResultSetNodes, so table numbering will begin at 0.) In case of
		 * referential action on delete , the table numbers can be > 0 because
		 * the nodes are create for dependent tables also in the the same
		 * context.
		 */
		boolean doJOO = false;
		ContextManager cm = getContextManager();
		resultSet = resultSet.bindNonVTITables(dataDictionary, new FromList(
				doJOO, cm));
		resultSet = resultSet.bindVTITables(new FromList(doJOO, cm));
	}

	/**
	 * Bind the expressions in this DML statement.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */

	protected void bindExpressions() throws StandardException { }

	 

	/**
	 * Returns the type of activation this class generates.
	 * 
	 * @return either (NEED_ROW_ACTIVATION | NEED_PARAM_ACTIVATION) or
	 *         (NEED_ROW_ACTIVATION) depending on params
	 *
	 */
	int activationKind() {
		List<ParameterNode> parameterList = getCompilerContext()
				.getParameterList();
		/*
		 * * We need rows for all types of DML activations. We need parameters*
		 * only for those that have parameters.
		 */
		if (parameterList != null && !parameterList.isEmpty()) {
			return StatementNode.NEED_PARAM_ACTIVATION;
		} else {
			return StatementNode.NEED_ROW_ACTIVATION;
		}
	}
 

	/**
	 * Make a ResultDescription for use in a PreparedStatement.
	 *
	 * ResultDescriptions are visible to JDBC only for cursor statements. For
	 * other types of statements, they are only used internally to get
	 * descriptions of the base tables being affected. For example, for an
	 * INSERT statement, the ResultDescription describes the rows in the table
	 * being inserted into, which is useful when the values being inserted are
	 * of a different type or length than the columns in the base table.
	 *
	 * @return A ResultDescription for this DML statement
	 */
	@Override
	public ResultDescription makeResultDescription() {
		ResultColumnDescriptor[] colDescs = resultSet.makeResultDescriptors();
		String statementType = statementToString();

		return getExecutionFactory().getResultDescription(colDescs,
				statementType);
	}

	/**
	 * Generate the code to create the ParameterValueSet, if necessary, when
	 * constructing the activation. Also generate the code to call a method that
	 * will throw an exception if we try to execute without all the parameters
	 * being set.
	 * 
	 * @param acb
	 *            The ActivationClassBuilder for the class we're building
	 */

	void generateParameterValueSet(ActivationClassBuilder acb)
			throws StandardException {
		List<ParameterNode> parameterList = getCompilerContext()
				.getParameterList();
		int numberOfParameters = (parameterList == null) ? 0 : parameterList
				.size();

		if (numberOfParameters <= 0)
			return;

		ParameterNode.generateParameterValueSet(acb, numberOfParameters,
				parameterList);
	}

	/**
	 * A read statement is atomic (DMLMod overrides us) if there are no work
	 * units, and no SELECT nodes, or if its SELECT nodes are all arguments to a
	 * function. This is admittedly a bit simplistic, what if someone has:
	 * 
	 * <pre>
	 * 	VALUES myfunc(SELECT max(c.commitFunc()) FROM T)
	 * </pre>
	 * 
	 * but we aren't going too far out of our way to catch every possible wierd
	 * case. We basically want to be permissive w/o allowing someone to
	 * partially commit a write.
	 * 
	 * @return true if the statement is atomic
	 *
	 * @exception StandardException
	 *                on error
	 */
	@Override
	public boolean isAtomic() throws StandardException {
		/*
		 * * If we have a FromBaseTable then we have* a SELECT, so we want to
		 * consider ourselves* atomic. Don't drill below StaticMethodCallNodes*
		 * to allow a SELECT in an argument to a method* call that can be
		 * atomic.
		 */
		HasNodeVisitor visitor = new HasNodeVisitor(FromBaseTable.class,
				StaticMethodCallNode.class);
 
		this.accept(visitor);
	 
		if (visitor.hasNode()) {
			return true;
		}

		return false;
	}

	/**
	 * Accept the visitor for all visitable children of this node.
	 * 
	 * @param v
	 *            the visitor
	 *
	 * @exception StandardException
	 *                on error
	 */
	@Override
	void acceptChildren(Visitor v) throws StandardException {
		super.acceptChildren(v);

		if (resultSet != null) {
			resultSet = (ResultSetNode) resultSet.accept(v);
		}
	}

	/**
	 * Return default privilege needed for this node. Other DML nodes can
	 * override this method to set their own default privilege.
	 *
	 * @return true if the statement is atomic
	 */
	int getPrivType() {
		return Authorizer.SELECT_PRIV;
	}
}
