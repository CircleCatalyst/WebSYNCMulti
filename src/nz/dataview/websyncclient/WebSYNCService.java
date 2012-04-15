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
 * Defines the web services provided by the KN web servers.
 * Currently only implemented by the WebSYNCSoapService class.
 * Future implementations using different technologies eg xml-rpc should
 * implement this interface.
 * 
 * @author  William Song, Tim Owens
 * @version 1.1.0
 */
public interface WebSYNCService {
   
  
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
    * @throws  javax.xml.soap.SOAPException  if something went wrong
    */
   public int doUploadBlock(String fileName, String[] data, int recId, int blockNum);
   /**
    * Indicates that the file has completed uploading and can be uncompressed.
    *
    * @param   recId	the int representing which record to append to (must be 0 on the first call)
    * @return		the record id
    * @throws  javax.xml.soap.SOAPException  if something went wrong
    */
   public int doUncompressFile(int recId);

   /**
    * Begs the LMS to mave mercy and ignore our past sins
    * @param recId
    */
   public void discardFile(int recId);
   /**
    * Retrieves the last successfully uploaded data block from doUpload() for
    * given file recId.
    * 
    * @param   recId the file recId as returned by doUpload()
    * @return	     the last successfully uploaded block, or -1 if error
    */
   public int doUploadGetLastBlock(int recId);
   
   /**
    * Sends a String containing a report, to be logged in websync_log on the server.
    * 
    * @param   report	the String containing the message to log
    * @param   success	true if the report is a success message, false otherwise
    * @param   where	what the current mechanism is eg MoE upload
    * @return		true if the report was logged ok, false otherwise
    */
   public boolean doRecordLog(String report, boolean success, String where);
   
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
   public boolean doTestUpload(String uploadDir, String downloadDir);
   
   /**
    * Retrieves the MD5 hash of the uploaded file with the give recId, from the server.
    * 
    * @param   recId the record ID of the file uploaded
    * @param   localHash the MD5 hash found here
    * @return	     the MD5 hash of the uploaded file
    */
   public String doGetUploadMD5Hash(int recId, String localHash);
   
   /**
    * Queries the LMS for the result of the last batch.
    * 
    * @return	     Blank string for "Not Finished", "SUCCESS" for a correct upload, other text for an error
    */
   public String doGetBatchResult();
   /**
    * Sends a message to KN to exchange each others status.
    *
    * @return	     KN status
    */
   public String doKeepAlive(String websyncStatus);
}
