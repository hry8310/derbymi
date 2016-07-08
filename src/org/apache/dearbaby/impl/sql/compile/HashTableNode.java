/*

   Derby - Class org.apache.derby.impl.sql.compile.HashTableNode

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
import org.apache.derby.iapi.services.compiler.MethodBuilder;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.CostEstimate;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A HashTableNode represents a result set where a hash table is built.
 *
 */

class HashTableNode extends SingleChildResultSetNode
{
	PredicateList	searchPredicateList;
	PredicateList	joinPredicateList;

	SubqueryList	pSubqueryList;
	SubqueryList	rSubqueryList;

	/**
     * Constructor for a HashTableNode.
	 *
	 * @param childResult			The child result set
	 * @param tableProperties	Properties list associated with the table
	 * @param resultColumns			The RCL.
	 * @param searchPredicateList	Single table clauses
	 * @param joinPredicateList		Multi table clauses
	 * @param accessPath			The access path
	 * @param costEstimate			The cost estimate
	 * @param pSubqueryList			List of subqueries in RCL
	 * @param rSubqueryList			List of subqueries in Predicate lists
	 * @param hashKeyColumns		Hash key columns
     * @param cm                    The context manager
	 */
    HashTableNode(ResultSetNode  childResult,
                  Properties     tableProperties,
                  ResultColumnList resultColumns,
                  PredicateList  searchPredicateList,
                  PredicateList  joinPredicateList,
                  AccessPathImpl accessPath,
                  CostEstimate   costEstimate,
                  SubqueryList   pSubqueryList,
                  SubqueryList   rSubqueryList,
                  int[]          hashKeyColumns,
                  ContextManager cm)
	{
        super(childResult, tableProperties, cm);
        setResultColumns( resultColumns );
        this.searchPredicateList = searchPredicateList;
        this.joinPredicateList = joinPredicateList;
        this.trulyTheBestAccessPath = accessPath;
         
        this.pSubqueryList = pSubqueryList;
        this.rSubqueryList = rSubqueryList;
        setHashKeyColumns(hashKeyColumns);
	}

	/*
	 *  Optimizable interface
	 */

 

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

			if (searchPredicateList != null)
			{
				printLabel(depth, "searchPredicateList: ");
				searchPredicateList.treePrint(depth + 1);
			}

			if (joinPredicateList != null)
			{
				printLabel(depth, "joinPredicateList: ");
				joinPredicateList.treePrint(depth + 1);
			}
		}
	}
 

	/**
	 * General logic shared by Core compilation and by the Replication Filter
	 * compiler. A couple ResultSets (the ones used by PREPARE SELECT FILTER)
	 * implement this method.
	 *
	 * @param acb	The ExpressionClassBuilder for the class being built
	 * @param mb the method  the expression will go into
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
	 * @param mb the method  the expression will go into
	 *
	 * @exception StandardException		Thrown on error
	 */

	private void generateMinion(ExpressionClassBuilder acb,
									 MethodBuilder mb, boolean genChildResultSet)
									throws StandardException
	{ }

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

		if (searchPredicateList != null)
		{
			searchPredicateList = (PredicateList)searchPredicateList.accept(v);
		}

		if (joinPredicateList != null)
		{
			joinPredicateList = (PredicateList)joinPredicateList.accept(v);
		}
	}
}
