package org.example;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// La intersección actúa como semáforo y zona crítica protegida por Mutex
public class Interseccion extends Celda {
    private static final List<String> DIRECCIONES = List.of("NORTE", "SUR", "ESTE", "OESTE");

    public enum Lado {
        NORTE, SUR, ESTE, OESTE
    }

    private record Movimiento(String entrada, String salida) {}

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Semaforo semaforo;
    private final Set<Lado> ladosConectados;
    private volatile Thread hiloSemaforo;
    private volatile Map<String, Celda> celdasAdyacentes = Map.of();
    private final ConcurrentMap<Movimiento, AtomicInteger> colasPorMovimiento = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> vehiculosEnCrucePorDireccion = new ConcurrentHashMap<>();
    private final ConcurrentMap<Vehiculo, String> direccionesDeVehiculosEnCruce = new ConcurrentHashMap<>();

    // Métricas de congestión seguras para múltiples hilos
    private final AtomicInteger vehiculosEnEspera = new AtomicInteger(0);
    private final AtomicInteger maxCongestion = new AtomicInteger(0);
    private final AtomicInteger totalCruces = new AtomicInteger(0);

    public Interseccion(int x, int y, Semaforo semaforo) {
        this(x, y, semaforo, Set.of());
    }

    public Interseccion(int x, int y, Semaforo semaforo, Set<Lado> ladosConectados) {
        super(x, y, true);
        this.semaforo = semaforo;
        this.ladosConectados = Set.copyOf(ladosConectados);
        GestorMetricas.getInstancia().registrarInterseccion(this);
    }

    public int getVehiculosEnEspera() { return vehiculosEnEspera.get(); }
    public int getMaxCongestion() { return maxCongestion.get(); }
    public int getTotalCruces() { return totalCruces.get(); }
    public String getCoordenadaTexto() { return "(" + x + ", " + y + ")"; }
    public Set<Lado> getLadosConectados() { return ladosConectados; }
    public int getCantidadFases() { return semaforo == null ? 0 : semaforo.getCantidadFases(); }

    public CompletableFuture<Integer> solicitarCambioFaseManualmente() {
        return semaforo == null
                ? CompletableFuture.completedFuture(-1)
                : semaforo.solicitarAvanceManual();
    }

    void configurarCeldasAdyacentes(Map<String, Celda> celdasAdyacentes) {
        this.celdasAdyacentes = Map.copyOf(celdasAdyacentes);
        if (semaforo != null) {
            semaforo.establecerEvaluadorPresion(this::calcularPresion);
        }
    }

    void iniciarSemaforo() {
        if (semaforo != null) {
            hiloSemaforo = new Thread(semaforo, "Semaforo-" + x + "-" + y);
            hiloSemaforo.setDaemon(true);
            hiloSemaforo.start();
        }
    }

    // El mapa guarda el hilo para poder detenerlo y esperar su salida al cerrar la ejecución.
    void detenerSemaforo() {
        Thread hilo = hiloSemaforo;
        if (hilo != null) {
            hilo.interrupt();
        }
    }

    void esperarDetencionSemaforo() throws InterruptedException {
        Thread hilo = hiloSemaforo;
        if (hilo != null) {
            hilo.join();
        }
    }

    double calcularPresion(FaseSemaforo fase) {
        double presionTotal = 0;
        Map<String, Celda> adyacentes = celdasAdyacentes;
        // Para cada movimiento servido: cola de entrada menos ocupación aguas abajo.
        for (String direccionEntrada : fase.direccionesAbiertas()) {
            Celda celdaAnterior = adyacentes.get(direccionOpuesta(direccionEntrada));
            if (celdaAnterior == null || !celdaAnterior.puedeSalirHacia(direccionEntrada)) {
                continue;
            }

            // Una intersección puede ser el destino final de una ruta.
            presionTotal += cantidadEnCola(direccionEntrada, null);
            for (String direccionSalida : DIRECCIONES) {
                if (direccionSalida.equals(direccionOpuesta(direccionEntrada))) {
                    continue; // no se modelan giros en U
                }
                Celda celdaSiguiente = adyacentes.get(direccionSalida);
                if (celdaSiguiente == null
                        || !celdaSiguiente.puedeEntrarDesde(direccionOpuesta(direccionSalida))) {
                    continue;
                }
                int colaEntrada = cantidadEnCola(direccionEntrada, direccionSalida);
                int colaSalida = celdaSiguiente.contarVehiculosEnDireccion(direccionSalida);
                presionTotal += colaEntrada - colaSalida;
            }
        }
        return presionTotal;
    }

    @Override
    public boolean puedeSalirHacia(String dir) { return true; }

    @Override
    public boolean puedeEntrarDesde(String dir) { return true; }

    @Override
    public boolean tieneEspacioEntrada(String direccionMovimiento) {
        return !lock.isLocked();
    }

    @Override
    public void entrar(Vehiculo v) throws InterruptedException {
        String direccionEntrada = v.getDireccionMovimiento();
        String direccionSalida = v.getDireccionSalidaInterseccion();
        Movimiento movimiento = new Movimiento(direccionEntrada, direccionSalida);
        AtomicInteger colaMovimiento = colasPorMovimiento.computeIfAbsent(
                movimiento, ignorado -> new AtomicInteger());
        colaMovimiento.incrementAndGet();

        int actuales = vehiculosEnEspera.incrementAndGet();
        maxCongestion.accumulateAndGet(actuales, Math::max);
        boolean contandoEspera = true;

        long inicioEspera = v.ahoraSimuladoMs();
        try {
            // La cola de entrada se conserva, pero no se avanza mientras el reloj esté pausado.
            while (v.estaPausado()
                    || (semaforo != null
                    && !"VERDE".equals(semaforo.getEstadoPorDireccion(direccionEntrada)))) {
                if (!v.esperarActivo(50)) {
                    throw new InterruptedException("La simulación terminó mientras el vehículo esperaba.");
                }
            }

            // La adquisición temporizada permite revisar pausa y cierre sin quedar bloqueado en el mutex.
            while (true) {
                if (!v.esperarSiActiva()) {
                    throw new InterruptedException("La simulación terminó antes de cruzar.");
                }
                if (!lock.tryLock(50, TimeUnit.MILLISECONDS)) {
                    continue;
                }
                if (v.estaPausado()) {
                    lock.unlock();
                    continue;
                }
                break;
            }
            direccionesDeVehiculosEnCruce.put(v, direccionEntrada);
            vehiculosEnCrucePorDireccion
                    .computeIfAbsent(direccionEntrada, ignorado -> new AtomicInteger())
                    .incrementAndGet();
            // El tiempo de espera medido excluye cualquier pausa global de la simulación.
            long tiempoDetenido = v.ahoraSimuladoMs() - inicioEspera;
            if (tiempoDetenido > 0) {
                v.agregarTiempoEsperaSemaforo(tiempoDetenido);
            }

            colaMovimiento.decrementAndGet();
            vehiculosEnEspera.decrementAndGet();
            contandoEspera = false;
            totalCruces.incrementAndGet();
            v.setSeccionActual(0, 0);
        } finally {
            if (contandoEspera) {
                colaMovimiento.decrementAndGet();
                vehiculosEnEspera.decrementAndGet();
            }
        }
    }

    @Override
    public void salir(Vehiculo v) {
        String direccionEntrada = direccionesDeVehiculosEnCruce.remove(v);
        if (direccionEntrada != null) {
            vehiculosEnCrucePorDireccion.get(direccionEntrada).decrementAndGet();
        }
        lock.unlock();
    }

    @Override
    public int contarVehiculosEnDireccion(String direccionMovimiento) {
        AtomicInteger cruzando = vehiculosEnCrucePorDireccion.get(direccionMovimiento);
        int cantidad = cruzando == null ? 0 : cruzando.get();
        for (Map.Entry<Movimiento, AtomicInteger> cola : colasPorMovimiento.entrySet()) {
            if (direccionMovimiento.equals(cola.getKey().entrada())) {
                cantidad += cola.getValue().get();
            }
        }
        return cantidad;
    }

    private int cantidadEnCola(String direccionEntrada, String direccionSalida) {
        AtomicInteger cola = colasPorMovimiento.get(new Movimiento(direccionEntrada, direccionSalida));
        return cola == null ? 0 : cola.get();
    }

    private String direccionOpuesta(String direccion) {
        return switch (direccion) {
            case "NORTE" -> "SUR";
            case "SUR" -> "NORTE";
            case "ESTE" -> "OESTE";
            case "OESTE" -> "ESTE";
            default -> throw new IllegalArgumentException("Dirección desconocida: " + direccion);
        };
    }

    @Override
    public void dibujar(GraphicsContext gc, double px, double py, double tamano) {
        gc.setFill(Color.web("#333742"));
        gc.fillRect(px, py, tamano, tamano);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        gc.strokeRect(px, py, tamano, tamano);

        if (semaforo != null) {
            double radio = tamano * 0.18;
            gc.setFont(Font.font("System", FontWeight.BOLD, 11));
            for (Lado lado : Lado.values()) {
                if (ladosConectados.contains(lado)) {
                    dibujarSemaforoLateral(gc, px, py, tamano, radio, lado);
                }
            }
        }

        // Muestra la cantidad de autos esperando si hay congestión en el cruce
        int cola = vehiculosEnEspera.get();
        if (cola > 0) {
            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("System", FontWeight.BOLD, 11));
            gc.fillText("Q:" + cola, px + tamano - 26, py + 16);
        }
    }

    private void dibujarSemaforoLateral(
            GraphicsContext gc,
            double px,
            double py,
            double tamano,
            double diametro,
            Lado lado) {
        double margen = 4;
        double x;
        double y;
        switch (lado) {
            case NORTE -> {
                x = px + (tamano - diametro) / 2;
                y = py + margen;
            }
            case SUR -> {
                x = px + (tamano - diametro) / 2;
                y = py + tamano - diametro - margen;
            }
            case ESTE -> {
                x = px + tamano - diametro - margen;
                y = py + (tamano - diametro) / 2;
            }
            case OESTE -> {
                x = px + margen;
                y = py + (tamano - diametro) / 2;
            }
            default -> throw new IllegalStateException("Lado de intersección desconocido: " + lado);
        }

        gc.setFill(obtenerColor(semaforo.getEstadoPorDireccion(direccionDeEntrada(lado))));
        gc.fillOval(x, y, diametro, diametro);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeOval(x, y, diametro, diametro);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 9));
        gc.fillText(lado.name().substring(0, 1), x + diametro * 0.32, y + diametro * 0.69);
    }

    private String direccionDeEntrada(Lado lado) {
        return switch (lado) {
            case NORTE -> "SUR";
            case SUR -> "NORTE";
            case ESTE -> "OESTE";
            case OESTE -> "ESTE";
        };
    }

    private Color obtenerColor(String estado) {
        switch (estado) {
            case "VERDE": return Color.LIMEGREEN;
            case "AMARILLO": return Color.GOLD;
            default: return Color.CRIMSON;
        }
    }
}
