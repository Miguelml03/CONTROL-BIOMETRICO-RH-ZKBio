package pe.gob.prorridre.asistencia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import pe.gob.prorridre.asistencia.config.AppConfig;
import pe.gob.prorridre.asistencia.domain.entity.Marcacion;
import pe.gob.prorridre.asistencia.domain.repository.MarcacionRepository;
import pe.gob.prorridre.asistencia.zkbio.dto.TransaccionDto;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarcacionServiceTest {

    @Test
    void guardaNombreYApellidoRecibidosDeZkbio() {
        MarcacionRepository repository = mock(MarcacionRepository.class);
        AppConfig config = mock(AppConfig.class);
        MarcacionService service = new MarcacionService(repository, config, new ObjectMapper());
        TransaccionDto dto = new TransaccionDto();
        dto.setId(42L);
        dto.setEmpCode("00123");
        dto.setFirstName("Ana");
        dto.setLastName("Pérez");
        dto.setPunchTime("2026-11-01 08:00:00");
        dto.setPunchState("0");
        when(repository.existsById(42L)).thenReturn(false);

        assertEquals(1, service.guardarNuevas(List.of(dto)));

        ArgumentCaptor<Marcacion> guardada = ArgumentCaptor.forClass(Marcacion.class);
        verify(repository).save(guardada.capture());
        assertEquals("Ana", guardada.getValue().getFirstName());
        assertEquals("Pérez", guardada.getValue().getLastName());
    }

    @Test
    void recuperaNombreYApellidoDeRawJsonEnMarcacionesExistentes() {
        MarcacionRepository repository = mock(MarcacionRepository.class);
        AppConfig config = mock(AppConfig.class);
        MarcacionService service = new MarcacionService(repository, config, new ObjectMapper());
        Marcacion existente = new Marcacion();
        existente.setId(42L);
        existente.setRawJson("{\"first_name\":\"Ana\",\"last_name\":\"Pérez\"}");
        when(repository.findTop50ByOrderByPunchTimeDesc()).thenReturn(List.of(existente));

        List<Marcacion> resultado = service.obtenerUltimas50();

        assertEquals("Ana", resultado.get(0).getFirstName());
        assertEquals("Pérez", resultado.get(0).getLastName());
    }
}
