package pe.gob.prorridre.asistencia.zkbio;

import pe.gob.prorridre.asistencia.config.AppConfig;
import pe.gob.prorridre.asistencia.zkbio.dto.PaginaTransacciones;
import pe.gob.prorridre.asistencia.zkbio.dto.TransaccionDto;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZkBioClientTest {

    @Test
    void conservaElRangoDeFechasAlSolicitarLaPaginaSiguiente() {
        AppConfig config = mock(AppConfig.class);
        ZkBioTokenManager tokenManager = mock(ZkBioTokenManager.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(config.getZkbioUrl()).thenReturn("http://zkbio.test:8085");
        when(config.getPageSize()).thenReturn(1000);
        when(tokenManager.obtenerHeaders()).thenReturn(new org.springframework.http.HttpHeaders());

        PaginaTransacciones primeraPagina = paginaConSiguiente(
            "http://zkbio.test:8085/iclock/api/transactions/?page=2"
                + "&start_time=2026-11-01+08%3A00%3A00"
                + "&end_time=2026-11-01+09%3A00%3A00"
        );
        PaginaTransacciones segundaPagina = paginaConSiguiente(null);
        List<String> urlsSolicitadas = new ArrayList<>();
        AtomicInteger llamadas = new AtomicInteger();

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(PaginaTransacciones.class)
        )).thenAnswer(invocacion -> {
            urlsSolicitadas.add(invocacion.getArgument(0));
            return ResponseEntity.ok(
                llamadas.getAndIncrement() == 0 ? primeraPagina : segundaPagina
            );
        });

        ZkBioClient client = new ZkBioClient(config, tokenManager, restTemplate);
        List<TransaccionDto> resultado = client.obtenerMarcaciones(
            LocalDateTime.of(2026, 11, 1, 8, 0),
            LocalDateTime.of(2026, 11, 1, 9, 0)
        );

        assertEquals(2, urlsSolicitadas.size());
        assertEquals("1", parametro(urlsSolicitadas.get(0), "page"));
        assertEquals("2", parametro(urlsSolicitadas.get(1), "page"));
        for (String url : urlsSolicitadas) {
            assertEquals("2026-11-01 08:00:00", parametro(url, "start_time"));
            assertEquals("2026-11-01 09:00:00", parametro(url, "end_time"));
        }
        assertEquals(2, resultado.size());
    }

    private static PaginaTransacciones paginaConSiguiente(String siguiente) {
        PaginaTransacciones pagina = new PaginaTransacciones();
        pagina.setData(List.of(new TransaccionDto()));
        pagina.setNext(siguiente);
        return pagina;
    }

    private static String parametro(String url, String nombre) {
        String query = url.substring(url.indexOf('?') + 1);
        for (String entrada : query.split("&")) {
            String[] partes = entrada.split("=", 2);
            if (partes[0].equals(nombre)) {
                return URLDecoder.decode(partes[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Falta el parámetro " + nombre + " en " + url);
    }
}
