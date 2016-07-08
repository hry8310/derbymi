/*

   Derby - Class org.apache.derby.impl.sql.compile.CursorNode

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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.dearbaby.sj.ResultMap;
import org.apache.dearbaby.util.ComparatorResult;
import org.apache.dearbaby.util.QueryUtil;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.impl.sql.CursorInfo;
import org.apache.derby.impl.sql.CursorTableReference;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A CursorNode represents a result set that can be returned to a client. A
 * cursor can be a named cursor created by the DECLARE CURSOR statement, or it
 * can be an unnamed cursor associated with a SELECT statement (more precisely,
 * a table expression that returns rows to the client). In the latter case, the
 * cursor does not have a name.
 *
 */

public class CursorNode extends DMLStatementNode {
	final static int UNSPECIFIED = 0;
	public final static int READ_ONLY = 1;
	final static int UPDATE = 2;

	private String name;
	private OrderByList orderByList;
	private ValueNode offset; // <result offset clause> value
	private ValueNode fetchFirst; // <fetch first clause> value
	private boolean hasJDBClimitClause; // true if using JDBC limit/offset
										// escape syntax
	private String statementType;
	private int updateMode;
	private boolean needTarget;

	/**
	 ** There can only be a list of updatable columns when FOR UPDATE is
	 * specified as part of the cursor specification.
	 */
	private List<String> updatableColumns;
	private FromTable updateTable;
	/**
	 * List of {@code TableDescriptor}s for base tables whose associated indexes
	 * should be checked for stale statistics.
	 */
	private ArrayList<TableDescriptor> statsToUpdate;
	private boolean checkIndexStats;

	// If cursor references session schema tables, save the list of those table
	// names in savedObjects in compiler context
	// Following is the position of the session table names list in savedObjects
	// in compiler context
	// At generate time, we save this position in activation for easy access to
	// session table names list from compiler context
	private int indexOfSessionTableNamesInSavedObjects = -1;

	// true if this CursorNode is the driving left-join of a MERGE statement
	private boolean forMergeStatement;

	/**
	 * Constructor for a CursorNode
	 *
	 * @param statementType
	 *            Type of statement (SELECT, UPDATE, INSERT)
	 * @param resultSet
	 *            A ResultSetNode specifying the result set for the cursor
	 * @param name
	 *            The name of the cursor, null if no name
	 * @param orderByList
	 *            The order by list for the cursor, null if no order by list
	 * @param offset
	 *            The value of a <result offset clause> if present
	 * @param fetchFirst
	 *            The value of a <fetch first clause> if present
	 * @param hasJDBClimitClause
	 *            True if the offset/fetchFirst clauses come from JDBC
	 *            limit/offset escape syntax
	 * @param updateMode
	 *            The user-specified update mode for the cursor, for example,
	 *            CursorNode.READ_ONLY
	 * @param updatableColumns
	 *            The array of updatable columns specified by the user in the
	 *            FOR UPDATE clause, null if no updatable columns specified. May
	 *            only be provided if the updateMode parameter is
	 *            CursorNode.UPDATE.
	 * @param forMergeStatement
	 *            True if this cursor is the driving left-join of a MERGE
	 *            statement
	 * @param cm
	 *            The context manager
	 */
	CursorNode(String statementType, ResultSetNode resultSet, String name,
			OrderByList orderByList, ValueNode offset, ValueNode fetchFirst,
			boolean hasJDBClimitClause, int updateMode,
			String[] updatableColumns, boolean forMergeStatement,
			ContextManager cm) {
		super(resultSet, cm);
		this.name = name;
		this.statementType = statementType;
		this.orderByList = orderByList;
		this.offset = offset;
		this.fetchFirst = fetchFirst;
		this.hasJDBClimitClause = hasJDBClimitClause;
		this.updateMode = updateMode;
		this.updatableColumns = updatableColumns == null ? null : Arrays
				.asList(updatableColumns);
		this.forMergeStatement = forMergeStatement;

		/*
		 * * This is a sanity check and not an error since the parser* controls
		 * setting updatableColumns and updateMode.
		 */
		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(this.updatableColumns == null
					|| this.updatableColumns.isEmpty()
					|| this.updateMode == UPDATE,
					"Can only have explicit updatable columns if "
							+ "update mode is UPDATE");
		}
	}

	@Override
	public void genQuery0() {
		resultSet.genQuery(qm);
		/*order by*/
		if(orderByList!=null){
			for(OrderByColumn col: orderByList.v){
				ColumnReference colRef=(ColumnReference)col.expression;
				colRef.genQuery(qm);
			
			};
		}
	}

	@Override
	public void exeQuery0() {
		resultSet.exeQuery();
	}

	@Override
	public boolean fetch() {
		return resultSet.fetch();
	}

	@Override
	public boolean match() {
		return resultSet.match();
	}
	
	@Override
	public Object getColVal(String tbl , String col) {
		return resultSet.getColVal(tbl,col);
	}

	/*获取行信息*/
	@Override
	public HashMap getMatchOrderByRow(OrderByList orderByList ){
		return resultSet.getMatchOrderByRow(orderByList);
	}
	
	 /*获取行信息*/
	@Override
	public HashMap getMatchRow(){
		return resultSet.getMatchRow();
	}
	
 
	
    public HashMap getMatchRow_1(){
    	HashMap map=new HashMap();
		ResultColumnList resultColumns=  getCols(); 
		for (Object o : resultColumns.v) {
			ResultColumn t = (ResultColumn) o;
			if (t._expression instanceof ColumnReference) {
				ColumnReference c=(ColumnReference)t._expression;
				String alias = c._qualifiedTableName.tableName;
				String cName = t.getSourceColumnName(); 
				Object obj =getColVal(alias,cName);
				map.put(alias+"."+cName, obj);
				
			} else if (t._expression instanceof AggregateNode) {
				 
				 
				String name=QueryUtil.getAggrColName(t);
				 
				Object obj =getColVal("#",name);
				map.put("#"+"."+name, obj);
			}else if (t._expression instanceof SubqueryNode) {
				 
				SubqueryNode subQ=(SubqueryNode)t._expression;
				Object obj=subQ.getColVal();
				String name=QueryUtil.getSubSelColName(t);
				 
				//Object obj =qt.getColVal("#",name);
				map.put("#"+"."+name, obj);
			}
			
		}
		return map;
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
			return "name: " + name + "\n" + "updateMode: "
					+ updateModeString(updateMode) + "\n" + super.toString();
		} else {
			return "";
		}
	}

	String statementToString() {
		return statementType;
	}

	/**
	 * Support routine for translating an updateMode identifier to a String
	 *
	 * @param updateMode
	 *            An updateMode identifier
	 *
	 * @return A String representing the update mode.
	 */

	private static String updateModeString(int updateMode) {
		if (SanityManager.DEBUG) {
			switch (updateMode) {
			case UNSPECIFIED:
				return "UNSPECIFIED (" + UNSPECIFIED + ")";

			case READ_ONLY:
				return "READ_ONLY (" + READ_ONLY + ")";

			case UPDATE:
				return "UPDATE (" + UPDATE + ")";

			default:
				return "UNKNOWN VALUE (" + updateMode + ")";
			}
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

			if (orderByList != null) {
				printLabel(depth, "orderByList: " + depth);
				orderByList.treePrint(depth + 1);
			}

			if (offset != null) {
				printLabel(depth, "offset:");
				offset.treePrint(depth + 1);
			}

			if (fetchFirst != null) {
				printLabel(depth, "fetch first/next:");
				fetchFirst.treePrint(depth + 1);
			}
		}
	}

	@Override
	public ResultColumnList getCols(){
		if(resultSet instanceof SelectNode){
			SelectNode sel=(SelectNode)resultSet;
			return sel.resultColumns;
		}
		return null;
	}
	 
 

	/**
	 * Return true if the node references SESSION schema tables (temporary or
	 * permanent)
	 *
	 * @return true if references SESSION schema tables, else false
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	public boolean referencesSessionSchema() throws StandardException {
		// If this node references a SESSION schema table, then return true.
		return resultSet.referencesSessionSchema();
	}

	// Check if this cursor references any session schema tables. If so, pass
	// those names to execution phase through savedObjects
	// This list will be used to check if there are any holdable cursors
	// referencing temporary tables at commit time.
	// If yes, then the data in those temporary tables should be preserved even
	// if they are declared with ON COMMIT DELETE ROWS option
	protected ArrayList<String> getSessionSchemaTableNamesForCursor()
			throws StandardException {
		FromList fromList = resultSet.getFromList();
		int fromListSize = fromList.size();
		FromTable fromTable;
		ArrayList<String> sessionSchemaTableNames = null;

		for (int i = 0; i < fromListSize; i++) {
			fromTable = (FromTable) fromList.elementAt(i);
			if (fromTable instanceof FromBaseTable
					&& isSessionSchema(fromTable.getTableDescriptor()
							.getSchemaDescriptor())) {
				if (sessionSchemaTableNames == null)
					sessionSchemaTableNames = new ArrayList<String>();
				sessionSchemaTableNames.add(fromTable.getTableName()
						.getTableName());
			}
		}

		return sessionSchemaTableNames;
	}

	 
	
	public List<ResultMap> getMatchRows(){
		if(orderByList==null){
			return resultSet.getMatchRows();
		}
		List<ResultMap>  list= resultSet.getMatchOrderByRows(orderByList);
		Collections.sort(list, new ComparatorResult(orderByList));  
		return list;
	}

	 
	/**
	 * Returns the type of activation this class generates.
	 * 
	 * @return either (NEED_CURSOR_ACTIVATION
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	int activationKind() {
		return NEED_CURSOR_ACTIVATION;
	}

	 

	// class interface

	String getUpdateBaseTableName() {
		return (updateTable == null) ? null : updateTable.getBaseTableName();
	}

	String getUpdateExposedTableName() throws StandardException {
		return (updateTable == null) ? null : updateTable.getExposedName();
	}

	String getUpdateSchemaName() throws StandardException {
		// we need to use the base table for the schema name
		return (updateTable == null) ? null : ((FromBaseTable) updateTable)
				.getTableNameField().getSchemaName();
	}

	int getUpdateMode() {
		return updateMode;
	}

	/**
	 * Returns whether or not this Statement requires a set/clear savepoint
	 * around its execution. The following statement "types" do not require
	 * them: Cursor - unnecessary and won't work in a read only environment Xact
	 * - savepoint will get blown away underneath us during commit/rollback
	 *
	 * @return boolean Whether or not this Statement requires a set/clear
	 *         savepoint
	 */
	@Override
	public boolean needsSavepoint() {
		return false;
	}

	/**
	 * Get information about this cursor. For sps, this is info saved off of the
	 * original query tree (the one for the underlying query).
	 *
	 * @return the cursor info
	 * @exception StandardException
	 *                thrown if generation fails
	 */
	@Override
	public Object getCursorInfo() throws StandardException {
		if (!needTarget)
			return null;

		return new CursorInfo(updateMode, new CursorTableReference(
				getUpdateExposedTableName(), getUpdateBaseTableName(),
				getUpdateSchemaName()), updatableColumns);
	}
 

	String getXML() {
		return null;
	}

	/**
	 * Returns a list of base tables for which the index statistics of the
	 * associated indexes should be updated.
	 *
	 * @return A list of table descriptors (potentially empty).
	 * @throws StandardException
	 *             if accessing the index descriptors of a base table fails
	 */
	@Override
	public TableDescriptor[] updateIndexStatisticsFor()
			throws StandardException {
		if (!checkIndexStats || statsToUpdate == null) {
			return EMPTY_TD_LIST;
		}
		// Remove table descriptors whose statistics are considered up-to-date.
		// Iterate backwards to remove elements, chances are high the stats are
		// mostly up-to-date (minor performance optimization to avoid copy).
		for (int i = statsToUpdate.size() - 1; i >= 0; i--) {
			TableDescriptor td = statsToUpdate.get(i);
			if (td.getAndClearIndexStatsIsUpToDate()) {
				statsToUpdate.remove(i);
			}
		}
		if (statsToUpdate.isEmpty()) {
			return EMPTY_TD_LIST;
		} else {
			TableDescriptor[] tmp = new TableDescriptor[statsToUpdate.size()];
			statsToUpdate.toArray(tmp);
			statsToUpdate.clear();
			return tmp;
		}
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

		if (orderByList != null) {
			orderByList.acceptChildren(v);
		}
		if (offset != null) {
			offset.acceptChildren(v);
		}
		if (fetchFirst != null) {
			fetchFirst.acceptChildren(v);
		}
	}

}
