# Sistema de Productos con Precios Históricos

API para gestionar productos y su histórico de precios: alta de productos, alta de
precios con validación de solapamiento de fechas, consulta del precio vigente en una
fecha dada y consulta del historial completo.

---

## 1. Resumen y contexto

Implementación de la prueba técnica descrita originalmente en este mismo repositorio
(ver historial de git para el enunciado tal cual se recibió). El enunciado marca el
**rendimiento** (arranque rápido, baja latencia, bajo uso de CPU/memoria) como uno de
los requisitos más importantes, con los contenedores limitados a 1 CPU / 1GB, así que
todas las decisiones de stack y arquitectura están tomadas con ese criterio como
prioridad, sin sacrificar corrección ni legibilidad.

El proceso de diseño e implementación completo (decisiones, pruebas realizadas, errores
encontrados y cómo se resolvieron) está documentado en [`HISTORIAL.md`](HISTORIAL.md).
El plan original está en [`PLAN.md`](PLAN.md).

---

## 2. Stack técnico y justificación

**Java 21 + [Javalin](https://javalin.io) 6.7.0 (Jetty embebido) + Jackson**, sin Spring
Boot y sin base de datos externa.

| Opción | Arranque | Memoria base | Por qué se descarta / elige |
|---|---|---|---|
| Spring Boot | ~1.5-3 s | Alta (classpath scanning, contenedor DI completo) | Justo lo contrario de lo que pide el enunciado como criterio prioritario |
| `com.sun.net.httpserver` a pelo | Muy rápido | Mínima | Obliga a reimplementar routing, parsing y manejo de errores a mano, con más riesgo de bugs sin ganancia real de rendimiento frente a Javalin |
| **Javalin** (elegido) | Cientos de ms | Baja (fino sobre Jetty, sin contenedor de DI ni classpath scanning) | Mejor equilibrio rendimiento / legibilidad / testabilidad |

**Sin base de datos**: no hay requisito de persistencia entre reinicios, así que una
base de datos solo añadiría latencia de I/O y tiempo de arranque sin aportar valor.
Se usan estructuras concurrentes en memoria (detalle en la sección 5).

**Hilos virtuales** (`config.useVirtualThreads = true`): con el límite de 1 CPU del
contenedor, los hilos virtuales **no paralelizan más cómputo** (el trabajo real sigue
serializado en un único core). Su beneficio aquí es de **memoria y escalabilidad de
conexiones entrantes**: evitan que un pool de hilos de plataforma (pila ~1MB cada uno)
consuma cientos de MB del límite de 1GB, y evitan colas/rechazos cuando entran los
miles de peticiones concurrentes del benchmark.

**`BigDecimal` para `value`** (no `double`): es dinero; la coma flotante binaria
introduce errores de redondeo que no son aceptables en un modelo de precios.

### Versiones de las dependencias

Todas verificadas contra Maven Central en el momento de escribir `build.gradle` (ver
`HISTORIAL.md` para el detalle de una primera pasada con números inventados que se
corrigió):

- `io.javalin:javalin:6.7.0`
- `com.fasterxml.jackson.core:jackson-databind:2.18.3` /
  `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3`
- `org.slf4j:slf4j-simple:2.0.17`
- Plugin `com.gradleup.shadow:8.3.6` (rama 8.3.x; la 9.x exige Gradle ≥ 9.2 y rompería
  la build con la imagen `gradle:8.5.0-jdk21` que fija el `Dockerfile`)
- Tests: `org.junit.jupiter:junit-jupiter:5.12.2`, `org.assertj:assertj-core:3.27.3`,
  `org.mockito:mockito-core`/`mockito-junit-jupiter:5.18.0`

---

## 3. Arquitectura

Arquitectura hexagonal simplificada en 3 paquetes bajo `com.mango.products`:

```
domain/            entidades e invariantes de negocio, sin dependencias externas
├── model/         DateRange, Price, Product
├── exception/      DomainException y subtipos (mapeados a códigos HTTP en la capa web)
├── repository/     ProductRepository (puerto)
└── idgen/          IdGenerator (puerto, Strategy)

application/
└── ProductService   casos de uso: createProduct, getProduct, addPrice, getPriceAt

infrastructure/
├── persistence/     InMemoryProductRepository, AtomicLongIdGenerator (adaptadores)
└── web/             JavalinApp, controllers, DTOs, mappers, GlobalExceptionHandler
```

El `domain` no conoce Javalin ni Jackson; `application` solo conoce el dominio y los
puertos; `infrastructure` es lo único que conoce las librerías concretas. Esto permite,
por ejemplo, sustituir el almacenamiento en memoria por una base de datos real
implementando `ProductRepository` sin tocar una sola línea de `domain` o `application`.

---

## 4. Modelo de dominio e invariantes

- **`DateRange`** (`initDate`, `endDate` nullable): rango **cerrado en ambos
  extremos** (`endDate` inclusivo); `endDate == null` significa "vigente
  indefinidamente". Un día de frontera compartido entre dos rangos (uno termina el día
  exacto en que otro empieza) cuenta como solapamiento, porque ese día pertenecería a
  ambos precios a la vez.
- **`Price`** (`value: BigDecimal`, `range: DateRange`): `value` debe ser estrictamente
  positivo.
- **`Product`** (aggregate root): mantiene sus precios en un
  `ConcurrentSkipListMap<LocalDate, Price>` ordenado por `initDate`, lo que da
  búsquedas/inserciones en `O(log n)` y hace que el historial ya salga ordenado
  cronológicamente sin necesidad de un `sort` adicional.

Todas las validaciones (fechas nulas, `initDate >= endDate`, `value <= 0`, nombre o
descripción en blanco) se hacen en el propio dominio mediante *static factory methods*
(`DateRange.of(...)`, `Price.create(...)`, `Product.create(...)`), no en la capa web, así
que son imposibles de saltarse aunque se añada un nuevo punto de entrada en el futuro.

---

## 5. Concurrencia y rendimiento

Esta es la sección que más pondera el enunciado, así que se detalla en profundidad.

### Algoritmo de solapamiento — `O(log n)`

Al añadir un precio (`Product.addPrice`), en vez de recorrer linealmente los precios
existentes:

1. `floorEntry(newInitDate)`: el único precio existente que podría solapar **por la
   izquierda** es el que tiene el mayor `initDate` menor o igual al del nuevo rango.
2. `subMap`/`tailMap` sobre `[newInitDate, newEndDate]` (ambos inclusive, o desde
   `newInitDate` en adelante si `newEndDate` es `null`): cualquier precio existente que
   empiece dentro de esa ventana solapa **por la derecha**, porque comparte como mínimo
   su día de inicio con el nuevo rango.

Con el invariante "los precios existentes nunca se solapan entre sí" (mantenido por
inducción, ya que solo se inserta tras pasar estas dos comprobaciones), estos dos pasos
son suficientes para detectar cualquier solapamiento posible. Ambos son operaciones de
`ConcurrentSkipListMap` en `O(log n)`, sin recorrer nada.

La búsqueda del precio vigente en una fecha (`findPriceAt`) es la misma idea: un único
`floorEntry(date)` seguido de comprobar si ese precio contiene la fecha — el clásico
*stabbing query* sobre intervalos ordenados y no solapados, también `O(log n)`.

### Estrategia de concurrencia

- **Escrituras** (`addPrice`) están serializadas **por producto** mediante un
  `ReentrantLock` privado dentro de `Product` (nunca `synchronized` sobre `this`, para
  que nada externo pueda interferir con el monitor). Es necesario porque añadir un
  precio es una operación *check-then-act* (comprobar que no solapa, luego insertar)
  que debe ser atómica.
- **Lecturas** (`findPriceAt`, `history`) son **completamente lock-free**:
  `ConcurrentSkipListMap` ya es thread-safe por sí mismo y sus vistas son "weakly
  consistent" (nunca lanzan `ConcurrentModificationException`), lo cual es suficiente
  aquí porque no hay requisito de que un cliente vea instantáneamente la escritura de
  otro cliente distinto.

Esto encaja con el patrón de tráfico del propio `benchmark.sh`: 1000 escrituras
concurrentes de productos frente a 20000+15000 lecturas concurrentes de precios — las
lecturas, que son la inmensa mayoría, nunca esperan a un lock.

Alternativas consideradas y descartadas:
- `ConcurrentHashMap.compute()` a nivel de producto: su lock interno es por *bin* del
  hash (no por clave exacta), así que una colisión podría serializar productos
  distintos sin necesidad; además prohíbe llamadas anidadas al mismo mapa dentro de la
  función de `compute`.
- `StampedLock`: su ventaja (lectura optimista de varios campos primitivos) no aporta
  nada aquí, ya que las lecturas ya son lock-free gracias al propio
  `ConcurrentSkipListMap`.

### Hilos virtuales, flags JVM y `ulimits`

- `config.useVirtualThreads = true` en Javalin: ver justificación en la sección 2.
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` (en `docker-compose.yml`):
  el valor por defecto de `MaxRAMPercentage` bajo detección de contenedor es 25%, lo
  que dejaría un heap innecesariamente pequeño de un límite de 1GB para una app sin más
  consumidor de memoria relevante que Jetty/Jackson. `UseSerialGC` evita que un
  recolector concurrente compita por el único core disponible con el propio servicio
  (con 1 CPU es probable que la propia ergonomía de la JVM ya elija Serial GC por
  defecto, pero se fija explícitamente para dejar la intención documentada).
- `ulimits.nofile: 65536` en `app` y `benchmark`: con decenas de miles de conexiones
  concurrentes, el límite por defecto de descriptores de fichero del contenedor puede
  agotarse antes de que el propio servidor sea el cuello de botella. Esto es un
  `ulimit`, no CPU ni memoria, así que no infringe la restricción del enunciado.

---

## 6. Patrones de diseño aplicados

- **Repository** (`ProductRepository` + `InMemoryProductRepository`): separa el
  dominio del mecanismo de almacenamiento.
- **Strategy** (`IdGenerator` + `AtomicLongIdGenerator`): permite cambiar de ID
  secuencial a, por ejemplo, UUID, sin tocar el resto del sistema.
- **DTO + Mapper** (`ProductMapper`, `PriceMapper`): separa el modelo de dominio del
  contrato JSON expuesto; un cambio en el formato de la API no obliga a tocar el
  dominio y viceversa.
- **Manejo centralizado de excepciones** (`GlobalExceptionHandler` vía
  `app.exception(...)` de Javalin): traduce excepciones de dominio a respuestas HTTP de
  forma uniforme, en un único punto.
- **Test Data Builder** (`PriceTestDataBuilder`): mantiene legibles los tests que varían
  fechas/valores sobre un mismo objeto base.
- **Static factory methods** (`DateRange.of`, `Price.create`, `Product.create`) en vez
  de Builder para las entidades del dominio: con 2-4 campos y validación en la propia
  construcción, un Builder habría sido indirección sin beneficio real. Nótese que esto
  es el idiom de *static factory* (Effective Java), no el patrón GoF "Factory Method"
  en sentido estricto (que implica subclases decidiendo qué instanciar) — se nombra así
  explícitamente para no reclamar un patrón que no es.

Decisiones conscientes de **no** aplicar más patrones de los necesarios:
- No se ha extraído un `Specification` para el solapamiento de fechas: la lógica ya
  vive testeable y aislada como método de `DateRange` (`overlaps`), envolverla en una
  interfaz `Specification<T>` habría sido indirección sin beneficio.
- `ProductService` es una clase concreta, sin interfaz: no hay más de una
  implementación real, y se testea con el repositorio mockeado, no con el propio
  servicio mockeado.

---

## 7. Contrato de la API

Los 4 endpoints obligatorios se han mantenido con el path, método y forma de body
exactos del enunciado (los usará la suite de pruebas automáticas). Se han añadido
algunas mejoras de semántica REST, todas hacia atrás compatibles:

| Método | Path | Body | Éxito | Errores |
|---|---|---|---|---|
| `POST` | `/products` | `{name, description}` | `201 Created` + producto (incluye `id`) | `400` si `name`/`description` en blanco |
| `POST` | `/products/{id}/prices` | `{value, initDate, endDate?}` | `201 Created` + precio creado | `404` producto inexistente · `409` solapamiento · `400` `initDate >= endDate` o `value <= 0` |
| `GET` | `/products/{id}/prices?date=YYYY-MM-DD` | — | `200` + `{"value": X}` | `404` producto inexistente o sin precio vigente esa fecha · `400` fecha con formato inválido |
| `GET` | `/products/{id}/prices` (sin `date`) | — | `200` + historial completo (`name`, `description`, `prices[]` ordenado cronológicamente) | `404` producto inexistente |
| `GET` | `/actuator/health` | — | `200` `{"status":"UP"}` | — |

**Desviaciones deliberadas respecto al enunciado** (todas hacia atrás compatibles, ya
que solo afectan al código de estado, nunca al path/método/forma del body):
- `201 Created` en vez de `200 OK` en las dos operaciones de creación (más correcto en
  términos REST).
- `404 Not Found` cuando no hay precio vigente en la fecha consultada, en vez de `200`
  con un cuerpo vacío o nulo — es más explícito y evita ambigüedad entre "no hay precio"
  y "el precio vale null".
- Errores devueltos siempre con la misma forma: `{"status": int, "error": string,
  "message": string}`.

No se ha implementado ningún endpoint de actualización/borrado de precios ni soporte
multi-moneda (ver sección 12, bonus descartados).

---

## 8. Cómo compilar y ejecutar

### En local

```bash
./gradlew build        # compila, corre los tests, genera build/libs/app.jar
java -jar build/libs/app.jar
```

La API queda escuchando en `http://localhost:8080`.

### Con Docker

```bash
docker compose up --build app
```

> **Nota**: las claves `deploy.resources.limits` de `docker-compose.yml` solo se
> aplican realmente con Compose V2 (`docker compose`, sin guion). Con el binario
> legacy `docker-compose` (V1) esas claves se ignoran silenciamente sin dar error.

Este entorno de desarrollo no tenía `docker` instalado, así que la validación de estos
ficheros se hizo con Podman (ver `HISTORIAL.md` para el detalle exacto de los comandos
usados como equivalente a `docker compose up`).

---

## 9. Cómo ejecutar el benchmark

```bash
docker compose up --build
```

Esto levanta `app` y, cuando su `/actuator/health` responde, el contenedor
`benchmark` ejecuta `benchmark.sh`: primero el flujo funcional completo (crear
producto, añadir 3 precios, consultar 3 fechas, historial), y después la sección de
carga (1000 altas de producto + 20000 consultas de precio vigente + 15000 consultas de
historial, todas concurrentes), imprimiendo la duración total de cada bloque.

Los resultados obtenidos en este entorno (vía Podman, con los mismos límites de
`--cpus=1.0 --memory=1g` que fija `docker-compose.yml`) están anotados en
`HISTORIAL.md`.

---

## 10. Tests

```bash
./gradlew test
```

71 tests entre unitarios y de integración, organizados así:

- **Dominio** (`DateRangeTest`, `PriceTest`, `ProductTest`): invariantes de negocio,
  casos borde de solapamiento/contención de fechas, y un test de concurrencia con 50
  hilos virtuales intentando insertar precios solapados a la vez sobre el mismo
  producto (exactamente 1 debe tener éxito).
- **Persistencia** (`InMemoryProductRepositoryTest`): guardar/recuperar y una alta
  concurrente de 1000 productos sin pérdidas.
- **Aplicación** (`ProductServiceTest`, con Mockito): delegación en los puertos y
  propagación sin envolver de las excepciones de dominio.
- **Web** (`ProductApiIntegrationTest`): tests de caja negra por HTTP contra un
  servidor Javalin real en un puerto aleatorio, cubriendo el contrato completo de la
  API y sus casos de error. `ProductMapperTest`/`PriceMapperTest`: mapeo a nivel
  unitario, incluido el formato de fecha ISO y el `endDate` null explícito.

---

## 11. Supuestos explícitos

- `value` debe ser estrictamente positivo (`> 0`).
- `name` y `description` no pueden estar en blanco.
- Rango de fechas cerrado-cerrado; un día de frontera compartido entre dos precios
  cuenta como solapamiento (ver sección 4).
- El formato de fecha en el query param `?date=` y en los bodies es siempre
  `yyyy-MM-dd` (ISO-8601), igual que en los ejemplos del enunciado.
- No hay autenticación ni persistencia entre reinicios del proceso (ver sección 12).

---

## 12. Bonus

**Incluidos**:
- Se ha dejado funcionando el desafío de prueba de rendimiento ya esbozado en el
  repo (`docker-compose.yml` + `benchmark.sh`), corrigiendo los bugs que traía (ver
  `HISTORIAL.md`, fase 8).

**Evaluados y descartados conscientemente** (documentado aquí, en vez de implementados
a medias):
- **Multi-moneda**: añadir un campo `currency` cambiaría la forma exacta `{"value": X}`
  que el enunciado fija para la respuesta de precio vigente, y que "se utilizará en
  pruebas automáticas" — riesgo innecesario para un bonus de bajo valor relativo aquí.
- **Update/delete de precios**: no aportaba valor adicional relevante para el foco
  principal (rendimiento y corrección del modelo existente) dentro del tiempo
  disponible, y complica el invariante de no-solapamiento (habría que revalidar contra
  el resto de precios al editar).
- **Autenticación**: rompería las llamadas sin token que hace el propio
  `benchmark.sh` si se hiciera obligatoria.
- **Swagger/OpenAPI en runtime**: añadir `swagger-ui` como dependencia friccionaría
  directamente con el objetivo de arranque rápido/bajo consumo de memoria que es el
  criterio prioritario del enunciado; el contrato de la API ya queda documentado en la
  sección 7 de este README.
- **Paginación/filtrado del historial**: el enunciado no lo pide y el historial de un
  producto no tiene un volumen esperado que lo justifique.
