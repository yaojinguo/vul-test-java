package com.ctf.run.controller;

import com.ctf.run.utils.ServletUtils;
import com.ctf.run.utils.StringUtils;
import java.io.IOException;
import java.util.Scanner;
import javax.servlet.http.HttpServletRequest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.BearerToken;
import org.apache.shiro.subject.Subject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/index"})
public class IndexController {
   @GetMapping({"/index"})
   public String index() {
      return "welcome demo";
   }

   @PostMapping({"/test/exec"})
   public String RunCmd(String cmd) {
      try {
         Scanner scanner = new Scanner(Runtime.getRuntime().exec(cmd).getInputStream()).useDelimiter("\\A");
         return scanner.hasNext() ? scanner.next() : "";
      } catch (IOException var4) {
         throw new RuntimeException(var4);
      }
   }

   @PostMapping({"/ping/exec"})
   public String TestCmd(String cmd) {
      try {
         Scanner scanner = new Scanner(Runtime.getRuntime().exec("ping " + cmd).getInputStream()).useDelimiter("\\A");
         return scanner.hasNext() ? scanner.next() : "";
      } catch (IOException var4) {
         throw new RuntimeException(var4);
      }
   }

   @GetMapping({"/csrf_token"})
   public String GetCsrf(HttpServletRequest request) {
      String csrf_token = ServletUtils.generateToken();
      request.getSession().setAttribute("csrf_token", csrf_token);
      return csrf_token;
   }

   @PostMapping({"/login"})
   public String login(@RequestHeader String AuthToken, @RequestHeader String Host) {
      BearerToken bearerToken = new BearerToken(AuthToken, Host);
      Subject subject = SecurityUtils.getSubject();

      try {
         subject.login(bearerToken);
         return "login success";
      } catch (AuthenticationException var7) {
         String msg = "用户或密码错误";
         if (StringUtils.isNotEmpty(var7.getMessage())) {
            msg = var7.getMessage();
         }

         return msg;
      }
   }
}
