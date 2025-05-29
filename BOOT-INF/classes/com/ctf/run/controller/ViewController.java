package com.ctf.run.controller;

import java.util.Arrays;
import java.util.List;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping({"/monitor/cache"})
public class ViewController {
   private static final List<String> SPEL_BLACKLIST = Arrays.asList(
      "T(java", "Runtime", "System", "exec", "processbuilder", "class.forname", "getclass", "new java", "file", "path", "scriptengine", "eval", "loadclass"
   );

   @GetMapping({"/getView"})
   public String view(String fragment) {
      return "cache::" + fragment;
   }

   @PostMapping({"/evalScript"})
   @ResponseBody
   public String eval(String script) {
      for(String forbidden : SPEL_BLACKLIST) {
         if (script.toLowerCase().contains(forbidden)) {
            return "Blocked by blacklist: " + forbidden;
         }
      }

      StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext(Thread.currentThread());
      SpelExpressionParser spelExpressionParser = new SpelExpressionParser();

      try {
         Expression spelExpression = spelExpressionParser.parseExpression(script);
         Object result = spelExpression.getValue(standardEvaluationContext);
         return result != null ? result.toString() : "error eval";
      } catch (SpelParseException var6) {
         return var6.getMessage();
      }
   }
}
