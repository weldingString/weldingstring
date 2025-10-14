 ## Weld string

This project are been used as a starting point to create our own Vaadin application with Spring Boot.
It contains all the necessary configuration and some placeholder files to get you started.

## Application based on this Vaadin start point: Flow CRM tutorial
The project is a multiuser project and start with /start/userid and stop by /logout/userid. 
All images and weld string are generated under that actual userid. If you start it without this commando
you will get standard userID for al users. It is meaning that this userid is the same userid as used in Weldit system
and is given automatic when the system are integrated.

There are many Java classes in this collection which are not yet an integrated part of the application. 

Project are based on Java 17.0.2, SpringBoot 3.1,2 , Vaadin Flow 24.6.6 , Maven 4.0.0. As IDE is IntelliJ IDEA 2024.3.4.

The system are based on manipulation of svg-files and will as end product generate a .svg file send to an API in the Weldit system.
System sending an API to https://weldit.weldit.no/api/images/userID  This URL is given in method sendSvgToApi()
in the Java-class MainViewSave.java  

The API have to:
- must be able to receive POST requests
- must support application/x-www-form-urlencoded
- understand name og svg or be customizable
- response must return 2xx status code (200 OK or 201 Created)
- URL must be accessible without errors

In sending we are using:
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

String body = "name" + fileName + "&svg" + URLEncoder.encode(svgContent,StandardCharsets.UFT_8);

There has to be correct parameter name:
name: the name for the file
svg: the svg contents itself

## Running the application

The project is a standard Maven project. To run it from the command line,
type `mvnw` (Windows), or `./mvnw` (Mac & Linux), then open
http://localhost:8080 in your browser.

You can also import the project to your IDE of choice as you would with any
Maven project. Read more on [how to import Vaadin projects to different IDEs](https://vaadin.com/docs/latest/guide/step-by-step/importing) (Eclipse, IntelliJ IDEA, NetBeans, and VS Code).

## Deploying to Production

To create a production build, call `mvnw clean package -Pproduction` (Windows),
or `./mvnw clean package -Pproduction` (Mac & Linux).
This will build a JAR file with all the dependencies and front-end resources,
ready to be deployed. The file can be found in the `target` folder after the build completes.

Once the JAR file is built, you can run it using
`java -jar target/flowcrmtutorial-1.0-SNAPSHOT.jar`

## Project structure

- `MainLayout.java` in `src/main/java` contains the navigation setup (i.e., the
  side/top bar and the main menu). This setup uses
  [App Layout](https://vaadin.com/docs/components/app-layout).
- `views` package in `src/main/java` contains the server-side Java views of your application.
- `views` folder in `frontend/` contains the client-side JavaScript views of your application.
- `themes` folder in `frontend/` contains the custom CSS styles.


## Deploying using Docker

To build the Dockerized version of the project, run

```
mvn clean package -Pproduction
docker build . -t flowcrmtutorial:latest
```

Once the Docker image is correctly built, you can test it locally using

```
docker run -p 8080:8080 flowcrmtutorial:latest
```
