package com.dazhihui.smdemo.network.nio;

import android.os.Process;

import java.util.concurrent.ArrayBlockingQueue;

public abstract class PacketHandler implements Runnable {

	/**
	 *  The max size of the queue.
	 */
	private static final int MAX_SIZE = 255;
	
	private ArrayBlockingQueue<byte[]> mResultQueue;
	
	/**
	 *  
	 */
	private boolean mDoing = true;
	
	private Thread mCurrentThd = null;
	
	/**
	 * 
	 */
	public PacketHandler(){
		mResultQueue = new ArrayBlockingQueue<byte[]>(MAX_SIZE);
		new Thread(this).start();
	}
	
	/**
	 * @param rsp
	 */
	public void processResponse(byte[] rsp) {
		if(rsp != null){
			while(!mResultQueue.offer(rsp)){
				mResultQueue.poll();
			}
		}
	}
	
	/**
	 * 
	 */
	public void waitForResponse() {
		while(mDoing) {
			byte[] response = null;
			try {
				response = mResultQueue.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				//Functions.printStackTrace(e);
			}
			if(response != null){
				handleResponse(response);
			}
		}
		
	}
	
	@Override
	public void run() {
		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		Thread.currentThread().setName("PacketHandler");
		mCurrentThd = Thread.currentThread();
		waitForResponse();
	}

	public void close(){
		mDoing = false;
		if(mCurrentThd != null && mCurrentThd.isAlive()){
			mCurrentThd.interrupt();
		}
	}
	
	protected abstract void handleResponse(byte[] data);
}
