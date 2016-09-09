package org.apache.dearbaby.data;

public class SinResultFac {

	public static SinResult getSinResult(){
		//return new SinResultMap();
		return new SinResultBuffer();
	}
	
}
