package pe.gob.prorridre.asistencia.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pe.gob.prorridre.asistencia.model.Departamento;

import java.util.*;

@Repository
public interface DepartamentoRepository extends JpaRepository<Departamento, Long> {

    Optional<Departamento> findByCodigo(String codigo);

    Optional<Departamento> findByNombreIgnoreCase(String nombre);

    List<Departamento> findByParentIsNullOrderByNombreAsc();

    List<Departamento> findByParent_IdOrderByNombreAsc(Long parentId);

    List<Departamento> findAllByOrderByNombreAsc();

    @Query("SELECT d FROM Departamento d LEFT JOIN FETCH d.parent ORDER BY d.nombre ASC")
    List<Departamento> findAllConPadre();

    /**
     * Resuelve de forma segura en memoria (sin problemas de Hibernate LazyInitialization)
     * todos los nombres de departamentos y metas que descienden del departamento especificado.
     */
    default Set<String> obtenerNombresDescendientesDe(String nombreDepartamento) {
        Set<String> resultado = new HashSet<>();
        if (nombreDepartamento == null || nombreDepartamento.trim().isEmpty()) {
            return resultado;
        }

        List<Departamento> todos = findAll();
        Map<Long, Departamento> porId = new HashMap<>();
        Map<Long, List<Long>> hijosPorPadre = new HashMap<>();

        Long raizId = null;
        for (Departamento d : todos) {
            porId.put(d.getId(), d);
            if (d.getNombre() != null && d.getNombre().equalsIgnoreCase(nombreDepartamento.trim())) {
                raizId = d.getId();
            }
            if (d.getParent() != null && d.getParent().getId() != null) {
                hijosPorPadre.computeIfAbsent(d.getParent().getId(), k -> new ArrayList<>()).add(d.getId());
            }
        }

        if (raizId == null) {
            resultado.add(nombreDepartamento.trim().toUpperCase());
            return resultado;
        }

        Queue<Long> cola = new LinkedList<>();
        cola.add(raizId);

        while (!cola.isEmpty()) {
            Long curId = cola.poll();
            Departamento d = porId.get(curId);
            if (d != null && d.getNombre() != null) {
                resultado.add(d.getNombre().trim().toUpperCase());
            }
            List<Long> hijos = hijosPorPadre.get(curId);
            if (hijos != null) {
                cola.addAll(hijos);
            }
        }

        return resultado;
    }

    /**
     * Resuelve de forma segura en memoria todos los IDs de departamentos descendientes.
     */
    default Set<Long> obtenerIdsDescendientesDe(Long departamentoId) {
        Set<Long> resultado = new HashSet<>();
        if (departamentoId == null) return resultado;

        List<Departamento> todos = findAll();
        Map<Long, List<Long>> hijosPorPadre = new HashMap<>();

        for (Departamento d : todos) {
            if (d.getParent() != null && d.getParent().getId() != null) {
                hijosPorPadre.computeIfAbsent(d.getParent().getId(), k -> new ArrayList<>()).add(d.getId());
            }
        }

        Queue<Long> cola = new LinkedList<>();
        cola.add(departamentoId);

        while (!cola.isEmpty()) {
            Long curId = cola.poll();
            resultado.add(curId);
            List<Long> hijos = hijosPorPadre.get(curId);
            if (hijos != null) {
                cola.addAll(hijos);
            }
        }

        return resultado;
    }
}
