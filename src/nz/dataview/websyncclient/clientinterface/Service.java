/**
 * WebSYNC Client Copyright 2007, 2008 Dataview Ltd
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software 
 * Foundation, either version 3 of the License, or (at your option) any later 
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * 
 * A copy of the GNU General Public License version 3 is included with this 
 * source distribution. Alternatively this licence can be viewed at 
 * <http://www.gnu.org/licenses/>
 */
package nz.dataview.websyncclient.clientinterface;

import java.io.IOException;

import java.net.UnknownHostException;

/**
 * Defines the methods avaiable for a remote JVM to call on the WebSYNCClient service.
 * 
 * @author  William Song
 * @version 1.0.2
 */
public interface Service {
   
   /**
    * Determines whether the service is currently running or not (sleeping).
    * 
    * @return  true  if the service is running, false otherwise
    * @throws  java.io.IOException
    */
   public boolean isRunningn() throws IOException;
   
   /**
    * Wakes up the service and starts its run immediately.
    * Does nothing if the service is running already.
    * 
    * @throws  java.io.IOException
    */
   public void startRunn() throws IOException;
   
   /**
    * Performs a test to see if the connection to the web server is up and running.
    * 
    * @return  true if connection is ok, false otherwise
    * @throws  java.net.UnknownHostException if DNS lookup fails
    * @throws  java.net.IOException	     if test hosts cannot be reached
    */
   public boolean testConnectionn() throws UnknownHostException, IOException;
   
   /**
    * Retrieves the latest log entries in one big String.
    * 
    * @return  a big String containing all the log messages
    * @throws  java.io.IOException
    */
   public String getLatestLogn() throws IOException;
   
   /**
    * Adds a new log entry.  Level represents one of the following:
    * <ul>
    *	 <li>FATAL</li>
    *	 <li>ERROR</li>
    *	 <li>WARN</li>
    *	 <li>INFO</li>
    *	 <li>DEBUG</li>
    *	 <li>TRACE</li>
    * </ul>
    * 
    * @param   level	the level of the log entry
    * @param   message	the message of the log entry
    * @return		true if the entry was added successfully, false otherwise
    * @throws  java.io.IOException
    */
   public boolean writeLogn(String level, String message) throws IOException;
   
   /**
    * Attempts to restart the JVM on which this program runs.
    * 
    * @throws java.io.IOException	  if something went wrong
    */
   public void restartn() throws IOException;

   /**
    * Send message
    *
    * @param message
    * @throws java.io.IOException
    */
   public void sendMessagen(String message) throws IOException;
}
