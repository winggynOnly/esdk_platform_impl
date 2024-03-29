package com.huawei.esdk.platform.remote;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

import org.apache.log4j.Logger;

import com.huawei.esdk.platform.abnormalevent.itf.IAbnormaleventRemote;
import com.huawei.esdk.platform.common.config.ConfigManager;
import com.huawei.esdk.platform.debugging.itf.IRemoteDebugging;

public class RemoteManager
{
    private static final Logger LOGGER = Logger.getLogger(RemoteManager.class);
    
    private IRemoteDebugging debuggingManager;
    
    private IAbnormaleventRemote abnormaleventRemote;
    
    public void bindRemote()
    {
        String serverEvent = ConfigManager.getInstance().getValue("remote.serverEvent");
        String serverLog4j = ConfigManager.getInstance().getValue("remote.serverLog4j");
        String port = ConfigManager.getInstance().getValue("remote.port");
        
        try
        {
            LocateRegistry.createRegistry(Integer.parseInt(port));
            Naming.rebind("//127.0.0.1:" + port + "/" + serverEvent, abnormaleventRemote);
            Naming.rebind("//127.0.0.1:" + port + "/" + serverLog4j, debuggingManager);
        }
        catch (RemoteException e)
        {
            LOGGER.error("", e);
        }
        catch (MalformedURLException e)
        {
            LOGGER.error("", e);
        }
    }
    
    public IRemoteDebugging getDebuggingManager()
    {
        return debuggingManager;
    }
    
    public void setDebuggingManager(IRemoteDebugging debuggingManager)
    {
        this.debuggingManager = debuggingManager;
    }
    
    public IAbnormaleventRemote getAbnormaleventRemote()
    {
        return abnormaleventRemote;
    }
    
    public void setAbnormaleventRemote(IAbnormaleventRemote abnormaleventRemote)
    {
        this.abnormaleventRemote = abnormaleventRemote;
    }
}
