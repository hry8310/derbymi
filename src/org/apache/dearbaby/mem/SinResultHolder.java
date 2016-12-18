package org.apache.dearbaby.mem;

import java.util.Date;

import org.apache.dearbaby.data.SinResultBuffer;

public class SinResultHolder {

	public SinResultBuffer result;
	public Date holdTime ;
	
	public SinResultHolder(SinResultBuffer r){
		result =r;
		holdTime=new Date();
	}
}
