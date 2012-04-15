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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.util.zip.DeflaterInputStream;

import java.security.NoSuchAlgorithmException;

import org.apache.log4j.Logger;

/**
 * Forms the core of the uploader, with functions common to both the batch and single file upload.
 * Overridden by the v1 and v2 uploader classes.
 * 
 * @author  William Song, Tim Owens
 * @version 1.1.0
 */
public abstract class Uploader extends Thread {

   /**
    * The logger!
    */
   public static Logger logger = Logger.getLogger(Uploader.class);
   /**
    * The Client which spawned this thread.
    */
   public Client parent;
   /**
    * The path to the directory at which the files to be uplaoded reside.
    */
   public String uploadDir;
   /**
    * Counts used by uploader to record file upload progress
    */
   public int warnings;
   public int successfulUploads;
   public int failedUploads;
   /**
    * Gives the current status of the upload. Only used for a batch upload
    */
   public String status = "";
   
   /**
    * The current batch number
    */
   public String batchNumber="";

   /**
    * Constructor.
    * 
    * @param   c  the Client which spawned this thread
    */
   public Uploader(Client c) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Uploader.contructor() with Client: " + c);
      }
      parent = c;
      reloadConfig();

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Uploader.constructor()");
      }
   }

    /**
    * Refreshes the current variables with new values (if applicable) from the <code>Client</code>'s 
    * configuration file, which could have been updated.
    */
   public void reloadConfig() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Uploader.reloadConfig()");
      }
      uploadDir = parent.getUploadDir();

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Uploader.reloadConfig()");
      }
   }

   /**
    * Not used in V1, but in V2 it is overridden to record the status of the upload.
    */
   public void getStatus() {
   }

   /**
    * Checks if the file is a file, readable, writeable, uploadable, and then calls doUpload to upload it.
    * @param file
    */
   public void uploadFile(File file) {
      String filename = file.getName();

      if (logger.isDebugEnabled()) {
         logger.debug("Got filename: " + filename + ", now checking ignore lists");      // the exceptions which should not be uploaded
      }
      if (Utilities.dontUploadFile(filename, parent.getDontUploadFiles())) { // some files are flagged to be ignored eg if previous upload succeeded but failed to remove file
         // this is WARNING level, as we should be cautious the files marked here should be looked at as to why they were marked
         String message = "File " + filename + " in the list of files to ignore, moving to next file";
         logger.warn(message);
         parent.appendReport(message);
         // dont set bad status, as this doesn't affect the program as a whole
         warnings++;

         if (logger.isDebugEnabled()) {
            logger.debug("Appended warning to report, increment warnings: " + warnings);
         }
         return;

      } else if (file.isDirectory()) {
         // send warning (we don't want rogue dirs sitting there).
         // also, whatever produced the directory should investigate why it was produced
         logger.warn("Found directory (" + filename + ") in " + uploadDir + ", expected normal file; ignoring file");
         warnings++;
         return;
      }

      if (logger.isDebugEnabled()) {
         logger.debug("File is not on the ignore lists, and is an ordinary file, proceed to upload checks");
      }
      if (file.canRead()) {
         if (logger.isDebugEnabled()) {
            logger.debug("File " + filename + " is readable");
         }
         try {

            boolean doDelete = doUpload(file);
            if (logger.isDebugEnabled()) {
               logger.debug("Returned from doUpload invocation with: " + doDelete);
            }
            if (doDelete && file.canWrite()) {
               logger.info("File " + filename + " was uploaded to the server");

               // attempt to delete the file
               boolean deleteDone = file.delete();
               if (logger.isDebugEnabled()) {
                  logger.debug("Attempted to delete file " + filename);               //deleteDone = false;
               }
               if (!deleteDone) {
                  String message = "Could not delete uploaded file " + filename + ", mark to not upload";
                  logger.warn(message);
                  parent.badOverallStatus();
                  parent.appendReport(message);
                  parent.addToDontUploadFiles(filename);
                  warnings++;

                  if (logger.isDebugEnabled()) {
                     logger.debug("Appended warning to report, set overall status to bad, incremented warnings: " + warnings);
                  }
               } else {
                  logger.info("Uploaded file " + filename + " was deleted from disk");
                  logger.info("File upload of " + filename + " completed successfully");
                  successfulUploads++;

                  if (logger.isDebugEnabled()) {
                     logger.debug("Incrementing successfulUploads: " + successfulUploads);
                  }
               }
            } else if (!doDelete) {
               String message = "Failed to upload file " + filename + ", my state: " + this;
               logger.error(message);
               parent.appendReport(message);
               parent.badOverallStatus();
               failedUploads++;

               if (logger.isDebugEnabled()) {
                  logger.debug("Appended error to report, set overall status to bad, incremented failedUploads: " + failedUploads);               // its not the end of the world; lets try the next file
               }
               return;

            } else if (!file.canWrite()) {
               String message = "Could not delete uploaded file " + filename + " (insufficient permissions), mark to not upload";
               logger.warn(message);
               parent.appendReport(message);
               parent.badOverallStatus();
               parent.addToDontUploadFiles(filename);
               warnings++;

               if (logger.isDebugEnabled()) {
                  logger.debug("Appended warning to report, set overall status to bad, incremented warnings: " + warnings);               // its not the end of the world; lets try the next file
               }
               return;
            }
         } catch (FileNotFoundException e) {
            // something weird is going on
            String message = "File " + filename + " could not be found (" + e.getMessage() + "): " + e.getStackTrace() + ", my state: " + this;
            logger.error(message);
            parent.appendReport(message);
            parent.badOverallStatus();
            failedUploads++;

            if (logger.isDebugEnabled()) {
               logger.debug("Appended error to report, set overall status to bad, incremented failedUploads: " + failedUploads);            // its not the end of the world; lets try the next file
            }
            return;
         }
      } else {
         String message = "Could not read file " + filename + " for upload, my state: " + this;
         logger.error(message);
         parent.appendReport(message);
         parent.badOverallStatus();
         failedUploads++;

         if (logger.isDebugEnabled()) {
            logger.debug("Appended error to report, set overall status to bad, incremented failedUploads: " + failedUploads);         // its not the end of the world; lets try the next file
         }
         return;
      }
   }

   /**
    * Performs the upload for the given file. Compresses the data in transit using the RFC 1951 (GZIP/ZLIB) standard
    * 
    * @param   f	the File to upload
    * @return  indicates whether the file was uploaded successfully and should therefore be deleted or not
    * @throws  java.io.FileNotFoundException thrown if IO error occurred while opening the given file
    */
   public boolean doUpload(File f) throws FileNotFoundException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Uploader.doUpload() with File: " + f);
      }
      boolean doDelete = false;

      if (f.canRead()) {
         if (logger.isDebugEnabled()) {
            logger.debug("File " + f + " is readable");
         }
         int limit = parent.getUploadByteLimit();
         //A file input stream, a buffered input stream, and a deflater input stream. Oh the joys of Java.
         BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));
         DeflaterInputStream dis = new DeflaterInputStream(bis);

         byte[] data = new byte[limit];

         if (logger.isDebugEnabled()) {
            logger.debug("Got upload byte limit: " + limit);
         }
         int recId = 0;	//first call should have recId of 0 to indicate new record
         String filename = f.getName();

         WebSYNCService caller = WebSYNCServiceFactory.getSoapService(
                 parent.getKnUrl(),
                 "http://www.dataview.co.nz/",
                 parent.getAuthenticationKey(),
                 parent.getSchoolName(),
                 parent.getSchoolNumber(),
                 parent.getScheduleUploadString(),
                 parent.getProcessTimeString());

         int bytesSoFar = 0;
         int numBytesRead = 0;
         int blockNum = 1;

         boolean isFatal = false;
         try {
            while ((numBytesRead = dis.read(data, 0, limit)) != -1) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Read " + limit + " bytes from byte no: " + bytesSoFar + ", numBytesRead: " + numBytesRead);               // if we are at the end of the byte stream, trim the data array to size
               // so that we don't encode empty bytes
               }
               if (numBytesRead < limit) {
                  byte[] tempData = data;
                  data = new byte[numBytesRead];
                  System.arraycopy(tempData, 0, data, 0, numBytesRead);
                  tempData = null;

                  if (logger.isDebugEnabled()) {
                     logger.debug("End of the byte stream detected, copy data array to trimmed array");
                  }
               }

               String[] dataToSend = Utilities.encodeForUpload(data);
               if (logger.isDebugEnabled()) {
                  logger.debug("Split into chunks of String of length 76 successfully");
                  logger.debug("Data block prepared for upload");
               }

               // make the call!
               int count = 0;
               boolean hasError = false;
               do {
                  hasError = false;
                  isFatal = false;

                  if (logger.isDebugEnabled()) {
                     logger.debug("About to upload block " + blockNum + " (attempt: " + (count + 1) + " of 5)");
                  }
                  int temptRecId = caller.doUploadBlock(filename, dataToSend, recId, blockNum);

                  if (logger.isDebugEnabled()) {
                     logger.debug("Returned from invocation with: " + temptRecId);                  // if something went wrong
                  }
                  if (temptRecId == -1) {
                     hasError = true;
                     // try and find the last successful block
                     logger.info("Block " + blockNum + " failed to upload, attempting to recover");
                     int lastBlockNum = caller.doUploadGetLastBlock(recId);
                     logger.info("Last successfully uploaded block: " + lastBlockNum + ", current block: " + blockNum);
                     // if the last successful upload was the not previous block, then this is not recoverable
                     if ((lastBlockNum + 1) != blockNum) {
                        logger.warn("Block cannot be recovered, this file upload cannot be completed");
                        isFatal = true;
                     } else {
                        logger.info("Block is recoverable, trying again");
                     }
                  } else {
                     // block uploaded successfully
                     // ideally, we should also do the integrity check here
                     // however the current WebSYNC architecture does not allow this
                     recId = temptRecId;
                  }

                  count++;
               } while (count < 5 && (hasError && !isFatal)); // if doUpload returns -1, something went wrong, try again
               if (count == 5 && hasError) {
                  isFatal = true;
               }
               if (isFatal) {
                  // the block upload failed, quit this file
                  String message = "Failed to upload file: " + filename + " (uploaded so far: " + bytesSoFar + " bytes), gave up after 5 tries, my state: " + this;
                  logger.error(message);
                  parent.appendReport(message);
                  parent.badOverallStatus();

                  if (logger.isDebugEnabled()) {
                     logger.debug("Appended error to report, set overall status to bad");                  // lets try and close off the stream
                  }
                  dis.close();
                  bis.close();
                  break;
               } else {
                  if (logger.isDebugEnabled()) {
                     logger.debug("Data block uploaded successfully, after " + count + " attempts");
                  }
                  bytesSoFar += limit;
                  blockNum++;
               }

               // clear the byte buffer
               data = new byte[limit];
            }

            // do not close off the file if a fatal error occurred - this marks this incomplete file as ready!
            if (!isFatal) {
               // make one last call to doUpload, with empty parameters, to indicate end of file
               int finalCount = 0;
               int ret = 0;
               do {
                  if (logger.isDebugEnabled()) {
                     logger.debug("About to call final upload to close off sequence (attempt: " + (finalCount + 1) + " of 5)");
                  }
                  ret = caller.doUploadBlock(filename, new String[0], recId, blockNum-1);

                  if (logger.isDebugEnabled()) {
                     logger.debug("Returned from upload invocation with: " + ret);
                  }
                  finalCount++;
               } while (finalCount < 5 && ret == -1);

               if (ret == -1) {
                  String message = "Failed to close off sequence, gave up after 5 tries, my state: " + this;
                  logger.error(message);
                  parent.appendReport(message);
                  parent.badOverallStatus();

                  if (logger.isDebugEnabled()) {
                     logger.debug("Appended error to report, set overall status to bad");
                  }
               } else if (!isFatal) {
                  if (logger.isDebugEnabled()) {
                     logger.debug("Sequence closed off successfully");
                     logger.debug("File " + filename + " uploaded successfully");
                  }
               }
               dis.close();
               bis.close();

               // now check the integrity of the uploaded file
               boolean validFile = false;
               try {
                  byte[] byteList = Utilities.getBytesFromFile(f);
                  validFile = isFileValid(recId, byteList);
               } catch (IOException e) {
                  logger.warn("Failed to read file for upload integrity check: " + e.getMessage());
               }

               if (!validFile) {
                  //If the file uploaded but failed to validate with MD5, we need to delete it quick...
                  caller.discardFile(recId);
                  String message = "Uploaded file integrity check failed";
                  logger.warn(message);
                  parent.appendReport(message);
                  parent.badOverallStatus();

                  if (logger.isDebugEnabled()) {
                     logger.debug("Appended warning to report, set overall status to bad");
                  }
               } else {
                  doDelete = true;
                  logger.info("Uploaded file integrity checked successfully");
               }
            }
         } catch (IOException e) {
            String message = "IO error ( " + e + ") encountered while reading file: " + filename + " to upload (uploaded so far: " + bytesSoFar + " bytes), giving up, my state: " + this;
            logger.error(message);
            parent.appendReport(message);
            parent.badOverallStatus();

            if (logger.isDebugEnabled()) {
               logger.debug("Appended error to report, set overall status to bad");
            }
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Uploader.doUpload() with boolean: " + doDelete);
      }
      return doDelete;
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
      sb.append(" uploadDir=[").append(this.uploadDir).append("]");
      return sb.toString();
   }

   /**
    * Determines whether uploaded file is valid.
    * 
    * @param   fileId	the ID of the file uploaded
    * @param   data	the file contents
    * @return		true if valid, false if not
    */
   private boolean isFileValid(int fileId, byte[] data) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Uploader.isFileValid() with int: " + fileId + ", byte[]: " + data.length);
      }
      if (data == null || data.length <= 0) {
         if (logger.isDebugEnabled()) {
            logger.debug("No data to check for consistency, assume data is already consistent");
         }
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
              parent.getProcessTimeString());

      try {
         String localHash = Utilities.MD5(data);
         if (logger.isDebugEnabled()) {
            logger.debug("Got local hash: " + localHash);
         }
         String remoteHash = caller.doGetUploadMD5Hash(fileId);
         if (logger.isDebugEnabled()) {
            logger.debug("Got remote hash: " + remoteHash);
         }
         ret = localHash.equals(remoteHash);
      } catch (NoSuchAlgorithmException e) {
         logger.warn("MD5 algorithm is unavailable: " + e);
      } catch (UnsupportedEncodingException e) {
         logger.warn("Encoding unavailable: " + e);
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Uploader.isFileValid() with boolean: " + ret);
      }
      return ret;
   }
}
