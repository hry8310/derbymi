package org.apache.dearbaby.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.dearbaby.query.JoinType;
import org.apache.dearbaby.query.SinQuery;

public class JoinTypeUtil {
	public static void addJoin(List<JoinType> js,JoinType jin){
		if(jin==null){
			return;
		}
		if(js.size()==0){
			js.add(jin);
			return;
		}
		System.out.println("ddddddddddd  "+js.size());
		for(int i=0;i<js.size();i++){
			JoinType j=js.get(i);
			String lt=j.left.getTableName();
			String rt=j.right.getTableName();
			
			String lti=jin.left.getTableName();
			String rti=jin.right.getTableName();
			
			if((lt.equalsIgnoreCase(lti)&&rt.equalsIgnoreCase(rti))||
					(lt.equalsIgnoreCase(lti)&&rt.equalsIgnoreCase(rti))){
				if(j.type==JoinType.HASH){
					return ;
				}
				if(jin.type==JoinType.HASH){
					js.add(i, jin);
					
				}
			}else{
				js.add(jin);
			}
			
			
		}
		
	}
	
	
	public static JoinType findJoin(ArrayList<JoinType> js ,SinQuery sq1,SinQuery sq2){
		
		for(JoinType j:js){
			String lt=j.left.getTableName();
			String rt=j.right.getTableName();
			if((lt.equals(sq1.alias)&&rt.equals(sq2.alias))||
					(rt.equals(sq1.alias)&&lt.equals(sq2.alias))	){
				return j;
			}
		}
		return null;
	}
	
	public static JoinType ans(ArrayList<JoinType> js ){
		System.out.println("js   "+js.size());
		JoinType j=js.get(0);
		j.emp=j.left.getTableName();
		j.nextTable=j.right.getTableName();
		JoinType jEnd=null; 
		js.remove(j);
		for(int i=0;i<js.size();i++){
			
			JoinType ij=js.get(i);
			if(ij.left.getTableName().equalsIgnoreCase(j.left.getTableName())){
				j.emp=j.right.getTableName();
				ij.emp=ij.right.getTableName();
				j.next=ij;
				ij.prev=j;
				j.nextTable=ij.left.getTableName();
				jEnd=ij;
				js.remove(i);
				break;
			}
			
			if(ij.left.getTableName().equalsIgnoreCase(j.right.getTableName())){
				j.emp=j.left.getTableName();
				ij.emp=ij.right.getTableName();
				j.nextTable=ij.left.getTableName();
				j.next=ij;
				ij.prev=j;
				jEnd=ij;
				js.remove(i);
				break;
			}
			if(ij.right.getTableName().equalsIgnoreCase(j.right.getTableName())){
				j.emp=j.left.getTableName();
				ij.emp=ij.left.getTableName();
				j.nextTable=ij.right.getTableName();
				j.next=ij;
				ij.prev=j;
				jEnd=ij;
				js.remove(i);
				break;
			}
			if(ij.right.getTableName().equalsIgnoreCase(j.left.getTableName())){
				j.emp=j.right.getTableName();
				ij.emp=ij.left.getTableName();
				j.nextTable=ij.right.getTableName();
				j.next=ij;
				ij.prev=j;
				jEnd=ij;
				js.remove(i);
				break;
			}
		}
	
		while(true){
			
			for(int i=0;i<js.size();i++){
				JoinType ij=js.get(i);
				if(ij.left.getTableName().equalsIgnoreCase(j.emp)){
					ij.emp=ij.right.getTableName();
					j.prev=ij;
					ij.next=j;
					ij.nextTable=j.emp;
					j=ij;
					js.remove(i);
					break;
				}
				
				if(ij.left.getTableName().equalsIgnoreCase(jEnd.emp)){
					ij.emp=ij.right.getTableName();
					jEnd.nextTable=jEnd.emp;
					jEnd.next=ij;
					ij.prev=jEnd;
					jEnd=ij;
					js.remove(i);
					break;
				}
				if(ij.right.getTableName().equalsIgnoreCase(j.emp)){
					ij.emp=ij.left.getTableName();
					j.prev=ij;
					ij.next=j;
					ij.nextTable=j.emp;
					j=ij;
					js.remove(i);
					break;
				}
				if(ij.right.getTableName().equalsIgnoreCase(jEnd.emp)){
					ij.emp=ij.left.getTableName();
					jEnd.next=ij;
					jEnd.nextTable=jEnd.emp;
					ij.prev=jEnd;
					jEnd=ij;
					js.remove(i);
					break;
				}
				
				js.remove(i);
			}
			if(js.size()==0){
				break;
			}
		}
		return j;
	}
 

 
	 
}
