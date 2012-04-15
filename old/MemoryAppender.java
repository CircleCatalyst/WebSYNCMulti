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
package nz.dataview.websyncclient;

import java.util.LinkedList;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

/**
 * A simple appender which just stores log events into memory, for retrieval
 * via RMI interface.  Uses a LinkedList as 'memory' - uses FIFO strategy.
 * 
 * @author  William Song
 * @version 1.0.2
 */
public class MemoryAppender extends AppenderSkeleton {
   /**
    * Our 'memory'!
    */
   private LinkedList<LoggingEvent> list;
   /**
    * The maximum size (number of logging events) allowed for our 'memory'.
    * Once this number is exceeded, calls to append() will result in the list
    * being emptied.
    */
   private int maxSize;
   
   /**
    * Constructor.
    */
   public MemoryAppender() {     
      list = new LinkedList<LoggingEvent>();
      maxSize = 10000; 
   }
   
   /**
    * Appends a new logging event into 'memory'.  If max size is reached, it will
    * simply reset the 'memory'.
    * @param e
    */
   public void append(LoggingEvent e) {    
      if (list.size() >= maxSize) {
	 list = null;
	 list = new LinkedList<LoggingEvent>();
      }
      list.add(e);
      
   }
   
   /**
    * Always returns false.
    * 
    * @return  false
    */
   public boolean requiresLayout() {  
      return false;
   }
   
   /**
    * Sets the LinkedList to null.  Does not re-initialise it.
    */
   public void close() {
      if (list != null && list.size() >= maxSize)
	 list = null;
   }
   
   /**
    * Just an alias for list.poll().
    * Retrieves the head element of the list, and removes the element from the list.
    * 
    * @return  the current head element of the list, null if list is empty
    */
   public LoggingEvent poll() {
      return list.poll();
   }
}
