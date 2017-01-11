 
package org.apache.dearbaby.mem;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.dearbaby.config.InitConfig;
import org.apache.dearbaby.data.AbstractSinResult;
import org.apache.dearbaby.data.SinResult;
import org.apache.dearbaby.data.SinResultBuffer;
import org.apache.dearbaby.help.PrintTrace;
import org.apache.dearbaby.util.DRConstant;

import sun.nio.ch.DirectBuffer;

 
public  class RowBufferPool {
	private   int chunkSize;
	private final LinkedBlockingQueue<ByteBuffer> items = new LinkedBlockingQueue<ByteBuffer>();
	private final LinkedBlockingQueue<SinResultHolder> holderQueue = new LinkedBlockingQueue<SinResultHolder>();
 
	private volatile int newCreated;
	private volatile int isFull=DRConstant.POOL_FREE;
	private   int capactiy; 
	private static RowBufferPool instance=new RowBufferPool();
	private AtomicInteger allo=new AtomicInteger(0) ;
	private AtomicInteger puted=new AtomicInteger(0) ;
	PoolManager pm=new PoolManager();
	
	private volatile long holder=DRConstant.NO_HOLDER; //1-
	private void init(int size,int chunkSize) {
		this.chunkSize = chunkSize;
		this.capactiy = size;
		for (int i = 0; i < capactiy/10; i++) {
			items.offer(createDirectBuffer(chunkSize));
		}
		pm.start(); 
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

	
	public synchronized void waiting( ){
		try{
			
			wait( );
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	
	
	public void entenWait(){
		//如果还没有人独占资源，就说明现在可以并发
		if(holder==DRConstant.NO_HOLDER){
			holder=Thread.currentThread().getId();
			return ;
		}
		if(holder==Thread.currentThread().getId()){
			return;
		}
	
	//	System.out.println("enting waiting......."+Thread.currentThread().getId());
	//	PrintTrace.print();
		//进入等待
		waiting();
	}
	
	public synchronized void notifying (int num){
		try{
			//正在释放
			holder=DRConstant.HOLDER_FREE_ING;
			if(num==DRConstant.NOTI_ALL){
				notifyAll();
				
			}else{
				for(int i=0;i<num;i++){
					notify( );
				}
					
			}
			//释放完毕
			holder=DRConstant.NO_HOLDER;
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void chkNotify(){
		double ratio=(double)allo.get()/capactiy;
		if(ratio<DRConstant.COMCU_TO_QUE_HOLD){
			notifying(DRConstant.NOTI_ALL);
		}else{
			notifying(1);
		}
	}
	
 
	 
	public ByteBuffer allocate() {
		ByteBuffer node = null;
		 
		node = items.poll();
		if (node == null) {
			if(allo.get()>((double)capactiy)*(1.2)){
				try{
					//预记为full
					//isFull=DRConstant.POOL_FULL;
					notifyFree();
					//并发改为按队列处理
					entenWait();
					//
					if(holder==Thread.currentThread().getId()){
					//	System.out.println("empty>>>>>>>>>>>>>>>>>>>>> "+holder);
					}
					//PrintTrace.print();
					node = items.poll(0, TimeUnit.MILLISECONDS); 
				}catch(Exception e){
					
				}
			}
		}
		//如果最后还是空了，只能增加一个了
		
		if (node == null){
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
	
	public ByteBuffer allocateAndFreeHolder(int size) {
		ByteBuffer b=null;
		if (size <= this.chunkSize) {
			  b= allocate();
		} else {
		 
			b= createTempBuffer(size);
		}
		chkNotify();
		return b;
	}

	private boolean checkValidBuffer(ByteBuffer buffer) {
		//System.out.println("ddddddddddddddddddd "+buffer.isDirect());
		if (buffer == null ) {
			return false;
		}   
		buffer.clear();
		return true;
	}

	public void recycle(ByteBuffer buffer) {
		
		if (!checkValidBuffer(buffer)) {
			return;
		}
		//System.out.println("offfferrrrrrrrrrrrr");
		allo.decrementAndGet();
		if(items.size()>capactiy){
			
			DirectBuffer d=(DirectBuffer)buffer;
			d.cleaner().clean();
			return ;
		}  
		ByteBuffer b=buffer;
		
		items.offer(b);
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
		System.out.println("------createTempBuffer--------");
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
	
	public boolean holder(SinResultBuffer rst){
		if(holder!=DRConstant.NO_HOLDER||holder!=DRConstant.HOLDER_FREE_ING){
			chkNotify();
		}
		
		if(rst.ref==null||rst.ref.r.get()==0){
			return false;
		}
		SinResultHolder holder3=new SinResultHolder(rst);
		holderQueue.add(holder3);
		return true;
	}
	
	public boolean freeHolder(){
		return freeHolder(1);
	}
	public boolean freeHolder(int freeTime){
		boolean hasFree=false;
		for(int i=0;i<freeTime;){
			SinResultHolder holder=holderQueue.poll();
			if(holder==null){
				return hasFree;
			}
			if(holder!=null){
				if(holder.result.clear()){
					hasFree=true;
					i++;
				}
			}
		}
		return false;
	}
	
	private void notifyFree(){
		pm.notifying();
	}
	
	class PoolManager extends Thread{

		public synchronized void waiting(long time){
			try{
				wait(time);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
		public synchronized void notifying (){
			try{
				notify( );
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
		public void run(){
			while(true){
				try{
					waiting(InitConfig.FREE_MONITOR_INYRTVAL);
					
					RowBufferPool pool=RowBufferPool.getPool();
					pool.freeHolder();
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	}
}
