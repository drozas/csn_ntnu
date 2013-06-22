package no.ntnu.fp.net.separat.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import no.ntnu.fp.net.co.ConnectionImpl;
import no.ntnu.fp.net.co.Connection;

public class SmallClient {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Connection client = new ConnectionImpl(20000);
		try
		{
			InetAddress remoteAddress = InetAddress.getByName("localhost");
			
			client.connect(remoteAddress, 10000);
			String msg = client.receive();
		}catch (SocketTimeoutException e){
			System.out.println(e.getMessage());
		}catch (IOException e){
			System.out.println(e.getMessage());
		}

	}

}
