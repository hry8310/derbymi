/*

   Derby - Class org.apache.derby.impl.sql.compile.FromTable

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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.sql.compile.AccessPath; 
import org.apache.derby.iapi.sql.compile.JoinStrategy;
import org.apache.derby.iapi.sql.compile.Optimizable;
import org.apache.derby.iapi.sql.compile.OptimizablePredicate;
import org.apache.derby.iapi.sql.compile.OptimizablePredicateList;
import org.apache.derby.iapi.sql.compile.Optimizer;
import org.apache.derby.iapi.sql.compile.RowOrdering;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.util.JBitSet;
import org.apache.derby.iapi.util.StringUtil; 
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A FromTable represents a table in the FROM clause of a DML statement. It can
 * be either a base table, a subquery or a project restrict.
 *
 * @see FromBaseTable
 * @see FromSubquery
 * @see ProjectRestrictNode
 *
 */
abstract class FromTable extends ResultSetNode {
	Properties tableProperties;
	public String correlationName;
	 TableName corrTableName;
	int tableNumber;
	/* (Query block) level is 0-based. */
	/*
	 * RESOLVE - View resolution will have to update the level within the view
	 * tree.
	 */
	int level;
	// hashKeyColumns are 0-based column #s within the row returned by the store
	// for hash scans
	int[] hashKeyColumns;
 

	AccessPathImpl currentAccessPath;
	AccessPathImpl bestAccessPath;
	AccessPathImpl bestSortAvoidancePath;
	AccessPathImpl trulyTheBestAccessPath;

	private int joinStrategyNumber;

	protected String userSpecifiedJoinStrategy;
 

	private double perRowUsage = -1;

	private boolean considerSortAvoidancePath;

	/**
	 * Set of object->trulyTheBestAccessPath mappings used to keep track of
	 * which of this Optimizable's "trulyTheBestAccessPath" was the best with
	 * respect to a specific outer query or ancestor node. In the case of an
	 * outer query, the object key will be an instance of OptimizerImpl. In the
	 * case of an ancestor node, the object key will be that node itself. Each
	 * ancestor node or outer query could potentially have a different idea of
	 * what this Optimizable's "best access path" is, so we have to keep track
	 * of them all.
	 */
	private HashMap<Object, AccessPathImpl> bestPlanMap;

	/** Operations that can be performed on bestPlanMap. */
	protected static final short REMOVE_PLAN = 0;
	protected static final short ADD_PLAN = 1;
	protected static final short LOAD_PLAN = 2;

	/** the original unbound table name */
	protected TableName origTableName;

	/** for resolving column references in MERGE statements in tough cases */
	private int _mergeTableID = ColumnReference.MERGE_UNKNOWN;

	/**
	 * Constructor for a table in a FROM list.
	 *
	 * @param correlationName
	 *            The correlation name
	 * @param tableProperties
	 *            Properties list associated with the table
	 * @param cm
	 *            The context manager
	 */
	FromTable(String correlationName, Properties tableProperties,
			ContextManager cm) {
		super(cm);
		this.correlationName = correlationName;
		this.tableProperties = tableProperties;
		tableNumber = -1;
		bestPlanMap = null;
	}

	/**
	 * Get this table's correlation name, if any.
	 */
	public String getCorrelationName() {
		return correlationName;
	}

	/*
	 * Optimizable interface
	 */


	/**
	 * @see Optimizable#nextAccessPath
	 * @exception StandardException
	 *                Thrown on error
	 */
	public boolean nextAccessPath(Optimizer optimizer,
			OptimizablePredicateList predList, RowOrdering rowOrdering)
			throws StandardException {
		int numStrat = optimizer.getNumberOfJoinStrategies();
		boolean found = false;
		AccessPath ap = getCurrentAccessPath();

		/*
		 * * Most Optimizables have no ordering, so tell the rowOrdering that*
		 * this Optimizable is unordered, if appropriate.
		 */
		if (userSpecifiedJoinStrategy != null) {
			/*
			 * * User specified a join strategy, so we should look at only one*
			 * strategy. If there is a current strategy, we have already* looked
			 * at the strategy, so go back to null.
			 */
			if (ap.getJoinStrategy() != null) {
				ap.setJoinStrategy((JoinStrategy) null);

				found = false;
			} else {
				ap.setJoinStrategy(optimizer
						.getJoinStrategy(userSpecifiedJoinStrategy));

				if (ap.getJoinStrategy() == null) {
					throw StandardException.newException(
							SQLState.LANG_INVALID_JOIN_STRATEGY,
							userSpecifiedJoinStrategy, getBaseTableName());
				}

				found = true;
			}
		} else if (joinStrategyNumber < numStrat) {
			/* Step through the join strategies. */
			ap.setJoinStrategy(optimizer.getJoinStrategy(joinStrategyNumber));

			joinStrategyNumber++;

			found = true;

			if (optimizerTracingIsOn()) {
				getOptimizerTracer().traceConsideringJoinStrategy(
						ap.getJoinStrategy(), tableNumber);
			}
		}

		/*
		 * * Tell the RowOrdering about columns that are equal to constant*
		 * expressions.
		 */
		tellRowOrderingAboutConstantColumns(rowOrdering, predList);

		return found;
	}

	/** Most Optimizables cannot be ordered */
	protected boolean canBeOrdered() {
		return false;
	}

	/** @see Optimizable#getCurrentAccessPath */
	public AccessPath getCurrentAccessPath() {
		return currentAccessPath;
	}

	/** @see Optimizable#getBestAccessPath */
	public AccessPath getBestAccessPath() {
		return bestAccessPath;
	}

	/** @see Optimizable#getBestSortAvoidancePath */
	public AccessPath getBestSortAvoidancePath() {
		return bestSortAvoidancePath;
	}

	/** @see Optimizable#getTrulyTheBestAccessPath */
	public AccessPath getTrulyTheBestAccessPath() {
		return trulyTheBestAccessPath;
	}

	/** @see Optimizable#rememberSortAvoidancePath */
	public void rememberSortAvoidancePath() {
		considerSortAvoidancePath = true;
	}

	/** @see Optimizable#considerSortAvoidancePath */
	public boolean considerSortAvoidancePath() {
		return considerSortAvoidancePath;
	}

	/** @see Optimizable#rememberJoinStrategyAsBest */
	public void rememberJoinStrategyAsBest(AccessPath ap) {
		Optimizer opt = ap.getOptimizer();

		ap.setJoinStrategy(getCurrentAccessPath().getJoinStrategy());

		if (optimizerTracingIsOn()) {
			getOptimizerTracer().traceRememberingJoinStrategy(
					getCurrentAccessPath().getJoinStrategy(), tableNumber);
		}

		if (ap == bestAccessPath) {
			if (optimizerTracingIsOn()) {
				getOptimizerTracer().traceRememberingBestAccessPathSubstring(
						ap, tableNumber);
			}
		} else if (ap == bestSortAvoidancePath) {
			if (optimizerTracingIsOn()) {
				getOptimizerTracer()
						.traceRememberingBestSortAvoidanceAccessPathSubstring(
								ap, tableNumber);
			}
		} else {
			/*
			 * We currently get here when optimizing an outer join. (Problem
			 * predates optimizer trace change.) RESOLVE - fix this at some
			 * point. if (SanityManager.DEBUG) { SanityManager.THROWASSERT(
			 * "unknown access path type"); }
			 */
			if (optimizerTracingIsOn()) {
				getOptimizerTracer()
						.traceRememberingBestUnknownAccessPathSubstring(ap,
								tableNumber);
			}
		}
	}

	/** @see Optimizable#getTableDescriptor */
	public TableDescriptor getTableDescriptor() {
		if (SanityManager.DEBUG) {
			SanityManager
					.THROWASSERT("getTableDescriptor() not expected to be called for "
							+ getClass().toString());
		}

		return null;
	}

	/**
	 * @see org.apache.derby.iapi.sql.compile.Optimizable#pushOptPredicate
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */

	public boolean pushOptPredicate(OptimizablePredicate optimizablePredicate)
			throws StandardException {
		return false;
	}

	/**
	 * @see Optimizable#pullOptPredicates
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public void pullOptPredicates(OptimizablePredicateList optimizablePredicates)
			throws StandardException {
		/* For most types of Optimizable, do nothing */
	}

	 

	/**
	 * @see Optimizable#isCoveringIndex
	 * @exception StandardException
	 *                Thrown on error
	 */
	public boolean isCoveringIndex(ConglomerateDescriptor cd)
			throws StandardException {
		return false;
	}

	/** @see Optimizable#getProperties */
	public Properties getProperties() {
		return tableProperties;
	}

	/** @see Optimizable#setProperties */
	public void setProperties(Properties tableProperties) {
		this.tableProperties = tableProperties;
	}

	/**
	 * @see Optimizable#verifyProperties
	 * @exception StandardException
	 *                Thrown on error
	 */
	public void verifyProperties(DataDictionary dDictionary)
			throws StandardException {
		if (tableProperties == null) {
			return;
		}
		/*
		 * Check here for: invalid properties key invalid joinStrategy invalid
		 * value for hashInitialCapacity invalid value for hashLoadFactor
		 * invalid value for hashMaxCapacity
		 */
		Enumeration<?> e = tableProperties.keys();

		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			String value = (String) tableProperties.get(key);

			if (key.equals("joinStrategy")) {
				userSpecifiedJoinStrategy = StringUtil.SQLToUpperCase(value);
			} else if (key.equals("hashInitialCapacity")) {
				initialCapacity = getIntProperty(value, key);

				// verify that the specified value is valid
				if (initialCapacity <= 0) {
					throw StandardException.newException(
							SQLState.LANG_INVALID_HASH_INITIAL_CAPACITY,
							String.valueOf(initialCapacity));
				}
			} else if (key.equals("hashLoadFactor")) {
				try {
					loadFactor = Float.parseFloat(value);
				} catch (NumberFormatException nfe) {
					throw StandardException.newException(
							SQLState.LANG_INVALID_NUMBER_FORMAT_FOR_OVERRIDE,
							value, key);
				}

				// verify that the specified value is valid
				if (loadFactor <= 0.0 || loadFactor > 1.0) {
					throw StandardException.newException(
							SQLState.LANG_INVALID_HASH_LOAD_FACTOR, value);
				}
			} else if (key.equals("hashMaxCapacity")) {
				maxCapacity = getIntProperty(value, key);

				// verify that the specified value is valid
				if (maxCapacity <= 0) {
					throw StandardException.newException(
							SQLState.LANG_INVALID_HASH_MAX_CAPACITY,
							String.valueOf(maxCapacity));
				}
			} else {
				// No other "legal" values at this time
				throw StandardException.newException(
						SQLState.LANG_INVALID_FROM_TABLE_PROPERTY, key,
						"joinStrategy");
			}
		}
	}

	/**
	 * @see Optimizable#getName
	 * @exception StandardException
	 *                Thrown on error
	 */
	public String getName() throws StandardException {
		return getExposedName();
	}

	/** @see Optimizable#getBaseTableName */
	public String getBaseTableName() {
		return "";
	}

	/** @see Optimizable#convertAbsoluteToRelativeColumnPosition */
	public int convertAbsoluteToRelativeColumnPosition(int absolutePosition) {
		return absolutePosition;
	}

	/** @see Optimizable#updateBestPlanMap */
	public void updateBestPlanMap(short action, Object planKey)
			throws StandardException {
		if (action == REMOVE_PLAN) {
			if (bestPlanMap != null) {
				bestPlanMap.remove(planKey);
				if (bestPlanMap.isEmpty()) {
					bestPlanMap = null;
				}
			}

			return;
		}

		AccessPath bestPath = getTrulyTheBestAccessPath();
		AccessPathImpl ap = null;
		if (action == ADD_PLAN) {
			// If we get to this method before ever optimizing this node, then
			// there will be no best path--so there's nothing to do.
			if (bestPath == null)
				return;

			// If the bestPlanMap already exists, search for an
			// AccessPath for the received key and use that if we can.
			if (bestPlanMap == null)
				bestPlanMap = new HashMap<Object, AccessPathImpl>();
			else
				ap = bestPlanMap.get(planKey);

			// If we don't already have an AccessPath for the key,
			// create a new one. If the key is an OptimizerImpl then
			// we might as well pass it in to the AccessPath constructor;
			// otherwise just pass null.
			if (ap == null) {
				if (planKey instanceof Optimizer)
					ap = new AccessPathImpl((Optimizer) planKey);
				else
					ap = new AccessPathImpl((Optimizer) null);
			}

			ap.copy(bestPath);
			bestPlanMap.put(planKey, ap);
			return;
		}

		// If we get here, we want to load the best plan from our map
		// into this Optimizable's trulyTheBestAccessPath field.

		// If we don't have any plans saved, then there's nothing to load.
		// This can happen if the key is an OptimizerImpl that tried some
		// join order for which there was no valid plan.
		if (bestPlanMap == null)
			return;

		ap = bestPlanMap.get(planKey);

		// It might be the case that there is no plan stored for
		// the key, in which case there's nothing to load.
		if ((ap == null) || (ap.getCostEstimate() == null))
			return;

		// We found a best plan in our map, so load it into this Optimizable's
		// trulyTheBestAccessPath field.
		bestPath.copy(ap);
	}

	 
 

	/**
	 * This method is called when this table is placed in a potential join
	 * order, or when a new conglomerate is being considered. Set this join
	 * strategy number to 0 to indicate that no join strategy has been
	 * considered for this table yet.
	 */
	protected void resetJoinStrategies(Optimizer optimizer) {
		joinStrategyNumber = 0;
		getCurrentAccessPath().setJoinStrategy((JoinStrategy) null);
	}

	 
	 
	/** @see Optimizable#isBaseTable */
	public boolean isBaseTable() {
		return false;
	}

	/**
	 * Check if any columns containing large objects (BLOBs or CLOBs) are
	 * referenced in this table.
	 *
	 * @return {@code true} if at least one large object column is referenced,
	 *         {@code false} otherwise
	 */
	public boolean hasLargeObjectColumns() {
		for (ResultColumn rc : getResultColumns()) {
			if (rc.isReferenced()) {
				DataTypeDescriptor type = rc.getType();
				if (type != null && type.getTypeId().isLOBTypeId()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @see Optimizable#isMaterializable
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public boolean isMaterializable() throws StandardException {
		/*
		 * Derived tables are materializable iff they are not correlated with an
		 * outer query block.
		 */

		HasCorrelatedCRsVisitor visitor = new HasCorrelatedCRsVisitor();
		accept(visitor);
		return !(visitor.hasCorrelatedCRs());
	}

	/** @see Optimizable#supportsMultipleInstantiations */
	public boolean supportsMultipleInstantiations() {
		return true;
	}

	/** @see Optimizable#getTableNumber */
	public int getTableNumber() {
		return tableNumber;
	}

	/** @see Optimizable#hasTableNumber */
	public boolean hasTableNumber() {
		return tableNumber >= 0;
	}

	/** @see Optimizable#forUpdate */
	public boolean forUpdate() {
		return false;
	}

	/** @see Optimizable#initialCapacity */
	public int initialCapacity() {
		if (SanityManager.DEBUG) {
			SanityManager.THROWASSERT("Not expected to be called");
		}

		return 0;
	}

	/** @see Optimizable#loadFactor */
	public float loadFactor() {
		if (SanityManager.DEBUG) {
			SanityManager.THROWASSERT("Not expected to be called");
		}

		return 0.0F;
	}

	/** @see Optimizable#maxCapacity */
	public int maxCapacity(JoinStrategy joinStrategy, int maxMemoryPerTable)
			throws StandardException {
		return joinStrategy.maxCapacity(maxCapacity, maxMemoryPerTable,
				getPerRowUsage());
	}

	private double getPerRowUsage() throws StandardException {
		if (perRowUsage < 0) {
			// Do not use getRefCols() because the cached refCols may no longer
			// be valid.
			FormatableBitSet refCols = getResultColumns()
					.getReferencedFormatableBitSet(cursorTargetTable(), true,
							false);
			perRowUsage = 0.0;

			/* Add up the memory usage for each referenced column */
			for (int i = 0; i < refCols.size(); i++) {
				if (refCols.isSet(i)) {
					ResultColumn rc = getResultColumns().elementAt(i);
					DataTypeDescriptor expressionType = rc.getExpression()
							.getTypeServices();
					if (expressionType != null)
						perRowUsage += expressionType.estimatedMemoryUsage();
				}
			}

			/*
			 * * If the proposed conglomerate is a non-covering index, add the*
			 * size of the RowLocation column to the total.** NOTE: We don't
			 * have a DataTypeDescriptor representing a* REF column here, so
			 * just add a constant here.
			 */
			ConglomerateDescriptor cd = getCurrentAccessPath()
					.getConglomerateDescriptor();
			if (cd != null) {
				if (cd.isIndex() && (!isCoveringIndex(cd))) {
					perRowUsage += 12.0;
				}
			}
		}
		return perRowUsage;
	} // end of getPerRowUsage

	/** @see Optimizable#hashKeyColumns */
	public int[] hashKeyColumns() {
		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(hashKeyColumns != null,
					"hashKeyColumns expected to be non-null");
		}

		return hashKeyColumns;
	}

	/** @see Optimizable#setHashKeyColumns */
	public void setHashKeyColumns(int[] columnNumbers) {
		hashKeyColumns = columnNumbers;
	}

	 

	/** @see Optimizable#memoryUsageOK */
	public boolean memoryUsageOK(double rowCount, int maxMemoryPerTable)
			throws StandardException {
		/*
		 * * Don't enforce maximum memory usage for a user-specified join*
		 * strategy.
		 */
		if (userSpecifiedJoinStrategy != null)
			return true;

		int intRowCount = (rowCount > Integer.MAX_VALUE) ? Integer.MAX_VALUE
				: (int) rowCount;
		return intRowCount <= maxCapacity(getCurrentAccessPath()
				.getJoinStrategy(), maxMemoryPerTable);
	}

	/**
	 * No-op in FromTable.
	 * 
	 * @see HalfOuterJoinNode#isJoinColumnForRightOuterJoin
	 */
	void isJoinColumnForRightOuterJoin(ResultColumn rc) {
	}

	/**
	 * @see Optimizable#legalJoinOrder
	 */
	public boolean legalJoinOrder(JBitSet assignedTableMap) {
		// Only those subclasses with dependencies need to override this.
		return true;
	}

	/**
	 * @see Optimizable#getNumColumnsReturned
	 */
	public int getNumColumnsReturned() {
		return getResultColumns().size();
	}

	/**
	 * @see Optimizable#isTargetTable
	 */
	public boolean isTargetTable() {
		return false;
	}

	/**
	 * @see Optimizable#isOneRowScan
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public boolean isOneRowScan() throws StandardException {
		/*
		 * We simply return isOneRowResultSet() for all subclasses except for
		 * EXISTS FBT where the semantics differ between 1 row per probe and
		 * whether or not there can be more than 1 rows that qualify on a scan.
		 */
		return isOneRowResultSet();
	}

	/**
	 * @see Optimizable#initAccessPaths
	 */
	public void initAccessPaths(Optimizer optimizer) {
		if (currentAccessPath == null) {
			currentAccessPath = new AccessPathImpl(optimizer);
		}
		if (bestAccessPath == null) {
			bestAccessPath = new AccessPathImpl(optimizer);
		}
		if (bestSortAvoidancePath == null) {
			bestSortAvoidancePath = new AccessPathImpl(optimizer);
		}
		if (trulyTheBestAccessPath == null) {
			trulyTheBestAccessPath = new AccessPathImpl(optimizer);
		}
	}

	/**
	 * @see Optimizable#uniqueJoin
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public double uniqueJoin(OptimizablePredicateList predList)
			throws StandardException {
		return -1.0;
	}

	/**
	 * Return the user specified join strategy, if any for this table.
	 *
	 * @return The user specified join strategy, if any for this table.
	 */
	String getUserSpecifiedJoinStrategy() {
		if (tableProperties == null) {
			return null;
		}

		return tableProperties.getProperty("joinStrategy");
	}

	/**
	 * Is this a table that has a FOR UPDATE clause. Overridden by
	 * FromBaseTable.
	 *
	 * @return true/false
	 */
	protected boolean cursorTargetTable() {
		return false;
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
			return "correlation Name: "
					+ correlationName
					+ "\n"
					+ (corrTableName != null ? corrTableName.toString()
							: "null") + "\n" + "tableNumber " + tableNumber
					+ "\n" + "level " + level + "\n" + super.toString();
		} else {
			return "";
		}
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
	ResultColumnList getResultColumnsForList(TableName allTableName,
			ResultColumnList inputRcl, TableName tableName)
			throws StandardException {
		TableName exposedName;
		TableName toCompare;
		/*
		 * If allTableName is non-null, then we must check to see if it matches
		 * our exposed name.
		 */

		if (correlationName == null)
			toCompare = tableName;
		else {
			if (allTableName != null)
				toCompare = makeTableName(allTableName.getSchemaName(),
						correlationName);
			else
				toCompare = makeTableName(null, correlationName);
		}

		if (allTableName != null && !allTableName.equals(toCompare)) {
			return null;
		}

		/*
		 * Cache exposed name for this table. The exposed name becomes the
		 * qualifier for each column in the expanded list.
		 */
		if (correlationName == null) {
			exposedName = tableName;
		} else {
			exposedName = makeTableName(null, correlationName);
		}

		final ContextManager cm = getContextManager();
		ResultColumnList rcList = new ResultColumnList(cm);

		/*
		 * Build a new result column list based off of resultColumns. NOTE: This
		 * method will capture any column renaming due to a derived column list.
		 */
		for (ResultColumn rc : inputRcl) {
			ColumnReference oldCR = rc.getReference();
			if (oldCR != null) {
				// for UPDATE actions of MERGE statement, preserve the original
				// table name.
				// this is necessary in order to correctly bind the column list
				// of the dummy SELECT.
				if (oldCR.getMergeTableID() != ColumnReference.MERGE_UNKNOWN) {
					exposedName = oldCR.getQualifiedTableName();
				}
			}

			ColumnReference newCR = new ColumnReference(rc.getName(),
					exposedName, cm);
			if ((oldCR != null)
					&& (oldCR.getMergeTableID() != ColumnReference.MERGE_UNKNOWN)) {
				newCR.setMergeTableID(oldCR.getMergeTableID());
			}

			ResultColumn newRc = new ResultColumn(rc.getName(), newCR, cm);

			rcList.addResultColumn(newRc);
		}
		return rcList;
	}

	/**
	 * Push expressions down to the first ResultSetNode which can do expression
	 * evaluation and has the same referenced table map. RESOLVE - This means
	 * only pushing down single table expressions to ProjectRestrictNodes today.
	 * Once we have a better understanding of how the optimizer will work, we
	 * can push down join clauses.
	 *
	 * @param predicateList
	 *            The PredicateList.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	void pushExpressions(PredicateList predicateList) throws StandardException {
		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(predicateList != null,
					"predicateList is expected to be non-null");
		}
	}

	/**
	 * Get the exposed name for this table, which is the name that can be used
	 * to refer to it in the rest of the query.
	 *
	 * @return The exposed name of this table.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	String getExposedName() throws StandardException {
		if (SanityManager.DEBUG)
			SanityManager
					.THROWASSERT("getExposedName() not expected to be called for "
							+ this.getClass().getName());
		return null;
	}

	/**
	 * Set the table # for this table.
	 *
	 * @param tableNumber
	 *            The table # for this table.
	 */
	void setTableNumber(int tableNumber) {
		/* This should only be called if the tableNumber has not been set yet */
		if (SanityManager.DEBUG)
			SanityManager.ASSERT(this.tableNumber == -1,
					"tableNumber is not expected to be already set");
		this.tableNumber = tableNumber;
	}

	/**
	 * Return a TableName node representing this FromTable. Expect this to be
	 * overridden (and used) by subclasses that may set correlationName to null.
	 *
	 * @return a TableName node representing this FromTable.
	 * @exception StandardException
	 *                Thrown on error
	 */
	TableName getTableName() throws StandardException {
		if (correlationName == null)
			return null;

		if (corrTableName == null) {
			corrTableName = makeTableName(null, correlationName);
		}

		return corrTableName;
	}

	/**
	 * Set the (query block) level (0-based) for this FromTable.
	 *
	 * @param level
	 *            The query block level for this FromTable.
	 */
	void setLevel(int level) {
		this.level = level;
	}

	/**
	 * Get the (query block) level (0-based) for this FromTable.
	 *
	 * @return int The query block level for this FromTable.
	 */
	int getLevel() {
		return level;
	}

	/**
	 * Decrement (query block) level (0-based) for this FromTable. This is
	 * useful when flattening a subquery.
	 *
	 * @param decrement
	 *            The amount to decrement by.
	 */
	void decrementLevel(int decrement) {
		if (SanityManager.DEBUG) {
			/*
			 * NOTE: level doesn't get propagated to nodes generated after
			 * binding.
			 */
			if (level < decrement && level != 0) {
				SanityManager.THROWASSERT("level (" + level
						+ ") expected to be >= decrement (" + decrement + ")");
			}
		}
		/*
		 * NOTE: level doesn't get propagated to nodes generated after binding.
		 */
		if (level > 0) {
			level -= decrement;
		}
	}

	/**
	 * Get a schema descriptor for the given table. Uses this.corrTableName.
	 *
	 * @return Schema Descriptor
	 *
	 * @exception StandardException
	 *                throws on schema name that doesn't exist
	 */
	SchemaDescriptor getSchemaDescriptor() throws StandardException {
		return getSchemaDescriptor(corrTableName);
	}

	/**
	 * Get a schema descriptor for the given table.
	 *
	 * @param tableName
	 *            the table name
	 *
	 * @return Schema Descriptor
	 *
	 * @exception StandardException
	 *                throws on schema name that doesn't exist
	 */
	SchemaDescriptor getSchemaDescriptor(TableName tableName)
			throws StandardException {
		SchemaDescriptor sd;

		sd = getSchemaDescriptor(tableName.getSchemaName());

		return sd;
	}

	/**
	 * Determine whether or not the specified name is an exposed name in the
	 * current query block.
	 *
	 * @param name
	 *            The specified name to search for as an exposed name.
	 * @param schemaName
	 *            Schema name, if non-null.
	 * @param exactMatch
	 *            Whether or not we need an exact match on specified schema and
	 *            table names or match on table id.
	 *
	 * @return The FromTable, if any, with the exposed name.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	FromTable getFromTableByName(String name, String schemaName,
			boolean exactMatch) throws StandardException {
		// Only FromBaseTables have schema names
		if (schemaName != null) {
			return null;
		}

		if (getExposedName().equals(name)) {
			return this;
		}
		return null;
	}

	/**
	 * Is this FromTable a JoinNode which can be flattened into the parents
	 * FromList.
	 *
	 * @return boolean Whether or not this FromTable can be flattened.
	 */
	boolean isFlattenableJoinNode() {
		return false;
	}

	/**
	 * no LOJ reordering for this FromTable.
	 */
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
	FromTable transformOuterJoins(ValueNode predicateTree, int numTables)
			throws StandardException {
		return this;
	}

	/**
	 * Fill the referencedTableMap with this ResultSetNode.
	 *
	 * @param passedMap
	 *            The table map to fill in.
	 */
	@Override
	void fillInReferencedTableMap(JBitSet passedMap) {
		if (tableNumber != -1) {
			passedMap.set(tableNumber);
		}
	}

	/**
	 * Mark as updatable all the columns in the result column list of this
	 * FromBaseTable that match the columns in the given update column list. If
	 * the list is null, it means all the columns are updatable.
	 *
	 * @param updateColumns
	 *            A list representing the columns that can be updated.
	 */
	protected void markUpdatableByCursor(List<String> updateColumns) {
		getResultColumns().markUpdatableByCursor(updateColumns);
	}

	/**
	 * Flatten this FromTable into the outer query block. The steps in
	 * flattening are: o Mark all ResultColumns as redundant, so that they are
	 * "skipped over" at generate(). o Append the wherePredicates to the outer
	 * list. o Return the fromList so that the caller will merge the 2 lists
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
	FromList flatten(ResultColumnList rcl, PredicateList outerPList,
			SubqueryList sql, GroupByList gbl, ValueNode havingClause)

	throws StandardException {
		if (SanityManager.DEBUG) {
			SanityManager
					.THROWASSERT("flatten() not expected to be called for "
							+ this);
		}
		return null;
	}

	/**
	 * Optimize any subqueries that haven't been optimized any where else. This
	 * is useful for a RowResultSetNode as a derived table because it doesn't
	 * get optimized otherwise.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	void optimizeSubqueries(DataDictionary dd, double rowCount)
			throws StandardException {
	}

	/**
	 * Tell the given RowOrdering about any columns that are constant due to
	 * their being equality comparisons with constant expressions.
	 */
	protected void tellRowOrderingAboutConstantColumns(RowOrdering rowOrdering,
			OptimizablePredicateList predList) {
		/*
		 * * Tell the RowOrdering about columns that are equal to constant*
		 * expressions.
		 */
		if (predList != null) {
			for (int i = 0; i < predList.size(); i++) {
				Predicate pred = (Predicate) predList.getOptPredicate(i);

				 
			}
		}

	}

	boolean needsSpecialRCLBinding() {
		return false;
	}

	/**
	 * Sets the original or unbound table name for this FromTable.
	 * 
	 * @param tableName
	 *            the unbound table name
	 *
	 */
	void setOrigTableName(TableName tableName) {
		this.origTableName = tableName;
	}

	/**
	 * Gets the original or unbound table name for this FromTable. The tableName
	 * field can be changed due to synonym resolution. Use this method to
	 * retrieve the actual unbound tablename.
	 * 
	 * @return TableName the original or unbound tablename
	 *
	 */
	TableName getOrigTableName() {
		return this.origTableName;
	}

	/** set the merge table id */
	void setMergeTableID(int mergeTableID) {
		_mergeTableID = mergeTableID;
	}

	/** get the merge table id */
	int getMergeTableID() {
		return _mergeTableID;
	}

	@Override
	void acceptChildren(Visitor v) throws StandardException {
		super.acceptChildren(v);

		if (origTableName != null) {
			origTableName = (TableName) origTableName.accept(v);
		}

		if (corrTableName != null) {
			corrTableName = (TableName) corrTableName.accept(v);
		}
	}
}
