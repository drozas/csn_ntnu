package no.ntnu.fp.net.separat.server;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import no.ntnu.fp.net.co.ConnectionImpl;
import no.ntnu.fp.net.co.Connection;

public class SmallServer {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Connection server = new ConnectionImpl(10000);
		try
		{
		server.accept();
		}catch (SocketTimeoutException e){
			System.out.println(e.getMessage());
		}catch (IOException e){
			System.out.println(e.getMessage());
		}

	}

}
