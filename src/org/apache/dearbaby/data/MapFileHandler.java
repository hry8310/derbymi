package org.apache.dearbaby.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

public class MapFileHandler extends RandomAccessFile {
	File file=null;
	String op=null;
	public MapFileHandler(File f,String o)  throws FileNotFoundException{
		super(f,o);
		file=f;
		op=o;
		 
	}
	public MapFileHandler copy(){
		try{
			return new MapFileHandler(file,op);
		}catch(Exception e){
			return null;
		}
	}
}
