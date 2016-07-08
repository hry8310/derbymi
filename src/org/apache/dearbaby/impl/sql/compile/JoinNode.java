/*

   Derby - Class org.apache.derby.impl.sql.compile.JoinNode

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
 
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.compiler.MethodBuilder;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.CompilerContext;
import org.apache.derby.iapi.sql.compile.CostEstimate;
import org.apache.derby.iapi.sql.compile.Optimizable;
import org.apache.derby.iapi.sql.compile.OptimizablePredicate;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.store.access.TransactionController;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.TypeId;
import org.apache.derby.iapi.util.JBitSet;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A JoinNode represents a join result set for either of the basic DML
 * operations: SELECT and INSERT. For INSERT - SELECT, any of the fields in a
 * JoinNode can be used (the JoinNode represents the (join) SELECT statement in
 * the INSERT - SELECT). For INSERT, the resultColumns in the selectList will
 * contain the names of the columns being inserted into or updated.
 *
 */

class JoinNode extends TableOperatorNode {
	/* Join semantics */
	static final int INNERJOIN = 1;
	static final int CROSSJOIN = 2;
	static final int LEFTOUTERJOIN = 3;
	static final int RIGHTOUTERJOIN = 4;
	static final int FULLOUTERJOIN = 5;
	static final int UNIONJOIN = 6;

	/** If this flag is true, this node represents a natural join. */
	private boolean naturalJoin;

	private boolean optimized;

	private PredicateList leftPredicateList;
	private PredicateList rightPredicateList;

	protected boolean flattenableJoin = true;
	List<AggregateNode> aggregates;
	SubqueryList subqueryList;
	ValueNode joinClause;
	boolean joinClauseNormalized;
	PredicateList joinPredicates;
	ResultColumnList usingClause;
	// User provided optimizer overrides
	Properties joinOrderStrategyProperties;

	/**
	 * Constructor for a JoinNode.
	 *
	 * @param leftResult
	 *            The ResultSetNode on the left side of this join
	 * @param rightResult
	 *            The ResultSetNode on the right side of this join
	 * @param onClause
	 *            The ON clause
	 * @param usingClause
	 *            The USING clause
	 * @param selectList
	 *            The result column list for the join
	 * @param tableProperties
	 *            Properties list associated with the table
	 * @param joinOrderStrategyProperties
	 *            User provided optimizer overrides
	 * @param cm
	 *            The context manager
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	JoinNode(ResultSetNode leftResult, ResultSetNode rightResult,
			ValueNode onClause, ResultColumnList usingClause,
			ResultColumnList selectList, Properties tableProperties,
			Properties joinOrderStrategyProperties, ContextManager cm)
			throws StandardException {

		super(leftResult, rightResult, tableProperties, cm);
 
		setResultColumns(selectList);
		this.joinClause = onClause;
		this.joinClauseNormalized = false;
		this.usingClause = usingClause;
		this.joinOrderStrategyProperties = joinOrderStrategyProperties;

		/*
		 * JoinNodes can be generated in the parser or at the end of
		 * optimization. Those generated in the parser do not have resultColumns
		 * yet.
		 */
		if (getResultColumns() != null) {
			/* A longer term assertion */
			if (SanityManager.DEBUG) {
				SanityManager
						.ASSERT((leftResultSet.getReferencedTableMap() != null && rightResultSet
								.getReferencedTableMap() != null)
								|| (leftResultSet.getReferencedTableMap() == null && rightResultSet
										.getReferencedTableMap() == null),
								"left and right referencedTableMaps are expected to either both be non-null or both be null");
			}

			/* Build the referenced table map (left || right) */
			if (leftResultSet.getReferencedTableMap() != null) {
				setReferencedTableMap((JBitSet) leftResultSet
						.getReferencedTableMap().clone());
				getReferencedTableMap().or(
						rightResultSet.getReferencedTableMap());
			}
		}

		this.joinPredicates = new PredicateList(cm);
	}

	/*
	 * Optimizable interface
	 */
 
	/**
	 * @see Optimizable#pushOptPredicate
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	public boolean pushOptPredicate(OptimizablePredicate optimizablePredicate)
			throws StandardException {
		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(optimizablePredicate instanceof Predicate,
					"optimizablePredicate expected to be instanceof Predicate");
			SanityManager
					.ASSERT(!optimizablePredicate.hasSubquery()
							&& !optimizablePredicate.hasMethodCall(),
							"optimizablePredicate either has a subquery or a method call");
		}

		/* Add the matching predicate to the joinPredicates */
		joinPredicates.addPredicate((Predicate) optimizablePredicate);

		/*
		 * Remap all of the ColumnReferences to point to the source of the
		 * values.
		 */
		RemapCRsVisitor rcrv = new RemapCRsVisitor(true);
		((Predicate) optimizablePredicate).getAndNode().accept(rcrv);

		return true;
	}
 

	/**
	 * Some types of joins (e.g. outer joins) will return a different number of
	 * rows than is predicted by optimizeIt() in JoinNode. So, adjust this value
	 * now. This method does nothing for most join types.
	 */
	protected void adjustNumberOfRowsReturned(CostEstimate costEstimate) {
	}

	/**
	 * Return a ResultColumnList with all of the columns in this table. (Used in
	 * expanding '*'s.) NOTE: Since this method is for expanding a "*" in the
	 * SELECT list, ResultColumn.expression will be a ColumnReference.
	 *
	 * @param allTableName
	 *            The qualifier on the "*"
	 *
	 * @return ResultColumnList List of result columns from this table.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ResultColumnList getAllResultColumns(TableName allTableName)
			throws StandardException {
		/*
		 * We need special processing when there is a USING clause. The
		 * resulting table will be the join columns from the outer table
		 * followed by the non-join columns from left side plus the non-join
		 * columns from the right side.
		 */
		if (usingClause == null) {
			return getAllResultColumnsNoUsing(allTableName);
		}

		/*
		 * Get the logical left side of the join. This is where the join columns
		 * come from. (For RIGHT OUTER JOIN, the left is the right and the right
		 * is the left and the JOIN is the NIOJ).
		 */
		ResultSetNode logicalLeftRS = getLogicalLeftResultSet();

		// Get the join columns
		ResultColumnList joinRCL = logicalLeftRS.getAllResultColumns(null)
				.getJoinColumns(usingClause);

		// Get the left and right RCLs
		ResultColumnList leftRCL = leftResultSet
				.getAllResultColumns(allTableName);
		ResultColumnList rightRCL = rightResultSet
				.getAllResultColumns(allTableName);

		/*
		 * Chop the join columns out of the both left and right. Thanks to the
		 * ANSI committee, the join columns do not belong to either table.
		 */
		if (leftRCL != null) {
			leftRCL.removeJoinColumns(usingClause);
		}
		if (rightRCL != null) {
			rightRCL.removeJoinColumns(usingClause);
		}

		/*
		 * If allTableName is null, then we want to return the splicing of the
		 * join columns followed by the non-join columns from the left followed
		 * by the non-join columns from the right. If not, then at most 1 side
		 * should match. NOTE: We need to make sure that the RC's
		 * VirtualColumnIds are correct (1 .. size).
		 */
		if (leftRCL == null) {
			if (rightRCL == null) {
				// Both sides are null. This only happens if allTableName is
				// non-null and doesn't match the table name of any of the
				// join tables (DERBY-4414).
				return null;
			}
			rightRCL.resetVirtualColumnIds();
			return rightRCL;
		} else if (rightRCL == null) {
			// leftRCL is non-null, otherwise the previous leg of the if
			// statement would have been chosen.
			leftRCL.resetVirtualColumnIds();
			return leftRCL;
		} else {
			/*
			 * Both sides are non-null. This should only happen if allTableName
			 * is null.
			 */
			if (SanityManager.DEBUG) {
				if (allTableName != null) {
					SanityManager.THROWASSERT("allTableName (" + allTableName
							+ ") expected to be null");
				}
			}
			joinRCL.destructiveAppend(leftRCL);
			joinRCL.destructiveAppend(rightRCL);
			joinRCL.resetVirtualColumnIds();
			return joinRCL;
		}
	}

	/**
	 * Return a ResultColumnList with all of the columns in this table. (Used in
	 * expanding '*'s.) NOTE: Since this method is for expanding a "*" in the
	 * SELECT list, ResultColumn.expression will be a ColumnReference. NOTE:
	 * This method is handles the case when there is no USING clause. The caller
	 * handles the case when there is a USING clause.
	 *
	 * @param allTableName
	 *            The qualifier on the "*"
	 *
	 * @return ResultColumnList List of result columns from this table.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private ResultColumnList getAllResultColumnsNoUsing(TableName allTableName)
			throws StandardException {
		ResultColumnList leftRCL = leftResultSet
				.getAllResultColumns(allTableName);
		ResultColumnList rightRCL = rightResultSet
				.getAllResultColumns(allTableName);
		/*
		 * If allTableName is null, then we want to return the spliced left and
		 * right RCLs. If not, then at most 1 side should match.
		 */
		if (leftRCL == null) {
			return rightRCL;
		} else if (rightRCL == null) {
			return leftRCL;
		} else {
			/*
			 * Both sides are non-null. This should only happen if allTableName
			 * is null.
			 */
			if (SanityManager.DEBUG) {
				if (allTableName != null) {
					SanityManager.THROWASSERT("allTableName (" + allTableName
							+ ") expected to be null");
				}
			}

			// Return a spliced copy of the 2 lists
			ResultColumnList tempList = new ResultColumnList(
					(getContextManager()));
			tempList.nondestructiveAppend(leftRCL);
			tempList.nondestructiveAppend(rightRCL);
			return tempList;
		}
	}

	/**
	 * Try to find a ResultColumn in the table represented by this FromTable
	 * that matches the name in the given ColumnReference.
	 *
	 * @param columnReference
	 *            The columnReference whose name we're looking for in the given
	 *            table.
	 *
	 * @return A ResultColumn whose expression is the ColumnNode that matches
	 *         the ColumnReference. Returns null if there is no match.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ResultColumn getMatchingColumn(ColumnReference columnReference)
			throws StandardException {
		/*
		 * Get the logical left and right sides of the join. (For RIGHT OUTER
		 * JOIN, the left is the right and the right is the left and the JOIN is
		 * the NIOJ).
		 */
		ResultSetNode logicalLeftRS = getLogicalLeftResultSet();
		ResultSetNode logicalRightRS = getLogicalRightResultSet();
		ResultColumn resultColumn = null;
		ResultColumn rightRC = null;
		ResultColumn usingRC = null;

		ResultColumn leftRC = logicalLeftRS.getMatchingColumn(columnReference);

		if (leftRC != null) {
			resultColumn = leftRC;

			/* Find out if the column is in the using clause */
			if (usingClause != null) {
				usingRC = usingClause.getResultColumn(leftRC.getName());
			}
		}

		/*
		 * We only search on the right if the column isn't in the USING clause.
		 */
		if (usingRC == null) {
			rightRC = logicalRightRS.getMatchingColumn(columnReference);
		} else {
			// If this column represents the join column from the
			// right table for predicate generated for USING/NATURAL
			// of RIGHT OUTER JOIN then flag it such by setting
			// rightOuterJoinUsingClause to true.
			// eg
			// select c from t1 right join t2 using (c)
			// For "using(c)", a join predicate will be created as
			// follows t1.c=t2.c
			// We are talking about column t2.c of the join predicate.
			if (this instanceof HalfOuterJoinNode
					&& ((HalfOuterJoinNode) this).isRightOuterJoin()) {
				leftRC.setRightOuterJoinUsingClause(true);
			}
		}

		if (rightRC != null) {
			/*
			 * We must catch ambiguous column references for joins here, since
			 * FromList only checks for ambiguous references between nodes, not
			 * within a node.
			 */
			if (leftRC != null) {
				throw StandardException.newException(
						SQLState.LANG_AMBIGUOUS_COLUMN_NAME,
						columnReference.getSQLColumnName());
			}

			// All columns on the logical right side of a "half" outer join
			// can contain nulls. The correct nullability is set by
			// bindResultColumns()/buildRCL(). However, if bindResultColumns()
			// has not been called yet, the caller of this method will see
			// the wrong nullability. This problem is logged as DERBY-2916.
			// Until that's fixed, set the nullability here too.
			if (this instanceof HalfOuterJoinNode) {
				rightRC.setNullability(true);
			}

			resultColumn = rightRC;
		}

		/*
		 * Insert will bind the underlying result sets which have tables twice.
		 * On the 2nd bind, resultColumns != null, we must return the RC from
		 * the JoinNode's RCL which is above the RC that we just found above.
		 * (Otherwise, the source for the ColumnReference will be from the wrong
		 * ResultSet at generate().)
		 */
		if (getResultColumns() != null) {
			for (ResultColumn rc : getResultColumns()) {
				VirtualColumnNode vcn = (VirtualColumnNode) rc.getExpression();
				if (resultColumn == vcn.getSourceColumn()) {
					resultColumn = rc;
					break;
				}
			}
		}

		return resultColumn;
	}

	 
	/**
	 * Bind the result columns of this ResultSetNode when there is no base table
	 * to bind them to. This is useful for SELECT statements, where the result
	 * columns get their types from the expressions that live under them.
	 *
	 * @param fromListParam
	 *            FromList to use/append to.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	void bindResultColumns(FromList fromListParam) throws StandardException {
		super.bindResultColumns(fromListParam);

		/* Now we build our RCL */
		buildRCL();

		/*
		 * We cannot bind the join clause until after we've bound our result
		 * columns. This is because the resultColumns from the children are
		 * propagated and merged to create our resultColumns in super.bindRCs().
		 * If we bind the join clause prior to that call, then the
		 * ColumnReferences in the join clause will point to the children's RCLs
		 * at the time that they are bound, but will end up pointing above
		 * themselves, to our resultColumns, after the call to super.bindRCS().
		 */
		deferredBindExpressions(fromListParam);
	}

	/**
	 * Bind the result columns for this ResultSetNode to a base table. This is
	 * useful for INSERT and UPDATE statements, where the result columns get
	 * their types from the table being updated or inserted into. If a result
	 * column list is specified, then the verification that the result column
	 * list does not contain any duplicates will be done when binding them by
	 * name.
	 *
	 * @param targetTableDescriptor
	 *            The TableDescriptor for the table being updated or inserted
	 *            into
	 * @param targetColumnList
	 *            For INSERT statements, the user does not have to supply column
	 *            names (for example, "insert into t values (1,2,3)". When this
	 *            parameter is null, it means that the user did not supply
	 *            column names, and so the binding should be done based on
	 *            order. When it is not null, it means do the binding by name,
	 *            not position.
	 * @param statement
	 *            Calling DMLStatementNode (Insert or Update)
	 * @param fromListParam
	 *            FromList to use/append to.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	void bindResultColumns(TableDescriptor targetTableDescriptor,
			FromVTI targetVTI, ResultColumnList targetColumnList,
			DMLStatementNode statement, FromList fromListParam)
			throws StandardException {
		super.bindResultColumns(targetTableDescriptor, targetVTI,
				targetColumnList, statement, fromListParam);

		/* Now we build our RCL */
		buildRCL();

		/*
		 * We cannot bind the join clause until after we've bound our result
		 * columns. This is because the resultColumns from the children are
		 * propagated and merged to create our resultColumns in super.bindRCs().
		 * If we bind the join clause prior to that call, then the
		 * ColumnReferences in the join clause will point to the children's RCLs
		 * at the time that they are bound, but will end up pointing above
		 * themselves, to our resultColumns, after the call to super.bindRCS().
		 */
		deferredBindExpressions(fromListParam);
	}

	/**
	 * Build the RCL for this node. We propagate the RCLs up from the children
	 * and splice them to form this node's RCL.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */

	private void buildRCL() throws StandardException {
		/*
		 * NOTE - we only need to build this list if it does not already exist.
		 * This can happen in the degenerate case of an insert select with a
		 * join expression in a derived table within the select.
		 */
		if (getResultColumns() != null) {
			return;
		}

		ResultColumnList leftRCL;
		ResultColumnList rightRCL;
		ResultColumnList tmpRCL;

		/*
		 * We get a shallow copy of the left's ResultColumnList and its
		 * ResultColumns. (Copy maintains ResultColumn.expression for now.)
		 */
		setResultColumns(leftResultSet.getResultColumns());
		leftRCL = getResultColumns().copyListAndObjects();
		leftResultSet.setResultColumns(leftRCL);

		/*
		 * Replace ResultColumn.expression with new VirtualColumnNodes in the
		 * ProjectRestrictNode's ResultColumnList. (VirtualColumnNodes include
		 * pointers to source ResultSetNode, this, and source ResultColumn.)
		 */
		getResultColumns().genVirtualColumnNodes(leftResultSet, leftRCL, false);

		/*
		 * * If this is a right outer join, we can get nulls on the left side,*
		 * so change the types of the left result set to be nullable.
		 */
		if (this instanceof HalfOuterJoinNode
				&& ((HalfOuterJoinNode) this).isRightOuterJoin()) {
			getResultColumns().setNullability(true);
		}

		/* Now, repeat the process with the right's RCL */
		tmpRCL = rightResultSet.getResultColumns();
		rightRCL = tmpRCL.copyListAndObjects();
		rightResultSet.setResultColumns(rightRCL);

		/*
		 * Replace ResultColumn.expression with new VirtualColumnNodes in the
		 * ProjectRestrictNode's ResultColumnList. (VirtualColumnNodes include
		 * pointers to source ResultSetNode, this, and source ResultColumn.)
		 */
		tmpRCL.genVirtualColumnNodes(rightResultSet, rightRCL, false);
		tmpRCL.adjustVirtualColumnIds(getResultColumns().size());

		/*
		 * * If this is a left outer join, we can get nulls on the right side,*
		 * so change the types of the right result set to be nullable.
		 */
		if (this instanceof HalfOuterJoinNode
				&& !((HalfOuterJoinNode) this).isRightOuterJoin()) {
			tmpRCL.setNullability(true);
		}

		/*
		 * Now we append the propagated RCL from the right to the one from the
		 * left and call it our own.
		 */
		getResultColumns().nondestructiveAppend(tmpRCL);
	}

	private void deferredBindExpressions(FromList fromListParam)
			throws StandardException {
		ContextManager cm = getContextManager();
		CompilerContext cc = getCompilerContext();

		/* Bind the expressions in the join clause */
		subqueryList = new SubqueryList(cm);
		aggregates = new ArrayList<AggregateNode>();

		/* ON clause */
		if (joinClause != null) {
			joinClause = bindExpression(joinClause, true, true, "ON");
		}
		/* USING clause */
		else if (usingClause != null) {
			/*
			 * Build a join clause from the usingClause, using the exposed names
			 * in the left and right RSNs. For each column in the list, we
			 * generate 2 ColumnReferences, 1 for the left and 1 for the right.
			 * We bind each of these to the appropriate side and build an
			 * equality predicate between the 2. We bind the = and AND nodes by
			 * hand because we have to bind the ColumnReferences a side at a
			 * time. We need to bind the CRs a side at a time to ensure that we
			 * don't find an bogus ambiguous column reference. (Bug 377)
			 */
			joinClause = new BooleanConstantNode(true, cm);

			for (ResultColumn rc : usingClause) {
				BinaryComparisonOperatorNode equalsNode;
				ColumnReference leftCR;
				ColumnReference rightCR;

				/* Create and bind the left CR */
				fromListParam.insertElementAt(leftResultSet, 0);
				leftCR = new ColumnReference(rc.getName(),
						((FromTable) leftResultSet).getTableName(), cm);
				leftCR = (ColumnReference) leftCR.bindExpression(fromListParam,
						subqueryList, aggregates);
				fromListParam.removeElementAt(0);

				/* Create and bind the right CR */
				fromListParam.insertElementAt(rightResultSet, 0);
				rightCR = new ColumnReference(rc.getName(),
						((FromTable) rightResultSet).getTableName(), cm);
				rightCR = (ColumnReference) rightCR.bindExpression(
						fromListParam, subqueryList, aggregates);
				fromListParam.removeElementAt(0);

				/* Create and insert the new = condition */
				equalsNode = new BinaryRelationalOperatorNode(
						BinaryRelationalOperatorNode.K_EQUALS, leftCR, rightCR,
						false, cm);
				equalsNode.bindComparisonOperator();

				// Create a new join clause by ANDing the new = condition and
				// the old join clause.
				AndNode newJoinClause = new AndNode(equalsNode, joinClause, cm);

				newJoinClause.postBindFixup();

				joinClause = newJoinClause;
			}
		}

		if (joinClause != null) {
			/*
			 * If joinClause is a parameter, (where ?), then we assume it will
			 * be a nullable boolean.
			 */
			if (joinClause.requiresTypeFromContext()) {
				joinClause.setType(new DataTypeDescriptor(TypeId.BOOLEAN_ID,
						true));
			}

			/*
			 * * Is the datatype of the JOIN clause BOOLEAN?** NOTE: This test
			 * is not necessary in SQL92 entry level, because* it is
			 * syntactically impossible to have a non-Boolean JOIN clause* in
			 * that level of the standard. But we intend to extend the* language
			 * to allow Boolean user functions in the JOIN clause,* so we need
			 * to test for the error condition.
			 */
			TypeId joinTypeId = joinClause.getTypeId();

			/*
			 * If the where clause is not a built-in type, then generate a bound
			 * conversion tree to a built-in type.
			 */
			if (joinTypeId.userType()) {
				joinClause = joinClause.genSQLJavaSQLTree();
			}

			if (!joinClause.getTypeServices().getTypeId()
					.equals(TypeId.BOOLEAN_ID)) {
				throw StandardException
						.newException(SQLState.LANG_NON_BOOLEAN_JOIN_CLAUSE,
								joinClause.getTypeServices().getTypeId()
										.getSQLTypeName());
			}
		}
	}

	/**
	 * Bind an expression against the child tables of the JoinNode. May update
	 * the subquery and aggregate lists in the JoinNode. Assumes that the
	 * subquery and aggregate lists for the JoinNode have already been created.
	 *
	 * @return the bound expression
	 */
	public ValueNode bindExpression(ValueNode expression, boolean useLeftChild,
			boolean useRightChild, String expressionType)
			throws StandardException {
		ContextManager cm = getContextManager();
		CompilerContext cc = getCompilerContext();

		/*
		 * Create a new fromList with only left and right children before
		 * binding the join clause. Valid column references in the join clause
		 * are limited to columns from the 2 tables being joined. This algorithm
		 * enforces that.
		 */
		FromList fromList = makeFromList(useLeftChild, useRightChild);

		int previousReliability = orReliability(CompilerContext.ON_CLAUSE_RESTRICTION);
		expression = expression.bindExpression(fromList, subqueryList,
				aggregates);
		cc.setReliability(previousReliability);

		// SQL 2003, section 7.7 SR 5
		SelectNode.checkNoWindowFunctions(expression, expressionType);

		/*
		 * * We cannot have aggregates in the ON clause.* In the future, if we
		 * relax this, we'll need* to be able to pass the list of aggregates up*
		 * the tree.
		 */
		if (!aggregates.isEmpty()) {
			throw StandardException
					.newException(SQLState.LANG_NO_AGGREGATES_IN_ON_CLAUSE);
		}

		return expression;
	}

	/** Make a FromList for binding */
	public FromList makeFromList(boolean useLeftChild, boolean useRightChild)
			throws StandardException {
		FromList fromList = new FromList(getOptimizerFactory()
				.doJoinOrderOptimization(), getContextManager());

		if (useLeftChild) {
			fromList.addElement((FromTable) leftResultSet);
		}
		if (useRightChild) {
			fromList.addElement((FromTable) rightResultSet);
		}

		return fromList;
	}

	/**
	 * Generate a result column list with all the column names that appear on
	 * both sides of the join operator. Those are the columns to use as join
	 * columns in a natural join.
	 *
	 * @return RCL with all the common columns
	 * @throws StandardException
	 *             on error
	 */
	private ResultColumnList getCommonColumnsForNaturalJoin()
			throws StandardException {
		ResultColumnList leftRCL = getLeftResultSet().getAllResultColumns(null);
		ResultColumnList rightRCL = getRightResultSet().getAllResultColumns(
				null);

		List<String> columnNames = extractColumnNames(leftRCL);
		columnNames.retainAll(extractColumnNames(rightRCL));

		ResultColumnList commonColumns = new ResultColumnList(
				(getContextManager()));

		for (String name : columnNames) {
			ResultColumn rc = new ResultColumn(name, null, getContextManager());
			commonColumns.addResultColumn(rc);
		}

		return commonColumns;
	}

	/**
	 * Extract all the column names from a result column list.
	 *
	 * @param rcl
	 *            the result column list to extract the names from
	 * @return a list of all the column names in the RCL
	 */
	private static List<String> extractColumnNames(ResultColumnList rcl) {
		ArrayList<String> names = new ArrayList<String>();

		for (ResultColumn rc : rcl) {
			names.add(rc.getName());
		}

		return names;
	}

	/**
	 * Put a ProjectRestrictNode on top of each FromTable in the FromList.
	 * ColumnReferences must continue to point to the same ResultColumn, so that
	 * ResultColumn must percolate up to the new PRN. However, that ResultColumn
	 * will point to a new expression, a VirtualColumnNode, which points to the
	 * FromTable and the ResultColumn that is the source for the
	 * ColumnReference. (The new PRN will have the original of the
	 * ResultColumnList and the ResultColumns from that list. The FromTable will
	 * get shallow copies of the ResultColumnList and its ResultColumns.
	 * ResultColumn.expression will remain at the FromTable, with the PRN
	 * getting a new VirtualColumnNode for each ResultColumn.expression.) We
	 * then project out the non-referenced columns. If there are no referenced
	 * columns, then the PRN's ResultColumnList will consist of a single
	 * ResultColumn whose expression is 1.
	 *
	 * @param numTables
	 *            Number of tables in the DML Statement
	 * @param gbl
	 *            The group by list, if any
	 * @param fromList
	 *            The from list, if any
	 *
	 * @return The generated ProjectRestrictNode atop the original FromTable.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ResultSetNode preprocess(int numTables, GroupByList gbl, FromList fromList)
			throws StandardException {
		ResultSetNode newTreeTop;

		newTreeTop = super.preprocess(numTables, gbl, fromList);

		/*
		 * Put the expression trees in conjunctive normal form. NOTE - This
		 * needs to occur before we preprocess the subqueries because the
		 * subquery transformations assume that any subquery operator negation
		 * has already occurred.
		 */
		if (joinClause != null) {
			normExpressions();

			/* Preprocess any subqueries in the join clause */
			if (subqueryList != null) {
				/*
				 * RESOLVE - In order to flatten a subquery in the ON clause of
				 * an inner join we'd have to pass the various lists from the
				 * outer select through to ResultSetNode.preprocess() and
				 * overload normExpressions in HalfOuterJoinNode. That's not
				 * worth the effort, so we say that the ON clause is not under a
				 * top level AND in normExpressions() to ensure that subqueries
				 * in the ON clause do not get flattened. That allows us to pass
				 * empty lists to joinClause.preprocess() because we know that
				 * no flattening will take place. (Bug #1206)
				 */
				joinClause = joinClause.preprocess(numTables, new FromList(
						getOptimizerFactory().doJoinOrderOptimization(),
						getContextManager()), new SubqueryList(
						getContextManager()), new PredicateList(
						getContextManager()));
			}

			/* Pull apart the expression trees */
			joinPredicates.pullExpressions(numTables, joinClause);
			joinPredicates.categorize();
			joinClause = null;
		}

		return newTreeTop;
	}

	/**
	 * Find the unreferenced result columns and project them out. This is used
	 * in pre-processing joins that are not flattened into the where clause.
	 */
	@Override
	void projectResultColumns() throws StandardException {
		leftResultSet.projectResultColumns();
		rightResultSet.projectResultColumns();
		getResultColumns().pullVirtualIsReferenced();
		super.projectResultColumns();
	}

	/**
	 * Put the expression trees in conjunctive normal form
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	void normExpressions() throws StandardException {
		if (joinClauseNormalized == true)
			return;

		/*
		 * For each expression tree: o Eliminate NOTs (eliminateNots()) o Ensure
		 * that there is an AndNode on top of every top level expression.
		 * (putAndsOnTop()) o Finish the job (changeToCNF())
		 */
		joinClause = joinClause.eliminateNots(false);
		if (SanityManager.DEBUG) {
			if (!(joinClause.verifyEliminateNots())) {
				joinClause.treePrint();
				SanityManager.THROWASSERT("joinClause in invalid form: "
						+ joinClause);
			}
		}
		joinClause = joinClause.putAndsOnTop();
		if (SanityManager.DEBUG) {
			if (!((joinClause instanceof AndNode) && (joinClause
					.verifyPutAndsOnTop()))) {
				joinClause.treePrint();
				SanityManager.THROWASSERT("joinClause in invalid form: "
						+ joinClause);
			}
		}
		/*
		 * RESOLVE - ON clause is temporarily "not under a top top level AND"
		 * until we figure out how to deal with subqueries in the ON clause.
		 * (Bug 1206)
		 */
		joinClause = joinClause.changeToCNF(false);
		if (SanityManager.DEBUG) {
			if (!((joinClause instanceof AndNode) && (joinClause
					.verifyChangeToCNF()))) {
				joinClause.treePrint();
				SanityManager.THROWASSERT("joinClause in invalid form: "
						+ joinClause);
			}
		}

		joinClauseNormalized = true;
	}

	/**
	 * Push expressions down to the first ResultSetNode which can do expression
	 * evaluation and has the same referenced table map. RESOLVE - This means
	 * only pushing down single table expressions to DistinctNodes today. Once
	 * we have a better understanding of how the optimizer will work, we can
	 * push down join clauses.
	 *
	 * @param outerPredicateList
	 *            The PredicateList from the outer RS.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	void pushExpressions(PredicateList outerPredicateList)
			throws StandardException {
		FromTable leftFromTable = (FromTable) leftResultSet;
		FromTable rightFromTable = (FromTable) rightResultSet;

		/*
		 * OuterJoinNodes are responsible for overriding this method since they
		 * have different rules about where predicates can be applied.
		 */
		if (SanityManager.DEBUG) {
			if (this instanceof HalfOuterJoinNode) {
				SanityManager
						.THROWASSERT("JN.pushExpressions() not expected to be called for "
								+ getClass().getName());
			}
		}

		/*
		 * We try to push "pushable" predicates to 1 of 3 places: o Predicates
		 * that only reference tables on the left are pushed to the
		 * leftPredicateList. o Predicates that only reference tables on the
		 * right are pushed to the rightPredicateList. o Predicates which
		 * reference tables on both sides (and no others) are pushed to the
		 * joinPredicates and may be pushed down further during optimization.
		 */
		// Left only
		pushExpressionsToLeft(outerPredicateList);
		leftFromTable.pushExpressions(getLeftPredicateList());
		// Right only
		pushExpressionsToRight(outerPredicateList);
		rightFromTable.pushExpressions(getRightPredicateList());
		// Join predicates
		grabJoinPredicates(outerPredicateList);

		/*
		 * By the time we're done here, both the left and right predicate lists
		 * should be empty because we pushed everything down.
		 */
		if (SanityManager.DEBUG) {
			if (getLeftPredicateList().size() != 0) {
				SanityManager
						.THROWASSERT("getLeftPredicateList().size() expected to be 0, not "
								+ getLeftPredicateList().size());
			}
			if (getRightPredicateList().size() != 0) {
				SanityManager
						.THROWASSERT("getRightPredicateList().size() expected to be 0, not "
								+ getRightPredicateList().size());
			}
		}
	}

	protected void pushExpressionsToLeft(PredicateList outerPredicateList)
			throws StandardException {
		FromTable leftFromTable = (FromTable) leftResultSet;

		JBitSet leftReferencedTableMap = leftFromTable.getReferencedTableMap();

		/*
		 * Build a list of the single table predicates on left result set that
		 * we can push down
		 */
		// Walk outerPredicateList backwards due to possible deletes
		for (int index = outerPredicateList.size() - 1; index >= 0; index--) {
			Predicate predicate = outerPredicateList.elementAt(index);

			if (!predicate.getPushable()) {
				continue;
			}

			JBitSet curBitSet = predicate.getReferencedSet();

			/* Do we have a match? */
			if (leftReferencedTableMap.contains(curBitSet)) {
				/* Add the matching predicate to the push list */
				getLeftPredicateList().addPredicate(predicate);

				/*
				 * Remap all of the ColumnReferences to point to the source of
				 * the values. The tree is something like: PRN1 | JN (this) / \
				 * PRN2 PRN3 | | FBT1 FBT2
				 * 
				 * The ColumnReferences start off pointing to the RCL off of
				 * PRN1. For optimization, we want them to point to the RCL off
				 * of PRN2. In order to do that, we remap them twice here. If
				 * optimization pushes them down to the base table, it will
				 * remap them again.
				 */
				RemapCRsVisitor rcrv = new RemapCRsVisitor(true);
				predicate.getAndNode().accept(rcrv);
				predicate.getAndNode().accept(rcrv);

				/* Remove the matching predicate from the outer list */
				outerPredicateList.removeElementAt(index);
			}
		}
	}

	private void pushExpressionsToRight(PredicateList outerPredicateList)
			throws StandardException {
		FromTable rightFromTable = (FromTable) rightResultSet;

		JBitSet rightReferencedTableMap = rightFromTable
				.getReferencedTableMap();

		/*
		 * Build a list of the single table predicates on right result set that
		 * we can push down
		 */
		// Walk outerPredicateList backwards due to possible deletes
		for (int index = outerPredicateList.size() - 1; index >= 0; index--) {
			Predicate predicate = outerPredicateList.elementAt(index);

			if (!predicate.getPushable()) {
				continue;
			}

			JBitSet curBitSet = predicate.getReferencedSet();

			/* Do we have a match? */
			if (rightReferencedTableMap.contains(curBitSet)) {
				/* Add the matching predicate to the push list */
				getRightPredicateList().addPredicate(predicate);

				/*
				 * Remap all of the ColumnReferences to point to the source of
				 * the values. The tree is something like: PRN1 | JN (this) / \
				 * PRN2 PRN3 | | FBT1 FBT2
				 * 
				 * The ColumnReferences start off pointing to the RCL off of
				 * PRN1. For optimization, we want them to point to the RCL off
				 * of PRN3. In order to do that, we remap them twice here. If
				 * optimization pushes them down to the base table, it will
				 * remap them again.
				 */
				RemapCRsVisitor rcrv = new RemapCRsVisitor(true);
				predicate.getAndNode().accept(rcrv);
				predicate.getAndNode().accept(rcrv);

				/* Remove the matching predicate from the outer list */
				outerPredicateList.removeElementAt(index);
			}
		}
	}

	private void grabJoinPredicates(PredicateList outerPredicateList)
			throws StandardException {
		FromTable leftFromTable = (FromTable) leftResultSet;
		FromTable rightFromTable = (FromTable) rightResultSet;

		JBitSet leftReferencedTableMap = leftFromTable.getReferencedTableMap();
		JBitSet rightReferencedTableMap = rightFromTable
				.getReferencedTableMap();

		/* Build a list of the join predicates that we can push down */
		// Walk outerPredicateList backwards due to possible deletes
		for (int index = outerPredicateList.size() - 1; index >= 0; index--) {
			Predicate predicate = outerPredicateList.elementAt(index);

			if (!predicate.getPushable()) {
				continue;
			}

			JBitSet curBitSet = predicate.getReferencedSet();

			/* Do we have a match? */
			JBitSet innerBitSet = (JBitSet) rightReferencedTableMap.clone();
			innerBitSet.or(leftReferencedTableMap);
			if (innerBitSet.contains(curBitSet)) {
				/* Add the matching predicate to the push list */
				joinPredicates.addPredicate(predicate);

				/*
				 * Remap all of the ColumnReferences to point to the source of
				 * the values. The tree is something like: PRN1 | JN (this) / \
				 * PRN2 PRN3 | | FBT1 FBT2
				 * 
				 * The ColumnReferences start off pointing to the RCL off of
				 * PRN1. For optimization, we want them to point to the RCL off
				 * of PRN2 or PRN3. In order to do that, we remap them twice
				 * here. If optimization pushes them down to the base table, it
				 * will remap them again.
				 */
				RemapCRsVisitor rcrv = new RemapCRsVisitor(true);
				predicate.getAndNode().accept(rcrv);
				predicate.getAndNode().accept(rcrv);

				/* Remove the matching predicate from the outer list */
				outerPredicateList.removeElementAt(index);
			}
		}
	}

	/**
	 * Flatten this JoinNode into the outer query block. The steps in flattening
	 * are: o Mark all ResultColumns as redundant, so that they are
	 * "skipped over" at generate(). o Append the joinPredicates to the outer
	 * list. o Create a FromList from the tables being joined and return that
	 * list so that the caller will merge the 2 lists
	 *
	 * @param rcl
	 *            The RCL from the outer query
	 * @param outerPList
	 *            PredicateList to append wherePredicates to.
	 * @param sql
	 *            The SubqueryList from the outer query
	 * @param gbl
	 *            The group by list, if any
	 * @param havingClause
	 *            The HAVING clause, if any
	 *
	 * @return FromList The fromList from the underlying SelectNode.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	FromList flatten(ResultColumnList rcl, PredicateList outerPList,
			SubqueryList sql, GroupByList gbl, ValueNode havingClause)

	throws StandardException {
		/*
		 * OuterJoinNodes should never get here. (They can be transformed, but
		 * never flattened directly.)
		 */
		if (SanityManager.DEBUG) {
			if (this instanceof HalfOuterJoinNode) {
				SanityManager
						.THROWASSERT("JN.flatten() not expected to be called for "
								+ getClass().getName());
			}
		}

		/*
		 * Build a new FromList composed of left and right children NOTE: We
		 * must call FL.addElement() instead of FL.addFromTable() since there is
		 * no exposed name. (And even if there was, we could care less about
		 * unique exposed name checking here.)
		 */
		FromList fromList = new FromList(getOptimizerFactory()
				.doJoinOrderOptimization(), getContextManager());
		fromList.addElement((FromTable) leftResultSet);
		fromList.addElement((FromTable) rightResultSet);

		/* Mark our RCL as redundant */
		getResultColumns().setRedundant();

		/*
		 * Remap all ColumnReferences from the outer query to this node. (We
		 * replace those ColumnReferences with clones of the matching expression
		 * in the left and right's RCL.
		 */
		rcl.remapColumnReferencesToExpressions();
		outerPList.remapColumnReferencesToExpressions();
		if (gbl != null) {
			gbl.remapColumnReferencesToExpressions();
		}

		if (havingClause != null) {
			havingClause.remapColumnReferencesToExpressions();
		}

		if (joinPredicates.size() > 0) {
			outerPList.destructiveAppend(joinPredicates);
		}

		if (subqueryList != null && subqueryList.size() > 0) {
			sql.destructiveAppend(subqueryList);
		}

		return fromList;
	}

	/**
	 * Currently we don't reordering any outer join w/ inner joins.
	 */
	@Override
	boolean LOJ_reorderable(int numTables) throws StandardException {
		return false;
	}

	/**
	 * Transform any Outer Join into an Inner Join where applicable. (Based on
	 * the existence of a null intolerant predicate on the inner table.)
	 *
	 * @param predicateTree
	 *            The predicate tree for the query block
	 *
	 * @return The new tree top (OuterJoin or InnerJoin).
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	FromTable transformOuterJoins(ValueNode predicateTree, int numTables)
			throws StandardException {
		/* Can't flatten if no predicates in where clause. */
		if (predicateTree == null) {
			// DERBY-4712. Make sure any nested outer joins know we are non
			// flattenable, too, since they inform their left and right sides
			// which, is they are inner joins, a priori think they are
			// flattenable. If left/right result sets are not outer joins,
			// these next two calls are no-ops.
			((FromTable) leftResultSet).transformOuterJoins(null, numTables);
			((FromTable) rightResultSet).transformOuterJoins(null, numTables);
			return this;
		}

		/* See if left or right sides can be transformed */
		leftResultSet = ((FromTable) leftResultSet).transformOuterJoins(
				predicateTree, numTables);
		rightResultSet = ((FromTable) rightResultSet).transformOuterJoins(
				predicateTree, numTables);

		return this;
	}

	 
 

	 

	 
	void oneRowRightSide(ActivationClassBuilder acb, MethodBuilder mb)
			throws StandardException {
		mb.push(rightResultSet.isOneRowResultSet());
		mb.push(rightResultSet.isNotExists()); // join is for NOT EXISTS
	}

	/**
	 * Return the number of arguments to the join result set. This will be
	 * overridden for other types of joins (for example, outer joins).
	 */
	protected int getNumJoinArguments() {
		return 11;
	}

	/**
	 * Generate and add any arguments specifict to outer joins. (Expected to be
	 * overriden, where appropriate, in subclasses.)
	 *
	 * @param acb
	 *            The ActivationClassBuilder
	 * @param mb
	 *            the method the generated code is to go into
	 *
	 *            return The number of args added
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	int addOuterJoinArguments(ActivationClassBuilder acb, MethodBuilder mb)
			throws StandardException {
		return 0;
	}

	/**
	 * Convert the joinType to a string.
	 *
	 * @param joinType
	 *            The joinType as an int.
	 *
	 * @return String The joinType as a String.
	 */
	static String joinTypeToString(int joinType) {
		switch (joinType) {
		case INNERJOIN:
			return "INNER JOIN";

		case CROSSJOIN:
			return "CROSS JOIN";

		case LEFTOUTERJOIN:
			return "LEFT OUTER JOIN";

		case RIGHTOUTERJOIN:
			return "RIGHT OUTER JOIN";

		case FULLOUTERJOIN:
			return "FULL OUTER JOIN";

		case UNIONJOIN:
			return "UNION JOIN";

		default:
			if (SanityManager.DEBUG) {
				SanityManager.ASSERT(false, "Unexpected joinType");
			}
			return null;
		}
	}

	protected PredicateList getLeftPredicateList() throws StandardException {
		if (leftPredicateList == null)
			leftPredicateList = new PredicateList(getContextManager());

		return leftPredicateList;
	}

	protected PredicateList getRightPredicateList() throws StandardException {
		if (rightPredicateList == null)
			rightPredicateList = new PredicateList(getContextManager());

		return rightPredicateList;
	}

	/**
	 * Get the lock mode for the target of an update statement (a delete or
	 * update). The update mode will always be row for CurrentOfNodes. It will
	 * be table if there is no where clause.
	 *
	 * @return The lock mode
	 */
	@Override
	int updateTargetLockMode() {
		/*
		 * Always use row locking if we have a join node. We can only have a
		 * join node if there is a subquery that got flattened, hence there is a
		 * restriction.
		 */
		return TransactionController.MODE_RECORD;
	}

	/**
	 * Mark this node and its children as not being a flattenable join.
	 */
	@Override
	void notFlattenableJoin() {
		flattenableJoin = false;
		leftResultSet.notFlattenableJoin();
		rightResultSet.notFlattenableJoin();
	}

	/**
	 * Is this FromTable a JoinNode which can be flattened into the parents
	 * FromList.
	 *
	 * @return boolean Whether or not this FromTable can be flattened.
	 */
	@Override
	boolean isFlattenableJoinNode() {
		return flattenableJoin;
	}

	/**
	 * Return whether or not the underlying ResultSet tree is ordered on the
	 * specified columns. RESOLVE - This method currently only considers the
	 * outermost table of the query block.
	 *
	 * @param crs
	 *            The specified ColumnReference[]
	 * @param permuteOrdering
	 *            Whether or not the order of the CRs in the array can be
	 *            permuted
	 * @param fbtHolder
	 *            List that is to be filled with the FromBaseTable
	 *
	 * @return Whether the underlying ResultSet tree is ordered on the specified
	 *         column.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	boolean isOrderedOn(ColumnReference[] crs, boolean permuteOrdering,
			List<FromBaseTable> fbtHolder) throws StandardException {
		/*
		 * RESOLVE - easiest thing for now is to only consider the leftmost
		 * child
		 */
		return leftResultSet.isOrderedOn(crs, permuteOrdering, fbtHolder);
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

			if (subqueryList != null) {
				printLabel(depth, "subqueryList: ");
				subqueryList.treePrint(depth + 1);
			}

			if (joinClause != null) {
				printLabel(depth, "joinClause: ");
				joinClause.treePrint(depth + 1);
			}

			if (joinPredicates.size() != 0) {
				printLabel(depth, "joinPredicates: ");
				joinPredicates.treePrint(depth + 1);
			}

			if (usingClause != null) {
				printLabel(depth, "usingClause: ");
				usingClause.treePrint(depth + 1);
			}
		}
	}

	void setSubqueryList(SubqueryList subqueryList) {
		this.subqueryList = subqueryList;
	}

	void setAggregates(List<AggregateNode> aggregates) {
		this.aggregates = aggregates;
	}

	/**
	 * Flag this as a natural join so that an implicit USING clause will be
	 * generated in the bind phase.
	 */
	void setNaturalJoin() {
		naturalJoin = true;
	}

	/**
	 * Return the logical left result set for this qualified join node. (For
	 * RIGHT OUTER JOIN, the left is the right and the right is the left and the
	 * JOIN is the NIOJ).
	 */
	ResultSetNode getLogicalLeftResultSet() {
		return leftResultSet;
	}

	/**
	 * Return the logical right result set for this qualified join node. (For
	 * RIGHT OUTER JOIN, the left is the right and the right is the left and the
	 * JOIN is the NIOJ).
	 */
	ResultSetNode getLogicalRightResultSet() {
		return rightResultSet;
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

		if (getResultColumns() != null) {
			setResultColumns((ResultColumnList) getResultColumns().accept(v));
		}

		if (joinClause != null) {
			joinClause = (ValueNode) joinClause.accept(v);
		}

		if (usingClause != null) {
			usingClause = (ResultColumnList) usingClause.accept(v);
		}

		if (joinPredicates != null) {
			joinPredicates = (PredicateList) joinPredicates.accept(v);
		}
	}

	// This method returns the table references in Join node, and this may be
	// needed for LOJ reordering. For example, we may have the following query:
	// (T JOIN S) LOJ (X LOJ Y)
	// The top most LOJ may be a join betw T and X and thus we can reorder the
	// LOJs. However, as of 10/2002, we don't reorder LOJ mixed with join.
	@Override
	JBitSet LOJgetReferencedTables(int numTables) throws StandardException {
		JBitSet map = leftResultSet.LOJgetReferencedTables(numTables);
		if (map == null)
			return null;
		else
			map.or(rightResultSet.LOJgetReferencedTables(numTables));

		return map;
	}

}
