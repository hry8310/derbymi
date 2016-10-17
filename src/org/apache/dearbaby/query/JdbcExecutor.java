package org.apache.dearbaby.query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.data.SinResult;
import org.apache.dearbaby.data.SinResultByte;
import org.apache.dearbaby.data.SinResultFac;
import org.apache.dearbaby.util.MysqlUtil;


public class JdbcExecutor implements IExecutor {
	Connection conn=null;
	 
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
	
 
	
	
	public Connection getConn() {
		return conn;
	}




	public void setConn(Connection conn) {
		this.conn = conn;
	}




	public SinResult exe(String sql,List<String> columns){
		open();
		SinResult results =SinResultFac.getSinResult();
		try {
				 
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql);
				System.out.println("begin-nexting");
				Map m = new HashMap();
				while (rs.next()) {
					
					for (String c : columns) {
						Object o = rs.getObject(c);
						m.put(c, o);
					}
					results.add(m);
				}
				results.addEnd();
				rs.close();
				stmt.close();
				
		}catch(Exception e){
			e.printStackTrace();
		}
		return results;
	}
}
