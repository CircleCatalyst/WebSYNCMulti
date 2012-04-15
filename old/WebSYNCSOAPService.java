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

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

//import javax.xml.namespace.QName;
//import javax.xml.rpc.ParameterMode;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPHeader;
//import javax.xml.soap.SOAPHeaderElement;
//import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import net.iharder.base64.Base64;

//import org.apache.axis.client.Call;
//import org.apache.axis.client.Service;
//import org.apache.axis.encoding.XMLType;
import org.apache.axis.message.RPCElement;
import org.apache.axis.message.SOAPBody;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.message.SOAPHeaderElement;
import org.apache.axis.message.SOAPBodyElement;
import org.apache.axis.soap.SOAPConnectionImpl;
import org.apache.log4j.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The SOAP implementation of the WebSYNCService interface.  Uses the Apache Axis
 * libraries (v1.4 final) to make the remote method invocations (see http://ws.apache.org/axis/).
 * 
 * Notes:<br />
 * There are some key differences in how some of the factory methods used here
 * behave in JRE 1.5 and JRE 1.6.  For example, with <code>MessageFactory.newInstance()</code>, 
 * the factory object returned is of class:<br />
 * <code>org.apache.axis.soap.MessageFactoryImpl</code> in JRE 1.5, whereas in JRE 1.6 something
 * different is produced.
 * Similarly, for <code>SOAPConnectionFactory.newInstance()</code>, the factory object returned
 * is of class:<br />
 * <code>org.apache.axis.soap.SOAPConnectionFactoryImpl</code> in JRE 1.5, whereas in JRE 1.6
 * something different is produced.
 * This is due to the System properties being set differently
 * on different JREs.  Why the properties are set differently is beyond me; however, we can
 * address the problem, by manually setting the appropriate properties on the command
 * line at runtime, by specifying the following command line options when we invoke
 * WebSYNC:<br />
 * For javax.xml.soap.MessageFactory:<br />
 * <code>-Djavax.xml.soap.MessageFactory=org.apache.axis.soap.MessageFactoryImpl</code>
 * and similarly for javax.xml.soap.SOAPConnectionFactory:<br />
 * <code>-Djavax.xml.soap.SOAPConnectionFactory=org.apache.axis.soap.SOAPConnectionFactoryImpl</code><br />
 * There, I just saved you 5 hours of debugging.
 * 
 * @author  William Song. Tidied by Tim
 * @version 1.1.0
 */
public class WebSYNCSOAPService implements WebSYNCService {

   /**
    * The logger!
    */
   private static Logger logger = Logger.getLogger(WebSYNCSOAPService.class);
   /**
    * The url representing the KN endpoint where the webservices lie.
    */
   private String endpoint;
   /**
    * The XML namespace to be used in the SOAP envelope etc.
    */
   private String namespace;
   /**
    * The name of the upload method to invoke on the server.
    */
   private String uploadMethod;
   /**
    * The name of the upload blocks method to invoke on the server.
    */
   private String uploadBlockMethod;
   /**
    * The name of the discard file method to invoke on the server
    */
   private String discardFileMethod;
   /**
    * The name of the get last upload block method on the server.
    */
   private String uploadGetLastBlockMethod;
   /**
    * The name of the download method to invoke on the server.
    */
   private String downloadMethod;
   /**
    * The name of the record log method to invoke on the server.
    */
   private String recordLogMethod;
   /**
    * The name of the update table raw method to invoke on the server.
    */
   private String updateTableRawMethod;
   /**
    * The name of the get upload status method to invoke on the server.
    */
   private String getUploadStatusMethod;
   /**
    * The name of the test upload method to invoke on the server.
    */
   private String testUploadMethod;
   /**
    * The name of the upload MD5 method.
    */
   private String uploadGetMD5Method;
   /**
    * The name of the download MD5 method.
    */
   private String downloadGetMD5Method;
   /**
    * The name of the batch result method
    */
   private String getBatchResultMethod;
   /**
    * The record ID of the last successfully downloaded file (-1 if none yet).
    */
   private int lastDownloadedFileId;
   /**
    * The filename of the last successfully downloaded file (<code>null</code> if none yet).
    */
   private String lastDownloadedFileName;
   /**
    * The key string used for encryption/decryption.
    */
   private String keyString;
   /**
    * The name of this school.
    */
   private String schoolName;
   /**
    * The MoE number of this school.
    */
   private String schoolNumber;
   /**
    * The scheduled upload time string.
    */
   private String scheduleUpload;
   /**
    * The process time string.
    */
   private String processTime;

   /**
    * Constructor.
    * 
    * @param   url	      the String representing the KN endpoint where the webservices lie
    * @param   ns	      the XML namespace to be used
    * @param   key	      the key string used for encryption/decryption
    * @param   schoolName     the name of this school
    * @param   schoolNumber   the MoE number of this school
    * @param   scheduleUpload the scheduled upload time
    * @param   processTime    the process time value
    */
   public WebSYNCSOAPService(String url, String ns, String key, String schoolName, String schoolNumber, String scheduleUpload, String processTime) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.constructor() with String: " + url + ", String: " + ns + ", String: " + key);
      }
      endpoint = url;
      namespace = ns;
      uploadMethod = "CopyUpToTable";
      uploadBlockMethod = "CopyUpToTablePaged";
      uploadGetLastBlockMethod = "UploadGetLastBlock";
      downloadMethod = "CopyDownFromTable";
      recordLogMethod = "recordLog";
      updateTableRawMethod = "doupdateTableRaw";
      getUploadStatusMethod = "getUploadStatusAssoc";
      testUploadMethod = "testUpload";
      uploadGetMD5Method = "UploadGetMD5";
      downloadGetMD5Method = "DownloadGetMD5";
      getBatchResultMethod = "getBatchResult";
      discardFileMethod = "discardFile";

      lastDownloadedFileId = -1;
      lastDownloadedFileName = null;

      this.schoolName = schoolName;
      this.schoolNumber = schoolNumber;
      this.scheduleUpload = scheduleUpload;
      this.processTime = processTime;

      keyString = key;

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.constructor()");
      }
   }

   /**
    * Uploads data in sequence.
    * Designed to be called in the following fashion:
    * <ul>
    *	 <li>binary data to be uploaded is read 2000 bytes at a time</li>
    *	 <li>doUpload should be called for each 2000 byte block</li>
    *	 <li>the first call should provide 'recId' of zero; this will create a new db record on the server</li>
    *	 <li>the first call will return the mysql last insert id</li>
    *	 <li>this last insert id should be used for subsequent calls, in order to append to the same record</li>
    *	 <li>once all binary data is uploaded, one last call, with an empty 'data' array, should be called to indicate end of data</li>
    * </ul>
    * 
    * The 'data' array should be provided as below:
    * <ul>
    *	 <li>binary data is read 2000 bytes at a time</li>
    *	 <li>this data is then base64 encoded (minus the line breaks)</li>
    *	 <li>this string is then split into an array of strings each 76 chars in length</li>
    * </ul>
    * 
    * See example in <code>Uploader.doUpload()</code> for the above algorithm in action.
    * 
    * @param   fileName the name of the file being uploaded
    * @param   data	the array of strings containing base64 encoded binary data of the file
    * @param   recId	the int representing which record to append to (must be 0 on the first call)
    * @return		the record id to be used in subsequent calls
    */
   public int doUpload(String fileName, String[] data, int recId) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doUpload() with String: " + fileName + ", String[]: " + data + "[" + data.length + "], int: " + recId);
      }
      int ret = 0;
      try {
         // we must do things manually in order to be able to encrypt/decrypt

         Object[] params = new Object[3];
         params[0] = fileName;
         params[1] = data;
         params[2] = recId;

         Document body=sendRequest(uploadMethod,params);
	 /*
         <ns1:CopyUpToTableResponse xmlns:ns1="mudder">
         <return xsi:type="xsd:string">1</return>
         </ns1:CopyUpToTableResponse>
          */         // parse it (painfully...)
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(uploadMethod + "Response") && node.hasChildNodes()) {
               NodeList list2 = node.getChildNodes();
               for (int j = 0; j < list2.getLength() && !successful; j++) {
                  Node node2 = list2.item(j);
                  if (node2.getNodeName().equals("return")) {
                     ret = Integer.parseInt(node2.getTextContent());
                     successful = true;
                  }
               }
            }
         }
         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + uploadMethod);
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + uploadMethod + "): " + e + ", " + e.getStackTrace());
         ret = -1;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doUpload() with int: " + ret);      // return the record identifier
      }
      return ret;
   }

   /**
    * Same as doUpload(), but supports partial uploads: an exception is thrown if something went wrong,
    * at which point doUploadGetLastBlock() can be called to determine which blockNumber
    * was last uploaded successfully, and perform any resends as necessary.
    *
    * @param   fileName the name of the file being uploaded
    * @param   data	the array of strings containing base64 encoded binary data of the file
    * @param   recId	the int representing which record to append to (must be 0 on the first call)
    * @param   blocknum	the block number to assign this block of data to upload
    * @return		the record id to be used in subsequent calls
    */
   public int doUploadBlock(String fileName, String[] data, int recId, int blockNum) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doUploadBlock() with String: " + fileName + ", String[]: " + data + "[" + data.length + "], int: " + recId + ", int: " + blockNum);
      }
      int ret = 0;
      try {

         Object[] params = new Object[4];
         params[0] = fileName;
         params[1] = data;
         params[2] = recId;
         params[3] = blockNum;

         Document body=sendRequest(uploadBlockMethod,params);
	 /*
         <ns1:CopyUpToTablePagedResponse xmlns:ns1="mudder">
         <return xsi:type="xsd:string">1</return>
         </ns1:CopyUpToTablePagedResponse>
          */         // parse it (painfully...)
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(uploadBlockMethod + "Response") && node.hasChildNodes()) {
               NodeList list2 = node.getChildNodes();
               for (int j = 0; j < list2.getLength() && !successful; j++) {
                  Node node2 = list2.item(j);
                  if (node2.getNodeName().equals("return")) {
                     ret = Integer.parseInt(node2.getTextContent());
                     successful = true;
                  }
               }
            }
         }
         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + uploadBlockMethod);
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + uploadBlockMethod + "): " + e + ", " + e.getStackTrace());
         ret = -1;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doUploadBlock() with int: " + ret);      // return the record identifier
      }
      return ret;
   }

   /**
    * Begs the LMS to mave mercy and ignore our past sins.
    *
    * Called when the file uploaded fine, but failed the MD5 check.
    * @param recId
    */
   public void discardFile(int recId) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.discardFile() with int: " + recId);
      }
      try {

         Object[] params = new Object[1];
         params[0] = recId;

         sendRequest(discardFileMethod,params);

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + discardFileMethod + "): " + e + ", " + e.getStackTrace());
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.discardFile()");      // return the record identifier
      }
   }

   /**
    * Retrieves the last successfully uploaded data block from doUpload() for
    * given file recId.
    * 
    * @param   recId the file recId as returned by doUpload()
    * @return	     the last successfully uploaded block, or -1 if error
    */
   public int doUploadGetLastBlock(int recId) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doUploadGetLastBlock() with int: " + recId);
      }
      int ret = 0;
      try {

         Object[] params = new Object[1];
         params[0] = recId;

         Document body=sendRequest(uploadGetLastBlockMethod,params);
	 /*
         <ns1:UploadGetLastBlockResponse xmlns:ns1="mudder">
         <return xsi:type="xsd:string">1</return>
         </ns1:UploadGetLastBlockResponse>
          */         // parse it (painfully...)
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(uploadGetLastBlockMethod + "Response") && node.hasChildNodes()) {
               NodeList list2 = node.getChildNodes();
               for (int j = 0; j < list2.getLength() && !successful; j++) {
                  Node node2 = list2.item(j);
                  if (node2.getNodeName().equals("return")) {
                     ret = Integer.parseInt(node2.getTextContent());
                     successful = true;
                  }
               }
            }
         }
         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + uploadGetLastBlockMethod);
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + uploadGetLastBlockMethod + "): " + e + ", " + e.getStackTrace());
         ret = -1;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doUploadGetLastBlock() with int: " + ret);
      }
      return ret;
   }

   /**
    * Downloads a file from the server.
    * Contrary to the <code>doUpload</code> method, this method will download the WHOLE file.
    * Returns null, if there are no files to download
    * Designed to be called in the following fashion:
    * <ul>
    *	 <li>while <code>byte[]</code> returned from this method is not null</li>
    *	 <li>save the <code>byte[]</code> to file</li>
    *	 <li>update the table to mark the downloaded file as 'downloaded' (<strong>this step is critical</strong>)</li>
    * </ul>
    * 
    * See example in <code>Downloader.doDownload()</code> for the above algorithm in action.
    * 
    * @return  array of bytes which should be written to file, null if no files to download
    */
   public byte[] doDownload() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doDownload()");
      }
      byte[] dataToReturn = null;

      try {
         // we must do things manually in order to be able to encrypt/decrypt

         Object[] params = new Object[1];
         params[0] = "dummy";

         Document body=sendRequest(downloadMethod,params);
	 /*
         <ns1:CopyDownFromTableResponse xmlns:ns1="mudder">
         <return xsi:type=":CopyDownFromTableReturn">
         <rec_id xsi:type="xsd:int">6</rec_id>
         <file xsi:type="SOAP-ENC:Array" SOAP-ENC:arrayType=":[92]">
         <item xsi:type="xsd:string">PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4NCjwhLS0gR2VuZXJhdGVkIGJ5</item>
         ...
         <item xsi:type="xsd:string">YXRlU21zU3R1ZGVudCB4bWxucz0idXJuOm56bDpnb3Z0OmVkdWNhdGluZzpkcmFmdDpjc2w6c2No</item>
         <item xsi:type="xsd:string">cGVyc29uPg0KPC9TbXNPbGVVcGRhdGVTbXNTdHVkZW50Pg0K</item>
         <item xsi:type="xsd:string"></item>
         </file>
         <name xsi:type="xsd:string">111100002-20061010090306-download.xml</name>
         <message xsi:type="xsd:string"></message>
         </return>
         </ns1:CopyDownFromTableResponse>
          * 
         (note: if there are no files to download, it will just return the empty 'message' element)
          */         // parse it (painfully...)
         boolean successful = false, recIdSuccess = false, nameSuccess = false, fileSuccess = false, messageSuccess = false;
         NodeList list = body.getChildNodes();
         ArrayList data = new ArrayList();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(downloadMethod + "Response") && node.hasChildNodes()) {
               NodeList list2 = node.getChildNodes();
               for (int j = 0; j < list2.getLength() && !successful; j++) {
                  Node node2 = list2.item(j);
                  if (node2.getNodeName().equals("return") && node2.hasChildNodes()) {
                     NodeList list3 = node2.getChildNodes();
                     for (int k = 0; k < list3.getLength(); k++) {
                        Node node3 = list3.item(k);
                        String nodeName = node3.getNodeName();
                        if (nodeName.equals("rec_id")) {
                           lastDownloadedFileId = Integer.parseInt(node3.getTextContent());
                           recIdSuccess = true;
                        } else if (nodeName.equals("name")) {
                           lastDownloadedFileName = node3.getTextContent();
                           nameSuccess = true;
                        } else if (nodeName.equals("message")) {
                           messageSuccess = true;
                        } else if (nodeName.equals("file") && node3.hasChildNodes()) {
                           NodeList list4 = node3.getChildNodes();
                           for (int l = 0; l < list4.getLength(); l++) {
                              Node node4 = list4.item(l);
                              if (node4.getNodeName().equals("item")) {
                                 data.add(node4.getTextContent());
                              }
                           }
                           fileSuccess = true;
                        }
                     }
                     successful = messageSuccess || (recIdSuccess && nameSuccess && fileSuccess);
                  }
               }
            }
         }
         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + downloadMethod);
         }
         String[] downloadedData = (String[]) data.toArray(new String[data.size()]);
         dataToReturn = Utilities.decodeFromDownload(downloadedData);

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + downloadMethod + "): " + e + ", " + e.getStackTrace());
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doDownload() with byte[]: " + dataToReturn + "[" + dataToReturn.length + "]");
      }
      return dataToReturn;
   }

   /**
    * Retrieves the record ID of the last successfully downloaded file.
    * 
    * @return  the ID if it exists, -1 otherwise
    */
   public int getDownloadedFileId() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.getDownloadedFileId()");
      }
      int ret = lastDownloadedFileId;

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.getDownloadedFileId() with int: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the filename of the last successfully downloaded file.
    * 
    * @return  the filename if it exists, null otherwise
    */
   public String getDownloadedFileName() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.getDownloadedFileName()");
      }
      String ret = lastDownloadedFileName;

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.getDownloadedFileName() with String: " + ret);
      }
      return ret;
   }

   /**
    * Sends a <code>String</code> containing a report, to be logged in websync_log on the server.
    * 
    * @param   report	the String containing the message to log
    * @param   success	true if the report is a success message, false otherwise
    * @param   where	what the current mechanism is eg MoE upload
    * @return		true if the report was logged ok, false otherwise
    */
   public boolean doRecordLog(String report, boolean success, String where) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doRecordLog() with String: " + report + ", boolean: " + success + ", String: " + where);
      }
      boolean ret = true;

      try {
         // we must do things manually in order to be able to encrypt/decrypt

         Object[] params = new Object[3];
         params[0] = report;
         params[1] = success;
         params[2] = where;

         Document body=sendRequest(recordLogMethod,params);
	 /*
         <ns1:recordLogResponse xmlns:ns1="namespace">
         <return xsi:nil="true" xsi:type="xsd:string"/>
         </ns1:recordLogResponse>
          */         // parse it (painfully...)
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(recordLogMethod + "Response") && node.hasChildNodes()) {
               NodeList list2 = node.getChildNodes();
               for (int j = 0; j < list2.getLength() && !successful; j++) {
                  Node node2 = list2.item(j);
                  if (node2.getNodeName().equals("return")) {
                     // don't care what its value is - the method doesn't return anything
                     successful = true;
                     break;
                  }
               }
            }
         }
         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + recordLogMethod);
         }
      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + recordLogMethod + "): " + e + ", " + e.getStackTrace());
         ret = false;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doRecordLog() with boolean: " + ret);
      }
      return ret;
   }

   /**
    * Sends a raw SQL insert/update statement to the server to execute.
    * 
    * @param   sql   the String containing the sql statement
    * @return	     true if executed successfully, false otherwise
    */
   public boolean doUpdateTableRaw(String sql) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doUpdateTableRaw() with String: " + sql);
      }
      boolean ret = true;

      try {
         // we must do things manually in order to be able to encrypt/decrypt

         Object[] params = new Object[1];
         params[0] = sql;

         Document body=sendRequest(updateTableRawMethod,params);
	 /*
         <ns1:doupdateTableRawResponse xmlns:ns1="namespace">
         <return xsi:type="xsd:string">1</return>
         </ns1:doupdateTableRawResponse>
          */         // parse it (painfully...)
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(updateTableRawMethod + "Response") && node.hasChildNodes()) {
               NodeList list2 = node.getChildNodes();
               for (int j = 0; j < list2.getLength(); j++) {
                  Node node2 = list2.item(j);
                  if (node2.getNodeName().equals("return")) {
                     // don't care what its value is - the method always returns 1
                     successful = true;
                     break;
                  }
               }
            }
         }
         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + updateTableRawMethod);
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + updateTableRawMethod + "): " + e + ", " + e.getStackTrace());
         ret = false;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doUpdateTableRaw() with boolean: " + ret);
      }
      return ret;
   }

   /**
    * Sends a keep alive message to the server.  Could be used for testing connection.
    * 
    * @return  true if successful, false otherwise
    */
   public boolean doKeepAlive() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doKeepAlive()");
      }
      boolean ret = true;

      try {
         // we must do things manually in order to be able to encrypt/decrypt

         Object[] params = new Object[3];
         params[0] = "Keep-alive";
         params[1] = true;
         params[2] = "MoE Upload";

         Document body=sendRequest(recordLogMethod,params);
	 /*
         <ns1:recordLogResponse xmlns:ns1="namespace">
         <return xsi:nil="true" xsi:type="xsd:string"/>
         </ns1:recordLogResponse>
          */         // parse it (painfully...)
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(recordLogMethod + "Response") && node.hasChildNodes()) {
               NodeList list2 = node.getChildNodes();
               for (int j = 0; j < list2.getLength() && !successful; j++) {
                  Node node2 = list2.item(j);
                  if (node2.getNodeName().equals("return")) {
                     // don't care what its value is - the method doesn't return anything
                     successful = true;
                     break;
                  }
               }
            }
         }
         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + recordLogMethod);
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + recordLogMethod + "): " + e + ", " + e.getStackTrace());
         ret = false;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doKeepAlive() with boolean: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the last time the service ran.
    * 
    * @return  the Date representing the last time the service ran, or null if error
    */
   public Date[] doGetUploadStatus() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doGetUploadStatus()");
      }
      Date date[] = new Date[2];

      try {
         // we must do things manually in order to be able to encrypt/decrypt

         Object[] params = new Object[1];
         params[0] = "dummy";

         Document body=sendRequest(getUploadStatusMethod,params);
	 /*
         <ns1:getUploadStatusAssocResponse xmlns:ns1="namespace">
         <return xsi:type=":getUploadStatusAssocReturn">
         <date xsi:type="xsd:string">2008-02-16 21:46:21</date>
         <active xsi:type="xsd:string">Y</active>
         </return>
         </ns1:getUploadStatusAssocResponse>
          */         // parse it (painfully...)
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(getUploadStatusMethod + "Response") && node.hasChildNodes()) {
               NodeList list2 = node.getChildNodes();
               for (int j = 0; j < list2.getLength() && !successful; j++) {
                  Node node2 = list2.item(j);
                  if (node2.getNodeName().equals("return") && node2.hasChildNodes()) {
                     NodeList list3 = node2.getChildNodes();
                     for (int k = 0; k < list3.getLength() && !successful; k++) {
                        Node node3 = list3.item(k);
                        String nodeName = node3.getNodeName();
                        if (nodeName.equals("date")) {
                           SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                           date[0] = format.parse(node3.getTextContent());
                        }
                        if (nodeName.equals("now")) {
                           successful = true;
                           SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                           date[1] = format.parse(node3.getTextContent());
                        }
                     // we are ignoring 'active' for the moment
                     }
                  }
               }
            }
         }
         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + getUploadStatusMethod);
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + getUploadStatusMethod + "): " + e + ", " + e.getStackTrace());
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doGetUploadStatus() with Date: " + date[0] + " and Now: " + date[1]);
      }
      return date;
   }

   /**
    * Performs a test transaction.  This is essentially just a simple file upload and download,
    * minus the complicated chunking of the normal upload method.
    * Ideally, some of the code in this method should go into the Uploader thread, or
    * indeed its own separate thread; however, time contraints do not allow me to do this.
    * 
    * @param   uploadDir      the upload dir
    * @param   downloadDir    the download dir
    * @return		      true if all went well, false otherwise
    */
   public boolean doTestUpload(String uploadDir, String downloadDir) {
      if (logger.isTraceEnabled()) {
         logger.trace("WebSYNCSOAPService.doTestUpload() with String: " + uploadDir + ", String: " + downloadDir);
      }
      boolean ret = false;

      try {
         String data = "Upload Directory location: " + uploadDir;
         data += "\nDownload Directory location: " + downloadDir + "\n";

         // write data to file (presumably to test that we can write)
         try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(uploadDir + "/TestUpload.txt"));
            bw.write(data);
            bw.close();
            logger.info("Created test upload file OK");
         } catch (IOException e) {
            logger.warn("Failed to write test upload file to: " + uploadDir + " (" + e + ")");
            return false;
         }

         Object[] params = new Object[1];
         params[0] = Base64.encodeBytes(data.getBytes(), Base64.DONT_BREAK_LINES);

         Document body=sendRequest(testUploadMethod,params);
	 /*
         <ns1:doTestUploadResponse xmlns:ns1="namespace">
         <return xsi:type=":doTestUploadResponse">data</return>
         </ns1:doTestUploadResponse>
          */
         boolean successful = false;
         String dataToWrite = null;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength(); i++) {
            Node curr = list.item(i);
            if (curr.getNodeName().contains(testUploadMethod + "Response") && curr.hasChildNodes()) {
               NodeList list2 = curr.getChildNodes();
               for (int j = 0; j < list2.getLength(); j++) {
                  Node curr2 = list2.item(j);
                  if (curr2.getNodeName().equals("return")) {
                     dataToWrite = curr2.getTextContent().trim();
                     successful = true;
                     break;
                  }
               }
            }
         }

         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + testUploadMethod);         // now try deleting the upload file	 
         }
         File file = new File(uploadDir + "/TestUpload.txt");
         if (!file.delete()) {
            logger.warn("Failed to delete test upload file: " + file.getName());
            return false;
         }
         logger.info("Deleted test upload file OK");

         try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(downloadDir + "/TestDownload.txt"));
            bw.write(dataToWrite);
            bw.close();
            ret = true;
         } catch (IOException e) {
            logger.warn("Failed to write test download file to: " + downloadDir + " (" + e + ")");
            return false;
         }

         if (ret) {
            File file2 = new File(downloadDir + "/TestDownload.txt");
            if (!file2.delete()) {
               logger.warn("Could not delete test transaction file: " + file2.getName());
            } else {
               logger.info("Deleted test download file OK");
               ret = true;
            }
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + testUploadMethod + "): " + e + ", " + e.getStackTrace());
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doTestUpload() boolean: " + ret);
      }
      return ret;
   }
   /**
    * Returns a String representation of this object.
    * 
    * @return  the string representation of this object
    */
   @Override
   public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append(getClass().getName());
      sb.append(" downloadMethod=[").append(this.downloadMethod).append("]");
      sb.append(" endpoint=[").append(this.endpoint).append("]");
      sb.append(" getUploadStatusMethod=[").append(this.getUploadStatusMethod).append("]");
      sb.append(" lastDownloadedFileId=[").append(this.lastDownloadedFileId).append("]");
      sb.append(" lastDownloadedFileName=[").append(this.lastDownloadedFileName).append("]");
      sb.append(" namespace=[").append(this.namespace).append("]");
      sb.append(" recordLogMethod=[").append(this.recordLogMethod).append("]");
      sb.append(" updateTableRawMethod=[").append(this.updateTableRawMethod).append("]");
      sb.append(" uploadMethod=[").append(this.uploadMethod).append("]");
      return sb.toString();
   }

   private Document sendRequest(String method, Object[] params)
   {
      Document body=null;
      try
      {
         // extract the SOAP body
         RPCElement rpcbody = new RPCElement(namespace, method, params);
         String soapbody = rpcbody.toString();
         if (logger.isDebugEnabled()) {
            logger.debug("Extracted SOAP body successfully");         // encrypt it
         }
         SOAPMessage smsg = encryptBody(soapbody, Client.WEBSYNC_VERSION, schoolName, schoolNumber, scheduleUpload, processTime);
         if (logger.isDebugEnabled()) {
            logger.debug("Encrypted and encoded SOAP body successfully, ready for invocation");         // invoke method	 
         }

         org.apache.axis.soap.SOAPConnectionFactoryImpl cf=new org.apache.axis.soap.SOAPConnectionFactoryImpl();
         SOAPConnectionImpl conn = (SOAPConnectionImpl) cf.createConnection();
         conn.setTimeout(new Integer(15000)); //15 seconds
         long startTime = System.currentTimeMillis();
         SOAPMessage response = conn.call(smsg, endpoint);
         long endTime = System.currentTimeMillis();
         if (logger.isDebugEnabled()) {
            logger.debug("Invocation took " + ((endTime - startTime) / 1000) + " seconds");
         }
         conn.close();
         if (logger.isDebugEnabled()) {
            logger.debug("Method " + method + " invoked, returned, connection closed");         // validate resonse
         }
         if (!validateDownload(response)) {
            String message = "Response data could not be validated";
            logger.warn(message);
            throw new Exception(message);
         } else if (logger.isDebugEnabled()) {
            logger.debug("Response data validated OK");
         }

         // decrypt the response
         body = decryptBody(response);
         if (logger.isDebugEnabled()) {
            logger.debug("Decoded and decrypted SOAP body successfully, ready for parsing");         // the message is in the following format:
         }
      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + method + "): " + e + ", " + e.getStackTrace());
      }

      return body;
   }
   /**
    * Checks whether the given SOAP message is in proper format and contains all
    * required elements.
    * 
    * @param   msg		    the response SOAPMessage object
    * @return			    true if valid, false otherwise
    */
   private boolean validateDownload(SOAPMessage msg) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.validateDownload() with SOAPMessage: " + msg);
      }
      boolean ret = false;

      try {
         // check headers for:
         //    -initialisationVector, and
         //    -schoolName, and
         //    -schoolNumber, and optionally (if authenticationCheck)
         //    -schoolRecongnised, and optionally (if authenticationCheck)
         //    -authenticated
         // check body for:
         //    -base64binary (or SOAP fault)

         SOAPHeader header = msg.getSOAPHeader();
         SOAPBody body = (SOAPBody) msg.getSOAPBody();
         if (logger.isDebugEnabled()) {
            logger.debug("Response header: " + header.toString());
            logger.debug("Response body: " + body.toString());
         }

         boolean ivOk = false;
         boolean schoolNameOk = false;
         boolean schoolNumberOk = false;
         boolean schoolRecognisedOk = false;
         boolean authenticatedOk = false;
         Iterator iterator = header.getChildElements();
         while (iterator.hasNext()) {
            SOAPHeaderElement el = (SOAPHeaderElement) iterator.next();
            String tagname = el.getTagName();

            if (logger.isDebugEnabled()) {
               logger.debug("Got header element tagname: " + tagname);
            }
            if (tagname.contains("schoolRecognised")) {
               String recognised = el.getValue();
               if (recognised != null && recognised.equals("N")) {
                  throw new Exception("school is not recognised - double check your school configurations");
               }
               schoolRecognisedOk = true;
            } else if (tagname.contains("authenticated")) {
               String authenticated = el.getValue();
               if (authenticated != null && authenticated.equals("N")) {
                  throw new Exception("authentication failed - double check your authentication key configuration");
               }
               authenticatedOk = true;
            } else if (tagname.contains("initialisationVector")) {
               if (el.getValue().length() < 1) {
                  throw new Exception("initialisation vector is empty");
               }
               ivOk = true;
            } else if (tagname.contains("schoolName")) {
               if (!el.getValue().equals(schoolName)) {
                  throw new Exception("invalid school name: " + el.getValue());
               }
               schoolNameOk = true;
            } else if (tagname.contains("schoolNumber")) {
               if (!el.getValue().equals(schoolNumber)) {
                  throw new Exception("invalid school number: " + el.getValue());
               }
               schoolNumberOk = true;
            }
         }

         if (!ivOk) {
            // check to see if this is a SOAP fault
            String soapFault = getSOAPFault(body);
            if (soapFault != null) {
               throw new Exception(soapFault);
            }
         }

         boolean headerOk = schoolRecognisedOk && authenticatedOk && ivOk && schoolNameOk && schoolNumberOk;

         if (!headerOk) {
            throw new Exception("missing headers");
         }

         if (logger.isDebugEnabled()) {
            logger.debug("Headers are valid");
         }
         boolean bodyOk = false;
         iterator = body.getChildElements();
         while (iterator.hasNext()) {
            SOAPBodyElement el = (SOAPBodyElement) iterator.next();
            String tagname = el.getTagName();

            if (logger.isDebugEnabled()) {
               logger.debug("Got body element tagname: " + tagname);
            }
            if (tagname.contains("base64binary")) {
               if (el.getValue().length() < 1) {
                  throw new Exception("SOAP body is empty");
               }
               bodyOk = true;
            } else if (tagname.contains("soap-env:fault")) {
               bodyOk = true;
            }
         }

         if (!bodyOk) {
            throw new Exception("missing body elements");
         }
         if (logger.isDebugEnabled()) {
            logger.debug("Body is valid");
         }
         ret = true;

      } catch (Exception e) {
         logger.error("Failed to validate downloaded data: " + e);
         ret = false;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.validateDownload() with boolean: " + ret);
      }
      return ret;
   }

   /**
    * Takes a String containing the SOAP body element (including the SOAP-ENV:Body tags),
    * encrypts it using 3DES, and returns a SOAPMessage object, with the generated IV inserted
    * into the message as a header, <ns1:initialisationVector>iv</initialisationVector>.
    * All binary data, including the encrypted SOAP body, and the IV, are base 64 encoded.
    * 
    * @param   body	      the String containing the SOAP body
    * @param   version	      the WebSYNC version number to be injected into SOAP headers
    * @param   schoolName     the school name to be injected into SOAP headers
    * @param   schoolNumber   the school MoE number to be injected into SOAP headers
    * @param   scheduleUpload the scheduled upload interval to be injected into SOAP headers
    * @return		      the complete SOAPMessage object to send
    * @throws  java.lang.Exception   if something went wrong...
    */
   private SOAPMessage encryptBody(String body, String version, String schoolName, String schoolNumber, String scheduleUpload, String processTime) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.encryptBody() with String: " + body + ", String: " + version + ", String: " + schoolName + ", String: " + schoolNumber + ", String: " + scheduleUpload + ", String: " + processTime);
      }
      SecretKey key = Utilities.getKey(keyString);
      if (logger.isDebugEnabled()) {
         logger.debug("Retrieved SecretKey from file");      // encrypt the body
      }
      Object[] result = Utilities.tripleDESEncrypt(body.getBytes(), key);
      byte[] ciphertext = (byte[]) result[0];
      byte[] iv = (byte[]) result[1];
      if (logger.isDebugEnabled()) {
         logger.debug("Encrypted body successfully");      // encode and encapsulate
      }
      String encoded = Base64.encodeBytes(ciphertext, Base64.DONT_BREAK_LINES);
      encoded = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
              "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
              "<soapenv:Body>" +
              "<base64binary>" + encoded + "</base64binary>" +
              "</soapenv:Body>" + "</soapenv:Envelope>";
      if (logger.isDebugEnabled()) {
         logger.debug("Encoded and encapsulated body successfully");      // create a new SOAP message
      }
      org.apache.axis.soap.MessageFactoryImpl mf=new org.apache.axis.soap.MessageFactoryImpl();
      SOAPMessage smsg = mf.createMessage(new MimeHeaders(), new ByteArrayInputStream(encoded.getBytes()));
      if (logger.isDebugEnabled()) {
         logger.debug("Created new SOAPMessage: " + smsg.getClass().getName());
      }
      SOAPPart sp = smsg.getSOAPPart();
      if (logger.isDebugEnabled()) {
         logger.debug("Got SOAPPart: " + sp.getClass().getName());
//      javax.xml.soap.SOAPEnvelope se = (SOAPEnvelope)sp.getEnvelope();
      }
      SOAPEnvelope se = (SOAPEnvelope) sp.getEnvelope();
      if (logger.isDebugEnabled()) {
         logger.debug("Got SOAPEnvelope: " + se.getClass().getName());      // encode the generated IV
      }
      String ivString = Base64.encodeBytes(iv, Base64.DONT_BREAK_LINES);

      // inject IV as SOAP header
//      SOAPHeaderElement header = new SOAPHeaderElement(namespace, "initialisationVector", ivString);
//      SOAPHeader header = smsg.getSOAPHeader();    
//      SOAPHeaderElement ivHeader = header.addHeaderElement(new PrefixedQName(namespace, "initialisationVector", ""));
      SOAPHeaderElement ivHeader = new SOAPHeaderElement(namespace, "initialisationVector", ivString);
      se.addHeader(ivHeader);
//      ivHeader.setValue(ivString);
      if (logger.isDebugEnabled()) {
         logger.debug("Created and injected IV header successfully");      // inject other required headers
      }
      SOAPHeaderElement versionHeader = new SOAPHeaderElement(namespace, "websyncVersion", version);
      se.addHeader(versionHeader);
      SOAPHeaderElement schoolNameHeader = new SOAPHeaderElement(namespace, "schoolName", schoolName);
      se.addHeader(schoolNameHeader);
      SOAPHeaderElement schoolNumberHeader = new SOAPHeaderElement(namespace, "schoolNumber", schoolNumber);
      se.addHeader(schoolNumberHeader);
      SOAPHeaderElement scheduleUploadHeader = new SOAPHeaderElement(namespace, "scheduleUpload", scheduleUpload);
      se.addHeader(scheduleUploadHeader);
      SOAPHeaderElement processTimeHeader = new SOAPHeaderElement(namespace, "processTime", processTime);
      se.addHeader(processTimeHeader);

      if (logger.isDebugEnabled()) {
         logger.debug("Created and injected other required headers successfully");
      }


      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.encryptBody() with SOAPMessage: " + smsg);
      }
      return smsg;
   }

   /**
    * Takes a complete SOAPMessage object returned from WebSYNC, which is assumed
    * to be encrypted using 3DES, extracts the IV from the SOAP header, and decrypts
    * the SOAP body element.  Returns a Document object, representing the contents
    * of the original SOAP body element, minus the SOAP-ENV:Body tags, i.e. manual
    * parsing needs to be performed using the Document object.
    * 
    * @param   response	a SOAPMessage representing the response from WebSYNC
    * @return		a Document object to be used for parsing
    * @throws  java.lang.Exception  if something went wrong...
    */
   private Document decryptBody(SOAPMessage response) throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.decryptBody() with SOAPMessage: " + response);
      }
      SecretKey key = Utilities.getKey(keyString);
      if (logger.isDebugEnabled()) {
         logger.debug("Retrieved SecretKey from file");
      }
      SOAPHeader resHeader = response.getSOAPHeader();
      Iterator it = resHeader.getChildElements();

      // extract IV
      SOAPHeaderElement el = null;
      byte[] iv = null;
      while (it.hasNext()) {
         if (logger.isDebugEnabled()) {
            logger.debug("SOAP header has elements");
         }
         el = (SOAPHeaderElement) it.next();
         String tagName = el.getTagName();
         if (logger.isDebugEnabled()) {
            logger.debug("Got header element: " + tagName);
         }
         if (tagName.contains("initialisationVector")) {
            iv = Base64.decode(el.getValue());
            if (logger.isDebugEnabled()) {
               logger.debug("Got IV");
            }
         }
      }

      if (iv == null) {
         // maybe its a SOAP fault
         SOAPBody body = (SOAPBody) response.getSOAPBody();
         String faultText = getSOAPFault(body);

         String message = "";

         if (faultText == null) {
            message = "Failed to extract IV from SOAP header";
         } else {
            message = "SOAP Fault found in body: " + faultText;
         }

         // whatever the case, its cause for concern
         logger.warn(message);
         throw new Exception(message);
      }

      // extract ciphertext
      byte[] ciphertext = null;
      SOAPBody resBody = (SOAPBody) response.getSOAPBody();
      Document doc = resBody.getAsDocument();
      Node root = doc.getFirstChild();
      if (root.hasChildNodes()) {
         if (logger.isDebugEnabled()) {
            logger.debug("SOAP body has child elements");
         }
         NodeList list = root.getChildNodes();
         for (int i = 0; i < list.getLength(); i++) {
            Node curr = list.item(i);
            if (curr.getNodeName().contains("base64binary")) {
               ciphertext = Base64.decode(curr.getTextContent());
               if (logger.isDebugEnabled()) {
                  logger.debug("Got ciphertext");
               }
            }
         }
      }

      if (ciphertext == null) {
         String message = "Failed to extract ciphertext from SOAP body";
         logger.warn(message);
         throw new Exception(message);
      }

      // decrypt ciphertext
      byte[] cleartext = Utilities.tripleDESDecrypt(ciphertext, key, new IvParameterSpec(iv));
      String resultBody = Utilities.binaryToString(cleartext).trim();
      if (resultBody == null) {
         String message = "Decrypted content contains invalid characters";
         logger.warn(message);
         throw new Exception(message);
      }
      if (logger.isDebugEnabled()) {
         logger.debug("Ciphertext decrypted: " + resultBody);      // create new Document
      }
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      byte[] bytes = resultBody.getBytes();
      ByteArrayInputStream in = new ByteArrayInputStream(bytes);
      Document ret = builder.parse(in);
      if (logger.isDebugEnabled()) {
         logger.debug("Document created");
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.decryptBody() with Document: " + ret);
      }
      return ret;
   }

   /**
    * Looks for any SOAP faults in the SOAP body, and returns the fault text if it
    * finds any.  Returns null if no fault is found.
    * 
    * @param   body  the SOAP body to search
    * @return	     the fault text, or null if no faults are found
    */
   private String getSOAPFault(SOAPBody body) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.isSOAPFault() with SOAPBody: " + body);
      }
      String ret = null;

      try {
         Document doc = body.getAsDocument();
         if (doc.hasChildNodes()) {
            NodeList list = doc.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
               Node curr = list.item(i);
               String tagName = curr.getNodeName().toLowerCase();
               if ((tagName.contains("soap-env:body") || tagName.contains("soapenv:body")) && curr.hasChildNodes()) {
                  NodeList list2 = curr.getChildNodes();
                  for (int j = 0; j < list2.getLength(); j++) {
                     Node curr2 = list2.item(j);
                     String tagName2 = curr2.getNodeName().toLowerCase();
                     if ((tagName2.contains("soap-env:fault") || tagName2.contains("soapenv:fault")) && curr2.hasChildNodes()) {
                        NodeList list3 = curr2.getChildNodes();
                        for (int k = 0; k < list3.getLength(); k++) {
                           Node curr3 = list3.item(k);
                           String tagName3 = curr3.getNodeName();
                           if (tagName3.equalsIgnoreCase("faultstring")) {
                              ret = curr3.getNodeValue();
                           }
                        }
                        ret = curr2.getTextContent();
                        break;
                     }
                  }

                  if (logger.isDebugEnabled()) {
                     logger.debug("Found SOAP fault in SOAP body: " + ret);
                  }
                  break;
               }
            }
         }
      } catch (Exception e) {
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.isSOAPFult() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the MD5 hash of the uploaded file with the give recId, from the server.
    * 
    * @param   recId the record ID of the file uploaded
    * @return	     the MD5 hash of the uploaded file
    */
   public String doGetUploadMD5Hash(int recId) {
      if (logger.isTraceEnabled()) {
         logger.trace("WebSYNCSOAPService.doGetUploadMD5Hash() with int: " + recId);
      }
      String ret = "";

      try {

         Object[] params = new Object[1];
         params[0] = recId;

         Document body=sendRequest(uploadGetMD5Method,params);
    	 /*
         <ns1:UploadGetMD5Response xmlns:ns1="namespace">
         <return xsi:type=":UploadGetMD5Response">data</return>
         </ns1:UploadGetMD5Response>
          */
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength(); i++) {
            Node curr = list.item(i);
            if (curr.getNodeName().contains(uploadGetMD5Method + "Response") && curr.hasChildNodes()) {
               NodeList list2 = curr.getChildNodes();
               for (int j = 0; j < list2.getLength(); j++) {
                  Node curr2 = list2.item(j);
                  if (curr2.getNodeName().equals("return")) {
                     ret = curr2.getTextContent().trim();
                     successful = true;
                     break;
                  }
               }
            }
         }

         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + uploadGetMD5Method);
         }
      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + uploadGetMD5Method + "): " + e + ", " + e.getStackTrace());
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doGetUploadMD5Hash() String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the MD5 hash of the downloaded file with the given recId, from the server.
    * 
    * @param   recId the record ID of the file downloaded
    * @return	     the MD5 hash of the downloaded file
    */
   public String doGetDownloadMD5Hash(int recId) {
      if (logger.isTraceEnabled()) {
         logger.trace("WebSYNCSOAPService.doGetDownloadMD5Hash() with int: " + recId);
      }
      String ret = "";

      try {

         Object[] params = new Object[1];
         params[0] = recId;

         Document body=sendRequest(downloadGetMD5Method,params);
	 /*
         <ns1:UploadGetMD5Response xmlns:ns1="namespace">
         <return xsi:type=":UploadGetMD5Response">data</return>
         </ns1:UploadGetMD5Response>
          */
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength(); i++) {
            Node curr = list.item(i);
            if (curr.getNodeName().contains(downloadGetMD5Method + "Response") && curr.hasChildNodes()) {
               NodeList list2 = curr.getChildNodes();
               for (int j = 0; j < list2.getLength(); j++) {
                  Node curr2 = list2.item(j);
                  if (curr2.getNodeName().equals("return")) {
                     ret = curr2.getTextContent().trim();
                     successful = true;
                     break;
                  }
               }
            }
         }

         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + downloadGetMD5Method);
         }
      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + downloadGetMD5Method + "): " + e + ", " + e.getStackTrace());
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doGetDownloadMD5Hash() String: " + ret);
      }
      return ret;
   }

   /**
    * Once the batch is uploaded, we check every so often to see if the LMS has completed. The LMS will either
    * return a blank string (if not complete), 'SUCCESS', or a base64 encoded error message.
    *
    * @return
    */
   public String doGetBatchResult() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doGetBatchResult()");
      }
      String ret = null;

      try {
         // we must do things manually in order to be able to encrypt/decrypt

         Object[] params = new Object[1];
         params[0] = "dummy";

         Document body=sendRequest(getBatchResultMethod,params);

         logger.debug("Decoded and decrypted SOAP body successfully, ready for parsing");         // the message is in the following format:
	 /*
         <ns1:getBatchResult xmlns:ns1="namespace">
         <return xsi:type="xsd:string">response</return>
         </ns1:getBatchResult>
          */         // parse it (painfully...)
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(getBatchResultMethod + "Response") && node.hasChildNodes()) {
               NodeList list2 = node.getChildNodes();
               for (int j = 0; j < list2.getLength() && !successful; j++) {
                  Node node2 = list2.item(j);
                  if (node2.getNodeName().equals("return")) {
                     ret = node2.getTextContent();
                     successful = true;
                     break;
                  }
               }
            }
         }
         if (!successful) {
            throw new Exception("Failed to validate return of method invocation: " + getBatchResultMethod);
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.warn("Exception occurred during method invocation (" + getBatchResultMethod + "): " + e + ", " + e.getStackTrace());
      }

      if(!ret.equals("SUCCESS"))
      {
         ret = new String(Base64.decode(ret));
      }
      
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doGetBatchResult() with String: " + ret);
      }
      return ret;
   }
}
