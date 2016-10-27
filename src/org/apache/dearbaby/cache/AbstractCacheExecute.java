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
 

public abstract class AbstractCacheExecute implements CacheExecute {
	 protected CacheTableConf tableConf;
	 public void setCacheTableConf(CacheTableConf conf){
		 tableConf=conf;
	 }
	 
	 public CacheTableConf getCacheTableConf( ){
		 return tableConf;
	 }
}
