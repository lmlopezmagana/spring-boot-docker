# Ejemplos de Dockerización de una aplicación de Spring Boot

> Muchos de estos ejemplos están basados en la guía de [Spring Boot with Docker](https://spring.io/guides/gs/spring-boot-docker/)

> En este tutorial se da por supuesto que estamos trabajando con Maven, y no con Gradle

## Ejemplo 1

Dockerización simple, a partir del fichero .jar

`Dockerfile`
```Dockerfile
FROM openjdk:8-jdk-alpine
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

El comando para construir la imagen podría ser:

```bash
docker build -t myname/spring-boot-docker:1.0 .
```

> Nótese el punto del final

El comando para crear un contenedor:

```bash
docker run --name spring-boot-app -p 8080:8080 -d myname/spring-boot-docker:1.0
```

## Ejemplo 2

Dockerización en un solo contenedor, desglosando el fichero .jar en diferentes capas.

La justificación de no trabajar con un archivo _fat jar_ es que, entre diferentes versiones de nuestra aplicación, esta no suele variar en las dependencias (es decir, no se suelen agregar nuevas dependencias; solo muy ocasionalmente). Solamente suele hacerlo en referencia al código fuente u, ocasionalmente, a los recursos asociados. Si dividimos nuestra imagen de docker en 3 capas, podemos reaprovechar las capas que no se hayan modificado, acelerando así la creación de imágenes y el lanzamiento de contenedores. 

> Se puede encontrar información más detallada al respecto en **[Don't Put Fat Jars in Docker Images](https://phauer.com/2019/no-fat-jar-in-docker-image/)**


El primer paso que tenemos que hacer es extraer de nuestro fichero jar todo lo necesario, ejecutando el siguiente comando:

```bash
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
```

Esto genera, entre otros, los siguientes directorios:

| Directorio | Contenido |
|------------|-----------|
| `BOOT-INF/lib` | Librerías que utiliza el proyecto |
| `META-INF` | Fichero de manifiesto, entre otros |
| `BOOT-INF/classes` | Código de nuestra aplicación |

Se modifica el fichero Dockerfile para copiar el contenido de estos 3 directorios, así como para lanzar la aplicación desde la clase principal

`Dockerfile`
```Dockerfile
FROM openjdk:8-jdk-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
ARG DEPENDENCY=target/dependency
COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app
ENTRYPOINT ["java","-cp","app:app/lib/*","com.salesianostriana.dam.EjemploDocker02Application"]
```

El comando para construir la imagen podría ser:

```bash
docker build -t myname/spring-boot-docker:2.0 .
```

> Nótese el punto del final

El comando para crear un contenedor:

```bash
docker run --name spring-boot-app-v2 -p 8081:8080 -d myname/spring-boot-docker:2.0
```

> Nótese que se ha cambiado el puerto local a 8081, por si no hemos detenido o eliminado el contenedor del ejemplo anterior

## Ejemplo 3

