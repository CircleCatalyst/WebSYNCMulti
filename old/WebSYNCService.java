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
   public int doUpload(String fileName, String[] data, int recId);
   
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
   public byte[] doDownload();
   
   /**
    * Retrieves the record ID of the last successfully downloaded file.
    * 
    * @return  the ID if it exists, -1 otherwise
    */
   public int getDownloadedFileId();
   
   /**
    * Retrieves the filename of the last successfully downloaded file.
    * 
    * @return  the filename if it exists, null otherwise
    */
   public String getDownloadedFileName();
   
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
    * Sends a raw SQL insert/update statement to the server to execute.
    * 
    * @param   sql   the String containing the sql statement
    * @return	     true if executed successfully, false otherwise
    */
   public boolean doUpdateTableRaw(String sql);
   
   /**
    * Sends a keep alive message to the server.  Could be used for testing connection.
    * 
    * @return  true if successful, false otherwise
    */
   public boolean doKeepAlive();
   
   /**
    * Retrieves the last time the service ran.
    * 
    * @return  the Date representing the last time the service ran and the
    * current time, or null if error
    */
   public java.util.Date[] doGetUploadStatus();
   
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
    * @return	     the MD5 hash of the uploaded file
    */
   public String doGetUploadMD5Hash(int recId);
   
   /**
    * Retrieves the MD5 hash of the downloaded file with the given recId, from the server.
    * 
    * @param   recId the record ID of the file downloaded
    * @return	     the MD5 hash of the downloaded file
    */
   public String doGetDownloadMD5Hash(int recId);
   
   /**
    * Queries the LMS for the result of the last batch.
    * 
    * @return	     Blank string for "Not Finished", "SUCCESS" for a correct upload, other text for an error
    */
   public String doGetBatchResult();
}
