package com.huawei.esdk.platform.debugging;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.huawei.esdk.platform.common.config.ConfigManager;
import com.huawei.esdk.platform.debugging.itf.IRemoteDebugging;

public class DebuggingManager extends UnicastRemoteObject
    implements IRemoteDebugging
{
    /**
     * UID
     */
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(DebuggingManager.class);
    
    private static String port = ConfigManager.getInstance().getValue("debugging.port");
    
    public DebuggingManager() throws RemoteException
    {
        super(Integer.parseInt(port));
    }

    public boolean setLoggerLevel(String packageName, String levelName)
    {
        LOGGER.debug("packageName=" + packageName + ", levelName="+ levelName);
        Level level = Level.toLevel(levelName);
        if ("".equals(packageName))
        {
            Logger logger = LogManager.getRootLogger();
            logger.setLevel(level);
            if (logger.getLevel().toString().equalsIgnoreCase(levelName))
            {
                return true;
            }
            return false;
        }
        Logger logger = LogManager.getLogger(packageName);
        if (null != logger)
        {
            logger.setLevel(level);
            Level le = logger.getLevel();
            if (null != le && le.toString().equalsIgnoreCase(levelName))
            {
                return true;
            }
        }
        return false;
    }
    
    public void destroy()
    {
        try
        {
            UnicastRemoteObject.unexportObject(this, true);
        }
        catch (NoSuchObjectException e)
        {
            LOGGER.error("", e);
        }
    }
}
