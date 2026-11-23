package dev.transittracker;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Transit Tracker API", version = "v1",
		description = "Live vehicle positions and trip delays for GoRaleigh, GoDurham, GoTriangle, Chapel Hill Transit "
				+ "and GoCary, from their GTFS-realtime feeds.",
		license = @License(name = "MIT")))
public class TransitTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransitTrackerApplication.class, args);
	}

}
