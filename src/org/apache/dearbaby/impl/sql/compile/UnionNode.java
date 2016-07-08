/*

   Derby - Class org.apache.derby.impl.sql.compile.UnionNode

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

import java.util.HashMap;
import java.util.Properties;

import org.apache.dearbaby.util.QueryUtil;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.util.JBitSet;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A UnionNode represents a UNION in a DML statement.  It contains a boolean
 * telling whether the union operation should eliminate duplicate rows.
 *
 */

class UnionNode extends SetOperatorNode
{
	/* Only optimize it once */
	/* Only call addNewNodes() once */
	private boolean addNewNodesCalled;

	/* Is this a UNION ALL generated for a table constructor -- a VALUES expression with multiple rows. */
	boolean			tableConstructor;

	/* True if this is the top node of a table constructor */
	boolean			topTableConstructor;


	/**
     * Constructor for a UnionNode.
	 *
	 * @param leftResult		The ResultSetNode on the left side of this union
	 * @param rightResult		The ResultSetNode on the right side of this union
	 * @param all				Whether or not this is a UNION ALL.
	 * @param tableConstructor	Whether or not this is from a table constructor.
	 * @param tableProperties	Properties list associated with the table
	 *
	 * @exception StandardException		Thrown on error
	 */

    UnionNode(ResultSetNode  leftResult,
              ResultSetNode  rightResult,
              boolean        all,
              boolean        tableConstructor,
              Properties     tableProperties,
              ContextManager cm) throws StandardException {

        super(leftResult, rightResult, all, tableProperties, cm);

        // Is this a UNION ALL for a table constructor?
        this.tableConstructor = tableConstructor;
    }

	/**
	 * Mark this as the top node of a table constructor.
	 */
    void markTopTableConstructor()
	{
		topTableConstructor = true;
	}

	/**
	 * Tell whether this is a UNION for a table constructor.
	 */
	boolean tableConstructor()
	{
		return tableConstructor;
	}

	/**
	 * Check for (and reject) ? parameters directly under the ResultColumns.
	 * This is done for SELECT statements.  Don't reject parameters that
	 * are in a table constructor - these are allowed, as long as the
	 * table constructor is in an INSERT statement or each column of the
	 * table constructor has at least one non-? column.  The latter case
	 * is checked below, in bindExpressions().
	 *
	 * @exception StandardException		Thrown if a ? parameter found
	 *									directly under a ResultColumn
	 */
    @Override
    void rejectParameters() throws StandardException
	{
		if ( ! tableConstructor())
			super.rejectParameters();
	}

	/**
	 * Set the type of column in the result column lists of each
	 * source of this union tree to the type in the given result column list
	 * (which represents the result columns for an insert).
	 * This is only for table constructors that appear in insert statements.
	 *
	 * @param typeColumns	The ResultColumnList containing the desired result
	 *						types.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
	void setTableConstructorTypes(ResultColumnList typeColumns)
			throws StandardException
	{
		if (SanityManager.DEBUG)
		{
			SanityManager.ASSERT(getResultColumns().size() <= typeColumns.size(),
				"More columns in ResultColumnList than in base table.");
		}

		ResultSetNode	rsn;

		/*
		** Should only set types of ? parameters to types of result columns
		** if it's a table constructor.
		*/
		if (tableConstructor())
		{
			/* By looping through the union nodes, we avoid recursion */
			for (rsn = this; rsn instanceof UnionNode; )
			{
				UnionNode union = (UnionNode) rsn;

				/*
				** Assume that table constructors are left-deep trees of UnionNodes
				** with RowResultSet nodes on the right.
				*/
				if (SanityManager.DEBUG)
					SanityManager.ASSERT(
						union.rightResultSet instanceof RowResultSetNode,
						"A " + union.rightResultSet.getClass().getName() +
						" is on the right of a union in a table constructor");

				((RowResultSetNode) union.rightResultSet).setTableConstructorTypes(
																typeColumns);

				rsn = union.leftResultSet;
			}

			/* The last node on the left should be a result set node */
			if (SanityManager.DEBUG)
				SanityManager.ASSERT(rsn instanceof RowResultSetNode,
					"A " + rsn.getClass().getName() +
					" is at the left end of a table constructor");

			((RowResultSetNode) rsn).setTableConstructorTypes(typeColumns);
		}
	}

	/**
	 * Make the RCL of this node match the target node for the insert. If this
	 * node represents a table constructor (a VALUES clause), we replace the
	 * RCL with an enhanced one if necessary, and recursively enhance the RCL
	 * of each child node. For table constructors, we also need to check that
	 * we don't attempt to override auto-increment columns in each child node
	 * (checking the top-level RCL isn't sufficient since a table constructor
	 * may contain the DEFAULT keyword, which makes it possible to specify a
	 * column without overriding its value).
	 *
	 * If this node represents a regular UNION, put a ProjectRestrictNode on
	 * top of this node and enhance the RCL in that node.
	 */
    @Override
	ResultSetNode enhanceRCLForInsert(
			InsertNode target, boolean inOrder, int[] colMap)
		throws StandardException
	{
		if (tableConstructor()) {
			leftResultSet = target.enhanceAndCheckForAutoincrement
                ( leftResultSet, inOrder, colMap, false );
			rightResultSet = target.enhanceAndCheckForAutoincrement
                ( rightResultSet, inOrder, colMap, false );
			if (!inOrder ||
                getResultColumns().size() < target.resultColumnList.size()) {
				setResultColumns( getRCLForInsert(target, colMap) );
			}
			return this;
		} else {
			// This is a regular UNION, so fall back to the default
			// implementation that adds a ProjectRestrictNode on top.
			return super.enhanceRCLForInsert(target, inOrder, colMap);
		}
	}

	 
	/**
	 * DERBY-649: Handle pushing predicates into UnionNodes. It is possible to push
	 * single table predicates that are binaryOperations or inListOperations. 
	 *
	 * Predicates of the form <columnReference> <RELOP> <constant> or <columnReference>
	 * IN <constantList> are currently handled. Since these predicates would allow
	 * optimizer to pick available indices, pushing them provides maximum benifit.
	 *
	 * It should be possible to expand this logic to cover more cases. Even pushing
	 * expressions (like a+b = 10) into SELECTs would improve performance, even if
	 * they don't allow use of index. It would mean evaluating expressions closer to
	 * data and hence could avoid sorting or other overheads that UNION may require.
	 *
	 * Note that the predicates are not removed after pushing. This is to ensure if
	 * pushing is not possible or only partially feasible.
	 *
	 * @param 	predicateList		List of single table predicates to push
	 *
	 * @exception	StandardException		Thrown on error
	 */
    @Override
    void pushExpressions(PredicateList predicateList)
					throws StandardException
	{
		// If left or right side is a UnionNode, further push the predicate list
		// Note, it is OK not to push these predicates since they are also evaluated
		// in the ProjectRestrictNode. There are other types of operations possible
		// here in addition to UnionNode or SelectNode, like RowResultSetNode.
		if (leftResultSet instanceof UnionNode)
			((UnionNode)leftResultSet).pushExpressions(predicateList);
		else if (leftResultSet instanceof SelectNode)
			predicateList.pushExpressionsIntoSelect((SelectNode)leftResultSet, true);

		if (rightResultSet instanceof UnionNode)
			((UnionNode)rightResultSet).pushExpressions(predicateList);
		else if (rightResultSet instanceof SelectNode)
			predicateList.pushExpressionsIntoSelect((SelectNode)rightResultSet, true);
	}
 
	/**
	 * Add any new ResultSetNodes that are necessary to the tree.
	 * We wait until after optimization to do this in order to
	 * make it easier on the optimizer.
	 *
	 * @return (Potentially new) head of the ResultSetNode tree.
	 *
	 * @exception StandardException		Thrown on error
	 */
	private ResultSetNode addNewNodes()
		throws StandardException
	{
		ResultSetNode treeTop = this;

		/* Only call addNewNodes() once */
		if (addNewNodesCalled)
		{
			return this;
		}

		addNewNodesCalled = true;

		/* RESOLVE - We'd like to generate any necessary NormalizeResultSets
		 * above our children here, in the tree.  However, doing so causes
		 * the following query to fail because the where clause goes against
		 * the NRS instead of the Union:
		 *		SELECT TABLE_TYPE
		 *		FROM SYS.SYSTABLES, 
		 *			(VALUES ('T','TABLE') ,
		 *				('S','SYSTEM TABLE') , ('V', 'VIEW')) T(TTABBREV,TABLE_TYPE) 
		 *		WHERE TTABBREV=TABLETYPE;
		 * Thus, we are forced to skip over generating the nodes in the tree
		 * and directly generate the execution time code in generate() instead.
		 * This solves the problem for some unknown reason.
		 */

		/* Simple solution (for now) to eliminating duplicates - 
		 * generate a distinct above the union.
		 */
		if (! all)
		{
			/* We need to generate a NormalizeResultSetNode above us if the column
			 * types and lengths don't match.  (We need to do it here, since they
			 * will end up agreeing in the PRN, which will be the immediate
			 * child of the DistinctNode, which means that the NormalizeResultSet
			 * won't get generated above the PRN.)
			 */
			if (! columnTypesAndLengthsMatch())
			{
                treeTop = new NormalizeResultSetNode(
                        treeTop, null, null, false, getContextManager());
			}

            treeTop = new DistinctNode(treeTop.genProjectRestrict(),
                                       false,
                                       tableProperties,
                                       getContextManager());
			/* HACK - propagate our table number up to the new DistinctNode
			 * so that arbitrary hash join will work correctly.  (Otherwise it
			 * could have a problem dividing up the predicate list at the end
			 * of modifyAccessPath() because the new child of the PRN above
			 * us would have a tableNumber of -1 instead of our tableNumber.)
			 */
			((FromTable)treeTop).setTableNumber(tableNumber);
			treeTop.setReferencedTableMap((JBitSet) getReferencedTableMap().clone());
			all = true;
		}

		/* Generate the OrderByNode if a sort is still required for
		 * the order by.
		 */
        for (int i=0; i < qec.size(); i++) {
            final OrderByList obl = qec.getOrderByList(i);

            if (obl != null)
            {
                treeTop = new OrderByNode(treeTop,
                                          obl,
                                          tableProperties,
                                          getContextManager());
            }

            // Do this only after the main ORDER BY; any extra added by
            final ValueNode offset = qec.getOffset(i);
            final ValueNode fetchFirst = qec.getFetchFirst(i);

            if (offset != null || fetchFirst != null) {
                ResultColumnList newRcl =
                        treeTop.getResultColumns().copyListAndObjects();
                newRcl.genVirtualColumnNodes(treeTop,
                                             treeTop.getResultColumns());

                treeTop = new RowCountNode(
                        treeTop,
                        newRcl,
                        offset,
                        fetchFirst,
                        qec.getHasJDBCLimitClause()[i].booleanValue(),
                        getContextManager());
            }
        }

		return treeTop;
	}

	/* left result will fetch */
	private boolean leftFetch=true;
	
	
	@Override
	public void genQuery0() {
		leftResultSet.genQuery(qm);
		rightResultSet.genQuery(qm);
	}

	@Override
	public void exeQuery0() {
		leftResultSet.exeQuery();
		rightResultSet.exeQuery();
	}

	@Override
	public boolean fetch() {
		if(leftFetch==true){
			leftFetch= leftResultSet.fetch();
		}
		
		if(leftFetch==true){
			return leftFetch;
		}
		return rightResultSet.fetch();
	}

	@Override
	public boolean match() {
		if( leftFetch==true)
			return leftResultSet.match();
		return rightResultSet.match();
	}
	
	@Override
	public Object getColVal(String tbl , String col) {
		if( leftFetch==true)
			return leftResultSet.getColVal(tbl,col);
		return rightResultSet.getColVal(tbl,col);
	}
	
	
	public void fetchInit() {	
		leftFetch=true;
		leftResultSet.fetchInit();
		rightResultSet.fetchInit();
	}
	
	 /*获取行信息*/
		@Override
	    public HashMap getMatchRow(){
			if( leftFetch==true)
				return leftResultSet.getMatchRow( );
			return rightResultSet.getMatchRow( );
	    }
		
		@Override
	    public HashMap getMatchRow(String _alias){
			if( leftFetch==true)
				return leftResultSet.getMatchRow( _alias);
			return rightResultSet.getMatchRow( _alias);
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
			return 	"tableConstructor: " + tableConstructor + "\n" + super.toString();
		}
		else
		{
			return "";
		}
	}
 
     

    String getOperatorName()
    {
        return "UNION";
    }
}
