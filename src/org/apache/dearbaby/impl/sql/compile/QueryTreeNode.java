/*

   Derby - Class org.apache.derby.impl.sql.compile.QueryTreeNode

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

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.apache.dearbaby.config.InitConfig;
import org.apache.dearbaby.data.ResultBuffer;
import org.apache.dearbaby.data.ResultBufferDisk;
import org.apache.dearbaby.query.FetchContext;
import org.apache.dearbaby.query.FilterRowValue;
import org.apache.dearbaby.query.JoinType;
import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.query.QueryResultManager;
import org.apache.dearbaby.query.QueryTaskCtrl;
import org.apache.dearbaby.query.SinQuery;
import org.apache.dearbaby.sj.ResultMap;
import org.apache.dearbaby.task.JoinTask;
import org.apache.dearbaby.task.QueryTask;
import org.apache.dearbaby.task.TaskPoolManager;
import org.apache.dearbaby.util.JoinTypeUtil;
import org.apache.derby.catalog.AliasInfo;
import org.apache.derby.catalog.TypeDescriptor;
import org.apache.derby.catalog.types.RowMultiSetImpl;
import org.apache.derby.catalog.types.SynonymAliasInfo;
import org.apache.derby.catalog.types.UserDefinedTypeIdImpl;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.ClassName;
import org.apache.derby.iapi.reference.SQLState; 
import org.apache.derby.iapi.services.compiler.MethodBuilder;
import org.apache.derby.iapi.services.context.ContextManager; 
import org.apache.derby.iapi.sql.StatementType;
import org.apache.derby.iapi.sql.StatementUtil;
import org.apache.derby.iapi.sql.compile.CompilerContext;
import org.apache.derby.iapi.sql.compile.OptTrace;
import org.apache.derby.iapi.sql.compile.OptimizerFactory;
import org.apache.derby.iapi.sql.compile.Parser;
import org.apache.derby.iapi.sql.compile.TypeCompiler;
import org.apache.derby.iapi.sql.compile.Visitable;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext; 
import org.apache.derby.iapi.sql.dictionary.AliasDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor; 
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.TypeId; 
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * QueryTreeNode is the root class for all query tree nodes. All query tree
 * nodes inherit from QueryTreeNode except for those that extend
 * QueryTreeNodeVector.
 *
 */

public abstract class QueryTreeNode implements Visitable {
	static final int AUTOINCREMENT_START_INDEX = 0;
	static final int AUTOINCREMENT_INC_INDEX = 1;
	static final int AUTOINCREMENT_IS_AUTOINCREMENT_INDEX = 2;
	// Parser uses this static field to make a note if the autoincrement column
	// is participating in create or alter table.
	static final int AUTOINCREMENT_CREATE_MODIFY = 3;
	
	static final int JOIN_LOOP=1;
	static final int JOIN_IDX=2;
	static final int JOIN_UN=3;

	private int beginOffset = -1; // offset into SQL input of the substring
	// which this query node encodes.
	private int endOffset = -1;

	private ContextManager cm;
	private LanguageConnectionContext lcc;
 

	private ArrayList<String> visitableTags;

	public QueryMananger qm;
	public QueryResultManager qs = new QueryResultManager();
	protected boolean isFilter =false;
	
	//for task 
	public QueryTaskCtrl taskCtrl=null;
	public ResultBuffer  resList;
	public int joins=JOIN_UN;

	public ArrayList<JoinType> ej=new ArrayList<JoinType> ();
	/*
	 * 如果为集合处理后，数据将会存放在这里
	 * */
	public FilterRowValue rowValue=new FilterRowValue();
	
	public FetchContext fetchCtx;
	
	public void setIsFilter(boolean is){
		isFilter=is;
	}
	
	public boolean getIsFilter( ){
		return isFilter;
	}
	
	/**
	 * In Derby SQL Standard Authorization, views, triggers and constraints
	 * execute with definer's privileges. Taking a specific eg of views user1
	 * create table t1 (c11 int); create view v1 as select * from user1.t1;
	 * grant select on v1 to user2; user2 select * from user1.v1; Running with
	 * definer's privileges mean that since user2 has select privileges on view
	 * v1 owned by user1, then that is sufficient for user2 to do a select from
	 * view v1. View v1 underneath might access some objects that user2 doesn't
	 * have privileges on, but that is not a problem since views execute with
	 * definer's privileges. In order to implement this behavior, when doing a
	 * select from view v1, we only want to check for select privilege on view
	 * v1. While processing the underlying query for view v1, we want to stop
	 * collecting the privilege requirements for the query underneath. Following
	 * flag, isPrivilegeCollectionRequired is used for this purpose. The flag
	 * will be true when we are the top level of view and then it is turned off
	 * while we process the query underlying the view v1.
	 */
	private boolean isPrivilegeCollectionRequired = true;

	QueryTreeNode(ContextManager cm) {
		this.cm = cm;

		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(cm != null, "cm not expected to be null");
		}
	}

	/**
	 * Get the current ContextManager.
	 *
	 * @return The current ContextManager.
	 */
	final ContextManager getContextManager() {
		if (SanityManager.DEBUG) {
			if (cm == null)
				SanityManager
						.THROWASSERT("Null context manager in QueryTreeNode of type :"
								+ this.getClass());
		}
		return cm;
	}

	/**
	 * Gets the NodeFactory for this database.
	 *
	 * @return the node factory for this database.
	 *
	 */
	public final OptimizerFactory getOptimizerFactory() {
		return getLanguageConnectionContext().getLanguageConnectionFactory()
				.getOptimizerFactory();
	}

	/** Convenience method for finding the optimizer tracer */
	public OptTrace getOptimizerTracer() {
		return getLanguageConnectionContext().getOptimizerTracer();
	}

	/** Convenience method for checking whether optimizer tracing is on */
	public boolean optimizerTracingIsOn() {
		return getLanguageConnectionContext().optimizerTracingIsOn();
	}

	public void genQuery(QueryMananger qs) {
		if (qm == null) {
			qm = qs;
		}
		genQuery0();
	}

	public void genQuery0() {

	}

	public void genCondition() {

	}
	
	public void clearCondition() {

	}

	public void exeQuery0() {

	}

	public void exeQuery() {
		QueryTaskCtrl qtc=new QueryTaskCtrl();
		int i=0;
		for (SinQuery q : qs.querys) {
			if(q.setTaskCtrl(qtc)){
				i++;
			}
			
		}
		qtc.setCount(i);
		for (SinQuery q : qs.querys) {
			q.genSql(qm);
			//q.exeSelect();
		}
		qtc.await();
		
		exeQuery0();
		exeFilter();
	}
	
	public void exeGroupBy(   ){
		
	}

	public boolean fetch() {
		
		if(isFilter==false){
			return fetch0();
		}else{
			return rowValue.next();
		}
	}

	final public void addCuRow() {
		qs.addFetch();
		qm.addFetch(qs);
	}

	public boolean fetch0() {
		 
		
		
		boolean r =false;
		//System.out.println("JOIN_UN  "+(joins==JOIN_UN));
		if(joins==JOIN_UN){
			 desi();
		}
		
		if(joins==JOIN_LOOP){
			r = qs.next();
			if (r == true) {
				//	addCuRow();
			}
		}
		
		if(joins==JOIN_IDX){
		
			r=qs.nextJoin();
		//	System.out.println("next-join "+r);
		}
		return r;
	}

	public ArrayList<JoinType>  desi00(){
		joins= JOIN_LOOP;
		ArrayList<JoinType> jss=new ArrayList<JoinType>();
		return null;
	}
	
	private boolean isCanFindByName(){
		if(qm.session.useDriverTable==null){
			return false;
		}
		return true;
	}
	 public ArrayList<JoinType>  desi0(){
		
		joins= JOIN_LOOP;
		for(SinQuery sq:qs.querys){
			if(sq.isOrCond==true){
				return null;
			}
		}
		
		int qsize= qs.querys.size();
		ArrayList<JoinType> js=qm.getJoins();
		
		for(int i=0;i<qsize-1;i++){
			for(int j=i+1;j<qsize;j++){
				JoinType jt=JoinTypeUtil.findJoin(js,qs.querys.get(i),qs.querys.get(j));
				if(jt!=null){
					joins=JOIN_IDX;
				
					JoinTypeUtil.addJoin(ej,jt);
				}
			}
		}
		if(ej.size()==0){
			return null;
		}
		int isMax=1;
		if(isCanFindByName()){
		//	isMax=desiMax(ej);
			ej=findMaxDriver(ej,qm.session.useDriverTable);
		}else{
			ej=findSingleq(ej,isMax);
		}
		ArrayList<JoinType> jss=new ArrayList<JoinType>();
		jss.addAll(ej);
		
		JoinType j=JoinTypeUtil.ans(jss);

		 fetchCtx=new FetchContext();
		 fetchCtx.jHeader=j;
		 ArrayList<JoinType> _js=qs.analyseJoin(j,fetchCtx); 
		 return _js;
	}
	
	 private int desiMax( ArrayList<JoinType> _js){
		 HashMap<String ,Integer> hs=new HashMap();
			for(JoinType j:_js){
				String left=j.left.getTableName();
				String right=j.right.getTableName();
				putTableCnt(left,hs);
				putTableCnt(right,hs);
			}
			int i=-1;
			String k="";
			Iterator<String >  it =hs.keySet().iterator();
			while(it.hasNext()){
				String key=it.next();
				if(hs.get(key).intValue()==1){
					int is=qm.findQuery(key).getDrvSize();
					if(is>=InitConfig.DISK_USE_MAX_SIZE){
						return 2;
					}
				}
			}
			return 1;
	 }
	 
	 //获取在缓存中最大的表作为驱动表
	 private  ArrayList<JoinType> findMaxDriver( ArrayList<JoinType> _js,String k){

			JoinType tj=null;
			
			for(JoinType j:_js){
				String left=j.left.getTableName();
				String right=j.right.getTableName();
				if(left.equalsIgnoreCase(k)||right.equalsIgnoreCase(k)){
					tj=j;
					break;
				}
			}
			if(tj==null){
				return findSingleq(_js,1);
			}
			tj.setToLeft(k);
			//设定qm的driverTable
			qm.driverTable=k;
			 ArrayList<JoinType> _jss= new  ArrayList<JoinType>();
			 _jss.add(tj);
			 _js.remove(tj);
			 _jss.addAll(_js);
			 return _jss;
	 }
	
	 //寻找记录集最小的sinquery并作为驱动表
	private  ArrayList<JoinType> findSingleq( ArrayList<JoinType> _js,int isMax){
		
		//System.out.println("xxnnnnnnnnnnxxxxxccc : "+isMax+" ,isMax"+_js.size());
		if(_js.size()==1){
			if(isMax!=1){
				_js.get(0).chgx();//
				
			}
			qm.driverTable=_js.get(0).left.getTableName();
			return _js;
		}
		HashMap<String ,Integer> hs=new HashMap();
		for(JoinType j:_js){
			String left=j.left.getTableName();
			String right=j.right.getTableName();
			putTableCnt(left,hs);
			putTableCnt(right,hs);
		}
		int i=-1;
		String k="";
		Iterator<String >  it =hs.keySet().iterator();
		while(it.hasNext()){
			String key=it.next();
			if(hs.get(key).intValue()==1){
				int is=qm.findQuery(key).getDrvSize();
				if(i==-1){
					i=is;
				}else{
					if(isMax==1){
						if(is<i){
							i=is;
							k=key;
						}
					}else{ 
						if(is>=i){ //获取大表作为驱动表，用于缓存
							i=is;
							k=key;
						}
					}
				}
			}
		}
		
		JoinType tj=null;
		
		for(JoinType j:_js){
			String left=j.left.getTableName();
			String right=j.right.getTableName();
			if(left.equalsIgnoreCase(k)||right.equalsIgnoreCase(k)){
				tj=j;
				break;
			}
		}
		if(tj==null){
			return _js;
		}
		tj.setToLeft(k);
		//设定qm的driverTable
		qm.driverTable=k;
		 ArrayList<JoinType> _jss= new  ArrayList<JoinType>();
		 _jss.add(tj);
		 _js.remove(tj);
		 _jss.addAll(_js);
		 return _jss;
	}
	
	private void putTableCnt(String key ,HashMap<String ,Integer>  h){
		if(h.containsKey(key)){
			h.put(key, h.get(key)+1);
		}else{
			h.put(key, 1);
		}
	}
	
	void desi(){
		ArrayList<JoinType> _js=desi0();
		if(_js==null){
			return;
		}
		qs.bindContext(fetchCtx); 
		qs.buildIndex(_js);
	}
	
	public void deciJoin(){
		if(joins==JOIN_UN){
			 desi();
		}
	}
	
	public void fetchInit() {	
			qs.init();
			rowValue.again();
	}

	

	public void initQuers() {
			qs.init();
			for(SinQuery sq:qs.querys){
				for (SinQuery q : qm.fetchRow) {
					if(q.alias.equalsIgnoreCase(sq.alias)){
						qm.fetchRow.remove(q);
						break;
					}
				}
			}
	}
	
	
	public void fetchEnd() {
		qm.initFetch();
	}
	public void fetchCxtEnd() {
		fetchCtx.drvQ.initJn();
		for (int i = fetchCtx.joinResult.size() - 1; i >= 0; i--){
			SinQuery sq = fetchCtx.joinResult.get(i);
			sq.indexInit();
		}
		fetchCtx.isJnMatch=false;
		fetchCtx.drvQFirst=true;
	}
	
	public void endSelect(){
		qm.endFetch();
	}

	public boolean match() {
		return true;
	}

	public void exeFilter(){
		exeFilter0();
	}
	
	public void exeFilter0(){
		
	}
	
	public SinQuery findSinQueryFromQs(String alias){
		 for(SinQuery s:qs.querys){
			 if(s.alias.equalsIgnoreCase(alias)){
				 return  s;
			 }
		 }
		 return null;
	}
	
	
	public Object getColVal(String alias,String colName){
		if(isFilter==true){
			return rowValue.getCurrVal(alias, colName);
		}else{
			SinQuery sq= qm.findFetchRow(alias);
			try{
				return sq.getCurrCol(colName);
			}catch(Exception e){
				System.out.println("ddddddddddddd : " + alias);
				SinQuery s=qm.findFetchRow(alias);
				s.getCurrCol(colName);
				return null;
			}
		}
	}
	
	/*
	 * 获取字段的值 
	 * */
	/*
	public Object getVal() {
		Object obj = null;
		try {
			if (this instanceof ResultColumn) {
				ResultColumn c = (ResultColumn) this;
				String alias = c.getTableName();
				String cName = c.getSourceColumnName();
				obj = getColVal(alias,cName);
			} else if (this instanceof ColumnReference) {
				ColumnReference c = (ColumnReference) this;
				String alias = c.getTableName();
				String cName = c._columnName;
				obj =  getColVal(alias,cName);
			} else if (this instanceof ConstantNode) {
				ConstantNode t = (ConstantNode) this;
				obj = t.getValue().getObject();
			} else if (this instanceof SelectNode) {
				SelectNode t = (SelectNode) this;
				List<ResultColumn> list = t.resultColumns.v;
				if (list.size() != 1) {
					return null;
				}
				ResultColumn c = list.get(0);
				c.qm = qm;
				if(isFilter==false){
					obj = c.getVal();
				}else{
					obj = rowValue.getCurrVal(c.getTableName(), c.getSourceColumnName());
				}
				return obj;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return obj;
	}
	*/
	
	public Object getVal() {
		
		return null;
	}
	
	 /*获取匹配行信息*/
    protected HashMap getMatchRow(){
    	LinkedHashMap map=new LinkedHashMap();
	
		return map;
    }
    
    /*获取匹配行信息*/
    protected HashMap getMatchRow(String alias){
    	LinkedHashMap map=new LinkedHashMap();
	
		return map;
    }
    
    protected HashMap getMatchOrderByRow(OrderByList orderByList ){
    	LinkedHashMap map=new LinkedHashMap();
    	
		return map;
    }
    
    public void syncGetMatchRows(){
    	JoinTask qtask=new JoinTask(this);
		TaskPoolManager.putTask(qtask);
    }
    
    public ResultBuffer getMatchRows(){
    	ResultBuffer list=new ResultBuffer();
    	//	ResultBuffer list=new ResultBufferDisk();
    	int i=0;
    	qs.bindContext(fetchCtx);
    	while ( fetch()) {
    		//System.out.println("fetch-ok-----  "+(i++));;
			if ( match()) {
			 	HashMap map= getMatchRow();
				//System.out.println("fetch-ok-----  "+(i++));;
			 	//ResultMap m=new ResultMap(map);
				
			 	list.add(map);
			}
			fetchEnd();
		}
    	endSelect();
    	return list;
    }
    
    public void initDrv(int begin,int end){
    //	System.out.println(qs.querys);
    	qs.initDrv(begin, end);
    }
    
    public int getDrvSize(){
    	return qs.getDrvSize();
    }
    
    public ResultBuffer getMatchOrderByRows(OrderByList orderByList){
    	ResultBuffer list=new ResultBuffer();
    	while ( fetch()) {
			 
			if ( match()) {
				HashMap map= getMatchOrderByRow(orderByList);
				
				//ResultMap m=new ResultMap(map);
				
				list.add(map);
			}
			fetchEnd();
		}
    	return list;
    }
    
    public QueryTreeNode copy(){
    	return null;
    }
    
    public List<QueryTreeNode> copys(int i){
    	List<QueryTreeNode>  ls=new ArrayList<QueryTreeNode>();
    	return ls;
    }
    
    
    public void copyQuerys(QueryTreeNode tag){
    		//if(tag.qm==null){
    		tag.qm=qm.copyOf();
    		tag.qs=qs.copyOf();
    		//}
    		for(SinQuery qs :qm.querys){
    			//处理drvQ
    			if(tag.qs.getDrvQ()!=null&&qs.alias.equalsIgnoreCase(tag.qs.getDrvQ().alias)){
    				tag.qm.querys.add(tag.qs.getDrvQ());
    				continue;
    			}
    			
    			SinQuery newOne=qs.clone();
    			tag.qm.querys.add(newOne);
    		 
    		}
    		 
    		System.out.println("querys-size  "+qs.querys.size());;
    		for(int i=0;i<qs.querys.size();i++){
    			for(int j=0;j<qm.querys.size();j++){
        			 if(qs.querys.get(i)==qm.querys.get(j)){
        				 tag.qs.querys.add( tag.qm.querys.get(j));
        				 break;
        			 }
        		}
    		}
    		
    		for(int i=0;i<qs.cxt.joinResult.size();i++){
    			for(int j=0;j<qm.querys.size();j++){
        			 if(qs.cxt.joinResult.get(i)==qm.querys.get(j)){
        				 tag.qs.cxt.joinResult.add( tag.qm.querys.get(j));
        				 break;
        			 }
        		}
    		}
    		
    }
    
    public void copyTO(	  QueryMananger _qm,QueryResultManager _qs ){
    	copyTO0(_qm,_qs);
    }
    
    public void copyTO0(	  QueryMananger _qm,QueryResultManager _qs ){
    	qm=_qm;
    	qs=_qs;
    }
    
	/**
	 * Gets the LanguageConnectionContext for this connection.
	 *
	 * @return the lcc for this connection
	 *
	 */
	protected final LanguageConnectionContext getLanguageConnectionContext() {
		if (lcc == null) {
			lcc = (LanguageConnectionContext) getContextManager().getContext(
					LanguageConnectionContext.CONTEXT_ID);
		}
		return lcc;
	}

	/**
	 * Gets the beginning offset of the SQL substring which this query node
	 * represents.
	 *
	 * @return The beginning offset of the SQL substring. -1 means unknown.
	 *
	 */
	public int getBeginOffset() {
		return beginOffset;
	}

	/**
	 * Sets the beginning offset of the SQL substring which this query node
	 * represents.
	 *
	 * @param beginOffset
	 *            The beginning offset of the SQL substring.
	 *
	 */
	public void setBeginOffset(int beginOffset) {
		this.beginOffset = beginOffset;
	}

	/**
	 * Gets the ending offset of the SQL substring which this query node
	 * represents.
	 *
	 * @return The ending offset of the SQL substring. -1 means unknown.
	 *
	 */
	public int getEndOffset() {
		return endOffset;
	}

	/**
	 * Sets the ending offset of the SQL substring which this query node
	 * represents.
	 *
	 * @param endOffset
	 *            The ending offset of the SQL substring.
	 *
	 */
	public void setEndOffset(int endOffset) {
		this.endOffset = endOffset;
	}

	/**
	 * Return header information for debug printing of this query tree node.
	 *
	 * @return Header information for debug printing of this query tree node.
	 */

	protected String nodeHeader() {
		if (SanityManager.DEBUG) {
			return "\n" + this.getClass().getName() + '@'
					+ Integer.toHexString(hashCode()) + "\n";
		} else {
			return "";
		}
	}

	/**
	 * Format a node that has been converted to a String for printing as part of
	 * a tree. This method indents the String to the given depth by inserting
	 * tabs at the beginning of the string, and also after every newline.
	 *
	 * @param nodeString
	 *            The node formatted as a String
	 * @param depth
	 *            The depth to indent the given node
	 *
	 * @return The node String reformatted with tab indentation
	 */

	static String formatNodeString(String nodeString, int depth) {
		if (SanityManager.DEBUG) {
			StringBuilder nodeStringBuilder = new StringBuilder(nodeString);
			int pos;
			char c;
			char[] indent = new char[depth];

			/*
			 * * Form an array of tab characters for indentation.
			 */
			while (depth > 0) {
				indent[depth - 1] = '\t';
				depth--;
			}

			/* Indent the beginning of the string */
			nodeStringBuilder.insert(0, indent);

			/*
			 * * Look for newline characters, except for the last character.* We
			 * don't want to indent after the last newline.
			 */
			for (pos = 0; pos < nodeStringBuilder.length() - 1; pos++) {
				c = nodeStringBuilder.charAt(pos);
				if (c == '\n') {
					/* Indent again after each newline */
					nodeStringBuilder.insert(pos + 1, indent);
				}
			}

			return nodeStringBuilder.toString();
		} else {
			return "";
		}
	}

	/**
	 * Print this tree for debugging purposes. This recurses through all the
	 * sub-nodes and prints them indented by their depth in the tree.
	 */

	public void treePrint() {
		if (SanityManager.DEBUG) {
			debugPrint(nodeHeader());
			String thisStr = formatNodeString(this.toString(), 0);

			if (containsInfo(thisStr) && !SanityManager.DEBUG_ON("DumpBrief")) {
				debugPrint(thisStr);
			}

			printSubNodes(0);
			debugFlush();
		}
	}

	/**
	 * Print call stack for debug purposes
	 */

	void stackPrint() {
		if (SanityManager.DEBUG) {
			debugPrint("Stacktrace:\n");
			Exception e = new Exception("dummy");
			StackTraceElement[] st = e.getStackTrace();
			for (int i = 0; i < st.length; i++) {
				debugPrint(st[i] + "\n");
			}

			debugFlush();
		}
	}

	/**
	 * Print this tree for debugging purposes. This recurses through all the
	 * sub-nodes and prints them indented by their depth in the tree, starting
	 * with the given indentation.
	 *
	 * @param depth
	 *            The depth of this node in the tree, thus, the amount to indent
	 *            it when printing it.
	 */

	void treePrint(int depth) {
		if (SanityManager.DEBUG) {
			Map<Object, Object> printed = getLanguageConnectionContext()
					.getPrintedObjectsMap();

			if (printed.containsKey(this)) {
				debugPrint(formatNodeString(nodeHeader(), depth));
				debugPrint(formatNodeString("***truncated***\n", depth));
			} else {
				printed.put(this, null);
				debugPrint(formatNodeString(nodeHeader(), depth));
				String thisStr = formatNodeString(this.toString(), depth);

				if (containsInfo(thisStr)
						&& !SanityManager.DEBUG_ON("DumpBrief")) {
					debugPrint(thisStr);
				}

				if (thisStr.charAt(thisStr.length() - 1) != '\n') {
					debugPrint("\n");
				}

				printSubNodes(depth);
			}

		}
	}

	private static boolean containsInfo(String str) {
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) != '\t' && str.charAt(i) != '\n') {
				return true;
			}
		}
		return false;
	}

	/**
	 * Print a String for debugging
	 *
	 * @param outputString
	 *            The String to print
	 */

	static void debugPrint(String outputString) {
		if (SanityManager.DEBUG) {
			SanityManager.GET_DEBUG_STREAM().print(outputString);
		}
	}

	/**
	 * Flush the debug stream out
	 */
	protected static void debugFlush() {
		if (SanityManager.DEBUG) {
			SanityManager.GET_DEBUG_STREAM().flush();
		}
	}

	/**
	 * Print the sub-nodes of this node.
	 *
	 * Each sub-class of QueryTreeNode is expected to provide its own
	 * printSubNodes() method. In each case, it calls super.printSubNodes(),
	 * passing along its depth, to get the sub-nodes of the super-class. Then it
	 * prints its own sub-nodes by calling treePrint() on each of its members
	 * that is a type of QueryTreeNode. In each case where it calls treePrint(),
	 * it should pass "depth + 1" to indicate that the sub-node should be
	 * indented one more level when printing. Also, it should call printLabel()
	 * to print the name of each sub-node before calling treePrint() on the
	 * sub-node, so that the reader of the printed tree can tell what the
	 * sub-node is.
	 *
	 * This printSubNodes() exists in here merely to act as a backstop. In other
	 * words, the calls to printSubNodes() move up the type hierarchy, and in
	 * this node the calls stop.
	 *
	 * I would have liked to put the call to super.printSubNodes() in this
	 * super-class, but Java resolves "super" statically, so it wouldn't get to
	 * the right super-class.
	 *
	 * @param depth
	 *            The depth to indent the sub-nodes
	 */

	void printSubNodes(int depth) {
	}

	/**
	 * Format this node as a string
	 *
	 * Each sub-class of QueryTreeNode should implement its own toString()
	 * method. In each case, toString() should format the class members that are
	 * not sub-types of QueryTreeNode (printSubNodes() takes care of following
	 * the references to sub-nodes, and toString() takes care of all members
	 * that are not sub-nodes). Newlines should be used liberally - one good way
	 * to do this is to have a newline at the end of each formatted member. It's
	 * also a good idea to put the name of each member in front of the formatted
	 * value. For example, the code might look like:
	 *
	 * "memberName: " + memberName + "\n" + ...
	 *
	 * Vector members containing subclasses of QueryTreeNode should subclass
	 * QueryTreeNodeVector. Such subclasses form a special case: These classes
	 * should not implement printSubNodes, since there is generic handling in
	 * QueryTreeNodeVector. They should only implement toString if they contain
	 * additional members.
	 *
	 * @return This node formatted as a String
	 */

	@Override
	public String toString() {
		return "";
	}

	/**
	 * Print the given label at the given indentation depth.
	 *
	 * @param depth
	 *            The depth of indentation to use when printing the label
	 * @param label
	 *            The String to print
	 */

	void printLabel(int depth, String label) {
		if (SanityManager.DEBUG) {
			debugPrint(formatNodeString(label, depth));
		}
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
	public boolean referencesSessionSchema() throws StandardException {
		return false;
	}

	/**
	 * Checks if the passed schema descriptor is for SESSION schema
	 *
	 * @return true if the passed schema descriptor is for SESSION schema
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	final boolean isSessionSchema(SchemaDescriptor sd) {
		return isSessionSchema(sd.getSchemaName());
	}

	/**
	 * Checks if the passed schema name is for SESSION schema
	 *
	 * @return true if the passed schema name is for SESSION schema
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	static boolean isSessionSchema(String schemaName) {
		return SchemaDescriptor.STD_DECLARED_GLOBAL_TEMPORARY_TABLES_SCHEMA_NAME
				.equals(schemaName);
	}

	/**
	 * Triggers, constraints and views get executed with their definers'
	 * privileges and they can exist in the system only if their definers still
	 * have all the privileges to create them. Based on this, any time a
	 * trigger/view/constraint is executing, we do not need to waste time in
	 * checking if the definer still has the right set of privileges. At compile
	 * time, we will make sure that we do not collect the privilege requirement
	 * for objects accessed with definer privileges by calling the following
	 * method.
	 */
	final void disablePrivilegeCollection() {
		isPrivilegeCollectionRequired = false;
	}

	/**
	 * Return true from this method means that we need to collect privilege
	 * requirement for this node. For following cases, this method will return
	 * true. 1)execute view - collect privilege to access view but do not
	 * collect privilege requirements for objects accessed by actual view uqery
	 * 2)execute select - collect privilege requirements for objects accessed by
	 * select statement 3)create view - collect privileges for select statement
	 * : the select statement for create view falls under 2) category above.
	 * 
	 * @return true if need to collect privilege requirement for this node
	 */
	boolean isPrivilegeCollectionRequired() throws StandardException {
		return isPrivilegeCollectionRequired
				&& getCompilerContext().passesPrivilegeFilters(this);
	}

	/**
	 * Do the code generation for this node. This is a place-holder method - it
	 * should be over-ridden in the sub-classes.
	 *
	 * @param acb
	 *            The ActivationClassBuilder for the class being built
	 * @param mb
	 *            The method for the generated code to go into
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */

	 

	/**
	 * Parameter info is stored in the compiler context. Hide this from the
	 * callers.
	 *
	 *
	 * @return null
	 *
	 * @exception StandardException
	 *                on error
	 */
	public DataTypeDescriptor[] getParameterTypes() throws StandardException {
		return ((CompilerContextImpl) getCompilerContext()).getParameterTypes();
	}

	 

	/**
	 * Get the DataDictionary
	 *
	 * @return The DataDictionary
	 *
	 */
	final public DataDictionary getDataDictionary() {
		return getLanguageConnectionContext().getDataDictionary();
	}
 
	/**
	 * Get the CompilerContext
	 *
	 * @return The CompilerContext
	 */
	protected final CompilerContext getCompilerContext() {
		return (CompilerContext) getContextManager().getContext(
				CompilerContext.CONTEXT_ID);
	}

	/**
	 * Get the TypeCompiler associated with the given TypeId
	 *
	 * @param typeId
	 *            The TypeId to get a TypeCompiler for
	 *
	 * @return The corresponding TypeCompiler
	 *
	 */
	protected final TypeCompiler getTypeCompiler(TypeId typeId) {
		// return DerTest.ctx.getTypeCompilerFactory().getTypeCompiler(typeId);
		return new BooleanTypeCompiler();
	}

	/**
	 * Accept a visitor, and call {@code v.visit()} on child nodes as necessary.
	 * Sub-classes should not override this method, but instead override the
	 * {@link #acceptChildren(Visitor)} method.
	 * 
	 * @param v
	 *            the visitor
	 *
	 * @exception StandardException
	 *                on error
	 */
	public final Visitable accept(Visitor v) throws StandardException {
		final boolean childrenFirst = v.visitChildrenFirst(this);
		final boolean skipChildren = v.skipChildren(this);
		// Pt.pr("accept");
		// Pt.o("accept-visitor: " + v.getClass());
		if (childrenFirst && !skipChildren && !v.stopTraversal()) {
			acceptChildren(v);
		}

		final Visitable ret = v.stopTraversal() ? this : v.visit(this);

		if (!childrenFirst && !skipChildren && !v.stopTraversal()) {
			acceptChildren(v);
		}

		return ret;
	}

	/**
	 * Accept a visitor on all child nodes. All sub-classes that add fields that
	 * should be visited, should override this method and call {@code accept(v)}
	 * on all visitable fields, as well as {@code super.acceptChildren(v)} to
	 * make sure all visitable fields defined by the super-class are accepted
	 * too.
	 *
	 * @param v
	 *            the visitor
	 * @throws StandardException
	 *             on errors raised by the visitor
	 */
	void acceptChildren(Visitor v) throws StandardException {
		// no children
	}

	public void addTag(String tag) {
		if (visitableTags == null) {
			visitableTags = new ArrayList<String>();
		}
		visitableTags.add(tag);
	}

	public boolean taggedWith(String tag) {
		if (visitableTags == null) {
			return false;
		} else {
			return visitableTags.contains(tag);
		}
	}

	/** Copy the tags from another QueryTreeNode */
	protected void copyTagsFrom(QueryTreeNode that) {
		if (that.visitableTags == null) {
			return;
		} else {
			for (String tag : that.visitableTags) {
				addTag(tag);
			}
		}
	}
	
	

	/**
	 * Get the int value of a Property
	 *
	 * @param value
	 *            Property value as a String
	 * @param key
	 *            Key value of property
	 *
	 * @return The int value of the property
	 *
	 * @exception StandardException
	 *                Thrown on failure
	 */
	protected int getIntProperty(String value, String key)
			throws StandardException {
		int intVal = -1;
		try {
			intVal = Integer.parseInt(value);
		} catch (NumberFormatException nfe) {
			throw StandardException.newException(
					SQLState.LANG_INVALID_NUMBER_FORMAT_FOR_OVERRIDE, value,
					key);
		}
		return intVal;
	}

	/**
	 * Get the long value of a Property
	 *
	 * @param value
	 *            Property value as a String
	 * @param key
	 *            Key value of property
	 *
	 * @return The long value of the property
	 *
	 * @exception StandardException
	 *                Thrown on failure
	 */
	protected long getLongProperty(String value, String key)
			throws StandardException {
		long longVal = -1;
		try {
			longVal = Long.parseLong(value);
		} catch (NumberFormatException nfe) {
			throw StandardException.newException(
					SQLState.LANG_INVALID_NUMBER_FORMAT_FOR_OVERRIDE, value,
					key);
		}
		return longVal;
	}

	/**
	 ** Parse the a SQL statement from the body of another SQL statement. Pushes
	 * and pops a separate CompilerContext to perform the compilation.
	 */
	StatementNode parseStatement(String sql, boolean internalSQL)
			throws StandardException {
		return (StatementNode) parseStatementOrSearchCondition(sql,
				internalSQL, true);
	}

	/**
	 * Parse an SQL fragment that represents a {@code <search condition>}.
	 *
	 * @param sql
	 *            a fragment of an SQL statement
	 * @param internalSQL
	 *            {@code true} if the SQL fragment is allowed to contain
	 *            internal syntax, {@code false} otherwise
	 * @return a {@code ValueNode} representing the parse tree of the SQL
	 *         fragment
	 * @throws StandardException
	 *             if an error happens while parsing
	 */
	ValueNode parseSearchCondition(String sql, boolean internalSQL)
			throws StandardException {
		return (ValueNode) parseStatementOrSearchCondition(sql, internalSQL,
				false);
	}

	/**
	 * Parse a full SQL statement or a fragment representing a {@code <search
	 * condition>}. This is a worker method that contains common logic for
	 * {@link #parseStatement} and {@link #parseSearchCondition}.
	 *
	 * @param sql
	 *            the SQL statement or fragment to parse
	 * @param internalSQL
	 *            {@code true} if it is allowed to contain internal syntax,
	 *            {@code false} otherwise
	 * @param isStatement
	 *            {@code true} if {@code sql} is a full SQL statement,
	 *            {@code false} if it is a fragment
	 * @return a parse tree
	 * @throws StandardException
	 *             if an error happens while parsing
	 */
	private Visitable parseStatementOrSearchCondition(String sql,
			boolean internalSQL, boolean isStatement) throws StandardException {
		/*
		 * * Get a new compiler context, so the parsing of the text* doesn't
		 * mess up anything in the current context
		 */
		LanguageConnectionContext lcc = getLanguageConnectionContext();
		CompilerContext newCC = lcc.pushCompilerContext();
		if (internalSQL)
			newCC.setReliability(CompilerContext.INTERNAL_SQL_LEGAL);

		try {
			Parser p = newCC.getParser();
			return isStatement ? p.parseStatement(sql) : p
					.parseSearchCondition(sql);
		}

		finally {
			lcc.popCompilerContext(newCC);
		}
	}

	/**
	 * Return the type of statement, something from StatementType.
	 *
	 * @return the type of statement
	 */
	protected int getStatementType() {
		return StatementType.UNKNOWN;
	}

	/**
	 * Get a ConstantNode to represent a typed null value.
	 *
	 * @param type
	 *            Type of the null node.
	 *
	 * @return A ConstantNode with the specified type, and a value of null
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	ConstantNode getNullNode(DataTypeDescriptor type) throws StandardException {
		switch (type.getTypeId().getJDBCTypeId()) {
		case Types.VARCHAR: {
			CharConstantNode ccn = new CharConstantNode(
					CharConstantNode.K_VARCHAR, type.getTypeId(), cm);
			ccn.setType(type.getNullabilityType(true));
			return ccn;
		}
		case Types.CHAR: {
			CharConstantNode ccn = new CharConstantNode(type.getTypeId(), cm);
			ccn.setType(type.getNullabilityType(true));
			return ccn;
		}
		case Types.TINYINT:
		case Types.SMALLINT:
		case Types.INTEGER:
		case Types.BIGINT:
		case Types.REAL:
		case Types.DOUBLE:
		case Types.DECIMAL: {
			NumericConstantNode nvn = new NumericConstantNode(type.getTypeId(),
					cm);
			nvn.setType(type.getNullabilityType(true)); // SUPERFLUOUS? FIXME
			return nvn;
		}
		case Types.NUMERIC: {
			// Map this to DECIMAL
			NumericConstantNode ncn = new NumericConstantNode(
					TypeId.getBuiltInTypeId(Types.DECIMAL), cm);
			ncn.setType(type.getNullabilityType(true)); // SUPERFLUOUS? FIXME
			return ncn;
		}
		case Types.DATE:
		case Types.TIME:
		case Types.TIMESTAMP: {
			UserTypeConstantNode utcn = new UserTypeConstantNode(
					type.getTypeId(), cm);
			utcn.setType(type.getNullabilityType(true));
			return utcn;
		}
		case Types.BINARY: {
			BitConstantNode bcn = new BitConstantNode(type.getTypeId(), cm);
			bcn.setType(type.getNullabilityType(true));
			return bcn;
		}
		case Types.VARBINARY: {
			VarbitConstantNode vcn = new VarbitConstantNode(type.getTypeId(),
					cm);
			vcn.setType(type.getNullabilityType(true));
			return vcn;
		}
		case Types.LONGVARCHAR: {
			CharConstantNode ccn = new CharConstantNode(
					CharConstantNode.K_LONGVARCHAR, type.getTypeId(), cm);
			ccn.setType(type.getNullabilityType(true));
			return ccn;
		}
		case Types.CLOB: {
			CharConstantNode ccn = new CharConstantNode(
					CharConstantNode.K_CLOB, type.getTypeId(), cm);
			ccn.setType(type.getNullabilityType(true));
			return ccn;
		}
		case Types.LONGVARBINARY: {
			VarbitConstantNode vcn = new VarbitConstantNode(type.getTypeId(),
					cm);
			vcn.setType(type.getNullabilityType(true));
			return vcn;
		}
		case Types.BLOB: {
			VarbitConstantNode vcn = new VarbitConstantNode(type.getTypeId(),
					cm);
			vcn.setType(type.getNullabilityType(true));
			return vcn;
		}

		case Types.SQLXML: {
			XMLConstantNode xcn = new XMLConstantNode(type.getTypeId(), cm);
			xcn.setType(type.getNullabilityType(true));
			return xcn;
		}
		case Types.BOOLEAN: {
			BooleanConstantNode bCn = new BooleanConstantNode(type.getTypeId(),
					cm);
			bCn.setType(type.getNullabilityType(true));
			return bCn;
		}
		default:
			if (type.getTypeId().userType()) {
				UserTypeConstantNode utcn = new UserTypeConstantNode(
						type.getTypeId(), cm);
				utcn.setType(type.getNullabilityType(true));
				return utcn;
			} else {
				if (SanityManager.DEBUG)
					SanityManager.THROWASSERT("Unknown type "
							+ type.getTypeId().getSQLTypeName()
							+ " in getNullNode");
				return null;
			}
		}
	}

	/**
	 * Translate a Default node into a default value, given a type descriptor.
	 *
	 * @param typeDescriptor
	 *            A description of the required data type.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	DataValueDescriptor convertDefaultNode(DataTypeDescriptor typeDescriptor)
			throws StandardException {
		/*
		 * * Override in cases where node type* can be converted to default
		 * value.
		 */
		return null;
	}

	public TableName makeTableName(String schemaName, String flatName)
			throws StandardException {
		return makeTableName(getContextManager(), schemaName, flatName);
	}

	public static TableName makeTableName(ContextManager contextManager,
			String schemaName, String flatName) throws StandardException {
		return new TableName(schemaName, flatName, contextManager);
	}

	public boolean isAtomic() throws StandardException {
		if (SanityManager.DEBUG) {
			SanityManager
					.THROWASSERT("isAtomic should not be called for this  class: "
							+ getClass().getName());
		}

		return false;
	}

	/**
	 * Get the descriptor for the named table within the given schema. If the
	 * schema parameter is NULL, it looks for the table in the current (default)
	 * schema. Table descriptors include object ids, object types (table, view,
	 * etc.) If the schema is SESSION, then before looking into the data
	 * dictionary for persistent tables, it first looks into LCC for temporary
	 * tables. If no temporary table tableName found for the SESSION schema,
	 * then it goes and looks through the data dictionary for persistent table
	 * We added getTableDescriptor here so that we can look for non data
	 * dictionary tables(ie temp tables) here. Any calls to getTableDescriptor
	 * in data dictionary should be only for persistent tables
	 *
	 * @param tableName
	 *            The name of the table to get the descriptor for
	 * @param schema
	 *            The descriptor for the schema the table lives in. If null, use
	 *            the current (default) schema.
	 *
	 * @return The descriptor for the table, null if table does not exist.
	 *
	 * @exception StandardException
	 *                Thrown on failure
	 */
	protected final TableDescriptor getTableDescriptor(String tableName,
			SchemaDescriptor schema) throws StandardException {
		TableDescriptor retval;

		// Following if means we are dealing with SESSION schema.
		if (isSessionSchema(schema)) {
			// First we need to look in the list of temporary tables to see if
			// this table is a temporary table.
			retval = getLanguageConnectionContext()
					.getTableDescriptorForDeclaredGlobalTempTable(tableName);
			if (retval != null)
				return retval; // this is a temporary table
		}

		// Following if means we are dealing with SESSION schema and we are
		// dealing with in-memory schema (ie there is no physical SESSION
		// schema)
		// If following if is true, it means SESSION.table is not a declared
		// table & it can't be physical SESSION.table
		// because there is no physical SESSION schema
		if (schema.getUUID() == null)
			return null;
 
		return null;
	}

	/**
	 * Get the descriptor for the named schema. If the schemaName parameter is
	 * NULL, it gets the descriptor for the current compilation schema.
	 * 
	 * QueryTreeNodes must obtain schemas using this method or the two argument
	 * version of it. This is to ensure that the correct default compliation
	 * schema is returned and to allow determination of if the statement being
	 * compiled depends on the current schema.
	 * 
	 * Schema descriptors include authorization ids and schema ids. SQL92 allows
	 * a schema to specify a default character set - we will not support this.
	 * Will check default schema for a match before scanning a system table.
	 * 
	 * @param schemaName
	 *            The name of the schema we're interested in. If the name is
	 *            NULL, get the descriptor for the current compilation schema.
	 *
	 * @return The descriptor for the schema.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	final SchemaDescriptor getSchemaDescriptor(String schemaName)
			throws StandardException {
		// return getSchemaDescriptor(schemaName, schemaName != null);
		return getSchemaDescriptor(schemaName, true);
	}

	/**
	 * Get the descriptor for the named schema. If the schemaName parameter is
	 * NULL, it gets the descriptor for the current compilation schema.
	 * 
	 * QueryTreeNodes must obtain schemas using this method or the single
	 * argument version of it. This is to ensure that the correct default
	 * compliation schema is returned and to allow determination of if the
	 * statement being compiled depends on the current schema.
	 * 
	 * @param schemaName
	 *            The name of the schema we're interested in. If the name is
	 *            NULL, get the descriptor for the current compilation schema.
	 * @param raiseError
	 *            True to raise an error if the schema does not exist, false to
	 *            return null if the schema does not exist.
	 * @return Valid SchemaDescriptor or null if raiseError is false and the
	 *         schema does not exist.
	 * @throws StandardException
	 *             Schema does not exist and raiseError is true.
	 */
	final SchemaDescriptor getSchemaDescriptor(String schemaName,
			boolean raiseError) throws StandardException {
		return StatementUtil.getSchemaDescriptor(schemaName, raiseError,
				getDataDictionary(), getLanguageConnectionContext(),
				getCompilerContext());
	}

	/**
	 * Resolve table/view reference to a synonym. May have to follow a synonym
	 * chain.
	 *
	 * @param tabName
	 *            to match for a synonym
	 *
	 * @return Synonym TableName if a match is found, NULL otherwise.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	TableName resolveTableToSynonym(TableName tabName) throws StandardException {
		DataDictionary dd = getDataDictionary();
		String nextSynonymTable = tabName.getTableName();
		String nextSynonymSchema = tabName.getSchemaName();
		boolean found = false;
		CompilerContext cc = getCompilerContext();

		// Circular synonym references should have been detected at the DDL
		// time, so
		// the following loop shouldn't loop forever.
		for (;;) {
			SchemaDescriptor nextSD = getSchemaDescriptor(nextSynonymSchema,
					false);
			if (nextSD == null || nextSD.getUUID() == null)
				break;

			AliasDescriptor nextAD = dd.getAliasDescriptor(nextSD.getUUID()
					.toString(), nextSynonymTable,
					AliasInfo.ALIAS_NAME_SPACE_SYNONYM_AS_CHAR);
			if (nextAD == null)
				break;

		 

			found = true;
			SynonymAliasInfo info = ((SynonymAliasInfo) nextAD.getAliasInfo());
			nextSynonymTable = info.getSynonymTable();
			nextSynonymSchema = info.getSynonymSchema();
		}

		if (!found)
			return null;

		TableName tableName = new TableName(nextSynonymSchema,
				nextSynonymTable, getContextManager());

		return tableName;
	}

	/**
	 * Verify that a java class exists, is accessible (public) and not a class
	 * representing a primitive type.
	 * 
	 * @param javaClassName
	 *            The name of the java class to resolve.
	 *
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	void verifyClassExist(String javaClassName) throws StandardException { }

	/**
	 * set the Information gathered from the parent table that is required to
	 * perform a referential action on dependent table.
	 */
	void setRefActionInfo(long fkIndexConglomId, int[] fkColArray,
			String parentResultSetId, boolean dependentScan) {
		if (SanityManager.DEBUG) {
			SanityManager
					.THROWASSERT("setRefActionInfo() not expected to be called for "
							+ getClass().getName());
		}
	}

	/**
	 * Add an authorization check into the passed in method.
	 */
	void generateAuthorizeCheck(ActivationClassBuilder acb, MethodBuilder mb,
			int sqlOperation) { }

	/**
	 * Bind time logic. Raises an error if this ValueNode, once compiled,
	 * returns unstable results AND if we're in a context where unstable results
	 * are forbidden.
	 *
	 * Called by children who may NOT appear in the WHERE subclauses of ADD
	 * TABLE clauses.
	 *
	 * @param fragmentType
	 *            Type of fragment as a String, for inclusion in error messages.
	 * @param fragmentBitMask
	 *            Type of fragment as a bitmask of possible fragment types
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public void checkReliability(String fragmentType, int fragmentBitMask)
			throws StandardException {
		// if we're in a context that forbids unreliable fragments, raise an
		// error
		if ((getCompilerContext().getReliability() & fragmentBitMask) != 0) {
			throwReliabilityException(fragmentType, fragmentBitMask);
		}
	}

	/**
	 * Bind time logic. Raises an error if this ValueNode, once compiled,
	 * returns unstable results AND if we're in a context where unstable results
	 * are forbidden.
	 *
	 * Called by children who may NOT appear in the WHERE subclauses of ADD
	 * TABLE clauses.
	 *
	 * @param fragmentBitMask
	 *            Type of fragment as a bitmask of possible fragment types
	 * @param fragmentType
	 *            Type of fragment as a String, to be fetch for the error
	 *            message.
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public void checkReliability(int fragmentBitMask, String fragmentType)
			throws StandardException { }

	/**
	 * Bind a UDT. This involves looking it up in the DataDictionary and filling
	 * in its class name.
	 *
	 * @param originalDTD
	 *            A datatype: might be an unbound UDT and might not be
	 *
	 * @return The bound UDT if originalDTD was an unbound UDT; otherwise
	 *         returns originalDTD.
	 */
	public DataTypeDescriptor bindUserType(DataTypeDescriptor originalDTD)
			throws StandardException {
		// if the type is a table type, then we need to bind its user-typed
		// columns
		if (originalDTD.getCatalogType().isRowMultiSet()) {
			return bindRowMultiSet(originalDTD);
		}

		// nothing to do if this is not a user defined type
		if (!originalDTD.getTypeId().userType()) {
			return originalDTD;
		}

		UserDefinedTypeIdImpl userTypeID = (UserDefinedTypeIdImpl) originalDTD
				.getTypeId().getBaseTypeId();

		// also nothing to do if the type has already been resolved
		if (userTypeID.isBound()) {
			return originalDTD;
		}

		// ok, we have an unbound UDT. lookup this type in the data dictionary

		DataDictionary dd = getDataDictionary();
		SchemaDescriptor typeSchema = getSchemaDescriptor(userTypeID
				.getSchemaName());
		char udtNameSpace = AliasInfo.ALIAS_NAME_SPACE_UDT_AS_CHAR;
		String unqualifiedTypeName = userTypeID.getUnqualifiedName();
		AliasDescriptor ad = dd.getAliasDescriptor(typeSchema.getUUID()
				.toString(), unqualifiedTypeName, udtNameSpace);

		if (ad == null) {
			throw StandardException.newException(
					SQLState.LANG_OBJECT_NOT_FOUND,
					AliasDescriptor.getAliasType(udtNameSpace),
					unqualifiedTypeName);
		}

		createTypeDependency(ad);

		DataTypeDescriptor result = new DataTypeDescriptor(
				TypeId.getUserDefinedTypeId(typeSchema.getSchemaName(),
						unqualifiedTypeName, ad.getJavaClassName()),
				originalDTD.isNullable());

		return result;
	}

	/** Bind user defined types as necessary */
	public TypeDescriptor bindUserCatalogType(TypeDescriptor td)
			throws StandardException {
		// if this is a user defined type, resolve the Java class name
		if (!td.isUserDefinedType()) {
			return td;
		} else {
			DataTypeDescriptor dtd = DataTypeDescriptor.getType(td);

			dtd = bindUserType(dtd);
			return dtd.getCatalogType();
		}
	}

	/** Get the AliasDescriptor of a UDT */
	public AliasDescriptor getUDTDesc(DataTypeDescriptor dtd)
			throws StandardException {
		UserDefinedTypeIdImpl userTypeID = (UserDefinedTypeIdImpl) dtd
				.getTypeId().getBaseTypeId();

		DataDictionary dd = getDataDictionary();
		SchemaDescriptor typeSchema = getSchemaDescriptor(userTypeID
				.getSchemaName());
		char udtNameSpace = AliasInfo.ALIAS_NAME_SPACE_UDT_AS_CHAR;
		String unqualifiedTypeName = userTypeID.getUnqualifiedName();
		AliasDescriptor ad = dd.getAliasDescriptor(typeSchema.getUUID()
				.toString(), unqualifiedTypeName, udtNameSpace);

		return ad;
	}

	/**
	 * Add USAGE privilege for all UDTs mentioned in the indicated ValueNodes.
	 */
	void addUDTUsagePriv(List<ValueNode> valueNodes) throws StandardException {
		if (!isPrivilegeCollectionRequired()) {
			return;
		}

		for (ValueNode val : valueNodes) {
			addUDTUsagePriv(val);
		}
	}

	/**
	 * Add USAGE privilege for a single UDT.
	 */
	void addUDTUsagePriv(ValueNode val) throws StandardException {
		if (!isPrivilegeCollectionRequired()) {
			return;
		}

		DataTypeDescriptor dtd = val.getTypeServices();
		if ((dtd != null) && dtd.getTypeId().userType()) {
			AliasDescriptor ad = getUDTDesc(dtd);
			getCompilerContext().addRequiredUsagePriv(ad);
		}
	}

	/**
	 * Bind the UDTs in a table type.
	 *
	 * @param originalDTD
	 *            A datatype: might be an unbound UDT and might not be
	 *
	 * @return The bound table type if originalDTD was an unbound table type;
	 *         otherwise returns originalDTD.
	 */
	public DataTypeDescriptor bindRowMultiSet(DataTypeDescriptor originalDTD)
			throws StandardException {
		if (!originalDTD.getCatalogType().isRowMultiSet()) {
			return originalDTD;
		}

		RowMultiSetImpl originalMultiSet = (RowMultiSetImpl) originalDTD
				.getTypeId().getBaseTypeId();
		TypeDescriptor[] columnTypes = originalMultiSet.getTypes();
		int columnCount = columnTypes.length;

		for (int i = 0; i < columnCount; i++) {
			columnTypes[i] = bindUserCatalogType(columnTypes[i]);
		}
		originalMultiSet.setTypes(columnTypes);

		return originalDTD;
	}

	/**
	 * Declare a dependency on a type and check that you have privilege to use
	 * it. This is only used if the type is an ANSI UDT.
	 *
	 * @param dtd
	 *            Type which may have a dependency declared on it.
	 */
	public void createTypeDependency(DataTypeDescriptor dtd)
			throws StandardException {
		 

	 
	}

	/**
	 * Declare a dependency on an ANSI UDT, identified by its AliasDescriptor,
	 * and check that you have privilege to use it.
	 */
	private void createTypeDependency(AliasDescriptor ad)
			throws StandardException { }

	/**
	 * Common code for the 2 checkReliability functions. Always throws
	 * StandardException.
	 *
	 * @param fragmentType
	 *            Type of fragment as a string, for inclusion in error messages.
	 * @param fragmentBitMask
	 *            Describes the kinds of expressions we ar suspicious of
	 * @exception StandardException
	 *                Throws an error, always.
	 */
	private void throwReliabilityException(String fragmentType,
			int fragmentBitMask) throws StandardException {
		final int reliability = getCompilerContext().getReliability();
		String sqlState;
		/*
		 * Error string somewhat dependent on operation due to different nodes
		 * being allowed for different operations.
		 */
		if (reliability == CompilerContext.DEFAULT_RESTRICTION) {
			sqlState = SQLState.LANG_INVALID_DEFAULT_DEFINITION;
		} else if (reliability == CompilerContext.GENERATION_CLAUSE_RESTRICTION) {
			switch (fragmentBitMask) {
			case CompilerContext.SQL_IN_ROUTINES_ILLEGAL:
				sqlState = SQLState.LANG_ROUTINE_CANT_PERMIT_SQL;
				break;

			default:
				sqlState = SQLState.LANG_NON_DETERMINISTIC_GENERATION_CLAUSE;
				break;
			}
		} else if ((reliability & fragmentBitMask & CompilerContext.SQL_IN_ROUTINES_ILLEGAL) != 0) {
			sqlState = SQLState.LANG_ROUTINE_CANT_PERMIT_SQL;
		} else if (reliability == CompilerContext.CHECK_CONSTRAINT) {
			sqlState = SQLState.LANG_UNRELIABLE_CHECK_CONSTRAINT;
		} else {
			sqlState = SQLState.LANG_UNRELIABLE_QUERY_FRAGMENT;
		}
		throw StandardException.newException(sqlState, fragmentType);
	}

	/**
	 * OR in more reliability bits and return the old reliability value.
	 */
	public int orReliability(int newBits) {
		CompilerContext cc = getCompilerContext();

		int previousReliability = cc.getReliability();

		cc.setReliability(previousReliability | newBits);

		return previousReliability;
	}

	/**
	 * Bind the parameters of OFFSET n ROWS and FETCH FIRST n ROWS ONLY, if any.
	 *
	 * @param offset
	 *            the OFFSET parameter, if any
	 * @param fetchFirst
	 *            the FETCH parameter, if any
	 *
	 * @exception StandardException
	 *                Thrown on error
	 */
	public static void bindOffsetFetch(ValueNode offset, ValueNode fetchFirst)
			throws StandardException {

		if (offset instanceof ConstantNode) {
			DataValueDescriptor dvd = ((ConstantNode) offset).getValue();
			long val = dvd.getLong();

			if (val < 0) {
				throw StandardException.newException(
						SQLState.LANG_INVALID_ROW_COUNT_OFFSET,
						Long.toString(val));
			}
		} else if (offset instanceof ParameterNode) {
			offset.setType(new DataTypeDescriptor(TypeId
					.getBuiltInTypeId(Types.BIGINT), false /*
															 * ignored tho; ends
															 * up nullable, so
															 * we test for NULL
															 * at execute time
															 */));
		}

		if (fetchFirst instanceof ConstantNode) {
			DataValueDescriptor dvd = ((ConstantNode) fetchFirst).getValue();
			long val = dvd.getLong();

			if (val < 1) {
				throw StandardException.newException(
						SQLState.LANG_INVALID_ROW_COUNT_FIRST,
						Long.toString(val));
			}
		} else if (fetchFirst instanceof ParameterNode) {
			fetchFirst.setType(new DataTypeDescriptor(TypeId
					.getBuiltInTypeId(Types.BIGINT), false /*
															 * ignored tho; ends
															 * up nullable, so
															 * we test for NULL
															 * at execute time
															 */));
		}
	}

	/**
	 * Get all child nodes of a specific type, and return them in the order in
	 * which they appear in the SQL text.
	 *
	 * @param <N>
	 *            the type of node to look for
	 * @param type
	 *            the type of node to look for
	 * @return all nodes of the specified type
	 * @throws StandardException
	 *             if an error occurs
	 */
	public <N extends QueryTreeNode> SortedSet<N> getOffsetOrderedNodes(
			Class<N> type) throws StandardException {
		OffsetOrderVisitor<N> visitor = new OffsetOrderVisitor<N>(type,
				getBeginOffset(), getEndOffset() + 1);
		accept(visitor);
		return visitor.getNodes();
	}

}
