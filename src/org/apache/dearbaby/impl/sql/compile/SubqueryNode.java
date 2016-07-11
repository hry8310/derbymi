/*

   Derby - Class org.apache.derby.impl.sql.compile.SubqueryNode

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

import org.apache.dearbaby.query.RowColumn;
import org.apache.dearbaby.util.ColCompare;
import org.apache.dearbaby.util.QueryUtil;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.Visitor; 
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.util.JBitSet;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A SubqueryNode represents a subquery. Subqueries return values to their outer
 * queries. An quantified subquery is one that appears under a quantified
 * operator (like IN or EXISTS) - quantified subqueries can return more than one
 * value per invocation. An expression subquery is one that is not directly
 * under a quantified operator - expression subqueries are allowed to return at
 * most one value per invocation (returning no value is considered to be
 * equivalent to returning NULL).
 *
 * There are a large number of subquery types. Because of the large number of
 * types, and the large amount of shared code, we have decided to have 1
 * SubqueryNode without any subclasses. The subquery type (and operator) is
 * encoded in the subqueryType field.
 *
 * The query optimizer is responsible for optimizing subqueries, and also for
 * transforming them so that code can be generated for them. The optimizer may
 * eliminate some subqueries by transforming them into joins, or it may change
 * the internal form of a subquery (for example, transforming 'where x in
 * (select y from z where ...)' into 'where (select true from z where x = y and
 * ...)').
 *
 * Note that aggregates present some additional issues. A transformation such
 * as:
 * <UL>
 * where x in (SELECT <I>expression</I> FROM z)
 * </UL>
 * has to be treated specially if <I>expression</I> has an aggregate. We change
 * it to:
 * <UL>
 * where x = (SELECT true FROM (SELECT MAX(x) FROM z) WHERE SQLCOL1 = y)
 * </UL>
 *
 */

public class SubqueryNode extends ValueNode {
	/*
	 * * This must be a single-column result set. If the subquery is* not
	 * quantified, it must also be a single-row result set - that is,*
	 * expression subqueries are allowed to return only a single value* per
	 * invocation.* NOTE: SubqueryNodes are used as an intermediate step within
	 * the parser* for building a derived table. Derived tables can be
	 * multi-column and* multi-table.
	 */
	ResultSetNode resultSet;

	/* Type of this subquery */
	int subqueryType;

	QueryTreeNode parentNode;
	/*
	 * Whether or not this subquery is immediately under a top level AndNode.
	 * (Important for subquery flattening.)
	 */
	boolean underTopAndNode;

	/* Whether or not we've been preprocessed. (Only do the work once.) */
	boolean preprocessed;

	/* Whether or not this subquery began life as a distinct expression subquery */
	boolean distinctExpression;

	/* Whether or not this subquery began life as a subquery in a where clause */
	boolean whereSubquery;

	/*
	 * Since we do not have separate subquery operator nodes, the type of the
	 * subquery is stored in the subqueryType field. Most subquery types take a
	 * left operand (except for expression and exists). We could either add a
	 * leftOperand field here or subclass SubqueryNode for those types that take
	 * a left operand. We have decided to add the left operand here for now.
	 */
	ValueNode leftOperand;
	boolean pushedNewPredicate;

	/**
	 * is this subquery part of a having clause. We need to know this so we can
	 * avoid flattening.
	 */
	boolean havingSubquery = false;

	/*
	 * Expression subqueries on the right side of a BinaryComparisonOperatorNode
	 * will get passed a pointer to that node prior to preprocess(). This allows
	 * us to replace the entire comparison, if we want to, when flattening.
	 */
	BinaryComparisonOperatorNode parentComparisonOperator;

	/*
	 * Private fields (all references via private methods) - We reuse true
	 * BooleanConstantNodes within this class, creating them on the first
	 * reference.
	 */
	private BooleanConstantNode trueNode;
	/* Reuse generated code where possible */
	// private Expression genResult;

	/* Subquery # for this subquery */
	private int subqueryNumber = -1;

	/* ResultSet # for the point of attachment for this subquery */
	private int pointOfAttachment = -1;

	/*
	 * * Indicate whether we found a correlation or not.* And track whether we
	 * have checked yet.
	 */
	private boolean foundCorrelation;
	private boolean doneCorrelationCheck;

	/*
	 * * Indicate whether we found an invariant node* below us or not. And track
	 * whether we have* checked yet.
	 */
	private boolean foundVariant;
	private boolean doneInvariantCheck;

	private OrderByList orderByList;
	private ValueNode offset;
	private ValueNode fetchFirst;
	private boolean hasJDBClimitClause; // true if using JDBC limit/offset
										// escape syntax

	/*
	 * Subquery types. NOTE: FROM_SUBQUERY only exists for a brief second in the
	 * parser. It should never appear in a query tree. NOTE: NOT EXISTS and NOT
	 * IN subquery types do not exist prior to NOT elimination during
	 * preprocessing. Prior to that, there is a separate NotNode above the
	 * SubqueryNode in the tree.
	 */
	final static int NOTIMPLEMENTED_SUBQUERY = -1;
	final static int FROM_SUBQUERY = 0;
	final static int IN_SUBQUERY = 1;
	final static int NOT_IN_SUBQUERY = 2;
	final static int EQ_ANY_SUBQUERY = 3;
	final static int EQ_ALL_SUBQUERY = 4;
	final static int NE_ANY_SUBQUERY = 5;
	final static int NE_ALL_SUBQUERY = 6;
	final static int GT_ANY_SUBQUERY = 7;
	final static int GT_ALL_SUBQUERY = 8;
	final static int GE_ANY_SUBQUERY = 9;
	final static int GE_ALL_SUBQUERY = 10;
	final static int LT_ANY_SUBQUERY = 11;
	final static int LT_ALL_SUBQUERY = 12;
	final static int LE_ANY_SUBQUERY = 13;
	final static int LE_ALL_SUBQUERY = 14;
	final static int EXISTS_SUBQUERY = 15;
	final static int NOT_EXISTS_SUBQUERY = 16;
	final static int EXPRESSION_SUBQUERY = 17;

	/**
	 * Constructor.
	 *
	 * @param resultSet
	 *            The ResultSetNode for the subquery
	 * @param subqueryType
	 *            The type of the subquery
	 * @param leftOperand
	 *            The left operand, if any, of the subquery
	 * @param orderCols
	 *            ORDER BY list
	 * @param offset
	 *            OFFSET n ROWS
	 * @param fetchFirst
	 *            FETCH FIRST n ROWS ONLY
	 * @param hasJDBClimitClause
	 *            True if the offset/fetchFirst clauses come from JDBC
	 *            limit/offset escape syntax
	 * @param cm
	 *            Context Manager
	 */

	SubqueryNode(ResultSetNode resultSet, int subqueryType,
			ValueNode leftOperand, OrderByList orderCols, ValueNode offset,
			ValueNode fetchFirst, boolean hasJDBClimitClause, ContextManager cm) {
		super(cm);
		this.resultSet = resultSet;
		this.subqueryType = subqueryType;
		this.orderByList = orderCols;
		this.offset = offset;
		this.fetchFirst = fetchFirst;
		this.hasJDBClimitClause = hasJDBClimitClause;

		/*
		 * Subqueries are presumed not to be under a top level AndNode by
		 * default. This is because expression normalization only recurses under
		 * Ands and Ors, not under comparison operators, method calls, built-in
		 * functions, etc.
		 */
		underTopAndNode = false;
		this.leftOperand = leftOperand;
	}

	/**
	 * Convert this object to a String. See comments in QueryTreeNode.java for
	 * how this should be done for tree printing.
	 *
	 * @return This object as a String
	 */
	@Override
	public String toString() {
		if (SanityManager.DEBUG) {
			return "subqueryType: " + subqueryType + "\n" + "underTopAndNode: "
					+ underTopAndNode + "\n" + "subqueryNumber: "
					+ subqueryNumber + "\n" + "pointOfAttachment: "
					+ pointOfAttachment + "\n" + "preprocessed: "
					+ preprocessed + "\n" + "distinctExpression: "
					+ distinctExpression + "\n" + super.toString();
		} else {
			return "";
		}
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

			if (leftOperand != null) {
				printLabel(depth, "leftOperand: ");
				leftOperand.treePrint(depth + 1);
			}

			if (orderByList != null) {
				printLabel(depth, "orderByList: ");
				orderByList.treePrint(depth + 1);
			}

			if (offset != null) {
				printLabel(depth, "offset: ");
				offset.treePrint(depth + 1);
			}

			if (fetchFirst != null) {
				printLabel(depth, "fetchFirst: ");
				fetchFirst.treePrint(depth + 1);
			}
		}
	}

	/**
	 * Return the resultSet for this SubqueryNode.
	 *
	 * @return ResultSetNode underlying this SubqueryNode.
	 */
	ResultSetNode getResultSet() {
		return resultSet;
	}

	/**
	 * Return the type of this subquery.
	 *
	 * @return int Type of this subquery.
	 */
	int getSubqueryType() {
		return subqueryType;
	}

	/**
	 * Set the type of this subquery.
	 *
	 * @param subqueryType
	 *            of this subquery.
	 */
	void setSubqueryType(int subqueryType) {
		this.subqueryType = subqueryType;
	}

	/**
	 * Set the point of attachment of this subquery.
	 *
	 * @param pointOfAttachment
	 *            The point of attachment of this subquery.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	void setPointOfAttachment(int pointOfAttachment) throws StandardException {
		/*
		 * Materialized subqueries always keep their point of attachment as -1.
		 */
		if (!isMaterializable()) {
			this.pointOfAttachment = pointOfAttachment;
		}
	}

	/**
	 * Return whether or not this subquery is immediately under a top level
	 * AndNode.
	 *
	 * @return boolean Whether or not this subquery is immediately under a top
	 *         level AndNode.
	 */
	boolean getUnderTopAndNode() {
		return underTopAndNode;
	}

	/**
	 * Get the ResultSet # for the point of attachment for this SubqueryNode.
	 *
	 * @return int The ResultSet # for the point of attachment
	 */
	int getPointOfAttachment() {
		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(pointOfAttachment >= 0,
					"pointOfAttachment expected to be >= 0");
		}
		return pointOfAttachment;
	}

	/**
	 * Get whether or not this SubqueryNode has already been preprocessed.
	 * 
	 * @return Whether or not this SubqueryNode has already been preprocessed.
	 */
	boolean getPreprocessed() {
		return preprocessed;
	}

	/**
	 * Set the parent BCON. Useful when considering flattening expression
	 * subqueries.
	 *
	 * @param parent
	 *            The parent BCON.
	 */
	void setParentComparisonOperator(BinaryComparisonOperatorNode parent) {
		parentComparisonOperator = parent;
	}

	@Override
	public boolean referencesSessionSchema() throws StandardException {
		return resultSet.referencesSessionSchema();
	}

	/**
	 * Remap all ColumnReferences in this tree to be clones of the underlying
	 * expression.
	 *
	 * @return ValueNode The remapped expression tree.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ValueNode remapColumnReferencesToExpressions() throws StandardException {
		/*
		 * We need to remap both the SELECT and Predicate lists since there may
		 * be correlated columns in either of them.
		 */
		if (resultSet instanceof SelectNode) {
			ResultColumnList selectRCL = resultSet.getResultColumns();
			SelectNode select = (SelectNode) resultSet;
			PredicateList selectPL = select.getWherePredicates();

			if (SanityManager.DEBUG) {
				SanityManager.ASSERT(selectPL != null,
						"selectPL expected to be non-null");
			}
			selectRCL.remapColumnReferencesToExpressions();
			selectPL.remapColumnReferencesToExpressions();
		}
		return this;
	}

	 
	/**
	 * Preprocess an expression tree. We do a number of transformations here
	 * (including subqueries, IN lists, LIKE and BETWEEN) plus subquery
	 * flattening. NOTE: This is done before the outer ResultSetNode is
	 * preprocessed.
	 *
	 * @param numTables
	 *            Number of tables in the DML Statement
	 * @param outerFromList
	 *            FromList from outer query block
	 * @param outerSubqueryList
	 *            SubqueryList from outer query block
	 * @param outerPredicateList
	 *            PredicateList from outer query block
	 *
	 * @return The modified expression
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ValueNode preprocess(int numTables, FromList outerFromList,
			SubqueryList outerSubqueryList, PredicateList outerPredicateList)
			throws StandardException {
		/*
		 * Only preprocess this node once. We may get called multiple times due
		 * to tree transformations.
		 */
		if (preprocessed) {
			return this;
		}
		preprocessed = true;

		boolean flattenable;
		ValueNode topNode = this;

		final boolean haveOrderBy; // need to remember for flattening decision

		// Push the order by list down to the ResultSet
		if (orderByList != null) {
			haveOrderBy = true;
			// If we have more than 1 ORDERBY columns, we may be able to
			// remove duplicate columns, e.g., "ORDER BY 1, 1, 2".
			if (orderByList.size() > 1) {
				orderByList.removeDupColumns();
			}

			resultSet.pushOrderByList(orderByList);
			orderByList = null;
		} else {
			haveOrderBy = false;
		}

		resultSet = resultSet.preprocess(numTables, null, (FromList) null);

		if (leftOperand != null) {
			leftOperand = leftOperand.preprocess(numTables, outerFromList,
					outerSubqueryList, outerPredicateList);
		}

		// Eliminate any unnecessary DISTINCTs
		if (resultSet instanceof SelectNode) {
			if (((SelectNode) resultSet).hasDistinct()) {
				((SelectNode) resultSet).clearDistinct();
				/*
				 * We need to remember to check for single unique value at
				 * execution time for expression subqueries.
				 */
				if (subqueryType == EXPRESSION_SUBQUERY) {
					distinctExpression = true;
				}
			}
		}

		/*
		 * Lame transformation - For IN/ANY subqueries, if result set is
		 * guaranteed to return at most 1 row and it is not correlated then
		 * convert the subquery into the matching expression subquery type. For
		 * example: c1 in (select min(c1) from t2) becomes: c1 = (select min(c1)
		 * from t2) (This actually showed up in an app that a potential customer
		 * was porting from SQL Server.) The transformed query can then be
		 * flattened if appropriate.
		 */
		if ((isIN() || isANY()) && resultSet.returnsAtMostOneRow()) {
			if (!hasCorrelatedCRs()) {
				changeToCorrespondingExpressionType();
			}
		}

		/*
		 * NOTE: Flattening occurs before the pushing of the predicate, since
		 * the pushing will add a node above the SubqueryNode.
		 */

		/*
		 * Values subquery is flattenable if: o It is not under an OR. o It is
		 * not a subquery in a having clause (DERBY-3257) o It is an expression
		 * subquery on the right side of a BinaryComparisonOperatorNode. o
		 * Either a) it does not appear within a WHERE clause, or b) it appears
		 * within a WHERE clause but does not itself contain a WHERE clause with
		 * other subqueries in it. (DERBY-3301)
		 */
		flattenable = (resultSet instanceof RowResultSetNode)
				&& underTopAndNode && !havingSubquery && !haveOrderBy
				&& offset == null && fetchFirst == null
				&& !isWhereExistsAnyInWithWhereSubquery()
				&& parentComparisonOperator != null;

		if (flattenable) {
			/*
			 * If we got this far and we are an expression subquery then we want
			 * to set leftOperand to be the left side of the comparison in case
			 * we pull the comparison into the flattened subquery.
			 */
			leftOperand = parentComparisonOperator.getLeftOperand();
			// Flatten the subquery
			RowResultSetNode rrsn = (RowResultSetNode) resultSet;
			FromList fl = new FromList(getContextManager());

			// Remove ourselves from the outer subquery list
			outerSubqueryList.removeElement(this);

			/*
			 * We only need to add the table from the subquery into the outer
			 * from list if the subquery itself contains another subquery.
			 * Otherwise, it just becomes a constant.
			 */
			if (rrsn.subquerys.size() != 0) {
				fl.addElement(rrsn);
				outerFromList.destructiveAppend(fl);
			}

			/*
			 * Append the subquery's subquery list to the outer subquery list.
			 */
			outerSubqueryList.destructiveAppend(rrsn.subquerys);

			/*
			 * return the new join condition If we are flattening an EXISTS then
			 * there is no new join condition since there is no leftOperand.
			 * Simply return TRUE.
			 * 
			 * NOTE: The outer where clause, etc. has already been normalized,
			 * so we simply return the BinaryComparisonOperatorNode above the
			 * new join condition.
			 */
			return getNewJoinCondition(leftOperand, getRightOperand());
		}

		/*
		 * Select subquery is flattenable if: o It is not under an OR. o The
		 * subquery type is IN, ANY or EXISTS or an expression subquery on the
		 * right side of a BinaryComparisonOperatorNode. o There are no
		 * aggregates in the select list o There is no group by clause or having
		 * clause. o There is a uniqueness condition that ensures that the
		 * flattening of the subquery will not introduce duplicates into the
		 * result set. o The subquery is not part of a having clause
		 * (DERBY-3257) o There are no windows defined on it
		 * 
		 * OR, o The subquery is NOT EXISTS, NOT IN, ALL (beetle 5173). o Either
		 * a) it does not appear within a WHERE clause, or b) it appears within
		 * a WHERE clause but does not itself contain a WHERE clause with other
		 * subqueries in it. (DERBY-3301)
		 */
		boolean flattenableNotExists = (isNOT_EXISTS() || canAllBeFlattened());

		flattenable = (resultSet instanceof SelectNode)
				&& !((SelectNode) resultSet).hasWindows()
				&& !haveOrderBy
				&& offset == null
				&& fetchFirst == null
				&& underTopAndNode
				&& !havingSubquery
				&& !isWhereExistsAnyInWithWhereSubquery()
				&& (isIN() || isANY() || isEXISTS() || flattenableNotExists || parentComparisonOperator != null);

		if (flattenable) {
			SelectNode select = (SelectNode) resultSet;
			if ((!select.hasAggregatesInSelectList())
					&& (select.havingClause == null)) {
				ValueNode origLeftOperand = leftOperand;

				/* Check for uniqueness condition. */
				/*
				 * Is the column being returned by the subquery a candidate for
				 * an = condition?
				 */
				boolean additionalEQ = (subqueryType == IN_SUBQUERY)
						|| (subqueryType == EQ_ANY_SUBQUERY);

				additionalEQ = additionalEQ
						&& ((leftOperand instanceof ConstantNode)
								|| (leftOperand instanceof ColumnReference) || (leftOperand
									.requiresTypeFromContext()));
				/*
				 * If we got this far and we are an expression subquery then we
				 * want to set leftOperand to be the left side of the comparison
				 * in case we pull the comparison into the flattened subquery.
				 */
				if (parentComparisonOperator != null) {
					leftOperand = parentComparisonOperator.getLeftOperand();
				}
				/*
				 * Never flatten to normal join for NOT EXISTS.
				 */

				if ((!flattenableNotExists)
						&& select.uniqueSubquery(additionalEQ)) {
					// Flatten the subquery
					return flattenToNormalJoin(numTables, outerFromList,
							outerSubqueryList, outerPredicateList);
				}
				/*
				 * We can flatten into an EXISTS join if all of the above
				 * conditions except for a uniqueness condition are true and: o
				 * Subquery only has a single entry in its from list and that
				 * entry is a FromBaseTable o All predicates in the subquery's
				 * where clause are pushable. o The leftOperand, if non-null, is
				 * pushable. If the subquery meets these conditions then we will
				 * flatten the FBT into an EXISTS FBT, pushd the subquery's
				 * predicates down to the PRN above the EBT and mark the
				 * predicates to say that they cannot be pulled above the PRN.
				 * (The only way that we can guarantee correctness is if the
				 * predicates do not get pulled up. If they get pulled up then
				 * the single next logic for an EXISTS join does not work
				 * because that row may get disqualified at a higher level.)
				 * DERBY-4001: Extra conditions to allow flattening to a NOT
				 * EXISTS join (in a NOT EXISTS join it does matter on which
				 * side of the join predicates/restrictions are applied): o All
				 * the predicates must reference the FBT, otherwise predicates
				 * meant for the right side of the join may be applied to the
				 * left side of the join. o The right operand (in ALL and NOT
				 * IN) must reference the FBT, otherwise the generated join
				 * condition may be used to restrict the left side of the join.
				 */
				else if ((isIN() || isANY() || isEXISTS() || flattenableNotExists)
						&& ((leftOperand == null) ? true : leftOperand
								.categorize(new JBitSet(numTables), false))
						&& select.getWherePredicates().allPushable()) {
					FromBaseTable fbt = singleFromBaseTable(select
							.getFromList());

					if (fbt != null
							&& (!flattenableNotExists || (select
									.getWherePredicates().allReference(fbt) && rightOperandFlattenableToNotExists(
									numTables, fbt)))) {
						return flattenToExistsJoin(numTables, outerFromList,
								outerSubqueryList, outerPredicateList,
								flattenableNotExists);
					}
				}

				// restore leftOperand to its original value
				leftOperand = origLeftOperand;
			}
		}

		resultSet.pushQueryExpressionSuffix();

		resultSet.pushOffsetFetchFirst(offset, fetchFirst, hasJDBClimitClause);

		/*
		 * We transform the leftOperand and the select list for quantified
		 * predicates that have a leftOperand into a new predicate and push it
		 * down to the subquery after we preprocess the subquery's resultSet. We
		 * must do this after preprocessing the underlying subquery so that we
		 * know where to attach the new predicate. NOTE - If we pushed the
		 * predicate before preprocessing the underlying subquery, then the
		 * point of attachment would depend on the form of that subquery. (Where
		 * clause? Having clause?)
		 */
		if (leftOperand != null) {
			topNode = pushNewPredicate(numTables);
			pushedNewPredicate = true;
		}
		/*
		 * EXISTS and NOT EXISTS subqueries that haven't been flattened, need an
		 * IS [NOT] NULL node on top so that they return a BOOLEAN. Other cases
		 * are taken care of in pushNewPredicate.
		 */
		else if (isEXISTS() || isNOT_EXISTS()) {
			topNode = genIsNullTree(isEXISTS());
			subqueryType = EXISTS_SUBQUERY;
		}

		/*
		 * * Do inVariant and correlated checks now. We* aren't going to use the
		 * results here, but they* have been stashed away by isInvariant() and
		 * hasCorrelatedCRs()
		 */
		isInvariant();
		hasCorrelatedCRs();

		/*
		 * If parentComparisonOperator is non-null then we are an expression
		 * subquery that was considered to be a candidate for flattening, but we
		 * didn't get flattened. In that case we are the rightOperand of the
		 * parent. We need to update the parent's rightOperand with the new
		 * topNode and return the parent because the parent is letting us decide
		 * whether or not to replace the entire comparison, which we can do if
		 * we flatten. Otherwise we simply return the new top node.
		 */
		if (parentComparisonOperator != null) {
			parentComparisonOperator.setRightOperand(topNode);
			return parentComparisonOperator;
		}

		return topNode;
	}
	
	void putParentNode(QueryTreeNode q){
		parentNode=q;
	}
	
	public Object getColVal( ){
		SubqueryNode sq=this;
		SelectNode sn=(SelectNode)sq.resultSet;
		List<ResultColumn> list = sn.resultColumns.v;
		if (list.size() != 1) {
			return  null;
		}
		ResultColumn c = list.get(0);
		sn.fetchInit();
		Object obj=null;
		setIsFilter(false);
		while(sn.fetch()){
			if(sn.match()){
				obj=sn.getColVal(c.getTableName(), c.getSourceColumnName());
			}
		}
		 
		 return obj;
	}

	@Override
	public void genQuery0() {
		if (leftOperand != null) {
			leftOperand.genQuery(qm);
		}
		/*�����Ӳ�ѯ��������Ϊ�򵥲�ѯ*/
		if(qm.currWhereQuery!=null){
			qm.currWhereQuery.simpleSelect=false;
		}
		resultSet.genQuery(qm);
		
	}

	@Override
	public void exeQuery0() {
		if (leftOperand != null) {
			leftOperand.exeQuery();
		}
		resultSet.exeQuery();
	}

	@Override
	public boolean match() {
		if (leftOperand == null) {
			return super.match();
		}
		boolean def= false;
		if(subqueryType==NOT_IN_SUBQUERY){
			def=true;
		}
		Object obj = leftOperand.getVal();
		resultSet.fetchInit();
		while (resultSet.fetch()) {
			if (resultSet.match()) {
				Object rObj = resultSet.getVal();
				if (ColCompare.compareObject(obj, rObj) == 0) { 
					if(subqueryType== IN_SUBQUERY){
						return true;
					}else{
						return false;
					}
				} 
			}
		}
		return def;
	}

	/**
	 * Does the from list from the subquery contain a single entry which is a
	 * FBT or a PRN/FBT.
	 *
	 * @param fromList
	 *            The from list from the subquery
	 *
	 * @return the {@code FromBaseTable} if the from list from the subquery
	 *         contains a single entry which is a FBT or a PRN/FBT, or
	 *         {@code null} if the subquery does not contain a single FBT
	 */
	private FromBaseTable singleFromBaseTable(FromList fromList) {
		FromBaseTable fbt = null;

		if (fromList.size() == 1) {
			FromTable ft = (FromTable) fromList.elementAt(0);
			if (ft instanceof FromBaseTable) {
				fbt = (FromBaseTable) ft;
			} else if (ft instanceof ProjectRestrictNode) {
				ResultSetNode child = ((ProjectRestrictNode) ft)
						.getChildResult();
				if (child instanceof FromBaseTable) {
					fbt = (FromBaseTable) child;
				}
			}
		}

		return fbt;
	}

	/**
	 * <p>
	 * Check if the right operand is on a form that makes it possible to flatten
	 * this query to a NOT EXISTS join. We don't allow flattening if the right
	 * operand doesn't reference the base table of the subquery. (Requirement
	 * added as part of DERBY-4001.)
	 * </p>
	 *
	 * <p>
	 * The problem with the right operand not referencing the base table of the
	 * subquery, is that the join condition may then be used to filter rows from
	 * the right side (outer) table in the NOT EXISTS join. In a NOT EXISTS
	 * join, the join condition can only safely be applied to the left side
	 * (inner) table of the join. Otherwise, it will filter out all the
	 * interesting rows too early.
	 * </p>
	 *
	 * <p>
	 * Take the query below as an example:
	 * </p>
	 *
	 * <pre>
	 * <code>
	 * SELECT * FROM T1 WHERE X NOT IN (SELECT 1 FROM T2)
	 * </code>
	 * </pre>
	 *
	 * <p>
	 * Here, the right operand is 1, and the join condition is {@code T1.X=1}.
	 * If flattened, the join condition will be used directly on the outer
	 * table, and hide all rows with {@code X<>1}, although those are the only
	 * rows we're interested in. If the join condition had only been used on the
	 * inner table, the NOT EXISTS join logic would do the correct thing.
	 * </p>
	 *
	 * <p>
	 * If the join condition references the inner table, the condition cannot be
	 * used directly on the outer table, so it is safe to flatten the query.
	 * </p>
	 *
	 * @param numTables
	 *            the number of tables in this statement
	 * @param fbt
	 *            the only {@code FromBaseTable} in this subquery
	 * @return {@code true} if it is OK to flatten this query to a NOT EXISTS
	 *         join, {@code false} otherwise
	 */
	private boolean rightOperandFlattenableToNotExists(int numTables,
			FromBaseTable fbt) throws StandardException {

		boolean flattenable = true;

		// If there is no left operand, there is no right operand. If there is
		// no right operand, it cannot cause any problems for the flattening.
		if (leftOperand != null) {
			JBitSet tableSet = new JBitSet(numTables);
			getRightOperand().categorize(tableSet, false);
			// The query can be flattened to NOT EXISTS join only if the right
			// operand references the base table.
			flattenable = tableSet.get(fbt.getTableNumber());
		}

		return flattenable;
	}

	/**
	 * Can NOT IN, ALL be falttened to NOT EXISTS join? We can't or the
	 * flattening doesn't easily make sense if either side of the comparison is
	 * nullable. (beetle 5173)
	 *
	 * @return Whether or not the NOT IN or ALL subquery can be flattened.
	 */
	private boolean canAllBeFlattened() throws StandardException {
		boolean result = false;
		if (isNOT_IN() || isALL()) {
			result = (!leftOperand.getTypeServices().isNullable() && !getRightOperand()
					.getTypeServices().isNullable());
		}
		return result;
	}

	/**
	 * Flatten this subquery into the outer query block. At this point we are
	 * only flattening based on a uniqueness condition and only flattening
	 * non-aggregate subqueries. So, we promote the subquery's from list, as is,
	 * into the outer from list. For EXISTS subquerys, we return a TRUE.
	 * Otherwise we return a new comparison between the leftOperand and the
	 * expression in the subquery's SELECT list. RESOLVE - we will need to
	 * modify this logic to account for exists joins and aggregates as we
	 * support flattening for them.
	 *
	 * Anyway, here's what we do: o We remove ourself from the outer subquery
	 * list. o We decrement the nesting level for all tables in the subquery
	 * tree. o We append the subquery's from list to the outer from list. o We
	 * add the subquery's predicate list to the outer predicate list. (The
	 * subquery has already been preprocessed.) o We add the subquery's subquery
	 * list to the outer subquery list. o For EXISTS, we return a true. o
	 * Otherwise, we return a new comparison between the leftOperand and the
	 * expression in the inner select's RCL.
	 *
	 * @param numTables
	 *            Number of tables in the DML Statement
	 * @param outerFromList
	 *            FromList from outer query block
	 * @param outerSubqueryList
	 *            SubqueryList from outer query block
	 * @param outerPredicateList
	 *            PredicateList from outer query block
	 *
	 * @return The modified expression
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private ValueNode flattenToNormalJoin(int numTables,
			FromList outerFromList, SubqueryList outerSubqueryList,
			PredicateList outerPredicateList) throws StandardException {
		SelectNode select = (SelectNode) resultSet;
		FromList fl = select.getFromList();
		int[] tableNumbers = fl.getTableNumbers();

		// Remove ourselves from the outer subquery list
		outerSubqueryList.removeElement(this);

		/*
		 * Decrease the nesting level for all tables in the subquey tree.
		 */
		select.decrementLevel(1);

		/* Add the table(s) from the subquery into the outer from list */
		outerFromList.destructiveAppend(fl);

		/*
		 * Append the subquery's predicate list to the outer predicate list.
		 */
		outerPredicateList.destructiveAppend(select.getWherePredicates());

		/*
		 * Append the subquery's subquery list to the outer subquery list. NOTE:
		 * We must propagate any subqueries from both the SELECT list and WHERE
		 * clause of the subquery that's getting flattened.
		 */
		outerSubqueryList.destructiveAppend(select.getWhereSubquerys());
		outerSubqueryList.destructiveAppend(select.getSelectSubquerys());

		/*
		 * return the new join condition If we are flattening an EXISTS then
		 * there is no new join condition since there is no leftOperand. Simply
		 * return TRUE.
		 * 
		 * NOTE: The outer where clause, etc. has already been normalized, so we
		 * simply return the BinaryComparisonOperatorNode above the new join
		 * condition.
		 */
		if (leftOperand == null) {
			return new BooleanConstantNode(true, getContextManager());
		} else {
			ValueNode rightOperand = getRightOperand();
			/*
			 * If the right operand is a CR, then we need to decrement its
			 * source level as part of flattening so that transitive closure
			 * will work correctly.
			 */
			if (rightOperand instanceof ColumnReference) {
				ColumnReference cr = (ColumnReference) rightOperand;
				int tableNumber = cr.getTableNumber();
				for (int index = 0; index < tableNumbers.length; index++) {
					if (tableNumber == tableNumbers[index]) {
						cr.setSourceLevel(cr.getSourceLevel() - 1);
						break;
					}
				}
			}
			return getNewJoinCondition(leftOperand, rightOperand);
		}
	}

	/**
	 * Flatten this subquery into the outer query block as an exists join. At
	 * this point we are only flattening non-aggregate subqueries with a single
	 * FBT in the from list. So, we transform all FBTs in the from list into
	 * ExistBaseTables, update the dependency lists for each of the tables and
	 * then flatten the subquery. RESOLVE - we will need to modify this logic to
	 * account for aggregates as we support flattening for them.
	 *
	 * @param numTables
	 *            Number of tables in the DML Statement
	 * @param outerFromList
	 *            FromList from outer query block
	 * @param outerSubqueryList
	 *            SubqueryList from outer query block
	 * @param outerPredicateList
	 *            PredicateList from outer query block
	 * @param flattenableNotExists
	 *            Is it a flattening into a NOT EXISTS join
	 *
	 * @return The modified expression
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private ValueNode flattenToExistsJoin(int numTables,
			FromList outerFromList, SubqueryList outerSubqueryList,
			PredicateList outerPredicateList, boolean flattenableNotExists)
			throws StandardException {
		SelectNode select = (SelectNode) resultSet;

		// Replace the FromBaseTables in the from list with ExistBaseTables
		select.getFromList().genExistsBaseTables(
				resultSet.getReferencedTableMap(), outerFromList,
				flattenableNotExists);

		/*
		 * NOTE: Because we are currently only flattening single table
		 * subqueries whose predicates are all pushable, we simply follow the
		 * rest of the flattening algorithm for unique subqueries. Should we
		 * decide to loosen these restrictions then we need to do more work such
		 * as:
		 * 
		 * Mark all of the predicates from the subquery as non-pullable. They
		 * must not be pulled so that we can guarantee correctness. Otherwise,
		 * we could add or subtract rows from the result set.
		 * 
		 * Remap all of the non-correlated CRs in the predicate list so that
		 * they point to the correct source. (We've chopped a level out of the
		 * RCL/VCN chain.) We then transfer those predicates to the PRN in the
		 * subquery's from list.
		 */

		return flattenToNormalJoin(numTables, outerFromList, outerSubqueryList,
				outerPredicateList);
	}

	/**
	 * Get the node that will be the right operand in the join condition if this
	 * ALL/ANY/SOME/(NOT) IN subquery is flattened to a join.
	 *
	 * @return the right operand
	 */
	private ValueNode getRightOperand() {
		ResultColumn firstRC = resultSet.getResultColumns().elementAt(0);
		return firstRC.getExpression();
	}

	/**
	 * Check to see if we have a Variant value below us. If so, return true.
	 * Caches the result so multiple calls are ok.
	 * 
	 * @return boolean whether we have
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private boolean isInvariant() throws StandardException {
		if (doneInvariantCheck) {
			return !foundVariant;
		}

		doneInvariantCheck = true;
		HasVariantValueNodeVisitor visitor = new HasVariantValueNodeVisitor();
		resultSet.accept(visitor);
		foundVariant = visitor.hasVariant();
		return !foundVariant;
	}

	/**
	 * Check to see if this subquery has correlated column references. Only
	 * useful results if called AFTER binding (after CRs have been bound).
	 *
	 * @return whether the subquery has correlated column references.
	 * @exception StandardException
	 *                Thrown on error
	 */
	boolean hasCorrelatedCRs() throws StandardException {
		if (doneCorrelationCheck) {
			return foundCorrelation;
		}
		doneCorrelationCheck = true;

		ResultSetNode realSubquery = resultSet;
		ResultColumnList oldRCL = null;

		/*
		 * If we have pushed the new join predicate on top, we want to disregard
		 * it to see if anything under the predicate is correlated. If nothing
		 * correlated under the new join predicate, we could then materialize
		 * the subquery. See beetle 4373.
		 */
		if (pushedNewPredicate) {
			if (SanityManager.DEBUG) {
				SanityManager.ASSERT(resultSet instanceof ProjectRestrictNode,
						"resultSet expected to be a ProjectRestrictNode!");
			}

			realSubquery = ((ProjectRestrictNode) resultSet).getChildResult();
			oldRCL = realSubquery.getResultColumns();

			/*
			 * Only first column matters.
			 */
			if (oldRCL.size() > 1) {
				ResultColumnList newRCL = new ResultColumnList(
						getContextManager());
				newRCL.addResultColumn(oldRCL.getResultColumn(1));
				realSubquery.setResultColumns(newRCL);
			}
		}

		HasCorrelatedCRsVisitor visitor = new HasCorrelatedCRsVisitor();
		realSubquery.accept(visitor);
		foundCorrelation = visitor.hasCorrelatedCRs();

		if (pushedNewPredicate && (oldRCL.size() > 1)) {
			realSubquery.setResultColumns(oldRCL);
		}

		return foundCorrelation;
	}

	/**
	 * Transform: expression QuantifiedOperator (select x from ...) into (select
	 * true from .. where expression <BinaryComparisonOperator> x ...) IS [NOT]
	 * NULL
	 *
	 * or, if we have an aggregate: (select true from (select AGG(x) from ...)
	 * where expression <BinaryComparisonOperator> x ...) IS [NOT] NULL
	 *
	 *
	 * For ANY and IN subqueries: o We generate an IS NULL above the
	 * SubqueryNode and return the top of the new tree to the caller. o The
	 * operator in the new predicate that is added to the subquery will
	 * correspond to the operator that modifies the ANY. (eg, = for = ANY, with
	 * = for IN.) For ALL and NOT IN subqueries: o We generate an IS NOT NULL
	 * above the SubqueryNode and return the top of the new tree to the caller.
	 * o The operator in the new predicate that is added to the subquery will be
	 * a BinaryAllOperatorNode whose bcoNodeType corresponds to the negation of
	 * the operator that modifies the ALL. (eg, <> for = ALL, with <> for NOT
	 * IN.)
	 *
	 * NOTE: This method is called after the underlying subquery has been
	 * preprocessed, so we build a new Predicate, not just a new expression.
	 *
	 * @param numTables
	 *            Number of tables in DML Statement
	 *
	 * @return UnaryComparisonOperatorNode An IS [NOT] NULL above the
	 *         transformed subquery.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private UnaryComparisonOperatorNode pushNewPredicate(int numTables)
			throws StandardException {
		AndNode andNode;
		JBitSet tableMap;
		Predicate predicate;
		ResultColumn firstRC;
		ResultColumnList resultColumns;
		UnaryComparisonOperatorNode ucoNode = null;
		ValueNode rightOperand;
		ContextManager cm = getContextManager();

		/*
		 * We have to ensure that the resultSet immediately under us has a
		 * PredicateList, otherwise we can't push the predicate down.
		 */
		resultSet = resultSet.ensurePredicateList(numTables);

		/*
		 * RESOLVE - once we understand how correlated columns will work, we
		 * probably want to mark leftOperand as a correlated column
		 */
		resultColumns = resultSet.getResultColumns();

		/*
		 * * Create a new PR node. Put it over the original subquery. resulSet*
		 * is now the new PR. We give the chance that things under the PR node*
		 * can be materialized. See beetle 4373.
		 */
		ResultColumnList newRCL = resultColumns.copyListAndObjects();
		newRCL.genVirtualColumnNodes(resultSet, resultColumns);
		resultSet = new ProjectRestrictNode(resultSet, // child
				newRCL, // result columns
				null, // restriction
				null, // restriction list
				null, // project subqueries
				null, // restrict subqueries
				null, cm);
		resultColumns = newRCL;

		firstRC = resultColumns.elementAt(0);
		rightOperand = firstRC.getExpression();

		BinaryComparisonOperatorNode bcoNode = getNewJoinCondition(leftOperand,
				rightOperand);

		ValueNode andLeft = bcoNode;

		/*
		 * For NOT IN or ALL, and if either side of the comparison is nullable,
		 * and the subquery can not be flattened (because of that), we need to
		 * add IS NULL node on top of the nullables, such that the behavior is
		 * (beetle 5173):
		 * 
		 * (1) If we have nulls in right operand, no row is returned. (2) If
		 * subquery result is empty before applying join predicate, every left
		 * row (including NULLs) is returned. (3) Otherwise, return {all left
		 * row} - {NULLs}
		 */
		if (isNOT_IN() || isALL()) {
			boolean leftNullable = leftOperand.getTypeServices().isNullable();
			boolean rightNullable = rightOperand.getTypeServices().isNullable();
			if (leftNullable || rightNullable) {
				/*
				 * Create a normalized structure.
				 */
				BooleanConstantNode falseNode = new BooleanConstantNode(false,
						cm);
				OrNode newOr = new OrNode(bcoNode, falseNode, cm);

				newOr.postBindFixup();
				andLeft = newOr;

				if (leftNullable) {
					UnaryComparisonOperatorNode leftIsNull = new IsNullNode(
							leftOperand, false, cm);
					leftIsNull.bindComparisonOperator();
					newOr = new OrNode(leftIsNull, andLeft, cm);
					newOr.postBindFixup();
					andLeft = newOr;
				}
				if (rightNullable) {
					UnaryComparisonOperatorNode rightIsNull = new IsNullNode(
							rightOperand, false, cm);
					rightIsNull.bindComparisonOperator();
					newOr = new OrNode(rightIsNull, andLeft, cm);
					newOr.postBindFixup();
					andLeft = newOr;
				}
			}
		}

		/* Place an AndNode above the <BinaryComparisonOperator> */
		andNode = new AndNode(andLeft, getTrueNode(), cm);

		/* Build the referenced table map for the new predicate */
		tableMap = new JBitSet(numTables);
		andNode.postBindFixup();

		/* Put the AndNode under a Predicate */
		predicate = new Predicate(andNode, tableMap, cm);
		predicate.categorize();

		/* Push the new Predicate to the subquery's list */
		resultSet = resultSet.addNewPredicate(predicate);

		/* Clean up the leftOperand and subquery ResultColumn */
		leftOperand = null;
		firstRC.setType(getTypeServices());
		firstRC.setExpression(getTrueNode());

		/* Add the IS [NOT] NULL above the SubqueryNode */
		switch (subqueryType) {
		case IN_SUBQUERY:
		case EQ_ANY_SUBQUERY:
		case NE_ANY_SUBQUERY:
		case LE_ANY_SUBQUERY:
		case LT_ANY_SUBQUERY:
		case GE_ANY_SUBQUERY:
		case GT_ANY_SUBQUERY:
			ucoNode = new IsNullNode(this, true, cm);
			break;

		case NOT_IN_SUBQUERY:
		case EQ_ALL_SUBQUERY:
		case NE_ALL_SUBQUERY:
		case LE_ALL_SUBQUERY:
		case LT_ALL_SUBQUERY:
		case GE_ALL_SUBQUERY:
		case GT_ALL_SUBQUERY:
			ucoNode = new IsNullNode(this, false, cm);
			break;
		default:
			if (SanityManager.DEBUG) {
				SanityManager.NOTREACHED();
			}
		}
		ucoNode.bindComparisonOperator();
		return ucoNode;
	}

	/**
	 * Build a new join condition between the leftOperand and the rightOperand.
	 * The comparison operator is dependent on the subquery type.
	 *
	 * @param leftOperand
	 *            The left operand for the new condition.
	 * @param rightOperand
	 *            The right operand for the new condition.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private BinaryComparisonOperatorNode getNewJoinCondition(
			ValueNode leftOperand, ValueNode rightOperand)
			throws StandardException {
		/*
		 * NOTE: If we are an expression subquery that's getting flattened then
		 * our subqueryType is EXPRESSION_SUBQUERY. However, we can get the
		 * comparison type from the parentComparisonOperator. In that case we
		 * dovetail on the ANY subquery types.
		 */
		int operatorType = subqueryType;
		if (subqueryType == EXPRESSION_SUBQUERY) {
			if (SanityManager.DEBUG) {
				SanityManager.ASSERT(parentComparisonOperator != null,
						"parentComparisonOperator expected to be non-null");
			}

			int parentOperator = -1;

			if (parentComparisonOperator.isRelationalOperator()) {
				RelationalOperator ro = (RelationalOperator) parentComparisonOperator;
				parentOperator = ro.getOperator();
			}

			if (parentOperator == RelationalOperator.EQUALS_RELOP) {
				operatorType = EQ_ANY_SUBQUERY;
			} else if (parentOperator == RelationalOperator.NOT_EQUALS_RELOP) {
				operatorType = NE_ANY_SUBQUERY;
			} else if (parentOperator == RelationalOperator.LESS_EQUALS_RELOP) {
				operatorType = LE_ANY_SUBQUERY;
			} else if (parentOperator == RelationalOperator.LESS_THAN_RELOP) {
				operatorType = LT_ANY_SUBQUERY;
			} else if (parentOperator == RelationalOperator.GREATER_EQUALS_RELOP) {
				operatorType = GE_ANY_SUBQUERY;
			} else if (parentOperator == RelationalOperator.GREATER_THAN_RELOP) {
				operatorType = GT_ANY_SUBQUERY;
			}
		}

		int bcoType = 0;
		int kind = -1;

		/* Build the <BinaryComparisonOperator> */
		switch (operatorType) {
		case IN_SUBQUERY:
		case EQ_ANY_SUBQUERY:
		case NOT_IN_SUBQUERY:
		case NE_ALL_SUBQUERY:
			kind = BinaryRelationalOperatorNode.K_EQUALS;
			break;

		case NE_ANY_SUBQUERY:
		case EQ_ALL_SUBQUERY:
			kind = BinaryRelationalOperatorNode.K_NOT_EQUALS;
			break;

		case LE_ANY_SUBQUERY:
		case GT_ALL_SUBQUERY:
			kind = BinaryRelationalOperatorNode.K_LESS_EQUALS;
			break;

		case LT_ANY_SUBQUERY:
		case GE_ALL_SUBQUERY:
			kind = BinaryRelationalOperatorNode.K_LESS_THAN;
			break;

		case GE_ANY_SUBQUERY:
		case LT_ALL_SUBQUERY:
			kind = BinaryRelationalOperatorNode.K_GREATER_EQUALS;
			break;

		case GT_ANY_SUBQUERY:
		case LE_ALL_SUBQUERY:
			kind = BinaryRelationalOperatorNode.K_GREATER_THAN;
			break;

		default:
			if (SanityManager.DEBUG)
				SanityManager.ASSERT(false, "subqueryType (" + subqueryType
						+ ") is an unexpected type");
		}

		BinaryComparisonOperatorNode bcoNode = new BinaryRelationalOperatorNode(
				kind, leftOperand, rightOperand, false, getContextManager());

		bcoNode.bindComparisonOperator();
		return bcoNode;
	}

	/**
	 * Eliminate NotNodes in the current query block. We traverse the tree,
	 * inverting ANDs and ORs and eliminating NOTs as we go. We stop at
	 * ComparisonOperators and boolean expressions. We invert
	 * ComparisonOperators and replace boolean expressions with boolean
	 * expression = false. NOTE: Since we do not recurse under
	 * ComparisonOperators, there still could be NotNodes left in the tree.
	 *
	 * @param underNotNode
	 *            Whether or not we are under a NotNode.
	 *
	 *
	 * @return The modified expression
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ValueNode eliminateNots(boolean underNotNode) throws StandardException {
		ValueNode result = this;

		if (underNotNode) {
			/*
			 * Negate the subqueryType. For expression subqueries we simply
			 * return subquery = false
			 */
			/*
			 * RESOLVE - This code needs to get cleaned up once there are more
			 * subquery types. (Consider using arrays.)
			 */
			switch (subqueryType) {
			case EXPRESSION_SUBQUERY:
				result = genEqualsFalseTree();
				break;

			case EXISTS_SUBQUERY:
				subqueryType = NOT_EXISTS_SUBQUERY;
				break;

			/* ANY subqueries */
			case IN_SUBQUERY:
			case EQ_ANY_SUBQUERY:
				subqueryType = NOT_IN_SUBQUERY;
				break;

			case NE_ANY_SUBQUERY:
				subqueryType = EQ_ALL_SUBQUERY;
				break;

			case GE_ANY_SUBQUERY:
				subqueryType = LT_ALL_SUBQUERY;
				break;

			case GT_ANY_SUBQUERY:
				subqueryType = LE_ALL_SUBQUERY;
				break;

			case LE_ANY_SUBQUERY:
				subqueryType = GT_ALL_SUBQUERY;
				break;

			case LT_ANY_SUBQUERY:
				subqueryType = GE_ALL_SUBQUERY;
				break;

			/*
			 * ALL subqueries - no need for NOT NOT_IN_SUBQUERY, since NOT IN
			 * only comes into existence here.
			 */
			case EQ_ALL_SUBQUERY:
				subqueryType = NE_ANY_SUBQUERY;
				break;

			case NE_ALL_SUBQUERY:
				subqueryType = EQ_ANY_SUBQUERY;
				break;

			case GE_ALL_SUBQUERY:
				subqueryType = LT_ANY_SUBQUERY;
				break;

			case GT_ALL_SUBQUERY:
				subqueryType = LE_ANY_SUBQUERY;
				break;

			case LE_ALL_SUBQUERY:
				subqueryType = GT_ANY_SUBQUERY;
				break;

			case LT_ALL_SUBQUERY:
				subqueryType = GE_ANY_SUBQUERY;
				break;

			default:
				if (SanityManager.DEBUG)
					SanityManager.ASSERT(false,
							"NOT is not supported for this time of subquery");
			}
		}

		/* Halt recursion here, as each query block is preprocessed separately */
		return result;
	}

	/**
	 * Finish putting an expression into conjunctive normal form. An expression
	 * tree in conjunctive normal form meets the following criteria: o If the
	 * expression tree is not null, the top level will be a chain of AndNodes
	 * terminating in a true BooleanConstantNode. o The left child of an AndNode
	 * will never be an AndNode. o Any right-linked chain that includes an
	 * AndNode will be entirely composed of AndNodes terminated by a true
	 * BooleanConstantNode. o The left child of an OrNode will never be an
	 * OrNode. o Any right-linked chain that includes an OrNode will be entirely
	 * composed of OrNodes terminated by a false BooleanConstantNode. o
	 * ValueNodes other than AndNodes and OrNodes are considered leaf nodes for
	 * purposes of expression normalization. In other words, we won't do any
	 * normalization under those nodes.
	 *
	 * In addition, we track whether or not we are under a top level AndNode.
	 * SubqueryNodes need to know this for subquery flattening.
	 *
	 * @param underTopAndNode
	 *            Whether or not we are under a top level AndNode.
	 *
	 *
	 * @return The modified expression
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ValueNode changeToCNF(boolean underTopAndNode) throws StandardException {
		/*
		 * Remember whether or not we are immediately under a top leve AndNode.
		 * This is important for subquery flattening. (We can only flatten
		 * subqueries under a top level AndNode.)
		 */
		this.underTopAndNode = underTopAndNode;

		/* Halt recursion here, as each query block is preprocessed separately */
		return this;
	}

	/**
	 * Categorize this predicate. Initially, this means building a bit map of
	 * the referenced tables for each predicate. If the source of this
	 * ColumnReference (at the next underlying level) is not a ColumnReference
	 * or a VirtualColumnNode then this predicate will not be pushed down.
	 *
	 * For example, in: select * from (select 1 from s) a (x) where x = 1 we
	 * will not push down x = 1. NOTE: It would be easy to handle the case of a
	 * constant, but if the inner SELECT returns an arbitrary expression, then
	 * we would have to copy that tree into the pushed predicate, and that tree
	 * could contain subqueries and method calls. RESOLVE - revisit this issue
	 * once we have views.
	 *
	 * @param referencedTabs
	 *            JBitSet with bit map of referenced FromTables
	 * @return boolean Whether or not source.expression is a ColumnReference or
	 *         a VirtualColumnNode.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	boolean categorize(JBitSet referencedTabs, boolean simplePredsOnly)
			throws StandardException {
		/*
		 * We stop here when only considering simple predicates as we don't
		 * consider method calls when looking for null invariant predicates.
		 */
		if (simplePredsOnly) {
			return false;
		}

		/*
		 * RESOLVE - We need to or in a bit map when there are correlation
		 * columns
		 */

		/* We categorize a query block at a time, so stop the recursion here */

		/* Predicates with subqueries are not pushable for now */

		/*
		 * * If we can materialize the subquery, then it is* both invariant and
		 * non-correlated. And so it* is pushable.
		 */
		return isMaterializable();

	}

	/*
	 * * Subquery is materializable if* it is an expression subquery that* has
	 * no correlations and is invariant.
	 */
	public boolean isMaterializable() throws StandardException {
		boolean retval = (subqueryType == EXPRESSION_SUBQUERY)
				&& !hasCorrelatedCRs() && isInvariant();
		/*
		 * If we can materialize the subquery, then we set the level of all of
		 * the tables to 0 so that we can consider bulk fetch for them.
		 */
		if (retval) {
			if (resultSet instanceof SelectNode) {
				SelectNode select = (SelectNode) resultSet;
				FromList fromList = select.getFromList();
				fromList.setLevel(0);
			}
		}

		return retval;
	}

	 
	/**
	 * Return the variant type for the underlying expression. The variant type
	 * can be: VARIANT - variant within a scan (method calls and non-static
	 * field access) SCAN_INVARIANT - invariant within a scan (column references
	 * from outer tables) QUERY_INVARIANT - invariant within the life of a query
	 * (constant expressions)
	 *
	 * @return The variant type for the underlying expression.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	protected int getOrderableVariantType() throws StandardException {
		/*
		 * If the subquery is variant, than return VARIANT. Otherwise, if we
		 * have an expression subquery and no correlated CRs we are going to
		 * materialize it, so it is QUERY_INVARIANT. Otherwise, SCAN_INVARIANT.
		 */
		return 0;
	}

	/**
	 * Do code generation for this subquery.
	 *
	 * @param expressionBuilder
	 *            The ExpressionClassBuilder for the class being built
	 * @param mbex
	 *            The method the expression will go into
	 *
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	 
 

	/* Private methods on private variables */
	private BooleanConstantNode getTrueNode() throws StandardException {
		if (trueNode == null) {
			trueNode = new BooleanConstantNode(true, getContextManager());
		}
		return trueNode;
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

		/*
		 * shortcut if we've already done it
		 */
		if ((v instanceof HasCorrelatedCRsVisitor) && doneCorrelationCheck) {
			((HasCorrelatedCRsVisitor) v).setHasCorrelatedCRs(foundCorrelation);
			return;
		}

		if (resultSet != null) {
			resultSet = (ResultSetNode) resultSet.accept(v);
		}

		if (leftOperand != null) {
			leftOperand = (ValueNode) leftOperand.accept(v);
		}
	}

	private boolean isIN() {
		return subqueryType == IN_SUBQUERY;
	}

	private boolean isNOT_IN() {
		return subqueryType == NOT_IN_SUBQUERY;
	}

	private boolean isANY() {
		switch (subqueryType) {
		case EQ_ANY_SUBQUERY:
		case NE_ANY_SUBQUERY:
		case LE_ANY_SUBQUERY:
		case LT_ANY_SUBQUERY:
		case GE_ANY_SUBQUERY:
		case GT_ANY_SUBQUERY:
			return true;

		default:
			return false;
		}
	}

	private boolean isALL() {
		switch (subqueryType) {
		case EQ_ALL_SUBQUERY:
		case NE_ALL_SUBQUERY:
		case LE_ALL_SUBQUERY:
		case LT_ALL_SUBQUERY:
		case GE_ALL_SUBQUERY:
		case GT_ALL_SUBQUERY:
			return true;

		default:
			return false;
		}
	}

	private boolean isEXISTS() {
		return subqueryType == EXISTS_SUBQUERY;
	}

	private boolean isNOT_EXISTS() {
		return subqueryType == NOT_EXISTS_SUBQUERY;
	}

	/**
	 * Convert this IN/ANY subquery, which is known to return at most 1 row, to
	 * an equivalent expression subquery.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private void changeToCorrespondingExpressionType() throws StandardException {
		int nodeType = -1;

		switch (subqueryType) {
		case EQ_ANY_SUBQUERY:
		case IN_SUBQUERY:
			nodeType = BinaryRelationalOperatorNode.K_EQUALS;
			break;

		case NE_ANY_SUBQUERY:
			nodeType = BinaryRelationalOperatorNode.K_NOT_EQUALS;
			break;

		case LE_ANY_SUBQUERY:
			nodeType = BinaryRelationalOperatorNode.K_LESS_EQUALS;
			break;

		case LT_ANY_SUBQUERY:
			nodeType = BinaryRelationalOperatorNode.K_LESS_THAN;
			break;

		case GE_ANY_SUBQUERY:
			nodeType = BinaryRelationalOperatorNode.K_GREATER_EQUALS;
			break;

		case GT_ANY_SUBQUERY:
			nodeType = BinaryRelationalOperatorNode.K_GREATER_THAN;
			break;
		default:
			if (SanityManager.DEBUG) {
				SanityManager.NOTREACHED();
			}
		}

		BinaryRelationalOperatorNode bcon = new BinaryRelationalOperatorNode(
				nodeType, leftOperand, this, false, getContextManager());

		// clean up the state of the tree to reflect a bound expression subquery
		subqueryType = EXPRESSION_SUBQUERY;
		setDataTypeServices(resultSet.getResultColumns());

		parentComparisonOperator = bcon;
		/* Set type info for the operator node */
		parentComparisonOperator.bindComparisonOperator();
		leftOperand = null;
	}

	private void setDataTypeServices(ResultColumnList resultColumns)
			throws StandardException {
		DataTypeDescriptor dts;

		/*
		 * Set the result type for this subquery (must be nullable). Quantified
		 * predicates will return boolean. However, the return type of the
		 * subquery's result list will probably not be boolean at this point,
		 * because we do not transform the subquery (other than EXISTS) into
		 * (select true/false ...) until preprocess(). So, we force the return
		 * type to boolean.
		 */
		if (subqueryType == EXPRESSION_SUBQUERY) {
			dts = resultColumns.elementAt(0).getTypeServices();
		} else {
			dts = getTrueNode().getTypeServices();
		}

		setType(dts.getNullabilityType(true));
	}

	/**
	 * {@inheritDoc}
	 */
	boolean isEquivalent(ValueNode o) {
		return false;
	}

	/**
	 * Is this subquery part of a having clause?
	 * 
	 * @return true if it is part of a having clause, otherwise false
	 */
	public boolean isHavingSubquery() {
		return havingSubquery;
	}

	/**
	 * Mark this subquery as being part of a having clause.
	 * 
	 * @param havingSubquery
	 */
	public void setHavingSubquery(boolean havingSubquery) {
		this.havingSubquery = havingSubquery;
	}

	/**
	 * Is this subquery part of a whereclause?
	 *
	 * @return true if it is part of a where clause, otherwise false
	 */
	boolean isWhereSubquery() {
		return whereSubquery;
	}

	/**
	 * Mark this subquery as being part of a where clause.
	 * 
	 * @param whereSubquery
	 */
	void setWhereSubquery(boolean whereSubquery) {
		this.whereSubquery = whereSubquery;
	}

	/**
	 * Check whether this is a WHERE EXISTS | ANY | IN subquery with a subquery
	 * in its own WHERE clause. Used in flattening decision making.
	 * 
	 * DERBY-3301 reported wrong results from a nested WHERE EXISTS, but
	 * according to the derby optimizer docs this applies to a broader range of
	 * WHERE clauses in a WHERE EXISTS subquery. No WHERE EXISTS subquery with
	 * anohter subquery in it own WHERE clause can be flattened.
	 * 
	 * @return true if this subquery is a WHERE EXISTS | ANY | IN subquery with
	 *         a subquery in its own WHERE clause
	 */
	boolean isWhereExistsAnyInWithWhereSubquery() throws StandardException {
		if (isWhereSubquery() && (isEXISTS() || isANY() || isIN())) {
			if (resultSet instanceof SelectNode) {
				SelectNode sn = (SelectNode) resultSet;
				/*
				 * Flattening happens in lower QueryTree nodes first and then
				 * removes nodes from the whereSubquerys list or whereClause.
				 * Hence we check the original WHERE clause for subqueries in
				 * SelectNode.init(), and simply check here.
				 */
				if (sn.originalWhereClauseHadSubqueries) {
					/*
					 * This is a WHERE EXISTS | ANY |IN subquery with a subquery
					 * in its own WHERE clause (or now in whereSubquerys).
					 */
					return true;
				}
			}
			/*
			 * This is a WHERE EXISTS | ANY | IN subquery, but does not contain
			 * a subquery in its WHERE subquerylist or clause
			 */
			return false;
		} else {
			/*
			 * This isn't a WHERE EXISTS | ANY | IN subquery
			 */
			return false;
		}
	}

	/**
	 * Get ORDER BY list (used to construct FROM_SUBQUERY only), cf.
	 * FromSubquery, for which this node is transient.
	 *
	 * @return order by list if specified, else null.
	 */
	public OrderByList getOrderByList() {
		return orderByList;
	}

	/**
	 * Get OFFSET (used to construct FROM_SUBQUERY only), cf. FromSubquery, for
	 * which this node is transient.
	 *
	 * @return offset if specified, else null.
	 */
	public ValueNode getOffset() {
		return offset;
	}

	/**
	 * Get FETCH FIRST (used to construct FROM_SUBQUERY only), cf. FromSubquery,
	 * for which this node is transient.
	 *
	 * @return fetch first if specified, else null.
	 */
	public ValueNode getFetchFirst() {
		return fetchFirst;
	}

	/**
	 * Return true if the offset/fetchFirst clauses were added by JDBC LIMIT
	 * escape syntax. This method is used to construct a FROM_SUBQUERY only, cf.
	 * FromSubquery, for which this node is transient.
	 *
	 * @return true if the JDBC limit/offset semantics (rather than the SQL
	 *         Standard OFFSET/FETCH NEXT) semantics apply
	 */
	public boolean hasJDBClimitClause() {
		return hasJDBClimitClause;
	}

}
