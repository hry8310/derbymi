/*

   Derby - Class org.apache.derby.impl.sql.compile.SelectNode

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.dearbaby.query.FilterRowValue;
import org.apache.dearbaby.query.RowColumn;
import org.apache.dearbaby.query.SinQuery;
import org.apache.dearbaby.sj.ResultMap;
import org.apache.dearbaby.util.ColCompare;
import org.apache.dearbaby.util.QueryUtil;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.Limits;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.JoinStrategy;
import org.apache.derby.iapi.sql.compile.OptimizableList;
import org.apache.derby.iapi.sql.compile.OptimizablePredicateList;
import org.apache.derby.iapi.sql.compile.Optimizer;
import org.apache.derby.iapi.sql.compile.OptimizerFactory;
import org.apache.derby.iapi.sql.compile.OptimizerPlan;
import org.apache.derby.iapi.sql.compile.RequiredRowOrdering;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A SelectNode represents the result set for any of the basic DML operations:
 * SELECT, INSERT, UPDATE, and DELETE. (A RowResultSetNode will be used for an
 * INSERT with a VALUES clause.) For INSERT - SELECT, any of the fields in a
 * SelectNode can be used (the SelectNode represents the SELECT statement in the
 * INSERT - SELECT). For UPDATE and DELETE, there will be one table in the
 * fromList, and the groupByList fields will be null. For both INSERT and
 * UPDATE, the resultColumns in the selectList will contain the names of the
 * columns being inserted into or updated.
 *
 */

class SelectNode extends ResultSetNode {
	/**
	 * List of tables in the FROM clause of this SELECT
	 */
	FromList fromList;
	FromTable targetTable;

	/** Aggregates in the SELECT list. */
	private List<AggregateNode> selectAggregates;
	/** Aggregates in the WHERE clause. */
	private List<AggregateNode> whereAggregates;
	/** Aggregates in the HAVING clause. */
	private List<AggregateNode> havingAggregates;

	/**
	 * The ValueNode for the WHERE clause must represent a boolean expression.
	 * The binding phase will enforce this - the parser does not have enough
	 * information to enforce it in all cases (for example, user methods that
	 * return boolean).
	 */
	ValueNode whereClause;
	ValueNode originalWhereClause;

	/**
	 * List of result columns in GROUP BY clause
	 */
	GroupByList groupByList;

	/**
	 * List of windows.
	 */
	WindowList windows;

	/** Full plan for this SELECT as specified in an optimizer override */
	OptimizerPlan overridingPlan;

	/**
	 * List of window function calls (e.g. ROW_NUMBER, AVG(i), DENSE_RANK).
	 */
	List<WindowFunctionNode> windowFuncCalls;

	/**
	 * User specified a group by without aggregates and we turned it into a
	 * select distinct
	 */
	private boolean wasGroupBy;

	boolean orderByQuery;

	QueryExpressionClauses qec = new QueryExpressionClauses();

	/* PredicateLists for where clause */
	PredicateList wherePredicates;

	/* SubqueryLists for select where and having clauses */
	SubqueryList selectSubquerys;
	SubqueryList whereSubquerys;
	SubqueryList havingSubquerys;

	/* Whether or not we are only binding the target list */
	private boolean bindTargetListOnly;

	private boolean isDistinct;

	private boolean orderByAndDistinctMerged;

	boolean originalWhereClauseHadSubqueries;

	/* Copy of fromList prior to generating join tree */
	private FromList preJoinFL;

	ValueNode havingClause;

	private int nestingLevel;
	
	boolean haveAggr=false;

	List<QueryTreeNode> qryNodes = new ArrayList<QueryTreeNode>();
	QueryTreeNode parentNode;
	
	void putParentNode(QueryTreeNode q){
		parentNode=q;
	}

	SelectNode(ResultColumnList selectList, FromList fromList,
			ValueNode whereClause, GroupByList groupByList,
			ValueNode havingClause, WindowList windowDefinitionList,
			OptimizerPlan overridingPlan, ContextManager cm)
			throws StandardException {
		super(cm);
		/*
		 * RESOLVE - Consider adding selectAggregates and whereAggregates
		 */
	 
		setResultColumns(selectList);

		if (getResultColumns() != null) {
			getResultColumns().markInitialSize();
		}

		this.fromList = fromList;
		this.whereClause = whereClause;
		this.originalWhereClause = whereClause;
		this.groupByList = groupByList;
		this.havingClause = havingClause;

		// This initially represents an explicit <window definition list>, as
		// opposed to <in-line window specifications>, see 2003, 6.10 and 6.11.
		// <in-line window specifications> are added later, see right below for
		// in-line window specifications used in window functions in the SELECT
		// column list and in genProjectRestrict for such window specifications
		// used in window functions in ORDER BY.
		this.windows = windowDefinitionList;

		this.overridingPlan = overridingPlan;

		bindTargetListOnly = false;

		this.originalWhereClauseHadSubqueries = false;
		if (this.whereClause != null) {
			CollectNodesVisitor<SubqueryNode> cnv = new CollectNodesVisitor<SubqueryNode>(
					SubqueryNode.class, SubqueryNode.class);
			this.whereClause.accept(cnv);
			if (!cnv.getList().isEmpty()) {
				this.originalWhereClauseHadSubqueries = true;
			}
		}

		if (getResultColumns() != null) {

			// Collect simply contained window functions (note: *not*
			// any inside nested SELECTs) used in result columns, and
			// check them for any <in-line window specification>s.

			CollectNodesVisitor<WindowFunctionNode> cnvw = new CollectNodesVisitor<WindowFunctionNode>(
					WindowFunctionNode.class, SelectNode.class);
			getResultColumns().accept(cnvw);
			windowFuncCalls = cnvw.getList();

			for (int i = 0; i < windowFuncCalls.size(); i++) {
				WindowFunctionNode wfn = windowFuncCalls.get(i);

				// Some window function, e.g. ROW_NUMBER() contains an inline
				// window specification, so we add it to our list of window
				// definitions.

				if (wfn.getWindow() instanceof WindowDefinitionNode) {
					// Window function call contains an inline definition, add
					// it to our list of windows.
					windows = addInlinedWindowDefinition(windows, wfn);
				} else {
					// a window reference, bind it later.

					if (SanityManager.DEBUG) {
						SanityManager
								.ASSERT(wfn.getWindow() instanceof WindowReferenceNode);
					}
				}
			}
		}
	}

	@Override
	public void genQuery0() {
		SinQuery tmp=null;
		int fromCnt=0;
		for (Object o : fromList.v) {
			if (o instanceof FromBaseTable) {
				fromCnt++;
				FromBaseTable t = (FromBaseTable) o;
				tmp=qm.addNode(t.correlationName, t.tableName.tableName, this);
			} else if (o instanceof HalfOuterJoinNode) {
				fromCnt=10;
				HalfOuterJoinNode t = (HalfOuterJoinNode) o;
				qryNodes.add(t);
				t.genQuery(qm);
			}else if (o instanceof FromSubquery ) {
				fromCnt=10;
				FromSubquery t = (FromSubquery) o;
				t.genQuery(qm);
			}
		}
		/*not simpleQery*/
		if(fromCnt>1){
			tmp=null;
		}else{
			tmp.simpleSelect=true;
		}
/* 老的实现，需优化，在优化确定没有问题前，保留旧的实现
		for (Object o : resultColumns.v) {
			ResultColumn t = (ResultColumn) o;
			if (t._expression instanceof ColumnReference) {
				ColumnReference c = (ColumnReference) t._expression;
				qm.currNode=this;
				c.genQuery(qm);
			} else if (t._expression instanceof AggregateNode) {
				haveAggr=true;
				AggregateNode agg=(AggregateNode)t._expression;
				agg.genQuery(qm);
			}else if (t._expression instanceof SubqueryNode) {
				SubqueryNode sn=(SubqueryNode)t._expression;
				sn.genQuery(qm);
				sn.putParentNode(this);
				 
			}
			// qm.addCol(c.getTableName(), c._columnName);
		}
*/		
		
		for (Object o : resultColumns.v) {
			ResultColumn t = (ResultColumn) o;
			if (t._expression instanceof AggregateNode) {
				haveAggr=true;
			}
			t._expression.genQuery(qm);
		}
		/*group by*/
		if(groupByList!=null){
			for(GroupByColumn col: groupByList.v){
				ColumnReference colRef=(ColumnReference)col.columnExpression;
				colRef.genQuery(qm);
			
			};
		}
	
		
		if (whereClause != null){
			qm.currWhereQuery=tmp;
			qm.currNode=this;
			if(tmp!=null){
				qm.currWhereQuery.whereClause=whereClause;
			}
			whereClause.genQuery(qm);
			whereClause.genCondition();
		}
		qm.currWhereQuery=null;
	}

	@Override
	public void exeQuery0() {
		if (whereClause != null)
			whereClause.exeQuery();
		for (int i = qryNodes.size() - 1; i > -1; i--) {
			QueryTreeNode n = qryNodes.get(i);
			n.exeQuery();
		}
		
		for (Object o : resultColumns.v) {
			ResultColumn t = (ResultColumn) o;
			if (t._expression instanceof SubqueryNode) {
				t._expression.exeQuery();
			}
			// qm.addCol(c.getTableName(), c._columnName);
		}
		
		for (Object o : fromList.v) {
			 if (o instanceof FromSubquery) {
				FromSubquery t = (FromSubquery) o;
				t.exeQuery();
				ArrayList<Map> list =t.getRest();
				SinQuery sq=new SinQuery();
				sq.alias=t.correlationName;
				sq.tableName="";
				sq.results=list;
				qs.querys.add(sq);
				qm.addOrReplaceQs(sq);
			}
		}
		
		
		
	}
	
	
	
	
	
	
	public void exeSubCol(ResultColumn t,RowColumn rc){
		SubqueryNode sq=(SubqueryNode)t._expression;
		SelectNode sn=(SelectNode)sq.resultSet;
		List<ResultColumn> list = sn.resultColumns.v;
		if (list.size() != 1) {
			return  ;
		}
		ResultColumn c = list.get(0);
		sn.fetchInit();
		Object obj=null;
		sn.setIsFilter(false);
		while(sn.fetch()){
			if(sn.match()){
				obj=sn.getColVal(c.getTableName(), c.getSourceColumnName());
			}
		}
		 
		String name =QueryUtil.getSubSelColName(t);
		rc.add2Row("#", name, obj);
	}
	
	RowColumn noGroupByOneRow=null;
	private void noGroupBy(){
		if(haveAggr==true){
			
			noGroupByHaveAggr();
			
		}else{
			//setIsFilter(false) ;
			//noGroupByNoAggr();
		}
	}
	
	
	private void noGroupByNoAggr(){
		RowColumn rc=new RowColumn();
		for (Object o : resultColumns.v) {
			ResultColumn t = (ResultColumn) o;
			if (t._expression instanceof ColumnReference) {
				ColumnReference c=(ColumnReference)t._expression;
				String alias = c._qualifiedTableName.tableName;
				String cName = t.getSourceColumnName(); 
				Object obj = qm.findFetchRow(alias).getCurrCol(cName);
				rc.add2Row(alias, cName, obj);
			}else if (t._expression instanceof SubqueryNode) {
				exeSubCol(t,rc);
			}
		}
		rowValue.add2Row(rc);
		rowValue.flushRow();
	}
	
	private void noGroupByHaveAggr(){
		boolean first=false;
	 
		if(noGroupByOneRow==null){
			noGroupByOneRow=new RowColumn();
			first=true;
		
			for (Object o : resultColumns.v) {
				ResultColumn t = (ResultColumn) o;
				if (t._expression instanceof ColumnReference) {
					ColumnReference c=(ColumnReference)t._expression;
					String alias = c._qualifiedTableName.tableName;
					String cName = t.getSourceColumnName(); 
					Object obj = qm.findFetchRow(alias).getCurrCol(cName);
					noGroupByOneRow.add2Row(alias, cName, obj);
				}else if (t._expression instanceof SubqueryNode) {
					exeSubCol(t,noGroupByOneRow);
				}
			}
			
			rowValue.flushTheRow(noGroupByOneRow);
			//rowValue.flushRow();
		}
		
		for (Object o : resultColumns.v) {
			ResultColumn t = (ResultColumn) o;
			if (t._expression instanceof AggregateNode) {
				AggregateNode agg=(AggregateNode)t._expression;
				String fun=agg.aggregateName; 
				aggre(noGroupByOneRow,first,fun,t);
			}
			
		}
		
	}
	 
	public void groupBy(  ){
		if(groupByList==null){
			return  ;
		}
		
		RowColumn rcc=new RowColumn();
		for(GroupByColumn col: groupByList.v){
			ColumnReference c=(ColumnReference)col.columnExpression;
		    String alias = c._qualifiedTableName.tableName;
			String cName = c._columnName;
			Object obj = qm.findFetchRow(alias).getCurrCol(cName);
			rcc.add2Row(alias,cName,obj);
		};
		RowColumn tmp =rowValue.findRow(rcc);
		if(tmp==null){
		   tmp=rcc;
		   dealAggr(tmp,true);
		}else{
			dealAggr(tmp,false);
		}
		for (Object o : resultColumns.v) {
			ResultColumn t = (ResultColumn) o;
			if (t._expression instanceof SubqueryNode) {
				exeSubCol(t,tmp);
			}
		}
		rowValue.add2Row(tmp);
		rowValue.flushRow();
	}
	
	private void dealAggr(RowColumn rcc,boolean first){
		 
			for (Object o : resultColumns.v) {
				ResultColumn t = (ResultColumn) o;
				if (t._expression instanceof AggregateNode) {
					AggregateNode agg=(AggregateNode)t._expression;
					String fun=agg.aggregateName; 
					aggre(rcc,first,fun,t);
					   
				}
			}
	}

	private void aggre(RowColumn rc,boolean first,String fun, ResultColumn t){
		if(fun.equalsIgnoreCase("COUNT")){
			aggreCount(rc,first,t);
		}
		if(fun.equalsIgnoreCase("SUM")){
			aggreSum(rc,first,t);
		}
		if(fun.equalsIgnoreCase("MAX")){
			aggreMinOrMax(rc,first,t,true);
		}
		if(fun.equalsIgnoreCase("min")){
			aggreMinOrMax(rc,first,t,false);
		}
	}
	
	private void aggreCount(RowColumn rc,boolean first, ResultColumn t){
		AggregateNode agg=(AggregateNode)t._expression;
	 
		ColumnReference c=(ColumnReference)agg.operand;
		String name=t._underlyingName;
		if(name==null){
			name=c.getColumnName();
		}
		if(first){
			rc.add2Row("#", name, 1);;
		}else{
			Object o=rc.findVal("#",name);
			if(o==null){
				rc.add2Row("#", name, 1);;
			}else{
				int i=Integer.valueOf(o.toString());
				i++;
				rc.replaceRow("#", name, i);
			}
		}
		
	}
	
	
	private void aggreSum(RowColumn rc,boolean first, ResultColumn t){
		AggregateNode agg=(AggregateNode)t._expression;
	 
		ColumnReference c=(ColumnReference)agg.operand;
		String name=QueryUtil.getAggrColName(t);
		 
		String alias = c._qualifiedTableName.tableName;
		String cName = c.getColumnName(); 
		Object obj = qm.findFetchRow(alias).getCurrCol(cName);
		int ri=0;
		if(obj!=null){
			ri=Integer.valueOf(obj.toString());
		}
		if(first){
			rc.add2Row("#", name, ri);;
		}else{
			Object o=rc.findVal("#",name);
			
			 
			if(o==null){
				rc.add2Row("#", name,ri);;
			}else{
				int i=Integer.valueOf(o.toString());
				rc.replaceRow("#", name, i+ri);
			}
		}
		
	}
	//max-true
	private void aggreMinOrMax(RowColumn rc,boolean first, ResultColumn t,boolean max){
		AggregateNode agg=(AggregateNode)t._expression;
	 
		ColumnReference c=(ColumnReference)agg.operand;
		String name=t._underlyingName;
		if(name==null){
			name=c.getColumnName();
		}
		String alias = c._qualifiedTableName.tableName;
		String cName = c.getColumnName(); 
		Object obj = qm.findFetchRow(alias).getCurrCol(cName);
		
		if(first){
			rc.add2Row("#", name, obj);;
		}else{
			Object o=rc.findVal("#",name);
			if(o==null){
				rc.add2Row("#", name, obj);;
			}else{
				if (max==false&&ColCompare.compareObject(o, obj) < 0){
					rc.replaceRow("#", name, obj);
				}
				if (max==true&&ColCompare.compareObject(o, obj) > 0){
					rc.replaceRow("#", name, obj);
				}
			}
		}
		
	}
	
	@Override
	public void exeFilter0(){
		if(groupByList==null&&haveAggr==false){
			return ;
		} 
		if(haveAggr==false){
			return;
		}
		
		while(fetch()){
			if(match()){
				if(groupByList==null){
					noGroupBy() ;
				}else{
					groupBy();
				}
			}
		}
		setIsFilter(true);
		
	}
	
	public void reExeFilter(){
		rowValue=new   FilterRowValue();
		exeFilter();
	}
	

	@Override
	public boolean match() {
		boolean r = true;
		if (whereClause != null)
			r = whereClause.match();
		return r;
	}

	@Override
	public boolean fetch() {
		if(getIsFilter()==true){
			return rowValue.next();
		}
		return _fetch();
	}
	public boolean _fetch() {
		boolean f = true;
		if (first == true) {
			f = fetch0();
			first = false;
		}
		while (f == true) {
			while (halfFetch() == true) {

				return true;
			}
			first = false;
			f = fetch0();

		}

		return false;
	}

	boolean first = true;
	
	@Override
	public void fetchInit() {
		
		super.fetchInit();
		first = true;
	
	}

	private boolean halfFetch() {
		if (qryNodes.size() == 0) {
			first = true;
			return true;
		}
		for (int i = qryNodes.size() - 1; i > -1; i--) {
			QueryTreeNode n = qryNodes.get(i);
			if (n.fetch()) {
				return true;
			} else {

				n.fetchInit();
				if (i == 0) {
					// first = true;
					return false;
				}
			}
		}

		return false;
	}

	
	
	 /*获取行信息*/
		@Override
	    protected HashMap getMatchRow(){
	    	HashMap map=new HashMap();
			 
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
		
		
		 /*获取行信息*/
		@Override
	    protected HashMap getMatchRow(String _alias){
	    	HashMap map=new HashMap();
			 
			for (Object o : resultColumns.v) {
				ResultColumn t = (ResultColumn) o;
				if (t._expression instanceof ColumnReference) {
					ColumnReference c=(ColumnReference)t._expression;
					String alias = c._qualifiedTableName.tableName;
					String cName = t.getSourceColumnName(); 
					Object obj =getColVal(alias,cName);
					map.put(cName, obj);
					
				} else if (t._expression instanceof AggregateNode) {
					 
					 
					String name=QueryUtil.getAggrColName(t);
					 
					Object obj =getColVal("#",name);
					map.put(name, obj);
				}else if (t._expression instanceof SubqueryNode) {
					 
					SubqueryNode subQ=(SubqueryNode)t._expression;
					Object obj=subQ.getColVal();
					String name=QueryUtil.getSubSelColName(t);
					 
					//Object obj =qt.getColVal("#",name);
					map.put(name, obj);
				}
				
			}
			return map;
	}
	@Override	
	public List<ResultMap> getMatchOrderByRows(OrderByList orderByList){
		List<ResultMap> list=super.getMatchOrderByRows(orderByList);
		if(hasDistinct()){
			Set<ResultMap> s=new HashSet<ResultMap>();
			s.addAll(list);
			List<ResultMap> l=new ArrayList<ResultMap>();
			l.addAll(s);
			return l;
		}
		return list;
	}
	
	@Override	
	public List<ResultMap> getMatchRows(){
		List<ResultMap> list=super.getMatchRows();
		if(hasDistinct()){
			Set<ResultMap> s=new HashSet<ResultMap>();
			s.addAll(list);
			List<ResultMap> l=new ArrayList<ResultMap>();
			l.addAll(s);
			return l;
		}
		return list;
	}
		
		/*获取行信息*/
		@Override
	    public HashMap getMatchOrderByRow(OrderByList orderByList ){
	    	HashMap map =getMatchRow();
			 
	    	for(OrderByColumn oc:orderByList.v ){
				 
				if (oc.expression instanceof ColumnReference) {
					ColumnReference c=(ColumnReference)oc.expression;
					String alias = c._qualifiedTableName.tableName;
					String cName = c.getColumnName();
					Object obj =getColVal(alias,cName);
					map.put(alias+"."+cName, obj);
				} 
				
			}
			return map;
	}
	
	private WindowList addInlinedWindowDefinition(WindowList wl,
			WindowFunctionNode wfn) {
		WindowDefinitionNode wdn = (WindowDefinitionNode) wfn.getWindow();

		if (wl == null) {
			// This is the first window we see, so initialize list.
			wl = new WindowList(getContextManager());
		}

		WindowDefinitionNode equiv = wdn.findEquivalentWindow(wl);

		if (equiv != null) {
			// If the window is equivalent an existing one, optimize
			// it away.

			wfn.setWindow(equiv);
		} else {
			// remember this window for posterity

			wl.addWindow((WindowDefinitionNode) wfn.getWindow());
		}

		return wl;
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
			return "isDistinct: " + isDistinct + "\n" + super.toString();
		} else {
			return "";
		}
	}

	String statementToString() {
		return "SELECT";
	}

	void makeDistinct() {
		isDistinct = true;
	}

	void clearDistinct() {
		isDistinct = false;
	}

	boolean hasDistinct() {
		return isDistinct;
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

			if (selectSubquerys != null) {
				printLabel(depth, "selectSubquerys: ");
				selectSubquerys.treePrint(depth + 1);
			}

			printLabel(depth, "fromList: ");

			if (fromList != null) {
				fromList.treePrint(depth + 1);
			}

			if (whereClause != null) {
				printLabel(depth, "whereClause: ");
				whereClause.treePrint(depth + 1);
			}

			if ((wherePredicates != null) && wherePredicates.size() > 0) {
				printLabel(depth, "wherePredicates: ");
				wherePredicates.treePrint(depth + 1);
			}

			if (whereSubquerys != null) {
				printLabel(depth, "whereSubquerys: ");
				whereSubquerys.treePrint(depth + 1);
			}

			if (groupByList != null) {
				printLabel(depth, "groupByList:");
				groupByList.treePrint(depth + 1);
			}

			if (havingClause != null) {
				printLabel(depth, "havingClause:");
				havingClause.treePrint(depth + 1);
			}

			printQueryExpressionSuffixClauses(depth, qec);

			if (preJoinFL != null) {
				printLabel(depth, "preJoinFL: ");
				preJoinFL.treePrint(depth + 1);
			}

			if (windows != null) {
				printLabel(depth, "windows: ");
				windows.treePrint(depth + 1);
			}
		}
	}

	/**
	 * Return the fromList for this SelectNode.
	 *
	 * @return FromList The fromList for this SelectNode.
	 */
	@Override
	FromList getFromList() {
		return fromList;
	}

	/**
	 * Find colName in the result columns and return underlying columnReference.
	 * Note that this function returns null if there are more than one FromTable
	 * for this SelectNode and the columnReference needs to be directly under
	 * the resultColumn. So having an expression under the resultSet would cause
	 * returning null.
	 *
	 * @param colName
	 *            Name of the column
	 *
	 * @return ColumnReference ColumnReference to the column, if found
	 */
	ColumnReference findColumnReferenceInResult(String colName)
			throws StandardException {
		if (fromList.size() != 1)
			return null;

		// This logic is similar to SubQueryNode.singleFromBaseTable(). Refactor
		FromTable ft = (FromTable) fromList.elementAt(0);
		if (!((ft instanceof ProjectRestrictNode) && ((ProjectRestrictNode) ft)
				.getChildResult() instanceof FromBaseTable)
				&& !(ft instanceof FromBaseTable))
			return null;

		// Loop through the result columns looking for a match
		for (ResultColumn rc : getResultColumns()) {
			if (!(rc.getExpression() instanceof ColumnReference))
				return null;

			ColumnReference crNode = (ColumnReference) rc.getExpression();

			if (crNode.getColumnName().equals(colName))
				return (ColumnReference) crNode.getClone();
		}

		return null;
	}

	/**
	 * Return the whereClause for this SelectNode.
	 *
	 * @return ValueNode The whereClause for this SelectNode.
	 */
	ValueNode getWhereClause() {
		return whereClause;
	}

	/**
	 * Return the wherePredicates for this SelectNode.
	 *
	 * @return PredicateList The wherePredicates for this SelectNode.
	 */
	PredicateList getWherePredicates() {
		return wherePredicates;
	}

	/**
	 * Return the selectSubquerys for this SelectNode.
	 *
	 * @return SubqueryList The selectSubquerys for this SelectNode.
	 */
	SubqueryList getSelectSubquerys() {
		return selectSubquerys;
	}

	/**
	 * Return the whereSubquerys for this SelectNode.
	 *
	 * @return SubqueryList The whereSubquerys for this SelectNode.
	 */
	SubqueryList getWhereSubquerys() {
		return whereSubquerys;
	}

	/**
	 * Bind the tables in this SelectNode. This includes getting their
	 * TableDescriptors from the DataDictionary and numbering the FromTables.
	 * NOTE: Because this node represents the top of a new query block, we bind
	 * both the non VTI and VTI tables under this node in this method call.
	 *
	 * @param dataDictionary
	 *            The DataDictionary to use for binding
	 * @param fromListParam
	 *            FromList to use/append to.
	 *
	 * @return ResultSetNode
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ResultSetNode bindNonVTITables(DataDictionary dataDictionary,
			FromList fromListParam) throws StandardException {
		int fromListSize = fromList.size();

		wherePredicates = new PredicateList(getContextManager());
		preJoinFL = new FromList(false, getContextManager());

		/* Set the nesting level in the fromList */
		if (fromListParam.size() == 0) {
			nestingLevel = 0;
		} else {
			nestingLevel = ((FromTable) fromListParam.elementAt(0)).getLevel() + 1;
		}
		fromList.setLevel(nestingLevel);

		/*
		 * Splice a clone of our FromList on to the beginning of fromListParam,
		 * before binding the tables, for correlated column resolution in VTIs.
		 */
		for (int index = 0; index < fromListSize; index++) {
			fromListParam.insertElementAt(fromList.elementAt(index), 0);
		}

		// Now bind our from list
		fromList.bindTables(dataDictionary, fromListParam);

		/* Restore fromListParam */
		for (int index = 0; index < fromListSize; index++) {
			fromListParam.removeElementAt(0);
		}

		// if an explicit join plan is requested, bind it
		if (overridingPlan != null) {
			overridingPlan.bind(dataDictionary, getLanguageConnectionContext(),
					getCompilerContext());
		}

		return this;
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
		/*
		 * We first bind the resultColumns for any FromTable which needs its own
		 * binding, such as JoinNodes. We pass through the fromListParam without
		 * adding our fromList to it, since the elements in our fromList can
		 * only be correlated with outer query blocks.
		 */
		fromList.bindResultColumns(fromListParam);
		super.bindResultColumns(fromListParam);
		/* Only 1012 elements allowed in select list */
		if (getResultColumns().size() > Limits.DB2_MAX_ELEMENTS_IN_SELECT_LIST) {
			throw StandardException
					.newException(SQLState.LANG_TOO_MANY_ELEMENTS);
		}

		// DERBY-4407: A derived table must have at least one column.
		if (getResultColumns().size() == 0) {
			throw StandardException
					.newException(SQLState.LANG_EMPTY_COLUMN_LIST);
		}
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
		/*
		 * We first bind the resultColumns for any FromTable which needs its own
		 * binding, such as JoinNodes. We pass through the fromListParam without
		 * adding our fromList to it, since the elements in our fromList can
		 * only be correlated with outer query blocks.
		 */
		fromList.bindResultColumns(fromListParam);
		super.bindResultColumns(targetTableDescriptor, targetVTI,
				targetColumnList, statement, fromListParam);
	}

	/**
	 * Push an expression into this SELECT (and possibly down into one of the
	 * tables in the FROM list). This is useful when trying to push predicates
	 * into unflattened views or derived tables.
	 *
	 * @param predicate
	 *            The predicate that we attempt to push
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	void pushExpressionsIntoSelect(Predicate predicate)
			throws StandardException {
		wherePredicates.pullExpressions(getReferencedTableMap().size(),
				predicate.getAndNode());
		fromList.pushPredicates(wherePredicates);
	}

	/**
	 * Verify that a SELECT * is valid for this type of subquery.
	 *
	 * @param outerFromList
	 *            The FromList from the outer query block(s)
	 * @param subqueryType
	 *            The subquery type
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	void verifySelectStarSubquery(FromList outerFromList, int subqueryType)
			throws StandardException {
		for (ResultColumn rc : getResultColumns()) {
			if (!(rc instanceof AllResultColumn)) {
				continue;
			}

			/*
			 * Select * currently only valid for EXISTS/NOT EXISTS. NOT EXISTS
			 * does not appear prior to preprocessing.
			 */
			if (subqueryType != SubqueryNode.EXISTS_SUBQUERY) {
				throw StandardException
						.newException(SQLState.LANG_CANT_SELECT_STAR_SUBQUERY);
			}

			/*
			 * If the AllResultColumn is qualified, then we have to verify that
			 * the qualification is a valid exposed name. NOTE: The exposed name
			 * can come from an outer query block.
			 */
			String fullTableName = ((AllResultColumn) rc).getFullTableName();

			if (fullTableName != null) {
				if (fromList.getFromTableByName(fullTableName, null, true) == null
						&& outerFromList.getFromTableByName(fullTableName,
								null, true) == null) {

					if (fromList.getFromTableByName(fullTableName, null, false) == null
							&& outerFromList.getFromTableByName(fullTableName,
									null, false) == null) {
						throw StandardException.newException(
								SQLState.LANG_EXPOSED_NAME_NOT_FOUND,
								fullTableName);
					}
				}
			}
		}
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
		return fromList.getFromTableByName(name, schemaName, exactMatch);
	}

	/**
	 * Check for (and reject) ? parameters directly under the ResultColumns.
	 * This is done for SELECT statements.
	 *
	 * @exception StandardException
	 *                Thrown if a ? parameter found directly under a
	 *                ResultColumn
	 */
	@Override
	void rejectParameters() throws StandardException {
		super.rejectParameters();
		fromList.rejectParameters();
	}

	@Override
	public void pushQueryExpressionSuffix() {
		qec.push();
	}

	/**
	 * Push the order by list down from the cursor node into its child result
	 * set so that the optimizer has all of the information that it needs to
	 * consider sort avoidance.
	 *
	 * @param orderByList
	 *            The order by list
	 */
	@Override
	void pushOrderByList(OrderByList orderByList) {
		qec.setOrderByList(orderByList);
		// remember that there was an order by list
		orderByQuery = true;
	}

	/**
	 * Push down the offset and fetch first parameters to this node.
	 *
	 * @param offset
	 *            the OFFSET, if any
	 * @param fetchFirst
	 *            the OFFSET FIRST, if any
	 * @param hasJDBClimitClause
	 *            true if the clauses were added by (and have the semantics of)
	 *            a JDBC limit clause
	 */
	@Override
	void pushOffsetFetchFirst(ValueNode offset, ValueNode fetchFirst,
			boolean hasJDBClimitClause) {
		qec.setOffset(offset);
		qec.setFetchFirst(fetchFirst);
		qec.setHasJDBCLimitClause(Boolean.valueOf(hasJDBClimitClause));
	}

	 

	/**
	 * Peform the various types of transitive closure on the where clause. The 2
	 * types are transitive closure on join clauses and on search clauses. Join
	 * clauses will be processed first to maximize benefit for search clauses.
	 *
	 * @param numTables
	 *            The number of tables in the query
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private void performTransitiveClosure(int numTables)
			throws StandardException {
		// Join clauses
		wherePredicates.joinClauseTransitiveClosure(numTables, fromList,
				getCompilerContext());

		// Search clauses
		wherePredicates.searchClauseTransitiveClosure(numTables,
				fromList.hashJoinSpecified());
	}

	/**
	 * Put the expression trees in conjunctive normal form
	 *
	 * @param boolClause
	 *            clause to normalize
	 * 
	 * @exception StandardException
	 *                Thrown on error
	 */
	private ValueNode normExpressions(ValueNode boolClause)
			throws StandardException {
		/*
		 * For each expression tree: o Eliminate NOTs (eliminateNots()) o Ensure
		 * that there is an AndNode on top of every top level expression.
		 * (putAndsOnTop()) o Finish the job (changeToCNF())
		 */
		if (boolClause != null) {
			boolClause = boolClause.eliminateNots(false);
			if (SanityManager.DEBUG) {
				if (!(boolClause.verifyEliminateNots())) {
					boolClause.treePrint();
					SanityManager.THROWASSERT("boolClause in invalid form: "
							+ boolClause);
				}
			}
			boolClause = boolClause.putAndsOnTop();
			if (SanityManager.DEBUG) {
				if (!((boolClause instanceof AndNode) && (boolClause
						.verifyPutAndsOnTop()))) {
					boolClause.treePrint();
					SanityManager.THROWASSERT("boolClause in invalid form: "
							+ boolClause);
				}
			}
			boolClause = boolClause.changeToCNF(true);
			if (SanityManager.DEBUG) {
				if (!((boolClause instanceof AndNode) && (boolClause
						.verifyChangeToCNF()))) {
					boolClause.treePrint();
					SanityManager.THROWASSERT("boolClause in invalid form: "
							+ boolClause);
				}
			}
		}

		return boolClause;
	}

	/**
	 * Add a new predicate to the list. This is useful when doing subquery
	 * transformations, when we build a new predicate with the left side of the
	 * subquery operator and the subquery's result column.
	 *
	 * @param predicate
	 *            The predicate to add
	 *
	 * @return ResultSetNode The new top of the tree.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ResultSetNode addNewPredicate(Predicate predicate) throws StandardException {
		wherePredicates.addPredicate(predicate);
		return this;
	}

	/**
	 * Evaluate whether or not the subquery in a FromSubquery is flattenable.
	 * Currently, a FSqry is flattenable if all of the following are true: o
	 * Subquery is a SelectNode. (ie, not a RowResultSetNode or a UnionNode) o
	 * It contains a single table in its FROM list. o It contains no subqueries
	 * in the SELECT list. o It does not contain a group by or having clause o
	 * It does not contain aggregates. o It is not a DISTINCT. o It does not
	 * have an ORDER BY clause (pushed from FromSubquery).
	 *
	 * @param fromList
	 *            The outer from list
	 *
	 * @return boolean Whether or not the FromSubquery is flattenable.
	 */
	@Override
	boolean flattenableInFromSubquery(FromList fromList) {
		if (isDistinct) {
			return false;
		}
		if (this.fromList.size() > 1) {
			return false;
		}

		/*
		 * Don't flatten (at least for now) if selectNode's SELECT list contains
		 * a subquery
		 */
		if ((selectSubquerys != null) && (selectSubquerys.size() > 0)) {
			return false;
		}

		/* Don't flatten if selectNode contains a group by or having clause */
		if ((groupByList != null) || (havingClause != null)) {
			return false;
		}

		/*
		 * Don't flatten if select list contains something that isn't cloneable.
		 */
		if (!getResultColumns().isCloneable()) {
			return false;
		}

		/* Don't flatten if selectNode contains an aggregate */
		if ((selectAggregates != null) && (selectAggregates.size() > 0)) {
			return false;
		}

		for (int i = 0; i < qec.size(); i++) {
			// Don't flatten if selectNode now has an order by or offset/fetch
			// clause
			if ((qec.getOrderByList(i) != null)
					&& (qec.getOrderByList(i).size() > 0)) {
				return false;
			}

			if ((qec.getOffset(i) != null) || (qec.getFetchFirst(i) != null)) {
				return false;
			}
		}

		return true;
	}

	 

	/**
	 * Is the result of this node an ordered result set. An ordered result set
	 * means that the results from this node will come in a known sorted order.
	 * This means that the data is ordered according to the order of the
	 * elements in the RCL. Today, the data is considered ordered if: o The RCL
	 * is composed entirely of CRs or ConstantNodes o The underlying tree is
	 * ordered on the CRs in the order in which they appear in the RCL, taking
	 * equality predicates into account. Future Enhancements: o The prefix will
	 * not be required to be in order. (We will need to reorder the RCL and
	 * generate a PRN with an RCL in the expected order.)
	 *
	 * @return boolean Whether or not this node returns an ordered result set.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private boolean isOrderedResult(ResultColumnList resultColumns,
			ResultSetNode newTopRSN, boolean permuteOrdering)
			throws StandardException {
		/*
		 * Not ordered if RCL contains anything other than a ColumnReference or
		 * a ConstantNode.
		 */
		int numCRs = 0;
		for (ResultColumn rc : resultColumns) {
			if (rc.getExpression() instanceof ColumnReference) {
				numCRs++;
			} else if (!(rc.getExpression() instanceof ConstantNode)) {
				return false;
			}
		}

		// Corner case, all constants
		if (numCRs == 0) {
			return true;
		}

		ColumnReference[] crs = new ColumnReference[numCRs];

		// Now populate the CR array and see if ordered
		int crsIndex = 0;
		for (ResultColumn rc : resultColumns) {
			if (rc.getExpression() instanceof ColumnReference) {
				crs[crsIndex++] = (ColumnReference) rc.getExpression();
			}
		}

		return newTopRSN.isOrderedOn(crs, permuteOrdering,
				(List<FromBaseTable>) null);
	}

	/**
	 * Ensure that the top of the RSN tree has a PredicateList.
	 *
	 * @param numTables
	 *            The number of tables in the query.
	 * @return ResultSetNode A RSN tree with a node which has a PredicateList on
	 *         top.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	ResultSetNode ensurePredicateList(int numTables) throws StandardException {
		return this;
	}

	 
	/**
	 * Get an optimizer to use for this SelectNode. Only get it once -
	 * subsequent calls return the same optimizer.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	private Optimizer getOptimizer(OptimizableList optList,
			OptimizablePredicateList predList, DataDictionary dataDictionary,
			RequiredRowOrdering requiredRowOrdering,
			OptimizerPlan overridingPlan) throws StandardException {
		if (getOptimizer() == null) {
			/* Get an optimizer. */
			OptimizerFactory optimizerFactory = getLanguageConnectionContext()
					.getOptimizerFactory();

			setOptimizer(new OptimizerImpl(optList, predList, dataDictionary,
					false, false, false, getCompilerContext().getNumTables(),
					(JoinStrategy[]) null, 0, requiredRowOrdering, 0,
					overridingPlan, getLanguageConnectionContext()));
		}

		getOptimizer().prepForNextRound();
		return getOptimizer();
	}
 

	/**
	 * Determine if this select is updatable or not, for a cursor.
	 */
	@Override
	boolean isUpdatableCursor(DataDictionary dd) throws StandardException {
		TableDescriptor targetTableDescriptor;

		if (isDistinct) {
			if (SanityManager.DEBUG)
				SanityManager.DEBUG("DumpUpdateCheck",
						"cursor select has distinct");
			return false;
		}

		if ((selectAggregates == null) || (selectAggregates.size() > 0)) {
			return false;
		}

		if (groupByList != null || havingClause != null) {
			return false;
		}

		if (SanityManager.DEBUG)
			SanityManager.ASSERT(fromList != null,
					"select must have from tables");
		if (fromList.size() != 1) {
			if (SanityManager.DEBUG)
				SanityManager.DEBUG("DumpUpdateCheck",
						"cursor select has more than one from table");
			return false;
		}

		targetTable = (FromTable) (fromList.elementAt(0));

		if (targetTable instanceof FromVTI) {

			return ((FromVTI) targetTable).isUpdatableCursor();
		}

		if (!(targetTable instanceof FromBaseTable)) {
			if (SanityManager.DEBUG)
				SanityManager.DEBUG("DumpUpdateCheck",
						"cursor select has non base table as target table");
			return false;
		}

		/*
		 * Get the TableDescriptor and verify that it is not for a view or a
		 * system table. NOTE: We need to use the base table name for the table.
		 * Simplest way to get it is from a FromBaseTable. We know that
		 * targetTable is a FromBaseTable because of check just above us. NOTE:
		 * We also need to use the base table's schema name; otherwise we will
		 * think it is the default schema Beetle 4417
		 */
		targetTableDescriptor = getTableDescriptor(
				((FromBaseTable) targetTable).getBaseTableName(),
				getSchemaDescriptor(((FromBaseTable) targetTable)
						.getTableNameField().getSchemaName()));
		if (targetTableDescriptor.getTableType() == TableDescriptor.SYSTEM_TABLE_TYPE) {
			if (SanityManager.DEBUG)
				SanityManager.DEBUG("DumpUpdateCheck",
						"cursor select is on system table");
			return false;
		}
		if (targetTableDescriptor.getTableType() == TableDescriptor.VIEW_TYPE) {
			if (SanityManager.DEBUG)
				SanityManager.DEBUG("DumpUpdateCheck",
						"cursor select is on view");
			return false;
		}
		if ((getSelectSubquerys() != null)
				&& (getSelectSubquerys().size() != 0)) {
			if (SanityManager.DEBUG)
				SanityManager.DEBUG("DumpUpdateCheck",
						"cursor select has subquery in SELECT list");
			return false;
		}

		if ((getWhereSubquerys() != null) && (getWhereSubquerys().size() != 0)) {
			if (SanityManager.DEBUG)
				SanityManager.DEBUG("DumpUpdateCheck",
						"cursor select has subquery in WHERE clause");
			return false;
		}

		return true;
	}

	/**
	 * Assumes that isCursorUpdatable has been called, and that it is only
	 * called for updatable cursors.
	 */
	@Override
	FromTable getCursorTargetTable() {
		if (SanityManager.DEBUG)
			SanityManager
					.ASSERT(targetTable != null,
							"must call isUpdatableCursor() first, and must be updatable");
		return targetTable;
	}

	/**
	 * Search to see if a query references the specifed table name.
	 *
	 * @param name
	 *            Table name (String) to search for.
	 * @param baseTable
	 *            Whether or not name is for a base table
	 *
	 * @return true if found, else false
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	boolean referencesTarget(String name, boolean baseTable)
			throws StandardException {
		if (fromList.referencesTarget(name, baseTable)
				|| (selectSubquerys != null && selectSubquerys
						.referencesTarget(name, baseTable))
				|| (whereSubquerys != null && whereSubquerys.referencesTarget(
						name, baseTable))) {
			return true;
		}
		return false;
	}

	/**
	 * Return whether or not this ResultSetNode contains a subquery with a
	 * reference to the specified target table.
	 * 
	 * @param name
	 *            The table name.
	 * @param baseTable
	 *            Whether or not table is a base table.
	 *
	 * @return boolean Whether or not a reference to the table was found.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	boolean subqueryReferencesTarget(String name, boolean baseTable)
			throws StandardException {
		if ((selectSubquerys != null && selectSubquerys.referencesTarget(name,
				baseTable))
				|| (whereSubquerys != null && whereSubquerys.referencesTarget(
						name, baseTable))) {
			return true;
		}
		return false;
	}

	/**
	 * Bind any untyped null nodes to the types in the given ResultColumnList.
	 *
	 * @param bindingRCL
	 *            The ResultColumnList with the types to bind to.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	@Override
	void bindUntypedNullsToResultColumns(ResultColumnList bindingRCL)
			throws StandardException {
		fromList.bindUntypedNullsToResultColumns(bindingRCL);
	}

	/**
	 * Decrement (query block) level (0-based) for all of the tables in this
	 * ResultSet tree. This is useful when flattening a subquery.
	 *
	 * @param decrement
	 *            The amount to decrement by.
	 */
	void decrementLevel(int decrement) {
		/* Decrement the level in the tables */
		fromList.decrementLevel(decrement);
		selectSubquerys.decrementLevel(decrement);
		whereSubquerys.decrementLevel(decrement);
		/*
		 * Decrement the level in any CRs in predicates that are interesting to
		 * transitive closure.
		 */
		wherePredicates.decrementLevel(fromList, decrement);
	}

	/**
	 * Determine whether or not this subquery, the SelectNode is in a subquery,
	 * can be flattened into the outer query block based on a uniqueness
	 * condition. A uniqueness condition exists when we can guarantee that at
	 * most 1 row will qualify in each table in the subquery. This is true if
	 * every table in the from list is (a base table and the set of columns from
	 * the table that are in equality comparisons with expressions that do not
	 * include a column from the same table is a superset of any unique index on
	 * the table) or an ExistsBaseTable.
	 *
	 * @param additionalEQ
	 *            Whether or not the column returned by this select, if it is a
	 *            ColumnReference, is in an equality comparison.
	 *
	 * @return Whether or not this subquery can be flattened based on a
	 *         uniqueness condition.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	boolean uniqueSubquery(boolean additionalEQ) throws StandardException {
		ColumnReference additionalCR = null;
		ResultColumn rc = getResultColumns().elementAt(0);

		/*
		 * Figure out if we have an additional ColumnReference in an equality
		 * comparison.
		 */
		if (additionalEQ && rc.getExpression() instanceof ColumnReference) {
			additionalCR = (ColumnReference) rc.getExpression();

			/*
			 * ColumnReference only interesting if it is not correlated.
			 */
			if (additionalCR.getCorrelated()) {
				additionalCR = null;
			}
		}

		return fromList.returnsAtMostSingleRow((additionalCR == null) ? null
				: getResultColumns(), whereClause, wherePredicates,
				getDataDictionary());
	}

	/**
	 * Get the lock mode for the target of an update statement (a delete or
	 * update). The update mode will always be row for CurrentOfNodes. It will
	 * be table if there is no where clause.
	 *
	 * @see org.apache.derby.iapi.store.access.TransactionController
	 *
	 * @return The lock mode
	 */
	@Override
	int updateTargetLockMode() {
		/* Do row locking if there is a restriction */
		return fromList.updateTargetLockMode();
	}

	/**
	 * Return whether or not this ResultSet tree is guaranteed to return at most
	 * 1 row based on heuristics. (A RowResultSetNode and a SELECT with a
	 * non-grouped aggregate will return at most 1 row.)
	 *
	 * @return Whether or not this ResultSet tree is guaranteed to return at
	 *         most 1 row based on heuristics.
	 */
	@Override
	boolean returnsAtMostOneRow() {
		return (groupByList == null && selectAggregates != null && !selectAggregates
				.isEmpty());
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
		if (fromList.referencesSessionSchema()
				|| (selectSubquerys != null && selectSubquerys
						.referencesSessionSchema())
				|| (whereSubquerys != null && whereSubquerys
						.referencesSessionSchema()))
			return true;

		return false;
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

		if (fromList != null) {
			fromList = (FromList) fromList.accept(v);
		}

		if (whereClause != null) {
			whereClause = (ValueNode) whereClause.accept(v);
		}

		if (wherePredicates != null) {
			wherePredicates = (PredicateList) wherePredicates.accept(v);
		}

		if (havingClause != null) {
			havingClause = (ValueNode) havingClause.accept(v);
		}

		// visiting these clauses was added as part of DERBY-6263. a better fix
		// might be to fix the
		// visitor rather than skip it.
		if (!(v instanceof HasCorrelatedCRsVisitor)) {
			if (selectSubquerys != null) {
				selectSubquerys = (SubqueryList) selectSubquerys.accept(v);
			}

			if (whereSubquerys != null) {
				whereSubquerys = (SubqueryList) whereSubquerys.accept(v);
			}

			if (groupByList != null) {
				groupByList = (GroupByList) groupByList.accept(v);
			}

			for (int i = 0; i < qec.size(); i++) {
				final OrderByList obl = qec.getOrderByList(i);

				if (obl != null) {
					qec.setOrderByList(i, (OrderByList) obl.accept(v));
				}

				final ValueNode offset = qec.getOffset(i);

				if (offset != null) {
					qec.setOffset(i, (ValueNode) offset.accept(v));
				}

				final ValueNode fetchFirst = qec.getFetchFirst(i);

				if (fetchFirst != null) {
					qec.setFetchFirst(i, (ValueNode) fetchFirst.accept(v));
				}
			}

			if (preJoinFL != null) {
				preJoinFL = (FromList) preJoinFL.accept(v);
			}

			if (windows != null) {
				windows = (WindowList) windows.accept(v);
			}
		}
	}

	/**
	 * @return true if there are aggregates in the select list.
	 */
	boolean hasAggregatesInSelectList() {
		return !selectAggregates.isEmpty();
	}

	/**
	 * Used by SubqueryNode to avoid flattening of a subquery if a window is
	 * defined on it. Note that any inline window definitions should have been
	 * collected from both the selectList and orderByList at the time this
	 * method is called, so the windows list is complete. This is true after
	 * preprocess is completed.
	 *
	 * @return true if this select node has any windows on it
	 */
	boolean hasWindows() {
		return windows != null;
	}

	static void checkNoWindowFunctions(QueryTreeNode clause, String clauseName)
			throws StandardException {

		// Clause cannot contain window functions except inside subqueries
		HasNodeVisitor visitor = new HasNodeVisitor(WindowFunctionNode.class,
				SubqueryNode.class);
		clause.accept(visitor);

		if (visitor.hasNode()) {
			throw StandardException.newException(
					SQLState.LANG_WINDOW_FUNCTION_CONTEXT_ERROR, clauseName);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * A no-op for SelectNode.
	 */
	@Override
	void replaceOrForbidDefaults(TableDescriptor ttd, ResultColumnList tcl,
			boolean allowDefaults) throws StandardException {
	}

	boolean hasOffsetFetchFirst() {
		return qec.hasOffsetFetchFirst();
	}

}
