package org.apache.dearbaby.cache;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.data.SinResult;
import org.apache.dearbaby.data.SinResultFac;
import org.apache.dearbaby.util.MysqlUtil;
 

public  class CacheExecuteImp extends  AbstractCacheExecute {
	Connection conn;
	SinResult results=null;
	public void open(){
		try{
			if(conn!=null){
				return;
			}
			Class.forName("com.mysql.jdbc.Driver");//
			conn = DriverManager.getConnection(MysqlUtil.url, "root", "123456");
		}catch(Exception e){
			
		}
		
	}
	
	public CacheExecuteImp(CacheTableConf conf){
		tableConf=conf;
				
	}
	
	public Connection getConn() {
		return conn;
	}


	public void setConn(Connection conn) {
		this.conn = conn;
	}


	 


	public SinResult exe(String table,String sql){
		results=SinResultFac.getSinResult(null,null);
		try {
				open();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql);
				ResultSetMetaData rsmd= rs.getMetaData(); 
				int count=rsmd.getColumnCount();
				
				Map m = new HashMap();
				while (rs.next()) {
					for(int i=1;i<=count;i++){
						String c=rsmd.getColumnName(i);
						Object o = rs.getObject(c);
						m.put(c.toUpperCase(), o);
					}
					results.add(m);
				}
				rs.close();
				stmt.close();
				
		}catch(Exception e){
			e.printStackTrace();
		}
		return results.copy();
	}
}
