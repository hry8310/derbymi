package org.apache.dearbaby.query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.util.MysqlUtil;


public class JdbcExecutor implements IExecutor {
	Connection conn;
	public JdbcExecutor()throws Exception{
		Class.forName("com.mysql.jdbc.Driver");//
		conn = DriverManager.getConnection(MysqlUtil.url, "root", "123456");
	}
	
	public ArrayList<Map> exe(String sql,List<String> columns){
		ArrayList<Map> results =new 	ArrayList<Map>();
		try {
				
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql);
			
				while (rs.next()) {
					Map m = new HashMap();
					for (String c : columns) {
						Object o = rs.getObject(c);
						m.put(c, o);
					}
					results.add(m);
				}
		}catch(Exception e){
			e.printStackTrace();
		}
		return results;
	}
}
