package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CargadorConfiguracionXmlTest {
    private final CargadorConfiguracionXml cargador = new CargadorConfiguracionXml();

    @Test
    void cargaConfiguracionYRasterizaAristas() throws IOException, URISyntaxException {
        Path archivo = Path.of(getClass().getResource("/map-test.xml").toURI());

        DefinicionSimulacion definicion = cargador.cargar(archivo);
        int[][] mapa = definicion.getDiseñoCiudad();
        ConfiguracionSimulacion configuracion = definicion.getConfiguracion();

        assertEquals(5, mapa.length);
        assertEquals(7, mapa[0].length);
        assertEquals(33, configuracion.getCantidadVehiculos());
        assertEquals(2, configuracion.getConfiguracionesSemaforo().size());
        ConfiguracionSemaforo configuracionCuatroFases =
                configuracion.getConfiguracionesSemaforo().get("four-phase");
        assertNotNull(configuracionCuatroFases);
        assertEquals(3, configuracionCuatroFases.duracionAmarilloSeg());
        assertEquals(1, configuracionCuatroFases.duracionRojoSeg());
        assertEquals(4, configuracionCuatroFases.fases().size());
        assertEquals(Set.of("NORTE"), configuracionCuatroFases.fases().get(0).direccionesAbiertas());
        assertFalse(configuracionCuatroFases.esDinamica());
        assertEquals(4, configuracion.getConfiguracionesSemaforo().get("two-phase").duracionRojoSeg());
        assertEquals("four-phase",
                definicion.getConfiguracionesPorInterseccion().get(new CoordenadaMapa(2, 0)));
        assertEquals("two-phase",
                definicion.getConfiguracionesPorInterseccion().get(new CoordenadaMapa(2, 4)));

        assertEquals(10, mapa[0][0]);
        assertEquals(2, mapa[1][0]);
        assertEquals(7, mapa[2][0]);
        assertEquals(5, mapa[2][1]);
        assertEquals(5, mapa[2][3]);
        assertEquals(7, mapa[2][4]);
        assertEquals(6, mapa[3][4]);
        assertEquals(10, mapa[4][4]);
    }

    @Test
    void generaRutaEntrePuntosCombinadosDelMapaXml() throws IOException, URISyntaxException {
        Path archivo = Path.of(getClass().getResource("/map-test.xml").toURI());
        DefinicionSimulacion definicion = cargador.cargar(archivo);
        Mapa mapa = new Mapa(definicion);

        PuntoGeneracion origen = mapa.buscarSpawnPorCoordenada(0, 0);
        PuntoGeneracion destino = mapa.buscarDestinoPorCoordenada(4, 4);
        assertNotNull(origen);
        assertNotNull(destino);
        assertEquals(2, mapa.getPuntosSpawn().size());
        assertEquals(2, mapa.getPuntosDestino().size());
        assertNotNull(mapa.calcularRutaDijkstra(origen, destino));
        assertEquals(9, mapa.calcularRutaDijkstra(origen, destino).size());
    }

    @Test
    void separaSpawnsYDestinosEnLaRutaYNoPermiteSalirDelDestino(
            @TempDir Path directorio) throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace(
                        "id=\"S0\" x=\"0\" y=\"0\" type=\"spawn-destination\"",
                        "id=\"S0\" x=\"0\" y=\"0\" type=\"spawn\"")
                .replace(
                        "id=\"S1\" x=\"4\" y=\"4\" type=\"spawn-destination\"",
                        "id=\"S1\" x=\"4\" y=\"4\" type=\"destination\"");
        Path archivo = directorio.resolve("map-separate-endpoints-test.xml");
        Files.writeString(archivo, xml);

        DefinicionSimulacion definicion = cargador.cargar(archivo);
        Mapa mapa = new Mapa(definicion);
        PuntoGeneracion origen = mapa.buscarSpawnPorCoordenada(0, 0);
        PuntoGeneracion destino = mapa.buscarDestinoPorCoordenada(4, 4);

        assertNotNull(origen);
        assertTrue(origen.esSpawn());
        assertFalse(origen.esDestino());
        assertNotNull(destino);
        assertFalse(destino.esSpawn());
        assertTrue(destino.esDestino());
        assertEquals(1, mapa.getPuntosSpawn().size());
        assertEquals(1, mapa.getPuntosDestino().size());
        assertNotNull(mapa.calcularRutaDijkstra(origen, destino));
        assertNull(mapa.calcularRutaDijkstra(destino, origen));
    }

    @Test
    void rechazaNodosNoDeclarados(@TempDir Path directorio) throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace("from=\"S0\"", "from=\"C\"");
        Path archivo = directorio.resolve("invalid-map.xml");
        Files.writeString(archivo, xml);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> cargador.cargar(archivo));
        assertTrue(error.getMessage().contains("no declarado"));
    }

    @Test
    void aceptaFasesDinamicasYUsaLimitesVerdesPredeterminados(@TempDir Path directorio)
            throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace("duration=\"10\"", "duration=\"dynamic\"")
                .replace("duration=\"5\"", "duration=\"dynamic\"");
        Path archivo = directorio.resolve("dynamic-map.xml");
        Files.writeString(archivo, xml);

        ConfiguracionSemaforo dinamica = cargador.cargar(archivo)
                .getConfiguracion()
                .getConfiguracionesSemaforo()
                .get("four-phase");
        assertTrue(dinamica.esDinamica());
        assertEquals(2, dinamica.duracionVerdeMinimaSeg());
        assertEquals(30, dinamica.duracionVerdeMaximaSeg());
        assertTrue(dinamica.fases().stream().allMatch(FaseSemaforo::duracionDinamica));
    }

    @Test
    void rechazaMezclarDuracionesFijasYDinamicas(@TempDir Path directorio)
            throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace("duration=\"10\"", "duration=\"dynamic\"");
        Path archivo = directorio.resolve("mixed-duration-map.xml");
        Files.writeString(archivo, xml);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> cargador.cargar(archivo));
        assertTrue(error.getMessage().contains("duration"));
    }

    @Test
    void cargaLimitesVerdesConfigurablesParaControlDinamico(@TempDir Path directorio)
            throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace(
                        "phase-config id=\"four-phase\" forced-yellow-duration=\"3\" forced-red-duration=\"1\"",
                        "phase-config id=\"four-phase\" forced-yellow-duration=\"3\" forced-red-duration=\"1\" "
                                + "min-green-duration=\"1\" max-green-duration=\"8\"")
                .replace("duration=\"10\"", "duration=\"dynamic\"")
                .replace("duration=\"5\"", "duration=\"dynamic\"");
        Path archivo = directorio.resolve("green-bounds-map.xml");
        Files.writeString(archivo, xml);

        ConfiguracionSemaforo dinamica = cargador.cargar(archivo)
                .getConfiguracion()
                .getConfiguracionesSemaforo()
                .get("four-phase");
        assertEquals(1, dinamica.duracionVerdeMinimaSeg());
        assertEquals(8, dinamica.duracionVerdeMaximaSeg());
    }

    @Test
    void rechazaDuracionVerdeMaximaMenorQueLaMinima(@TempDir Path directorio)
            throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace("duration=\"10\"", "duration=\"dynamic\"")
                .replace("duration=\"5\"", "duration=\"dynamic\"")
                .replace(
                        "phase-config id=\"four-phase\" forced-yellow-duration=\"3\" forced-red-duration=\"1\"",
                        "phase-config id=\"four-phase\" forced-yellow-duration=\"3\" forced-red-duration=\"1\" "
                                + "min-green-duration=\"5\" max-green-duration=\"4\"");
        Path archivo = directorio.resolve("invalid-green-bounds-map.xml");
        Files.writeString(archivo, xml);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> cargador.cargar(archivo));
        assertTrue(error.getMessage().contains("máxima"), error.getMessage());
    }

    @Test
    void rechazaReferenciasAConfiguracionesDeSemaforoInexistentes(@TempDir Path directorio)
            throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace("phase-config=\"four-phase\"", "phase-config=\"missing\"");
        Path archivo = directorio.resolve("unknown-phase-config.xml");
        Files.writeString(archivo, xml);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> cargador.cargar(archivo));
        assertTrue(error.getMessage().contains("no declarada"));
    }

    @Test
    void requiereConfiguracionEnCadaInterseccion(@TempDir Path directorio)
            throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace(" phase-config=\"four-phase\"", "");
        Path archivo = directorio.resolve("missing-phase-config.xml");
        Files.writeString(archivo, xml);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> cargador.cargar(archivo));
        assertTrue(error.getMessage().contains("phase-config"));
    }

    @Test
    void requiereAlMenosUnSpawnYUnDestino(@TempDir Path directorio)
            throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace("type=\"spawn-destination\"", "type=\"spawn\"");
        Path archivo = directorio.resolve("missing-destination-map.xml");
        Files.writeString(archivo, xml);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> cargador.cargar(archivo));
        assertTrue(error.getMessage().contains("destination"));
    }

    @Test
    void rechazaIdentificadoresDeConfiguracionDuplicados(@TempDir Path directorio)
            throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace("phase-config id=\"two-phase\"", "phase-config id=\"four-phase\"");
        Path archivo = directorio.resolve("duplicate-phase-config.xml");
        Files.writeString(archivo, xml);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> cargador.cargar(archivo));
        assertTrue(error.getMessage().contains("está repetido"));
    }

    @Test
    void rechazaAristasDiagonales(@TempDir Path directorio) throws IOException, URISyntaxException {
        String xml = Files.readString(Path.of(getClass().getResource("/map-test.xml").toURI()))
                .replace("id=\"N1\" x=\"4\" y=\"2\"", "id=\"N1\" x=\"4\" y=\"3\"");
        Path archivo = directorio.resolve("diagonal-map.xml");
        Files.writeString(archivo, xml);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> cargador.cargar(archivo));
        assertTrue(error.getMessage().contains("no es recta"));
    }
}
