package com.mymvc;

import com.alibaba.fastjson.JSONObject;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Web框架
 */
public class DispatcherServlet extends HttpServlet {

    @Override
    public void init(ServletConfig config) throws ServletException {
        this.loadPropertiesFile();
    }


    //用于存储ApplicationContext.properties中请求名和真实类名的对应关系
    private Map<String, String> realClassNameMap = new HashMap<>();
    //用于存储每一个Controller控制层类的对象  实现单例  延迟加载
    //key  请求名  value  对象
    private Map<String, Object> objectMap = new HashMap<>();
    //用于存储Controller对象中的所有方法
    //key 对象  value  Map<方法名, 方法>
    private Map<Object, Map<String, Method>> objectMethodMap = new HashMap<>();
    //重写servlet的init方法
    //调用loadProperties方法  创建这个Servlet对象时就执行
    //读取ApplicationContext.properties配置文件  放入realClassNameMap中
        //此方法用于一次性读取ApplicationContext.properties文件 存入缓存(map集合)
    private void loadPropertiesFile () {
        try {
            //读取ApplicationContext.properties文件  通过请求名得到类全名
            Properties properties = new Properties();
            InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ApplicationContext.properties");
            properties.load(inputStream);
            //获取文件中的全部信息
            Enumeration en = properties.propertyNames();
            while (en.hasMoreElements()) {
                String key = (String) en.nextElement();//获取key
                String value = properties.getProperty(key);
                //存入集合
                realClassNameMap.put(key, value);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //此方法负责解析读取到的uri
    //参数  String类型的uri  返回值  解析后的请求名
    private String parseURI (String uri) {
        //通过截取解析出请求名
        String className = uri.substring(uri.lastIndexOf("/") + 1, uri.indexOf("."));
        return className;
    }

    //此方法负责根据请求名  找到对应的Controller对象
    //参数  请求名  返回值  对象
    private Object findObject (String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Object obj = objectMap.get(className);
        if (obj == null) {
            String realClassName = realClassNameMap.get(className);
            if (realClassName == null) {
                throw new RuntimeException(className + "不存在");
            }
            //反射获取Class
            Class clazz = Class.forName(realClassName);
            //反射创建对象
            obj = clazz.newInstance();
            //存入objectMap集合
            objectMap.put(className, obj);
            //-------------------
            //创建对象之后  解析对象中的所有方法
            //获取当前clazz中的所有方法
            Method[] methods = clazz.getDeclaredMethods();
            //存储到objectMethodMap中
            Map<String, Method> methodMap = new HashMap<>();
            for (Method method : methods) {
                methodMap.put(method.getName(), method);
            }
            objectMethodMap.put(obj, methodMap);
        }
        return obj;
    }

    //此方法负责反射  找寻方法
    //参数   String类型方法名   返回值  Method
    private Method findMethod (Object obj, String methodName) throws NoSuchMethodException {
        //去objectMethodMap集合中寻找方法
        Map<String, Method> methodMap = this.objectMethodMap.get(obj);
        Method method = methodMap.get(methodName);
        return method;
    }

    //为injectionParameters方法做支持  注入基础类型的参数
    private Object injectionNormal (Class parameterClazz, RequestParam paramAnnotation, HttpServletRequest request) {
        Object result = null;
        //获取注解中的key
        String key = paramAnnotation.value();
        //从请求中获取key对应的值
        String value = request.getParameter(key);
        //根据不同类型进行判断  存值
        if (parameterClazz == String.class) {
            result = value;
        } else if (parameterClazz == int.class || parameterClazz == Integer.class) {
            result = new Integer(value);
        } else if (parameterClazz == float.class || parameterClazz == Float.class) {
            result = new Float(value);
        } else if (parameterClazz == double.class || parameterClazz == Double.class) {
            result = new Double(value);
        } else if (parameterClazz == boolean.class || parameterClazz == Boolean.class) {
            result = new Boolean(value);
        } else {
            //如果不行  就抛出异常
        }
        return result;
    }

    //为injectionParameters方法做支持  注入Map类型的参数
    private Map injectionMap (Object obj, HttpServletRequest request) {
        //类型还原
        Map map = (Map)obj;
        //因为Map中本身没有key
        //用请求过来的所有信息作为key
        Enumeration en = request.getParameterNames();//获取请求全部的key
        while (en.hasMoreElements()) {
            String key = (String)en.nextElement();
            String value = request.getParameter(key);
            map.put(key, value);
        }
        return map;
    }

    //为injectionParameters方法做支持  注入domain类型的参数
    private Object injectDomain (Object obj, Class parameterClazz, HttpServletRequest request) throws Exception {
        //domain  属性才是key  分析对象  获取里面的属性名字作为key  去请求中寻找
        Field[] fields = parameterClazz.getDeclaredFields();
        //获取每一个属性名作为key
        for (Field field : fields) {
            //操作私有属性
            field.setAccessible(true);
            //获取每一个私有属性
            String fieldName = field.getName();
            //用request去取值
            String value = request.getParameter(fieldName);
            //将这个value存入domain中的某一个属性中
            Class fieldType = field.getType();
            //找寻属性类型对应的构造方法
            Constructor con = fieldType.getConstructor(String.class);
            //执行这个属性的构造方法  将String类型转换成属性对应的类型
            field.set(obj, con.newInstance(value));
        }
        return obj;

    }

    //此方法负责用request接收请求发送过来的参数  将这些参数存入Object[]
    //交给invoke方法执行时作为参数
    //参数  request  Method(分析方法的参数  回头找请求参数是否有这个参数)  返回值Object[]
    private Object[] injectionParameters (Method method, HttpServletRequest request, HttpServletResponse response) throws Exception {
        //解析方法上的所有参数
        Parameter[] parameters = method.getParameters();
        //严谨判断
        if (parameters == null || parameters.length == 0){
            return null;
        }
        //方法有参数  我们解析参数后将参数放入Object[]中拿走
        Object[] resultValue = new Object[parameters.length];
        //遍历方法的每一个参数
        for (int i = 0 ; i < parameters.length ; i ++) {
            Parameter parameter = parameters[i];//得到某一个参数
            //分析参数类型  基础类型  domain类型  map类型
            Class parameterClazz = parameter.getType();
            //寻找当前参数前面是否带有@RequestParam注解
            RequestParam paramAnnotation = parameter.getAnnotation(RequestParam.class);
            //判断注解是否存在
            if (paramAnnotation != null) {//有注解  说明参数是一个基础类型
                resultValue[i] = this.injectionNormal(parameterClazz, paramAnnotation, request);
            } else {//没有注解  说明参数是一个domain或者一个map
                if (parameterClazz == Map.class || parameterClazz == List.class || parameterClazz == Set.class) {
                    //自定义异常  这些类型我处理不了
                } else {//某一种具体的Map集合  某一个domain
                    //如果不想用我们的参数自动注入  request  response对象也给你保留
                    if (parameterClazz == HttpServletRequest.class) {
                        resultValue[i] = request; continue;
                    }
                    if (parameterClazz == HttpServletResponse.class) {
                        resultValue[i] = response; continue;
                    }
                    Object obj = parameterClazz.newInstance();
                    if (obj instanceof Map) {
                        resultValue[i] = this.injectionMap(obj, request);
                    } else {//domain
                        resultValue[i] = this.injectDomain(obj, parameterClazz, request);
                    }
                }
            }
        }
        return resultValue;
    }

    //此方法用于支持finalResponse方法
    //负责解析ModelAndView中的map
    private void parseModelAndView (ModelAndView mv, HttpServletRequest request) {
        //获取mv中的map集合
        Map<String, Object> mvMap = mv.getAttributeMap();
        //遍历集合  存入request
        Iterator<String> it = mvMap.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            Object value = mvMap.get(key);
            //将这一组键值对存入request作用域中
            request.setAttribute(key, value);
        }
    }

    //此方法用于支持finalResponse方法
    //用于解析String  并根据String看是转发还是重定向
    private void parseString (String methodResult, HttpServletRequest request, HttpServletResponse response) throws Exception {
        //严谨性的判断
        if ("".equals(methodResult) || "null".equals(methodResult)) {
            //可以抛异常  提示响应路径有误
            return;
        }
        //正常的路径  进行解析
        String[] value = methodResult.split(":");
        if (value.length == 1) {//没有:  说明不是重定向  就是转发
            request.getRequestDispatcher(methodResult).forward(request, response);
        } else {//重定向
            if ("redirect".equals(value[0])) {
                response.sendRedirect(value[1]);
            }
        }
    }


    //此方法负责处理最终method执行后的返回值
    //ModelAndView
    //String
    //直接响应(利用注解@ResponseBody做标识)
    // 转发  重定向
    // JSON
    // void
    //参数  处理结果的返回值  方法本身(方法上可能会存在@ResponseBody注解)
    //request  response
    void finalResponse (Object methodResult, Method method, HttpServletRequest request, HttpServletResponse response) throws Exception {
        //1.判断方法是否有返回值(是否需要框架来帮忙处理返回值)
        if (methodResult != null) {//方法具有返回值  需要进行处理
            //先判断是否使用了我们提供的ModelAndView
            if (methodResult instanceof ModelAndView) {
                //解析ModelAndView  map集合  String路径
                ModelAndView mv = (ModelAndView)methodResult;
                this.parseModelAndView(mv, request);
                this.parseString(mv.getViewName(), request, response);
            } else if (methodResult instanceof String) {
                //先看一看方法上是否有@ResponseBody注解
                ResponseBody responseBody = method.getAnnotation(ResponseBody.class);
                if (responseBody != null) {//有注解  是直接响应的字符串
                    //是一个普通的字符串  需要使用流写回浏览器
                    response.setContentType("text/html;charset=UTF-8");
                    response.getWriter().write((String) methodResult);
                } else {//没有注解  说明是转发或者是重定向
                    String viewName = (String)methodResult;
                    this.parseString(viewName, request, response);
                }
            } else {//可能是对象  集合....  我们也需要@ResponseBody做一个标识  统一变成JSON形式返回
                //将返回值转化为JSON形式返回浏览器
                ResponseBody responseBody = method.getAnnotation(ResponseBody.class);
                if (responseBody != null) {
                    //转换为JSON
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("jsonObject", methodResult);
                    response.getWriter().write(jsonObject.toJSONString());
                }
            }
        } else {//方法没有返回值
            System.out.println("OK  无需我的帮忙  那你自己干吧");
        }
    }
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        try {
            //1.获取请求名
            String uri = request.getRequestURI();//包名 + 请求名
            //2.获取方法名
            String methodName = request.getParameter("method");
            //3.loadPropertiesFile方法放在init方法中执行  读取配置文件放入集合中
            //4.调用parseURI方法  得到请求名
            String className = this.parseURI(uri);
            //5.调用findObject方法  找到对应的Controller类的对象
            Object obj = this.findObject(className);
            //6.调用findMethod方法  找到方法
            Method method = this.findMethod(obj, methodName);
            //7.接收请求发送过来的参数  有可能值不止一个
            // 我们需要将这些值装入一个容器中 Object[]  方法执行invoke时一并传入
            Object[] parameterValues = this.injectionParameters(method, request, response);
            //7.执行方法
            Object result = method.invoke(obj, parameterValues);
            //8.处理响应
            this.finalResponse(result, method, request, response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
