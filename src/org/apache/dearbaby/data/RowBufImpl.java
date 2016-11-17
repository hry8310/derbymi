package org.apache.dearbaby.data;

import java.nio.ByteBuffer;

public interface RowBufImpl {
	public byte[] getRow(int rowId);
	public boolean addRow(byte[] row,int rowId,int rowLength);
	public void compress();
	public ByteBuffer toSer();
	 
}
