/*

   Derby - Class org.apache.derby.impl.sql.compile.TableOperatorNode

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

import java.util.Properties;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.Optimizable;
import org.apache.derby.iapi.sql.compile.Optimizer;
import org.apache.derby.iapi.sql.compile.RequiredRowOrdering;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.util.JBitSet;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A TableOperatorNode represents a relational operator like UNION, INTERSECT,
 * JOIN, etc. that takes two tables as parameters and returns a table.  The
 * parameters it takes are represented as ResultSetNodes.
 *
 * Currently, all known table operators are binary operators, so there are no
 * subclasses of this node type called "BinaryTableOperatorNode" and
 * "UnaryTableOperatorNode".
 *
 */

abstract class TableOperatorNode extends FromTable
{
	ResultSetNode	leftResultSet;
	ResultSetNode	rightResultSet;
	Optimizer		leftOptimizer;
	Optimizer		rightOptimizer;
	private boolean 	leftModifyAccessPathsDone;
	private boolean 	rightModifyAccessPathsDone;

    /**
     * Constructor for a TableOperatorNode.
	 *
	 * @param leftResultSet		The ResultSetNode on the left side of this node
	 * @param rightResultSet	The ResultSetNode on the right side of this node
	 * @param tableProperties	Properties list associated with the table
     * @param cm                The context manager
	 *
	 * @exception StandardException		Thrown on error
	 */
    TableOperatorNode(ResultSetNode  leftResultSet,
                      ResultSetNode  rightResultSet,
                      Properties     tableProperties,
                      ContextManager cm)
				throws StandardException
	{
		/* correlationName is always null */
        super(null, tableProperties, cm);
        this.leftResultSet = leftResultSet;
        this.rightResultSet = rightResultSet;
	}

	/**
	 * DERBY-4365
	 * Bind untyped nulls to the types in the given ResultColumnList.
	 * This is used for binding the nulls in row constructors and
	 * table constructors.  
	 *
	 * @param rcl	The ResultColumnList with the types to bind nulls to
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    void bindUntypedNullsToResultColumns(ResultColumnList rcl)
	throws StandardException
	{
		leftResultSet.bindUntypedNullsToResultColumns(rcl);
		rightResultSet.bindUntypedNullsToResultColumns(rcl);
	}


	/** @see Optimizable#verifyProperties 
	 * @exception StandardException		Thrown on error
	 */
    @Override
	public void verifyProperties(DataDictionary dDictionary)
		throws StandardException
	{
		if (leftResultSet instanceof Optimizable)
		{
			((Optimizable) leftResultSet).verifyProperties(dDictionary);
		}
		if (rightResultSet instanceof Optimizable)
		{
			((Optimizable) rightResultSet).verifyProperties(dDictionary);
		}

		super.verifyProperties(dDictionary);
	}

	/**
	 * @see Optimizable#updateBestPlanMap
	 *
	 * Makes a call to add/load/remove the plan mapping for this node,
	 * and then makes the necessary call to recurse on this node's
	 * left and right child, in order to ensure that we've handled
	 * the full plan all the way down this node's subtree. 
	 */
    @Override
	public void updateBestPlanMap(short action,
		Object planKey) throws StandardException
	{
		super.updateBestPlanMap(action, planKey);

		// Now walk the children.  Note that if either child is not
		// an Optimizable and the call to child.getOptimizerImpl()
		// returns null, then that means we haven't tried to optimize
		// the child yet.  So in that case there's nothing to
		// add/load.

		if (leftResultSet instanceof Optimizable)
		{
			((Optimizable)leftResultSet).
				updateBestPlanMap(action, planKey);
		}
		else if (leftResultSet.getOptimizerImpl() != null)
		{
			leftResultSet.getOptimizerImpl().
				updateBestPlanMaps(action, planKey);
		}

		if (rightResultSet instanceof Optimizable)
		{
			((Optimizable)rightResultSet).
				updateBestPlanMap(action, planKey);
		}
		else if (rightResultSet.getOptimizerImpl() != null)
		{
			rightResultSet.getOptimizerImpl().
				updateBestPlanMaps(action, planKey);
		}
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
			return "nestedInParens: " + false + "\n" +
				super.toString();
		}
		else
		{
			return "";
		}
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

			if (leftResultSet != null)
			{
				printLabel(depth, "leftResultSet: ");
				leftResultSet.treePrint(depth + 1);
			}

			if (rightResultSet != null)
			{
				printLabel(depth, "rightResultSet: ");
				rightResultSet.treePrint(depth + 1);
			}
		}
	}

	/**
	 * Get the leftResultSet from this node.
	 *
	 * @return ResultSetNode	The leftResultSet from this node.
	 */
    ResultSetNode getLeftResultSet()
	{
		return leftResultSet;
	}

	/**
	 * Get the rightResultSet from this node.
	 *
	 * @return ResultSetNode	The rightResultSet from this node.
	 */
    ResultSetNode getRightResultSet()
	{
		return rightResultSet;
	}

    ResultSetNode getLeftmostResultSet()
	{
		if (leftResultSet instanceof TableOperatorNode)
		{
			return ((TableOperatorNode) leftResultSet).getLeftmostResultSet();
		}
		else
		{
			return leftResultSet;
		}
	}

    void setLeftmostResultSet(ResultSetNode newLeftResultSet)
	{
		if (leftResultSet instanceof TableOperatorNode)
		{
			((TableOperatorNode) leftResultSet).setLeftmostResultSet(newLeftResultSet);
		}
		else
		{
			this.leftResultSet = newLeftResultSet;
		}
	}

	/**
	 * Set the (query block) level (0-based) for this FromTable.
	 *
	 * @param level		The query block level for this FromTable.
	 */
    @Override
    void setLevel(int level)
	{
		super.setLevel(level);
		if (leftResultSet instanceof FromTable)
		{
			((FromTable) leftResultSet).setLevel(level);
		}
		if (rightResultSet instanceof FromTable)
		{
			((FromTable) rightResultSet).setLevel(level);
		}
	}

	/**
	 * Return the exposed name for this table, which is the name that
	 * can be used to refer to this table in the rest of the query.
	 *
	 * @return	The exposed name for this table.
	 */
    @Override
    String getExposedName()
	{
		return null;
	}

	/**
	 * Mark whether or not this node is nested in parens.  (Useful to parser
	 * since some trees get created left deep and others right deep.)
	 * The resulting state of this cal was never used so its
	 * field was removed to save runtimespace for this node.
	 * Further cleanup can be done including parser changes
	 * if this call is really nor required.
	 *
	 * @param nestedInParens	Whether or not this node is nested in parens.
	 */
    void setNestedInParens(boolean nestedInParens)
	{
	}


	/**
	 * Bind the non VTI tables in this TableOperatorNode. This means getting
	 * their TableDescriptors from the DataDictionary.
	 * We will build an unbound RCL for this node.  This RCL must be
	 * "bound by hand" after the underlying left and right RCLs
	 * are bound.
	 *
	 * @param dataDictionary	The DataDictionary to use for binding
	 * @param fromListParam		FromList to use/append to.
	 *
	 * @return	ResultSetNode		Returns this.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    ResultSetNode bindNonVTITables(DataDictionary dataDictionary,
						  FromList fromListParam) 
							throws StandardException
	{
		leftResultSet = leftResultSet.bindNonVTITables(dataDictionary, fromListParam);
		rightResultSet = rightResultSet.bindNonVTITables(dataDictionary, fromListParam);
		/* Assign the tableNumber */
		if (tableNumber == -1)  // allow re-bind, in which case use old number
			tableNumber = getCompilerContext().getNextTableNumber();

		return this;
	}

	/**
	 * Bind the VTI tables in this TableOperatorNode. This means getting
	 * their TableDescriptors from the DataDictionary.
	 * We will build an unbound RCL for this node.  This RCL must be
	 * "bound by hand" after the underlying left and right RCLs
	 * are bound.
	 *
	 * @param fromListParam		FromList to use/append to.
	 *
	 * @return	ResultSetNode		Returns this.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    ResultSetNode bindVTITables(FromList fromListParam)
							throws StandardException
	{
		leftResultSet = leftResultSet.bindVTITables(fromListParam);
		rightResultSet = rightResultSet.bindVTITables(fromListParam);

		return this;
	}
 

	/**
	 * Check for (and reject) ? parameters directly under the ResultColumns.
	 * This is done for SELECT statements.  For TableOperatorNodes, we
	 * simply pass the check through to the left and right children.
	 *
	 * @exception StandardException		Thrown if a ? parameter found
	 *									directly under a ResultColumn
	 */
    @Override
    void rejectParameters() throws StandardException
	{
		leftResultSet.rejectParameters();
		rightResultSet.rejectParameters();
	}
 
	/**
	 * Bind the result columns of this ResultSetNode when there is no
	 * base table to bind them to.  This is useful for SELECT statements,
	 * where the result columns get their types from the expressions that
	 * live under them.
	 *
	 * @param fromListParam		FromList to use/append to.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    void bindResultColumns(FromList fromListParam)
					throws StandardException
	{
		leftResultSet.bindResultColumns(fromListParam);
		rightResultSet.bindResultColumns(fromListParam);
	}

	/**
	 * Bind the result columns for this ResultSetNode to a base table.
	 * This is useful for INSERT and UPDATE statements, where the
	 * result columns get their types from the table being updated or
	 * inserted into.
	 * If a result column list is specified, then the verification that the 
	 * result column list does not contain any duplicates will be done when
	 * binding them by name.
	 *
	 * @param targetTableDescriptor	The TableDescriptor for the table being
	 *				updated or inserted into
	 * @param targetColumnList	For INSERT statements, the user
	 *					does not have to supply column
	 *					names (for example, "insert into t
	 *					values (1,2,3)".  When this
	 *					parameter is null, it means that
	 *					the user did not supply column
	 *					names, and so the binding should
	 *					be done based on order.  When it
	 *					is not null, it means do the binding
	 *					by name, not position.
	 * @param statement			Calling DMLStatementNode (Insert or Update)
	 * @param fromListParam		FromList to use/append to.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    void bindResultColumns(TableDescriptor targetTableDescriptor,
            FromVTI targetVTI, ResultColumnList targetColumnList,
            DMLStatementNode statement, FromList fromListParam)
				throws StandardException
	{
		leftResultSet.bindResultColumns(targetTableDescriptor,
										targetVTI,
										targetColumnList,
										statement, fromListParam);
		rightResultSet.bindResultColumns(targetTableDescriptor,
										targetVTI,
										targetColumnList,
										statement, fromListParam);
	}

	/** 
	 * Determine whether or not the specified name is an exposed name in
	 * the current query block.
	 *
	 * @param name	The specified name to search for as an exposed name.
	 * @param schemaName	Schema name, if non-null.
	 * @param exactMatch	Whether or not we need an exact match on specified schema and table
	 *						names or match on table id.
	 *
	 * @return The FromTable, if any, with the exposed name.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    FromTable getFromTableByName(String name, String schemaName, boolean exactMatch)
		throws StandardException
	{
		FromTable result = leftResultSet.getFromTableByName(name, schemaName, exactMatch);

		/* We search both sides for a TableOperatorNode (join nodes)
		 * but only the left side for a UnionNode.
		 */
		if (result == null)
		{
			result = rightResultSet.getFromTableByName(name, schemaName, exactMatch);
		}
		return result;
	}

	/** 
	 * Put a ProjectRestrictNode on top of each FromTable in the FromList.
	 * ColumnReferences must continue to point to the same ResultColumn, so
	 * that ResultColumn must percolate up to the new PRN.  However,
	 * that ResultColumn will point to a new expression, a VirtualColumnNode, 
	 * which points to the FromTable and the ResultColumn that is the source for
	 * the ColumnReference.  
	 * (The new PRN will have the original of the ResultColumnList and
	 * the ResultColumns from that list.  The FromTable will get shallow copies
	 * of the ResultColumnList and its ResultColumns.  ResultColumn.expression
	 * will remain at the FromTable, with the PRN getting a new 
	 * VirtualColumnNode for each ResultColumn.expression.)
	 * We then project out the non-referenced columns.  If there are no referenced
	 * columns, then the PRN's ResultColumnList will consist of a single ResultColumn
	 * whose expression is 1.
	 *
	 * @param numTables			Number of tables in the DML Statement
	 * @param gbl				The group by list, if any
	 * @param fromList			The from list, if any
	 *
	 * @return The generated ProjectRestrictNode atop the original FromTable.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    ResultSetNode preprocess(int numTables,
									GroupByList gbl,
									FromList fromList)
								throws StandardException
	{
		leftResultSet = leftResultSet.preprocess(numTables, gbl, fromList);
		/* If leftResultSet is a FromSubquery, then we must explicitly extract
		 * out the subquery (flatten it).  (SelectNodes have their own
		 * method of flattening them.
		 */
		if (leftResultSet instanceof FromSubquery)
		{
			leftResultSet = ((FromSubquery) leftResultSet).extractSubquery(numTables);
		}
		rightResultSet = rightResultSet.preprocess(numTables, gbl, fromList);
		/* If rightResultSet is a FromSubquery, then we must explicitly extract
		 * out the subquery (flatten it).  (SelectNodes have their own
		 * method of flattening them.
		 */
		if (rightResultSet instanceof FromSubquery)
		{
			rightResultSet = ((FromSubquery) rightResultSet).extractSubquery(numTables);
		}

		/* Build the referenced table map (left || right) */
		setReferencedTableMap( (JBitSet) leftResultSet.getReferencedTableMap().clone() );
        getReferencedTableMap().or(rightResultSet.getReferencedTableMap());
		getReferencedTableMap().set(tableNumber);

		/* Only generate a PRN if this node is not a flattenable join node. */
		if (isFlattenableJoinNode())
		{
			return this;
		}
		else
		{
			/* Project out any unreferenced RCs before we generate the PRN.
			 * NOTE: We have to do this at the end of preprocess since it has to 
			 * be from the bottom up.  We can't do it until the join expression is 
			 * bound, since the join expression may contain column references that
			 * are not referenced anywhere else above us.
			 */
            projectResultColumns();
			return genProjectRestrict(numTables);
		}
	}

    /**
     * Find the unreferenced result columns and project them out. This is used in pre-processing joins
     * that are not flattened into the where clause.
     */
    @Override
    void projectResultColumns() throws StandardException
    {
        getResultColumns().doProjection();
    }
    
    /**
     * Set the referenced columns in the column list if it may not be correct.
     */
    void setReferencedColumns()
    {}
    
 
	/**
	 * Search to see if a query references the specifed table name.
	 *
	 * @param name		Table name (String) to search for.
	 * @param baseTable	Whether or not name is for a base table
	 *
	 * @return	true if found, else false
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    boolean referencesTarget(String name, boolean baseTable)
		throws StandardException
	{
		return leftResultSet.referencesTarget(name, baseTable) ||
			   rightResultSet.referencesTarget(name, baseTable);
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
		return leftResultSet.referencesSessionSchema() ||
			   rightResultSet.referencesSessionSchema();
	}

	 

	/**
	 * Decrement (query block) level (0-based) for 
	 * all of the tables in this ResultSet tree.
	 * This is useful when flattening a subquery.
	 *
	 * @param decrement	The amount to decrement by.
	 */
    @Override
	void decrementLevel(int decrement)
	{
		leftResultSet.decrementLevel(decrement);
		rightResultSet.decrementLevel(decrement);
	}

	/**
	 * @see ResultSetNode#adjustForSortElimination
	 */
    @Override
	void adjustForSortElimination()
	{
		leftResultSet.adjustForSortElimination();
		rightResultSet.adjustForSortElimination();
	}

	/**
	 * @see ResultSetNode#adjustForSortElimination
	 */
    @Override
	void adjustForSortElimination(RequiredRowOrdering rowOrdering)
		throws StandardException
	{
		leftResultSet.adjustForSortElimination(rowOrdering);
		rightResultSet.adjustForSortElimination(rowOrdering);
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

		if (leftResultSet != null)
		{
			leftResultSet = (ResultSetNode)leftResultSet.accept(v);
		}
		if (rightResultSet != null)
		{
			rightResultSet = (ResultSetNode)rightResultSet.accept(v);
		}
	}

	/** 
	 * apparently something special needs to be done for me....
	 */
    @Override
    boolean needsSpecialRCLBinding()
	{
		return true;
	}
}
