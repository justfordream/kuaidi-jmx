/**
 * 
 */
package com.kuaidadi.jmx;


/**
 * Spring容器Bean监控类JMX接口
 * @author manzhizhen
 *
 * 2015年6月13日
 */
public interface SpringBeanMXBean {
	
	/**
	 * 修改某个bean对象的属性值
	 * 
	 * @param beanName 	bean的名称
	 * @param fieldName	bean的属性名称，可以用"."分隔
	 * @param newValue	新值
	 */
	void modifyBean(String beanName, String fieldName, String newValue);
	
	/**
	 * 打印Spring容器中所有bean
	 * 直接调用bean的toString方法，
	 * @param packagePrefix bean类的包前缀，用于过滤，如果为空，则打印所有bean
	 */
	void printAllBean(String packagePrefix);
	
	/**
	 * 打印bean名称的
	 * @param beanName
	 */
	void printBean(String beanName);
}
