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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.net.UnknownHostException;

import java.rmi.RemoteException;

import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Pattern;

import java.security.Security;

import javax.mail.*;
import javax.mail.internet.*;


import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggingEvent;


/**
 * The main application class.  Initiates all web service calls, log records,
 * etc.
 * 
 * @author  William Song, Tim Owens
 * @version 1.1.2
 */
public class Client {

   /**
    * The WebSYNC version.
    */
   public static final String WEBSYNC_VERSION = "1.1.2";
   /**
    * The logger!
    */
   private static Logger logger = Logger.getLogger(Client.class);
   /**
    * The system configurations.  <strong>MUST</strong> be initialised before anything else.
    */
   private Properties systemConfig;
   /**
    * Application configurations contain the settings which can be changed via the GUI.
    */
   private Properties appConfig;
   /**
    * The path at which the application configuration file lies.
    */
   private String appConfigFilePath;
   /**
    * The path at which the system configuration file lies.
    */
   private String systemConfigFilePath;
   /**
    * The path at which the log configuration file lies.
    */
   private String logConfigFilePath;
   /**
    * Overall report of the application execution, which should be uploaded to the server for records.
    */
   private String report;
   /**
    * Represents the current status of the WebSYNC transfer as far as the <strong>SERVER</strong> is concerned.
    * True if all good, false otherwise.
    */
   private boolean overallRunStatus;
   /**
    * Reference to the running main thread.
    */
   private Thread myself;
   /**
    * Reference to the uploader thread.
    */
   private Uploader uploader;
   /**
    * Reference to the downloader thread.
    */
   private Downloader downloader;
   /**
    * Whether to force run or not.
    */
   private boolean forced;

   /**
    * Constructor.  Initialises variables etc.
    */
   public Client() throws RemoteException {
      try {
         // note this value comes from the command line
         systemConfigFilePath = System.getProperty("nz.dataview.websyncclient.sysconf_file", "./system.properties");
         initialiseConfig();
         initialiseLogger();
         initialiseProxy();
      } catch (IOException e) {
         String message = "Fatal error: failed to initialise system configurations";
         System.err.println(message);
         setOverallStatus(false);
         appendReport(message);
         e.printStackTrace();
         System.exit(1);
      }
      myself = Thread.currentThread();

      // register shutdown hook
      Runtime.getRuntime().addShutdownHook(new Thread() {

         @Override
         public void run() {
            onShutDown();
         }
      });
   }

   /**
    * Begins the applicaton proper.
    */
   public void run() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.run()");
      }
      int runFailCount = 0;
      while (true) {

         if (logger.isDebugEnabled()) {
            logger.debug("Iterating through infinite while loop");         // re-initialise configurations
         }
         try {

            initialiseConfig();
            initialiseLogger();
            logger.info("Initialised config and logger OK");
            if (logger.isTraceEnabled()) {
               logger.trace("My state: " + this);            // note: this cannot be done on the fly - the http.proxySet property
            // is only read once at startup.  Therefore, the program must be restarted.
//	    initialiseProxy();
            }
         } catch (IOException e) {
            String message = "Error: failed to re-initialise system configurations (1)" + e.getMessage() + ", my state: " + this;
            if (logger != null) {
               logger.error(message);
            }
            System.err.println(message);
            try {
               //Wait a bit, try again
               Thread.sleep(1000);
            } catch (Exception e1) {
            }
            continue;
         }

         System.setProperty("java.protocol.handler.pkgs", "com.sun.net.ssl.internal.www.protocol");
         Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());

         // clear the report
         submitReport("", false);
         resetOverallStatus();
         logger.info("Reset report and overall status OK");
         if (logger.isTraceEnabled()) {
            logger.trace("My state: " + this);         // how long have we been sleep?
         }
         WebSYNCService caller = WebSYNCServiceFactory.getSoapService(
                 getKnUrl(),
                 "http://www.dataview.co.nz/",
                 getAuthenticationKey(),
                 getSchoolName(),
                 getSchoolNumber(),
                 getScheduleUploadString(),
                 getProcessTimeString());
         Date[] dates = caller.doGetUploadStatus();
         Date date = null;
         Date now = null;

         logger.info("Retrieved last run time: " + dates[0] + " and current time: " + dates[1]);
         if (dates[0] == null) {
            runFailCount++;
            // this is an indication that something is wrong with the server
            // or there is a bug!
            int failedUploadCount = getFailedUploadCount();
            String message = "Could not retrieve last run time (attempt " + runFailCount + " of " + failedUploadCount + ")";
            logger.warn(message);
            String reportsEmail = getReportsEmail();
            if (runFailCount == failedUploadCount && !reportsEmail.equals("")) {
               //Send report email
               Properties props = new Properties();
               props.setProperty("mail.transport.protocol", "smtp");
               props.setProperty("mail.host", getSmtpHost());
               if (!getSmtpUser().equals("")) {
                  props.setProperty("mail.user", getSmtpUser());
                  props.setProperty("mail.password", getSmtpPassword());
               }
               try {
                  Session mailSession = Session.getDefaultInstance(props, null);
                  Transport transport = mailSession.getTransport();

                  MimeMessage msg = new javax.mail.internet.MimeMessage(mailSession);
                  msg.setSubject("WebSYNC cannot connect");
                  msg.setContent("WebSYNC has tried " + runFailCount + " time(s) to connect to the LMS, but has not been successful.\n\nIt will continue to attempt to connect.", "text/plain");
                  msg.addRecipient(Message.RecipientType.TO,
                          new InternetAddress(reportsEmail));
                  msg.setFrom(new InternetAddress("support@dataview.co.nz"));

                  transport.connect();
                  transport.sendMessage(msg,
                          msg.getRecipients(Message.RecipientType.TO));
                  transport.close();
               } catch (Exception e) {
                  //Ah well. Did our best.
               }
            }
            try {
               //Doze off for a minute...
               Thread.sleep(1000 * 60);
            } catch (InterruptedException e) {
               logger.info("Woken up by GUI");
            }
            continue;
         }
         date = dates[0];
         now = dates[1];

         long lastRun = date.getTime();
         long currentTime = 0;
         if (now != null) {
            currentTime = now.getTime();
         } else {
            currentTime = System.currentTimeMillis();
         }
         long actualSleepTime = currentTime - lastRun;

         // and how long were we supposed to sleep?
         long scheduledSleepTime = getScheduledUploadInterval();

         // have we overslept? (most of the time, this should be true, unless woken up manually via GUI, or restarted)
         boolean sleptEnough = actualSleepTime >= scheduledSleepTime;

         boolean timeConditionsOk = timeConditionOk(currentTime);

         // only run if we are forced, or (we have slept enough and time conditions are met)
         boolean doRun = forced || (sleptEnough && timeConditionsOk);
         // prevent an infinite loop of sleep
         // only possible if schedule_upload is 5 (1 day)
         // if time conditions aren't met for the 24 hr sleep time, it will 
         // slumber... FOREVER... flag for a reset

         boolean resetSleep = !timeConditionsOk && getScheduleUpload() == 5;
         if (logger.isDebugEnabled()) {
            logger.debug("resetSleep: " + resetSleep);         // always instantiate new objects - they are threads!
         }
         if (getSchemaVersion().equals("1")) {
            uploader = new V1Uploader(this);
         } else {
            uploader = new V2Uploader(this);
            uploader.getStatus();
            if (uploader.status.equals("waiting for response")) {
               //When waiting for a response, we have a different sleep interval, and ignore the time conditions
               scheduledSleepTime = getResponseTime() * 60000;
               sleptEnough = actualSleepTime >= scheduledSleepTime;
               timeConditionsOk = true;
               doRun = forced || (sleptEnough && timeConditionsOk);
            }
         }
         if (logger.isDebugEnabled()) {
            logger.debug("lastRun: " + lastRun + ", currentTime: " + currentTime + ", actualSleepTime: " + actualSleepTime + ", scheduledSleepTime: " + scheduledSleepTime);         // also make sure time conditions are met
            logger.debug("forced: " + forced + ", sleptEnough: " + sleptEnough + ", timeConditionsOk: " + timeConditionsOk);
         }
         //The downloader is only applicable to Version 1, but we'll run it anyway if a directory is supplied
         if (getDownloadDir().equals("")) {
            downloader = null;
         } else {
            downloader = new Downloader(this);
         }

         uploader.reloadConfig();
         if (downloader != null) {
            downloader.reloadConfig();
         }
         if (logger.isTraceEnabled()) {
            logger.trace("Created uploader thread: " + uploader);
            if (downloader != null) {
               logger.trace("Created downloader thread: " + downloader);
            }
         }

         long start = System.currentTimeMillis();

         if (doRun) {


            logger.info("*****Starting run*****");

            uploader.start();
            if (logger.isDebugEnabled()) {
               logger.debug("uploder thread started");
            }
            if (downloader != null) {
               downloader.start();
               if (logger.isDebugEnabled()) {
                  logger.debug("downloader thread started");
               }
            }
            try {
               // wait for the upload and download threads to finish before sleeping
               finishThreads();

               if (forced) {
                  logger.info("User initiated transfer completed");
               } else {
                  logger.info("Scheduled transfer completed");
               }

               long end = System.currentTimeMillis();

               double timeTaken = (end - start) * 0.001;
               if (report.length() > 2) {
                  String message = "*****Time taken for this run: " + timeTaken + " second(s)*****";
                  logger.info(message);
                  appendReport(message);

                  // once the threads have finished, submit the report of this run to the server
                  caller = WebSYNCServiceFactory.getSoapService(
                          getKnUrl(),
                          "http://www.dataview.co.nz/",
                          getAuthenticationKey(),
                          getSchoolName(),
                          getSchoolNumber(),
                          getScheduleUploadString(),
                          getProcessTimeString());
                  if (!caller.doRecordLog(report, overallRunStatus, "MoE Upload")) {
                     logger.error("Failed to submit report of this run to server");
                  } else if (logger.isDebugEnabled()) {
                     logger.debug("Report submitted OK");
                  }
               }
            } catch (Exception e) {
               String message = "Fatal error: unexpected error encountered, exit program (1) " + e.getStackTrace() + ", my state: " + this;
               logger.fatal(message);
               System.exit(1);
            }
         } else {
            logger.info("Not running this time round");
         }

         // clear the forced flag
         forced = false;

         try {
            // refresh the config

            initialiseConfig();
            if (logger.isTraceEnabled()) {
               logger.trace("Saved and refreshed config OK, my state: " + this);            // now go to sleep   
            }
            long sleepInterval = 0;
            if (timeConditionsOk) {
               sleepInterval = getSleepInterval(scheduledSleepTime, actualSleepTime);
            } else {
               sleepInterval = scheduledSleepTime;
            }
            if (resetSleep) {
               // halve it to hopefully get it out of a potential infinite loop of slumber
               sleepInterval /= 2;
            }

            logger.info("Going to sleep for " + (sleepInterval / 1000) + " seconds...");
            Thread.sleep(sleepInterval);
            logger.info("Woke myself up after " + (sleepInterval / 1000) + "seconds...");

            logger.info("Scheduled transfer initiated");

         } catch (InterruptedException e) {
            logger.info("Woken up by GUI");
         } catch (IllegalArgumentException e) {
            logger.info("Do not sleep (slept long enough), continue with next run");
         } catch (IOException e) {
            String message = "Warning: failed to re-initialise system configurations: " + e.getMessage() + ", my state: " + this;
            logger.warn(message);
         }
      }
   }

   /**
    * Gets called when the system is about to exit.  Do any necessary operations here
    * before shutting down.
    * At the moment it doesn't do a lot.
    */
   private void onShutDown() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.onShutDown()");
      }
      logger.info("Waiting for uploader and downloader threads to finish");
      finishThreads();
      logger.info("Exiting cleanly");

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.onShutDown()");
      }
   }

   /**
    * Waits for the uploader and downloader threads to finish.  After this method
    * is called, the uploader and downloader objects will be effectively null.
    */
   private void finishThreads() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.finishThreads()");
      }
      try {
         uploader.join();
         if (logger.isDebugEnabled()) {
            logger.debug("uploader thread finished");
         }
         if (downloader != null) {
            downloader.join();
            if (logger.isDebugEnabled()) {
               logger.debug("downloader thread finished");
            }
         }
      } catch (InterruptedException e) {
         logger.warn("Failed to wait for threads to finish, " + e);
         downloader = null;
         uploader = null;
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.finishThreads()");
      }
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
      sb.append(" appConfigFilePath=[").append(this.appConfigFilePath).append("]");
      sb.append(" forced=[").append(this.forced).append("]");
      sb.append(" logConfigFilePath=[").append(this.logConfigFilePath).append("]");
      sb.append(" overallRunStatus=[").append(this.overallRunStatus).append("]");
      sb.append(" systemConfigFilePath=[").append(this.systemConfigFilePath).append("]");
      return sb.toString();
   }

   /**
    * Determines whether the service is currently running or not (sleeping).
    * 
    * @return  true  if the service is running, false otherwise
    * @throws  java.rmi.RemoteException
    */
   public boolean isRunning() throws RemoteException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.isRunning()");
      }
      boolean ret = false;
      // make sure I am running or waiting for the uploader/downloader threads
      if (myself != null) {
         Thread.State currentState = myself.getState();
         ret = currentState.equals(Thread.State.RUNNABLE) || currentState.equals(Thread.State.WAITING);
         if (logger.isDebugEnabled()) {
            logger.debug("currentState: " + currentState);
         }
      } else if (logger.isDebugEnabled()) {
         logger.debug("myself is null");
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.isRunning() with boolean: " + ret);
      }
      return ret;
   }

   /**
    * Wakes up the service and starts its run immediately.
    * Does nothing if the service is running already.
    * 
    * @throws  java.rmi.RemoteException
    */
   public void startRun() throws RemoteException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.startRun()");
      }
      if (myself != null && !isRunning()) {
         forced = true;
         myself.interrupt();
         logger.info("Transfer initiated by user");
      } else if (logger.isDebugEnabled()) {
         logger.debug("myself is null OR Client.isRunning() returned true");
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.startRun()");
      }
   }


   /**
    * Retrieves the latest log entries in one big String.
    * 
    * @return  a big String containing all the log messages
    * @throws  java.rmi.RemoteException
    */
   public String getLatestLog() throws RemoteException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getLatestLog()");
      }
      StringBuffer buffer = new StringBuffer();

      // find our MemoryAppender
      Enumeration en = logger.getAllAppenders();
      if (en != null) {
         while (en.hasMoreElements()) {
            Appender ap = (Appender) en.nextElement();
            if (ap instanceof MemoryAppender) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Found MemoryLogger, compiling latest entries");
               }
               MemoryAppender appender = (MemoryAppender) ap;
               LoggingEvent ev;
               while ((ev = appender.poll()) != null) {
                  String date = new Date(ev.getTimeStamp()).toString();
                  String level = ev.getLevel().toString();
                  String message = (String) ev.getMessage();
                  buffer.append(date).append(" - ").append("(").append(level).append("): ").append(message).append("\n");
               }
            }
         }
      }

      String ret = buffer.toString();

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getLatestLog() with String: " + ret);
      }
      return ret;
   }

   /**
    * Adds a new log entry.  Level represents one of the following:
    * <ul>
    *	 <li>FATAL</li>
    *	 <li>ERROR</li>
    *	 <li>WARN</li>
    *	 <li>INFO</li>
    *	 <li>DEBUG</li>
    *	 <li>TRACE</li>
    * </ul>
    * 
    * @param   level	the level of the log entry
    * @param   message	the message of the log entry
    * @return		true if the entry was added successfully, false otherwise
    * @throws  java.rmi.RemoteException
    */
   public boolean writeLog(String level, String message) throws RemoteException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.writeLog() with String: " + level + ", message: " + message);
      }
      boolean ret = false;

      if (level != null && level.length() > 0) {
         if (level.equalsIgnoreCase("trace") && logger.isTraceEnabled()) {
            logger.trace(message);
            ret = true;
         } else if (level.equalsIgnoreCase("debug") && logger.isDebugEnabled()) {
            logger.debug(message);
            ret = true;
         } else if (level.equalsIgnoreCase("info")) {
            logger.info(message);
            ret = true;
         } else if (level.equalsIgnoreCase("warn")) {
            logger.warn(message);
            ret = true;
         } else if (level.equalsIgnoreCase("error")) {
            logger.error(message);
            ret = true;
         } else if (level.equalsIgnoreCase("fatal")) {
            logger.fatal(message);
            ret = true;
         }
      }

      if (ret && logger.isDebugEnabled()) {
         logger.debug("Logged new log entry from GUI");
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.writeLog() with boolean: " + ret);
      }
      return ret;
   }

   /**
    * Performs a test to see if the connection to the web server is up and running.
    * 
    * @return  true if connection is ok, false otherwise
    * @throws  java.rmi.RemoteException
    * @throws  java.net.UnknownHostException if DNS lookup fails
    * @throws  java.net.IOException	     if test hosts cannot be reached
    */
   public boolean testConnection() throws RemoteException, UnknownHostException, IOException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.testConnection()");
      }
      logger.info("Test initiated by user");

      // re-initialise config
      logger.info("Reading configuration");
      initialiseConfig();

      boolean okSoFar = false;

//      // first 'ping' Google
//      okSoFar = Utilities.isReachable("http://www.google.com");
//      if (logger.isDebugEnabled())
//	 logger.debug("Utilities.isReachable(http://www.google.com) returned OK without exception");
//      
//      if (okSoFar) {
//	 logger.info("http://www.google.com was reachable");
//	 
//	 // then 'ping' the KN
//	 String knUrl = getKnUrl();
//
//	 okSoFar = Utilities.isReachable(knUrl);
//	 if (logger.isDebugEnabled())
//	    logger.debug("Utilities.isReachable(" + knUrl + ") returned OK without exception");
//	 
//	 if (okSoFar) {
//	    logger.info(knUrl + " was reachable");

      // then call a web service
      WebSYNCService caller = WebSYNCServiceFactory.getSoapService(
              getKnUrl(),
              "http://www.dataview.co.nz/",
              getAuthenticationKey(),
              getSchoolName(),
              getSchoolNumber(),
              getScheduleUploadString(),
              getProcessTimeString());
//	    okSoFar = caller.doKeepAlive();
//	    logger.info("Keep alive sent successfully");
      okSoFar = caller.doTestUpload(getUploadDir(), getDownloadDir());

      if (okSoFar) {
         logger.info("Test transaction to " + getKnUrl() + " was completed successfully");
      }
//	 }
//      }      

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.testConnection() with boolean: " + okSoFar);
      }
      return okSoFar;
   }

   /**
    * Calculates the number of milliseconds to sleep, depending on the given parameters.
    * 
    * @param   scheduledSleepTime   the number of milliseconds thread was supposed to sleep
    * @param   actualSleepTime	    the number of milliseconds thread actually slept
    * @return			    the number of milliseconds thread should sleep this time (will be -ve if not sleep)
    */
   public long getSleepInterval(long scheduledSleepTime, long actualSleepTime) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getSleepInterval() with long: " + scheduledSleepTime + ", long: " + actualSleepTime);
      }
      long ret = scheduledSleepTime;

      if (actualSleepTime > scheduledSleepTime) {
         ret = scheduledSleepTime - (actualSleepTime - scheduledSleepTime);
         if (logger.isDebugEnabled()) {
            logger.debug("actualSleepTime is greater than scheduledSleepTime");
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getSleepInterval() with long: " + ret);
      }
      return ret;
   }

   /**
    * Determines whether the given timestamp is considered OK to run the program.
    * The program should only run depending on the configuration 'process_time' setting.
    * 
    * @param   currentTime the time to consider
    * @return		   true if it is ok to run at this time, false otherwise
    */
   private boolean timeConditionOk(long currentTime) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.timeConditionOk() with long: " + currentTime);
      }
      boolean ret = false;

      int option = getProcessTime();
      if (logger.isDebugEnabled()) {
         logger.debug("Got process_time: " + option);
      }
      String debugMessage = null;
      switch (option) {
         case 0:
            // no restrictions
            debugMessage = "0: no restrictions";
            ret = true;
            break;
         case 1:
            // only during school hours (8am - 4pm) m-f
            debugMessage = "1: only during school hours";
            ret = Utilities.isSchoolHours(currentTime);
            break;
         case 2:
            // only during non-school hours (8am - 4pm) or weekend
            debugMessage = "2: only during non-school hours";
            ret = !Utilities.isSchoolHours(currentTime);
            break;
         default:
            // doesn't hurt to run...
            debugMessage = "Cannt find option, default (no restrictions)";
            ret = true;
      }
      if (logger.isDebugEnabled() && debugMessage != null) {
         logger.debug(debugMessage);
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.timeConditionOk() with boolean: " + ret);
      }
      return ret;
   }

   /**
    * Manipulates the report, which will be sent to the server for logging.
    * 
    * @param   msg	the message to set/append
    * @param   append	set to true to append the message, false to overwrite the current report
    */
   public synchronized void submitReport(String msg, boolean append) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.submitReport() with String: " + msg + ", boolean: " + append);
      }
      if (append) {
         report += msg + "\n";
      } else {
         report = msg + "\n";
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.submitReport()");
      }
   }

   /**
    * Appends a message to the report to be sent to the server for logging.
    * 
    * @param   msg   the message to append
    */
   public void appendReport(String msg) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.appendReport()");
      }
      submitReport(msg, true);

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.apendReport())");
      }
   }

   /**
    * Sets the overall status.  Do not call this directly, but rather, call <code>resetOverallStatus()</code>
    * which is all you should really need.
    * 
    * @param   b  the status to set - true if all ok, false if errors exist
    */
   private synchronized void setOverallStatus(boolean b) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.setOverallStatus() with boolean: " + b);
      }
      overallRunStatus = b;

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.setOverallStatus()");
      }
   }

   /**
    * Sets the 'error flag' to be uploaded to the server as a report.
    */
   public void badOverallStatus() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.badOverallStatus()");
      }
      setOverallStatus(false);

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.badOverallStatus()");
      }
   }

   /**
    * Returns the success flag
    */
   public boolean getOverallStatus() {
      return overallRunStatus;
   }

   /**
    * Clears the 'error flag' to be uploaded to the server as a report.
    */
   private void resetOverallStatus() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.resetOverallStatus()");
      }
      setOverallStatus(true);

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.resetOverallStatus()");
      }
   }

   /**
    * Initialises the logger object.
    */
   private void initialiseLogger() {
      // ideally this should be done before anything else
      // to prevent it from producing the "Please initialize the log4j system properly." error
      LogManager.resetConfiguration();
      PropertyConfigurator.configure(logConfigFilePath);

      /*
       * Lets establish some rules for logging:
       * -method entries/exits should be logged as trace(), with param/ret values
       * -all TRACE and DEBUG level logs should be wrapped around if (traceEnabled() etc
       * -TRACE should be enough to follow the exact execution process (finest level of granularity)
       * -DEBUG should be for debugging purposes - should represent logic decisions etc
       * -INFO should be for informative log events, perhaps to indicate progress
       * -WARN should be for problems which are recoverable - not ideal situation, but program safe to continue
       * -ERROR should be for genuine problems - program can continue, but unsafe to do so
       * -FATAL should be for unrecoverable events which should result in system exiting
       */
      logger.info("Logger initialised OK");
   }

   /**
    * Initialises the application configurations into the <code>Properties</code> object.
    * <strong>This should be the first method call in the constructor.</strong>
    * All methods assume that this is called first before anything else.
    * 
    * @throws  java.io.IOException  thrown if IO error occurred while opening the configuration file
    */
   private void initialiseConfig() throws IOException {
      if (logger != null && logger.isTraceEnabled()) {
         logger.trace("Entered Client.initialiseConfig()");
      }
      if (systemConfig == null) {
         systemConfig = new Properties();
      }
      if (appConfig == null) {
         appConfig = new Properties();
      }
      systemConfig.loadFromXML(new FileInputStream(systemConfigFilePath));
      if (logger != null && logger.isDebugEnabled()) {
         logger.debug("System configurations loaded successfully");
      }
      appConfigFilePath = systemConfig.getProperty("nz.dataview.websyncclient.appconfig_file");
      appConfig.loadFromXML(new FileInputStream(appConfigFilePath));
      if (logger != null && logger.isDebugEnabled()) {
         logger.debug("Application configurations loaded successfully");
      }
      logConfigFilePath = getLoggerConfigFile();

      try {
         verifyConfig();
      } catch (Exception e) {
         throw new IOException(e.getMessage());
      }

      if (logger != null && logger.isTraceEnabled()) {
         logger.trace("Exiting Client.initialiseConfig()");
      }
   }

   /**
    * Checks to make sure all configuration settings have been set and are valid.
    * 
    * @throws java.lang.Exception if something is wrong
    */
   private void verifyConfig() throws Exception {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.verifyConfig()");
      }
      String message = "";

      // system configuration file location
      String sysConfFile = System.getProperty("nz.dataview.websyncclient.sysconf_file","system.properties");
      File file = null;
      if (sysConfFile != null) {
         file = new File(sysConfFile);
      }
      if (!(file != null && file.isFile() && file.canRead() && file.canWrite())) {
         message += "Invalid sysconf_file value: " + sysConfFile + "\n";      // logger configuration file location
      }
      String loggerConfFile = systemConfig.getProperty("nz.dataview.websyncclient.logconfig_file");
      file = null;
      if (loggerConfFile != null) {
         file = new File(loggerConfFile);
      }
      if (!(file != null && file.isFile() && file.canRead() && file.canWrite())) {
         message += "Invalid logconfig_file value: " + loggerConfFile + "\n";      // application configuration file location
      }
      String appConfFile = systemConfig.getProperty("nz.dataview.websyncclient.appconfig_file");
      file = null;
      if (appConfFile != null) {
         file = new File(appConfFile);
      }
      if (!(file != null && file.isFile() && file.canRead() && file.canWrite())) {
         message += "Invalid appconfig_file value: " + appConfFile + "\n";      // log file path
      }
      Properties temp = new Properties();
      try {
         temp.load(new FileInputStream(logConfigFilePath));
         String logFile = temp.getProperty("log4j.appender.A1.File");
         file = null;
         if (logFile != null) {
            file = new File(logFile);
         }
         if (!(file != null && file.isFile() && file.canRead() && file.canWrite())) {
            message += "Invalid log4j.appender.A1.File value: " + logFile + "\n";
         }
      } catch (IOException e) {
         message += "Could not load: " + logConfigFilePath + ", " + e + "\n";
      }

      // authentication key
      String authKey = appConfig.getProperty("nz.dataview.websyncclient.authentication_key");
      if (authKey == null || authKey.length() < 1 || authKey.length() > 255) {
         message += "Invalid authentication_key value: " + authKey + "\n";      // KN url
      }
      String knUrl = appConfig.getProperty("nz.dataview.websyncclient.kn_web_service_url");
      Pattern knUrlPattern = Pattern.compile("^https?://[a-zA-Z][a-zA-Z0-9:._-]{1,256}(/|/[a-zA-Z0-9;&?_=.,/-]{1,512})?$");
      if (knUrl == null || !knUrlPattern.matcher(knUrl).find()) {
         message += "Invalid kn_web_service_url value: " + knUrl + "\n";      // school name
      }
      String schoolName = appConfig.getProperty("nz.dataview.websyncclient.school_name");
      Pattern schoolNamePattern = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");
      if (schoolName == null || !schoolNamePattern.matcher(schoolName).find()) {
         message += "Invalid school_name value: " + schoolName + "\n";      // school number
      }
      String schoolNumber = appConfig.getProperty("nz.dataview.websyncclient.school_moe_no");
      Pattern schoolNoPattern = Pattern.compile("^\\d{1,6}$");
      if (schoolNumber == null || !schoolNoPattern.matcher(schoolNumber).find()) {
         message += "Invalid school_moe_no value: " + schoolNumber + "\n";      // upload dir
      }
      String upDir = appConfig.getProperty("nz.dataview.websyncclient.upload_dir");
      file = null;
      if (upDir != null) {
         file = new File(upDir);
      }
      if (file == null) {
         message += "Invalid upload folder name: " + upDir + "\n";
      } else {
         if (!file.canRead()) {
            message += "No read permission to upload folder: " + upDir + "\n";
         }
         if (!file.canWrite()) {
            message += "No write permission to upload folder: " + upDir + "\n";
         }
         if (!file.isDirectory()) {
            message += "Upload folder is not a folder: " + upDir + "\n";
         }
      }
      String downDir = appConfig.getProperty("nz.dataview.websyncclient.download_dir");
      if (getSchemaVersion().equals("1")) {
         file = null;
         if (downDir != null) {
            file = new File(downDir);
         }
         if (file == null) {
            message += "Invalid download folder name: " + downDir + "\n";
         } else {
            if (!file.canRead()) {
               message += "No read permission to download folder: " + downDir + "\n";
            }
            if (!file.canWrite()) {
               message += "No write permission to download folder: " + downDir + "\n";
            }
            if (!file.isDirectory()) {
               message += "Download folder is not a folder: " + downDir + "\n";
            }
         }
      }
      boolean proxy = getUsingProxy();
      if (proxy) {
         // only validate if using proxy
         String proxyHost = getProxyHost();
         Pattern proxyHostPattern = Pattern.compile("^[a-zA-Z0-9.-_]{1,256}$");
         if (proxyHost == null || !proxyHostPattern.matcher(proxyHost).find()) {
            message += "Invalid proxy_host value: " + proxyHost + "\n";
         }
         String proxyPort = "" + getProxyPort();
         Pattern proxyPortPattern = Pattern.compile("^\\d{1,5}$");
         if (!proxyPortPattern.matcher(proxyPort).find()) {
            message += "Invalid proxy_port value: " + proxyPort + "\n";
         }
         String proxyUser = getProxyUser();
         if (proxyUser != null && proxyUser.length() > 0) {
            // only validate if provided
            if (proxyUser.length() > 20) {
               message += "Invalid proxy_user value: " + proxyUser + "\n";
            }
         }

         String proxyPw = getProxyPw();
         if (proxyPw != null && proxyPw.length() > 0) {
            // only validate if provided
            if (proxyPw.length() > 20) {
               message += "Invalid proxy_pw value: " + proxyPw + "\n";
            }
         }
      }

      if (message.length() > 0) {
         logger.warn("Verification of configuration values failed: " + message);
         throw new Exception(message);
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.verifyConfig()");
      }
   }

   /**
    * Initialises the network proxy settings based on the configurations.  Should be called
    * before any network calls are made.
    * Note: the http.proxy* settings <strong>do not change on the fly</strong> - you can set it
    * once it seems; however, any subsequent changes seem to be ignored.
    * Therefore, changes to the proxy configurations <strong>will require a restart
    * of the JVM</strong> in order for them to take effect.
    */
   private void initialiseProxy() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.initialiseProxy()");      // set config to use proxy
      }
      boolean useProxy = getUsingProxy();
      Properties props = new Properties(System.getProperties());

      if (useProxy) {
         logger.info("Use proxy - configuring...");

         String proxyHost = getProxyHost();
         int proxyPort = getProxyPort();
         String proxyUser = getProxyUser();
         String proxyPw = getProxyPw();

         // don't bother if proxy server is invalid
         Pattern url = Pattern.compile("([^/]*)$");
         if (url.matcher(proxyHost).find()) {
            logger.info("Attemping to use proxy settings (host: " + proxyHost + ", port: " + proxyPort + ", user: " + proxyUser + ", pw (length): " + proxyPw.length());

            props.put("http.proxySet", "" + useProxy);
            props.put("http.proxyHost", proxyHost);
            props.put("http.proxyPort", "" + proxyPort);
            props.put("http.proxyUser", proxyUser);
            props.put("http.proxyPassword", proxyPw);
            props.put("networkaddress.cache.ttl", getDnsCacheTtl());
            props.put("networkaddress.cache.negative.ttl", getNegativeDnsCacheTtl());

            logger.info("Proxy settings configured successfully");

         } else {
            logger.info("Proxy host: " + proxyHost + " is invalid, ignoring proxy settings");
         }
      } else {
         props.put("http.proxySet", "" + useProxy);
         props.put("http.proxyHost", "x");
         props.put("http.proxyPort", "");
         props.put("http.proxyUser", "");
         props.put("http.proxyPassword", "");
         props.put("networkaddress.cache.ttl", getDnsCacheTtl());
         props.put("networkaddress.cache.negative.ttl", getNegativeDnsCacheTtl());

         logger.info("Not using proxy server, reset all proxy settings OK");
      }

      Properties newProps = new Properties(props);
      System.setProperties(newProps);
      if (logger.isDebugEnabled()) {
         logger.debug("Applied new proxy settings to system properties");
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.initialiseProxy()");
      }
   }

   /**
    * Saves the current application configurations to the config file.
    * Ensure that all configs are up to date - this will overwrite the 
    * existing configuration files!
    * 
    * @throws java.io.IOException   thrown if IO error occurred while opening/closing the configuration file
    */
   private synchronized void saveConfig() throws IOException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.saveConfig()");
      }
      if (logger.isTraceEnabled()) {
         logger.trace("My state: " + this);
      }
      if (systemConfig != null) {
         systemConfig.storeToXML(new FileOutputStream(systemConfigFilePath), null);
      }
      if (appConfig != null) {
         appConfig.storeToXML(new FileOutputStream(appConfigFilePath), null);
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.saveConfig()");
      }
   }

   /**
    * Retrieves the upload_dir from the configurations.
    * 
    * @return  the upload dir
    */
   public String getUploadDir() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getUploadDir()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.upload_dir");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getUploadDir() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the download_dir from the configurations.
    * 
    * @return  the download dir
    */
   public String getDownloadDir() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getDownloadDir()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.download_dir");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getDownloadDir() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the upload_byte_limit from the configurations.
    * 
    * @return  the upload byte limit
    */
   public int getUploadByteLimit() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getUploadByteLimit()");
      }
      int ret = Integer.parseInt(systemConfig.getProperty("nz.dataview.websyncclient.upload_byte_limit", "2000"));

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getUploadByteLimit() with int: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the KN service URL from the configurations.
    * 
    * @return  the KN service URL
    */
   public String getKnUrl() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getKnUrl()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.kn_web_service_url");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getKnUrl() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the downloads_per_session value from the configurations.
    * 
    * @return  the max downloads per session
    */
   public int getDownloadsPerSession() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getDownloadsPerSession()");
      }
      int ret = Integer.parseInt(systemConfig.getProperty("nz.dataview.websyncclient.downloads_per_session", "5"));

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getDownloadsPerSession() with int: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the uploads_per_session value from the configurations.
    * 
    * @return  the max uploads per session
    */
   public int getUploadsPerSession() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getUploadsPerSession()");
      }
      int ret = Integer.parseInt(systemConfig.getProperty("nz.dataview.websyncclient.uploads_per_session", "5"));

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getUploadsPerSesion() with int: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the school_moe_no from the configurations.
    * 
    * @return  the school moe number
    */
   public String getSchoolNumber() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getSchoolNumber()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.school_moe_no");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getSchoolNumber() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the school_name value from the configurations.
    * 
    * @return  the school name
    */
   public String getSchoolName() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getSchoolName()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.school_name");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getSchoolName() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the schedule_upload value from the configurations.
    * 
    * @return  the scheduled upload index
    */
   public int getScheduleUpload() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getScheduleUpload()");
      }
      int ret = Integer.parseInt(appConfig.getProperty("nz.dataview.websyncclient.schedule_upload", "1"));

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getScheduleUpload() with int: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the length of time this program will go to sleep during runs.
    * 
    * @return  the length of time in milliseconds (Long.MAX_VALUE for indefinite sleep)
    */
   public long getScheduledUploadInterval() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getScheduledUploadInterval()");
      }
      long ret = 0;
      int option;
      try {
         option = getScheduleUpload();
      } catch (NumberFormatException e) {
         option = 1;
         if (logger.isDebugEnabled()) {
            logger.debug("Invalid schedule_upload value from config, default to 1");
         }
      }

      String debugMessage;
      switch (option) {
         case 0:
            // 10 min
            debugMessage = "0: 10 min";
            ret = 10 * 60 * 1000;
            break;
         case 1:
            // 30 min
            debugMessage = "1: 30 min";
            ret = 30 * 60 * 1000;
            break;
         case 2:
            // 1 hour
            debugMessage = "2: 1 hour";
            ret = 60 * 60 * 1000;
            break;
         case 3:
            // 2 hours
            debugMessage = "3: 2 hours";
            ret = 2 * 60 * 60 * 1000;
            break;
         case 4:
            // 4 hours
            debugMessage = "4: 4 hours";
            ret = 4 * 60 * 60 * 1000;
            break;
         case 5:
            // 1 day
            debugMessage = "5: 1 day";
            ret = 24 * 60 * 60 * 1000;
            break;
         case 6:
            // never (manual only)
            debugMessage = "6: never (manual only)";
            ret = Long.MAX_VALUE;
            break;
         default:
            // 30 min
            debugMessage = "default: 30 min";
            ret = 30 * 60 * 1000;
      }

      if (logger.isDebugEnabled()) {
         logger.debug(debugMessage);
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getScheduleUploadInterval() with long: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the String version of the schedule upload
    * value.
    * 
    * @return  the string representation of the upload schedule value
    */
   public String getScheduleUploadString() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getScheduleUploadString()");
      }
      int val = getScheduleUpload();
      String ret = "";
      switch (val) {
         case 0:
            ret = "SCHEDULE_UPLOAD_10_MIN";
            break;
         case 1:
            ret = "SCHEDULE_UPLOAD_30_MIN";
            break;
         case 2:
            ret = "SCHEDULE_UPLOAD_1_HR";
            break;
         case 3:
            ret = "SCHEDULE_UPLOAD_2_HR";
            break;
         case 4:
            ret = "SCHEDULE_UPLOAD_4_HR";
            break;
         case 5:
            ret = "SCHEDULE_UPLOAD_1_DAY";
            break;
         case 6:
            ret = "SCHEDULE_UPLOAD_NEVER";
            break;
         default:
            ret = "SCHEDULE_UPLOAD_30_MIN";
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getScheduleUploadString() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the time periods during which this program is allowed to run.
    * Return value meanings discussed below:
    * <ul>
    *	 <li><code>0</code> - run all the time (no restrictions)</li>
    *	 <li><code>1</code> - run only during school hours which are defined as between 8am and 4pm</li>
    *	 <li><code>2</code> - run only during non-school hours which are defined as between 4pm and 8am</li>
    * </ul>
    * 
    * @return  0 for unrestricted running, 1 for only during school hours (8am - 4pm), 2 for only during non-school hours (4pm - 8am)
    */
   public int getProcessTime() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getProcessTime()");
      }
      int option = 0;

      try {
         option = Integer.parseInt(appConfig.getProperty("nz.dataview.websyncclient.process_time", "0"));
      } catch (NumberFormatException e) {
         option = 0;
         if (logger.isDebugEnabled()) {
            logger.debug("Invalid process_time value from config, default to 0");
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getProcessTime() with int: " + option);
      }
      return option;
   }

   /**
    * Retrieves the String versino of the process time value.
    * 
    * @return  the string representation of the process time value in the config
    */
   public String getProcessTimeString() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getProcessTimeString()");
      }
      String ret = "";

      int option = getProcessTime();
      switch (option) {
         case 0:
            ret = "PROCESS_TIME_ALWAYS";
            break;
         case 1:
            ret = "PROCESS_TIME_SCHOOL_HOURS";
            break;
         case 2:
            ret = "PROCESS_TIME_NON_SCHOOL_HOURS";
            break;
         default:
            ret = "PROCESS_TIME_ALWAYS";
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getProcessTimeString() with String: " + ret);
      }
      return ret;
   }

   /**
    * Returns whether the program should use a proxy or not.
    * 
    * @return  true if use proxy, false otherwise
    */
   public boolean getUsingProxy() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getUsingProxy()");
      }
      boolean ret = appConfig.getProperty("nz.dataview.websyncclient.use_proxy", "n").equalsIgnoreCase("y");

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getUsingProxy() with boolean: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the host of the proxy.
    * 
    * @return  the host of the proxy
    */
   public String getProxyHost() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getProxyHost()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.proxy_host");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getProxyHost() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the port number of the proxy.
    * 
    * @return  the port number
    */
   public int getProxyPort() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getProxyPort()");
      }
      int port = 0;
      try {
         port = Integer.parseInt(appConfig.getProperty("nz.dataview.websyncclient.proxy_port", "8080"));
      } catch (NumberFormatException e) {
         port = 8080;
         if (logger.isDebugEnabled()) {
            logger.debug("Invalid proxy_port value from config, default to 8080");
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getProxyPort() with int: " + port);
      }
      return port;
   }

   /**
    * Retrieves the proxy username.
    * 
    * @return  the username
    */
   public String getProxyUser() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getProxyUser()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.proxy_user");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getProxyUser() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the proxy password.
    * 
    * @return  the password
    */
   public String getProxyPw() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getProxyPw()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.proxy_pw");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getProxyPw() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the authentication_key form the config.
    * 
    * @return  the authentication key
    */
   public String getAuthenticationKey() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getAuthenticationKey()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.authentication_key");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getAuthenticationKey() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the schema version from the config. Either 1 or 2
    *
    * @return
    */
   public String getSchemaVersion() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getSchemaVersion()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.schema_version");
      if (ret == null) {
         ret = "2";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getSchemaVersion() with String: " + ret);
      }
      return ret;
   }

   /**
    * Once the batch is sent, we check periodically to see if it has worked.
    * Retrieves the number of minutes between each check
    * @return
    */
   public Integer getResponseTime() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getSchemaVersion()");
      }
      String time = appConfig.getProperty("nz.dataview.websyncclient.response_check_time");
      Integer responseCheckTime = 10;
      if (time != null) {
         try {
            responseCheckTime = Integer.parseInt(time);
         } catch (NumberFormatException e) {
            //Just set it to default;
         }
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getSchemaVersion() with String: " + responseCheckTime);
      }
      return responseCheckTime;
   }

   /**
    * If we fail to connect to retrieve the last run time, we try again every
    * minute. After the FailedUploadCount of tries, we send an email, but keep
    * trying.
    *
    * @return 
    */
   public Integer getFailedUploadCount() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getFailedUploadCount()");
      }
      String time = appConfig.getProperty("nz.dataview.websyncclient.failed_upload_count");
      Integer failedUploadCount = 0;
      if (time != null) {
         try {
            failedUploadCount = Integer.parseInt(time);
         } catch (NumberFormatException e) {
            //Just set it to default;
         }
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getFailedUploadCount() with String: " + failedUploadCount);
      }
      return failedUploadCount;
   }

   /**
    * Gets the email to send failures to
    * @return
    */
   public String getReportsEmail() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getReportEmail()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.reports_email");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getReportEmail() with String: " + ret);
      }
      return ret;
   }

   /**
    * SMTP host for sending emails
    *
    * @return
    */
   public String getSmtpHost() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getSmtpHost()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.smtp_host");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getSmtpHost() with String: " + ret);
      }
      return ret;
   }

   /**
    * SMTP user for sending emails, if required
    *
    * @return
    */
   public String getSmtpUser() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getSmtpUser()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.smtp_user");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getSmtpUser() with String: " + ret);
      }
      return ret;
   }

   /**
    * SMTP pasword, if required
    *
    * @return
    */
   public String getSmtpPassword() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getSmtpPassword()");
      }
      String ret = appConfig.getProperty("nz.dataview.websyncclient.smtp_password");
      if (ret == null) {
         ret = "";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getSmtpPassword() with String: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the successful DNS cache TTL for the JVM from the config.
    * 
    * @return  the number of seconds for the TTL
    */
   public int getDnsCacheTtl() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getDnsCacheTtl()");
      }
      int ttl = 0;

      try {
         ttl = Integer.parseInt(systemConfig.getProperty("nz.dataview.websyncclient.dnscachettl", "900"));
      } catch (NumberFormatException e) {
         ttl = 900;
         if (logger.isDebugEnabled()) {
            logger.debug("Invalid dnscachettl value from config, default to 900");
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getDnsCacheTtl() with int: " + ttl);
      }
      return ttl;
   }

   /**
    * Retrieves the negative (unsuccessful) DNS cache TTL for the JVM from the config.
    * 
    * @return  the number of seconds for the TLL
    */
   public int getNegativeDnsCacheTtl() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getNegativeDnsCacheTtl()");
      }
      int ttl = 0;

      try {
         ttl = Integer.parseInt(systemConfig.getProperty("nz.dataview.websyncclient.negativednscachettl", "10"));
      } catch (NumberFormatException e) {
         ttl = 10;
         if (logger.isDebugEnabled()) {
            logger.debug("Invalid negativednscachettl value from config, default to 10");
         }
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getNegativeDnsCacheTtl() with int: " + ttl);
      }
      return ttl;
   }

   /**
    * Retrieves the logger config file path.
    * 
    * @return  the path to the log config file
    */
   public String getLoggerConfigFile() {
      return systemConfig.getProperty("nz.dataview.websyncclient.logconfig_file");
   }

   /**
    * Retrieves the String array containing the filenames which have been marked
    * to not be uploaded.  Returns <code>null</code> if there are no marked files.
    * 
    * @return  the list of filenames to not upload (null if there are none)
    */
   public String[] getDontUploadFiles() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getDontUploadFiles()");
      }
      String[] ret = null;
      String list = systemConfig.getProperty("nz.dataview.websyncclient.dont_upload_files");
      if (list != null && list.length() > 0) {
         if (logger.isDebugEnabled()) {
            logger.debug("dont_upload_files value exists: " + list);
         }
         ret = list.split(",");
      }

      if (logger.isTraceEnabled()) {
         String msg = "Exiting Client.getDontUploadFiles() with String[]: ";
         if (ret == null) {
            msg += "null";
         } else {
            msg += ret + "[" + ret.length + "]";
         }
         logger.trace(msg);
      }

      return ret;
   }

   /**
    * Adds a new filename to the "dont_upload_files" configuration.
    * 
    * @param   filename the new filename to add
    */
   public void addToDontUploadFiles(String filename) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.addToDontUploadFiles() with String: " + filename);
      }
      String[] current = getDontUploadFiles();
      if (current == null) {
         current = new String[0];
      }
      if (logger.isDebugEnabled()) {
         logger.debug("Loaded current list of files marked to not upload (of which there are: " + current.length + " )");
      }
      String[] newArray = new String[current.length + 1];
      System.arraycopy(current, 0, newArray, 0, current.length);
      newArray[newArray.length - 1] = filename;

      if (logger.isDebugEnabled()) {
         logger.debug("Appended file: " + filename + " to the current list of files marked to not upload");
      }
      systemConfig.setProperty("dont_upload_files", parseDontUploadFiles(newArray));
      if (logger.isDebugEnabled()) {
         logger.debug("Saved list of files marked to not upload into memory");
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.addToDontUploadFiles()");
      }
   }

   /**
    * Takes an array of <code>Strings</code>, and returns the equivalent comma separated <code>String</code>.
    * 
    * @param   list  the String array to parse
    * @return	     the comma separated String
    */
   private String parseDontUploadFiles(String[] list) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.parseDontUploadFiles() with String[]: " + list + "[" + list.length + "]");
      }
      String ret = "";

      if (list != null && list.length > 0) {
         if (logger.isDebugEnabled()) {
            logger.debug("Parse list of files");
         }
         for (int i = 0; i < list.length; i++) {
            ret += list[i] + ",";
         }
         // strip the last comma
         ret = ret.substring(0, ret.length() - 1);
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.parseDontUploadFies() with String: " + ret);
      }
      return ret;
   }

   /**
    * The main method.  The jsl service wrapper is expected to invoke this
    * method.
    * 
    * @param   args  not used
    */
   public static void main(String[] args) {
      Client app;
      try {
         app = new Client();
         app.run();
      } catch (Exception e) {
         System.err.println("Fatal error: " + e);
         e.printStackTrace();
         System.exit(1);
      }
   }
}
