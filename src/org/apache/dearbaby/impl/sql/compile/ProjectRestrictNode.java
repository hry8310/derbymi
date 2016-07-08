/*

   Derby - Class org.apache.derby.impl.sql.compile.ProjectRestrictNode

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

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.compiler.MethodBuilder;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.AccessPath;
import org.apache.derby.iapi.sql.compile.Optimizable;
import org.apache.derby.iapi.sql.compile.OptimizablePredicate;
import org.apache.derby.iapi.sql.compile.OptimizablePredicateList;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.store.access.TransactionController;
import org.apache.derby.iapi.util.JBitSet;
import org.apache.derby.shared.common.sanity.SanityManager;
 
/**
 * A ProjectRestrictNode represents a result set for any of the basic DML
 * operations: SELECT, INSERT, UPDATE, and DELETE.  For INSERT with
 * a VALUES clause, restriction will be null. For both INSERT and UPDATE,
 * the resultColumns in the selectList will contain the names of the columns
 * being inserted into or updated.
 *
 * NOTE: A ProjectRestrictNode extends FromTable since it can exist in a FromList.
 *
 */

class ProjectRestrictNode extends SingleChildResultSetNode
{
	/**
	 * The ValueNode for the restriction to be evaluated here.
	 */
    ValueNode   restriction;

	/**
	 * Constant expressions to be evaluated here.
	 */
	ValueNode	constantRestriction = null;

	/**
	 * Restriction as a PredicateList
	 */
    PredicateList restrictionList;

	/**
	 * List of subqueries in projection
	 */
	SubqueryList projectSubquerys;

	/**
	 * List of subqueries in restriction
	 */
	SubqueryList restrictSubquerys;

	private boolean accessPathModified;

	/* Should we get the table number from this node,
	 * regardless of the class of our child.
	 */
	private boolean getTableNumberHere;

    /**
     * Used with {@code validatingBaseTableCID} to validating deferred check
     * constraints.
     */
    private boolean validatingCheckConstraints = false;
    private String validatingBaseTableUUIDString;
	/**
     * Constructor for a ProjectRestrictNode.
	 *
	 * @param childResult	The child ResultSetNode
	 * @param projection	The result column list for the projection
	 * @param restriction	An expression representing the restriction to be 
	 *					    evaluated here.
	 * @param restrictionList Restriction as a PredicateList
	 * @param projectSubquerys List of subqueries in the projection
	 * @param restrictSubquerys List of subqueries in the restriction
	 * @param tableProperties	Properties list associated with the table
     * @param cm            The context manager
	 */

    ProjectRestrictNode(ResultSetNode    childResult,
                        ResultColumnList projection,
                        ValueNode        restriction,
                        PredicateList    restrictionList,
                        SubqueryList     projectSubquerys,
                        SubqueryList     restrictSubquerys,
                        Properties       tableProperties,
                        ContextManager   cm)
	{
        super(childResult, tableProperties, cm);
        setResultColumns( projection );
        this.restriction = restriction;
        this.restrictionList = restrictionList;
        this.projectSubquerys = projectSubquerys;
        this.restrictSubquerys = restrictSubquerys;

		/* A PRN will only hold the tableProperties for
		 * a result set tree if its child is not an
		 * optimizable.  Otherwise, the properties will
		 * be transferred down to the child.
		 */
		if (tableProperties != null &&
			 (childResult instanceof Optimizable))
		{
			((Optimizable) childResult).setProperties(getProperties());
			setProperties((Properties) null);
		}
	}

	/*
	 *  Optimizable interface
	 */

	 

	 
 
	/** @see Optimizable#getTableNumber */
    @Override
	public int getTableNumber()
	{
		/* GROSS HACK - We need to get the tableNumber after
		 * calling modifyAccessPaths() on the child when doing
		 * a hash join on an arbitrary result set.  The problem
		 * is that the child will always be an optimizable at this
		 * point.  So, we 1st check to see if we should get it from
		 * this node.  (We set the boolean to true in the appropriate
		 * place in modifyAccessPaths().)
		 */
		if (getTableNumberHere)
		{
			return super.getTableNumber();
		}

		if (childResult instanceof Optimizable)
			return ((Optimizable) childResult).getTableNumber();

		return super.getTableNumber();
	}

	 
	 
	/** @see Optimizable#getCurrentAccessPath */
     

	/** @see Optimizable#getTrulyTheBestAccessPath */
    @Override
	public AccessPath getTrulyTheBestAccessPath()
	{
		/* The childResult will always be an Optimizable
		 * during code generation.  If the childResult was
		 * not an Optimizable during optimization, then this node
		 * will have the truly the best access path, so we want to
		 * return it from this node, rather than traversing the tree.
		 * This can happen for non-flattenable derived tables.
		 * Anyway, we note this state when modifying the access paths.
		 */
		if (hasTrulyTheBestAccessPath)
		{
			return super.getTrulyTheBestAccessPath();
		}

		if (childResult instanceof Optimizable)
			return ((Optimizable) childResult).getTrulyTheBestAccessPath();

		return super.getTrulyTheBestAccessPath();
	}

	/** @see Optimizable#rememberSortAvoidancePath */
    @Override
	public void rememberSortAvoidancePath()
	{
		if (childResult instanceof Optimizable)
			((Optimizable) childResult).rememberSortAvoidancePath();
		else
			super.rememberSortAvoidancePath();
	}

	/** @see Optimizable#considerSortAvoidancePath */
    @Override
	public boolean considerSortAvoidancePath()
	{
		if (childResult instanceof Optimizable)
			return ((Optimizable) childResult).considerSortAvoidancePath();

		return super.considerSortAvoidancePath();
	}

	/**
	 * @see Optimizable#pushOptPredicate
	 *
	 * @exception StandardException		Thrown on error
	 */

    @Override
	public boolean pushOptPredicate(OptimizablePredicate optimizablePredicate)
			throws StandardException
	{
		if (SanityManager.DEBUG)
		{
			SanityManager.ASSERT(optimizablePredicate instanceof Predicate,
				"optimizablePredicate expected to be instanceof Predicate");
			SanityManager.ASSERT(! optimizablePredicate.hasSubquery() &&
								 ! optimizablePredicate.hasMethodCall(),
				"optimizablePredicate either has a subquery or a method call");
		}

		/* Add the matching predicate to the restrictionList */
		if (restrictionList == null)
		{
            restrictionList = new PredicateList(getContextManager());
		}
		restrictionList.addPredicate((Predicate) optimizablePredicate);

		/* Remap all of the ColumnReferences to point to the
		 * source of the values.
		 */
		Predicate pred = (Predicate)optimizablePredicate;

		/* If the predicate is scoped then the call to "remapScopedPred()"
		 * will do the necessary remapping for us and will return true;
		 * otherwise, we'll just do the normal remapping here.
		 */
		if (!pred.remapScopedPred())
		{
			RemapCRsVisitor rcrv = new RemapCRsVisitor(true);
			pred.getAndNode().accept(rcrv);
		}

		return true;
	}

	 

	 
 
	/** @see Optimizable#verifyProperties 
	 * @exception StandardException		Thrown on error
	 */
    @Override
	public void verifyProperties(DataDictionary dDictionary)
		throws StandardException
	{
		/* Table properties can be attached to this node if
		 * its child is not an optimizable, otherwise they
		 * are attached to its child.
		 */

		if (childResult instanceof Optimizable)
		{
			((Optimizable) childResult).verifyProperties(dDictionary);
		}
		else
		{
			super.verifyProperties(dDictionary);
		}
	}

	/**
	 * @see Optimizable#legalJoinOrder
	 */
    @Override
	public boolean legalJoinOrder(JBitSet assignedTableMap)
	{
		if (childResult instanceof Optimizable)
		{
			return ((Optimizable) childResult).legalJoinOrder(assignedTableMap);
		}
		else
		{
			return true;
		}
	}

	/**
	 * @see Optimizable#uniqueJoin
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
	public double uniqueJoin(OptimizablePredicateList predList)
					throws StandardException
	{
		if (childResult instanceof Optimizable)
		{
			return ((Optimizable) childResult).uniqueJoin(predList);
		}
		else
		{
			return super.uniqueJoin(predList);
		}
	}

	/**
	 * Return the restriction list from this node.
	 *
	 * @return	The restriction list from this node.
	 */
	PredicateList getRestrictionList()
	{
		return restrictionList;
	}

	/** 
	 * Return the user specified join strategy, if any for this table.
	 *
	 * @return The user specified join strategy, if any for this table.
	 */
    @Override
	String getUserSpecifiedJoinStrategy()
	{
		if (childResult instanceof FromTable)
		{
			return ((FromTable) childResult).getUserSpecifiedJoinStrategy();
		}
		else
		{
			return userSpecifiedJoinStrategy;
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

			if (restriction != null)
			{
				printLabel(depth, "restriction: ");
				restriction.treePrint(depth + 1);
			}

			if (restrictionList != null)
			{
				printLabel(depth, "restrictionList: ");
				restrictionList.treePrint(depth + 1);
			}

			if (projectSubquerys != null)
			{
				printLabel(depth, "projectSubquerys: ");
				projectSubquerys.treePrint(depth + 1);
			}

			if (restrictSubquerys != null)
			{
				printLabel(depth, "restrictSubquerys: ");
				restrictSubquerys.treePrint(depth + 1);
			}
		}
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
		childResult = childResult.preprocess(numTables, gbl, fromList);

		/* Build the referenced table map */
		setReferencedTableMap( (JBitSet) childResult.getReferencedTableMap().clone() );

		return this;
	}

	/**
	 * Push expressions down to the first ResultSetNode which can do expression
	 * evaluation and has the same referenced table map.
	 * RESOLVE - This means only pushing down single table expressions to
	 * ProjectRestrictNodes today.  Once we have a better understanding of how
	 * the optimizer will work, we can push down join clauses.
	 *
	 * @param predicateList	The PredicateList.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    void pushExpressions(PredicateList predicateList)
					throws StandardException
	{
		if (SanityManager.DEBUG)
		SanityManager.ASSERT(predicateList != null,
							 "predicateList is expected to be non-null");

		/* Push single table predicates down to the left of an outer
		 * join, if possible.  (We need to be able to walk an entire
		 * join tree.)
		 */
		if (childResult instanceof JoinNode)
		{
			((FromTable) childResult).pushExpressions(predicateList);

		}

		/* Build a list of the single table predicates that we can push down */
        PredicateList pushPList =
            predicateList.getPushablePredicates(getReferencedTableMap());

		/* If this is a PRN above a SelectNode, probably due to a 
		 * view or derived table which couldn't be flattened, then see
		 * if we can push any of the predicates which just got pushed
		 * down to our level into the SelectNode.
		 */
		if (pushPList != null &&
				(childResult instanceof SelectNode))
		{
            SelectNode childSelect = (SelectNode)childResult;

            // We can't push down if there is a window
            // function because that would make ROW_NUMBER give wrong
            // result:
            // E.g.
            //     SELECT * from (SELECT ROW_NUMBER() OVER (), j FROM T
            //                    ORDER BY j) WHERE j=5
            //
            // Similarly, don't push if we have OFFSET and/or FETCH FROM.
            //
            if (childSelect.hasWindows() ||
                childSelect.hasOffsetFetchFirst()) {
            } else {
                pushPList.pushExpressionsIntoSelect((SelectNode) childResult,
                                                    false);
            }
        }


		/* DERBY-649: Push simple predicates into Unions. It would be up to UnionNode
		 * to decide if these predicates can be pushed further into underlying SelectNodes
		 * or UnionNodes.  Note, we also keep the predicateList at this
		 * ProjectRestrictNode in case the predicates are not pushable or only
		 * partially pushable.
		 *
		 * It is possible to expand this optimization in UnionNode later.
		 */
		if (pushPList != null && (childResult instanceof UnionNode))
			((UnionNode)childResult).pushExpressions(pushPList);

		if (restrictionList == null)
		{
			restrictionList = pushPList;
		}
		else if (pushPList != null && pushPList.size() != 0)
		{
			/* Concatenate the 2 PredicateLists */
			restrictionList.destructiveAppend(pushPList);
		}

		/* RESOLVE - this looks like the place to try to try to push the 
		 * predicates through the ProjectRestrict.  Seems like we should
		 * "rebind" the column references and reset the referenced table maps
		 * in restrictionList and then call childResult.pushExpressions() on
		 * restrictionList.
		 */
	}

	/**
	 * Add a new predicate to the list.  This is useful when doing subquery
	 * transformations, when we build a new predicate with the left side of
	 * the subquery operator and the subquery's result column.
	 *
	 * @param predicate		The predicate to add
	 *
	 * @return ResultSetNode	The new top of the tree.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    ResultSetNode addNewPredicate(Predicate predicate)
			throws StandardException
	{
		if (restrictionList == null)
		{
            restrictionList = new PredicateList(getContextManager());
		}
		restrictionList.addPredicate(predicate);
		return this;
	}

	/**
	 * Evaluate whether or not the subquery in a FromSubquery is flattenable.  
	 * Currently, a FSqry is flattenable if all of the following are true:
	 *		o  Subquery is a SelectNode. 
	 *		o  It contains no top level subqueries.  (RESOLVE - we can relax this)
	 *		o  It does not contain a group by or having clause
	 *		o  It does not contain aggregates.
	 *
	 * @param fromList	The outer from list
	 *
	 * @return boolean	Whether or not the FromSubquery is flattenable.
	 */
    @Override
    boolean flattenableInFromSubquery(FromList fromList)
	{
		/* Flattening currently involves merging predicates and FromLists.
		 * We don't have a FromList, so we can't flatten for now.
		 */
		/* RESOLVE - this will introduce yet another unnecessary PRN */
		return false;
	}

	/**
	 * Ensure that the top of the RSN tree has a PredicateList.
	 *
	 * @param numTables			The number of tables in the query.
	 * @return ResultSetNode	A RSN tree with a node which has a PredicateList on top.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    ResultSetNode ensurePredicateList(int numTables)
		throws StandardException
	{
		return this;
	}

	 

 
	 

    
	/**
	 * General logic shared by Core compilation.
	 *
	 * @param acb	The ExpressionClassBuilder for the class being built
	 * @param mb	The method the expression will go into
	 *
	 *
	 * @exception StandardException		Thrown on error
	 */

    @Override
    void generateResultSet(ExpressionClassBuilder acb, MethodBuilder mb)
									throws StandardException
	{
		generateMinion( acb, mb, true);
	}

	/**
	 * Logic shared by generate() and generateResultSet().
	 *
	 * @param acb	The ExpressionClassBuilder for the class being built
	 * @param mb	The method the expression will go into
	 *
	 * @exception StandardException		Thrown on error
	 */

	private void generateMinion(ExpressionClassBuilder acb,
									 MethodBuilder mb, boolean genChildResultSet)
									throws StandardException
	{ }

	/**
	 * Determine whether this ProjectRestrict does anything.  If it doesn't
	 * filter out any rows or columns, it's a No-Op.
	 *
	 * @return	true if this ProjectRestrict is a No-Op.
	 */
	boolean nopProjectRestrict()
	{
		/*
		** This ProjectRestrictNode is not a No-Op if it does any
		** restriction.
		*/
		if ( (restriction != null) || (constantRestriction != null) ||
			 (restrictionList != null && restrictionList.size() > 0) )
		{
			return false;
		}

		ResultColumnList	childColumns = childResult.getResultColumns();
		ResultColumnList	PRNColumns = this.getResultColumns();

		/*
		** The two lists have the same numbers of elements.  Are the lists
		** identical?  In other words, is the expression in every ResultColumn
		** in the PRN's RCL a ColumnReference that points to the same-numbered
		** column?
		*/
		if (PRNColumns.nopProjection(childColumns))
			return true;

		return false;
	}

	/**
	 * Bypass the generation of this No-Op ProjectRestrict, and just generate
	 * its child result set.
	 *
	 * @exception StandardException		Thrown on error
	 */
    void generateNOPProjectRestrict()
			throws StandardException
	{
		this.getResultColumns().setRedundant();
	}

	/**
	 * Consider materialization for this ResultSet tree if it is valid and cost effective
	 * (It is not valid if incorrect results would be returned.)
	 *
	 * @return Top of the new/same ResultSet tree.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    ResultSetNode considerMaterialization(JBitSet outerTables)
		throws StandardException
	{
		childResult = childResult.considerMaterialization(outerTables);
		if (childResult.performMaterialization(outerTables))
		{
			MaterializeResultSetNode	mrsn;
			ResultColumnList			prRCList;

			/* If the restriction contians a ColumnReference from another
			 * table then the MRSN must go above the childResult.  Otherwise we can put
			 * it above ourselves. (The later is optimal since projection and restriction 
			 * will only happen once.)
			 * Put MRSN above PRN if any of the following are true:
			 *	o  PRN doesn't have a restriction list
			 *	o  PRN's restriction list is empty 
			 *  o  Table's referenced in PRN's restriction list are a subset of
			 *	   table's referenced in PRN's childResult.  (NOTE: Rather than construct
			 *     a new, empty JBitSet before checking, we simply clone the childResult's
			 *	   referencedTableMap.  This is done for code simplicity and will not 
			 *	   affect the result.)
			 */
			ReferencedTablesVisitor rtv = new ReferencedTablesVisitor(
												(JBitSet) childResult.getReferencedTableMap().clone());
			boolean emptyRestrictionList = (restrictionList == null || restrictionList.size() == 0);
			if (! emptyRestrictionList)
			{
				restrictionList.accept(rtv);
			}
			if (emptyRestrictionList ||
				childResult.getReferencedTableMap().contains(rtv.getTableMap()))
			{
				/* We get a shallow copy of the ResultColumnList and its 
				 * ResultColumns.  (Copy maintains ResultColumn.expression for now.)
				 */
				prRCList = getResultColumns();
				setResultColumns(getResultColumns().copyListAndObjects());

				/* Replace ResultColumn.expression with new VirtualColumnNodes
				 * in the NormalizeResultSetNode's ResultColumnList.  (VirtualColumnNodes include
				 * pointers to source ResultSetNode, this, and source ResultColumn.)
				 */
				prRCList.genVirtualColumnNodes(this, getResultColumns());

				/* Finally, we create the new MaterializeResultSetNode */
                mrsn = new MaterializeResultSetNode(
									this,
									prRCList,
									tableProperties,
									getContextManager());
				// Propagate the referenced table map if it's already been created
				if (getReferencedTableMap() != null)
				{
					mrsn.setReferencedTableMap((JBitSet) getReferencedTableMap().clone());
				}
				return mrsn;
			}
			else
			{
				/* We get a shallow copy of the ResultColumnList and its 
				 * ResultColumns.  (Copy maintains ResultColumn.expression for now.)
				 */
				prRCList = childResult.getResultColumns();
				childResult.setResultColumns(prRCList.copyListAndObjects());

				/* Replace ResultColumn.expression with new VirtualColumnNodes
				 * in the MaterializeResultSetNode's ResultColumnList.  (VirtualColumnNodes include
				 * pointers to source ResultSetNode, this, and source ResultColumn.)
				 */
				prRCList.genVirtualColumnNodes(childResult, childResult.getResultColumns());

				/* RESOLVE - we need to push single table predicates down so that
				 * they get applied while building the MaterializeResultSet.
				 */

                mrsn = new MaterializeResultSetNode(
									childResult,
									prRCList,
									tableProperties,
									getContextManager());
				// Propagate the referenced table map if it's already been created
				if (childResult.getReferencedTableMap() != null)
				{
					mrsn.setReferencedTableMap((JBitSet) childResult.getReferencedTableMap().clone());
				}
				childResult = mrsn;
			}
		}

		return this;
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
		return childResult.getFromTableByName(name, schemaName, exactMatch);
	}

	/**
	 * Get the lock mode for the target of an update statement
	 * (a delete or update).  The update mode will always be row for
	 * CurrentOfNodes.  It will be table if there is no where clause.
	 *
	 * @return	The lock mode
	 */
    @Override
    int updateTargetLockMode()
	{
		if (restriction != null || constantRestriction != null)
		{
			return TransactionController.MODE_RECORD;
		}
		else
		{
			return childResult.updateTargetLockMode();
		}
	}

	/**
	 * Is it possible to do a distinct scan on this ResultSet tree.
	 * (See SelectNode for the criteria.)
	 *
	 * @param distinctColumns the set of distinct columns
	 * @return Whether or not it is possible to do a distinct scan on this ResultSet tree.
	 */
    @Override
    boolean isPossibleDistinctScan(Set<BaseColumnNode> distinctColumns)
	{
		if (restriction != null || 
			(restrictionList != null && restrictionList.size() != 0))
		{
			return false;
		}

		HashSet<BaseColumnNode> columns = new HashSet<BaseColumnNode>();

        for (ResultColumn rc : getResultColumns()) {
			BaseColumnNode bc = rc.getBaseColumnNode();
			if (bc == null) return false;
			columns.add(bc);
		}

		return columns.equals(distinctColumns) && childResult.isPossibleDistinctScan(distinctColumns);
	}

	/**
	 * Mark the underlying scan as a distinct scan.
	 */
    @Override
	void markForDistinctScan()
	{
		childResult.markForDistinctScan();
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

		if (restriction != null)
		{
			restriction = (ValueNode)restriction.accept(v);
		}

		if (restrictionList != null)
		{
			restrictionList = (PredicateList)restrictionList.accept(v);
		}
	}



	/**
	 * set the Information gathered from the parent table that is 
     * required to perform a referential action on dependent table.
	 */
    @Override
    void setRefActionInfo(long fkIndexConglomId,
								 int[]fkColArray, 
								 String parentResultSetId,
								 boolean dependentScan)
	{
		childResult.setRefActionInfo(fkIndexConglomId,
								   fkColArray,
								   parentResultSetId,
								   dependentScan);
	}

    void setRestriction(ValueNode restriction) {
		this.restriction = restriction;
	}

    @Override
    public void pushQueryExpressionSuffix() {
        childResult.pushQueryExpressionSuffix();
    }


	/**
	 * Push the order by list down from InsertNode into its child result set so
	 * that the optimizer has all of the information that it needs to consider
	 * sort avoidance.
	 *
	 * @param orderByList	The order by list
	 */
    @Override
	void pushOrderByList(OrderByList orderByList)
	{
		childResult.pushOrderByList(orderByList);
	}

    /**
     * Push down the offset and fetch first parameters, if any, to the
     * underlying child result set.
     *
     * @param offset    the OFFSET, if any
     * @param fetchFirst the OFFSET FIRST, if any
     * @param hasJDBClimitClause true if the clauses were added by (and have the semantics of) a JDBC limit clause
     */
    @Override
    void pushOffsetFetchFirst( ValueNode offset, ValueNode fetchFirst, boolean hasJDBClimitClause )
    {
        childResult.pushOffsetFetchFirst( offset, fetchFirst, hasJDBClimitClause );
    }

    void setValidatingCheckConstraints( String baseTableUUIDString ) {
        validatingCheckConstraints = true;
        validatingBaseTableUUIDString = baseTableUUIDString;
    }
}
