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
import java.util.HashMap;

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
    * The name of the uncompress file method to invoke on the server
    */
   private String uncompressFileMethod;
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
    * The name of the keep alive method
    */
   private String getKeepAliveMethod;
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
      uploadBlockMethod = "UploadBlock";
      uncompressFileMethod = "UncompressFile";
      uploadGetLastBlockMethod = "UploadGetLastBlock";
      downloadMethod = "CopyDownFromTable";
      recordLogMethod = "recordLog";
      updateTableRawMethod = "doupdateTableRaw";
      getUploadStatusMethod = "getUploadStatusAssoc";
      testUploadMethod = "testUpload";
      uploadGetMD5Method = "UploadGetMD5Check";
      downloadGetMD5Method = "DownloadGetMD5";
      getBatchResultMethod = "getBatchResult";
      getKeepAliveMethod = "keepAlive";
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
			if(body==null) return -1;
	 /*
         <ns1:UploadBlockResponse xmlns:ns1="mudder">
         <return xsi:type="xsd:string">1</return>
         </ns1:UploadBlockResponse>
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
            logger.error("0108: Failed to read response from uploadBlock message.");
				if(logger.isDebugEnabled())
				{
					logger.debug("0108: Failed to read response from uploadBlock message:\n"+body.toString());
				}
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.error("0002: Exception occurred during " + uploadBlockMethod + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + uploadBlockMethod + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
         ret = -1;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doUploadBlock() with int: " + ret);      // return the record identifier
      }
      return ret;
   }
   /**
    * Indicates that the file has completed uploading and can be uncompressed.
    *
    * @param   recId	the int representing which record to append to (must be 0 on the first call)
    * @return		the record id
    * @throws  javax.xml.soap.SOAPException  if something went wrong
    */
   public int doUncompressFile(int recId) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doUncompressFile() with int: " + recId);
      }
      int ret = 0;
      try {

         Object[] params = new Object[1];
         params[0] = recId;

         Document body=sendRequest(uncompressFileMethod,params);
			if(body==null) return -1;

			boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(uncompressFileMethod + "Response") && node.hasChildNodes()) {
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
            logger.error("0108: Failed to read response from uncompressFile message.");
				if(logger.isDebugEnabled())
				{
					logger.debug("0108: Failed to read response from uncompressFile message:\n"+body.toString());
				}
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.error("0002: Exception occurred during " + uncompressFileMethod + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + uncompressFileMethod + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
         ret = -1;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doUncompressFile() with int: " + ret);      // return the record identifier
      }
      return ret;
   }

   /**
    * Begs the LMS to have mercy and ignore our past sins.
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
         logger.error("0002: Exception occurred during " + discardFileMethod + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + discardFileMethod + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
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
			if(body==null) return -1;
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
            logger.error("0108: Failed to read response from uploadGetLastBlock message.");
				if(logger.isDebugEnabled())
				{
					logger.debug("0108: Failed to read response from uploadGetLastBlock message:\n"+body.toString());
				}
          }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.error("0002: Exception occurred during " + uploadGetLastBlockMethod + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + uploadGetLastBlockMethod + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
          ret = -1;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doUploadGetLastBlock() with int: " + ret);
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
			if(body==null) return false;
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
            logger.error("0108: Failed to read response from recordLog message.");
				if(logger.isDebugEnabled())
				{
					logger.debug("0108: Failed to read response from recordLog message:\n"+body.toString());
				}
         }
      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.error("0002: Exception occurred during " + recordLogMethod + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + recordLogMethod + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
         ret = false;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doRecordLog() with boolean: " + ret);
      }
      return ret;
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
			if(body==null) return false;
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
            logger.error("0108: Failed to read response from TestUpload message.");
				if(logger.isDebugEnabled())
				{
					logger.debug("0108: Failed to read response from TestUpload message:\n"+body.toString());
				}
         }
         File file = new File(uploadDir + "/TestUpload.txt");
         if (!file.delete()) {
            logger.warn("Failed to delete test upload file: " + file.getName());
            return false;
         }
         logger.info("Deleted test upload file OK");
      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.error("0002: Exception occurred during " + testUploadMethod + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + testUploadMethod + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
      }

      ret = true;
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doTestUpload() boolean: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the MD5 hash of the uploaded file with the give recId, from the server.
    *
    * @param   recId the record ID of the file uploaded
    * @param   localHash The MD5 of the file here
    * @return	     the MD5 hash of the uploaded file
    */
   public String doGetUploadMD5Hash(int recId, String localHash) {
      if (logger.isTraceEnabled()) {
         logger.trace("WebSYNCSOAPService.doGetUploadMD5Hash() with int: " + recId + ", String: " + localHash);
      }
      String ret = "";

      try {

         Object[] params = new Object[2];
         params[0] = recId;
         params[1] = localHash;

         Document body=sendRequest(uploadGetMD5Method,params);
			if(body==null) return "";
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
            logger.error("0108: Failed to read response from GetUploadMD5Hash message.");
				if(logger.isDebugEnabled())
				{
					logger.debug("0108: Failed to read response from GetUploadMD5Hash message:\n"+body.toString());
				}
         }
      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.error("0002: Exception occurred during " + uploadGetMD5Method + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + uploadGetMD5Method + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doGetUploadMD5Hash() String: " + ret);
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
			if(body==null) return null;

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
            logger.error("0108: Failed to read response from GetBatchResult message.");
				if(logger.isDebugEnabled())
				{
					logger.debug("0108: Failed to read response from GetBatchResult message:\n"+body.toString());
				}
         }

      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.error("0002: Exception occurred during " + getBatchResultMethod + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + getBatchResultMethod + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
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

	   /**
    * Once the batch is uploaded, we check every so often to see if the LMS has completed. The LMS will either
    * return a blank string (if not complete), 'SUCCESS', or a base64 encoded error message.
    *
    * @return
    */
   public String doKeepAlive(String websyncStatus) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.doKeepAlive()");
      }
      String ret = null;

      try {
         // we must do things manually in order to be able to encrypt/decrypt

         Object[] params = new Object[1];
         params[0] =websyncStatus;

         Document body=sendRequest(getKeepAliveMethod,params);
			if(body==null) return null;

         logger.debug("Decoded and decrypted SOAP body successfully, ready for parsing");         // the message is in the following format:
	 /*
         <ns1:doKeepAlive xmlns:ns1="namespace">
         <return xsi:type="xsd:string">response</return>
         </ns1:doKeepAlive>
          */         // parse it (painfully...)
         boolean successful = false;
         NodeList list = body.getChildNodes();
         for (int i = 0; i < list.getLength() && !successful; i++) {
            Node node = list.item(i);
            if (node.getNodeName().contains(getKeepAliveMethod + "Response") && node.hasChildNodes()) {
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
				logger.error("0108: Failed to read response from keep alive message.");
         }
      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.error("0002: Exception occurred during " + getKeepAliveMethod + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + getKeepAliveMethod + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting WebSYNCSOAPService.doKeepAlive() with Object: " + ret.toString());
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

         // decrypt the response
         body = decryptBody(response);
         if (body!=null &&logger.isDebugEnabled()) {
            logger.debug("Decoded and decrypted SOAP body successfully, ready for parsing");         // the message is in the following format:
         }
      } catch (Exception e) {
         // there could be many reasons why an exception was thrown...
         logger.error("0002: Exception occurred during " + method + ": " + e.getLocalizedMessage());
			if(logger.isDebugEnabled())
			{
				logger.debug("0002: Exception occurred during " + method + ": \n" + e.getLocalizedMessage()+"\n"+e.getStackTrace());
			}
      }

      return body;
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
		boolean schoolRecognisedOk = false;
		boolean authenticatedOk = false;
		boolean ivOk = false;

      // extract IV
      SOAPHeaderElement el = null;
      byte[] iv = null;
      while (it.hasNext()) {
         el = (SOAPHeaderElement) it.next();
         String tagName = el.getTagName();
         if (logger.isDebugEnabled()) {
            logger.debug("Got header element: " + tagName);
         }
         if (tagName.contains("schoolRecognised")) {
				String recognised = el.getValue();
				if (recognised != null && recognised.equals("N")) {
					logger.error("0104: School is not recognised - double check your school configurations");
					return null;
				}
				schoolRecognisedOk = true;
			} else if (tagName.contains("authenticated")) {
				String authenticated = el.getValue();
				if (authenticated != null && authenticated.equals("N")) {
					logger.error("0105: Authentication failed. Double check your authentication key configuration.");
					return null;
				}
				authenticatedOk = true;
			} else if (tagName.contains("initialisationVector")) {
				if (el.getValue().length() < 1) {
					logger.error("0106: Initialisation vector is empty");
					if (logger.isDebugEnabled()) {
						logger.debug("0106: Initialisation vector: "+el.getValue());
					}
					return null;
				}
            iv = Base64.decode(el.getValue());
				ivOk = true;
            if (logger.isDebugEnabled()) {
               logger.debug("Got IV");
            }
         }
      }

      if (iv == null) {
         // maybe its a SOAP fault
         SOAPBody body = (SOAPBody) response.getSOAPBody();
         HashMap faultText = getSOAPFault(body);

         String message = "";
         String details = "";

         if (faultText == null) {
            message = "0100: Invalid response received from LMS";
            details = "0100: Invalid response received from LMS: \n"+resHeader.toString();
         } else {
				if(faultText.get("fault").equals("org.xml.sax.SAXException: Processing instructions are not allowed within SOAP messages"))
				{
					message="0101: LMS returned a web page. It could be down. Try visiting the web service URL in a browser.";
				} else if(faultText.get("fault").equals("java.net.SocketTimeoutException: Read timed out"))
				{
					message="0109: Timeout - No response was received.";
				} else if(faultText.get("fault").equals("java.net.SocketTimeoutException: connect timed out"))
				{
					message="0110: Timeout - Could not connect.";
				} else
				{
					message = "0001: Fault returned from LMS: " + faultText.get("fault");
					details = "0001: Fault returned from LMS: " + faultText.get("details");
				}
         }

         // whatever the case, its cause for concern
         logger.error(message);
			if(!details.equals("") && logger.isDebugEnabled()) logger.debug(details);
			return null;
      }
		boolean headerOk = schoolRecognisedOk && authenticatedOk && ivOk;

		if (!headerOk) {
			logger.error("0107: Missing headers");
			if(logger.isDebugEnabled())
			{
				logger.debug("0107: Missing headers: schoolRecognised:"+schoolRecognisedOk+" authenticated:"+authenticatedOk+" IV:"+ivOk);
			}
			return null;
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
         String message = "0102: Could not find the message contents";
         logger.error(message);
			if(logger.isDebugEnabled())
			{
				String details = "0102: Could not find the message contents: \n"+resBody.toString();
				logger.debug(details);
			}
         return null;
      }

      // decrypt ciphertext
      byte[] cleartext = Utilities.tripleDESDecrypt(ciphertext, key, new IvParameterSpec(iv));
      String resultBody = Utilities.binaryToString(cleartext).trim();
      if (resultBody == null) {
         String message = "0103: Decrypted content contains invalid characters";
         logger.error(message);
			if(logger.isDebugEnabled())
			{
				String details = "0103: Decrypted content contains invalid characters: \n"+cleartext.toString();
				logger.debug(details);
			}
         return null;
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
   private HashMap getSOAPFault(SOAPBody body) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered WebSYNCSOAPService.isSOAPFault() with SOAPBody: " + body);
      }
      HashMap ret = null;
		String fault=null;
		String details=null;

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
                              fault = curr3.getTextContent();
                           } else if (tagName3.equalsIgnoreCase("detail")) {
                              details = curr3.getTextContent();
                           }
                        }
                        //ret = curr2.getTextContent();
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
		if(fault!=null)
		{
			ret=new HashMap(0);
			ret.put("fault",fault);
			ret.put("details",details);
		}
      return ret;
   }
}