package pe.gob.prorridre.asistencia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.prorridre.asistencia.domain.repository.DepartamentoRepository;
import pe.gob.prorridre.asistencia.domain.repository.EmpleadoRepository;
import pe.gob.prorridre.asistencia.domain.repository.RegistroAsistenciaRepository;
import pe.gob.prorridre.asistencia.dto.DashboardKpiDto;
import pe.gob.prorridre.asistencia.model.Departamento;
import pe.gob.prorridre.asistencia.model.Empleado;
import pe.gob.prorridre.asistencia.model.RegistroAsistencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de analítica y KPIs para el Dashboard de Asistencia PRORRIDRE PUNO.
 * Procesa en tiempo real indicadores de puntualidad, ausentismo real (Días-Hombre),
 * horas de déficit/excedente, rankings por Departamento/Meta y alertas de maestros desconocidos.
 */
@Service
@Transactional(readOnly = true)
public class DashboardKpiService {

    private static final Logger log = LoggerFactory.getLogger(DashboardKpiService.class);

    private final RegistroAsistenciaRepository registroRepo;
    private final EmpleadoRepository empleadoRepo;
    private final DepartamentoRepository departamentoRepo;

    public DashboardKpiService(RegistroAsistenciaRepository registroRepo,
                               EmpleadoRepository empleadoRepo,
                               DepartamentoRepository departamentoRepo) {
        this.registroRepo = registroRepo;
        this.empleadoRepo = empleadoRepo;
        this.departamentoRepo = departamentoRepo;
    }

    /**
     * Calcula en tiempo real todos los KPIs del Dashboard aplicando filtros opcionales
     * y resolución jerárquica de departamentos.
     */
    public DashboardKpiDto calcularKpis(LocalDate fechaInicio,
                                        LocalDate fechaFin,
                                        String departamentoFiltro,
                                        String empIdFiltro) {

        DashboardKpiDto dto = new DashboardKpiDto();

        // 1. Resolver fechas por defecto si no vienen especificadas
        List<LocalDate> fechasDisponibles = registroRepo.findDistinctFechas();
        if (fechasDisponibles.isEmpty()) {
            dto.setFechaInicio(LocalDate.now().toString());
            dto.setFechaFin(LocalDate.now().toString());
            dto.setDetalleAusencias(Collections.emptyList());
            dto.setRankingDepartamentos(Collections.emptyList());
            return dto;
        }

        LocalDate maxFecha = fechasDisponibles.get(0);
        LocalDate minFecha = fechasDisponibles.get(fechasDisponibles.size() - 1);

        LocalDate desde = (fechaInicio != null) ? fechaInicio : minFecha;
        LocalDate hasta = (fechaFin != null) ? fechaFin : maxFecha;

        if (desde.isAfter(hasta)) {
            LocalDate tmp = desde;
            desde = hasta;
            hasta = tmp;
        }

        dto.setFechaInicio(desde.toString());
        dto.setFechaFin(hasta.toString());
        dto.setDepartamentoFiltro(departamentoFiltro);
        dto.setEmpIdFiltro(empIdFiltro);

        // Resolver jerarquía departamental si hay filtro de forma segura en memoria
        Set<String> departamentosPermitidos = null;
        if (departamentoFiltro != null && !departamentoFiltro.trim().isEmpty()) {
            departamentosPermitidos = departamentoRepo.obtenerNombresDescendientesDe(departamentoFiltro.trim());
        }

        // 2. Obtener lista de empleados activos
        List<Empleado> empleadosActivos = obtenerPadronEmpleadosActivos(departamentosPermitidos, empIdFiltro);
        dto.setTotalEmpleadosActivos(empleadosActivos.size());

        // Alerta de empleados desconocidos
        List<Empleado> desconocidos = empleadoRepo.findEmpleadosDesconocidos();
        dto.setCountEmpleadosDesconocidos(desconocidos.size());
        List<DashboardKpiDto.EmpleadoDesconocidoDto> detalleDesconocidos = new ArrayList<>();
        for (Empleado e : desconocidos) {
            detalleDesconocidos.add(new DashboardKpiDto.EmpleadoDesconocidoDto(
                    e.getEmpId(),
                    e.getNombres(),
                    e.getApellidos(),
                    e.getDepartamentoNombre(),
                    "Empleado registrado con datos incompletos en el reloj biométrico. Requiere actualización en ZKBio Time o Sistema."
            ));
        }
        dto.setDetalleEmpleadosDesconocidos(detalleDesconocidos);

        // 3. Obtener registros de asistencia evaluados en el rango
        List<RegistroAsistencia> registros = registroRepo.findByFechaBetweenOrderByFechaDescDepartamentoAscApellidosAsc(desde, hasta);

        // Filtrar en memoria por departamento (con jerarquía) o empId
        if (departamentosPermitidos != null) {
            final Set<String> permitidos = departamentosPermitidos;
            registros = registros.stream()
                    .filter(r -> r.getDepartamento() != null && permitidos.contains(r.getDepartamento().trim().toUpperCase()))
                    .collect(Collectors.toList());
        }
        if (empIdFiltro != null && !empIdFiltro.trim().isEmpty()) {
            registros = registros.stream()
                    .filter(r -> r.getEmpId() != null && r.getEmpId().equalsIgnoreCase(empIdFiltro.trim()))
                    .collect(Collectors.toList());
        }

        dto.setTotalRegistrosEvaluados(registros.size());

        // 4. Calcular días hábiles laborables en el rango (Lunes a Viernes)
        List<LocalDate> diasHabiles = new ArrayList<>();
        LocalDate cur = desde;
        while (!cur.isAfter(hasta)) {
            if (cur.getDayOfWeek() != DayOfWeek.SATURDAY && cur.getDayOfWeek() != DayOfWeek.SUNDAY) {
                diasHabiles.add(cur);
            }
            cur = cur.plusDays(1);
        }
        dto.setTotalDiasHabilesRango(diasHabiles.size());

        // 5. Cruce de Ausentismo Real (Días-Hombre): Padrón vs Días Hábiles
        Set<String> marcacionesExistentes = registros.stream()
                .filter(r -> r.getNumMarcaciones() != null && r.getNumMarcaciones() > 0)
                .map(r -> r.getEmpId() + "|" + r.getFecha())
                .collect(Collectors.toSet());

        List<DashboardKpiDto.AusenciaDto> detalleAusencias = new ArrayList<>();
        long totalDiasHombreFalta = 0;

        for (Empleado emp : empleadosActivos) {
            for (LocalDate dia : diasHabiles) {
                String clave = emp.getEmpId() + "|" + dia;
                if (!marcacionesExistentes.contains(clave)) {
                    totalDiasHombreFalta++;
                    detalleAusencias.add(new DashboardKpiDto.AusenciaDto(
                            emp.getEmpId(),
                            emp.getNombreCompleto(),
                            emp.getDepartamentoNombre(),
                            dia.toString()
                    ));
                }
            }
        }
        dto.setDiasHombreFalta(totalDiasHombreFalta);
        dto.setDetalleAusencias(detalleAusencias);

        // 6. Conteo y porcentajes de Puntualidad (Columna M)
        long countATiempo = 0;
        long countTardanza = 0;
        long countOmision = 0;
        long totalMinTardanza = 0;

        for (RegistroAsistencia r : registros) {
            String cond = r.getCondicionIngreso() != null ? r.getCondicionIngreso().trim() : "";
            if ("A Tiempo".equalsIgnoreCase(cond)) {
                countATiempo++;
            } else if ("Tardanza".equalsIgnoreCase(cond)) {
                countTardanza++;
            } else if (cond.contains("Omisión") || cond.contains("Sin Marcación")) {
                countOmision++;
            }

            if (r.getMinutosTardanza() != null) {
                totalMinTardanza += r.getMinutosTardanza();
            }
        }

        long n = registros.size();
        dto.setCountATiempo(countATiempo);
        dto.setCountTardanza(countTardanza);
        dto.setCountOmisionEntrada(countOmision);
        dto.setPctATiempo(n > 0 ? redondear((countATiempo * 100.0) / n) : 0.0);
        dto.setPctTardanza(n > 0 ? redondear((countTardanza * 100.0) / n) : 0.0);
        dto.setPctOmisionEntrada(n > 0 ? redondear((countOmision * 100.0) / n) : 0.0);

        dto.setTotalMinutosTardanza(totalMinTardanza);
        dto.setPromedioMinutosTardanza(countTardanza > 0 ? redondear((double) totalMinTardanza / countTardanza) : 0.0);

        // 7. Cumplimiento de Jornada (Columna R)
        long countExcedente = 0;
        long countReglamentaria = 0;
        long countDeficit = 0;
        long countIncompleto = 0;

        double totalHorasEfectivas = 0.0;
        double totalHorasExtra = 0.0;
        double totalHorasDeficit = 0.0;

        for (RegistroAsistencia r : registros) {
            String est = r.getEstadoCumplimiento() != null ? r.getEstadoCumplimiento().trim() : "";
            if ("Excedente de Jornada".equalsIgnoreCase(est)) {
                countExcedente++;
            } else if ("Jornada Reglamentaria".equalsIgnoreCase(est)) {
                countReglamentaria++;
            } else if ("Déficit de Jornada".equalsIgnoreCase(est)) {
                countDeficit++;
            } else if (est.startsWith("Incompleto") || est.contains("Falta")) {
                countIncompleto++;
            }

            if (r.getHorasEfectivasDecimal() != null) {
                totalHorasEfectivas += r.getHorasEfectivasDecimal();
            }
            if (r.getBalanceJornada() != null) {
                if (r.getBalanceJornada() > 0) {
                    totalHorasExtra += r.getBalanceJornada();
                } else if (r.getBalanceJornada() < 0) {
                    totalHorasDeficit += Math.abs(r.getBalanceJornada());
                }
            }
        }

        dto.setCountExcedente(countExcedente);
        dto.setCountReglamentaria(countReglamentaria);
        dto.setCountDeficit(countDeficit);
        dto.setCountIncompleto(countIncompleto);
        dto.setPctExcedente(n > 0 ? redondear((countExcedente * 100.0) / n) : 0.0);
        dto.setPctReglamentaria(n > 0 ? redondear((countReglamentaria * 100.0) / n) : 0.0);
        dto.setPctDeficit(n > 0 ? redondear((countDeficit * 100.0) / n) : 0.0);
        dto.setPctIncompleto(n > 0 ? redondear((countIncompleto * 100.0) / n) : 0.0);

        dto.setTotalHorasEfectivas(redondear(totalHorasEfectivas));
        dto.setTotalHorasExtra(redondear(totalHorasExtra));
        dto.setTotalHorasDeficit(redondear(totalHorasDeficit));

        // 8. Ranking por Departamento/Meta
        dto.setRankingDepartamentos(construirRankingDepartamentos(registros, detalleAusencias));

        return dto;
    }

    private List<DashboardKpiDto.RankingDepartamentoDto> construirRankingDepartamentos(
            List<RegistroAsistencia> registros,
            List<DashboardKpiDto.AusenciaDto> detalleAusencias) {

        Map<String, Long> faltasPorDepto = detalleAusencias.stream()
                .collect(Collectors.groupingBy(DashboardKpiDto.AusenciaDto::getDepartamento, Collectors.counting()));

        Map<String, List<RegistroAsistencia>> porDepto = registros.stream()
                .collect(Collectors.groupingBy(r -> r.getDepartamento() != null ? r.getDepartamento() : "SIN DEPARTAMENTO"));

        List<DashboardKpiDto.RankingDepartamentoDto> ranking = new ArrayList<>();

        for (Map.Entry<String, List<RegistroAsistencia>> entry : porDepto.entrySet()) {
            String depto = entry.getKey();
            List<RegistroAsistencia> list = entry.getValue();

            DashboardKpiDto.RankingDepartamentoDto rDto = new DashboardKpiDto.RankingDepartamentoDto();
            rDto.setDepartamento(depto);
            rDto.setRegistros(list.size());

            long emps = list.stream().map(RegistroAsistencia::getEmpId).distinct().count();
            rDto.setEmpleados(emps);

            double horas = 0.0;
            long minTard = 0;
            long tardanzas = 0;
            long aTiempo = 0;
            long incompletos = 0;

            for (RegistroAsistencia r : list) {
                if (r.getHorasEfectivasDecimal() != null) horas += r.getHorasEfectivasDecimal();
                if (r.getMinutosTardanza() != null) minTard += r.getMinutosTardanza();
                if ("Tardanza".equalsIgnoreCase(r.getCondicionIngreso())) tardanzas++;
                if ("A Tiempo".equalsIgnoreCase(r.getCondicionIngreso())) aTiempo++;
                if (r.getEstadoCumplimiento() != null && r.getEstadoCumplimiento().startsWith("Incompleto")) incompletos++;
            }

            rDto.setHorasEfectivas(redondear(horas));
            rDto.setPromedioHorasDia(list.size() > 0 ? redondear(horas / list.size()) : 0.0);
            rDto.setTotalMinutosTardanza(minTard);
            rDto.setTardanzas(tardanzas);
            rDto.setaTiempo(aTiempo);
            rDto.setIncompletos(incompletos);
            rDto.setFaltas(faltasPorDepto.getOrDefault(depto, 0L));

            ranking.add(rDto);
        }

        ranking.sort((a, b) -> Double.compare(b.getHorasEfectivas(), a.getHorasEfectivas()));
        return ranking;
    }

    private List<Empleado> obtenerPadronEmpleadosActivos(Set<String> departamentosPermitidos, String empIdFiltro) {
        List<Empleado> lista = empleadoRepo.findByActivoTrueOrderByDepartamentoAscApellidosAsc();

        // Si la tabla empleado aún no ha sido poblada, deducirla desde los registros de asistencia
        if (lista.isEmpty()) {
            List<RegistroAsistencia> todosRegistros = registroRepo.findAll();
            Map<String, Empleado> deducidos = new LinkedHashMap<>();

            for (RegistroAsistencia r : todosRegistros) {
                String empId = r.getEmpId();
                if (empId == null || empId.trim().isEmpty()) continue;

                if (!deducidos.containsKey(empId)) {
                    Empleado nuevo = new Empleado(
                            empId,
                            r.getNombres() != null ? r.getNombres() : "",
                            r.getApellidos() != null ? r.getApellidos() : "",
                            r.getEmpresa() != null ? r.getEmpresa() : "PRORRIDRE PUNO",
                            r.getDepartamento() != null ? r.getDepartamento() : "SIN DEPARTAMENTO"
                    );
                    deducidos.put(empId, nuevo);
                }
            }

            if (!deducidos.isEmpty()) {
                lista = new ArrayList<>(deducidos.values());
                try {
                    empleadoRepo.saveAll(lista);
                } catch (Exception ex) {
                    log.warn("[Dashboard] Error al sincronizar padrón base de empleados: {}", ex.getMessage());
                }
            }
        }

        // Aplicar filtros
        if (departamentosPermitidos != null) {
            final Set<String> permitidos = departamentosPermitidos;
            lista = lista.stream()
                    .filter(e -> e.getDepartamentoNombre() != null && permitidos.contains(e.getDepartamentoNombre().trim().toUpperCase()))
                    .collect(Collectors.toList());
        }
        if (empIdFiltro != null && !empIdFiltro.trim().isEmpty()) {
            lista = lista.stream()
                    .filter(e -> e.getEmpId() != null && e.getEmpId().equalsIgnoreCase(empIdFiltro.trim()))
                    .collect(Collectors.toList());
        }

        return lista;
    }

    private double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
