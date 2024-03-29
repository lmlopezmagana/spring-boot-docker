package com.salesianostriana.dam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class EjemploDocker03Application {

	public static void main(String[] args) {
		SpringApplication.run(EjemploDocker03Application.class, args);
	}

	@GetMapping("/")
	public String index() {
		return "Hello Docker from 2DAM";
	}

}
