package com.github.sbadolankaur.rpg.platform.web;

import java.util.Set;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;

@Configuration(proxyBeanMethods = false)
class DemoTemplatesConfiguration {
  @Bean
  SpringResourceTemplateResolver demoTemplateResolver(ApplicationContext context) {
    var resolver = new SpringResourceTemplateResolver();
    resolver.setApplicationContext(context);
    resolver.setPrefix("classpath:/templates-demo/");
    resolver.setSuffix(".html");
    resolver.setCharacterEncoding("UTF-8");
    resolver.setCheckExistence(true);
    resolver.setOrder(2);
    resolver.setResolvablePatterns(Set.of("demo-*"));
    return resolver;
  }
}
