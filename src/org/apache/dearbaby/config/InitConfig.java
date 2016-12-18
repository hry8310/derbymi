package org.apache.dearbaby.config;

public class InitConfig {

	public static int ROW_ARROW_REMAIN_LENGTH=10;
	public static double ROW_ARROW_REMAIN_ROTIO=0.2;
	public static double BLOCK_ARROW_REMAIN_ROTIO=0.2;

	public static int BLOCK_ARROW_REMAIN_LENGTH=100;
	
	public static int DISK_USE=1;
	
	public static int DISK_USE_MAX_SIZE=50;
	
	public static int ROWS_BUFFER_SIZE=1024*1024;
	public static int ROWS_SIZE=1000;	
	
	public static int DISK_ROW_BUFFER_SIZE_ROTIO=4;
	
	public static int MAP_FILE_HEAD_SIZE=10*1000;
	public static double MAP_FILE_HEAD_SIZE_RE_RATIO=0.25;
	
	public static String  MAP_FILE_DIR="F:/derbymi/";
	
	public static int HASH_IDX_RATIO=16; //必须大于8，最好定义为8的倍数
	
	public static int DIR_BUFFER_CAP=800;
	
	public static int FREE_MONITOR_INYRTVAL=1000*300;
}
