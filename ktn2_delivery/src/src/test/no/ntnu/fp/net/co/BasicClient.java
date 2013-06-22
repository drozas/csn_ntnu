package test.no.ntnu.fp.net.co;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import no.ntnu.fp.net.co.Connection;
import no.ntnu.fp.net.co.ConnectionImpl;

import org.junit.Before;
import org.junit.Test;

import test.infastructure.BasicClientServer;

public class BasicClient {

	Process server;
	Connection client;
	BufferedReader serverMessages;
	
	@Before
	public void startServer() {
		Runtime home = Runtime.getRuntime();
		String uglyHack = ".:bin:lib/*"; // sorry about that. Add your output dir here
		String command = "java -cp " + uglyHack + " " + BasicClientServer.class.getCanonicalName();
		try {
			System.out.println("running command: " + command);
			server = home.exec(command);
			
			serverMessages = new BufferedReader(new InputStreamReader(server.getInputStream()));
			
			System.out.println("Server says: " + serverMessages.readLine());
			// give the server time to start
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				fail("SetUp: Unable to sleep");
			}
			
			System.out.println("Server says: " + serverMessages.readLine());
	
		} catch (IOException e) {
			System.err.println("Could not run " + command + ". Pleace check your setup");
			fail("Could not run " + command + ". Pleace check your setup");
		}
	}
	
	@Test
	public void testConnectClose() {
		connect();
		close();
	}
	
	@Test
	public void testConnectSendClose() {
		connect();
		sendSomeData();
		close();
	}
	
	@Test
	public void testConnectSendRecieveClose() {
		connect();
		sendSomeData();
		recieveSomeData();
		close();
	}
	
	/*
	 * Support code
	 */
	
	public void connect() {
		client = new ConnectionImpl(9000);
		String method = "connect";
		try {
			client.connect(InetAddress.getByName("localhost"), 10000);
		} catch (SocketTimeoutException e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		} catch (UnknownHostException e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		} catch (IOException e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		} catch (Exception e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		}
	}
	
	public void close() {
		String method = "close";
		try {
			client.close();
		} catch (SocketTimeoutException e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		} catch (IOException e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		}
	}
	
	public void sendSomeData() {
		String method = "sendSomeData";
		try {
			client.send("Some data");
		} catch (java.net.ConnectException e) {
			
		} catch (IOException e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		} catch (Exception e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		}
	}
	
	public void recieveSomeData() {
		String method = "recieveSomeData";
		try {
			assertEquals("correct", client.receive());
		} catch (ConnectException e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		} catch (IOException e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		} catch (Exception e) {
			fail(method + ": " + e.getClass().getCanonicalName() + ": " + e.getMessage());
		}
	}
}
