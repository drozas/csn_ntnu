package no.ntnu.fp.net.co;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import no.ntnu.fp.net.admin.Log;

/**
 * Helper class implementing asynchronous handling of incoming messages.  Objects
 * that want to receive incoming messages register themselves as MessageListener
 * to this class.  The MessageListener.messageReceived() method will be called
 * each time a new incoming message is received.
 * <br><br>
 * This interface is provided by the core "Fellesprosjekt" development team. 
 * This class is not needed to solve the KTN excerise, but might be used by
 * the "fellesprosjekt" application.
 * 
 * @author Thomas &Oslash;sterlie
 * @version 0.1
 */
public class ReceiveWorker extends Thread {

	private Connection aConnection;
	private List messageListenerList;
	
	/**
	 * 
	 * @param aConnection a Connection object that is connected with remote instance
	 */
	public ReceiveWorker(Connection aConnection) {
		this.aConnection = aConnection;
		messageListenerList = new ArrayList();
	}

	/**
	 * Register a new MessageListener object
	 * 
	 * @param listener the MessageListener to be registered
	 */
	public void addMessageListener(MessageListener listener) {
		messageListenerList.add(listener);
	}
	
	/**
	 * Unregister a MessageListener object
	 * 
	 * @param listener the MessageListener to be unregistered
	 */
	public void removeMessageListener(MessageListener listener) {
		messageListenerList.remove(listener);
	}
	
	/**
	 * The worker thread.
	 */
	public void run() {
	  boolean running = true;
		try {
			while (running) {
				String message = aConnection.receive();
				Iterator iterator = messageListenerList.iterator();
				while (iterator.hasNext()) {
					MessageListener listener = (MessageListener)iterator.next();
					listener.messageReceived(message);
				}
			}
		} 
		catch (EOFException e){
		  if ("Disconnect requested!".equals(e.getMessage())) {
		    try {
		      aConnection.close();
		      running = false;
		    } catch (IOException exp) {
		    // Ignored.
		    }
		  }
		  else {
		    Log.writeToLog("ReceiveWorker stopped. Reason: "+e.getMessage(),"ReceiveWorker");
		    //stop the receiver-thread:
		    running = false;
		  }
		}
		catch(Exception e) {
			e.printStackTrace(); //TODO: better handling of exceptions
		}
	}
	
}
