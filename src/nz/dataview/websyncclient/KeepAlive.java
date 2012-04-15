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

import org.apache.log4j.Logger;
import java.util.*;
import java.io.*;

import java.util.regex.Pattern;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import net.iharder.base64.Base64;
import java.text.SimpleDateFormat;

public class KeepAlive extends Thread {

	/**
	 * The logger!
	 */
	public Logger logger;
	/**
	 * The Client which spawned this thread.
	 */
	public Client parent;
	/**
	 * The path to the directory at which the files to be uplaoded reside.
	 */
	public String uploadDir;
   private String startFileName;
   private String batchXmlFileName;
	private String batchNumber;
   private String status = "";
   private int count=0;
	private int filesfound=0;

	/**
	 * Constructor.
	 *
	 * @param   c  the Client which spawned this thread
	 */
	public KeepAlive(Client c) {
      logger = Logger.getLogger(c.school);
		if (logger.isTraceEnabled()) {
			logger.trace("Entered KeepAlive.contructor() with Client: " + c);
		}
		parent = c;

		if (logger.isTraceEnabled()) {
			logger.trace("Exiting KeepAlive.constructor()");
		}
	}
   /**
    * Read the status from the websync_status file
    */
   public void getStatus() {
      try {
         FileInputStream fis = new FileInputStream(parent.controlDirectory + File.separator + "websync_status.txt");
         BufferedReader br = new BufferedReader(new InputStreamReader(fis));

         String line = "";
         line = br.readLine();
         status = line.trim();
         line = br.readLine();
         batchNumber = line.trim();

         fis.close();
      } catch (FileNotFoundException e) {
         //No need for an error.
      } catch (IOException e) {
         logger.error("0202: Could not read status file (keep alive message).");
			if(logger.isDebugEnabled())
			{
				logger.debug("0202: Could not read status file (keep alive message):\n" + e.getMessage());
			}
      }
   }

   /**
    * Given an array of files in the upload folder, we need to identify the
    * batch file, which could be in three different states depending on the
    * status of the upload.
    *
    * While we're at it, load the status file if found.
    *
    * @param files
    */
   private void huntForBatch(File[] files) {
      Pattern startMatch = Pattern.compile("^start_(\\d{12,14})\\.txt$", java.util.regex.Pattern.CASE_INSENSITIVE);
      Pattern xmlMatch = Pattern.compile("^batch_index_(\\d{12,14})\\.xml$", java.util.regex.Pattern.CASE_INSENSITIVE);
      Pattern normalMatch = Pattern.compile("^\\d{12,14}.*\\.xml$", java.util.regex.Pattern.CASE_INSENSITIVE);
      Calendar rightNow = Calendar.getInstance();
      String batchindex="";
      
      startFileName = "";
      batchXmlFileName = "";
      
      for (int i = 0; i < files.length; i++) {
         String filename = files[i].getName();
         java.util.regex.Matcher m=xmlMatch.matcher(filename);
         if (m.find() && files[i].lastModified()+600000 < rightNow.getTimeInMillis()) {
            //Pick the earliest batch
            if(batchindex.equals("") || batchindex.compareTo(m.group(1))>0)
            {
               batchXmlFileName = filename;
               batchindex=m.group(1);
            }
         }
      }
      if (batchindex.equals("")) {
         //Do we have a new batch?
         for (int i = 0; i < files.length; i++) {
            String filename = files[i].getName();
            java.util.regex.Matcher m = startMatch.matcher(filename);
            if (m.find() && files[i].lastModified()+600000 < rightNow.getTimeInMillis()) {
               //Pick the earliest batch
               if (batchindex.equals("") || batchindex.compareTo(m.group(1)) > 0) {
                  startFileName = filename;
                  batchindex=m.group(1);
               }
            }
         }
      }
      
      if(batchNumber==null || batchNumber.equals(""))
      {
         batchNumber=batchindex;
      }
      
		filesfound=0;
		if(batchNumber!=null && !batchNumber.equals(""))
		{
			for (int i = 0; i < files.length; i++) {
				String filename = files[i].getName();

				if (!files[i].isDirectory() && 
				    normalMatch.matcher(filename).find() &&
					 filename.substring(0, batchNumber.length()).equals(batchNumber)) filesfound++;
			}
		}
   }

	@Override
	public void run() {
		HashMap websyncStatus = new HashMap(0);
		String commentary="";
		int filecount=0;

		if (logger.isTraceEnabled()) {
			logger.trace("Entered KeepAlive.run()");
		}
		uploadDir = parent.getUploadDir();
		File root = null;
      try {
         root = new File(uploadDir);
      } catch (Exception e) {
         logger.error("0304: Upload folder could not be read from config.");
      }

		do {
			try {
				//batchNumber="";
				status="";
				filecount=0;

				getStatus();
				if (root.isDirectory() && root.canRead()) {
					if (logger.isDebugEnabled()) {
						logger.debug("Directory (" + uploadDir + ") is readable, about to loop through files");
					}
					File[] files = root.listFiles();
					count=files.length;

					//Do we have a batch file, or are we already processing one?
					startFileName = "";
					batchXmlFileName = "";
					huntForBatch(files);
				}
				if (!batchXmlFileName.equals("")){
					try{
						FileInputStream fis = new FileInputStream(uploadDir + File.separator + batchXmlFileName);
						BufferedReader br = new BufferedReader(new InputStreamReader(fis));

						String line = "";
						while ((line = br.readLine()) != null)
						{
							if(line.length()>0) filecount++;
						}
						fis.close();
					} catch(Exception e){}
				}
				commentary=status;
				if(commentary.equals(""))
				{
					if(!startFileName.equals(""))
					{
						commentary="New batch arrived";
					} else if (!batchXmlFileName.equals("")){
						commentary="Batch "+batchNumber+" contains "+(filecount+1)+" files. - "+filesfound+" files remaining.";
					} else
					{
						commentary="No batch present.";
					}
				} else if(commentary.equals("waiting for response"))
				{
					commentary="Batch "+batchNumber+" containing "+(filecount+1)+" files has been sent, waiting for response.";
				} else if(commentary.equals("trying again later"))
				{
					commentary="Batch "+batchNumber+" containing "+(filecount+1)+" files waiting to be sent - "+filesfound+" files remaining. Some messages did not send - trying again later.";
				}

				try{
					FileInputStream fis = new FileInputStream(parent.controlDirectory + File.separator+"last_response.txt");
					BufferedReader br = new BufferedReader(new InputStreamReader(fis));

					String lastStatus = br.readLine();
					String lastBatch = br.readLine();
					if(!lastBatch.equals(""))
					{
						Date date=new Date();
						File file = new File(parent.controlDirectory + File.separator + "last_response.txt");
						SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy, HH:mm");
						date.setTime(file.lastModified());
						commentary+= "<br>Last batch "+lastBatch+" completed "+lastStatus+" at "+formatter.format(date);
					}
					fis.close();
				} catch(Exception e){}

				WebSYNCService caller = WebSYNCServiceFactory.getSoapService(
						  parent.getKnUrl(),
						  "http://www.dataview.co.nz/",
						  parent.getAuthenticationKey(),
						  parent.getSchoolName(),
						  parent.getSchoolNumber(),
						  parent.getScheduleUploadString(),
						  parent.getProcessTimeString());
				Calendar calendar = Calendar.getInstance();

				websyncStatus.put("batchNumber",batchNumber);
				websyncStatus.put("startFile",!startFileName.equals(""));
				websyncStatus.put("batchXmlFile",!batchXmlFileName.equals(""));
				websyncStatus.put("status",status);
				websyncStatus.put("commentary","<p>"+commentary+"<p>");
				websyncStatus.put("date",(int)(calendar.getTime().getTime()/1000));
				websyncStatus.put("files_total",count);
				websyncStatus.put("filecount",filecount);
				websyncStatus.put("filesfound",filesfound);
				websyncStatus.put("websync_version",parent.WEBSYNC_VERSION);
				websyncStatus.put("upload_byte_limit",parent.getUploadByteLimit());
				websyncStatus.put("upload_dir",parent.getUploadDir());
				websyncStatus.put("process_time",parent.getProcessTime());

				byte[] serialized=PHPSerializer.serialize(websyncStatus);
				parent.websyncStatusString=Base64.encodeBytes(serialized,Base64.DONT_BREAK_LINES);
				
				parent.knStatusString = caller.doKeepAlive(parent.websyncStatusString);
				if(parent.knStatusString!=null)
				{
					try{
						parent.knStatus = PHPSerializer.unserialize(Base64.decode(parent.knStatusString));
					} catch(UnSerializeException e)
					{
						logger.error("0201: Could not unserialize keep alive response");
						if (logger.isDebugEnabled()) {
							logger.debug("0201: Could not unserialize keep alive response : \n" + parent.knStatusString);
						}
					}
				}

				//Grab the status again in case it's just changed
				getStatus();
				//Sometimes the status can get corrupt...?
				if(!status.equals("") && !status.equals("waiting for response") && !status.equals("trying again later"))
				{
					status="";
               parent.updateStatus(status);
				}

				//If KN is sitting there twiddling its thumbs (no files to process and no status to return) then stop waiting for it.
				//OR, if KN has part of a new batch, doing nothing, and we're somehow waiting for a response, then clear our status. It's probably that guy messing with his computer again.
				if(parent.knStatus!=null && status.equals("waiting for response"))
				{
					try{
						int outstanding_files=Integer.parseInt(((HashMap)parent.knStatus).get("outstanding_files").toString());
						String upload_status=((HashMap)parent.knStatus).get("status").toString();
						Object new_batch=((HashMap)parent.knStatus).get("new_batch");
						if(upload_status.equals("") && 
							(outstanding_files==0 || !new_batch.toString().equals("false"))) 
                  {
                     status="";
                     parent.updateStatus(status);
                  }
					} catch (Exception e) {
					}
				}
				//Vice versa, if KN is busy processing, and we're not expecting something, then await the response
				else if(parent.knStatus != null && status.equals(""))
				{
					try{
						int outstanding_files=Integer.parseInt(((HashMap)parent.knStatus).get("outstanding_files").toString());
						String upload_status=((HashMap)parent.knStatus).get("status").toString();
						if(upload_status.equals("complete") || upload_status.equals("error") || outstanding_files!=0 && !upload_status.equals(""))
						{
							status = "waiting for response";
                     parent.updateStatus(status);
							String bn=((HashMap)parent.knStatus).get("lastest_batch_number").toString();
							if(!bn.equals(""))
                     {
                        batchNumber=bn;
                        parent.updateBatchNumber(batchNumber);
                     }
                     
						}
					} catch (Exception e) {
					}
				}

 				parent.updateMessage(parent.websyncStatusString, parent.knStatusString);

				//long sleepInterval = 10000;
				long sleepInterval = 600000;
				Thread.sleep(sleepInterval);

			} catch (InterruptedException e) {
				break;
			}

		} while (true);
	}
}
