package com.huawei.esdk.platform.log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;

import com.huawei.esdk.platform.common.config.ConfigManager;
import com.huawei.esdk.platform.common.utils.NumberUtils;
import com.huawei.esdk.platform.common.utils.StringUtils;

public class LogFileUploaderTask implements Runnable
{
    private static final Logger LOGGER = Logger.getLogger(LogFileUploaderTask.class);
    
    private static final String LOG_PRODUCT_SERVER = "eSDK-Server";
    
    private long getSleepTime()
    {
        Random generator = new Random();
        double num = generator.nextDouble() / 2;
        
        long result =
            (long)(60L * NumberUtils.parseIntValue(ConfigManager.getInstance()
                .getValue("platform.upload.log.file.interval", "60")) * num);
        
        return result;
    }
    
    @Override
    public void run()
    {
        try
        {
            long sleepTime;
            while (true)
            {
                sleepTime = getSleepTime();
                LOGGER.debug("sleepTime=" + sleepTime);
                TimeUnit.SECONDS.sleep(sleepTime);
                try
                {
                    //upload Logs
                    uploadLogFiles();
                }
                catch (Exception e)
                {
                    LOGGER.error("", e);
                }
            }
        }
        catch (InterruptedException e)
        {
            //InterruptedException Exception happened
            LOGGER.error("", e);
        }
    }
    
    private boolean hasUploadRights(HttpClient httpClient)
    {
        HttpPost httpPost = new HttpPost(ConfigManager.getInstance().getValue("log.server.url"));
        MultipartEntity mutiEntity = new MultipartEntity(HttpMultipartMode.STRICT);
        httpPost.setEntity(mutiEntity);
        try
        {
            mutiEntity.addPart("LogFileInfo",
                new StringBody("{\"product\":\"" + LOG_PRODUCT_SERVER + "\"}", Charset.forName("UTF-8")));
        }
        catch (UnsupportedEncodingException e)
        {
            LOGGER.error("UTF-8 is not supported encode");
        }
        
        HttpResponse httpResponse;
        try
        {
            httpResponse = httpClient.execute(httpPost);
            HttpEntity httpEntity = httpResponse.getEntity();
            String content = EntityUtils.toString(httpEntity);
            if (content.contains("\"resultCode\":\"3\""))
            {
                return false;
            }
        }
        catch (ClientProtocolException e)
        {
            LOGGER.error("", e);
            return false;
        }
        catch (IOException e)
        {
            LOGGER.error("", e);
            return false;
        }
        
        return true;
    }
    
    private void uploadLogFiles()
    {
        LOGGER.debug("uploadLogFiles begin");
        HttpClient httpClient = new DefaultHttpClient();
        
        if (!hasUploadRights(httpClient))
        {
            LOGGER.debug("Current machine is not allowed to upload file to server or the server has something wrong.");
            return;
        }
        
        String[] logTypes = new String[] {"run", "interface", "operation"};
        String logFile;
        File file;
        boolean currentWritingLogFileFlag = false;
        for (String logType : logTypes)
        {
            //Reset variables
            currentWritingLogFileFlag = false;
            
            //Loop all log files for specified log type
            while (true)
            {
                logFile = LogFileUploaderHelper.getOldestLogFile(logType);
                LOGGER.debug("logFile=" + logFile);
                if (StringUtils.isEmpty(logFile)
                    || (currentWritingLogFileFlag && !LogFileUploaderHelper.isBackLogFile(logFile)))
                {
                    break;
                }
                else
                {
                    if (!LogFileUploaderHelper.isBackLogFile(logFile))
                    {
                        currentWritingLogFileFlag = true;
                    }
                    file = new File(logFile);
                    if (0 == file.length())
                    {
                        continue;
                    }
                    
                    if (!LogFileUploaderHelper.isBackLogFile(logFile))
                    {
                        logFile = processCurrentLogFile(logType, logFile);
                    }
                    if (StringUtils.isEmpty(logFile))
                    {
                        continue;
                    }
                    logFile = moveFile(logFile);
                    if (doLogFileUpload(logFile, LOG_PRODUCT_SERVER))
                    {
                        LogFileUploaderHelper.deleteLogFile(logFile);
                    }
                }
            }
        }
        
        LOGGER.debug("uploadLogFiles end");
    }
    
    private String moveFile(String logFile)
    {
        if (StringUtils.isEmpty(logFile))
        {
            return logFile;
        }
        
        File file = new File(logFile);
        //Move the file to temp folder for uploading
        File destFile = new File(file.getParent() + File.separator + "temp" + File.separator + file.getName());
        try
        {
            if (destFile.exists())
            {
                destFile.delete();
            }
            FileUtils.moveFile(file, destFile);
            file = destFile;
        }
        catch (IOException e)
        {
            LOGGER.error("", e);
        }
        
        return file.getPath();
    }
    
    private String processCurrentLogFile(String logType, String logFile)
    {
        File file = new File(logFile);
        //Different appenders for different file types
        RollingFileAppender appender;
        if ("interface".equalsIgnoreCase(logType))
        {
            appender =
                (RollingFileAppender)Logger.getLogger("com.huawei.esdk.platform.log.InterfaceLog").getAppender("FILE1");
        }
        else if ("operation".equalsIgnoreCase(logType))
        {
            try
            {
                File destDir = new File(file.getParent() + File.separator + "temp" + File.separator + file.getName());
                FileUtils.moveFile(file, destDir);
                FileUtils.moveFile(destDir, file);
                return logFile;
            }
            catch (IOException e)
            {
                return "";
            }
        }
        else
        {
            appender = (RollingFileAppender)Logger.getRootLogger().getAppender("fileLogger");
        }
        
        long origSize = appender.getMaximumFileSize();
        appender.setMaximumFileSize(file.length());
        if ("interface".equalsIgnoreCase(logType))
        {
            LOGGER.debug("Rolling the interface log file");
            //Call the rooOver method in order to backup the current log file for uploading
            appender.rollOver();
        }
        else
        {
            //Call the rooOver method in order to backup the current log file for uploading
            appender.rollOver();
            LOGGER.debug("Log File size reset");
        }
        LOGGER.debug("origSize=" + origSize + ", logType=" + logType);
        appender.setMaximumFileSize(origSize);
        String result = logFile + ".1";
        file = new File(result);
        if (file.exists())
        {
            return result;
        }
        else
        {
            return "";
        }
    }
    
    private boolean doLogFileUpload(String fileNameWithPath, String product)
    {
        if (StringUtils.isEmpty(fileNameWithPath))
        {
            return true;
        }
        
        String content = doUploadByHttpURLConnection(fileNameWithPath, product);
        if (content.contains("\"resultCode\":\"0\""))
        {
            return true;
        }
        else
        {
            LOGGER.warn("File file " + fileNameWithPath + " is uploaded to log server failed,"
                + " the response from server is " + content);
        }
        
        return false;
    }
    
    private String doUploadByHttpURLConnection(String fileNameWithPath, String product)
    {
        String boundary = UUID.randomUUID().toString();
        System.out.println(boundary);
        String crlf = "\r\n";
        String twoHyphens = "--";
        File file = new File(fileNameWithPath);
        InputStream responseStream = null;
        BufferedReader responseStreamReader = null;
        StringBuilder stringBuilder = new StringBuilder();
        
        try
        {
            URL url = new URL(ConfigManager.getInstance().getValue("log.server.url"));
            HttpURLConnection httpUrlConnection = (HttpURLConnection)url.openConnection();
            httpUrlConnection.setUseCaches(false);
            httpUrlConnection.setDoOutput(true);
            
            httpUrlConnection.setRequestMethod("POST");
            httpUrlConnection.setRequestProperty("Connection", "Keep-Alive");
            httpUrlConnection.setRequestProperty("Cache-Control", "no-cache");
            httpUrlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            
            DataOutputStream request = new DataOutputStream(httpUrlConnection.getOutputStream());
            //JSON String
            request.writeBytes(twoHyphens + boundary + crlf);
            request.writeBytes("Content-Disposition: form-data; name=\"LogFileInfo\"" + crlf);
            request.writeBytes(crlf);
            request.writeBytes("{\"product\":\"" + product + "\"}");
            request.writeBytes(crlf);
            
            //Content wrapper
            request.writeBytes(twoHyphens + boundary + crlf);
            request.writeBytes("Content-Disposition: form-data; name=\"LogFile\"; filename=\"" + file.getName() + "\""
                + crlf);
            request.writeBytes("Content-Type: text/plain" + crlf);
            request.writeBytes(crlf);
            
            //Write File content
            request.write(FileUtils.readFileToByteArray(file));
            
            //End content wrapper:
            request.writeBytes(crlf);
            request.writeBytes(twoHyphens + boundary + twoHyphens + crlf);
            
            //Flush output buffer:
            request.flush();
            request.close();
            
            int responseCode = httpUrlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
            {
                //Get Response
                responseStream = new BufferedInputStream(httpUrlConnection.getInputStream());
                
                responseStreamReader = new BufferedReader(new InputStreamReader(responseStream));
                
            }
            else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND)
            {
                return HttpURLConnection.HTTP_NOT_FOUND + "";
            }
            else
            {
                return "";
            }
            String line = "";
            
            while ((line = responseStreamReader.readLine()) != null)
            {
                stringBuilder.append(line).append("\n");
            }
        }
        catch (ClientProtocolException e)
        {
            LOGGER.error("", e);
        }
        catch (IOException e)
        {
            LOGGER.error("", e);
        }
        finally
        {
            if (null != responseStreamReader)
            {
                try
                {
                    responseStreamReader.close();
                }
                catch (IOException e)
                {
                    LOGGER.error("", e);
                }
            }
            if (null != responseStream)
            {
                try
                {
                    responseStream.close();
                }
                catch (IOException e)
                {
                    LOGGER.error("", e);
                }
            }
        }
        
        return stringBuilder.toString();
    }
    
}
