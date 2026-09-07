package com.example.bff;
import com.example.bff.config.BffProperties;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@SpringBootApplication @EnableConfigurationProperties(BffProperties.class)
public class BffApplication { public static void main(String[] args) { SpringApplication.run(BffApplication.class,args); } }

