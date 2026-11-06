package com.shareride.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.shareride")
public class AuthServiceApplication {

   public static void main(String[] args) {
      SpringApplication.run(AuthServiceApplication.class, args);
   }

}