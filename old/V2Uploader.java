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

import java.io.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.regex.Pattern;
import org.apache.log4j.NDC;

/**
 * The uploader thread.  Performs upload of data from the upload dir, specified
 * in the application configuration. Uses the batch processing mechanism as
 * defined in the New Zealand SMS-LMS interoperability specification 2
 * 
 * @author  Tim Owens
 * @version 1.1.0
 */
public class V2Uploader extends Uploader {

   private String batchFileName;
   private String batchXmlFileName;
   private int batchIndex;
   private int batchXmlIndex;
   private int statusFile;
   private boolean convertBatch;

   /**
    * Constructor.
    * 
    * @param   c  the Client which spawned this thread
    */
   public V2Uploader(Client c) {
      //Just call the Uploader constructor.
      super(c);
   }

   /**
    * Read the status from the websync_status file
    */
   @Override
   public void getStatus() {
      try {
         FileInputStream fis = new FileInputStream(uploadDir + "/websync_status.txt");
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
         logger.error("Could not read status file" + e.getMessage());
      }
   }

   /**
    * Save the given status in the websync_status file, along with the current batch number.
    *
    * @param stat
    * @param batchnum
    * @return true if it worked.
    */
   public boolean updateStatus(String stat, String batchnum) {
      boolean ret = true;
      try {
         PrintStream ps = new PrintStream(new FileOutputStream(uploadDir + "/websync_status.txt"));
         ps.println(stat);
         ps.println(batchnum);
         ps.close();
         status = stat;
         batchNumber = batchnum;
      } catch (Exception e) {
         logger.error("Could not update status file" + e.getMessage());
         ret = false;
      }
      return ret;
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
      Pattern startMatch = Pattern.compile("^start_\\d{12,14}\\.txt$", java.util.regex.Pattern.CASE_INSENSITIVE);
      Pattern batchMatch = Pattern.compile("^batch_index_\\d{12,14}\\.txt$", java.util.regex.Pattern.CASE_INSENSITIVE);
      Pattern xmlMatch = Pattern.compile("^batch_index_\\d{12,14}\\.xml$", java.util.regex.Pattern.CASE_INSENSITIVE);
      for (int i = 0; i < files.length; i++) {
         String filename = files[i].getName();
         if (startMatch.matcher(filename).find()) {
            batchFileName = filename;
            batchIndex = i;
            convertBatch = true;
         } else if (batchMatch.matcher(filename).find()) {
            batchFileName = filename;
            batchIndex = i;
         } else if (xmlMatch.matcher(filename).find()) {
            batchXmlFileName = filename;
            batchXmlIndex = i;
         } else if (filename.equals("websync_status.txt")) {
            statusFile = i;
            try {
               FileInputStream fis = new FileInputStream(filename);
               BufferedReader br = new BufferedReader(new InputStreamReader(fis));

               String line = "";
               line = br.readLine();
               status = line.trim();

               fis.close();
            } catch (IOException e) {
               if (logger.isDebugEnabled()) {
                  logger.error("Could not read status file");
               }
            }
         }
      }
   }

   /**
    * Looks in the upload_dir specified in the config, and if there are files in there,
    * and the files have not been marked to not upload, will upload to the web server.
    */
   @Override
   public void run() {
      int i;

      NDC.push("Uploader");

      if (logger.isTraceEnabled()) {
         logger.trace("Entered Uploader.run()");
      }
      if (logger.isDebugEnabled()) {
         logger.debug("uploadDir: " + uploadDir);
      }
      File root = null;
      try {
         root = new File(uploadDir);
      } catch (Exception e) {
         logger.error(e.getMessage());
      }
      successfulUploads = 0;
      failedUploads = 0;
      warnings = 0;

      getStatus();

      abort:
      do {
         if (status.equals("") || status.equals("trying again later")) {
            error:
            do {
               if (root.isDirectory() && root.canRead()) {
                  if (logger.isDebugEnabled()) {
                     logger.debug("Directory (" + uploadDir + ") is readable, about to loop through files");
                  }
                  File[] files = root.listFiles();

                  //Do we have a batch file, or are we already processing one?
                  batchFileName = "";
                  batchXmlFileName = "";
                  batchIndex = -1;
                  batchXmlIndex = -1;
                  statusFile = -1;

                  convertBatch = false;
                  huntForBatch(files);
                  if (convertBatch) {
                     //Convert the file to a batch_index, and an XML file
                     File batchFile = new File(uploadDir + "/batch_index" + batchFileName.substring(5));
                     boolean success = files[batchIndex].renameTo(batchFile);
                     if (!success) {
                        String message = "Could not rename batch file " + batchFileName + ", my state: " + this;
                        logger.error(message);
                        parent.appendReport(message);
                        parent.badOverallStatus();

                        logger.debug("Appended error to report, set overall status to bad");
                        break error;
                     }
                     batchFileName = batchFile.getName();
                     String batchXMLName = "batch_index" + batchFileName.substring(11, batchFileName.length() - 4) + ".xml";
                     try {
                        Pattern batchNumberMatch = Pattern.compile("^.*?(\\d{12,14}).*?$");
                        java.util.regex.Matcher batchNumberMatcher = batchNumberMatch.matcher(batchFileName);
                        batchNumberMatcher.find();
                        batchNumber = batchNumberMatcher.group(1);

                        PrintStream ps = new PrintStream(new FileOutputStream(uploadDir + "/" + batchXMLName));
                        //We no longer wrap the data in XML...
//                        ps.println("<Message xmlns=\"urn:smslms:LMSservice\">");
//                        ps.println("   <Parameters>");
//                        ps.println("   <SchoolId>" + parent.getSchoolNumber() + "</SchoolId>");
//                        ps.println("   <BatchNo>" + batchNumber + "</BatchNo>");
//                        ps.println("   <CLSValidationVersionNumberID>1</CLSValidationVersionNumberID>");
//                        ps.println("   </Parameters>");
//                        ps.println("   <BatchIndex>");

                        FileInputStream fis = new FileInputStream(uploadDir + "/" + batchFileName);
                        BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                        String line = "";
                        while ((line = br.readLine()) != null) {
                           ps.println(line);
                        }

                        fis.close();

//                        ps.println("   </BatchIndex>");
//                        ps.println("</Message>");
                        ps.close();
                     } catch (java.io.FileNotFoundException e) {
                        String message = "Could not create batch xml file " + batchXMLName + ", \n" + e.getMessage();
                        logger.error(message);
                        parent.appendReport(message);
                        parent.badOverallStatus();
                        break error;
                     } catch (java.io.IOException e) {
                        String message = "Error writing batch xml file " + batchXMLName + ", \n" + e.getMessage();
                        logger.error(message);
                        parent.appendReport(message);
                        parent.badOverallStatus();
                        break error;
                     }
                     files = root.listFiles();
                     huntForBatch(files);
                  }

                  if (!batchFileName.equals("") && batchXmlIndex >= 0 )
                  {
                     if (logger.isDebugEnabled()) {
                        logger.debug("files to upload: " + files.length);
                     }
                     //Try to upload each of the files in turn, but don't upload the batch file(s) or status file
                     for (i = 0; i < files.length; i++) {
                        if (i != batchIndex && i != batchXmlIndex && i != statusFile) {
                           super.uploadFile(files[i]);
                        }
                     }
                     //If all the files uploaded fine, send the batch file.
                     if(parent.getOverallStatus())
                     {
                         //Lastly, upload the XML file.
                         super.uploadFile(files[batchXmlIndex]);

                        if (parent.getOverallStatus()) {
                           if (!updateStatus("waiting for response", batchNumber)) {
                              break error;
                           }
                        } else {
                           updateStatus("trying again later", batchNumber);
                        }
                     } else {
                        updateStatus("trying again later", batchNumber);
                     }
                  } else {
                     logger.debug("No batch file found.");
                     break abort;
                  }
               } else {
                  // oh dear!
                  String message = "Could not read the upload dir (" + uploadDir + "), my state: " + this;
                  logger.error(message);
                  parent.appendReport(message);
                  parent.badOverallStatus();

                  logger.debug("Appended error to report, set overall status to bad");
                  break error;
               }
            } while (false);

            if(successfulUploads+failedUploads+warnings>0)
            {
               String overview = "Overview of upload run: ";
               overview += "successful uploads: " + successfulUploads;
               overview += ", failed uploads: " + failedUploads;
               overview += ", warnings: " + warnings;
               logger.info("Completed upload run (" + overview + ")");
               parent.appendReport(overview);
            }

            if (failedUploads > 0) {
               logger.error(failedUploads + " file(s) failed to upload during this run");
            }
            if (warnings > 0) {
               logger.warn(warnings + " warning(s) were detected during this run");
            }
         } else {
            //Check for success/fail
            WebSYNCService caller = WebSYNCServiceFactory.getSoapService(
                    parent.getKnUrl(),
                    "http://www.dataview.co.nz/",
                    parent.getAuthenticationKey(),
                    parent.getSchoolName(),
                    parent.getSchoolNumber(),
                    parent.getScheduleUploadString(),
                    parent.getProcessTimeString());

            String result = caller.doGetBatchResult();
            if (logger.isDebugEnabled()) {
               logger.debug("Got result: " + result);
            }
            if (result.equals("SUCCESS")) {
               //Create success message batchnumber-success.txt
               try {
                  PrintStream ps = new PrintStream(new FileOutputStream(uploadDir + "/" + batchNumber + "-success.txt"));
                  ps.println(" ");
                  ps.close();
                  updateStatus("", "");
               } catch (java.io.FileNotFoundException e) {
                  String message = "Could not create success file " + uploadDir + "/" + batchNumber + "-success.txt\n" + e.getMessage();
                  logger.error(message);
                  parent.appendReport(message);
                  parent.badOverallStatus();
               } catch (java.io.IOException e) {
                  String message = "Error writing success file " + uploadDir + "/" + batchNumber + "-success.txt\n" + e.getMessage();
                  logger.error(message);
                  parent.appendReport(message);
                  parent.badOverallStatus();
               }
            } else if (result.equals("")) {
               //Not finished
            } else {
               //Create fail message (batchnumber-reject.txt)
               try {
                  PrintStream ps = new PrintStream(new FileOutputStream(uploadDir + "/" + batchNumber + "-reject.txt"));
                  ps.print(result);
                  ps.close();
                  updateStatus("", "");
               } catch (java.io.FileNotFoundException e) {
                  String message = "Could not create failure file " + uploadDir + "/" + batchNumber + "-reject.txt\n" + e.getMessage();
                  logger.error(message);
                  parent.appendReport(message);
                  parent.badOverallStatus();
               } catch (java.io.IOException e) {
                  String message = "Error writing failure file " + uploadDir + "/" + batchNumber + "-reject.txt\n" + e.getMessage();
                  logger.error(message);
                  parent.appendReport(message);
                  parent.badOverallStatus();
               }
            }
         }
      } while (false);

      logger.trace("Exiting Uploader.run()");

      NDC.pop();
      NDC.remove();
   }
}
