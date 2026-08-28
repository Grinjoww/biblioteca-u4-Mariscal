# Cuestionario — Parte A del examen de la Unidad IV

> **Cómo se llena este archivo.** Responda **dentro de este mismo archivo**, debajo de cada pregunta, en el bloque marcado como `**Respuesta:**`. No borre ni reescriba los enunciados: el evaluador compara pregunta por pregunta. No añada ni quite secciones.
>
> **Este archivo se versiona en el repositorio.** Debe existir en la raíz, llamarse exactamente `Cuestionario.md`, y sus respuestas deben llegar por *commits* sucesivos hechos cuando el docente lo indique. Un archivo que aparece completo en un único *commit* al final de la sesión no cumple el protocolo y se trata según el criterio de piso 4 del examen.
>
> Se valora la precisión técnica y la justificación, **no la extensión**. Una respuesta correcta de seis líneas vale más que una página imprecisa. Cuando la pregunta pida referirse al proyecto base, hágalo con nombres concretos de clases o de *endpoints*.

---

## Datos del estudiante

| Campo | Valor |
|---|---|
| Apellidos y nombres | Mariscal Cabrera Jaime Josué|
| Número de carnet | 1250710835 |
| Correo institucional | jmariscalc@uteq.edu.ec |
| Fecha | 28/8/2026 |
| URL del repositorio | https://github.com/Grinjoww/biblioteca-u4-Mariscal |

---

## A1. Restricciones de REST aplicadas a un caso concreto — 8 puntos

**a) Enuncie las seis restricciones del estilo arquitectónico REST según Fielding. (3 puntos)**

**Respuesta:**
1. *Cliente-servidor*: es tipo la separación de responsabilidades; el cliente (app web/móvil de la clínica) maneja la interfaz, y el servidor (biopet en mi caso de PFC) el almacenamiento y la lógica de negocio veterinario, evolucionando cada lado por separado.
2. *Sin estado (stateless)*: cada petición del cliente debe contener toda la información necesaria para procesarla, sin que el servidor dependa de contexto guardado de peticiones hechas anteriormente.
3. *Cacheable*: las respuestas deben indicar si pueden almacenarse en caché, para reutilizarlas y reducir carga/latencia.
4. *Interfaz uniforme*: contrato consistente de acceso a los recursos (mascotas, propietarios, citas, historiales), con identificación de recursos, manipulación por representaciones y mensajes autodescriptivos.
5. *Sistema en capas*: el cliente no distingue si habla directamente con el servidor de biopet o con un intermediario (gateway, balanceador, proxy de caché).
6. *Código bajo demanda (opcional)*: el servidor puede enviar código ejecutable al cliente para extender su funcionalidad


**b) El proyecto base expone `GET /api/v1/autores` y guarda el estado de la sesión del usuario solo en el JWT que el cliente envía en cada petición. Explique qué restricción concreta se está cumpliendo con esa decisión y qué consecuencia práctica tiene para escalar el sistema a varios servidores detrás de un balanceador. (3 puntos)**

**Respuesta:**
Al exponer `GET /api/v1/mascotas` y mantener toda la información de sesión únicamente dentro del JWT que viaja en cada solicitud, biopet está respetando la restricción de **ausencia de estado**: el backend no retiene ningún dato de sesión de un usuario entre una llamada y otra.

Esto tiene una consecuencia directa sobre la escalabilidad: como ninguna instancia del servidor necesita "recordar" quién es cada usuario, **el sistema puede replicarse en múltiples nodos detrás de un balanceador sin coordinación adicional**, ya que cualquiera de ellos puede atender cualquier solicitud entrante. Se elimina así la necesidad de sesiones pegajosas (*sticky sessions*) o de un almacén de sesiones compartido, lo que facilita agregar o retirar instancias según la demanda.


**c) De las seis restricciones, indique cuál es opcional y dé un ejemplo real de una API que la use. (2 puntos)**

**Respuesta:**
Es el **código bajo demanda**. Un ejemplo concreto es el de servicios web que envían scripts ejecutables al navegador del cliente para extender su comportamiento sin publicar una nueva versión de la aplicación —tal como hace **Google Maps**, que transmite código JavaScript al cliente para manejar la interacción con el mapa.


---

## A2. Anatomía y ciclo de vida de un JWT — 8 puntos

**a) Un JWT tiene tres partes separadas por puntos. Nómbrelas en orden e indique qué contiene cada una. (3 puntos)**

**Respuesta:**
1. **Header**: identifica el tipo de token y el algoritmo criptográfico empleado para firmarlo (por ejemplo, HS256).
2. **Payload**: contiene las afirmaciones (*claims*) sobre el usuario y el contexto de la sesión —en biopet, por ejemplo, el identificador del usuario, su rol (veterinario, recepcionista, etc.) y las marcas de tiempo de emisión y expiración.
3. **Signature**: se calcula aplicando el algoritmo del header sobre la concatenación codificada de header y payload usando una clave del emisor; sirve para comprobar que el token no ha sido modificado y que proviene de una fuente legítima.



**b) Un compañero afirma: «como el JWT va firmado, puedo guardar en el *payload* la contraseña del usuario sin riesgo». Explique por qué está equivocado, precisando la diferencia entre firmar y cifrar. (2 puntos)**

**Respuesta:**
La confusión radica en tratar "firmar" como sinónimo de "ocultar". **Firmar** un JWT únicamente certifica que su contenido no ha sido manipulado y que fue emitido por quien corresponde, pero el payload permanece **codificado en Base64url, no cifrado**, por lo que cualquiera puede decodificarlo y leerlo sin esfuerzo. **Cifrar**, en cambio, sí convierte el contenido en algo ilegible sin poseer la clave correspondiente. Por eso, guardar la contraseña de un usuario dentro del payload la deja expuesta en texto plano ante cualquiera que capture o inspeccione el token.



**c) El JWT es *stateless* por diseño, lo que genera un problema conocido: no se puede invalidar un token antes de que expire. Describa dos estrategias distintas para revocarlo y señale la desventaja de cada una. (3 puntos)**

**Respuesta:**
1. **Registro de tokens invalidados** (lista negra) mantenido en un almacén de acceso rápido, con un tiempo de vida igual al que le resta al token original.

 - *Desventaja*: obliga a introducir estado nuevamente en el lado del servidor y agrega una verificación adicional en cada petición, además de requerir sincronización si hay múltiples instancias.

2. **Combinación de token de acceso de corta duración y token de refresco controlado en base de datos**, donde este último sí puede eliminarse para cortar el acceso.
 - *Desventaja*: incrementa la complejidad del sistema (dos tipos de token, endpoint de renovación, rotación) y el token de acceso ya emitido seguirá siendo válido hasta su corta expiración, aun cuando se intente revocar antes.




---

## A3. SOAP frente a REST — 8 puntos

**a) Complete la tabla comparativa con seis criterios entre SOAP y REST. (5 puntos)**

**Respuesta:**

| Criterio | SOAP | REST |
|---|---|---|
| Formato del mensaje | Envelope XML rígido y normado | Cualquier formato ligero, típicamente JSON |
| Contrato de descripción | WSDL, definición formal y estricta | OpenAPI/Swagger, documentación flexible no obligatoria |
| Sobrecarga de serialización | Considerable, por las etiquetas y espacios de nombres XML | Mínima, gracias a la compacidad del JSON |
| Tipado | Estricto, validado contra esquemas XSD | Flexible o dinámico según el formato usado |
| Consumo desde dispositivos móviles | Complicado, por el peso de los mensajes y el procesamiento XML | Sencillo, por el bajo consumo de datos y batería |
| Manejo de errores | A través de un elemento `<Fault>` normado dentro del propio mensaje | Mediante códigos de estado HTTP acompañados de un cuerpo descriptivo |

**b) El Servicio de Rentas Internas del Ecuador expone la autorización de comprobantes electrónicos mediante servicios SOAP. Explique dos razones técnicas por las que una institución de ese tipo mantiene SOAP en lugar de migrar a REST. (3 puntos)**

**Respuesta:**
1. **Robustez en seguridad y validación formal**: el ecosistema WS-Security ofrece firma digital y no repudio integrados al mensaje XML, respaldados por esquemas XSD estrictos definidos en el WSDL —elementos indispensables cuando se autorizan comprobantes tributarios— sin que REST cuente todavía con un estándar equivalente tan consolidado.
2. **Garantías de entrega y consistencia transaccional**: extensiones como WS-ReliableMessaging o WS-AtomicTransaction aseguran que un mensaje se procese exactamente una vez y que las transacciones distribuidas mantengan consistencia, algo crítico en un sistema fiscal y que en REST tendría que implementarse manualmente sin soporte nativo.



---

## A4. Cache-aside sobre un servicio externo — 8 puntos

> El proyecto base define en `CacheConfig` dos espacios de caché: `libros` con TTL de 2 minutos y `openlibrary` con TTL de 24 horas.

**a) Describa el patrón *cache-aside* en sus cuatro pasos, desde que llega la petición hasta que se responde. (3 puntos)**

**Respuesta:**
1. Al recibir la solicitud, el sistema **revisa primero si el dato ya está en caché**.
2. Si el dato está presente y aún vigente (*hit*), se **entrega directamente desde la caché**, sin consultar la fuente original.
3. Si no está o ya expiró (*miss*), se **recurre a la fuente original** (base de datos propia o servicio externo) para obtenerlo.
4. Una vez obtenido, se **almacena en la caché con su tiempo de vida definido** y luego se **envía la respuesta** al cliente.


**b) Justifique técnicamente por qué el TTL de `openlibrary` es doce veces mayor que el de `libros`, y qué criterio general debe guiar la elección de un TTL. (3 puntos)**

**Respuesta:**



**c) Explique por qué nunca debe almacenarse en caché la respuesta de un fallo del servicio externo, y describa qué le ocurriría al sistema si se hiciera. (2 puntos)**

**Respuesta:**



---

## A5. Diagnóstico de códigos de estado y contrato de errores — 8 puntos

> Todos los errores del proyecto base salen en formato *Problem Details* conforme a la RFC 9457, que obsoleta a la RFC 7807.

Para cada escenario indique el código HTTP correcto y explique en una línea por qué. **Cada fila vale 1 punto** (0,5 por el código y 0,5 por la justificación); el literal g) vale 2 puntos.

| # | Escenario | Código | Justificación (una línea) |
|---|---|---|---|
| a | `GET /api/v1/libros/999999` y ese identificador no existe | | |
| b | `POST /api/v1/libros` sin cabecera `Authorization` | | |
| c | Usuario autenticado con rol `LECTOR` envía `POST /api/v1/libros` | | |
| d | `POST /api/v1/libros` con el campo `titulo` vacío | | |
| e | Prestar un libro a un socio que ya tiene tres préstamos activos | | |
| f | La API de Open Library no responde dentro del *timeout* configurado | | |

**g) Explique por qué devolver `200 OK` con un cuerpo `{"success": false}` es un error de diseño, y qué restricción de REST se incumple al hacerlo. (2 puntos)**

**Respuesta:**



---

## Declaración de honestidad académica

Marque con una `x` y complete:

- [ ] Declaro que estas respuestas son de mi autoría, redactadas durante la sesión de examen, sin asistencia de inteligencia artificial ni comunicación con terceros.

Firma (nombre completo): ______________________________
