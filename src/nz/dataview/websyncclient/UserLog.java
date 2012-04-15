package nz.dataview.websyncclient;

import org.apache.log4j.Logger;

public class UserLog {
   private static Logger logger = Logger.getLogger(UserLog.class);

   public static void logMessage(String message) {
      if (!logger.isInfoEnabled())
         throw new RuntimeException(UserLog.class + " requires to be configred at INFO level");

      logger.info(message);
   }
}
