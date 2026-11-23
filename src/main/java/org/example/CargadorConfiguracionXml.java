package org.example;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CargadorConfiguracionXml {
    public DefinicionSimulacion cargar(Path ruta) throws IOException {
        Document documento = leerDocumento(ruta);
        Element raiz = documento.getDocumentElement();
        if (raiz == null || !"map".equals(raiz.getTagName())) {
            throw error("El elemento raíz debe ser <map>.");
        }

        Element elementoConfig = hijoRequerido(raiz, "config");
        Element forma = hijoRequerido(elementoConfig, "shape");
        int filas = enteroPositivo(forma, "height");
        int columnas = enteroPositivo(forma, "width");
        int cantidadVehiculos = enteroPositivo(hijoRequerido(elementoConfig, "cars"), "max");
        Map<String, ConfiguracionSemaforo> configuracionesSemaforo =
                leerConfiguracionesSemaforo(elementoConfig);

        ConfiguracionSimulacion configuracion = new ConfiguracionSimulacion();
        configuracion.setCantidadVehiculos(cantidadVehiculos);
        configuracion.setConfiguracionesSemaforo(configuracionesSemaforo);

        MapaXml mapa = construirMapa(raiz, filas, columnas, configuracionesSemaforo.keySet());
        return new DefinicionSimulacion(
                mapa.diseñoCiudad, configuracion, mapa.configuracionesPorInterseccion);
    }

    private Document leerDocumento(Path ruta) throws IOException {
        if (ruta == null || !Files.isRegularFile(ruta)) {
            throw new IOException("No se encontró un archivo XML regular en la ruta indicada.");
        }

        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
            fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder constructor = fabrica.newDocumentBuilder();
            constructor.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            try (var entrada = Files.newInputStream(ruta)) {
                return constructor.parse(entrada);
            }
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("No se pudo leer el XML: " + e.getMessage(), e);
        }
    }

    private MapaXml construirMapa(
            Element raiz,
            int filas,
            int columnas,
            Set<String> idsConfiguracionSemaforo) {
        Element elementoNodos = hijoRequerido(raiz, "nodes");
        Element elementoAristas = hijoRequerido(raiz, "edges");
        Map<String, NodoMapa> nodos = new HashMap<>();
        Map<String, NodoMapa> nodosPorCoordenada = new HashMap<>();
        Map<CoordenadaMapa, String> configuracionesPorInterseccion = new LinkedHashMap<>();

        for (Element elemento : hijos(elementoNodos, "node")) {
            String id = atributoRequerido(elemento, "id");
            int x = enteroNoNegativo(elemento, "x");
            int y = enteroNoNegativo(elemento, "y");
            String tipo = atributoRequerido(elemento, "type").toLowerCase(Locale.ROOT);
            int tipoCelda = switch (tipo) {
                case "spawn" -> 8;
                case "destination" -> 9;
                case "spawn-destination" -> 10;
                case "intersection" -> 7;
                default -> throw error("Tipo de nodo desconocido '" + tipo + "' en el nodo " + id + ".");
            };
            String idConfiguracionSemaforo = null;
            if (tipoCelda == 7) {
                idConfiguracionSemaforo = atributoRequerido(elemento, "phase-config");
                if (!idsConfiguracionSemaforo.contains(idConfiguracionSemaforo)) {
                    throw error("El nodo " + id + " referencia la configuración de semáforo no declarada '"
                            + idConfiguracionSemaforo + "'.");
                }
            } else if (elemento.hasAttribute("phase-config")) {
                throw error("El atributo 'phase-config' solo se permite en nodos de tipo intersection.");
            }

            if (x >= columnas || y >= filas) {
                throw error("El nodo " + id + " está fuera de los límites de <shape>.");
            }

            NodoMapa nodo = new NodoMapa(id, x, y, tipoCelda);
            if (nodos.putIfAbsent(id, nodo) != null) {
                throw error("El identificador de nodo '" + id + "' está repetido.");
            }
            String claveCoordenada = clave(x, y);
            if (nodosPorCoordenada.putIfAbsent(claveCoordenada, nodo) != null) {
                throw error("Hay más de un nodo en las coordenadas (" + x + ", " + y + ").");
            }
            if (idConfiguracionSemaforo != null) {
                configuracionesPorInterseccion.put(new CoordenadaMapa(y, x), idConfiguracionSemaforo);
            }
        }

        List<NodoMapa> spawns = nodos.values().stream().filter(NodoMapa::esSpawn).toList();
        List<NodoMapa> destinos = nodos.values().stream().filter(NodoMapa::esDestino).toList();
        if (spawns.isEmpty()) {
            throw error("El mapa XML debe declarar al menos un nodo de tipo spawn o spawn-destination.");
        }
        if (destinos.isEmpty()) {
            throw error("El mapa XML debe declarar al menos un nodo de tipo destination o spawn-destination.");
        }
        boolean hayParOrigenDestinoDistinto = spawns.stream().anyMatch(origen ->
                destinos.stream().anyMatch(destino -> origen.x != destino.x || origen.y != destino.y));
        if (!hayParOrigenDestinoDistinto) {
            throw error("El mapa debe incluir un spawn y un destino en nodos distintos.");
        }

        int[][] diseñoCiudad = new int[filas][columnas];
        for (NodoMapa nodo : nodos.values()) {
            diseñoCiudad[nodo.y][nodo.x] = nodo.tipoCelda;
        }

        int[][] caminosAsignados = new int[filas][columnas];
        List<Element> aristas = hijos(elementoAristas, "edge");
        if (aristas.isEmpty()) {
            throw error("El mapa XML debe declarar al menos una arista.");
        }
        for (Element arista : aristas) {
            String desdeId = atributoRequerido(arista, "from");
            String hastaId = atributoRequerido(arista, "to");
            NodoMapa desde = nodos.get(desdeId);
            NodoMapa hasta = nodos.get(hastaId);
            if (desde == null || hasta == null) {
                String faltante = desde == null ? desdeId : hastaId;
                throw error("La arista referencia el nodo no declarado '" + faltante + "'.");
            }
            if (desde.x == hasta.x && desde.y == hasta.y) {
                throw error("La arista " + desdeId + " → " + hastaId + " tiene longitud cero.");
            }
            if (desde.x != hasta.x && desde.y != hasta.y) {
                throw error("La arista " + desdeId + " → " + hastaId
                        + " no es recta; las aristas deben ser horizontales o verticales.");
            }

            String tipo = atributoRequerido(arista, "type").toLowerCase(Locale.ROOT);
            if (!"one-way".equals(tipo) && !"two-way".equals(tipo)) {
                throw error("Tipo de arista desconocido '" + tipo + "'.");
            }
            int codigoCalle;
            if ("two-way".equals(tipo)) {
                codigoCalle = desde.y == hasta.y ? 5 : 6;
            } else if (desde.x == hasta.x) {
                codigoCalle = hasta.y > desde.y ? 2 : 1;
            } else {
                codigoCalle = hasta.x > desde.x ? 3 : 4;
            }
            rasterizarArista(desde, hasta, codigoCalle, diseñoCiudad, caminosAsignados);
        }

        return new MapaXml(diseñoCiudad, configuracionesPorInterseccion);
    }

    private void rasterizarArista(
            NodoMapa desde,
            NodoMapa hasta,
            int codigoCalle,
            int[][] diseñoCiudad,
            int[][] caminosAsignados) {
        int pasos = Math.max(Math.abs(hasta.x - desde.x), Math.abs(hasta.y - desde.y));
        int pasoX = Integer.compare(hasta.x, desde.x);
        int pasoY = Integer.compare(hasta.y, desde.y);
        for (int paso = 1; paso < pasos; paso++) {
            int x = desde.x + pasoX * paso;
            int y = desde.y + pasoY * paso;
            if (diseñoCiudad[y][x] == 7
                    || diseñoCiudad[y][x] == 8
                    || diseñoCiudad[y][x] == 9
                    || diseñoCiudad[y][x] == 10) {
                continue;
            }
            if (caminosAsignados[y][x] != 0 && caminosAsignados[y][x] != codigoCalle) {
                throw error("Las aristas se cruzan o se superponen de forma incompatible en ("
                        + x + ", " + y + "); declare un nodo de tipo intersection para los cruces.");
            }
            caminosAsignados[y][x] = codigoCalle;
            diseñoCiudad[y][x] = codigoCalle;
        }
    }

    private Map<String, ConfiguracionSemaforo> leerConfiguracionesSemaforo(Element elementoConfig) {
        List<Element> elementosConfiguracion = hijos(elementoConfig, "phase-config");
        if (elementosConfiguracion.isEmpty()) {
            throw error("Se requiere al menos un elemento <phase-config>.");
        }

        Map<String, ConfiguracionSemaforo> resultado = new LinkedHashMap<>();
        for (Element elementoConfiguracion : elementosConfiguracion) {
            String id = atributoRequerido(elementoConfiguracion, "id");
            int duracionAmarillo = enteroPositivo(elementoConfiguracion, "forced-yellow-duration");
            int duracionRojo = enteroPositivo(elementoConfiguracion, "forced-red-duration");
            int duracionVerdeMinima = atributoPositivoOpcional(
                    elementoConfiguracion,
                    "min-green-duration",
                    ConfiguracionSemaforo.DURACION_VERDE_MINIMA_POR_DEFECTO_SEG);
            int duracionVerdeMaxima = atributoPositivoOpcional(
                    elementoConfiguracion,
                    "max-green-duration",
                    ConfiguracionSemaforo.DURACION_VERDE_MAXIMA_POR_DEFECTO_SEG);
            ConfiguracionSemaforo configuracion = new ConfiguracionSemaforo(
                    leerFases(elementoConfiguracion),
                    duracionAmarillo,
                    duracionRojo,
                    duracionVerdeMinima,
                    duracionVerdeMaxima);
            if (resultado.putIfAbsent(id, configuracion) != null) {
                throw error("El identificador de phase-config '" + id + "' está repetido.");
            }
        }
        return Map.copyOf(resultado);
    }

    private List<FaseSemaforo> leerFases(Element elementoConfiguracion) {
        List<Element> elementosFase = hijos(elementoConfiguracion, "phase");
        if (elementosFase.isEmpty()) {
            throw error("<phase-config> debe contener al menos una <phase>.");
        }

        List<FaseSemaforo> fases = new ArrayList<>();
        Boolean duracionDinamica = null;
        for (Element elementoFase : elementosFase) {
            String duracion = atributoRequerido(elementoFase, "duration");
            boolean faseDinamica = "dynamic".equalsIgnoreCase(duracion);
            if (duracionDinamica != null && duracionDinamica != faseDinamica) {
                throw error("Todas las fases de un phase-config deben tener duración fija o duration=\"dynamic\".");
            }
            duracionDinamica = faseDinamica;
            int duracionSeg = 0;
            if (!faseDinamica) {
                try {
                    duracionSeg = Integer.parseInt(duracion);
                } catch (NumberFormatException e) {
                    throw error("Duración de fase no válida: '" + duracion + "'.");
                }
                if (duracionSeg <= 0) {
                    throw error("La duración de cada fase debe ser mayor que cero.");
                }
            }

            Set<String> direcciones = new LinkedHashSet<>();
            for (String direccion : atributoRequerido(elementoFase, "open-directions").split("\\s+")) {
                if (direccion.isBlank()) {
                    continue;
                }
                String traducida = switch (direccion.toLowerCase(Locale.ROOT)) {
                    case "north" -> "NORTE";
                    case "south" -> "SUR";
                    case "east" -> "ESTE";
                    case "west" -> "OESTE";
                    default -> throw error("Dirección desconocida en open-directions: '" + direccion + "'.");
                };
                if (!direcciones.add(traducida)) {
                    throw error("La dirección '" + direccion + "' está repetida en una fase.");
                }
            }
            if (direcciones.isEmpty()) {
                throw error("open-directions debe incluir al menos una dirección.");
            }
            fases.add(faseDinamica
                    ? FaseSemaforo.conDuracionDinamica(direcciones)
                    : new FaseSemaforo(direcciones, duracionSeg));
        }
        return List.copyOf(fases);
    }

    private static List<Element> hijos(Element padre, String nombre) {
        List<Element> resultado = new ArrayList<>();
        NodeList nodos = padre.getChildNodes();
        for (int i = 0; i < nodos.getLength(); i++) {
            Node nodo = nodos.item(i);
            if (nodo instanceof Element elemento && nombre.equals(elemento.getTagName())) {
                resultado.add(elemento);
            }
        }
        return resultado;
    }

    private static Element hijoRequerido(Element padre, String nombre) {
        List<Element> elementos = hijos(padre, nombre);
        if (elementos.size() != 1) {
            throw error("Se requiere exactamente un elemento <" + nombre + "> dentro de <"
                    + padre.getTagName() + ">.");
        }
        return elementos.get(0);
    }

    private static String atributoRequerido(Element elemento, String nombre) {
        String valor = elemento.getAttribute(nombre).trim();
        if (valor.isEmpty()) {
            throw error("Falta el atributo '" + nombre + "' en <" + elemento.getTagName() + ">.");
        }
        return valor;
    }

    private static int enteroPositivo(Element elemento, String atributo) {
        int valor = entero(elemento, atributo);
        if (valor <= 0) {
            throw error("El atributo '" + atributo + "' debe ser mayor que cero.");
        }
        return valor;
    }

    private static int atributoPositivoOpcional(Element elemento, String atributo, int valorPorDefecto) {
        return elemento.hasAttribute(atributo)
                ? enteroPositivo(elemento, atributo)
                : valorPorDefecto;
    }

    private static int enteroNoNegativo(Element elemento, String atributo) {
        int valor = entero(elemento, atributo);
        if (valor < 0) {
            throw error("El atributo '" + atributo + "' no puede ser negativo.");
        }
        return valor;
    }

    private static int entero(Element elemento, String atributo) {
        String texto = atributoRequerido(elemento, atributo);
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            throw error("El atributo '" + atributo + "' debe ser un entero: '" + texto + "'.");
        }
    }

    private static String clave(int x, int y) {
        return x + ":" + y;
    }

    private static IllegalArgumentException error(String mensaje) {
        return new IllegalArgumentException(mensaje);
    }

    private record NodoMapa(String id, int x, int y, int tipoCelda) {
        private boolean esSpawn() {
            return tipoCelda == 8 || tipoCelda == 10;
        }

        private boolean esDestino() {
            return tipoCelda == 9 || tipoCelda == 10;
        }
    }
    private record MapaXml(
            int[][] diseñoCiudad,
            Map<CoordenadaMapa, String> configuracionesPorInterseccion) {}
}
