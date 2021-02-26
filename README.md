# Web-framework
为什么要封装web框架?
Controller层比较麻烦   有很多样板代码  耦合度太高
1.每一个类都需遵守规则

继承HttpServlet
重写方法service  doGet  doPost
参数列表HttpServletRequest  HttpServletResponse
抛出异常ServletException  IOException
需要配置web.xml文件  或者  使用注解

2.更重要的是

一个请求(一个功能点)
对应一个Servlet类  而类中只有一个方法
这是对类的浪费

所以封装web框架的目的是简化我们的操作  避免产生过多的类
并且给Controller类解耦  使其变成一个普通的类  却能做Controller类做的事情

封装的web框架是模仿springMVC框架来封装的
用到了集合  IO  反射  注解  自定义异常  JSON等知识以及JavaWeb相关的知识

此框架规定后缀为.do的请求为Servlet请求  
web.xml中也拦截所有的.do请求
并且规定请求url为  请求对应的Controller类名.do?method=请求对应的方法名&携带的参数
例如AtmController.do?method=login&name=zzt&pass=123

用户只需写一个配置文件ApplicationContext.properties
其中配置请求--Controller类全名的映射关系
即可以使用

做到了对Controller类进行解耦
Controller类无需继承  无需重写方法  无需request参数  response参数
Controller类变成一个真正的普通类
但注意  Controller类中的方法名必须与请求url中的method=方法名一致
也无需为每一个Servlet请求都在web.xml文件中配置8行进行拦截

提供了两个注解
@RequestParam  此注解用于接收请求的参数
@ResponseBody  此注解用于标识响应信息是直接响应回去的String  
有这个注解  说明这是直接响应回去的String

还提供了ModelAndView对象用于存储转发路径  重定向路径及响应信息中携带的参数

并将方法执行后的返回值(非直接响应回去的String  例如对象  集合等)以JSON形式响应回浏览器


框架如何去使用

1.mymvc包中的所有类加载入工程(jar)

2.web.xml文件中配置一个核心入口  拦截所有.do请求  类名必须为mymvc.DispatcherServlet

```xml
<servlet>
    <servlet-name>mvc</servlet-name>
    <servlet-class>mymvc.DispatcherServlet</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>mvc</servlet-name>
    <url-pattern>*.do</url-pattern>
</servlet-mapping>
```

3.src根目录中写一个配置文件ApplicationContext.properties
(如果是maven项目  则放在resources目录下)

其中配置请求--Controller类全名的映射关系

```properties
AtmController=controller.AtmController
ShoppingController=controller.ShoppingController
```

4.可以进行使用  

规定后缀为.do的请求为Servlet请求  

请求url规定为  请求对应的Controller类名.do?method=请求对应的方法名&携带的参数

例如AtmController.do?method=login&name=zzt&pass=123

自己创建一个类作为Controller

无需继承HttpServlet

无需重写方法  

方法参数随意(基础类型  需要添加@RequestParam注解  domain类型  map类型  也保留了request response参数  可以正常使用)

方法中正常去实现

方法返回值想要携带信息  需要使用ModelAndView类中的两个方法  addAttribute  setViewName

方法返回值  允许ModelAndView类型  String(转发或重定向路径)  及  直接响应的String信息(需要在方法上添加@ResponseBody注解)  还支持domain/map/list/数组等



示例

index.jsp

```jsp
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
  <head>
    <title>Document</title>
  </head>
  <body>
  <!--  发送请求  需要携带三个信息
  请求类型  servlet请求  规定后缀为.do
  请求类名
  请求方法名
  -->

  <a href="AtmController.do?method=login&name=zzt&pass=123">测试1(模拟一个ATM的登录)</a><br>
  <a href="AtmController.do?method=query&name=zzt&pass=123">测试2(模拟一个ATM的查询余额)</a><br>
  </body>
</html>
```



```java
package controller;

import domain.User;
import mymvc.ModelAndView;
import mymvc.RequestParam;
import mymvc.ResponseBody;
import service.AtmService;

public class AtmController {

    AtmService service = new AtmService();

    public ModelAndView login (User user) {
        String result = service.login(user.getName(), user.getPass());
        System.out.println(result);
        System.out.println("请求到达登录控制层" + user);
        ModelAndView mv = new ModelAndView();
        if (result.equals("登录成功")) {
            //return "welcome.jsp";
            //转发到welcome.jsp页面
            mv.setViewName("welcome.jsp");
        } else {
            //携带参数
            mv.addAttribute("result", result);
            //重定向到index.jsp
            mv.setViewName("redirect:index.jsp");
        }
        //request.setAttribute("result", result);
        return mv;
    }

    @ResponseBody
    public String query (@RequestParam("name") String name, @RequestParam("pass") String pass) {
        System.out.println("这是AtmController中的query方法" + name + "--" + pass);
        return "ATM查询方法";
    }
}
```
