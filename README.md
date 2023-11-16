# Construcción de imágenes con una una aplicación Spring Boot

En este ejemplo, que tendrá varias versiones, vamos a construir la imagen para una aplicación de Spring Boot. 

## Versión 1: A partir del fichero JAR ya construido

Esta primera versión vamos a construir el JAR de forma externa a Docker, y posteriormente vamos a pasarlo a la imagen. Para construir el JAR podemos invocar el siguiente comando dentro de la carpeta en la que se encuentre el fichero `pom.xml`:

```bash
$ mvn clean install
```

Esto generará la siguiente ruta:

```bash
$ ls -lart target

total 37152
drwxr-xr-x@ 11 lmlopez  staff       352 Nov 16 13:04 ..
drwxr-xr-x@  3 lmlopez  staff        96 Nov 16 13:04 generated-sources
drwxr-xr-x@  3 lmlopez  staff        96 Nov 16 13:04 maven-status
drwxr-xr-x@  4 lmlopez  staff       128 Nov 16 13:04 classes
drwxr-xr-x@  3 lmlopez  staff        96 Nov 16 13:04 generated-test-sources
drwxr-xr-x@  3 lmlopez  staff        96 Nov 16 13:04 test-classes
drwxr-xr-x@  4 lmlopez  staff       128 Nov 16 13:04 surefire-reports
drwxr-xr-x@  3 lmlopez  staff        96 Nov 16 13:04 maven-archiver
-rw-r--r--@  1 lmlopez  staff      3141 Nov 16 13:04 EjemploDocker01-0.0.1-SNAPSHOT.jar.original
drwxr-xr-x@ 11 lmlopez  staff       352 Nov 16 13:04 .
-rw-r--r--@  1 lmlopez  staff  19014241 Nov 16 13:04 EjemploDocker01-0.0.1-SNAPSHOT.jar
```

Es decir, que hemos generado el fichero `.jar` de la aplicación. Podemos comprobar que funciona si ejecutamos el comando 

```bash
$ java -jar EjemploDocker01-0.0.1-SNAPSHOT.jar
```

Ahora veamos el Dockerfile

```Dockerfile
FROM amazoncorretto:17-alpine
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

Vemos que en la construcción vamos a poder proporcionar como argumento la ruta del fichero JAR; si no se hace, por defecto se copian todos los ficheros jar de la carpeta target.

A partir de ahí, tan solo tenemos que ejecutar el JAR como lo haríamos sin Docker.


```bash
$ docker build -t lmlopez/springboot-ej01:1.0 .
```

Comprobamos que la imagen se ha creado:

```bash
$ docker images
REPOSITORY                 TAG                 IMAGE ID            CREATED             SIZE
lmlopez/springboot-ej01    1.0                 ed609bce6be3        1 minute ago        414MB
```

Y podemos crear un contenedor:

```bash
$ docker run -d -p 8083:8080 --name springbootej01 lmlopez/springboot-ej01:1.0
```

> Prueba con otras imágenes de base que incluyan Java 17. Hasta hace no mucho estaba disponible la imagen de openjdk, pero ya está deprecada. Puedes empezar probando con eclipse temurin, y comprobar que imagen ocupa menos.


## Versión 2: A partir del fichero JAR ya construido pero deconstruido después

Un _fat jar_ de Spring Boot tiene algunas capas debido a la forma en que se _empaqueta_ este fichero. Si lo descomprimimos, podemos dividirlo entre dependencias internas y externas. A partir de esta estructura de ficheros, podemos copiar en capas individuales en nuestra imagen. De todas ellas, es posible que la única que cambie es la del código, ya que las dependencias en un proyecto suelen ser estables. Así, la creación de imágenes para nuevas versiones, y la carga de contenedores será más rápida.

Primero, una vez obtenido el JAR, ejecutamos los siguientes comandos:

```bash
$ mkdir target/dependency
$ (cd target/dependency; jar -xf ../*.jar)
```
Ahora veamos el Dockerfile

```Dockerfile
FROM amazoncorretto:17-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
ARG DEPENDENCY=target/dependency
COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app
ENTRYPOINT ["java","-cp","app:app/lib/*","com.salesianostriana.dam.EjemploDocker02Application"]
```

- En primer lugar, creamos un usuario y un grupo, para que la aplicación no se ejecute como root. Así será menos vulnerable.
- Establecemos como usuario/grupo los recién creados.
- Añadimos un argumento que se puede pasar en la construcción con el valor por defecto como la ruta que hemos creado arriba.
- Copiamos las 3 carpetas que se han generado al descomprimir el _fat jar_. Cada una de ellas irá a una capa diferente de la imagen. La última contiene nuestro código fuente compilado.
- Ejecutamos la aplicación; en lugar de como un jar, le indicamos el _classpath_ con la ruta de todas las librerías y como punto de entrada la clase anotada con `@SpringBootApplication`.

Ahora podemos construir la imagen.

```bash
$ docker build -t lmlopez/springboot-ej02:1.0 .
```

Comprobamos que la imagen se ha creado:

```bash
$ docker images
REPOSITORY                 TAG                 IMAGE ID            CREATED             SIZE
lmlopez/springboot-ej02    1.0                 a204be2f5a2e        1 minute ago        306MB
```

Y podemos crear un contenedor:

```bash
$ docker run -d -p 8080:8080 --name springbootej02 lmlopez/springboot-ej02:1.0
```

## Versión 3: A través de una imagen Multi-Stage

Hasta ahora hemos tenido que crear el jar desde fuera de Docker. Esto también lo podemos hacer dentro de docker usando un Dockerfile multistage, y copiando el resultado de un _stage_ en otro.

El fichero Dockerfile debería ser similar a este:

```Dockerfile
FROM amazoncorretto:17-alpine as build
WORKDIR /workspace/app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN ./mvnw install -DskipTests
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM amazoncorretto:17-alpine
VOLUME /tmp
ARG DEPENDENCY=/workspace/app/target/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app
ENTRYPOINT ["java","-cp","app:app/lib/*","com.salesianostriana.dam.EjemploDocker03Application"]
```

Como podemos ver, combinamos el Dockerfile de la version 2 con una _stage_ anterior donde se descargan las dependencias y se compila el código.

Ahora, la copia de las capas de la aplicación ya no se hace directamente desde nuestro host, sino que se realiza desde el _stage_ anterior donde se ha compilado.

> Como es lógico, la compilación es más lenta, ya que no se podrá aprovechar la caché de la _stage_ de compilación. Sin embargo, la compilación se hará independiente del host donde se ejecute. Esto nos permite también agregar algunos elementos propios de nuestro equipo de desarrollo a la compilación, ya que podemos crear una imagen base que tenga elementos de seguridad, paquetes propios, etc...

Ahora podemos construir la imagen.

```bash
$ docker build -t lmlopez/springboot-ej03:1.0 .
```

Comprobamos que la imagen se ha creado:

```bash
$ docker images
REPOSITORY                 TAG                 IMAGE ID            CREATED             SIZE
lmlopez/springboot-ej03    1.0                 d12a378f6e73        1 minute ago        306MB
```

Y podemos crear un contenedor:

```bash
$ docker run -d -p 8080:8080 --name springbootej03 lmlopez/springboot-ej03:1.0
```
