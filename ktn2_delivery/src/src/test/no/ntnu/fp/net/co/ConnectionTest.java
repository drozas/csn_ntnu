package test.no.ntnu.fp.net.co;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import no.ntnu.fp.net.admin.Settings;
import no.ntnu.fp.net.co.Connection;
import no.ntnu.fp.net.co.ConnectionImpl;

import org.junit.Test;

public class ConnectionTest {

	Connection client;
	Connection server;
	Connection establishedServer;
	
	InetAddress serverAddress;
	int serverPort ;
	
	
	@Test
	public void testConnect() {
		System.out.println("Running testConnect()**********************************************************");
		connect();
		cleanUp();
		
	}
	

	@Test
	public void testSend() {
		System.out.println("Running testSend()*****************************************************************");
		connect();
		ServerReceive serverReceive = new ServerReceive();
		Thread receiving = new Thread(serverReceive);
		receiving.start();
		
		try {
			client.send("Client sending something");
		} catch (ConnectException e) {
			fail("Unable to send: "+ e.getMessage());
		} catch (IOException e) {
			fail("Unable to send: "+ e.getMessage());
		}
		assertEquals("Data corrected during sending", "Client sending something", serverReceive.message);
		cleanUp();
	}

	@Test
	public void testReceive() {
		fail("Not yet implemented");
	}

	@Test
	public void testClose() {
		fail("Not yet implemented");
	}
	
	/* 
	 * Stuff used by tests
	 */
	
	
	
	private void initClient() {
		client = new ConnectionImpl(25);
	}
	
	/**
	 * Must always be run in separate thread
	 *
	 */
	private void fireServer() {
		try {
			server.accept();
		} catch (SocketTimeoutException e) {
			fail(e.getMessage());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}
	
	private void initServerAddress() {
		Settings settings = new Settings();
        serverPort = settings.getServerPort();
        try {
			serverAddress= InetAddress.getByName("localhost");
		} catch (UnknownHostException e) {
			fail("Unable to allocate localhost: Check java installation. " + e.getMessage());
		}
	}
	private void connect() {
		initServerAddress();
		Thread background = new Thread(new Server());
		background.start();
		initClient();
		
		
		try {
			client.connect(serverAddress, serverPort);
		} catch (SocketTimeoutException e) {
			fail("Connect failed: " + e.getMessage() );
		} catch (IOException e) {
			fail("Connect failed: " + e.getMessage() );
		}
	}
	private void cleanUp() {
		try {
			client.close();
			establishedServer.close();
			server.close();
		} catch (IOException e) {
			System.err.println("Unable to close:");
		}
	}
	private class Server implements Runnable{
		public void run() {
			server = new ConnectionImpl(serverPort);
			try {
				establishedServer = server.accept();
			} catch (SocketTimeoutException e) {
				fail("Server: " + e.getMessage());
				e.printStackTrace();
			} catch (IOException e) {
				fail("Server: " + e.getMessage());
				e.printStackTrace();
			}
			
		}
		
	}
	
	private class ServerReceive implements Runnable{

		public String message;

		public void run() {
			try {
				message = establishedServer.receive();
			} catch (ConnectException e) {
				fail("Server receiving: " + e.getMessage());
			} catch (IOException e) {
				fail("Server receiving: " + e.getMessage());
			}	
		}

	}

}
