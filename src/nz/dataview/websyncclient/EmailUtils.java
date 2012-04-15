package nz.dataview.websyncclient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.apache.commons.io.IOUtils;

/*
 * Class for emailing and attachment of files
 */


public class EmailUtils {

   public static File zipFiles(File[] files) throws FileNotFoundException, IOException {

      File zipFile = File.createTempFile("websync", ".zip");
      ZipOutputStream zipStream = new ZipOutputStream(new FileOutputStream(zipFile));
      zipStream.setLevel(Deflater.BEST_COMPRESSION);

      try {
         for (File file:files) {
            zipStream.putNextEntry(new ZipEntry(file.getAbsolutePath()));
            IOUtils.copy(new FileInputStream(file), zipStream);
            zipStream.closeEntry();
         }
      } finally {
         zipStream.close();
      }

      return zipFile;

   }

   public static MimeMessage createEmailWithAttachment(String to, String from, String subject, String message, File attachment)
           throws AddressException, MessagingException {

      // create a message
      MimeMessage msg = new MimeMessage(getSession());
      msg.setFrom(new InternetAddress(from));
      InternetAddress[] address = {new InternetAddress(to)};
      msg.setRecipients(Message.RecipientType.TO, address);
      msg.setSubject(subject);

      // create and fill the first message part
      MimeBodyPart mbp1 = new MimeBodyPart();
      mbp1.setText(message);

      // create the second message part
      MimeBodyPart mbp2 = new MimeBodyPart();
      // attach the file to the message
      FileDataSource fds = new FileDataSource(attachment);
      mbp2.setDataHandler(new DataHandler(fds));
      mbp2.setFileName(fds.getName());

      // create the Multipart and add its parts to it
      Multipart mp = new MimeMultipart();
      mp.addBodyPart(mbp1);
      mp.addBodyPart(mbp2);

      // add the Multipart to the message
      msg.setContent(mp);

      // set the Date: header
      msg.setSentDate(new Date());

      // save the message
      msg.saveChanges();
      return msg;
   }

   public static MimeMessage createEmail(String to, String from, String subject, String message)
           throws AddressException, MessagingException {
      // create a message
      MimeMessage msg = new MimeMessage(getSession());
      msg.setFrom(new InternetAddress(from));
      InternetAddress[] address = {new InternetAddress(to)};
      msg.setRecipients(Message.RecipientType.TO, address);
      msg.setSubject(subject);
      msg.setContent(message, "text/plain");

      // set the Date: header
      msg.setSentDate(new Date());

      // save the message
      msg.saveChanges();
      return msg;

   }

   public static void sendEmail(MimeMessage msg, String host, String username, String password)
           throws NoSuchProviderException, MessagingException {
      Transport tr = getSession().getTransport("smtp");
      try {
         if (!username.isEmpty()) tr.connect(host, username, password);
            else tr.connect(host, "", "");
         tr.sendMessage(msg, msg.getAllRecipients());
      } finally {
         tr.close();
      }
   }

   private static Session getSession() {
      // create some properties and get the default Session
      Properties props = System.getProperties();
      return Session.getInstance(props);
   }


}
