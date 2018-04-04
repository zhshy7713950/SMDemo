package com.dazhihui.smdemo.network.nio;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;


import com.dazhihui.smdemo.network.exception.ConnectionException;
import com.dazhihui.smdemo.network.exception.ProxyException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NioConnection implements Runnable {

	private static final Pattern RESPONSE_PATTERN = Pattern.compile("HTTP/\\S+\\s(\\d+)\\s(.*)\\s*");

	private String host;
	private int port;
	private String proxyHost;
	private int proxyPort;
	private boolean needProxy = false;

	private Selector selector;
	private int mOps = SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE;
	private int mCurrentOps;

	private ByteBuffer readBuffer = ByteBuffer.allocate(2048);

	private List<ByteBuffer> pendingData = new ArrayList<ByteBuffer>();

	private PacketHandler mPacketHandler = null;

	private ConnectHandler mConnectHandler = null;

	private int mConnectStatus = ConnectHandler.DISCONNECT;
	private volatile boolean mDone = false;

	private Thread mProcessThd = null;
	private AtomicInteger atomic = new AtomicInteger(0);
	private AtomicInteger mSendDataCount = new AtomicInteger(0);
	private final static int SEND_MAX_COUNT = 5;
	private final static long WRITE_WAIT_TIME = 15 * 1000;

	public NioConnection(String host, int port, int ops) throws IOException {
		mOps = ops;
		this.host = host;
		this.port = port;
	}

	public NioConnection(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void setProxy(String proxyHost, int proxyPort) {
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
	}

	public String getHost(){
		return host;
	}

	public int getPort(){
		return port;
	}

	public void setNeedProxy(boolean isProxy) {
		needProxy = isProxy;
	}

	private Handler startHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			startHandler.removeMessages(0);
			startThread();
		}

	};

	private synchronized void init() {
		try {
			this.selector = this.initSelector();
		} catch (IOException e) {
			mConnectStatus = ConnectHandler.DISCONNECT;
			if (mConnectHandler != null) {
				mConnectHandler.netConnectException(e);
			}
			e.printStackTrace();
		}
	}

	public void resetServerAddress(String host, int port) {
		this.stopProcessThread();
		this.host = host;
		this.port = port;
		// mOps = SelectionKey.OP_CONNECT;
		mConnectStatus = ConnectHandler.CONNECTING;
		if (!exceptionHandler.hasMessages(1)) {
			exceptionHandler.sendEmptyMessageDelayed(1, WRITE_WAIT_TIME);
		}
		// start();
	}

	public void registPacketHandler(PacketHandler handler) {
		if (mPacketHandler != null) {
			mPacketHandler.close();
			mPacketHandler = null;
		}
		mPacketHandler = handler;
	}

	public void registConnectHandler(ConnectHandler handler) {
		mConnectHandler = handler;
	}

	public void send(byte[] data) {
		synchronized (this.pendingData) {
			if (pendingData == null) {
				pendingData = new ArrayList<ByteBuffer>();
			}
			pendingData.add(ByteBuffer.wrap(data));
		}
	}

	private Selector initSelector() throws IOException {
		// Create a new selector
		return SelectorProvider.provider().openSelector();
	}

	public void start() {
		startHandler.sendEmptyMessageDelayed(0, 100);
	}

	private void startThread() {
		new Thread(this).start();
	}

	@Override
	public void run() {
		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		Thread.currentThread().setName("NioThread" + atomic.incrementAndGet());
		stopProcessThread();
		init();
		mProcessThd = Thread.currentThread();
		SocketChannel socketChannel = null;
		if (mConnectStatus == ConnectHandler.DISCONNECT || mConnectStatus == ConnectHandler.CONNECTING) {
			try {
				socketChannel = initiateConnection();
			} catch (Exception e1) {
				mConnectStatus = ConnectHandler.DISCONNECT;
				if (mConnectHandler != null) {
					mConnectHandler.netConnectException(e1);
				}
				mDone = false;
				mCurrentOps = 0;
				mProcessThd = null;
				try {
					if (selector != null) {
						selector.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				selector = null;
				return;
			}
		}
		mDone = true;
		while (mDone) {
			try {
				int channelCount = 0;
				int readyChannels = 0;
				do {
					exceptionHandler.sendEmptyMessageDelayed(0, WRITE_WAIT_TIME);
					readyChannels = selector.select();
					exceptionHandler.removeMessages(0);
					channelCount++;
					if (channelCount >= 5 && readyChannels == 0) {
						throw new Exception("selector exception");
					} else if (readyChannels == 0) {
						Thread.sleep(100);
					}
				} while (readyChannels == 0);

				Set<SelectionKey> selectedKeys = selector.selectedKeys();

				Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

				while (keyIterator.hasNext()) {

					SelectionKey key = keyIterator.next();
					keyIterator.remove();
					if (!key.isValid()) {
						continue;
					}
					if (mCurrentOps != 0 && ((mCurrentOps & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)) {
						if (!exceptionHandler.hasMessages(0)) {
							exceptionHandler.sendEmptyMessageDelayed(0, WRITE_WAIT_TIME);
						}
					} else {
						exceptionHandler.removeMessages(0);
					}
					if (key.isAcceptable()) {
						// a connection was accepted by a
						// ServerSocketChannel.
					} else if (key.isConnectable()) {
						finishConnection(key);
					} else {
						if (key.isReadable()) {
							this.read(key);
						}
						if (key.isWritable()) {
							if (exceptionHandler.hasMessages(0)) {
								exceptionHandler.removeMessages(0);
							}
							this.write(key);
						}
					}
				}
				Thread.sleep(50);
			} catch (Exception e) {
				e.printStackTrace();
				exceptionHandler.removeMessages(0);
				mConnectStatus = ConnectHandler.DISCONNECT;
				mDone = false;
				if (mConnectHandler != null && !(e instanceof InterruptedException)) {
					mConnectHandler.invokeStatusChanged(ConnectHandler.DISCONNECT);
					mConnectHandler.netConnectException(e);
				}
				break;
			}
		}
		exceptionHandler.removeMessages(0);
		try {
			if (selector != null) {
				selector.close();
			}
			if(socketChannel != null){
				socketChannel.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		selector = null;
	}

	public void close() {
		if (mPacketHandler != null) {
			mPacketHandler.close();
			mPacketHandler = null;
		}
		stopProcessThread();
	}

	private synchronized void stopProcessThread() {
		mDone = false;
		mCurrentOps = 0;
		if (selector != null && selector.isOpen()) {
			try {
				selector.wakeup();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (mProcessThd != null && mProcessThd.isAlive()) {
			try {
				mProcessThd.join(500);
				if (mProcessThd != null) {
					mProcessThd.interrupt();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				// Functions.printStackTrace(e);
			}
			mProcessThd = null;
		}
	}

	private void write(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		if (needProxy && mConnectStatus == ConnectHandler.CONNECTING) {
			String hostport = "CONNECT " + host + ":" + port;
			String proxyLine = "";
			ByteBuffer buf = ByteBuffer.wrap((hostport + " HTTP/1.1\r\nHost: " + hostport + proxyLine + "\r\n\r\n")
					.getBytes("UTF-8"));
			socketChannel.write(buf);
			key.interestOps(SelectionKey.OP_READ);
		} else {
			synchronized (this.pendingData) {

				while (!pendingData.isEmpty()) {
					ByteBuffer buf = (ByteBuffer) pendingData.get(0);
					socketChannel.write(buf);
					if (buf.remaining() > 0) {
						break;
					}
					pendingData.remove(0);
					mSendDataCount.incrementAndGet();
					break;
				}
			}
			if (mSendDataCount.get() > SEND_MAX_COUNT) {
				socketChannel.register(this.selector, SelectionKey.OP_READ);
				mCurrentOps = SelectionKey.OP_READ;
			} else {
				socketChannel.register(this.selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				mCurrentOps = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
			}
		}
	}

	private void read(SelectionKey key) throws ClosedChannelException/*
																	 * throws
																	 * IOException
																	 */{
		SocketChannel socketChannel = (SocketChannel) key.channel();
		if (needProxy && mConnectStatus == ConnectHandler.CONNECTING) {
			if (exceptionHandler.hasMessages(1)) {
				exceptionHandler.removeMessages(1);
			}
			this.readBuffer.clear();
			try {
				int numRead = socketChannel.read(this.readBuffer);
				if (numRead > 0) {
					byte[] temp = readBuffer.array();
					ByteArrayInputStream in = new ByteArrayInputStream(temp);
					StringBuilder got = new StringBuilder(100);
					int nlchars = 0;
					while (true) {
						char c = (char) in.read();
						got.append(c);
						if (got.length() > 1024) {
							throw new ProxyException("Recieved " + "header of >1024 characters from " + proxyHost
									+ ", cancelling connection");
						}
						if (c == -1) {
							throw new ProxyException("Don't support http proxy!");
						}
						if ((nlchars == 0 || nlchars == 2) && c == '\r') {
							nlchars++;
						} else if ((nlchars == 1 || nlchars == 3) && c == '\n') {
							nlchars++;
						} else {
							nlchars = 0;
						}
						if (nlchars == 4) {
							break;
						}
					}
					if (nlchars != 4) {
						throw new ProxyException("Never " + "received blank line from " + proxyHost
								+ ", cancelling connection");
					}
					String gotstr = got.toString();
					BufferedReader br = new BufferedReader(new StringReader(gotstr));
					String response = br.readLine();
					if (response == null) {
						throw new ProxyException("Empty proxy " + "response from " + proxyHost + ", cancelling");
					}
					Matcher m = RESPONSE_PATTERN.matcher(response);
					if (!m.matches()) {
						throw new ProxyException("Unexpected " + "proxy response from " + proxyHost + ": " + response);
					}
					int code = Integer.parseInt(m.group(1));
					if (code != HttpURLConnection.HTTP_OK) {
						throw new ProxyException("Don't support http proxy!");
					}
					mConnectStatus = ConnectHandler.CONNECTED;
					if (mOps == SelectionKey.OP_CONNECT) {
						mDone = false;
					} else {
						key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
					}
					if (mConnectHandler != null) {
						mConnectHandler.invokeStatusChanged(ConnectHandler.CONNECTED);
					}
				}
			} catch (IOException e) {
				mConnectStatus = ConnectHandler.DISCONNECT;
				if (e instanceof ProxyException) {
					// key.cancel();
					needProxy = false;
					// socketChannel.register(this.selector,mOps);
					stopProcessThread();
					start();
				} else {
					key.cancel();
					mDone = false;
					if (mConnectHandler != null) {
						mConnectHandler.invokeStatusChanged(ConnectHandler.DISCONNECT);
						mConnectHandler.netConnectException(e);
					}
				}
			}
		} else {
			// Clear out our read buffer so it's ready for new data
			this.readBuffer.clear();

			// Attempt to read off the channel
			int numRead;
			byte[] bytes = new byte[0];
			try {
				numRead = socketChannel.read(this.readBuffer);
				while (numRead > 0) {
					/*
					 * readBuffer.flip(); while (readBuffer.hasRemaining()) {
					 * byteList.add(readBuffer.get()); }
					 */
					byte[] temp = readBuffer.array();
					byte[] distBytes = new byte[bytes.length + numRead];
					System.arraycopy(bytes, 0, distBytes, 0, bytes.length);
					System.arraycopy(temp, 0, distBytes, bytes.length, numRead);
					bytes = distBytes;
					readBuffer.clear();
					numRead = socketChannel.read(readBuffer);
					mSendDataCount.decrementAndGet();
				}
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			socketChannel.register(this.selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
			mCurrentOps = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
			mSendDataCount.set(0);
			/*
			 * if(mSendDataCount.get() < SEND_MAX_COUNT){
			 * socketChannel.register(this.selector,SelectionKey.OP_READ |
			 * SelectionKey.OP_WRITE); mSendDataCount.set(0); } else{
			 * socketChannel.register(this.selector,SelectionKey.OP_READ); }
			 */
			if (bytes.length == 0) {
				return;
			}
			// Handle the response
			this.handleResponse(socketChannel, bytes/* , numRead */);
		}
	}

	private void handleResponse(SocketChannel socketChannel, byte[] data/*
																		 * , int
																		 * numRead
																		 */) /*
																			 * throws
																			 * IOException
																			 */{
		// Make a correctly sized copy of the data before handing it
		// to the client
		/*
		 * byte[] rspData = new byte[numRead]; System.arraycopy(data, 0,
		 * rspData, 0, numRead);
		 */

		if (mPacketHandler != null) {
			mPacketHandler.processResponse(data);
		}
	}

	private void finishConnection(SelectionKey key) {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		if (needProxy) {
			try {
				boolean status = socketChannel.finishConnect();
				if (status) {
					key.interestOps(SelectionKey.OP_WRITE);
				} else {
					mConnectStatus = ConnectHandler.DISCONNECT;
					mDone = false;
					if (mConnectHandler != null) {
						mConnectHandler.invokeStatusChanged(ConnectHandler.DISCONNECT);
					}
				}
			} catch (IOException e) {
				key.cancel();
				mConnectStatus = ConnectHandler.DISCONNECT;
				if (mConnectHandler != null) {
					mConnectHandler.invokeStatusChanged(ConnectHandler.DISCONNECT);
					mConnectHandler.netConnectException(e);
				}
				stopProcessThread();
			}
		} else {
			try {
				if (exceptionHandler.hasMessages(1)) {
					exceptionHandler.removeMessages(1);
				}
				boolean status = socketChannel.finishConnect();
				if (status) {
					mConnectStatus = ConnectHandler.CONNECTED;
					if (mOps == SelectionKey.OP_CONNECT) {
						mDone = false;
					} else {
						key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
					}
					if (mConnectHandler != null) {
						mConnectHandler.invokeStatusChanged(ConnectHandler.CONNECTED);
					}
				} else {
					mConnectStatus = ConnectHandler.DISCONNECT;
					mDone = false;
					if (mConnectHandler != null) {
						mConnectHandler.invokeStatusChanged(ConnectHandler.DISCONNECT);
					}
				}
			} catch (IOException e) {
				key.cancel();
				mConnectStatus = ConnectHandler.DISCONNECT;
				if (mConnectHandler != null) {
					mConnectHandler.invokeStatusChanged(ConnectHandler.DISCONNECT);
					mConnectHandler.netConnectException(e);
				}
				stopProcessThread();
			}
		}
	}

	private SocketChannel initiateConnection() throws IOException, InterruptedException {
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		if (needProxy) {
			socketChannel.connect(new InetSocketAddress(InetAddress.getByName(this.proxyHost), this.proxyPort));
		} else {
			socketChannel.connect(new InetSocketAddress(InetAddress.getByName(this.host), this.port));
		}
		if (selector == null) {
			Thread.sleep(100);
			init();
		}
		socketChannel.register(selector, mOps/*
											 * SelectionKey.OP_CONNECT |
											 * SelectionKey.OP_READ |
											 * SelectionKey.OP_WRITE
											 */);
		mCurrentOps = mOps;
		mConnectStatus = ConnectHandler.CONNECTING;
		if (mConnectHandler != null) {
			mConnectHandler.invokeStatusChanged(ConnectHandler.CONNECTING);
		}
		if (!exceptionHandler.hasMessages(1)) {
			exceptionHandler.sendEmptyMessageDelayed(1, WRITE_WAIT_TIME);
		}
		return socketChannel;
	}

	public int getConnectStatus() {
		if (mDone && mProcessThd != null && !mProcessThd.isAlive()) {
			exceptionHandler.removeMessages(0);
			mConnectStatus = ConnectHandler.DISCONNECT;
			mDone = false;
			if (mConnectHandler != null) {
				mConnectHandler.invokeStatusChanged(ConnectHandler.DISCONNECT);
				mConnectHandler.netConnectException(new ConnectionException("UnKnow Exception"));
			}
		}
		return mConnectStatus;
	}

	private Handler exceptionHandler = new Handler(Looper.getMainLooper()) {

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case 0:
			case 1:
				close();
				mConnectStatus = ConnectHandler.DISCONNECT;
				mDone = false;
				if (mConnectHandler != null) {
					mConnectHandler.invokeStatusChanged(ConnectHandler.DISCONNECT);
					mConnectHandler.netConnectException(new IOException("Unknow io exception!"));
				}
				break;
			}
		}

	};

}
