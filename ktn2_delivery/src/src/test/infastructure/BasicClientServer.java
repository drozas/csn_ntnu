package test.infastructure;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import no.ntnu.fp.net.co.Connection;
import no.ntnu.fp.net.co.ConnectionImpl;

public class BasicClientServer {
	
	Connection newServer;
	
	public void start() {
		Connection me = new ConnectionImpl(10000);
		
		try {
			newServer = me.accept();
			System.out.println("Got connection...");
			
			(new Thread(new NewServer())).start();
		} catch (SocketTimeoutException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		} catch (IOException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		}
	}
	
	public static void main(String[] args) {
		System.out.println("Server started...");
		
		BasicClientServer me = new BasicClientServer();
		me.start();
	}
	
	private class NewServer implements Runnable {

		public void run() {
			Connection me = newServer;
			try {
				me.receive();
				me.send("correct");
			} catch (ConnectException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
}
