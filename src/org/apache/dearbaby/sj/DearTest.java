package org.apache.dearbaby.sj;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.cache.CacheExecuteImp;
import org.apache.dearbaby.cache.CacheTableConf;
import org.apache.dearbaby.cache.ExcCacheConf;
import org.apache.dearbaby.cache.JustCacheTableConf;
import org.apache.dearbaby.cache.ResultCache;
import org.apache.dearbaby.cache.SimpCacheTableConf;
import org.apache.dearbaby.cache.UserCacheConf;
import org.apache.dearbaby.config.InitConfig;
import org.apache.dearbaby.data.ResultBuffer;
import org.apache.dearbaby.query.JdbcExecutor;
import org.apache.dearbaby.query.QuerySession;
import org.apache.dearbaby.util.DRConstant;

public class DearTest {

	public static int ix=0;
	 
/*
	String sql = "";
	 
	sql = "select a.name,a.iss from news a ,comany c where a.id in(select b.iid from cust b) and a.ikey=c.ikey";
	sql = "select a.workid as wid,(select b.kid from cust b where b.id=a.id ) as kid FROM worker  a ";
	sql = "select a.workid,a.docName from worker a ,good c where a.docId=c.docId  ";
	sql = "select a.workid,c.docName from worker a left join good c on a.docId=c.docId   ";
	sql = "SELECT a.workid FROM worker  a , OrderListParameter e WHERE a.id IN(SELECT b.id FROM good b WHERE a.docId=b.docId AND b.DeptId IN(SELECT c.DeptId FROM DeptInforParameter c WHERE c.DeptName=a.DeptName)) or e.WorkId=a.WorkId ";
*/
	public static void run3() {
		String sql="";
		sql = "SELECT  a.docName FROM good a WHERE a.docId IN (SELECT c.docId FROM worker c)";
		sql="SELECT a.docName  FROM worker  a , good b WHERE  a.docId=b.docId  OR a.Id>1380";
		sql="SELECT a.docName  FROM worker  a , good b WHERE  a.docName=b.docId  OR a.Id>4380";
		sql="SELECT a.docName  FROM worker  a   WHERE  a.docId in (select b.docId from good b)  ";
		sql="SELECT a.docId, sum(a.flag)    FROM worker  a   WHERE  a.docId in (select b.docId from good b)   ";
		sql="SELECT a.docId,  (select b.docName From good b Where a.docId=b.docId) as name   FROM worker  a   ";
		sql="SELECT a.docId   , b.docName  FROM worker  a  , (select  c.docName , c.docId From good c where c.docId='222' ) as b  where    a.docId=b.docId";
		sql = "SELECT a.workid,c.docName as Name FROM worker a  LEFT JOIN (SELECT d.docId,d.docName FROM  good d  WHERE d.id>120 ) c ON a.docId=c.docId      ";
	//	sql = "  SELECT D.docId , D.docName , D.ID   FROM good D  WHERE  d.id>10 AND d.id>100  ";
	
		
	//	sql="select e.docId from (SELECT a.docId   , a.docName  FROM worker  a  UNION all SELECT b.docId   , b.docName  FROM good  b  UNION ALL SELECT c.docId   , c.docName  FROM good  c ) e";
	//	sql="select distinct e.docName from good e  where e.id=10";
		//sql="select distinct e.docName from good e  where e.docName='ddd'";
	//	sql="SELECT a.docName  FROM worker  a , good b WHERE a.id=b.id+1200";
	//	sql="SELECT a.id from good  a ";
		sql="SELECT a.docName  FROM worker2  a , good2 b, good3 c WHERE  a.docId=b.docName   ";
		sql="SELECT a.docName  FROM (select c.docId from  worker2 c)  a , (select d.docId from  good2 d where d.id>200000) b  WHERE  a.docId=b.docId   ";
	//	sql="SELECT a.docName  FROM worker4  a left join good4 b on  a.docId=b.docId  ";
	 	sql="SELECT a.docName,b.docId  FROM worker6  a , good7 b  WHERE  a.docId=b.docId   ";
		sql="SELECT  a.docId aid,b.docId  bid   FROM worker7  a , good6 b  WHERE  a.docId=b.docId   ";
		
		//sql="SELECT a.docName  FROM worker5  a    ";
	//	 Date d1=new Date();
		
		
		ExcCacheConf ccf=new ExcCacheConf();
		UserCacheConf userConf1=new UserCacheConf("worker7","WORK", CacheTableConf.ALL,DRConstant.USEIDX,3);
		//UserCacheConf userConf2=new UserCacheConf("good6", CacheTableConf.ALL,DRConstant.USEIDX,3);
		
		//ccf.put("good", CacheTableConf.ALL,DRConstant.USEIDX);
		//ccf.put("worker7", CacheTableConf.ALL,DRConstant.USEIDX);
		ccf.put(userConf1);
	//	ccf.put(userConf2);
		DearSelector selector =new DearSelector();  
		selector.setExecutor(new JdbcExecutor()); 
		QuerySession s=QuerySession.jdbcSession();
		s.useDriverTable="worker7";
		s.cacheConf=ccf;
		
	
		selector.query(sql,s);
		/*
		 while(true){
			 ResultMap map=selector.fetch();
			 if(map==null){
				 break;
			 }
		 }
		 */
		System.out.println("开始时间点" );
		 Date d1=new Date();
		 ResultBuffer  list=selector.getResult();
		 list.init();
		/* 
		 while(list.isEndOut()==false){
			 System.out.println("row - "+list.getCurrCol("a","docId") );
			 list.nextTo();
		 }
	 */
		 Date d2=new Date();
		 System.out.println("sizeiiiii - "+list.size()+" , time:"+(d2.getTime()-d1.getTime()));
		 
		 
	}
	
	public static void init(){
		CacheTableConf ct=new JustCacheTableConf("good7");
		ct.setType(CacheTableConf.ALL);
		ct.setCacheType(DRConstant.DISKCACHE);
	//	ct.setSql("select  docId   from good7   ");
		ct.executor=new CacheExecuteImp(ct);
		
		CacheTableConf ct2=new JustCacheTableConf("worker7","WORK");
		ct2.setType(CacheTableConf.ALL);
		ct2.setCacheType(DRConstant.DISKCACHE);
	//	ct2.setSql("select  docName ,  docId   from worker6  ");
		ct2.executor=new CacheExecuteImp(ct2);
		
	//	ResultCache.addTable(ct);
		ResultCache.addTable(ct2);
	}
	
	public static void hinit(){
 
		
		CacheTableConf ct2=new JustCacheTableConf("worker7","WORK");
		ct2.setType(CacheTableConf.ALL);
		ct2.setCacheType(DRConstant.DISKCACHE); 
		ct2.executor=new CacheExecuteImp(ct2);
 
		ResultCache.addTable(ct2);
	}

	public static void test1(){
		hinit();
		System.out.println("main-end");
	//	run3(); 
		trun();
	}
	
	public static void test2(){
		String sql;
		sql="SELECT  a.docId aid,b.docId  bid   FROM worker2  a , good2 b  WHERE  a.docId=b.docId   ";
	 	sql=" SELECT  a.docId aid,b.docId  bid   FROM worker4  a , good4 b  WHERE  a.docId=b.docId and  a.docId in"
	 			+ " (select c.docId from   worker5 c ,good5 d where c.docId=d.docId and  c.docId=a.docId) ";
	//	sql="SELECT a.docName  FROM worker5  a    ";
	//	 Date d1=new Date();
	 	
	 	sql="SELECT count(a.docId) cnt   FROM worker4  a , good4 b  WHERE  a.docId=b.docId  group by  a.docId  ";
	 	
	 	//sql="SELECT  a.docId aid,b.docId  bid   FROM worker4  a , good4 b  WHERE  a.docId=b.docId   ";
		//sql="SELECT  a.docId aid,b.docId  bid   FROM worker4  a , (select c.docId ,c.docName from good4 c where c.docName=a.docName ) b  WHERE  a.docId=b.docId   ";
		//sql="SELECT  a.docId aid, a.docName aname  FROM worker4 a  where   a.docId in ( select c.docId   from good4 c  where a.docId=c.docId ) ";
		 
		DearSelector selector =new DearSelector();  
		selector.setExecutor(new JdbcExecutor()); 
		QuerySession s=QuerySession.jdbcSession();
		 
		
	
		selector.query(sql,s);
	 
		System.out.println("开始时间点" );
		 Date d1=new Date();
		 ResultBuffer  list=selector.getResult();
		 list.init();
		 Date d2=new Date();
		 System.out.println("sizeiiiii - "+list.size()+" , time:"+(d2.getTime()-d1.getTime()));
		 list.again();
		 while(list.hasNext()){
			 System.out.println("cnt:   - "+  list.getAggrCurrCol("CNT"));
			 list.nextTo();
		 }
		
		
	
	}
	
	public static void main(String[] args) {
		test2();
	}
	
	public static void trun(){
		for(int i=0;i<3;i++){
			Thread t=new Thread(){
				public void run(){
					run3();
				}
			};
			t.start();
		}
	}
}
