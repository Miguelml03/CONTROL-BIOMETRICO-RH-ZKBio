---
name: biometrico-prorridre-core
description: Reglas técnicas, fórmulas auditadas de asistencia biométrica PRORRIDRE PUNO (ZKTeco / ZKBio Time 8.0.8) y motor financiero de pagos y descuentos para el Doctor.
---

# CONTEXTO Y ARQUITECTURA DEL PROYECTO
- **Proyecto:** `BIOMETRICO-PRO` / `asistencia-prorridre` (Java Spring Boot Maven en `http://localhost:8080`).
- **Origen de Datos:** Reloj biométrico ZKTeco (huella/rostro) -> ZKBio Time v8.0.8 -> Exportación Excel/API (18 columnas `A:R`, 101 empleados en 9 departamentos/metas institucionales).
- **Estructura de Paquetes Backend (`src/main/java/pe/gob/prorridre/asistencia/...`):**
  - `config/`: Configuración CORS global (`CorsConfig.java`) y parámetros en `application.properties`.
  - `model/`:
    * `RegistroAsistencia.java`: Mapeo exacto de las 18 columnas auditadas `A:R` + campos de trazabilidad y justificación del Doctor.
    * `Empleado.java`: Padrón institucional de trabajadores activos por departamento.
    * `EmpleadoConfigFinanciera.java`: Haberes mensuales, días/horas base, multa por omisión y switch de compensación.
    * `JustificacionAuditoria.java`: Trazabilidad histórica de exoneraciones autorizadas por el Doctor.
  - `dto/`:
    * `RegistroAsistenciaDto.java`: Mapeo de las 18 columnas con nombre completo y estado de justificación.
    * `DashboardKpiDto.java`: KPIs en tiempo real (puntualidad, Días-Hombre de ausentismo real, horas, ranking por metas).
    * `LiquidacionPagoDto.java`: Desglose financiero mensual (sueldo, descuentos, bonificaciones, neto a pagar).
    * `ConfigFinancieraRequestDto.java` y `JustificarRequestDto.java`.
  - `domain/repository/`:
    * `RegistroAsistenciaRepository.java`, `EmpleadoRepository.java`, `EmpleadoConfigFinancieraRepository.java`, `JustificacionAuditoriaRepository.java`.
  - `service/`:
    * `AttendanceEngineService.java`: Motor matemático auditado con filtro antirrebote (Debounce 300s), Time Buckets (I, J, K, L), imputación reglamentaria de almuerzo (13:00-14:00), tolerancia retroactiva contra las 08:00 y balance seguro sin errores `#VALUE!`.
    * `ExcelCsvImportService.java`: Sincronizador automático desde `marcacion` y procesador de CSV/XLSX sin dependencias externas pesadas.
    * `DashboardKpiService.java`: Motor de analítica y cruce de ausentismo real contra días hábiles (Lunes a Viernes).
    * `PayrollService.java`: Motor financiero de haberes, descuentos y acción rápida de justificación médica/directiva.
    * `ExcelExportService.java`: Generador de libros OpenXML (.xlsx) con 3 hojas (`Resumen_Asistencia_Depurado`, `Planilla_Pagos_Descuentos`, `Glosario y Reglas Tecnicas`) con fórmulas Excel corregidas.
  - `controller/`:
    * `AttendanceController.java`: `/api/v1/asistencia` (listado 18 columnas, sincronizar, importar, calcular-vector, justificar).
    * `DashboardController.java`: `GET /api/v1/dashboard/resumen` (KPIs, Días-Hombre, ranking).
    * `PayrollController.java`: `/api/v1/planillas` (liquidaciones masivas e individuales, configuración financiera).
    * `ExportController.java`: `GET /api/v1/exportar/excel` (descarga del reporte .xlsx con 3 hojas auditadas).
- **Estructura Frontend (`src/main/resources/static/`):**
  - `index.html`: SPA responsiva con Sidebar institucional, barra de filtros globales y 4 vistas:
    * Vista 1: Dashboard Ejecutivo de Analítica (6 KPI Cards + 4 Gráficos Chart.js).
    * Vista 2: Matriz de Auditoría Biométrica (18 columnas A:R con pestañas de filtro y Modal de Acción del Doctor).
    * Vista 3: Panel del Doctor (Tarjetas de presupuesto, panel de reglas en vivo, tabla de liquidación con sueldos editables inline y Modal de Boleta de Pago imprimible).
    * Vista 4: Glosario Técnico y Reglas de Negocio (Documentación completa con fórmulas corregidas y comparativo de fallos resueltos).
  - `css/prorridre.css`: Paleta corporativa PRORRIDRE, componentes, modales y estilos limpios de impresión `@media print`.
  - `js/toast.js`, `js/api.js`, `js/dashboard.js`, `js/asistencia.js`, `js/pagos-doctor.js`.
