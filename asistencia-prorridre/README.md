# PRORRIDRE PUNO – Sistema de Control de Asistencia (Fase 1)

Sistema web de control de asistencia en tiempo real que lee marcaciones del
reloj biométrico **ZKBio Time** (solo lectura) y las muestra en un panel web
que se actualiza automáticamente cada 10 segundos.

---

## Requisitos

| Herramienta | Versión mínima |
|---|---|
| Java JDK | 17 |
| Maven | 3.9+ |
| Acceso de red | Al servidor ZKBio Time (`192.168.1.101:8085`) |

> **Nota:** Maven descarga todas las dependencias automáticamente.
> No se necesita instalar ninguna base de datos; el sistema usa H2 en archivo.

---

## Variables de entorno obligatorias

Las credenciales **nunca** van en el código ni en `application.properties`.
Deben definirse como variables de entorno antes de ejecutar el sistema:

### Windows (CMD)
```cmd
set ZK_USER=tu_usuario_zkbio
set ZK_PASS=tu_clave_zkbio
```

### Windows (PowerShell)
```powershell
$env:ZK_USER = "tu_usuario_zkbio"
$env:ZK_PASS = "tu_clave_zkbio"
```

### Linux / macOS
```bash
export ZK_USER=tu_usuario_zkbio
export ZK_PASS=tu_clave_zkbio
```

Si alguna variable falta, la aplicación falla al arrancar con este mensaje:

```
[PRORRIDRE] La variable de entorno ZK_USER no está definida o está vacía.
```

---

## Compilar y ejecutar

### 1. Compilar el proyecto

```cmd
cd "d:\DESCARGAS\proyec PRORRIDRE\asistencia-prorridre"
mvn clean package -DskipTests
```

### 2. Definir variables y ejecutar (todo en un bloque en PowerShell)

```powershell
$env:ZK_USER = "tu_usuario"
$env:ZK_PASS  = "tu_clave"
java -jar target\asistencia-prorridre-1.0.0-SNAPSHOT.jar
```

### 3. Abrir el panel web

Abrir en el navegador:
```
http://localhost:8080
```

---

## Estructura del proyecto

```
asistencia-prorridre/
│
├── pom.xml                          # Dependencias Maven
├── README.md                        # Este archivo
│
└── src/
    ├── main/
    │   ├── java/pe/gob/prorridre/asistencia/
    │   │   │
    │   │   ├── AsistenciaApplication.java    # Punto de entrada
    │   │   │
    │   │   ├── config/
    │   │   │   └── AppConfig.java            # Configuración y validación de credenciales
    │   │   │
    │   │   ├── domain/
    │   │   │   ├── entity/
    │   │   │   │   ├── Marcacion.java         # Tabla de marcaciones crudas
    │   │   │   │   └── EstadoMarcacion.java   # Tabla de configuración de estados
    │   │   │   └── repository/
    │   │   │       ├── MarcacionRepository.java
    │   │   │       └── EstadoMarcacionRepository.java
    │   │   │
    │   │   ├── zkbio/
    │   │   │   ├── ZkBioTokenManager.java    # Login JWT y renovación de token
    │   │   │   ├── ZkBioClient.java          # Cliente HTTP de solo lectura
    │   │   │   └── dto/
    │   │   │       ├── LoginRequest.java
    │   │   │       ├── LoginResponse.java
    │   │   │       ├── TransaccionDto.java    # Marcación individual de ZKBio
    │   │   │       └── PaginaTransacciones.java
    │   │   │
    │   │   ├── scheduler/
    │   │   │   └── LectorMarcaciones.java    # @Scheduled cada 20 s
    │   │   │
    │   │   ├── service/
    │   │   │   └── MarcacionService.java     # Lógica de negocio y persistencia
    │   │   │
    │   │   └── web/
    │   │       ├── MarcacionController.java  # API REST interna
    │   │       └── dto/
    │   │           └── MarcacionResponse.java
    │   │
    │   └── resources/
    │       ├── application.properties        # Configuración (sin credenciales)
    │       ├── data.sql                      # Datos iniciales: tabla de estados
    │       └── static/
    │           └── index.html                # Interfaz web (actualización cada 10 s)
    │
    └── test/
        └── java/pe/gob/prorridre/asistencia/
            └── AsistenciaApplicationTests.java
```

---

## Endpoints de la API REST interna

| Método | URL | Descripción |
|---|---|---|
| `GET` | `/api/marcaciones/ultimas` | Últimas 50 marcaciones con nombre, código y estado real |
| `GET` | `/api/estados` | Tabla de configuración de estados |
| `GET` | `/api/info` | Estado del sistema y hora del servidor |
| `GET` | `/h2-console` | Consola web H2 (solo desarrollo) |

---

## Tabla de estados (configurable sin recompilar)

Los estados se guardan en la tabla `estado_marcacion` y se cargan desde `data.sql`
al arrancar. Se pueden editar directamente en la BD o desde la pantalla de
configuración (Fase 4).

| punch_state | Nombre en ZKBio | Significado real | Hora típica |
|---|---|---|---|
| `0` | Entrada | **Entrada** | ~07:59 |
| `5` | Salida Hora Extra | **Salida a refrigerio** | ~13:02 |
| `4` | Entrada Hora Extra | **Regreso de refrigerio** | ~13:56 |
| `1` | Salida | **Salida final** | ~18:25 |

> ⚠️ Los nombres de ZKBio Time son engañosos. El sistema siempre muestra
> el **significado real** verificado con datos históricos de PRORRIDRE.

---

## Cambiar la URL del servidor ZKBio Time

Editar `src/main/resources/application.properties`:

```properties
zkbio.url=http://192.168.1.101:8085
```

No requiere recompilar si se usa el JAR con `-Dzkbio.url=...`:

```powershell
java -Dzkbio.url=http://NUEVA_IP:PUERTO -jar target\asistencia-prorridre-1.0.0-SNAPSHOT.jar
```

---

## Cambiar el intervalo de lectura

Por defecto 20 segundos. Para cambiarlo a 60 s:

```powershell
java -Dapp.lector.periodo-ms=60000 -jar target\asistencia-prorridre-1.0.0-SNAPSHOT.jar
```

---

## Consola H2 (solo para desarrollo)

Acceder a `http://localhost:8080/h2-console` con:
- **JDBC URL:** `jdbc:h2:file:./datos/asistencia`
- **Usuario:** `sa`
- **Contraseña:** *(vacía)*

> 🔒 Deshabilitar en producción: `spring.h2.console.enabled=false`

---

## Migrar a PostgreSQL (cuando sea necesario)

1. Añadir al `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
   </dependency>
   ```

2. Cambiar en `application.properties`:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/asistencia_prorridre
   spring.datasource.driver-class-name=org.postgresql.Driver
   spring.datasource.username=postgres
   spring.datasource.password=${DB_PASS}
   spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
   spring.h2.console.enabled=false
   ```

---

## Seguridad y privacidad (Ley 29733)

- El sistema **solo lee** de ZKBio Time. No escribe ni modifica nada.
- Los nombres y apellidos que entrega ZKBio se guardan junto con la marcación; para registros previos se recuperan del JSON crudo guardado, cuando está disponible.
- No se almacenan plantillas de huellas, fotos ni datos biométricos.
- Las credenciales van en variables de entorno; **nunca** en el código.
- El sistema no debe exponerse fuera de la red interna de PRORRIDRE.
- Login propio con roles y auditoría se implementarán en Fase 4.

---

## Fases del proyecto

| Fase | Estado | Contenido |
|---|---|---|
| **Fase 1** | ✅ **Completa** | Cliente ZKBio, lector programado, tabla de estados, panel web básico |
| Fase 2 | 🔜 Pendiente | Empleados, áreas, motor de reglas, pruebas unitarias |
| Fase 3 | 🔜 Pendiente | Panel en vivo: semáforo, tardanzas, presentes, por revisar |
| Fase 4 | 🔜 Pendiente | Detalle por empleado, reportes, exportación Excel, configuración |

---

## Ejecución de pruebas unitarias

```cmd
mvn test
```

Los tests usan H2 en memoria y credenciales ficticias; no necesitan el reloj real.
