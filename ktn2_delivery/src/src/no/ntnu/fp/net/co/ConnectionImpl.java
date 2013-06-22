/*
 * Created on Oct 27, 2004
 *
 */
package no.ntnu.fp.net.co;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import no.ntnu.fp.net.admin.Log;
import no.ntnu.fp.net.cl.ClException;
import no.ntnu.fp.net.cl.ClSocket;
import no.ntnu.fp.net.cl.KtnDatagram;

/**
 *
 * Implementation of the Connection-interface.
 * <br><br>
 * This class implements the behaviour in the methods specified in the
 * interface {@link Connection} over the unreliable, connectionless network
 * realised in {@link ClSocket}. The base class, {@link AbstractConnection}
 * implements some of the functionality, leaving message passing and error
 * handling to this implementation.
 *
 * @author Sebj�rn Birkeland and Stein Jakob Nordb�
 *
 * @see no.ntnu.fp.net.co.Connection
 * @see no.ntnu.fp.net.cl.ClSocket
 */
public class ConnectionImpl extends AbstractConnection {
        
        private boolean closed = false;
        
        /** Keeps track of the used ports for each server port. */
        private static Map usedPorts = Collections.synchronizedMap(new HashMap());
        
        private static Random generator = new Random();
        
        /**
         * Initialise initial sequence number and setup state machine.
         *
         * @param myPort - the local port to associate with this connection
         */
        public ConnectionImpl(int myPort) {
                super();
                
                try {
                        this.myPort = myPort;
                        usedPorts.put(new Integer(myPort), new Boolean(true));
                        
                        this.myAddress = InetAddress.getLocalHost().getHostAddress();
                } catch(UnknownHostException e) {
                        System.out.println(e.getMessage());
                }
        }
        
        
        /**
         * Establish a connection to a remote location.
         *
         * @param remoteAddress - the remote IP-address to connect to
         * @param remotePort - the remote portnumber to connect to
         *
         * @throws IOException If connection could not be made.
         *
         * @see AbstractConnection#sendPacket(KtnDatagram)
         */
        public void connect(InetAddress remoteAddress, int remotePort)
        throws IOException, SocketTimeoutException {
                
                //Store/set properties of the remote host
                this.remoteAddress = remoteAddress.getHostAddress();
                this.remotePort = remotePort;
                
                //Create a syn packate and send it to remote host
                KtnDatagram syn = constructPacket(KtnDatagram.SYN, null);               
                lastPacketSent = syn;
                Timer timer = new Timer();
                timer.scheduleAtFixedRate(new SendTimer(new ClSocket(), this.remoteAddress, syn), 0, RETRANSMIT);
                this.state = SYN_SENT;
                
                //Wait for the acknowledgement and restransmit syn packet after RETRANSMIT miliseconds
                KtnDatagram syn_ack = receiveAck();
                timer.cancel();
                
                //Syn_ack is null - there is something wrong on "the other side"
                if (syn_ack == null) {
                        throw new SocketTimeoutException();
                }
                
                //Store port for the new negotiated conncetion
                int newRemotePort = syn_ack.getSrc_port();
                
                
                //syn = constructPacket(KtnDatagram.ACK, null);
                //this is going to send it to the new port
                //because is taking the value from the pkg, we have to send it to the old one
                //So we set the src port of the package to the old one
                syn_ack.setSrc_port(remotePort);
                sendAck(syn_ack, KtnDatagram.ACK);
                
                this.remotePort = newRemotePort;
                this.state = ESTABLISHED;
        }
        
        /**
         * Listen for, and accept, incoming connections.
         *
         * @return A new ConnectionImpl-object representing the new connection.
         */
        public Connection accept() throws IOException, SocketTimeoutException {
                
                int newPort;
                
                //Transition to state Listen
                this.state = LISTEN;
                
                //Blocked infinitely til a SYN packet is received
                KtnDatagram syn = this.doReceive(true);
                
                //Store port and adress of the source
                int remotePort = syn.getSrc_port();
                String remoteAddress = syn.getSrc_addr();
                
                //Transition to state SYN_Received
                this.state = SYN_RCVD;
                
                //New port negotiation
                newPort = this.getAvailablePort();
                //newPort = 6666;
                usedPorts.put(new Integer(newPort), new Boolean(true));
                
                //Dest_port of the packet received will be src port once we call sendAck
                //We can add the new port here modifyng the packet just received, and send the SYN_ACK
                //syn.setDest_port(newPort);
                //this was not working!!!!!
                
                //solution: we have to change our port temporarily.
                int oldPort = this.myPort;	
                this.myPort = newPort;
                sendAck(syn, KtnDatagram.SYN_ACK);
                this.myPort = oldPort;
                //Wait for acknowledgement
                KtnDatagram ack = doReceive(true);
                
                //if we have not received ack, then we do not create the
                //new connection and we throw an exception
                if (ack == null) {
                	throw (new SocketTimeoutException()); 
                }
                
                //Successful - create a new connection then
                Connection newConnection = new ConnectionImpl(newPort);

                //Set properties of the new connection
                ((ConnectionImpl)newConnection).state = ESTABLISHED;
                ((ConnectionImpl)newConnection).remotePort = remotePort;
                ((ConnectionImpl)newConnection).remoteAddress = remoteAddress;

                //Change state
                this.state = CLOSED;

                //Return connection with the new negotiated port
                return newConnection;
        }
        
        /**
         * Send a message from the application.
         *
         * @param msg - the String to be sent.
         *
         * @throws ConnectException If no connection could be made.
         * @throws IOException If no ACK was received.
         *
         * @see AbstractConnection#sendPacket(KtnDatagram)
         * @see no.ntnu.fp.net.co.Connection#send(String)
         */
        public void send(String msg) throws ConnectException, IOException {
                
                if (state != ESTABLISHED) {
                        throw new ConnectException();
                }
         
                //Prepare the packets
                KtnDatagram packet = constructPacket(-1, msg);
                KtnDatagram ack;
                //Number of times we going to try to retransmit and receive an ACK if it does not
                //match to the packet that was sent
                int counter = 3;
                
                //Continue sending packets until the received ack corresponds to the packed that was sent
                do {
                        //send packet and wait for the acknowledgement, retransmit otherwise
                        //(it is is done automaticall inside sendPacket method)
                        ack = sendPacket(packet);
                        if (ack == null) {
                                throw new ConnectException();
                        }
                } while ((packet.getSeq_nr() != ack.getAck()) && (counter-- > 0));
                
                //If ve haven't received the correct ACK, even after
                //several attempts to send it, throws an exception
                if (packet.getSeq_nr() != ack.getAck()) {
                        throw new ConnectException();
                }
                
        }
        
        /**
         * Wait for incoming data.
         *
         * @return The received data's payload as a String.
         *
         * @see ConnectionImpl#doReceive
         */
        public String receive() throws ConnectException, IOException {

                if (state != ESTABLISHED) {
                        throw new ConnectException();
                }
                
                KtnDatagram packet = doReceive(false);
                
                if (packet == null) {
                        throw new ConnectException();
                }
                
                //Return the data
                if (packet.getPayload() instanceof String) {
                        return (String) packet.getPayload();
                } else {
                        return packet.getPayload().toString();
                }
        }
        
        /**
         * Close the connection.
         *Perform active and passive closing
         *
         * It does not implement Simultaneous close because doReceive do not 
         * distinguish between ack/fin while being in state fin_wait_1
         * So this is part of abstract connection
         * 
         * @see ConnectionImpl#doClose
         */
        public void close() throws IOException {
                
        	//both passive and active closing
        	//passive close
        	if(this.ackedFin)
        	{
        		//PASSIVE CLOSING
        		KtnDatagram fin = constructPacket(KtnDatagram.FIN, null);
        	 	//We change the state before sending because the previous state
	        	//is not going to affect send, but if we do not have the next state
	        	//this may affect the doReceive performed internally
        		this.state= this.LAST_ACK;
        		KtnDatagram ackFromFin = sendPacket(fin);
        		if(ackFromFin.getAck()==fin.getSeq_nr()){
        			//ack received is from our last fin
        			this.state= this.CLOSED;
        			this.ackedFin = false;
        		}

        	} else {
        	//ACTIVE CLOSING
	        //if(!ackedFin){	
	        	//active close we send a fin
	        	if (this.state==ESTABLISHED)
	        	{
		        	KtnDatagram fin = constructPacket(KtnDatagram.FIN, null);
		        	
		        	//We change the state before sending because the previous state
		        	//is not going to affect send, but if we do not have the next state
		        	//this may affect the doReceive performed internally
		       
		        	this.state = FIN_WAIT_1;
		    		KtnDatagram ackFromFin = sendPacket(fin);
		    		this.state = FIN_WAIT_2;
                                
                                if (ackFromFin == null) {
                                        throw new ConnectException();
                                }
                                
		    		if(ackFromFin.getAck()==this.lastPacketSent.getSeq_nr())
		    		{
		    			KtnDatagram fin2 = doReceive(true);
                                        sendAck(fin2, KtnDatagram.ACK);
                                        this.state = TIME_WAIT;
                                        KtnDatagram fin_aux;
                                        //Do timeout - try to receive until there are no more packages waiting
                                        do
                                        {
                                                //This takes care of more fin pckgs in case of our ack is lost
                                                fin_aux = doReceive(true);
                                                if (fin_aux!=null && fin_aux.getFlags()==KtnDatagram.FIN)
                                                        sendAck(fin_aux, KtnDatagram.ACK);
                                        } while(fin_aux!=null);

                                        this.state = CLOSED;
		    		}
	    		
	    		}
        	}	
        }
        
        /**
         * Test a packet for transmission errors. This function is only called in
         * the ESTABLISHED state.
         *
         * @param packet Packet to test.
         * @return true if packet is free of errors, false otherwise.
         */
        protected boolean isValid(KtnDatagram packet) {
                
                //Calculate the checksum
                long checksum = packet.calculateChecksum();

                //Return false if the chcecksum does not match
                if (checksum != packet.getChecksum()) {
                        return false;
                }
                
                //True if there is no lastPacketReceived or it is not a data packet
                if ((lastPacketReceived == null) || (packet.getAck() != -1)) {
                        return true;
                } 
                
                int lastSeq_nr = lastPacketReceived.getSeq_nr();
                
                //Dublicity - resent the acknowledgement
                if (lastSeq_nr == packet.getSeq_nr()) {
                        try {
                                sendAck(packet, KtnDatagram.ACK);
                        } catch (IOException ex) {
                                System.out.println("Failed to send the ACK to the received" +
                                    "message in isValid() method: " + ex.getMessage());
                        }
                        return false;
                }
                
                //Is not the consequent packet in transmition - discard it
                if ((lastSeq_nr + 1) != packet.getSeq_nr()) {
                        return false;
                }
                
                return true;
        }
        
        /**
         * Returns an available port between 1025 and 65535
         *
         * @return available port
         */
        
        private int getAvailablePort() {
                int portAux;
                
                do {
                        portAux = generator.nextInt(65535 - 1024) + 1024;
                } while(usedPorts.containsKey(new Integer(portAux)));
                
                return portAux;
        }
        
}
