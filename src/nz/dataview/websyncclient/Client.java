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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;
import java.security.Security;
import java.sql.*;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggingEvent;

class Database {

   Connection conn = null;
   Statement stmt = null;
   ResultSet rs = null;

   public void Connect(String database, String host, String user, String pass) throws SQLException {
      /* Get a connection to the database */
      conn = DriverManager.getConnection("jdbc:mysql://" + host.trim() + ":3306/" + database + "?" + "user=" + user + "&password=" + pass);
      stmt = conn.createStatement();
   }

   public void Disconnect() {
      if (stmt != null) {
         try {
            stmt.close();
         } catch (SQLException sqlEx) {
            //my_logger.log("SQLException: " + sqlEx.getMessage());
         }
         stmt = null;
      }
      if (conn != null) {
         try {
            conn.close();
         } catch (SQLException sqlEx) {
            // Ignore
         }
         conn = null;
      }
   }

   public ResultSet getTableDataRaw(String sqlstr) throws SQLException {
      /* Execute the query */
      rs = stmt.executeQuery(sqlstr);

      return rs;
   }

   public PreparedStatement prepareUpdate(String sqlstr) throws SQLException {
      PreparedStatement pstmt = conn.prepareStatement(sqlstr);
      return pstmt;
   }

   public void updateTableRaw(String sqlstr) throws SQLException {
      /* Execute the query */
      stmt.execute(sqlstr);
   }
}

/**
 * The main application class.  Initiates all web service calls, log records,
 * etc.
 * 
 * @author  William Song, Tim Owens
 * @version 1.1.3
 */
public class Client {

   /**
    * The WebSYNC version.
    */
   public static final String WEBSYNC_VERSION = "2.1.etap";
   /**
    * The logger!
    */
   private Logger logger;
   /**
    * The system configurations.  <strong>MUST</strong> be initialised before
    * anything else.
    */
   private Properties systemConfig;
   /**
    * Application configurations contain the settings which can be changed via
    * the GUI.
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
    * The path at which the message files lie.
    */
   public String controlDirectory;
   /**
    * Overall report of the application execution, which should be uploaded to
    * the server for records.
    */
   private String report;
   /**
    * Represents the current status of the WebSYNC transfer as far as the 
    * <strong>SERVER</strong> is concerned.
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
    * Whether to force run or not.
    */
   private boolean forced;
	private boolean sleptEnough;
	private long scheduledSleepTime;
	private KeepAlive keepalive;
	public Object knStatus = null;
	public String knStatusString = null;
	public String websyncStatusString = null;
   
   public static String control_folder="/data/u00/www/etap/control/";
   public static String data_folder="/data/u00/www/etap/data/";
//   public static String control_folder="C:/control/";
//   public static String data_folder="C:/data/";
   public String school;
   public String school_moe_number;

   /**
    * Constructor.  Initialises variables etc.
    */
   public Client(String sch, String moe_number) {
      school=sch;
      school_moe_number=moe_number;
      logger = Logger.getLogger(school);
      try {
         
         // note this value comes from the command line
         systemConfigFilePath = control_folder+"system.properties";
         initialiseConfig();
         initialiseLogger();
         initialiseProxy();
      } catch (IOException e) {
         String message =
                 "Fatal error: failed to initialise system configurations";
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

      if (logger.isDebugEnabled()) {
         // re-initialise configurations
         logger.debug("Iterating through infinite while loop");
      }

      if (!initialize()) {
         return;
      }

      // clear the report
      submitReport("", false);
      resetOverallStatus();
      if (logger.isTraceEnabled()) {
         // how long have we been sleep?
         logger.trace("My state: " + this);
      }

      java.util.Date lastRun = getLastRunDate();
      java.util.Date now = new java.util.Date();

      long actualSleepTime = now.getTime() - lastRun.getTime();

      boolean timeConditionsOk = timeConditionOk(now.getTime());

      // We need to check if new should upload immediately, if we need to
      // test connectivity, restart, etc..
      try {
         checkMessagesAndUpdateStatus();
      } catch (IOException ioEx) {
         logger.error("0003: Problem processing control files.");
         if (logger.isDebugEnabled()) {
            logger.debug("0003: Problem processing control files:\n"+ioEx.getMessage()+"\n"+ioEx.getStackTrace());
         }
      }

      // only run if we are forced, or (we have slept enough and time
      // conditions are met)
      boolean doRun = forced || timeConditionsOk;

      uploader = new Uploader(this);
      uploader.getStatus();
      if (uploader.status.equals("waiting for response")) {
         // When waiting for a response, we have a different sleep
         // interval, and ignore the time conditions
         scheduledSleepTime = getResponseTime() * 60000;
         sleptEnough = actualSleepTime >= scheduledSleepTime;
         timeConditionsOk = true;
         doRun = forced || (sleptEnough && timeConditionsOk);
      }
      if (logger.isDebugEnabled()) {
         // also make sure time conditions are met
         logger.debug("lastRun: " + lastRun.getTime() + ", currentTime: " +
                 now.getTime() + ", actualSleepTime: " + actualSleepTime +
                 ", scheduledSleepTime: " + scheduledSleepTime);
         logger.debug("forced: " + forced + ", sleptEnough: " + sleptEnough +
                 ", timeConditionsOk: " + timeConditionsOk);
      }

      uploader.reloadConfig();

      if (logger.isTraceEnabled()) {
         logger.trace("Created uploader thread: " + uploader);
      }

      long start = System.currentTimeMillis();

      if (doRun) {
         try {
            doRun(start);
         } catch (IOException ex) {
            logger.error("0004: Problem creating control files.");
            if (logger.isDebugEnabled()) {
               logger.debug("0004: Problem creating control files:\n"+ex.getMessage()+"\n"+ex.getStackTrace());
            }
         }
      } else {
         logger.debug("Not running this time round");
      }

      // clear the forced flag
      forced = false;
   }

   /**
    * Extracted per 3.19 of SOW 3.3
    */
   private boolean doProcessNullDates(java.util.Date[] dates, int runFailCount) {
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
            try {
               String subject = "WebSYNC cannot connect";
               String content = "WebSYNC has tried " + runFailCount + " time(s) to connect to the LMS, but has not been " +
                       "successful.\n\nIt will continue to attempt to connect.";
               MimeMessage msg = EmailUtils.createEmail(reportsEmail, "support@dataview.co.nz", subject, content);
               EmailUtils.sendEmail(msg, getSmtpHost(), getSmtpUser(), getSmtpPassword());
            } catch (MessagingException ex) {
               //Ah well. Did our best.
               //Not sure if the above comment was best, lets log it so we know what happened
					logger.error("0400: Unable to email. Are your SMTP settings correct?");
					if (logger.isDebugEnabled()) {
						logger.debug("0400: Unable to email:\n"+ex.getMessage());
					}
            }
         }
         try {
            //Doze off for a minute...
            Thread.sleep(1000 * 60);
         } catch (InterruptedException e) {
            logger.info("Woken up by GUI");
         }
         return true;
      }
      return false;
   }

   private void doRun(long start) throws IOException {
      // create is_running file & last_run file.
      sendMessage("is_running");
      sendMessage("last_run");
      uploader.start();
      if (logger.isDebugEnabled()) {
         logger.debug("uploder thread started");
      }
      try {
         // wait for the upload and download threads to finish before sleeping
         finishThreads();
         if (forced) {
            logger.info("User initiated transfer completed");
         }
         long end = System.currentTimeMillis();
         double timeTaken = (end - start) * 0.001;
         if (report.length() > 2) {
            String message = "*****Time taken for this run: " + timeTaken + " second(s)*****";
            logger.info(message);
            appendReport(message);
            // once the threads have finished, submit the report of this
            // run to the server
            WebSYNCService caller = WebSYNCServiceFactory.getSoapService(
                    getKnUrl(),
                    "http://www.dataview.co.nz/",
                    getAuthenticationKey(),
                    getSchoolName(),
                    getSchoolNumber(),
                    getScheduleUploadString(),
                    getProcessTimeString());
            if (!caller.doRecordLog(report, overallRunStatus, "MoE Upload")) {
               logger.error("0401: Failed to submit report of this run to server.");
            } else if (logger.isDebugEnabled()) {
               logger.debug("Report submitted OK");
            }
         }
      } catch (RuntimeException e) {
         // Message gracefully before dying.
         String message = "0000: Fatal error: unexpected error encountered, " + "exit program (1) " + e.getStackTrace() + ", my state: " + this;
         logger.fatal(message);
         System.exit(1);
      }
      clearMessage("is_running");
   }

   private java.util.Date getLastRunDate() {
      // Check last_run to determine last run time
      File lastRun = new File(controlDirectory + File.separator + "last_run");
      if (lastRun.exists()) {
         return new java.util.Date(lastRun.lastModified());
      } else {
			//Somebody deleted our alarm clock. Assume it's time to get up.
			java.util.Date now=new java.util.Date();
         return new java.util.Date(now.getTime()-1000*60*60);
      }
   }

   private boolean initialize() {
      try {
         initialiseConfig();
         initialiseLogger();
         logger.debug("Initialised config and logger OK");
         if (logger.isTraceEnabled()) {
            // note: this cannot be done on the fly - the http.proxySet
            // property is only read once at startup.  Therefore, the
            // program must be restarted.
            logger.trace("My state: " + this);
         }
      } catch (IOException e) {
         String message = "0005: Error: failed to re-initialise system configurations.";
         if (logger != null) {
            logger.error(message);
				if (logger.isDebugEnabled()) {
					logger.debug(message+"\n"+e.getMessage()+"\n"+e.getStackTrace());
				}
         }
         System.err.println(message);
         try {
            //Wait a bit, try again
            Thread.sleep(1000);
         } catch (InterruptedException ex) {
         }
         return false;
      }
      System.setProperty("javax.net.ssl.trustStore",
              "C:\\Program Files\\Java\\jre1.6.0_07\\bin\\client.keystore");
      System.setProperty("java.protocol.handler.pkgs",
              "com.sun.net.ssl.internal.www.protocol");
      Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
      return true;
   }

   /**
    * Gets called when the system is about to exit.  Do any necessary
    * operations here before shutting down.
    * At the moment it doesn't do a lot.
    */
   private void onShutDown() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.onShutDown()");
      }
      logger.info("Waiting for uploader and downloader threads to finish");
      finishThreads();
      if(keepalive!=null) keepalive.interrupt();//Not sure if that's essential
      logger.info("Exiting cleanly");

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.onShutDown()");
      }
   }

   /**
    * Waits for the uploader and downloader threads to finish.  After this 
    * method is called, the uploader and downloader objects will be
    * effectively null.
    */
   private void finishThreads() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.finishThreads()");
      }
      try {
         if(uploader!=null) uploader.join();
         if (logger.isDebugEnabled()) {
            logger.debug("uploader thread finished");
         }
      } catch (InterruptedException e) {
         logger.warn("Failed to wait for threads to finish, " + e);
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
    */
   private boolean isRunning() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.isRunning()");
      }
      boolean ret = false;
      // make sure I am running or waiting for the uploader/downloader threads
      if (myself != null) {
         Thread.State currentState = myself.getState();
         ret = currentState.equals(Thread.State.RUNNABLE) ||
                 currentState.equals(Thread.State.WAITING);
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

   public boolean updateMessage(String websync_stat, String kn_stat) {
      boolean ret = true;
      try {
         String status="";
         String batchNumber="";
         
         try {
            FileInputStream fis = new FileInputStream(controlDirectory + File.separator + "websync_status.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            String line = "";
            line = br.readLine();
            status = line.trim();
            line = br.readLine();
            batchNumber = line.trim();
            fis.close();
         } catch (Exception e) {}

         PrintStream ps = new PrintStream(new FileOutputStream(controlDirectory + File.separator + "websync_status.txt"));
         ps.println(status);
         ps.println(batchNumber);
         ps.println(websync_stat);
         ps.println(kn_stat);
         ps.close();
      } catch (Exception e) {
         logger.error("0303: Could not update status file.");
			if(logger.isDebugEnabled())
			{
				logger.debug("0303: Could not update status file:\n" + e.getMessage());
			}
         ret = false;
      }
      return ret;
   }
   
   public boolean updateStatus(String status) {
      boolean ret = true;
      try {
         String batchNumber="";
         String kn_stat="";
         String websync_stat="";

         try {
            FileInputStream fis = new FileInputStream(controlDirectory + File.separator + "websync_status.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            String line = "";
            line = br.readLine();
            line = br.readLine();
            batchNumber = line.trim();
            line = br.readLine();
            websync_stat = line.trim();
            line = br.readLine();
            kn_stat = line.trim();
            fis.close();
         } catch (Exception e) {}

         PrintStream ps = new PrintStream(new FileOutputStream(controlDirectory + File.separator + "websync_status.txt"));
         ps.println(status);
         ps.println(batchNumber);
         ps.println(websync_stat);
         ps.println(kn_stat);
         ps.close();
      } catch (Exception e) {
         logger.error("0303: Could not update status file.");
			if(logger.isDebugEnabled())
			{
				logger.debug("0303: Could not update status file:\n" + e.getMessage());
			}
         ret = false;
      }
      return ret;
   }
   
   public boolean updateBatchNumber(String batchNumber) {
      boolean ret = true;
      try {
         String status="";
         String kn_stat="";
         String websync_stat="";

         try {
            FileInputStream fis = new FileInputStream(controlDirectory + File.separator + "websync_status.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            String line = "";
            line = br.readLine();
            status = line.trim();
            line = br.readLine();
            line = br.readLine();
            websync_stat = line.trim();
            line = br.readLine();
            kn_stat = line.trim();
            fis.close();
         } catch (Exception e) {}

         PrintStream ps = new PrintStream(new FileOutputStream(controlDirectory + File.separator + "websync_status.txt"));
         ps.println(status);
         ps.println(batchNumber);
         ps.println(websync_stat);
         ps.println(kn_stat);
         ps.close();
      } catch (Exception e) {
         logger.error("0303: Could not update status file.");
			if(logger.isDebugEnabled())
			{
				logger.debug("0303: Could not update status file:\n" + e.getMessage());
			}
         ret = false;
      }
      return ret;
   }

    /**
    * Retrieves the latest log entries in one big String.
    * 
    * @return  a big String containing all the log messages
    */
   private String getLatestLog() {
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
                  String date = new java.util.Date(ev.getTimeStamp()).toString();
                  String level = ev.getLevel().toString();
                  String message = (String) ev.getMessage();
                  buffer.append(date).append(" - ").append("(").append(level).
                          append("): ").append(message).append("\n");
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

   public void sendMessage(String messageFileName, String messageText) throws IOException {
      File message = new File(controlDirectory + File.separator + messageFileName);

      if (message.exists()) {
         message.setLastModified(System.currentTimeMillis());
      } else {
         message.createNewFile();
      }
      if (messageText != null) {
         FileWriter fw = new FileWriter(message);
         try {
            fw.write(messageText);
         } finally {
            fw.close();
         }
      }
   }

   public void sendMessage(String messageFileName) throws IOException {
      sendMessage(messageFileName, null);
   }

   public boolean clearMessage(String messageFileName) throws IOException {
      File message = new File(controlDirectory + File.separator + messageFileName);
      if (message.exists()) {
         message.delete();
         return true;
      }
      return false;
   }

   /**
    * Performs a test to see if the connection to the web server is up and running.
    * 
    * @return  true if connection is ok, false otherwise
    * @throws  java.net.UnknownHostException if DNS lookup fails
    */
   private boolean testConnection() throws UnknownHostException,
           IOException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.testConnection()");
      }

      // re-initialise config
      initialiseConfig();

      boolean okSoFar = false;

      // then call a web service
      WebSYNCService caller = WebSYNCServiceFactory.getSoapService(
              getKnUrl(),
              "http://www.dataview.co.nz/",
              getAuthenticationKey(),
              getSchoolName(),
              getSchoolNumber(),
              getScheduleUploadString(),
              getProcessTimeString());
      okSoFar = caller.doTestUpload(getUploadDir(), getDownloadDir());

      if (okSoFar) {
         logger.info("Test transaction to " + getKnUrl() +
                 " was completed successfully");
      } else
		{
         logger.error("Test transaction to " + getKnUrl() +
                 " failed");
		}

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.testConnection() with boolean: " +
                 okSoFar);
      }
      return okSoFar;
   }

   /**
    * Determines whether the given timestamp is considered OK to run the 
    * program.  The program should only run depending on the configuration
    * 'process_time' setting.
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
    * @param   msg
    *    the message to set/append
    * @param   append
    *    set to true to append the message, false to overwrite the current report
    */
   public synchronized void submitReport(String msg, boolean append) {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.submitReport() with String: " + msg +
                 ", boolean: " + append);
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
    * Sets the overall status.  Do not call this directly, but rather, call 
    * <code>resetOverallStatus()</code> which is all you should really need.
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
      // ideally this should be done before anything else to prevent it from
      // producing the "Please initialize the log4j system properly." error
      LogManager.resetConfiguration();
      PropertyConfigurator.configure(logConfigFilePath);

      /*
       * Lets establish some rules for logging:
       * -method entries/exits should be logged as trace(), with param/ret
       *    values
       * -all TRACE and DEBUG level logs should be wrapped around if
       *    (traceEnabled() etc
       * -TRACE should be enough to follow the exact execution process
       *    (finest level of granularity)
       * -DEBUG should be for debugging purposes - should represent logic
       *    decisions etc
       * -INFO should be for informative log events, perhaps to indicate progress
       * -WARN should be for problems which are recoverable - not ideal
       *    situation, but program safe to continue
       * -ERROR should be for genuine problems - program can continue, but
       *    unsafe to do so
       * -FATAL should be for unrecoverable events which should result in
       *    system exiting
       */
      logger.debug("Logger initialised OK");
   }

   /**
    * Initialises the application configurations into the <code>Properties</code>
    * object.
    * <strong>This should be the first method call in the constructor.</strong>
    * All methods assume that this is called first before anything else.
    * 
    * @throws  java.io.IOException
    *       thrown if IO error occurred while opening the configuration file
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
      appConfigFilePath = control_folder+"websyncclient.properties";
      appConfig.loadFromXML(new FileInputStream(appConfigFilePath));
      if (logger != null && logger.isDebugEnabled()) {
         logger.debug("Application configurations loaded successfully");
      }
      logConfigFilePath = getLoggerConfigFile();

      controlDirectory = getControlDirectory();

      verifyConfig();

      if (logger != null && logger.isTraceEnabled()) {
         logger.trace("Exiting Client.initialiseConfig()");
      }
   }

   /**
    * Checks to make sure all configuration settings have been set and are valid.
    * 
    * @throws IOException if something is wrong
    */
   private void verifyConfig() throws IOException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.verifyConfig()");
      }
      String message = "";

      // system configuration file location
      File file = null;
      file = new File(systemConfigFilePath);

      if (!(file != null && file.isFile() && file.canRead() && file.canWrite())) {
         // logger configuration file location
         message += "Invalid sysconf_file value: " + systemConfigFilePath + "\n";
      }

      file = null;
      file = new File(logConfigFilePath);

      if (!(file != null && file.isFile() && file.canRead() && file.canWrite())) {
         // application configuration file location
         message += "Invalid logconfig_file value: " + logConfigFilePath + "\n";
      }

      file = null;
      file = new File(appConfigFilePath);
      if (!(file != null && file.isFile() && file.canRead() && file.canWrite())) {
         // log file path
         message += "Invalid appconfig_file value: " + appConfigFilePath + "\n";
      }
      Properties temp = new Properties();
      try {
         temp.load(new FileInputStream(logConfigFilePath));
         String logFile = temp.getProperty("log4j.appender.MAIN.File");
         file = null;
         if (logFile != null) {
            file = new File(logFile);
         }
         if (!(file != null && file.isFile() && file.canRead() && file.canWrite())) {
            message += "Invalid log4j.appender.MAIN.File value: " + logFile + "\n";
         }
      } catch (IOException e) {
         message += "Could not load: " + logConfigFilePath + ", " + e + "\n";
      }

      // authentication key
      String authKey = appConfig.getProperty("nz.dataview.websyncclient.authentication_key");
      if (authKey == null || authKey.length() < 1 || authKey.length() > 255) {
         message += "Invalid authentication_key value: " + authKey + "\n";
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
         throw new IOException(message);
      }

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.verifyConfig()");
      }
   }

   /**
    * Initialises the network proxy settings based on the configurations.  
    * Should be called before any network calls are made.
    * Note: the http.proxy* settings <strong>do not change on the fly</strong> - 
    * you can set it once it seems; however, any subsequent changes seem to be
    * ignored.  Therefore, changes to the proxy configurations <strong>will
    * require a restart of the JVM</strong> in order for them to take effect.
    */
   private void initialiseProxy() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.initialiseProxy()");
      }
      // set config to use proxy
      boolean useProxy = getUsingProxy();
      Properties props = new Properties(System.getProperties());

      if (useProxy) {
         logger.debug("Use proxy - configuring...");

         String proxyHost = getProxyHost();
         int proxyPort = getProxyPort();
         String proxyUser = getProxyUser();
         String proxyPw = getProxyPw();

         // don't bother if proxy server is invalid
         Pattern url = Pattern.compile("([^/]*)$");
         if (url.matcher(proxyHost).find()) {
            logger.debug("Attemping to use proxy settings (host: " + proxyHost +
                    ", port: " + proxyPort + ", user: " + proxyUser +
                    ", pw (length): " + proxyPw.length());

            props.put("http.proxySet", "" + useProxy);
            props.put("http.proxyHost", proxyHost);
            props.put("http.proxyPort", "" + proxyPort);
            props.put("http.proxyUser", proxyUser);
            props.put("http.proxyPassword", proxyPw);
            props.put("networkaddress.cache.ttl", getDnsCacheTtl());
            props.put("networkaddress.cache.negative.ttl", getNegativeDnsCacheTtl());

            logger.debug("Proxy settings configured successfully");

         } else {
            logger.warn("Proxy host: " + proxyHost + " is invalid, ignoring " +
                    "proxy settings");
         }
      } else {
         props.put("http.proxySet", "" + useProxy);
         props.put("http.proxyHost", "x");
         props.put("http.proxyPort", "");
         props.put("http.proxyUser", "");
         props.put("http.proxyPassword", "");
         props.put("networkaddress.cache.ttl", getDnsCacheTtl());
         props.put("networkaddress.cache.negative.ttl", getNegativeDnsCacheTtl());

         logger.debug("Not using proxy server, reset all proxy settings OK");
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
    * Retrieves the upload_dir from the configurations.
    * 
    * @return  the upload dir
    */
   public String getUploadDir() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getUploadDir()");
      }
      String ret = data_folder+school_moe_number+"/";
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
      int ret = Integer.parseInt(systemConfig.getProperty(
              "nz.dataview.websyncclient.upload_byte_limit", "2000"));

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
      String ret = "http://localhost/"+school+".knowledge.net.nz/index.php?page=sms_upload";
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
      int ret = Integer.parseInt(systemConfig.getProperty(
              "nz.dataview.websyncclient.downloads_per_session", "5"));

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
      int ret = Integer.parseInt(systemConfig.getProperty(
              "nz.dataview.websyncclient.uploads_per_session", "5"));

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
      String ret = school_moe_number;
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
      String ret = appConfig.getProperty(
              "nz.dataview.websyncclient.school_name");
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
      int ret = Integer.parseInt(appConfig.getProperty(
              "nz.dataview.websyncclient.schedule_upload", "1"));

      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getScheduleUpload() with int: " + ret);
      }
      return ret;
   }

   /**
    * Retrieves the length of time this program will go to sleep during runs.
    * 
    * @return  the length of time in milliseconds (Long.MAX_VALUE for
    *    indefinite sleep)
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
    *	 <li><code>1</code> - run only during school hours which are defined
    *    as between 8am and 4pm</li>
    *	 <li><code>2</code> - run only during non-school hours which are defined
    *    as between 4pm and 8am</li>
    * </ul>
    * 
    * @return  0 for unrestricted running, 1 for only during school hours
    *    (8am - 4pm), 2 for only during non-school hours (4pm - 8am)
    */
   public int getProcessTime() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getProcessTime()");
      }
      int option = 0;

      try {
         option = Integer.parseInt(appConfig.getProperty(
                 "nz.dataview.websyncclient.process_time", "0"));
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
      boolean ret = appConfig.getProperty(
              "nz.dataview.websyncclient.use_proxy", "n").equalsIgnoreCase("y");

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
         port = Integer.parseInt(appConfig.getProperty(
                 "nz.dataview.websyncclient.proxy_port", "8080"));
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
      String ret = appConfig.getProperty(
              "nz.dataview.websyncclient.schema_version");
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
      String time = appConfig.getProperty(
              "nz.dataview.websyncclient.response_check_time");
      Integer responseCheckTime = 10;
      if (time != null) {
         try {
            responseCheckTime = Integer.parseInt(time);
         } catch (NumberFormatException e) {
            //Just set it to default;
         }
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getSchemaVersion() with String: " +
                 responseCheckTime);
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
      String time = appConfig.getProperty(
              "nz.dataview.websyncclient.failed_upload_count");
      Integer failedUploadCount = 0;
      if (time != null) {
         try {
            failedUploadCount = Integer.parseInt(time);
         } catch (NumberFormatException e) {
            //Just set it to default;
         }
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getFailedUploadCount() with String: " +
                 failedUploadCount);
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
      String ret = appConfig.getProperty(
              "nz.dataview.websyncclient.reports_email");
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
         ttl = Integer.parseInt(systemConfig.getProperty(
                 "nz.dataview.websyncclient.dnscachettl", "900"));
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
         ttl = Integer.parseInt(systemConfig.getProperty(
                 "nz.dataview.websyncclient.negativednscachettl", "10"));
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
      return control_folder+"logger.properties";
   }

   /**
    * Retrieves the control file directory.
    *
    * @return  the directory path.
    */
   public String getControlDirectory() {
      String ret=control_folder+school_moe_number+"/";
		if(ret!=null)
		{
			return ret;
		} else
		{
			return "Control";
		}
   }

   public String getDbhost() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getDbhost()");
      }
      String dbhost = appConfig.getProperty("nz.dataview.websyncclient.dbhost");

      if (dbhost == null) {
         dbhost = "localhost";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getDbhost() with String: " + dbhost);
      }
      return dbhost;
   }

   public String getDbuser() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getDbuser()");
      }
      String dbuser = appConfig.getProperty("nz.dataview.websyncclient.dbuser");

      if (dbuser == null) {
         dbuser = "root";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getDbuser() with String: " + dbuser);
      }
      return dbuser;
   }

   public String getDbpass() {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.getDbpass()");
      }
      String dbpass = appConfig.getProperty("nz.dataview.websyncclient.dbpass");

      if (dbpass == null) {
         dbpass = "root";
      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.getDbpass() with String: " + dbpass);
      }
      return dbpass;
   }
   
   /**
    * Check the messages in the message folder, and do appropriate processing.
    * 
    * @throws IOException
    */
   private void checkMessagesAndUpdateStatus() throws IOException {
      if (logger.isTraceEnabled()) {
         logger.trace("Entered Client.checkMessagesAndUpdateStatus()");
      }
      // First, touch status.
      sendMessage("is_up");
      clearMessage("restarting");

      // Check for restart.  Check restart first, as we can do the other two
      // immediately afterwards.
      if (clearMessage("restart")) {
         // Create the restarting response.
         sendMessage("restarting");
     }

      // Check for initTest.
      if (clearMessage("init_test")) {
         UserLog.logMessage("{TestingConnection}");
         String responseFile, responseMessage = null;
         try {
            if (testConnection()) {
               responseFile = "success_test";
            } else {
               responseFile = "fail_test";
            }
         } catch (UnknownHostException uhEx) {
            logger.debug("Unknown host in test connection.", uhEx);
            responseMessage = uhEx.getMessage();
            responseFile = "fail_test";
         }
         sendMessage(responseFile, responseMessage);
      }

      // Check for initUpload.
      if (clearMessage("init_upload")) {
         UserLog.logMessage("{Uploading}");
         forced = true;
      }

      if (clearMessage("email_logs")) {
         //emailLogs();

      }
      if (logger.isTraceEnabled()) {
         logger.trace("Exiting Client.checkMessagesAndUpdateStatus()");
      }
   }

   private void emailLogs() throws IOException {
      try {
         FileAppender appender = (FileAppender) logger.getAppender("MAIN");
         String logFilePath = appender.getFile();
         File[] files = {new File(appConfigFilePath), new File(systemConfigFilePath), new File(logConfigFilePath), new File(logFilePath)};
         String content = "Log files and configuration of websync attached.\n";
         String subject = "Log files and configuration of websync";
         File zipFile = EmailUtils.zipFiles(files);
         MimeMessage msg = EmailUtils.createEmailWithAttachment(getReportsEmail(), "websynclient@dataview.co.nz", subject, content, zipFile);
         EmailUtils.sendEmail(msg, getSmtpHost(), getSmtpUser(), getSmtpPassword());
         zipFile.delete();
      } catch (AddressException ex) {
         throw new IOException(ex);
      } catch (MessagingException ex) {
         throw new IOException(ex);
      }
   }



      /**
    * The main method.  The jsl service wrapper is expected to invoke this
    * method.
    *
    * @param   args  not used
    */
   public static void main(String[] args) {
      HashMap clients =new HashMap(0);
      HashMap schoolNames =new HashMap(0);
      ResultSet rs = null;
      
      Client controlClient=new Client("","");
      try {
         while(true)
         {
            Database globalDb=new Database();
            try{
               globalDb.Connect("global",controlClient.getDbhost(),controlClient.getDbuser(),controlClient.getDbpass());
               rs = globalDb.getTableDataRaw("SELECT school_moe_number, school_name FROM master_school_status WHERE school_moe_number<>'' AND subdomain='' AND user_access_status=-1 AND smsole='2'");
            } catch (Exception e2) {
               try {
                  Thread.sleep(10000);
               } catch (InterruptedException e) {
               }
               continue;
            }
            if (rs != null) {
               try {
                  rs.beforeFirst();
                  while (rs.next()) {
                     String moe_number="0000"+rs.getString("school_moe_number");
                     moe_number=moe_number.substring(moe_number.length()-4);
                     schoolNames.put(moe_number, rs.getString("school_name"));
                  }
               } catch (Exception e2) {
                  try {
                     Thread.sleep(10000);
                  } catch (InterruptedException e) {
                  }
                  continue;
               }
            }

            //Read through data folders
            File data_root = null;
            try {
               data_root = new File(data_folder);
            } catch (Exception e) {}
				File[] files = data_root.listFiles();
            
            for (int i = 0; i < files.length; i++) 
            {
               if(files[i].isDirectory())
               {
                  String school_moe_number=files[i].getName();
                  String padded_moe_number="0000"+school_moe_number;
                  padded_moe_number=padded_moe_number.substring(padded_moe_number.length()-4);
                  String school_name=(String)schoolNames.get(padded_moe_number);
                  if(school_name==null) continue;
                  //Instantiate new Client if none exists (and start keep_alive)
                  if(clients.get(school_name)==null)
                  {
                     //Create control folder if none exists
                     try {
                        File school_control = new File(control_folder+school_moe_number);
                        if(!school_control.exists())
                        {
                           school_control.mkdir();
                        }
                     }
                     catch (Exception e)
                     {
                       continue;
                     }
                     
                     Client new_school = new Client(school_name, school_moe_number);
                     clients.put(school_name,new_school);
                     new_school.keepalive=new KeepAlive(new_school);
                     new_school.keepalive.start();
                  }
                  Client school_client=(Client)clients.get(school_name);
                  school_client.run();
               }
            }
            
            try {
               long sleepInterval = 10000;
               Thread.sleep(sleepInterval);

            } catch (InterruptedException e) {
            } catch (IllegalArgumentException e) {
            } 
         }
      } catch (Exception e) {
         // If sometime goes wrong, log it before dying.
         System.err.println("Fatal error: " + e);
         e.printStackTrace();
         System.exit(1);
      }
   }



}
