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

import java.io.File;

import org.apache.log4j.NDC;

/**
 * The uploader thread.  Performs upload of data from the upload dir, specified
 * in the application configuration.
 * 
 * @author  Tim Owens, derived from code by William Song
 * @version 1.1.0
 */
public class V1Uploader extends Uploader {

   /**
    * Constructor.
    * 
    * @param   c  the Client which spawned this thread
    */
   public V1Uploader(Client c) {
      //Just call the Uploader constructor.
      super(c);
   }

   /**
    * Looks in the upload_dir specified in the config, and if there are files in there,
    * and the files have not been marked to not upload, will upload to the web server.
    */
   @Override
   public void run() {
      NDC.push("Uploader");

      if (logger.isTraceEnabled()) {
         logger.trace("Entered Uploader.run()");
      }
      if (logger.isDebugEnabled()) {
         logger.debug("uploadDir: " + uploadDir);
      }
      File root = new File(uploadDir);
      successfulUploads = 0;
      failedUploads = 0;
      warnings = 0;

      if (root.isDirectory() && root.canRead()) {
         if (logger.isDebugEnabled()) {
            logger.debug("Directory (" + uploadDir + ") is readable, about to loop through files");
         }
         File[] files = root.listFiles();
         int maxNumUploads = parent.getUploadsPerSession();
         boolean unrestrict = false;
         if (maxNumUploads <= 0) {
            unrestrict = true;
         }
         if (logger.isDebugEnabled()) {
            logger.debug("files to upload: " + files.length + ", maxNumUploads: " + maxNumUploads);
         }
         for (int i = 0; i < files.length && (unrestrict || successfulUploads < maxNumUploads); i++) {
            super.uploadFile(files[i]);
         }
      } else {
         // oh dear!
         String message = "Could not read the upload dir (" + uploadDir + "), my state: " + this;
         logger.error(message);
         parent.appendReport(message);
         parent.badOverallStatus();

         if (logger.isDebugEnabled()) {
            logger.debug("Appended error to report, set overall status to bad");
         }
      }

      String overview = "Overview of upload run: ";
      overview += "successful uploads: " + successfulUploads;
      overview += ", failed uploads: " + failedUploads;
      overview += ", warnings: " + warnings;
      logger.info("Completed upload run (" + overview + ")");
      parent.appendReport(overview);

      if (failedUploads > 0) {
         logger.error(failedUploads + " file(s) failed to upload during this run");
      }
      if (warnings > 0) {
         logger.warn(warnings + " warning(s) were detected during this run");
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Uploader.run()");
      }
      NDC.pop();
      NDC.remove();
   }
}
