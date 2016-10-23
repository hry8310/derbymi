package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.dearbaby.cache.CacheIndex;
import org.apache.dearbaby.cache.CacheTableConf;
import org.apache.dearbaby.cache.ResultCache;
import org.apache.dearbaby.config.InitConfig;
import org.apache.dearbaby.query.JoinType;
import org.apache.dearbaby.util.ByteUtil;
import org.apache.dearbaby.util.ColCompare;
import org.apache.dearbaby.util.DRConstant;

public class SinResultBuffer  extends AbstractSinResult  {
	protected int rowId = 0;
	protected int bufferId=0;
	protected boolean endOut = false;
	public ArrayList<RowsBuffer> results = new ArrayList<RowsBuffer>();
	

	protected ArrayList<String > head=null;
	protected int[] dataType=null;
	protected int rowSize=20;
	protected int rows=0;
	
	protected int drvRowId=0;
	protected int endSize=0;
	
	public IIndex hashIndex;
	protected boolean isBuild=false;
	
	protected boolean isPress=false;
	
	protected int hl=2;
	public SinResultBuffer(){
		 
		RowsBuffer rb=new RowsBuffer();
		results.add(rb);
		
	}
	public void drv(int rowid,int end){
		drvRowId=rowid;
		rows=end;
		rowId=rowid;
	}
	
	public int endSize(){
		return endSize;
	}
	
	private void compress(){
		if(isPress==true){
			return;
		}
		if(results.size()==0){
			return;
		}
		results.get(results.size()-1).compress();
		isPress=true;
	}
	
	public void addEnd(){
		compress();
	}
	public Map getCurrRow0 () {
		
		if (results.size() == 0) {
			return null;
		}
		HashMap m=new HashMap();
		RowsBuffer rb=null;
		for(int i=bufferId;i<results.size();i++){
			RowsBuffer _rb=results.get(i);
			if(rowId<=_rb.end){
				rb=results.get(i);
				break;
			}
		}
		byte[] b= rb.getRow(rowId);
		 
		for(int i=0;i<head.size();i++){
			m.put(head.get(i), getCol(b,i));
		}
	//	return results.get(rowId);
		return m;
	}
	
	public Object getCurrCol0 (String name) {
		
		if (results.size() == 0) {
			return null;
		}
		
		HashMap m=new HashMap();
		RowsBuffer rb=null;
		 
		for(int i=bufferId;i<results.size();i++){
			RowsBuffer _rb=results.get(i);
		//	System.out.println("end:::  "+_rb.end+"   i  "+i);
			if(rowId<=_rb.end){
				rb=results.get(i);
				break;
			}
		}
		byte[] b= rb.getRow(rowId);
		 
		for(int i=0;i<head.size();i++){
			 if(head.get(i).equals(name)){
				 return getCol(b,i);
			 }
		}
	 
		return null;
	}
	
	public Map getCurrRow () {
		if(isBuild==true){
			return getHsCurrRow();
		}
		return getCurrRow0();
	}
	
	public  Object getCurrCol (String name) {
		//System.out.println("ddddddddddddddddddddd "+isBuild);
		if(isBuild==true){
			return getHsCurrCol(name);
		}
		return getCurrCol0(name);
	}
	
	public Map nextRow() {
		rowId++;
/*
		if (rowId > results2.size() - 1) {
			endOut = true;
			rowId = results2.size() - 1;
		}
*/
		if (rowId > rows - 1) {
			endOut = true;
			rowId = rows - 1;
		}
		return getCurrRow();
	}
	
	public void nextTo() {
		rowId++;
		//System.out.println("rows : "+rows+"  , rowId : "+rowId);
		if (rowId > rows - 1) {
			endOut = true;
			rowId = rows - 1;
		}
		 ;
	}
	
	public void init() {
		rowId = drvRowId;
		endOut = false;
	}
	
	public boolean isEnd() {
		//return rowId >= results2.size() - 1;
		return rowId>=rows-1;
	}

	public boolean isEndOut() {
		return endOut;
	}
	
	public void add(Map m){
		if(m==null){
			return;
		}
		
		int needType=0;
		if(head==null){
			head=new ArrayList<String>();
			
			Iterator iter = m.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				String key = entry.getKey().toString();
				head.add(key);
			}
			needType=1;
		}
		Object obj[]=new Object[head.size()];
		if(dataType==null){
			dataType=new int[head.size()];
		}
		byte cos[]=new byte[head.size()*4+rowSize];
		
		for(int i=0;i<head.size();i++){
			obj[i]=m.get(head.get(i));
			cos=putCol(cos,m.get(head.get(i)),i);
			if(needType==1){
				dataType[i]=ColCompare.getColType(obj[i]);
			}
			
		}
		cos=compressCol(cos);
		addCol(cos);
		
		rows=rows+1;
		endSize++;
		 
	}
	
	private void buildHashIndex(String col){
		if(hashIndex!=null){
			return;
		}
		int ss=rows/5;
		if(ss==0){
			ss=1;
		}
		 
		hashIndex = new HashIndex(ss); 

		for(int i=0;i<results.size();i++){
			RowsBuffer rb=results.get(i);
			int j=0;
			try{
			
			for(int b=rb.begin;b<=rb.end;b++){
				//HashBufEle e=new HashBufEle();
				//e.bufIdx=i; 
				//e.rowId=b;
				long e=ByteUtil.compInt(i, b);
				
				Object key=getCol(rb.getRow(b),col);
				hashIndex.addKey(key, e);
			}
			}catch(Exception e){
				e.printStackTrace();
			}
			
		}
		isBuild=true;
	}
	
	private void buildSortIndex(String col,JoinType jt,int ct){
		if(hashIndex!=null){
			return;
		}
		 
		 
		hashIndex = new SortIndex(rows,ct); 

		for(int i=0;i<results.size();i++){
			RowsBuffer rb=results.get(i);
			int j=0;
			try{
			
			for(int b=rb.begin;b<=rb.end;b++){
			 
				long e=ByteUtil.compInt(i, b);
				
				Object key=getCol(rb.getRow(b),col);
				hashIndex.addKey(key, e);
			}
			}catch(Exception e){
				e.printStackTrace();
			}
			
		}
		hashIndex.buildEnd();
		isBuild=true;
	}
	
	private boolean cacheIndex(String col,JoinType jt){
		if(qm.cacheConf.getIndex(tableName)!=DRConstant.USEIDX){
			return false;
		}
		if(tableName==null){
			return false;
		}
		CacheTableConf ccf=ResultCache.findTable(tableName);
		if(ccf==null){
			return false;
		}
		CacheIndex idx=ccf.indexs.findIndex(col, jt.type);
		if(idx==null){
			return false;
		}
		
		hashIndex=idx.index.clone();
		isBuild=true;
		return true;
	}
	
	public void buildIndex(String col,JoinType jt,int ct){
		//System.out.println("ddddddddddddddd isBuild  "+isBuild);
		if(cacheIndex(col,jt)){
			return ;
		}
		System.out.println("begin-build-index");
		if(jt.type==jt.HASH){
			buildHashIndex(col);
		}else{
			buildSortIndex(col,jt,ct);
		}
		System.out.println("end-build-index");
		if(qm.cacheConf.getIndex(tableName)==DRConstant.USEIDX){
			CacheTableConf ccf=ResultCache.findTable(tableName);
			if(ccf==null){
				return  ;
			}
			ccf.indexs.addIndex(col, jt.type, hashIndex.clone());
		}
		//System.exit(0);
	}
	
	public Map getHsCurrRow () {
	//	HashBufEle ele=  (HashBufEle)hashIndex.getCurrRow();
		
	//	byte[] b=results.get(ele.bufIdx).getRow(ele.rowId);
		long ele=  (long)hashIndex.getCurrRow();
		byte[] b=results.get(ByteUtil.getIntHght(ele)).getRow(ByteUtil.getIntLow(ele));
		
		return getColMap(b);
	}
	
	public Object getHsCurrCol (String name) {
		 
		Map m=getHsCurrRow();
	//	System.out.println("alias   name  "+name+"   "+isBuild+" ,  obj : "+m); 
		 
		Object obj= m.get(name);
	//	System.out.println("obj   "+obj+"  name  "+name); 
		return obj;
	}
	
	
	
	public SinResultBuffer copy(){
		SinResultBuffer ret=new SinResultBuffer();
		ret.results=this.results; 
		ret.rowId=this.rowId;
		ret.endOut=this.endOut;
		ret.endSize=this.endSize;
		ret.rows=this.rows; 
		ret.isBuild=this.isBuild;
		ret.head=this.head;
		ret.dataType=this.dataType;
		if(this.hashIndex!=null){
			ret.hashIndex=this.hashIndex.clone();
		}
			
		return ret;
	}
	
	public int size(){
		//return results2.size();
		return endSize;
	}
	
	public boolean firstMatch(Object key){
		boolean r= hashIndex.nextMatch(key);
	//	System.out.println("dddkkkkkk  "+key+" r   "+r);
		return r;
	 
	}
	
	public void indexInit(){
		hashIndex.init();
	}
	
	public boolean firstMatch(){
		return hashIndex.nextMatch( );
	 
	}
	
	public boolean matchNext(){
		return hashIndex.nextMatch();
	}
	 
	public void setIndex(boolean idx){
		isBuild=idx;
	}
	public boolean getIndex(){
		return isBuild;
	}
	
	
	private byte[] putCol(byte[] buf,Object o ,int i){
		int pre=0;
		int hSize=head.size();
		if(i-1>=0){
			pre=ByteUtil.byte2intShort(buf,(i-1)*hl);
		}
		byte[] b=new byte[0]; 
		if(o!=null){
			b=o.toString().getBytes();
		}
		
		if(buf.length-(head.size()*hl+pre)<b.length){
			byte[ ] tmp;
			if(i==hSize-1){
				 tmp=new byte[head.size()*hl+pre+b.length];
				// tmp=new byte[buf.length+b.length];
			}else{
				 tmp=new byte[buf.length+b.length];
			}
			System.arraycopy(buf, 0,tmp , 0, buf.length);
			buf=tmp;
		}
		if(i==hSize-1){
			rowSize=pre+b.length;
		}
		//ByteUtil.intCopybyte(pre+b.length, buf, i*4);
		ByteUtil.shortCopybyte(pre+b.length, buf, i*hl);
		System.arraycopy( b,0,buf,head.size()*hl+pre,b.length);
		return buf;
	}
	
	protected byte[] compressCol(byte[] buf){
		if(buf.length-rowSize<InitConfig.row_arrow_remain_length){
			return buf ;
		}
		double f=((double)(buf.length-rowSize))/buf.length;
		if(f<InitConfig.row_arrow_remain_rotio){
			return buf ;
		}
		byte[] colTmp=new byte[head.size()*hl+rowSize];
		System.arraycopy( buf,0,colTmp,0, head.size()*hl+rowSize);
		return colTmp;
	}
	
	protected Object getCol(byte[] buf,int i){
		int pre=0;
		if(i-1>=0){
			pre=ByteUtil.byte2intShort(buf,(i-1)*hl);
		}
		int ts=ByteUtil.byte2intShort(buf,i*hl);
		byte[] b=new byte[ts-pre];
	//	System.out.println("pre  "+pre+"  pos :  "+(head.size()*4+pre) +",  buf : "+buf.length +"  leng "+(ts-pre));
		System.arraycopy(buf, head.size()*hl+pre, b, 0, ts-pre);
		Object obj=null;
		obj= ByteUtil.getCol(b,dataType[i]);
	 
		return obj;
			
	}
	
	public Object getCol(byte[] b,String name){
		for(int i=0;i<head.size();i++){
			 if(head.get(i).equals(name)){
				 return getCol(b,i);
			 }
		}
		return null;
	}
	
	protected int getResSize(){
		return results.size()-1;
	}
	public void addCol(byte[] row){
		RowsBuffer rb=results.get(getResSize());
	 
		if(rb.addRow(row, rows)==false){
			RowsBuffer rb2=new RowsBuffer();
			 rb2.addRow(row, rows);
			 results.add(rb2);
			
		};
		
	}
	Map m=new HashMap();
	protected Map getColMap(byte[] row){
		
		for(int i=0;i<head.size();i++){
			m.put(head.get(i), getCol(row,i));
		}
		return m;
	}
	
}
