package com.huawei.esdk.platform.abnormalevent;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.huawei.esdk.platform.abnormalevent.itf.IAbnormalevent;
import com.huawei.esdk.platform.abnormalevent.itf.IAbnormaleventRemote;
import com.huawei.esdk.platform.common.bean.abnormalevent.AbnormaleventBean;
import com.huawei.esdk.platform.common.config.ConfigManager;

public class AbnormaleventManager extends UnicastRemoteObject
    implements IAbnormalevent, IAbnormaleventRemote
{
    /**
     * UID
     */
    private static final long serialVersionUID = 1L;

    private final static Logger LOGGER = Logger.getLogger(AbnormaleventManager.class);

    private transient Map<String, AbnormaleventBean> exceptionMap = new HashMap<String, AbnormaleventBean>();
    
    private static String port = ConfigManager.getInstance().getValue("abnormalevent.port");

    public AbnormaleventManager() throws RemoteException
    {
        super(Integer.parseInt(port));
    }
    
    @Override
    public void occurException(String id, AbnormaleventBean bean)
    {
        synchronized (exceptionMap)
        {
            if (null == exceptionMap.get(id))
            {
                exceptionMap.put(id, bean);
            }
        }
    }

    @Override
    public void endException(String id, AbnormaleventBean bean)
    {
        AbnormaleventBean excBean;
        synchronized (exceptionMap)
        {
            excBean = exceptionMap.get(id);
            if (null == excBean)
            {
                return;
            }
            excBean.setEndTime(new Date());
            exceptionMap.remove(id);
        }
        writeToFile(excBean);
    }

    private void writeToFile(AbnormaleventBean bean)
    {
        String format = "yyyyMMddHHmmss";
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        LOGGER.info(bean.getObjName()
                + ","
                + bean.getOccurrence() + ","
                + sdf.format(bean.getOccurTime())
                + ","
                + sdf.format(bean.getEndTime()) + ","
                        + bean.getExceptionMessage());
    }

    @Override
    public boolean existException(String id)
    {
        return null == exceptionMap.get(id) ? false : true;
    }

    @Override
    public List<Integer> sumExceptions()
    {
        int neSum = 0;
        int fileSum = 0;
        int auSum = 0;
        List<Integer> sumList = new ArrayList<Integer>();
        sumList.add(exceptionMap.size());
        for (String key : exceptionMap.keySet())
        {
            if (key.contains("fail.to.connect"))
            {
                neSum++;
            }
            else if (key.contains("fail.to.authenticate"))
            {
                auSum++;
            }
            else
            {
                fileSum++;
            }
        }
        sumList.add(neSum);
        sumList.add(auSum);
        sumList.add(fileSum);
        return sumList;
    }

    @Override
    public List<AbnormaleventBean> queryExceptionInfos()
    {
        List<AbnormaleventBean> list = new ArrayList<AbnormaleventBean>();
        for (String key : exceptionMap.keySet())
        {
            list.add(exceptionMap.get(key));
        }
        return list;
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
