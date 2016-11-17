 
package org.apache.dearbaby.mem;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.dearbaby.config.InitConfig;

import sun.nio.ch.DirectBuffer;

 
public  class RowBufferPool {
	private   int chunkSize;
	private final LinkedBlockingQueue<ByteBuffer> items = new LinkedBlockingQueue<ByteBuffer>();
 
	private volatile int newCreated;
	private   int capactiy; 
	private static RowBufferPool instance=new RowBufferPool();
	private AtomicInteger allo=new AtomicInteger(0) ;
	private AtomicInteger puted=new AtomicInteger(0) ;
	private void init(int size,int chunkSize) {
		this.chunkSize = chunkSize;
		this.capactiy = size;
		for (int i = 0; i < capactiy/10; i++) {
			items.offer(createDirectBuffer(chunkSize));
		}
		 
	}
	public RowBufferPool(int size,int chunkSize) {
		init(size,chunkSize);
		 
	}
	public RowBufferPool(){
		init(InitConfig.DIR_BUFFER_CAP,InitConfig.ROWS_BUFFER_SIZE);
	}
	
	public int getChunkSize() {
		return chunkSize;
	}
 

	public int size() {
		return this.items.size();
	}

	public int capacity() {
		return capactiy + newCreated;
	}

	public ByteBuffer allocate() {
		ByteBuffer node = null;
		 
		node = items.poll();
		if(allo.get()>((double)capactiy)*(1.2)){
			try{
				node = items.take();
			}catch(Exception e){
				
			}
		}
		
		if (node == null) {
			newCreated++;
			node = this.createDirectBuffer(chunkSize);
		}
		node.clear();
		allo.incrementAndGet();
		return node;
	}
	public ByteBuffer allocate(int size) {
		if (size <= this.chunkSize) {
			return allocate();
		} else {
		 
			return createTempBuffer(size);
		}
	}

	private boolean checkValidBuffer(ByteBuffer buffer) {
		if (buffer == null || !buffer.isDirect()) {
			return false;
		}   
		buffer.clear();
		return true;
	}

	public void recycle(ByteBuffer buffer) {
		
		if (!checkValidBuffer(buffer)) {
			return;
		}
		allo.decrementAndGet();
		if(items.size()>capactiy){
			DirectBuffer d=(DirectBuffer)buffer;
			d.cleaner().clean();
			return ;
		}  
		items.offer(buffer);
	}

	 
	public boolean testIfDuplicate(ByteBuffer buffer) {
		for (ByteBuffer exists : items) {
			if (exists == buffer) {
				return true;
			}
		}
		return false;

	}

	private ByteBuffer createTempBuffer(int size) {
		return ByteBuffer.allocate(size);
	}

	private ByteBuffer createDirectBuffer(int size) {
		// for performance
		return ByteBuffer.allocateDirect(size);
	}

	public int freeBuffer(){
		return items.size();
	} 

	
	/*
	public static void main(String[] args) {
		BufferPool pool = new BufferPool(1024 * 5, 1024);
		int i = pool.capacity();
		ArrayList<ByteBuffer> all = new ArrayList<ByteBuffer>();
		for (int j = 0; j <= i; j++) {
			all.add(pool.allocate());
		}
		for (ByteBuffer buf : all) {
			pool.recycle(buf);
		}
		System.out.println(pool.size());
	}
	*/
	
	public static RowBufferPool getPool(){
		return instance;
	}
	 
}
