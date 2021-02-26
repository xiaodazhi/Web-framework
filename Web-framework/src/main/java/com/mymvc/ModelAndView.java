package com.mymvc;

import java.util.HashMap;
import java.util.Map;

/**
 * model模型  数据模型(用来存入request作用域带走的)
 * view视图  用来转发展示用的(转发路径--->展示的视图层资源)
 * 用来代替request.setAttribute();方法   再在DispatcherServlet中去处理响应信息
 */
public class ModelAndView {
    //目的是为了将一个map集合和一个String路径包装在一起
    //属性
    private String viewName;//视图的响应路径
    //属性
    private Map<String, Object> attributeMap = new HashMap();


    //如下这两个方法是给Controller用户使用的
    //设计方法给两个属性存入具体的数据
    public void setViewName (String viewName){
        this.viewName = viewName;
    }
    public void addAttribute (String key, Object value){
        this.attributeMap.put(key, value);
    }


    //如下这三个方法是留给框架使用的
    String getViewName () {
        return this.viewName;
    }
    Object getAttribute (String key) {
        return this.attributeMap.get(key);
    }
    Map<String,Object> getAttributeMap () {
        return this.attributeMap;
    }
}
