/*

   Derby - Class org.apache.derby.impl.sql.compile.IndexToBaseRowNode

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

import java.util.List;
import java.util.Properties;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.sql.compile.AccessPath;
import org.apache.derby.iapi.sql.compile.Optimizable;
import org.apache.derby.iapi.sql.compile.RequiredRowOrdering;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;

/**
 * This node type translates an index row to a base row.  It takes a
 * FromBaseTable as its source ResultSetNode, and generates an
 * IndexRowToBaseRowResultSet that takes a TableScanResultSet on an
 * index conglomerate as its source.
 */
class IndexToBaseRowNode extends FromTable
{
	protected FromBaseTable	source;
	protected ConglomerateDescriptor	baseCD;
	protected boolean	cursorTargetTable;
	protected PredicateList restrictionList;
	protected boolean	forUpdate;
	private FormatableBitSet	heapReferencedCols;
	private FormatableBitSet	indexReferencedCols;
	private FormatableBitSet	allReferencedCols;
	private FormatableBitSet	heapOnlyReferencedCols;

    IndexToBaseRowNode(
            FromBaseTable    source,
            ConglomerateDescriptor  baseCD,
            ResultColumnList resultColumns,
            boolean          cursorTargetTable,
            FormatableBitSet heapReferencedCols,
            FormatableBitSet indexReferencedCols,
            PredicateList    restrictionList,
            boolean          forUpdate,
            Properties       tableProperties,
            ContextManager   cm)
	{
        super(null, tableProperties, cm);
        this.source = source;
        this.baseCD = baseCD;
        setResultColumns( resultColumns );
        this.cursorTargetTable = cursorTargetTable;
        this.restrictionList = restrictionList;
        this.forUpdate = forUpdate;
        this.heapReferencedCols = heapReferencedCols;
        this.indexReferencedCols = indexReferencedCols;

		if (this.indexReferencedCols == null) {
			this.allReferencedCols = this.heapReferencedCols;
			heapOnlyReferencedCols = this.heapReferencedCols;
		}
		else {
			this.allReferencedCols =
				new FormatableBitSet(this.heapReferencedCols);
			this.allReferencedCols.or(this.indexReferencedCols);
			heapOnlyReferencedCols =
				new FormatableBitSet(allReferencedCols);
			heapOnlyReferencedCols.xor(this.indexReferencedCols);
		}
	}

	/** @see Optimizable#forUpdate */
    @Override
	public boolean forUpdate()
	{
		return source.forUpdate();
	}

	/** @see Optimizable#getTrulyTheBestAccessPath */
    @Override
	public AccessPath getTrulyTheBestAccessPath()
	{
		// Get AccessPath comes from base table.
		return ((Optimizable) source).getTrulyTheBestAccessPath();
	}

      
	/**
	 * Return whether or not the underlying ResultSet tree
	 * is ordered on the specified columns.
	 * RESOLVE - This method currently only considers the outermost table 
	 * of the query block.
	 *
	 * @param	crs					The specified ColumnReference[]
	 * @param	permuteOrdering		Whether or not the order of the CRs in the array can be permuted
     * @param   fbtHolder           List that is to be filled with the FromBaseTable
	 *
	 * @return	Whether the underlying ResultSet tree
	 * is ordered on the specified column.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    boolean isOrderedOn(ColumnReference[] crs, boolean permuteOrdering, List<FromBaseTable> fbtHolder)
				throws StandardException
	{
        return source.isOrderedOn(crs, permuteOrdering, fbtHolder);
	}
 
	/**
	 * Return whether or not the underlying ResultSet tree will return
	 * a single row, at most.
	 * This is important for join nodes where we can save the extra next
	 * on the right side if we know that it will return at most 1 row.
	 *
	 * @return Whether or not the underlying ResultSet tree will return a single row.
	 * @exception StandardException		Thrown on error
	 */
    @Override
    boolean isOneRowResultSet() throws StandardException
	{
		// Default is false
		return source.isOneRowResultSet();
	}

	/**
	 * Return whether or not the underlying FBT is for NOT EXISTS.
	 *
	 * @return Whether or not the underlying FBT is for NOT EXISTS.
	 */
    @Override
    boolean isNotExists()
	{
		return source.isNotExists();
	}

	/**
	 * Decrement (query block) level (0-based) for this FromTable.
	 * This is useful when flattening a subquery.
	 *
	 * @param decrement	The amount to decrement by.
	 */
    @Override
	void decrementLevel(int decrement)
	{
		source.decrementLevel(decrement);
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
		return source.updateTargetLockMode();
	}

	/**
	 * @see ResultSetNode#adjustForSortElimination
	 */
    @Override
	void adjustForSortElimination()
	{
		/* NOTE: We use a different method to tell a FBT that
		 * it cannot do a bulk fetch as the ordering issues are
		 * specific to a FBT being under an IRTBR as opposed to a
		 * FBT being under a PRN, etc.
		 */
		source.disableBulkFetch();
	}

	/**
	 * @see ResultSetNode#adjustForSortElimination
	 */
    @Override
	void adjustForSortElimination(RequiredRowOrdering rowOrdering)
		throws StandardException
	{
		/* rowOrdering is not important to this specific node, so
		 * just call the no-arg version of the method.
		 */
		adjustForSortElimination();

		/* Now pass the rowOrdering down to source, which may
		 * need to do additional work. DERBY-3279.
		 */
		source.adjustForSortElimination(rowOrdering);
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

		if (source != null)
		{
			source = (FromBaseTable)source.accept(v);
		}
	}

}
