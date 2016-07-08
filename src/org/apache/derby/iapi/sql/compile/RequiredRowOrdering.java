/*

   Derby - Class org.apache.derby.iapi.sql.compile.RequiredRowOrdering

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

package org.apache.derby.iapi.sql.compile;

import org.apache.derby.iapi.error.StandardException;

import org.apache.derby.iapi.util.JBitSet;

/**
 * This interface provides a representation of the required ordering of rows
 * from a ResultSet.  Different operations can require ordering: ORDER BY,
 * DISTINCT, GROUP BY.  Some operations, like ORDER BY, require that the
 * columns be ordered a particular way, while others, like DISTINCT and
 * GROUP BY, reuire only that there be no duplicates in the result.
 */
public interface RequiredRowOrdering
{
	static final int SORT_REQUIRED = 1;
	static final int ELIMINATE_DUPS = 2;
	static final int NOTHING_REQUIRED = 3;

    /**
     * Tell whether sorting is required for this RequiredRowOrdering,
     * given a RowOrdering.
     *
     * @param rowOrdering   The order of rows in question
     * @param optimizableList The list of join participants
     * @param proposedJoinOrder The current join order being considered by
     *    the optimizer. We need to look into this to determine if the outer
     *    optimizables are single row result set if the order by column is
     *    on an inner optimizable and that inner optimizable is not a one.
     *    {@code proposedJoinOrder} is a map onto {@code optimizableList}:
     *    it contains indexes of optimizables in that list in the join order
     *    proposed, cf. {@code OptimizerImpl#proposedJoinOrder}.
     * @return  SORT_REQUIRED if sorting is required,
     *          ELIMINATE_DUPS if no sorting is required but duplicates
     *                          must be eliminated (i.e. the rows are in
     *                          the right order but there may be duplicates),
     *          NOTHING_REQUIRED is no operation is required
     *
     * @exception StandardException     Thrown on error
     */
    int sortRequired(RowOrdering rowOrdering,
                     OptimizableList optimizableList,
                     int[] proposedJoinOrder) throws StandardException;

   /**
    * Tell whether sorting is required for this RequiredRowOrdering,
    * given a RowOrdering representing a partial join order, and
    * a bit map telling what tables are represented in the join order.
    * This is useful for reducing the number of cases the optimizer
    * has to consider.
    *
    * @param rowOrdering   The order of rows in the partial join order
    * @param tableMap      A bit map of the tables in the partial join order
    * @param optimizableList The list of join participants
    * @param proposedJoinOrder The current join order being considered by
    *    the optimizer. We need to look into this to determine if the outer
    *    optimizables are single row result set if the order by column is
    *    on an inner optimizable and that inner optimizable is not a one.
    *    {@code proposedJoinOrder} is a map onto {@code optimizableList}:
    *    it contains indexes of optimizables in that list in the join order
    *    proposed, cf. {@code OptimizerImpl#proposedJoinOrder}.
    *    DERBY-3926 and DERBY-6148
    *
    * @return  SORT_REQUIRED if sorting is required,
    *          ELIMINATE_DUPS if no sorting is required by duplicates
    *                          must be eliminated (i.e. the rows are in
    *                          the right order but there may be duplicates),
    *          NOTHING_REQUIRED is no operation is required
    *
    * @exception StandardException     Thrown on error
    */
    int sortRequired(RowOrdering rowOrdering,
                     JBitSet tableMap,
                     OptimizableList optimizableList,
                     int[] proposedJoinOrder) throws StandardException;
 

	/**
	 * Indicate that a sort is necessary to fulfill this required ordering.
	 * This method may be called many times during a single optimization.
	 */
	void sortNeeded();

	/**
	 * Indicate that a sort is *NOT* necessary to fulfill this required
	 * ordering.  This method may be called many times during a single
	 * optimization.
	 */
	void sortNotNeeded();

	boolean getSortNeeded();
}
