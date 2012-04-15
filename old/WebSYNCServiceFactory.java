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

/**
 * Seemed like a good idea at the time...
 * 
 * @author  William Song
 * @version 1.0.2
 */
public class WebSYNCServiceFactory {
   
   /**
    * Retrieves a SOAP service which can be used to invoke remote methods via SOAP.
    * 
    * @param   endpoint	      the server which will serve the requests
    * @param   namespace      the namespace to be used throughout the XML messages
    * @param   key	      the key stringto be used for encryption/decryption
    * @param   schoolName     the name of this school
    * @param   schoolNumber   the MoE number of this school
    * @param   scheduleUpload the scheduled upload value
    * @param   processTime    the process time value
    * @return		      the SOAP-implemented object implementing the WebSYNCService interface
    */
   public static WebSYNCService getSoapService(String endpoint, String namespace, String key, String schoolName, String schoolNumber, String scheduleUpload, String processTime) {
      return new WebSYNCSOAPService(endpoint, namespace, key, schoolName, schoolNumber, scheduleUpload, processTime);
   }
}
