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
import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.security.spec.InvalidKeySpecException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;

import net.iharder.base64.Base64;

import org.apache.log4j.Logger;

/**
 * Contains bunch of static methods I deemed useful for repeated use...
 * 
 * @author  William Song, Tim Owens
 * @version 1.1.0
 */
public class Utilities {
   /**
    * The logger!
    */
   private static Logger logger = Logger.getLogger(Utilities.class);
   /**
    * Determines whether the JCE provider has been installed or not.
    */
   private static boolean providerInstalled = false;
   
   /**
    * Encodes an array of <code>bytes</code> containing the raw byte data read from a file, into
    * a <code>String</code> array ready for upload.
    * 
    * @param   data  the array of bytes, typically from the BufferedInputStream.read() method
    * @return	     an array of base64 encoded, 76 char chunked Strings
    */
   public static String[] encodeForUpload(byte[] data) {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.encodeForUpload() with byte[]: " + data + "[" + data.length + "]");
      
      // the raw file must be encoded in base64
      // make sure to remove the line breaks!
      String encoded = Base64.encodeBytes(data, Base64.DONT_BREAK_LINES);
      if (logger.isDebugEnabled())
	 logger.debug("Encoded bytes to base64 successfully");

      // it must then be split into chunks of 76
      // if only there was a chunk_split equivalent in Java...
      ArrayList list = new ArrayList();
      for (int i = 0; i < encoded.length(); i += 76) {
	 String s;
	 try {
	    s = encoded.substring(i, i + 76);
	 } catch (IndexOutOfBoundsException e) {
	    // we are at the end of this set of bytes
	    s = encoded.substring(i);
	 }
	 list.add(s);
      }
      // the chunks must then be placed in an array
      String[] dataToSend = (String[])list.toArray(new String[list.size()]);
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.encodeForUpload() with String[]: " + dataToSend + "[" + dataToSend.length + "]");
      
      return dataToSend;
   }
   
   /**
    * Decodes an encoded array of <code>Strings</code> downloaded from WebSYNC, to an array
    * of <code>bytes</code> ready to be written to file.
    * 
    * @param   encodedData the array of Strings downloaded to be decoded
    * @return		   the decoded array of bytes ready to be written to file (null if no data passed in)
    */
   public static byte[] decodeFromDownload(String[] encodedData) {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.decodeFromUpload() with String[]: " + encodedData + "[" + encodedData.length + "]");
      
      byte[] data = null;
      
      // turn the array into one big string
      String implodedData = "";
      if (encodedData != null && encodedData.length > 0) {
	 for (int i = 0; i < encodedData.length; i++) {
	    implodedData += encodedData[i];
	 }
	 // now, base64 decode
	 if (implodedData.length() > 0) {
	    data = Base64.decode(implodedData);
	    if (logger.isDebugEnabled())
	       logger.debug("Decoded base64 string to byte[] successfully");
	 }
      }
      
      if (logger.isTraceEnabled())
      {
          String msg="Exiting Utilities.decodeFromData() with byte[]: ";
          if(data==null)
          {
              msg+="null";
          } else
          {
              msg+=data + "[" + data.length + "]";
          }
          
          logger.trace(msg);
      }
      
      return data;
   }
   
   /**
    * Encrypts given byte array using 3DES, with given SecretKey.  Returns the 
    * following:
    * <code>ret = {byte[] ciphertext, byte[] iv};</code>
    * Throws a bunch of exceptions if something bad happens...
    * 
    * @param   cleartext   the cleartext to encrypt
    * @param   key	   the SecretKey to encrypt with
    * @return		   the first element of this array is the byte array of the ciphertext, the second is the byte array of the IV
    * @throws  java.security.NoSuchAlgorithmException if DESede (3DES) is not available
    * @throws  javax.crypto.NoSuchPaddingException if padding scheme of 'NoPadding' is unavailable
    * @throws  java.security.InvalidKeyException   if SecretKey is invalid
    * @throws  javax.crypto.IllegalBlockSizeException if unable to process cleartext
    * @throws  javax.crypto.BadPaddingException	if cleartext is not padded correctly
    */
   public static Object[] tripleDESEncrypt(byte[] cleartext, SecretKey key) throws
      NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
      IllegalBlockSizeException, BadPaddingException {
      
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.tripleDESEncrypt() with byte[]: " + cleartext + "[" + cleartext.length + "]");
      
      if (!providerInstalled)
	 installSunJCEProvider();
      
      int padlen = (cleartext.length+15)/16;
      padlen *= 16;
      byte[] paddedtext = new byte[padlen];
      int i=0;
          
       // pad to 16 byte length multiples with " "
      for (i= 0; i < cleartext.length; i++) {
          paddedtext[i]=cleartext[i];
      }
      for(i = cleartext.length; i<padlen; i++)
      {
          paddedtext[i]=32;
      }
            
      if (logger.isDebugEnabled())
	 logger.debug("Padded ciphertext to 16 byte length multiples with spaces");
      
      Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding"); // no padding, since we pad manually with spaces
      cipher.init(Cipher.ENCRYPT_MODE, key);
      
      if (logger.isDebugEnabled())
	 logger.debug("Cipher initialised for encryption");
      
      byte[] ciphertext = cipher.doFinal(paddedtext);
      
      if (logger.isDebugEnabled())
	 logger.debug("Encryption completed successfully");
      
      // ooo naughty...
      Object[] ret = {
	 ciphertext,
	 cipher.getIV()
      };
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.tripleDESEncrypt() with Object[]: " + ret + "[" + ret.length + "]");
      
      return ret;
   }
   
   /**
    * Decrypts given byte array, assumed to be encrypted with 3DES, with given parameters.
    * The returned value, if it is a String, should be trimmed.
    * Throws a bunch of exceptions if something bad happens...
    * 
    * @param   ciphertext  the encrypted byte array to decrypt
    * @param   key	   the SecretKey with which to decrypt
    * @param   ivSpec	   the IV which the ciphertext was encrypted with
    * @return		   the decrypted byte array
    * @throws  java.security.NoSuchAlgorithmException if DESede (3DES) is not available
    * @throws  javax.crypto.NoSuchPaddingException if padding scheme of 'NoPadding' is unavailable
    * @throws  java.security.InvalidKeyException   if SecretKey is invalid
    * @throws  java.security.InvalidAlgorithmParameterException	  if SecretKey or IV is invalid
    * @throws  javax.crypto.IllegalBlockSizeException if unable to process ciphertext
    * @throws  javax.crypto.BadPaddingException	if ciphertext is not padded correctly
    */
   public static byte[] tripleDESDecrypt(byte[] ciphertext, SecretKey key, IvParameterSpec ivSpec) throws 
      NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
      InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
      
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.tripleDESDecrypt() with byte[]: " + ciphertext + "[" + ciphertext.length + "]");
      
      if (!providerInstalled)
	 installSunJCEProvider();
      
      Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
      
      if (logger.isDebugEnabled())
	 logger.debug("Cipher initialised for encryption");
      
      byte[] ret = cipher.doFinal(ciphertext);
      
      if (logger.isDebugEnabled())
	 logger.debug("Encryption completed successfully");
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.tripleDESDecrypt() with byte[]: " + ret + "[" + ret.length + "]");
      
      return ret;
   }
   
   /**
    * Attempts to install Sun JCE Provider if required for DESede.
    */
   public static void installSunJCEProvider() {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.installSunJCEProvider()");
      
      try {
	 Cipher c = Cipher.getInstance("DESede/CBC/NoPadding");
      } catch (Exception e) {
	 
	 if (logger.isDebugEnabled())
	    logger.debug("Exception occurred while initialising Cipher: " + e.getMessage() + ", installing Sun JCE Provider");
	 
	 Provider sun = new com.sun.crypto.provider.SunJCE();
	 Security.addProvider(sun);
	 
	 if (logger.isDebugEnabled())
	    logger.debug("Sun JCE Provider installed successfully");
      }
      
      providerInstalled = true;
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.installSunJCEProvider()");
   }
   
   /**
    * Pads a given String to multiples of 16 characters in length, with " ".
    * Primarily used for Java/PHP 3DES encryption/decryption interoperability.
    * Obtained from: http://nz.php.net/manual/en/ref.mcrypt.php
    * 
    * @param   in the String to pad
    * @return	  the padded String
    */
   public static String padString(String in) {
      int slen = (in.length() % 16);
      int i = (16 - slen);
      if ((i > 0) && (i < 16)) {
	 StringBuffer buf = new StringBuffer(in.length() + i);
	 buf.insert(0, in);
	 for (i = (16 - slen); i > 0; i--) {
	    buf.append(" ");
	 }
	 return buf.toString();
      } else {
	 return in;
      }
   }
   
   /**
    * @return xor of arrays a and b
    */
   public static char[] xor ( char[] a, char[] b )
      {
      int length = Math.min (a.length, b.length );
      char[] result = new char[length];
      for ( int i=0; i<length ; i++ )
         {
         // ^ is the xor operator
         // see http://mindprod.com/jgloss/xor.html
         result[i] = (char) ( a[i] ^ b[i] );
         }
      return result;
      }

   /**
    * A simple obscuring method using XOR. Works both ways.
    *
    * The keystring is base64 - it needs to be a strict multiple of 3 characters
    * (padded with =) and only containing characters valid in base64.
    *
    * If you change it, you need to work out a way to convert all existing
    * passwords too...
    *
    * The key string needs to be 1/3 longer than the longest string to encode.
    * @param cleartext
    * @return
    */
   private static String xorencode(String cleartext,boolean toBase64) {
      String ret;
      char[] encoded;
      String keystring="GoTheD4t4V13wMassive/MaryHadALittleLambTheDoctorWasSuprisedWhenOldMcDonaldHadAFarmTheDoctorNearlyDied=";
      byte[] keybyte = net.iharder.base64.Base64.decode(keystring);
      char[] key= new char[keybyte.length];
      for(int i=0; i<keybyte.length;i++) key[i]=(char)keybyte[i];
          
      encoded = xor( cleartext.toCharArray(), key );
      
      if(toBase64)
      {
          byte[] encodedbyte = new byte[encoded.length];
          for(int i=0; i<encoded.length;i++) encodedbyte[i]=(byte)encoded[i];
          ret=net.iharder.base64.Base64.encodeBytes(encodedbyte,Base64.DONT_BREAK_LINES);
      } else
      {
          ret=new String(encoded);
      }
      return ret;
   }
   
   /**
    * Encode a string for storing in the config. The @# is just a little
    * marker to tell us it's encoded.
    *
    * @param cleartext
    * @return
    */
   public static String simpleEncrypt(String cleartext) {
       return "@#"+xorencode(cleartext,true);
   }
   
   /**
    * Decode a string. However, it could be a plain text version from an old
    * config, so check for the @# first.
    *
    * @param ciphertext
    * @return
    */
   public static String simpleDecrypt(String ciphertext) {
       //Need to cater for existing unencrypted strings
      if(ciphertext.length() >= 2 && ciphertext.substring(0,2).equals("@#"))
      {
          byte[] encodedbyte=net.iharder.base64.Base64.decode(ciphertext.substring(2));
          char[] encodedchar= new char[encodedbyte.length];
          for(int i=0; i<encodedbyte.length;i++) encodedchar[i]=(char)encodedbyte[i];
          ciphertext= new String(encodedchar);
          return xorencode(ciphertext,false);
      } else 
      {
          return ciphertext;
      }
   }   
   
   /**
    * Determines whether the given URL is reachable.
    * 
    * @param   url   the URL to query
    * @return	     true if reachable, false if not
    * @throws  java.net.UnknownHostException if DNS lookup for the URL failed
    * @throws  java.io.IOException	     if network error
    */
   public static boolean isReachable(String url) throws UnknownHostException, IOException {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.isReachable() with String: " + url);
      
      boolean okSoFar = false;
      
      if (logger.isDebugEnabled())
	 logger.debug("Creating InetAddress with: " + url);
      
      InetAddress address = InetAddress.getByName(url);
      okSoFar = true;
      
      String hostname = address.getHostName();
      if (logger.isDebugEnabled())
	 logger.debug("Got hostname of: " + hostname);
      
      String ipAdd = address.getHostAddress();
      if (logger.isDebugEnabled())
	 logger.debug("Got IP Address of: " + ipAdd);
      
      if (logger.isDebugEnabled())
	 logger.debug("Determining if reachable");
      okSoFar = address.isReachable(3000);
      if (logger.isDebugEnabled())
	 logger.debug("Is " + url + " reachable? " + okSoFar);
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.isReachable() with boolean: " + okSoFar);
      
      return okSoFar;
   }
   
   /**
    * Determines whether the current timestamp is within school hours.
    * School hours are defined as between 8am and 4pm, Monday to Friday.
    * Ignores school holidays.
    *
    * @return  true if given timestamp is in school hours, false otherwise
    */
   public static boolean isSchoolHours() {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.isSchoolHours()");
      
      boolean ret = isSchoolHours(System.currentTimeMillis());
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.isSchoolHours() with boolean: " + ret);
      
      return ret;
   }
   
   /**
    * Determines whether the given timestamp is within school hours.
    * School hours are defined as between 8am and 4pm, Monday to Friday.
    * Ignores school holidays.
    * 
    * @param   currentTime the timestamp to check
    * @return		   true if given timestamp is in school hours, false otherwise
    */
   public static boolean isSchoolHours(long currentTime) {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.isSchoolHours() with long: " + currentTime);
      
      boolean ret = false;
      
      // manipulating time isn't as easy as PHP...
      
      Date currentDate = new Date(currentTime);      
      Calendar calendar = Calendar.getInstance();
      
      calendar.clear();
      calendar.setTimeInMillis(currentTime);
      
      // determine if given day is the weekend
      int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
      boolean isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
      
      // set time to 8am
      calendar.set(Calendar.HOUR_OF_DAY, 8);
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      
      Date schoolStartDate = calendar.getTime();
      
      // set time to 4pm
      calendar.set(Calendar.HOUR_OF_DAY, 16);
      
      Date schoolEndDate = calendar.getTime();
      
      if (logger.isDebugEnabled())
	 logger.debug("Got school start date: " + schoolStartDate + ", end date: " + schoolEndDate + ", current date: " + currentDate);
      
      // nice and easy...
      ret = !isWeekend && currentDate.after(schoolStartDate) && currentDate.before(schoolEndDate);
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.isSchoolHours() with boolean: " + ret);
      
      return ret;
   }
   
   /**
    * Takes an array of bytes and converts each byte into an ascii char, and returns
    * the String containing all the chars.
    * 
    * @param   binary	the array of bytes to convert
    * @return		the converted String
    */
   public static String binaryToString(byte[] binary) {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.binaryToString() with byte[]: " + binary + "[" + binary.length + "]");
      
      StringBuffer buffer = new StringBuffer();
      
      if (binary != null && binary.length > 0) {
	 for (int i = 0; i < binary.length; i++) {
	    byte curr = binary[i];
//	    if ((curr >= 32 && curr <= 126) || curr == 13 || curr == 10) {
//	       buffer.append((char)curr);
//	    } else {
//	       if (logger.isTraceEnabled())
//		  logger.trace("Exiting Utilities.binaryToString() with null");
//	       
//	       return null;
//	    }
	    buffer.append((char)curr);
	 }
      }
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.binaryToString() with: " + buffer.toString());
      
      return buffer.toString();
   }
   
   /**
    * Creates the SecretKey object from given keystring, used for encryption/decryption.
    * 
    * @param   keyString   the key string
    * @return		   the SecretKey
    * @throws  java.io.IOException
    * @throws  java.security.NoSuchAlgorithmException
    * @throws  java.security.InvalidKeyException
    * @throws  java.security.spec.InvalidKeySpecException
    */
   public static SecretKey getKey(String keyString) throws IOException,
      NoSuchAlgorithmException, InvalidKeyException,
      InvalidKeySpecException {
      
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.readKey() with String: " + keyString);
      
      byte[] rawkey = keyString.getBytes();

      DESedeKeySpec keyspec = new DESedeKeySpec(rawkey);
      SecretKeyFactory keyfactory = SecretKeyFactory.getInstance("DESede");
      SecretKey key = keyfactory.generateSecret(keyspec);
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.readKey() with SecretKey: " + key);
      
      return key;
   }
   
   /**
    * Produces the MD5 has of given array of bytes.
    * Code from: http://www.anyexample.com/programming/java/java_simple_class_to_compute_md5_hash.xml
    * 
    * @param   bytes  the bytes to hash
    * @return	     the MD5 hash
    * @throws  java.security.NoSuchAlgorithmException
    * @throws  java.io.UnsupportedEncodingException
    */
   public static String MD5(byte[] bytes) throws NoSuchAlgorithmException, UnsupportedEncodingException {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.MD5() with byte[]: " + bytes.length);
      
      MessageDigest md;
      md = MessageDigest.getInstance("MD5");
      byte[] md5hash = new byte[32];
//      md.update(text.getBytes("iso-8859-1"), 0, text.length());
      md.update(bytes);
      md5hash = md.digest();
      String ret = convertToHex(md5hash);
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.MD5() with String: " + ret);
      
      return ret;
   }
   
   /**
    * Converts given bytes into hexidecimal.
    * Code from: http://www.anyexample.com/programming/java/java_simple_class_to_compute_md5_hash.xml
    * 
    * @param   data  bytes to convert
    * @return	     the hex equivalent
    */
   private static String convertToHex(byte[] data) {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.convertToHex() with byte[]: " + data + "[" + data.length + "]");
      
      StringBuffer buf = new StringBuffer();
      for (int i = 0; i < data.length; i++) {
	 int halfbyte = (data[i] >>> 4) & 0x0F;
	 int two_halfs = 0;
	 do {
	    if ((0 <= halfbyte) && (halfbyte <= 9))
	       buf.append((char) ('0' + halfbyte));
	    else
	       buf.append((char) ('a' + (halfbyte - 10)));
	       halfbyte = data[i] & 0x0F;
	 } while(two_halfs++ < 1);
      }
      
      String ret = buf.toString();
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.convertToHex() with String: " + ret);
      
      return ret;
   }
   
   /**
    * Reads a given file and returns its contents in an array of bytes.
    * Code from: http://www.exampledepot.com/egs/java.io/File2ByteArray.html
    * 
    * @param   file  the file to read
    * @return	     array of bytes containing the file contents
    * @throws java.io.IOException
    */
   public static byte[] getBytesFromFile(File file) throws IOException {
      if (logger.isTraceEnabled())
	 logger.trace("Entered Utilities.getBytesFromFile() with File: " + file);
      
      InputStream is = new FileInputStream(file);

      // Get the size of the file
      long length = file.length();

      // You cannot create an array using a long type.
      // It needs to be an int type.
      // Before converting to an int type, check
      // to ensure that file is not larger than Integer.MAX_VALUE.
      if (length > Integer.MAX_VALUE) {
	 // File is too large
	 throw new IOException("File " + file + " is too large to be read safely");
      }

      // Create the byte array to hold the data
      byte[] bytes = new byte[(int)length];

      // Read in the bytes
      int offset = 0;
      int numRead = 0;
      while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
	 offset += numRead;
      }

      // Ensure all the bytes have been read in
      if (offset < bytes.length) {
	 throw new IOException("Could not completely read file " + file.getName());
      }

      // Close the input stream and return bytes
      is.close();
      
      if (logger.isTraceEnabled())
	 logger.trace("Exiting Utilities.getBytesFromFile() with byte[]: " + bytes);
      
      return bytes;
   }
}
