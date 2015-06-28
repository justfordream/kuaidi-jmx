/**
 * 
 */
package com.kuaidadi.jmx;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.PostConstruct;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import com.sun.jdmk.comm.HtmlAdaptorServer;

/**
 * Spring容器Bean监控类
 * @author 易振强
 *
 * 2015年6月13日
 */
@Service("springBeanMonitor")
public class SpringBeanMonitor extends NotificationBroadcasterSupport implements
		SpringBeanMXBean, ApplicationContextAware {
	private ApplicationContext applicationContext;
	
	private long sequenceNumber = 1;
	
	private Logger logger = LoggerFactory.getLogger(SpringBeanMonitor.class);

	public SpringBeanMonitor() {
		addNotificationListener(new NotificationListener() {
			@Override
			public void handleNotification(Notification notification,
					Object handback) {
				System.out.println("*** Handling new notification ***");
				System.out.println("Message: " + notification.getMessage());
				System.out.println("Seq: " + notification.getSequenceNumber());
				System.out.println("*********************************");
			}
		}, null, null);
	}

	@Override
	public void printBean(String beanName) {
		if(beanName == null || beanName.trim().equals("")) {
			logger.error("【JMX】bean名称不能为空！");
			return ;
		}
		
		Object bean = getBean(beanName);
		if(bean != null) {
			logger.info(String.format("【JMX】%s:%s", new Object[]{beanName, bean}));
		} else {
			logger.error(String.format("【JMX】%s不存在！", beanName));
		}
	}
	
	@Override
	public void modifyBean(String beanName, String fieldName, String newValue){
	     Notification n = new AttributeChangeNotification(this,
	                sequenceNumber++, System.currentTimeMillis(),
	                "modifyBean", fieldName, newValue == null ? null : newValue.getClass().getName(),
	                null, newValue);
		
		Object bean = getBean(beanName);
		
		if(bean == null) {
			throw new IllegalArgumentException("Bean名称不存在：" + beanName);
		}
		
		String[] fieldNames = fieldName.split("\\.");
		Object temp = bean;
		try {
			for(int i = 0; i < fieldNames.length; i++) {
				String name = fieldNames[i];
				Class<?> clazz = temp.getClass();
				if(i == fieldNames.length - 1) {
					Method[] methods = clazz.getDeclaredMethods();
					Method method = null;
					String setMethodName = getSetMethodName(name);
					for(Method tt : methods) {
						if(tt.getName().equals(setMethodName)) {
							method = tt;
							break;
						}
					}
					Class<?>[] types = method.getParameterTypes();
					if(types != null && types.length > 0) {
						method.invoke(temp, stringToObject(types[0], newValue));
					}
				} else {
					Method method = clazz.getDeclaredMethod(getGetMethodName(name));
					temp = method.invoke(temp);
				}
			}
			
			logger.info(String.format("【JMX】修改后的 %s：%s", new Object[]{beanName, temp}));
		} catch (Exception e) {
			
			logger.error(String.format("【JMX】修改 %s 异常", beanName), e);
		}
		
        this.sendNotification(n);
	}

	@Override
	public void printAllBean(String packagePrefix) {
		List<Object> objs = getAllBeanInfo(packagePrefix);
		if(objs != null) {
			logger.info(String.format(("【JMX】打印所有 %s bean：%s"), 
					new Object[]{packagePrefix, objs}));
		}
		
	}

	@PostConstruct
	public void start() {
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		if(server == null) {
			server = MBeanServerFactory.createMBeanServer();
		}
		
		ObjectName objectName;
		try {
			objectName = new ObjectName(
					"com.kuaidi:type=SpringBeanMXBean");
			server.registerMBean(this, objectName);
			ObjectName adapterName = new ObjectName(
					"kuaidi-agent:name=htmladapter,port=8082");
			HtmlAdaptorServer adapter = new HtmlAdaptorServer();

			server.registerMBean(adapter, adapterName);

			adapter.start();
			System.out.println("已经成功启动了！");
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}
	
	private List<Object> getAllBeanInfo(String packagePrefix) {
		if(applicationContext == null) {
			logger.error("【JMX】applicationContext为空，springBeanMonitor还未初始化！");
			return null;
		}
		
		if(packagePrefix == null) {
			packagePrefix = "";
			
		} else {
			packagePrefix = packagePrefix.trim();
		}
		
		Map<String, Object> treeMap = new TreeMap<String, Object>();
//		String[] beanNames = applicationContext.getBeanDefinitionNames();
		String[] beanNames = applicationContext.getBeanNamesForType(Object.class, false, true);
		Object bean = null;
		for (String name : beanNames) {
			bean = applicationContext.getBean(name);
			if(bean.getClass().getName().startsWith(packagePrefix)) {
				treeMap.put(name, bean);
			}
		}

		return new ArrayList<Object>(treeMap.values());
	}
	
	private String getSetMethodName(String fieldName) {
		return "set" + String.valueOf(fieldName.charAt(0)).toUpperCase() + fieldName.substring(1);
	}
	
	private String getGetMethodName(String fieldName) {
		return "get" + String.valueOf(fieldName.charAt(0)).toUpperCase() + fieldName.substring(1);
	}
	
	// 规定可以发送的Notification Type，不在Type list中的Notification不会被发送。
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        String[] types = new String[]{
            AttributeChangeNotification.ATTRIBUTE_CHANGE
        };
         
        String name = AttributeChangeNotification.class.getName();
        String description = "An attribute of this MBean has changed";
        MBeanNotificationInfo info = 
                new MBeanNotificationInfo(types, name, description);
        return new MBeanNotificationInfo[]{info};
    }
    
    private Object stringToObject(Class<?> clazz, String value) {
    	if(clazz == String.class) {
    		return value;
    	} else if(clazz == Integer.class || clazz == int.class) {
    		return Integer.valueOf(value);
    	} else if(clazz == Long.class || clazz == long.class) {
    		return Long.valueOf(value);
    	} else if(clazz == Short.class || clazz == short.class) {
    		return Short.valueOf(value);
    	} else if(clazz == Double.class || clazz == double.class) {
    		return Double.valueOf(value);
    	} else if(clazz == Float.class || clazz == float.class) {
    		return Float.valueOf(value);
    	} else if(clazz == Date.class) {
    		Date date = null;
    		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    		try {
    			date = format.parse(value.trim());
    		} catch (Exception e) {
    			try {
    				format = new SimpleDateFormat("yyyyMMddHHmmss");
    				date = format.parse(value.trim());
    			} catch (Exception e1) {
    				try {
    					format = new SimpleDateFormat("yyyy-MM-dd");
    					date = format.parse(value.trim());
    				} catch (Exception e2) {
    				}
    			}
    		}
    		
    		return date;
    	} else {
    		return null;
    	}
    }

	private Object getBean(String beanName) {
		Object bean = null;
		if(applicationContext != null) {
			try {
				bean = applicationContext.getBean(beanName);
			} catch (BeansException e) {
			}
		}
		
		return bean;
	}
	
	private <T> Collection<T> getBeansByType(Class<T> requiredType) {
		Map<String, T> map = applicationContext.getBeansOfType(requiredType, false, true);
		if(map != null) {
			return map.values();
		}
		
		return null;
	}


}
