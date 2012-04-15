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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.security.NoSuchAlgorithmException;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

/**
 * The downloader thread.  Performs download of data to the download dir, specified
 * in the application configuration.
 * 
 * @author  William Song
 * @version 1.0.2
 */
public class Downloader extends Thread {
   /**
    * The logger!
    */
   private static Logger logger = Logger.getLogger(Downloader.class);
   /**
    * The Client which spawned this thread.
    */
   private Client parent;
   /**
    * The path to the directory to which downloaded files should be stored.
    */
   private String downloadDir;
   
   /**
    * Constructor.
    * 
    * @param   c  the Client which spawned this thread
    */
   public Downloader(Client c) {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Downloader.constructor() with Client: " + c);
      
      parent = c;
      reloadConfig();
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Downloader.constructor()");
   }
   
   /**
    * Runs the download procedure.
    */
   @Override public void run() {
      NDC.push("Downloader");
      
      if (logger.isTraceEnabled())
	 logger.trace("Entered Downloader.run()");
      
      doDownload();
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Downloader.run()");
      
      NDC.pop();
      NDC.remove();
   }

   /**
    * Performs the download of files from the server.
    */
   private void doDownload() {      
      if (logger.isTraceEnabled())
	 logger.trace("Entered Downloader.doDownload()");
      
      WebSYNCService caller = WebSYNCServiceFactory.getSoapService(
	 parent.getKnUrl(), 
	 "http://www.dataview.co.nz/", 
	 parent.getAuthenticationKey(), 
	 parent.getSchoolName(), 
	 parent.getSchoolNumber(), 
	 parent.getScheduleUploadString(), 
	 parent.getProcessTimeString()
      );
      byte[] data;
      int numFailedDownloads = 0;
      int numSuccessfulDownloads = 0;
      int maxNumDownloads = parent.getDownloadsPerSession();
      boolean unrestrict = false;
      if (maxNumDownloads <= 0)
	 unrestrict = true;
      if (logger.isDebugEnabled())
	 logger.debug("maxNumDownloads: " + maxNumDownloads);
      
      int retryCounter = 0;
      int maxRetries = 5;
      
      while ((data = caller.doDownload()) != null && (unrestrict || numSuccessfulDownloads < maxNumDownloads) && retryCounter < maxRetries) {
	 logger.info("Downloading and processing file (attempt " + (retryCounter + 1) + " of " + maxNumDownloads);
	 
	 if (logger.isDebugEnabled()) {
	    logger.debug("data: " + data.length + ", numSuccessfulDownloads: " + numSuccessfulDownloads + ", retryCounter: " + retryCounter);
	    logger.debug("Iterate");
	 }
	 
	 // get the associated fields for the last download
	 int fileId = caller.getDownloadedFileId();
	 String filename = caller.getDownloadedFileName();
	 if (logger.isDebugEnabled())
	    logger.debug("fileId: " + fileId + ", filename: " + filename);
	 
	 if (fileId == -1 || filename == null) {
	    String message = "Invalid data received from download, file was not saved to disk: fileId: " + fileId + ", filename: " + filename;
	    logger.warn(message);
	    parent.appendReport(message);
	    parent.badOverallStatus();
	    numFailedDownloads++;
	    retryCounter++;
	    
	    if (logger.isDebugEnabled())
	       logger.debug("Appended warning to report, set overall status to bad, incremented numFailedDownloads: " + numFailedDownloads);
	    
	    continue;
	 }
	 
	 logger.info("Downloaded file data successfully");
	 
	 // now check file integrity
	 boolean fileValid = isFileValid(fileId, data);
	 if (!fileValid) {
	    String message = "File integrity check failed, file was not saved to disk: fileId: " + fileId;
	    logger.warn(message);
	    parent.appendReport(message);
	    parent.badOverallStatus();
	    numFailedDownloads++;
	    retryCounter++;
	    
	    if (logger.isDebugEnabled())
	       logger.debug("Appended warning to report, set overall status to bad, incremented numFailedDownloads: " + numFailedDownloads);
	    
	    continue;
	 }
	 
	 logger.info("Downloaded file integrity checked successfully");
	 
	 // write the data to file
	 try {
	    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(downloadDir + "/" + filename));
	    bos.write(data, 0, data.length);
	    bos.close();
	    logger.info("Written file data to disk successfully");
	    
	 } catch (IOException e) {
	    String message = "Exception encountered while attempting to save downloaded file, file was not saved to disk: " + e;
	    logger.warn(message);
	    parent.appendReport(message);
	    parent.badOverallStatus();
	    numFailedDownloads++;
	    retryCounter++;
	    
	    if (logger.isDebugEnabled())
	       logger.debug("Appended warning to report, set overall status to bad, incremented numFailedcownloads: " + numFailedDownloads);
	    
	    continue;
	 }
	 
	 // if we are here, then download went ok for this file
	 String sql = "UPDATE sms_download SET downloaded=\"Y\" WHERE rec_id = " + fileId;
	 if (logger.isTraceEnabled())
	    logger.trace("Update SQL: " + sql);
	 
	 boolean downloadMarked;
	 int count = 0;
	 do {
	    
	    if (logger.isDebugEnabled())
	       logger.debug("About to mark file as \"downloaded\" on server (attempt " + (count + 1) + " of 5)");
	    
	    downloadMarked = caller.doUpdateTableRaw(sql);
	    
	    if (logger.isDebugEnabled())
	       logger.debug("Returned from invocation with: " + downloadMarked);
	    
	    count++;
	 } while (!downloadMarked && count < 5);
	 
	 if (!downloadMarked) {
	    // this is an error because it has the potential to block all further downloads without user intervention
	    String message = "Failed to mark downloaded file (" + filename + ") on server as downloaded after 5 attempts - file must be marked as downloaded manually on the server, my state: " + this;
	    logger.error(message);
	    parent.appendReport(message);
	    parent.badOverallStatus();
	    
	    if (logger.isDebugEnabled())
	       logger.debug("Appended error to report, set overall status to bad, returning from method");
	    
	    return;
	 }
	 
	 // if we are here, everything went ok
	 numSuccessfulDownloads++;
	 retryCounter = 0;
	 logger.info("Marked file as downloaded on server");
	 logger.info("File download completed successfully");
	 if (logger.isDebugEnabled())
	    logger.debug("Increment numSuccessfulDownloads: " + numSuccessfulDownloads);
      }
      
      if (retryCounter > maxRetries) {
	 String message = "Failed to download file, exceeded maximum retries, skipping download";
	 logger.error(message);
	 parent.appendReport(message);
	 parent.badOverallStatus();
	 
	 if (logger.isDebugEnabled())
	    logger.debug("Appended error to report, set overall status to bad, returning from method");
	 
	 return;
      }
      
      if(numSuccessfulDownloads+numFailedDownloads>0)
      {
         String overview = "Overview of download run: ";
         overview += "successful downloads: " + numSuccessfulDownloads;
         overview += ", failed downloads: " + numFailedDownloads;
         logger.info("Completed download run (" + overview + ")");
         parent.appendReport(overview);
      }
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Downloader.doDownload()");
   }
   
   /**
    * Refreshes the current variables with new values (if applicable) from the <code>Client</code>'s 
    * configuration file, which could have been updated.
    */
   public void reloadConfig() {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Downloader.reloadConfig()");
      
      downloadDir = parent.getDownloadDir();
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Downloader.reloadConfig()");
   }
   
   /**
    * Returns a String representation of this object.
    * 
    * @return  the string representation of this object
    */
   @Override public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append(getClass().getName());
      sb.append(" downloadDir=[").append(this.downloadDir).append("]");
      return sb.toString();
   }
   
   /**
    * Determines whether downloaded file is valid.
    * 
    * @param   fileId	the ID of the file downloaded
    * @param   data	the file contents
    * @return		true if valid, false if not
    */
   private boolean isFileValid(int fileId, byte[] data) {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Downloader.isFileValid() with int: " + fileId + ", byte[]: " + data.length);
      
      if (data == null || data.length <= 0) {
	 if (logger.isDebugEnabled())
	    logger.debug("No data to check for consistency, assume data is already consistent");
	 return true;
      }
      
      boolean ret = false;
      WebSYNCService caller = WebSYNCServiceFactory.getSoapService(
	 parent.getKnUrl(), 
	 "http://www.dataview.co.nz/", 
	 parent.getAuthenticationKey(), 
	 parent.getSchoolName(), 
	 parent.getSchoolNumber(), 
	 parent.getScheduleUploadString(), 
	 parent.getProcessTimeString()
      );
      
      try {
	 String localHash = Utilities.MD5(data);
	 if (logger.isDebugEnabled())
	    logger.debug("Got local hash: " + localHash);
	 
	 String remoteHash = caller.doGetDownloadMD5Hash(fileId);
	 if (logger.isDebugEnabled())
	    logger.debug("Got remote hash: " + remoteHash);
	 
	 ret = localHash.equals(remoteHash);
      } catch (NoSuchAlgorithmException e) {
	 logger.warn("MD5 algorithm is unavailable: " + e);
      } catch (UnsupportedEncodingException e) {
	 logger.warn("Encoding unavailable: " + e);
      }
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Downloader.isFileValid() with boolean: " + ret);
      
      return ret;
   }
}
