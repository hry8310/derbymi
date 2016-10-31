package org.apache.dearbaby.data;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.apache.dearbaby.config.InitConfig;

public class MapFile {

	public String path;
	
	public File file;
	public MapFileHandler raf=null;
	private int headerSet=InitConfig.MAP_FILE_HEAD_SIZE*4+4;
	int[] header=null;
	int headerIndex=0;
	
	public MapFile copy(){
		MapFile mf=new MapFile();
		mf.path=this.path;
		mf.raf=this.raf.copy();
		mf.headerSet=this.headerSet;
		mf.header=this.header;
		mf.headerIndex=this.headerIndex;
		return mf;
		
	}
	
	public void reOpen(){
		try{
			  raf.close();
			  raf=new MapFileHandler(file,"rw");  
			}catch(Exception e){
				e.printStackTrace();
			}
		
	}
	public boolean open(){
		file=new File(path);
		if(file.exists()){
			file.delete();
			file=new File(path);
		}
		try{
		   file.createNewFile();
		   raf=new MapFileHandler(file,"rw");  
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public boolean open(String f){
		path=f;
		return open();
	}
	
	public void close(){
		
		if(raf!=null){
			try{
				raf.close();
				raf=null;
				
			}catch(Exception e){
				
			}
		}
	}
	
	public boolean writeHeader(int[] is){
		 if(raf==null){
			 return  false;
		 }
		 if(is.length>=InitConfig.MAP_FILE_HEAD_SIZE*4){
			 return false;
		 }
		 try{
			 raf.seek(0);
			 ByteBuffer buf = ByteBuffer.allocate(headerSet);
			 buf.putInt(is.length);
			 for(int i=0;i<is.length;i++){
					buf.putInt(is[i]);
			}
			raf.write(buf.array());
		}catch(Exception e){
			return false;
		}
		 return true;
	}
	
	private boolean writeHeader(int off){
		int offset=headerIndex*4+4;
		
		try{
			int b=0;
			if(headerIndex>0){
				raf.seek(offset);
				b=raf.readInt();
			}
			raf.seek(offset+4); 
			raf.writeInt(off+b);
			
		}catch(Exception e){
			return false;
		}
		
		headerIndex++;
		return true;
	}
	
	public boolean writeRs(byte[] rs,int offset,int len){
	//	System.out.println("resultTO  "+ new String(rs));
		if(raf==null){
			 return  false;
		 }
		  
		 try{
			 System.out.println("write-offset "+offset);
			 raf.seek(headerSet+offset);
			 
			 
			raf.write(rs,0,len);
			  
			 
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		 return true;
	} 
	
	public boolean writeRs(byte[] rs,int offset ){
		return writeRs(rs,offset,rs.length);
	}
	
	
	
	public int[] readHeader(){
		if(header!=null){
			return header;
		}
		if(raf==null){
			 return  null;
		 }
		 try{
			 raf.seek(0);
			 byte[] h=new byte[headerSet];
			 raf.read(h);
			 ByteBuffer buf = ByteBuffer.wrap(h);
			 int l=buf.getInt();
			 int[] le=new int[l];
			 for(int i=0;i<l;i++){
				 le[i]=buf.getInt();
			 }
			 header=le;
			 return le;
		}catch(Exception e){
			return null;
		} 
		 
	}
	
	
	public byte[] readRs(int offset,int length){
		if(raf==null){
			 return  null;
		 }
		 try{
			  
			 raf.seek(headerSet+offset);
			 byte[] h=new byte[length];
			 raf.read(h);
			// System.out.println("fromSer-----   "+offset);
			 return h;
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
		 
	}
	
	public  byte[] readRs(int i){
		
		if(header==null){
			readHeader();
			if(header==null){
				return null;
			}
		}
		if(header.length<i){
			return null;
		}
		int bi=header[i];
		int begin=0;
		if(i>0){
			begin=header[i-1];
		}
		return readRs(begin,bi-begin);
	}
}
