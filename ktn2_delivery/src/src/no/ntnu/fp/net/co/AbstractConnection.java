/*
 * Created on Sep 20, 2005
 *
 */
package no.ntnu.fp.net.co;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;

import no.ntnu.fp.net.admin.Log;
import no.ntnu.fp.net.cl.ClException;
import no.ntnu.fp.net.cl.ClSocket;
import no.ntnu.fp.net.cl.KtnDatagram;

/**
 * A partial realisation of the Connection interface.
 *
 * This class implements some basic functionality and provides utility
 * methods that can be useful in completing the implementation as a subclass
 * of this class.
 *
 * @author Stein J. Nordbø, Sebjørn S. Birkeland
 */
public abstract class AbstractConnection implements Connection {

  /**
   * Timeout for receives. Setting this too high can cause slow operation in
   * the case of many errors, while setting it too low can cause failure of
   * operation because of the delays in A2. 4800 milliseconds seems to be a
   * reasonable value (6*RETRANSMIT) for a total of 7 transmits.
   */
  protected final static int TIMEOUT = 4800;

  /**
   * Time between retransmissions. When setting this, also consider setting
   * {@link #TIMEOUT}: There has to be time for a few retransmissions within
   * the timeout. Setting RETRANSMIT too low will result in a lot of traffic
   * and duplicate packets because of the delays in A2. Note: Low values of
   * RETRANSMIT will generate duplicate packets independently of the setting
   * for duplicate packets in the configuration for A2!
   */
  protected final static int RETRANSMIT = 800;

  /** To prevent more than one thread to concurrently execute certain parts
   *  of doReceive(). DO NOT alter the value of this variable unless you
   *  KNOW what you are doing, as this may hang the implementation or cause
   *  BindExceptions in A2 - you are warned!
   */
  private boolean receiveRunning;

  /**
   * Unhandled internal packets. Packets are put in this queue when waiting
   * for a data packet and receiving an internal packet (e.g. ACK).
   */
  private List internalQueue;

  /**
   * Unhandled external (application-destined) packets. Packets are put in
   * this queue when waiting for internal packets (e.g. ACK) and receiving
   * a data packet.
   */
  private List externalQueue;

  /**
   * Identifies the state of the connection. Takes values defined by the
   * constants CLOSED, LISTEN, SYN_SENT, etc.
   */
  protected int state;

  /*
   * State mnemonic constants. See Tanenbaum for explanation.
   */
  /** State: The connection is closed. */
  protected static final int CLOSED = 0;
  /** State: Listening for a new connection (server). */
  protected static final int LISTEN = 1;
  /** State: Sent first connect packet (client). */
  protected static final int SYN_SENT = 2;
  /** State: First connect packet received (server). */
  protected static final int SYN_RCVD = 3;
  /** State: Connection established - send/receive possible. */
  protected static final int ESTABLISHED = 4;
  /** State: Disconnection in progress. */
  protected static final int FIN_WAIT_1 = 5;
  /** State: Disconnection in progress. */
  protected static final int FIN_WAIT_2 = 6;
  /** State: Disconnection in progress. */
  protected static final int TIME_WAIT = 7;
  /** State: Disconnection in progress, wait for application to close(). */
  protected static final int CLOSE_WAIT = 8;
  /** State: Disconnection in progress, wait for last ACK. */
  protected static final int LAST_ACK = 9;

  /** Local and remote ip-address. */
  protected String myAddress, remoteAddress;
  /** Local and remote port number. */
  protected int myPort, remotePort;
  /** Reference to the last packet received. */
  protected KtnDatagram lastPacketReceived;
  /** Reference to the last packet sent. */
  protected KtnDatagram lastPacketSent;
  /** The current sequence number used in packets to be sent. */
  protected int sequenceNo;
  /** The sequence number used in disconnection. */
  protected int disconnectSeqNo;
  /** If a FIN has been received, it is stored in disconnectRequest. */
  protected KtnDatagram disconnectRequest;
  /** True if a received FIN was successfully ACK'ed. */
  protected boolean ackedFin;

  /** Initialise variables to default values. */
  public AbstractConnection() {
    internalQueue = Collections.synchronizedList(new LinkedList());
    externalQueue = Collections.synchronizedList(new LinkedList());
    receiveRunning = false;
    sequenceNo = (int) (Math.random()*10000 + 1);
    disconnectRequest = null;
    ackedFin = false;
    lastPacketSent = null;
    lastPacketReceived = null;
    state = CLOSED;
  }

  /**
   * Construct a datagram with given flags and payload.
   * <br><br>
   * Note: This method *depends* on the values of `remotePort',
   *       `remoteAddress', `myPort', `myAddress' and `sequenceNo'. Failing
   *       to set these before calling this method causes undefined
   *       behaviour. Also note that if you want to set values to something
   *       else than the default, you must construct the packet manually or
   *       alter the returned object.
   *
   * @param flags Flags for the packet, see the constants in
   *              {@link KtnDatagram}. Set this to -1 (no flag) for data
   *              packets.
   * @param payload Payload for packet (if data packet) or null
   * @return Initialised datagram.
   */
  protected KtnDatagram constructPacket(int flags, String payload) {
    // Update sequence number
    sequenceNo++;

    KtnDatagram packet = new KtnDatagram();
    packet.setDest_port(remotePort);
    packet.setDest_addr(remoteAddress);
    packet.setSrc_addr(myAddress);
    packet.setSrc_port(myPort);
    packet.setFlags(flags);
    packet.setSeq_nr(sequenceNo);
    packet.setPayload(payload);

    return packet;
  }

  /**
   * Send a packet and wait for ack in one operation. This method employs
   * a timer that resends the packet until an ack is received (or the timeout
   * is reached).
   * <ol>
   * <li> Start a timer used to resend the packet with a specified interval,
   *      and that immediately starts trying (sending the first packet as
   *      well as the retransmits).
   * <li> Wait for the ACK using receiveAck().
   * <li> Cancel the timer.
   * <li> Return the ACK-packet.
   * </ol>
   *
   * @param packet - the packet to be sent.
   * @return The ack-package received for the send packet (NB: ack can
   *         be null - see receiveAck())
   *
   * @throws IOException - thrown if unable to send packet.
   *
   * @see #receiveAck
   * @see no.ntnu.fp.net.cl.ClSocket#send(KtnDatagram)
   */
  protected synchronized KtnDatagram sendPacket(KtnDatagram packet)
    throws IOException {

    lastPacketSent = packet;

    // Create a timer that sends the packet and retransmits every
    // RETRANSMIT milliseconds until cancelled.
    Timer timer = new Timer();
    timer.scheduleAtFixedRate(new SendTimer(new ClSocket(), remoteAddress,
					    packet),
			      0, RETRANSMIT);

    KtnDatagram ack = receiveAck();
    timer.cancel();

    return ack;
  }

  /**
   * Send an ack for the given packet.
   * <ol>
   * <li> Generate a new ack packet based on the packet given as input
   * <li> Try to send the ack
   * <li> Catch a ConnectException if the sending failed - and write this to
   *      the {@link Log}.
   * </ol>
   *
   * If the send fails, there is no retransmission of the ack-packet: Just
   * wait for the other side to retransmit the original packet.
   *
   * @param data Packet to be sent.
   * @param flag ACK-flag to be set in the packet (should be Ktn_Datagram.ACK
   *             or Ktn_Datagram.SYN_ACK)
   *
   * @throws ConnectException Thrown if unable to send packet.
   *
   * @see no.ntnu.fp.net.cl.ClSocket#send(KtnDatagram)
   */
  protected void sendAck(KtnDatagram data, int flag)
    throws IOException, ConnectException {

    int tries = 3;
    boolean sent = false;

    // Since this is a new packet, increment the sequence number.
    sequenceNo++;
    // Construct the ACK (since many fields are initialised from `data',
    // constructPacket is not used.
    KtnDatagram datagram = new KtnDatagram();
    datagram.setSeq_nr(sequenceNo);
    datagram.setDest_addr(data.getSrc_addr());
    datagram.setSrc_addr(myAddress);
    datagram.setDest_port(data.getSrc_port());
    datagram.setSrc_port(myPort);
    datagram.setFlags(flag);
    datagram.setAck(data.getSeq_nr());

    // Send the ack, trying at most `tries' times.
    lastPacketSent = datagram;
    Log.writeToLog(datagram,"Sending Ack: "+datagram.getAck(),
		   "AbstractConnection");

    do {
      try {
	new ClSocket().send(datagram);
	sent = true;
      }
      catch (ClException e){
	Log.writeToLog(datagram, "CLException: Could not establish a " +
		       "connection to the specified address/port!",
		       "AbstractConnection");
      }
      catch (ConnectException e){
	// Silently ignore: Maybe recipient was processing and didn't
	// manage to call receiveAck() before we were ready to send.
	try{Thread.sleep(250);}
	catch(InterruptedException ex) {}
      }
    } while (!sent && (tries-- > 0));

    if (!sent) {
      sequenceNo--;
      throw new ConnectException("Unable to send ACK.");
    }
  }

  /**
   * Wait for an ACK or SYN_ACK.
   *
   * Blocks until the ack is recieved. Returns null if no ack recieved after
   * the specified time.
   *
   * @return The KtnDatagram recieved as an ACK (can be null).
   *
   * @see #receive
   *
   * @throws IOException
   */
  protected KtnDatagram receiveAck() throws IOException{
    KtnDatagram ack;

    do {
      ack = doReceive(true);

      if ((ack != null) && !((ack.getFlags() == KtnDatagram.ACK) ||
			     (ack.getFlags() == KtnDatagram.SYN_ACK))) {
	Log.writeToLog(ack, "Got this packet while waiting for ACK, queuing.",
		       "AbstractConnection");
	internalQueue.add(ack);
      } else
	break;
    } while (true);

    return ack;
  }

  /**
   * Receive a packet.
   * <br><br>
   * Listens for incoming packets, checks for errors and determines what to
   * do with the packet based on the content of the packet and the result from
   * the check for errors.
   * <br><br>
   * If the datagram is destined for the application, it is returned if
   * parameter `internal' is false, and vice versa for the internal usage.
   * A received packet that is not returned is kept until return is the
   * appropriate action.
   * <br><br>
   * Note: Use this for receiving! Do not attempt to use A2 directly without
   * implementing something like this! Do not tinker with this code unless you
   * KNOW what you are doing!
   * <br><br>
   * In short:
   * <ol>
   * <li> Make sure no more than one thread attempts to listen at the same
   *      time, and make sure packets are delivered to the right thread
   *      (avoiding return of protocol packet (SYN, ACK, etc.) to application).
   * <li> Enforce state machine.
   * <li> Check validity of packet: If the datagram is corrupted or not
   *      expected, discard it and wait for a new packet.
   * <li> Handle passive close (other side sends FIN). This is signaled by
   *      an EOFException to the application. The FIN packet is ACK'ed, and
   *      saved in `disconnectRequest'; if the ACK was successfully sent,
   *      ackedFIN is set to `true'. `disconnectSeqNo' is set to the sequence
   *      number of the FIN, and state is set to CLOSE_WAIT.
   * <li> ACK incoming data packets.
   * </ol>
   *
   * @param internal True if caller wants an internal packet (SYN/ACK/FIN).
   * @return The received packet.
   * @throws EOFException If peer attempts to disconnect (passive close, this
   *         should be handled in the application by calling close() on the
   *         connection instance that threw the exception to generate FIN for
   *         the other side.
   *
   * @see no.ntnu.fp.net.co.Connection#receive()
   * @see no.ntnu.fp.net.cl.ClSocket#receive
   * @see #isValid
   */
  protected KtnDatagram doReceive(boolean internal)
    throws ConnectException,IOException,EOFException {

    // Aquire monitor for this instance, and see if another thread runs
    // receive on our port. If so, see if that thread gets the packet
    // that was meant for us.
	synchronized (this) {
		long before, after;
	
		before = System.currentTimeMillis();
		while (receiveRunning) {
			try {
				if (internal) wait(TIMEOUT);  // wait with timeout
				else wait();                  // wait (potentially) forever
			}
			catch (InterruptedException e) { /* do nothing */ }
			after = System.currentTimeMillis();
	
			// If a packet for us has arrived, return it.
			if (internal) {
				// Case 1: Internal (protocol) caller, check internalQueue
				if (!internalQueue.isEmpty()) { return (KtnDatagram)internalQueue.remove(0); }
				// If no packet arrived, see if timeout has expired.
				else if ((after - before) > TIMEOUT) { return null; }
			} 
			else {
				// Case 2: Non-internal (application) caller, check externalQueue
				if (!externalQueue.isEmpty()) {
					return (KtnDatagram)externalQueue.remove(0);
				}
			}
		}
	
		// When we get here, this thread has not got its packet, and it's
		// allowed to enter the listening part of doReceive().
		receiveRunning = true;
	}

    Log.writeToLog("Waiting for incoming packet in doReceive()",
		   "AbstractConnection");

    KtnDatagram packet;
    // All internal receiving, except for listening for new connections, is to
    // be done using a timeout.
    if (internal && (state != LISTEN)) {
    	InternalReceiver receiver = new InternalReceiver(myPort);
    	receiver.start();
    	// wait at most TIMEOUT millis for thread to die
    	try { receiver.join(TIMEOUT); }
    	catch (InterruptedException e) { /* do nothing */ }
    	
    	receiver.stopReceive();
    	packet = receiver.getPacket();
    	if (packet == null) {
        // No packet was received
    		synchronized (this) {
    			receiveRunning = false;
    			notify();
    			return null;
    		}
    	}
    } 
    else {
		// External receive and LISTEN has no timeout: Wait until we get a packet
		packet = new ClSocket().receive(myPort);
    }

    Log.writeToLog(packet, "Processing packet.", "AbstractConnection");

    // Enforce state machine
    if ((state == LISTEN) || (state == SYN_RCVD)) {
    	// Handling of connection sequence, drop all packets that are not
    	// to be received during a server-side connect.
    	if ((packet.getFlags() != KtnDatagram.SYN) && (packet.getFlags() != KtnDatagram.ACK)) {
    		Log.writeToLog(packet, "Unable to handle this packet during " +
    		"connection, discarding and re-receiving.",
    		"AbstractConnection");
		
			synchronized (this) {
				receiveRunning = false;
				notify();
			}
			return doReceive(internal);     
    	}
    	else {
			if (!internal) {
				synchronized (this) {
					internalQueue.add(packet);
					receiveRunning = false;
			    	notify();
			  	}
			  	return doReceive(internal);
			} 
			else {
				synchronized (this) {
				    receiveRunning = false;
				    notify();
				}
				return packet;
			}
    	}
    }
    
    else if (state == SYN_SENT) {
    	// Handling of connection sequence, drop all packets that are not
    	// to be received during a client-side connect.
    	if (packet.getFlags() != KtnDatagram.SYN_ACK) {
    		Log.writeToLog(packet, "Unable to handle this packet during " +
		    "connection, discarding and re-receiving.",
    		"AbstractConnection");

			synchronized (this) {
				receiveRunning = false;
				notify();
			}
			return doReceive(internal);
    	}
    	else {
			if (!internal) {
				synchronized (this) {
				    internalQueue.add(packet);
				    receiveRunning = false;
				    notify();
				}
				return doReceive(internal);
			} 
			else {
				synchronized (this) {
				    receiveRunning = false;
				    notify();
				}
				return packet;
			}
    	}
    }
    
    else if ((state == FIN_WAIT_1) || (state == FIN_WAIT_2) ||
	       (state == LAST_ACK)) {
    	// Handle disconnection, drop all packets that are not
    	// to be received during disconnect.
    	if ((packet.getFlags() != KtnDatagram.FIN) &&
    			(packet.getFlags() != KtnDatagram.ACK)) {
    		Log.writeToLog(packet, "Unable to handle this packet while "+
    		"disconnecting, re-receiving.", "CONNECTION");
	
    		synchronized (this) {
    			receiveRunning = false;
    			notify();
    		}
    		return doReceive(internal);
	    } 
    	else {
    		if (!internal) {
    			synchronized (this) {
    				internalQueue.add(packet);
				    receiveRunning = false;
				    notify();
    			}
    			return doReceive(internal);
    		} 
    		else {
    			synchronized (this) {
    				receiveRunning = false;
				    notify();
    			}
    			return packet;
			}
      	}
    } 
    
    else if (state == ESTABLISHED) {
    	// Handle normal traffic, check for errors.
    	if (!isValid(packet)) {
			// Packet has errors, discard and re-receive
			synchronized (this) {
			  receiveRunning = false;
			  notify();
			}
			return doReceive(internal);
    	}

    	// Handle ACKs differently than data packets.
    	if (packet.getFlags() == KtnDatagram.ACK) {
			if (packet.getAck() == lastPacketSent.getSeq_nr()) {
				Log.writeToLog(packet,"Received Ack for sequence number: "+
				packet.getAck(), "AbstractConnection");
				lastPacketReceived = packet;
			}
			else if (packet.getAck() == disconnectSeqNo) {
				Log.writeToLog(packet, "ACK (for DISCONNECT) received! Closing " +
				"connection!","AbstractConnection");
				lastPacketReceived = packet;
				state = CLOSED;
				Log.writeToLog("State: CLOSED","AbstractConnection");
			}
			else {
				Log.writeToLog(packet,"ERROR! Received Ack with wrong ackno.: "+
				packet.getAck()+". Packet discarded!",
				"AbstractConnection");
			}
			
			if (!internal) {
				// External receive, got ack; continue to listen.
				synchronized (this) {
				    internalQueue.add(packet);
				    receiveRunning = false;
				    notify();
				}
				return doReceive(internal);
			} 
			else {
				// Internal receive, got ack; fine, we're finished
				synchronized (this) {
					receiveRunning = false;
					notify();
				}
			}
			return packet;
    	}

    	// Passive close, ACK the FIN and alert application.
    	if (packet.getFlags() == KtnDatagram.FIN) {
    		Log.writeToLog(packet, "FIN received: Passive close",
		    "AbstractConnection");
			disconnectRequest = packet;
			lastPacketReceived = packet;
			disconnectSeqNo = packet.getSeq_nr();
			state = CLOSE_WAIT;
			Log.writeToLog("State: CLOSE_WAIT", "AbstractConnection");

			try {
				sendAck(packet, KtnDatagram.ACK);
				ackedFin = true;
			}
			catch (ConnectException e){
				Log.writeToLog(packet,"ConnectException: could not send ack for " +
				"this packet! Packet must be discarded!",
				"AbstractConnection");
			}
		
			synchronized (this) {
				internalQueue.add(packet);
				receiveRunning = false;
				notify();
			}
			// This will, eventually, reach the application, either it is
			// thrown directly or propagating through sendPacket(), etc.
			// doReceive() is always called in an application thread.
			throw new EOFException("Disconnect requested!");
    	}
    	
    	// Non-ACK in ESTABLISHED state, `internal' decides what to do.
    	try {
			sendAck(packet, KtnDatagram.ACK);
			lastPacketReceived = packet;
			Log.writeToLog(packet, "Packet successfully received.",
		       "AbstractConnection");
			if (!internal) {
				synchronized (this) {
				    receiveRunning = false;
				    notify();
				}
				return packet;
			} 
			else {
				// Got data packet while listening for internal packet,
				// make the data available for the application.
				synchronized (this) {
				    externalQueue.add(packet);
				    receiveRunning = false;
				    notify();
				}
				return doReceive(internal);
			}
    	}
    	catch (ConnectException e){
    		Log.writeToLog(packet,"ConnectException: could not send ack for " +
		       "this packet! Packet must be discarded!",
		       "AbstractConnection");

			synchronized (this) {
				receiveRunning = false;
			  	notify();
			}
			return doReceive(internal);
    	}
    }
    else {
    	Log.writeToLog(packet,"ERROR! Could not handle this packet in the " +
		     "current state!", "AbstractConnection");

    	synchronized (this) {
			receiveRunning = false;
			notify();
    	}
    	return doReceive(internal);
    }
  }

  /**
   * Test a packet for transmission errors. This function is only called in
   * the ESTABLISHED state.
   *
   * @param packet Packet to test.
   * @return true if packet is free of errors, false otherwise.
   */
  protected abstract boolean isValid(KtnDatagram packet);

}
