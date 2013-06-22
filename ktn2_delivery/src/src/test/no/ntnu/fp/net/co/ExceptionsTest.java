package test.no.ntnu.fp.net.co;

import java.io.IOException;
import java.net.ConnectException;

import no.ntnu.fp.net.co.ConnectionImpl;
import no.ntnu.fp.net.co.Connection;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExceptionsTest {

	@Test
	public void sendToNotInitialized() {
		Connection conn = new ConnectionImpl(25);
		try {
			conn.send("I am so 1337 I can send without beeing connected!!!");
                        fail("We are not connected. An Exception should be cast");
		} catch (ConnectException e) {
			assertNotNull(e);
		} catch (IOException e) {
			fail("We are not connected. This Exception is for when we get no ACK");
		}
	}
	
	@Test
	public void receiveToNotInitialized() {
		Connection conn = new ConnectionImpl(25);
		try {
			conn.receive();
                        fail("We are not connected. An Exception should be cast");
		} catch (ConnectException e) {
			assertNotNull(e);
		} catch (IOException e) {
			fail("We are not connected. This Exception is for when an I/O error occurs when sending");
		}
	}
}
