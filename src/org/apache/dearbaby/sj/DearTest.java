package org.apache.dearbaby.sj;

import java.util.List;
import java.util.Map;

public class DearTest {

	 
/*
	String sql = "";
	 
	sql = "select a.name,a.iss from news a ,comany c where a.id in(select b.iid from cust b) and a.ikey=c.ikey";
	sql = "select a.workid as wid,(select b.kid from cust b where b.id=a.id ) as kid FROM WorkInforParameter  a ";
	sql = "select a.workid,a.doctorName from WorkInforParameter a ,DoctorInforParameter c where a.doctorid=c.doctorid  ";
	sql = "select a.workid,c.doctorName from WorkInforParameter a left join DoctorInforParameter c on a.doctorid=c.doctorid   ";
	sql = "SELECT a.workid FROM WorkInforParameter  a , OrderListParameter e WHERE a.id IN(SELECT b.id FROM DoctorInforParameter b WHERE a.DoctorId=b.DoctorId AND b.DeptId IN(SELECT c.DeptId FROM DeptInforParameter c WHERE c.DeptName=a.DeptName)) or e.WorkId=a.WorkId ";
*/
	public static void run3() {
		String sql="";
		sql = "SELECT  a.doctorName FROM DoctorInforParameter a WHERE a.doctorid IN (SELECT c.doctorid FROM WorkInforParameter c)";
		sql="SELECT a.doctorName  FROM WorkInforParameter  a , doctorinforparameter b WHERE  a.DoctorId=b.DoctorId  OR a.Id>1380";
		sql="SELECT a.doctorName  FROM WorkInforParameter  a , doctorinforparameter b WHERE  a.doctorName=b.DoctorId  OR a.Id>4380";
		sql="SELECT a.doctorName  FROM WorkInforParameter  a   WHERE  a.DoctorId in (select b.DoctorId from doctorinforparameter b)  ";
		sql="SELECT a.doctorId, sum(a.flag)    FROM WorkInforParameter  a   WHERE  a.DoctorId in (select b.DoctorId from doctorinforparameter b)   ";
		sql="SELECT a.doctorId,  (select b.doctorName From doctorinforparameter b Where a.doctorId=b.doctorId) as name   FROM WorkInforParameter  a   ";
		sql="SELECT a.doctorId   , b.doctorName  FROM WorkInforParameter  a  , (select  c.doctorName , c.doctorId From doctorinforparameter c where c.doctorId='222' ) as b  where    a.doctorId=b.doctorId";
		sql = "SELECT a.workid,c.doctorName as Name FROM WorkInforParameter a  LEFT JOIN (SELECT d.doctorid,d.doctorName FROM  DoctorInforParameter d  WHERE d.id>120 ) c ON a.doctorid=c.doctorid      ";
		sql = "  SELECT D.DOCTORID , D.DOCTORNAME , D.ID   FROM DOCTORINFORPARAMETER D  WHERE  d.id>10 AND d.id<1000  ";
	
		
		sql="select e.doctorId from (SELECT a.doctorId   , a.doctorName  FROM WorkInforParameter  a  UNION all SELECT b.doctorId   , b.doctorName  FROM doctorinforparameter  b  UNION ALL SELECT c.doctorId   , c.doctorName  FROM doctorinforparameter  c ) e";
		sql="select distinct e.doctorName from doctorinforparameter e  where e.id=10";
		//sql="select distinct e.doctorName from doctorinforparameter e  where e.docName='ddd'";
		
		//sql="SELECT a.doctorName from DoctorInforParameter  a order by a.doctorid";
		 DearSelector selector =new DearSelector();  
		 selector.query(sql);
		/*
		 while(true){
			 ResultMap map=selector.fetch();
			 if(map==null){
				 break;
			 }
		 }
		 */
		 
		 List<ResultMap>  list=selector.getResult();
		 for(ResultMap r:list){
			 System.out.println("r  "+r.m);
		 }
		 
	}

	public static void main(String[] args) {
		run3();
	}

}
