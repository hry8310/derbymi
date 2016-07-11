/*

   Derby - Class org.apache.derby.impl.sql.compile.OptimizerImpl

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

import java.util.HashMap;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager; 
import org.apache.derby.iapi.sql.compile.AccessPath; 
import org.apache.derby.iapi.sql.compile.JoinStrategy;
import org.apache.derby.iapi.sql.compile.OptTrace;
import org.apache.derby.iapi.sql.compile.Optimizable;
import org.apache.derby.iapi.sql.compile.OptimizableList;
import org.apache.derby.iapi.sql.compile.OptimizablePredicate;
import org.apache.derby.iapi.sql.compile.OptimizablePredicateList;
import org.apache.derby.iapi.sql.compile.Optimizer;
import org.apache.derby.iapi.sql.compile.OptimizerPlan;
import org.apache.derby.iapi.sql.compile.RequiredRowOrdering;
import org.apache.derby.iapi.sql.compile.RowOrdering;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.dictionary.UniqueTupleDescriptor;
import org.apache.derby.iapi.util.JBitSet;
import org.apache.derby.iapi.util.StringUtil;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * Optimizer uses OptimizableList to keep track of the best join order as it
 * builds it. For each available slot in the join order, we cost all of the
 * Optimizables from that slot til the end of the OptimizableList. Later, we
 * will choose the best Optimizable for that slot and reorder the list
 * accordingly. In order to do this, we probably need to move the temporary
 * pushing and pulling of join clauses into Optimizer, since the logic will be
 * different for other implementations. (Of course, we're not pushing and
 * pulling join clauses between permutations yet.)
 */

class OptimizerImpl implements Optimizer {
	private LanguageConnectionContext lcc;

	private DataDictionary dDictionary;
	/* The number of tables in the query as a whole. (Size of bit maps.) */
	private int numTablesInQuery;
	/* The number of optimizables in the list to optimize */
	private int numOptimizables;

	/*
	 * Bit map of tables that have already been assigned to slots. Useful for
	 * pushing join clauses as slots are assigned. Enforcement of ordering
	 * dependencies is done through assignedTableMap.
	 */
	private JBitSet assignedTableMap;
	private OptimizableList optimizableList;
	private OptimizerPlan overridingPlan;
	private OptimizerPlan currentPlan;
	private OptimizablePredicateList predicateList;
	private JBitSet nonCorrelatedTableMap;

	private int[] proposedJoinOrder;
	private int[] bestJoinOrder;
	private int joinPosition;
	private boolean desiredJoinOrderFound;

	/*
	 * This implements a state machine to jump start to a appearingly good join
	 * order, when the number of tables is high, and the optimization could take
	 * a long time. A good start can prune better, and timeout sooner.
	 * Otherwise, it may take forever to exhaust or timeout (see beetle 5870).
	 * Basically after we jump, we walk the high part, then fall when we reach
	 * the peak, finally we walk the low part til where we jumped to.
	 */
	private static final int NO_JUMP = 0;
	private static final int READY_TO_JUMP = 1;
	private static final int JUMPING = 2;
	private static final int WALK_HIGH = 3;
	private static final int WALK_LOW = 4;
	private int permuteState;
	private int[] firstLookOrder;

	private boolean ruleBasedOptimization;
 
	private long timeOptimizationStarted;
	private long currentTime;
	private boolean timeExceeded;
	private boolean noTimeout;
	private boolean useStatistics;
	private int tableLockThreshold;

	private JoinStrategy[] joinStrategies;

	private RequiredRowOrdering requiredRowOrdering;

	private boolean foundABestPlan;
 
	private RowOrdering currentRowOrdering = new RowOrderingImpl();
	private RowOrdering bestRowOrdering = new RowOrderingImpl();

	// max memory use per table
	private int maxMemoryPerTable;

	// Whether or not we need to reload the best plan for an Optimizable
	// when we "pull" [1] it. If the latest complete join order was the
	// best one so far, then the Optimizable will already have the correct
	// best plan loaded so we don't need to do the extra work. But if
	// the most recent join order was _not_ the best, then this flag tells
	// us that we need to reload the best plan when pulling.
	//
	// [1]: As part of the iteration through the join orders, the optimizer has
	// to "pull" Optimizables from the the join order before re-placing them in
	// a different order. As an example, in order to get from:
	//
	// { HOJ, TAB_V, TAB_D } to
	//
	// { HOJ, TAB_D, TAB_V}
	//
	// the optimizer will first pull TAB_D from the join order, then
	// it will pull TAB_V, then it will place TAB_D, and finally place
	// TAB_V. I.e.:
	//
	// { HOJ, TAB_V, - }
	// { HOJ, -, - }
	// { HOJ, TAB_D, - }
	// { HOJ, TAB_D, TAB_V }

	private boolean reloadBestPlan;

	// Set of optimizer->bestJoinOrder mappings used to keep track of which
	// of this OptimizerImpl's "bestJoinOrder"s was the best with respect to a
	// a specific outer query; the outer query is represented by an instance
	// of Optimizer. Each outer query could potentially have a different
	// idea of what this OptimizerImpl's "best join order" is, so we have
	// to keep track of them all.
	private HashMap<Object, int[]> savedJoinOrders;

	// Value used to figure out when/if we've timed out for this
	// Optimizable.
	private double timeLimit;
 

	/*
	 * Status variables used for "jumping" to previous best join order when
	 * possible. In particular, this helps when this optimizer corresponds to a
	 * subquery and we are trying to find out what the best join order is if we
	 * do a hash join with the subquery instead of a nested loop join. In that
	 * case the previous best join order will have the best join order for a
	 * nested loop, so we want to start there when considering hash join because
	 * odds are that same join order will give us the best cost for hash join,
	 * as well. We only try this, though, if neither the previous round of
	 * optimization nor this round relies on predicates that have been pushed
	 * down from above--because that's the scenario for which the best join
	 * order is likely to be same for consecutive rounds.
	 */
	private boolean usingPredsPushedFromAbove;
	private boolean bestJoinOrderUsedPredsFromAbove;

	public OptimizerImpl(OptimizableList optimizableList,
			OptimizablePredicateList predicateList, DataDictionary dDictionary,
			boolean ruleBasedOptimization, boolean noTimeout,
			boolean useStatistics, int maxMemoryPerTable,
			JoinStrategy[] joinStrategies, int tableLockThreshold,
			RequiredRowOrdering requiredRowOrdering, int numTablesInQuery,
			OptimizerPlan overridingPlan, LanguageConnectionContext lcc)
			throws StandardException {
		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(optimizableList != null,
					"optimizableList is not expected to be null");
		}

		 
		// Verify that any Properties lists for user overrides are valid
		optimizableList.verifyProperties(dDictionary);

		this.numTablesInQuery = numTablesInQuery;
		numOptimizables = optimizableList.size();
		proposedJoinOrder = new int[numOptimizables];
		if (initJumpState() == READY_TO_JUMP)
			firstLookOrder = new int[numOptimizables];

		/* Mark all join positions as unused */
		for (int i = 0; i < numOptimizables; i++)
			proposedJoinOrder[i] = -1;

		bestJoinOrder = new int[numOptimizables];
		joinPosition = -1;
		this.optimizableList = optimizableList;
		this.overridingPlan = overridingPlan;
		this.predicateList = predicateList;
		this.dDictionary = dDictionary;
		this.ruleBasedOptimization = ruleBasedOptimization;
		this.noTimeout = noTimeout;
		this.maxMemoryPerTable = maxMemoryPerTable;
		this.joinStrategies = joinStrategies;
		this.tableLockThreshold = tableLockThreshold;
		this.requiredRowOrdering = requiredRowOrdering;
		this.useStatistics = useStatistics;
		this.lcc = lcc;

		/* initialize variables for tracking permutations */
		assignedTableMap = new JBitSet(numTablesInQuery);

		/*
		 * * Make a map of the non-correlated tables, which are the tables* in
		 * the list of Optimizables we're optimizing. An reference* to a table
		 * that is not defined in the list of Optimizables* is presumed to be
		 * correlated.
		 */
		nonCorrelatedTableMap = new JBitSet(numTablesInQuery);
		for (int tabCtr = 0; tabCtr < numOptimizables; tabCtr++) {
			Optimizable curTable = optimizableList.getOptimizable(tabCtr);
			nonCorrelatedTableMap.or(curTable.getReferencedTableMap());
		}

		/* Get the time that optimization starts */
		timeOptimizationStarted = System.currentTimeMillis();
		reloadBestPlan = false;
		savedJoinOrders = null;
		timeLimit = Double.MAX_VALUE;

		usingPredsPushedFromAbove = false;
		bestJoinOrderUsedPredsFromAbove = false;

		// Optimization started
		if (tracingIsOn()) {
			tracer().traceStartQueryBlock(timeOptimizationStarted, hashCode(),
					optimizableList);
		}

		// make sure that optimizer overrides are bound and left-deep
		if (overridingPlan != null) {
			if (!overridingPlan.isBound()) {
				throw StandardException
						.newException(SQLState.LANG_UNRESOLVED_ROW_SOURCE);
			}

			int actualRowSourceCount = optimizableList.size();
			int overriddenRowSourceCount = overridingPlan.countLeafNodes();
			if (actualRowSourceCount != overriddenRowSourceCount) {
				throw StandardException.newException(
						SQLState.LANG_BAD_ROW_SOURCE_COUNT,
						overriddenRowSourceCount, actualRowSourceCount);
			}
		}
	}

	/**
	 * This method is called before every "round" of optimization, where we
	 * define a "round" to be the period between the last time a call to
	 * getOptimizer() (on either a ResultSetNode or an OptimizerFactory)
	 * returned _this_ OptimizerImpl and the time a call to this OptimizerImpl's
	 * getNextPermutation() method returns FALSE. Any re-initialization of state
	 * that is required before each round should be done in this method.
	 */
	public void prepForNextRound() { }

	/**
	 * Determine if we want to try "jumping" permutations with this
	 * OptimizerImpl, and (re-)initialize the permuteState field accordingly.
	 */
	private int initJumpState() {
		permuteState = (numTablesInQuery >= 6 ? READY_TO_JUMP : NO_JUMP);
		return permuteState;
	}

	public void setOuterRows(double thi){
		
	}
	private boolean tracingIsOn() {
		return lcc.optimizerTracingIsOn();
	}

	public int getMaxMemoryPerTable() {
		return maxMemoryPerTable;
	}

	/**
	 * @see Optimizer#getNextPermutation
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public boolean getNextPermutation() throws StandardException { return true;}

	private void rewindJoinOrder() throws StandardException {
		for (;; joinPosition--) {
			Optimizable pullMe = optimizableList
					.getOptimizable(proposedJoinOrder[joinPosition]);
			pullMe.pullOptPredicates(predicateList);
			if (reloadBestPlan)
				pullMe.updateBestPlanMap(FromTable.LOAD_PLAN, this);
			proposedJoinOrder[joinPosition] = -1;
			if (joinPosition == 0)
				break;
		}
		 
		assignedTableMap.clearAll();
	}

	/**
	 * Do any work that needs to be done after the current round of optimization
	 * has completed. For now this just means walking the subtrees for each
	 * optimizable and removing the "bestPlan" that we saved (w.r.t to this
	 * OptimizerImpl) from all of the nodes. If we don't do this
	 * post-optimization cleanup we can end up consuming a huge amount of memory
	 * for deeply- nested queries, which can lead to OOM errors. DERBY-1315.
	 */
	private void endOfRoundCleanup() throws StandardException {
		for (int i = 0; i < numOptimizables; i++) {
			optimizableList.getOptimizable(i).updateBestPlanMap(
					FromTable.REMOVE_PLAN, this);
		}
	}

	/**
	 * Iterate through all optimizables in the current proposedJoinOrder and
	 * find the accumulated sum of their estimated costs. This method is used to
	 * 'recover' cost estimate sums that have been lost due to the
	 * addition/subtraction of the cost estimate for the Optimizable at position
	 * "joinPosition". Ex. If the total cost for Optimizables at positions <
	 * joinPosition is 1500, and then the Optimizable at joinPosition has an
	 * estimated cost of 3.14E40, adding those two numbers effectively "loses"
	 * the 1500. When we later subtract 3.14E40 from the total cost estimate (as
	 * part of "pull" processing), we'll end up with 0 as the result--which is
	 * wrong. This method allows us to recover the "1500" that we lost in the
	 * process of adding and subtracting 3.14E40.
	 */
	private double recoverCostFromProposedJoinOrder(boolean sortAvoidance)
			throws StandardException { 

		return 0;
	}

	/**
	 * Check to see if the optimizable corresponding to the received optNumber
	 * can legally be placed within the current join order. More specifically,
	 * if the optimizable has any dependencies, check to see if those
	 * dependencies are satisified by the table map representing the current
	 * join order.
	 */
	private boolean joinOrderMeetsDependencies(int optNumber)
			throws StandardException {
		Optimizable nextOpt = optimizableList.getOptimizable(optNumber);
		return nextOpt.legalJoinOrder(assignedTableMap);
	}

	/**
	 * Pull whatever optimizable is at joinPosition in the proposed join order
	 * from the join order, and update all corresponding state accordingly.
	 */
	private void pullOptimizableFromJoinOrder() throws StandardException { }

	/*
	 * * Push predicates from this optimizer's list to the given optimizable,*
	 * as appropriate given the outer tables.** @param curTable The Optimizable
	 * to push predicates down to* @param outerTables A bit map of outer tables*
	 * * @exception StandardException Thrown on error
	 */
	void pushPredicates(Optimizable curTable, JBitSet outerTables)
			throws StandardException {
		/*
		 * * Push optimizable clauses to current position in join order.**
		 * RESOLVE - We do not push predicates with subqueries not
		 * materializable.
		 */

		int numPreds = predicateList.size();
		JBitSet predMap = new JBitSet(numTablesInQuery);
		JBitSet curTableNums = null;
		BaseTableNumbersVisitor btnVis = null;
		int tNum;
		Predicate pred;

		/*
		 * Walk the OptimizablePredicateList. For each OptimizablePredicate, see
		 * if it can be assigned to the Optimizable at the current join
		 * position.
		 * 
		 * NOTE - We walk the OPL backwards since we will hopefully be deleted
		 * entries as we walk it.
		 */
		for (int predCtr = numPreds - 1; predCtr >= 0; predCtr--) {
			pred = (Predicate) predicateList.getOptPredicate(predCtr);

			/* Skip over non-pushable predicates */
			if (!isPushable(pred)) {
				continue;
			}

			/*
			 * Make copy of referenced map so that we can do destructive
			 * manipulation on the copy.
			 */
			predMap.setTo(pred.getReferencedMap());

			/*
			 * Clear bits representing those tables that have already been
			 * assigned, except for the current table. The outer table map
			 * includes the current table, so if the predicate is ready to be
			 * pushed, predMap will end up with no bits set.
			 */
			for (int index = 0; index < predMap.size(); index++) {
				if (outerTables.get(index)) {
					predMap.clear(index);
				}
			}

			/*
			 * * Only consider non-correlated variables when deciding where* to
			 * push predicates down to.
			 */
			predMap.and(nonCorrelatedTableMap);

			/*
			 * At this point what we've done is figure out what FromTables the
			 * predicate references (using the predicate's "referenced map") and
			 * then: 1) unset the table numbers for any FromTables that have
			 * already been optimized, 2) unset the table number for curTable,
			 * which we are about to optimize, and 3) cleared out any remaining
			 * table numbers which do NOT directly correspond to UN-optimized
			 * FromTables in this OptimizerImpl's optimizableList.
			 * 
			 * Note: the optimizables in this OptImpl's optimizableList are
			 * called "non-correlated".
			 * 
			 * So at this point predMap holds a list of tableNumbers which
			 * correspond to "non-correlated" FromTables that are referenced by
			 * the predicate but that have NOT yet been optimized. If any such
			 * FromTable exists then we canNOT push the predicate yet. We can
			 * only push the predicate if every FromTable that it references
			 * either 1) has already been optimized, or 2) is about to be
			 * optimized (i.e. the FromTable is curTable itself). We can check
			 * for this condition by seeing if predMap is empty, which is what
			 * the following line does.
			 */
			boolean pushPredNow = (predMap.getFirstSetBit() == -1);

			/*
			 * If the predicate is scoped, there's more work to do. A scoped
			 * predicate's "referenced map" may not be in sync with its actual
			 * column references. Or put another way, the predicate's referenced
			 * map may not actually represent the tables that are referenced by
			 * the predicate. For example, assume the query tree is something
			 * like:
			 * 
			 * SelectNode0 (PRN0, PRN1) | | T1 UnionNode / | PRN2 PRN3 | |
			 * SelectNode1 SelectNode2 (PRN4, PRN5) (PRN6) | | | T2 T3 T4
			 * 
			 * Assume further that we have an equijoin predicate between T1 and
			 * the Union node, and that the column reference that points to the
			 * Union ultimately maps to T3. The predicate will then be scoped to
			 * PRN2 and PRN3 and the newly-scoped predicates will get passed to
			 * the optimizers for SelectNode1 and SelectNode2--which brings us
			 * here. Assume for this example that we're here for SelectNode1 and
			 * that "curTable" is PRN4. Since the predicate has been scoped to
			 * SelectNode1, its referenced map will hold the table numbers for
			 * T1 and PRN2--it will NOT hold the table number for PRN5, even
			 * though PRN5 (T3) is the actual target for the predicate. Given
			 * that, the above logic will determine that the predicate should be
			 * pushed to curTable (PRN4)--but that's not correct. We said at the
			 * start that the predicate ultimately maps to T3--so we should NOT
			 * be pushing it to T2. And hence the need for some additional
			 * logic. DERBY-1866.
			 */
			if (pushPredNow && pred.isScopedForPush() && (numOptimizables > 1)) {
				if (btnVis == null) {
					curTableNums = new JBitSet(numTablesInQuery);
					btnVis = new BaseTableNumbersVisitor(curTableNums);
				}

				/*
				 * What we want to do is find out if the scoped predicate is
				 * really supposed to be pushed to curTable. We do that by
				 * getting the base table numbers referenced by curTable along
				 * with curTable's own table number. Then we get the base table
				 * numbers referenced by the scoped predicate. If the two sets
				 * have at least one table number in common, then we know that
				 * the predicate should be pushed to curTable. In the above
				 * example predMap will end up holding the base table number for
				 * T3, and thus this check will fail when curTable is PRN4 but
				 * will pass when it is PRN5, which is what we want.
				 */
				tNum = ((FromTable) curTable).getTableNumber();
				curTableNums.clearAll();
				btnVis.setTableMap(curTableNums);
				((FromTable) curTable).accept(btnVis);
				if (tNum >= 0)
					curTableNums.set(tNum);

				btnVis.setTableMap(predMap);
				pred.accept(btnVis);

				predMap.and(curTableNums);
				if ((predMap.getFirstSetBit() == -1))
					pushPredNow = false;
			}

			/*
			 * * Finally, push the predicate down to the Optimizable at the* end
			 * of the current proposed join order, if it can be evaluated*
			 * there.
			 */
			if (pushPredNow) {
				/* Push the predicate and remove it from the list */
				if (curTable.pushOptPredicate(pred)) {
					predicateList.removeOptPredicate(predCtr);
				}
			}
		}
	}

	/**
	 * @see Optimizer#getNextDecoratedPermutation
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public boolean getNextDecoratedPermutation() throws StandardException { 
		return true;
	}

	/**
	 * Get the unique tuple descriptor of the current access path for an
	 * Optimizable.
	 */
	private UniqueTupleDescriptor getTupleDescriptor(Optimizable optimizable)
			throws StandardException {
		if (isTableFunction(optimizable)) {
			ProjectRestrictNode prn = (ProjectRestrictNode) optimizable;
			return ((StaticMethodCallNode) ((FromVTI) prn.getChildResult())
					.getMethodCall()).ad;
		} else {
			return optimizable.getCurrentAccessPath()
					.getConglomerateDescriptor();
		}
	}

	/** Return true if the optimizable is a table function */
	static boolean isTableFunction(Optimizable optimizable) {
		if (!(optimizable instanceof ProjectRestrictNode)) {
			return false;
		}

		ResultSetNode rsn = ((ProjectRestrictNode) optimizable)
				.getChildResult();
		if (!(rsn instanceof FromVTI)) {
			return false;
		}

		return (((FromVTI) rsn).getMethodCall() instanceof StaticMethodCallNode);
	}

	 
	/**
	 * @see org.apache.derby.iapi.sql.compile.Optimizer#costPermutation
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public void costPermutation() throws StandardException { }
 

	 
 

	/**
	 * @see org.apache.derby.iapi.sql.compile.Optimizer#getDataDictionary
	 */

	public DataDictionary getDataDictionary() {
		return dDictionary;
	}

	/**
	 * @see Optimizer#modifyAccessPaths
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public void modifyAccessPaths() throws StandardException {
		if (tracingIsOn()) {
			tracer().traceModifyingAccessPaths(hashCode());
		}

		if (!foundABestPlan) {
			if (tracingIsOn()) {
				tracer().traceNoBestPlan();
			}

			throw StandardException
					.newException(SQLState.LANG_NO_BEST_PLAN_FOUND);
		}

		/* Change the join order of the list of optimizables */
		optimizableList.reOrder(bestJoinOrder);

		/* Form a bit map of the tables as they are put into the join order */
		JBitSet outerTables = new JBitSet(numOptimizables);

		/* Modify the access path of each table, as necessary */
		for (int ictr = 0; ictr < numOptimizables; ictr++) {
			Optimizable optimizable = optimizableList.getOptimizable(ictr);

			/* Current table is treated as an outer table */
			outerTables.or(optimizable.getReferencedTableMap());

			/*
			 * * Push any appropriate predicates from this optimizer's list* to
			 * the optimizable, as appropriate.
			 */
			pushPredicates(optimizable, outerTables);

			optimizableList.setOptimizable(ictr,
					optimizable.modifyAccessPath(outerTables));
		}
	}
 
 

	/** @see Optimizer#tableLockThreshold */
	public int tableLockThreshold() {
		return tableLockThreshold;
	}

	/**
	 * Get the number of join strategies supported by this optimizer.
	 */
	public int getNumberOfJoinStrategies() {
		return 0;
	}

	/** @see Optimizer#getJoinStrategy */
	public JoinStrategy getJoinStrategy(int whichStrategy) {
		if (SanityManager.DEBUG) {
			if (whichStrategy < 0 || whichStrategy >= joinStrategies.length) {
				SanityManager.THROWASSERT("whichStrategy value "
						+ whichStrategy
						+ " out of range - should be between 0 and "
						+ (joinStrategies.length - 1));
			}

			if (joinStrategies[whichStrategy] == null) {
				SanityManager.THROWASSERT("Strategy " + whichStrategy
						+ " not filled in.");
			}
		}

		return joinStrategies[whichStrategy];
	}

	/** @see Optimizer#getJoinStrategy */
	public JoinStrategy getJoinStrategy(String whichStrategy) {
		JoinStrategy retval = null;
		String upperValue = StringUtil.SQLToUpperCase(whichStrategy);

		for (int i = 0; i < joinStrategies.length; i++) {
			if (upperValue.equals(joinStrategies[i].getName())) {
				retval = joinStrategies[i];
			}
		}

		return retval;
	}

	/**
	 * @see Optimizer#uniqueJoinWithOuterTable
	 * @exception StandardException
	 *                Thrown on error
	 */
	public double uniqueJoinWithOuterTable(OptimizablePredicateList predList)
			throws StandardException {
		double retval = -1.0; 
		return retval;
	}

	private boolean isPushable(OptimizablePredicate pred) {
		/*
		 * Predicates which contain subqueries that are not materializable are
		 * not currently pushable.
		 */
		if (pred.hasSubquery()) {
			return false;
		} else {
			return true;
		}
	}

	 

	/** @see Optimizer#getLevel */
	public int getLevel() {
		return 2;
	} 
	
	/** @see Optimizer#useStatistics */
	public boolean useStatistics() {
		return useStatistics && optimizableList.useStatistics();
	}

	/**
	 * Process (i.e. add, load, or remove) current best join order as the best
	 * one for some outer query or ancestor node, represented by another
	 * OptimizerImpl or an instance of FromTable, respectively. Then iterate
	 * through our optimizableList and tell each Optimizable to do the same. See
	 * Optimizable.updateBestPlan() for more on why this is necessary.
	 *
	 * @param action
	 *            Indicates whether to add, load, or remove the plan
	 * @param planKey
	 *            Object to use as the map key when adding/looking up a plan. If
	 *            this is an instance of OptimizerImpl then it corresponds to an
	 *            outer query; otherwise it's some Optimizable above this
	 *            OptimizerImpl that could potentially reject plans chosen by
	 *            this OptimizerImpl.
	 */
	public void updateBestPlanMaps(short action, Object planKey)
			throws StandardException {
		// First we process this OptimizerImpl's best join order. If there's
		// only one optimizable in the list, then there's only one possible
		// join order, so don't bother.
		if (numOptimizables > 1) {
			int[] joinOrder = null;
			if (action == FromTable.REMOVE_PLAN) {
				if (savedJoinOrders != null) {
					savedJoinOrders.remove(planKey);
					if (savedJoinOrders.isEmpty()) {
						savedJoinOrders = null;
					}
				}
			} else if (action == FromTable.ADD_PLAN) {
				// If the savedJoinOrder map already exists, search for the
				// join order for the target optimizer and reuse that.
				if (savedJoinOrders == null)
					savedJoinOrders = new HashMap<Object, int[]>();
				else
					joinOrder = savedJoinOrders.get(planKey);

				// If we don't already have a join order array for the
				// optimizer,
				// create a new one.
				if (joinOrder == null)
					joinOrder = new int[numOptimizables];

				System.arraycopy(bestJoinOrder, 0, joinOrder, 0,
						bestJoinOrder.length);

				savedJoinOrders.put(planKey, joinOrder);
			} else {
				// If we get here, we want to load the best join order from our
				// map into this OptimizerImpl's bestJoinOrder array.

				// If we don't have any join orders saved, then there's nothing
				// to
				// load. This can happen if the optimizer tried some join order
				// for which there was no valid plan.
				if (savedJoinOrders != null) {
					joinOrder = savedJoinOrders.get(planKey);
					if (joinOrder != null) {
						System.arraycopy(joinOrder, 0, bestJoinOrder, 0,
								joinOrder.length);
					}
				}
			}
		}

		// Now iterate through all Optimizables in this OptimizerImpl's
		// list and add/load/remove the best plan "mapping" for each one,
		// as described in in Optimizable.updateBestPlanMap().
		for (int i = optimizableList.size() - 1; i >= 0; i--) {
			optimizableList.getOptimizable(i)
					.updateBestPlanMap(action, planKey);
		}
	}

	/**
	 * Add scoped predicates to this optimizer's predicateList. This method is
	 * intended for use during the modifyAccessPath() phase of compilation, as
	 * it allows nodes (esp. SelectNodes) to add to the list of predicates
	 * available for the final "push" before code generation. Just as the
	 * constructor for this class allows a caller to specify a predicate list to
	 * use during the optimization phase, this method allows a caller to specify
	 * a predicate list to use during the modify-access-paths phase.
	 *
	 * Before adding the received predicates, this method also clears out any
	 * scoped predicates that might be sitting in OptimizerImpl's list from the
	 * last round of optimizing.
	 *
	 * This method should be in the Optimizer interface, but it relies on an
	 * argument type (PredicateList) which lives in an impl package.
	 *
	 * @param pList
	 *            List of predicates to add to this OptimizerImpl's own list for
	 *            pushing.
	 */
	void addScopedPredicatesToList(PredicateList pList, ContextManager cm)
			throws StandardException {
		if ((pList == null) || (pList == predicateList))
			// nothing to do.
			return;

		 

		// First, we need to go through and remove any predicates in this
		// optimizer's list that may have been pushed here from outer queries
		// during the previous round(s) of optimization. We know if the
		// predicate was pushed from an outer query because it will have
		// been scoped to the node for which this OptimizerImpl was
		// created.
		Predicate pred;
		for (int i = predicateList.size() - 1; i >= 0; i--) {
			pred = (Predicate) predicateList.getOptPredicate(i);
			if (pred.isScopedForPush())
				predicateList.removeOptPredicate(i);
		}

		// Now transfer scoped predicates in the received list to
		// this OptimizerImpl's list, where appropriate.
		for (int i = pList.size() - 1; i >= 0; i--) {
			pred = (Predicate) pList.getOptPredicate(i);
			if (pred.isScopedToSourceResultSet()) {
				// Clear the scoped predicate's scan flags; they'll be set
				// as appropriate when they make it to the restrictionList
				// of the scoped pred's source result set.
				pred.clearScanFlags();
				predicateList.addOptPredicate(pred);
				pList.removeOptPredicate(i);
			}
		}
	}

	/** Get the trace machinery */
	private OptTrace tracer() {
		return lcc.getOptimizerTracer();
	}

	public int getOptimizableCount() {
		return optimizableList.size();
	}

	public Optimizable getOptimizable(int idx) {
		return optimizableList.getOptimizable(idx);
	}
}
