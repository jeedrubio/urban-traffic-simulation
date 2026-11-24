package org.example;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

public class Semaforo implements Runnable {
    private static final long INTERVALO_REVISION_PRESION_MS = 100;
    private static final double TOLERANCIA_PRESION = 1.0e-9;

    private enum Etapa {
        INACTIVO, VERDE, AMARILLO, ROJO
    }

    private record DecisionFase(int indiceDestino, CompletableFuture<Integer> cambioManual) {}

    private String estadoNorte = "ROJO";
    private String estadoSur = "ROJO";
    private String estadoEste = "ROJO";
    private String estadoOeste = "ROJO";

    private final List<FaseSemaforo> fases;
    private final long duracionAmarilloMs;
    private final long duracionRojoMs;
    private final long duracionVerdeMinimaMs;
    private final long duracionVerdeMaximaMs;
    private final boolean controlDinamico;
    private final ControlPausaSimulacion controlPausa;
    private final Queue<CompletableFuture<Integer>> avancesManualesPendientes = new ArrayDeque<>();
    private final List<CompletableFuture<Integer>> cambiosEnTransicion = new ArrayList<>();
    private volatile ToDoubleFunction<FaseSemaforo> evaluadorPresion = fase -> 0.0;
    private Etapa etapaActual = Etapa.INACTIVO;

    public Semaforo() {
        this(5, 2, 1);
    }

    public Semaforo(int verdeSeg, int amarilloSeg, int rojoSeg) {
        this(List.of(
                new FaseSemaforo(Set.of("NORTE", "SUR"), verdeSeg),
                new FaseSemaforo(Set.of("ESTE", "OESTE"), verdeSeg)
        ), amarilloSeg, rojoSeg);
    }

    public Semaforo(ConfiguracionSimulacion configuracion) {
        this(configuracion, null);
    }

    Semaforo(ConfiguracionSimulacion configuracion, ControlPausaSimulacion controlPausa) {
        this(
                List.of(
                        new FaseSemaforo(Set.of("NORTE", "SUR"), configuracion.getDuracionVerdeSeg()),
                        new FaseSemaforo(Set.of("ESTE", "OESTE"), configuracion.getDuracionVerdeSeg())
                ),
                configuracion.getDuracionAmarilloSeg() * 1000L,
                configuracion.getDuracionRojoSeg() * 1000L,
                ConfiguracionSemaforo.DURACION_VERDE_MINIMA_POR_DEFECTO_SEG * 1000L,
                ConfiguracionSemaforo.DURACION_VERDE_MAXIMA_POR_DEFECTO_SEG * 1000L,
                controlPausa);
    }

    public Semaforo(ConfiguracionSemaforo configuracion) {
        this(configuracion, null);
    }

    Semaforo(ConfiguracionSemaforo configuracion, ControlPausaSimulacion controlPausa) {
        this(
                configuracion.fases(),
                configuracion.duracionAmarilloSeg() * 1000L,
                configuracion.duracionRojoSeg() * 1000L,
                configuracion.duracionVerdeMinimaSeg() * 1000L,
                configuracion.duracionVerdeMaximaSeg() * 1000L,
                controlPausa);
    }

    public Semaforo(List<FaseSemaforo> fases, int amarilloSeg, int rojoSeg) {
        this(
                fases,
                amarilloSeg * 1000L,
                rojoSeg * 1000L,
                ConfiguracionSemaforo.DURACION_VERDE_MINIMA_POR_DEFECTO_SEG * 1000L,
                ConfiguracionSemaforo.DURACION_VERDE_MAXIMA_POR_DEFECTO_SEG * 1000L);
    }

    Semaforo(List<FaseSemaforo> fases, long duracionAmarilloMs, long duracionRojoMs) {
        this(
                fases,
                duracionAmarilloMs,
                duracionRojoMs,
                ConfiguracionSemaforo.DURACION_VERDE_MINIMA_POR_DEFECTO_SEG * 1000L,
                ConfiguracionSemaforo.DURACION_VERDE_MAXIMA_POR_DEFECTO_SEG * 1000L);
    }

    Semaforo(
            List<FaseSemaforo> fases,
            long duracionAmarilloMs,
            long duracionRojoMs,
            long duracionVerdeMinimaMs,
            long duracionVerdeMaximaMs) {
        this(
                fases,
                duracionAmarilloMs,
                duracionRojoMs,
                duracionVerdeMinimaMs,
                duracionVerdeMaximaMs,
                null);
    }

    Semaforo(
            List<FaseSemaforo> fases,
            long duracionAmarilloMs,
            long duracionRojoMs,
            long duracionVerdeMinimaMs,
            long duracionVerdeMaximaMs,
            ControlPausaSimulacion controlPausa) {
        if (fases == null || fases.isEmpty()) {
            throw new IllegalArgumentException("El semáforo debe tener al menos una fase.");
        }
        if (duracionAmarilloMs <= 0 || duracionRojoMs <= 0) {
            throw new IllegalArgumentException("Las duraciones de amarillo y rojo deben ser mayores que cero.");
        }
        if (duracionVerdeMinimaMs <= 0 || duracionVerdeMaximaMs < duracionVerdeMinimaMs) {
            throw new IllegalArgumentException(
                    "La duración verde máxima debe ser igual o mayor que la duración verde mínima, y ambas positivas.");
        }
        boolean hayFasesDinamicas = fases.stream().anyMatch(FaseSemaforo::duracionDinamica);
        boolean hayFasesFijas = fases.stream().anyMatch(fase -> !fase.duracionDinamica());
        if (hayFasesDinamicas && hayFasesFijas) {
            throw new IllegalArgumentException("No se pueden mezclar fases fijas y dinámicas.");
        }
        this.fases = List.copyOf(fases);
        this.duracionAmarilloMs = duracionAmarilloMs;
        this.duracionRojoMs = duracionRojoMs;
        this.duracionVerdeMinimaMs = duracionVerdeMinimaMs;
        this.duracionVerdeMaximaMs = duracionVerdeMaximaMs;
        this.controlDinamico = hayFasesDinamicas;
        this.controlPausa = controlPausa;
    }

    public synchronized String getEstadoHorizontal() {
        return combinarEstados(estadoEste, estadoOeste);
    }

    public synchronized String getEstadoVertical() {
        return combinarEstados(estadoNorte, estadoSur);
    }

    public synchronized String getEstadoPorDireccion(String direccionMovimiento) {
        return switch (direccionMovimiento) {
            case "NORTE" -> estadoNorte;
            case "SUR" -> estadoSur;
            case "ESTE" -> estadoEste;
            case "OESTE" -> estadoOeste;
            default -> "ROJO";
        };
    }

    /**
     * Solicita pasar a la fase configurada siguiente. La petición interrumpe la
     * espera verde, pero nunca acorta las fases amarilla ni roja de despeje.
     * El resultado es el índice (base cero) de la fase que finalmente se activa.
     */
    public synchronized CompletableFuture<Integer> solicitarAvanceManual() {
        if (fases.size() < 2) {
            return CompletableFuture.completedFuture(-1);
        }
        CompletableFuture<Integer> cambio = new CompletableFuture<>();
        if (etapaActual == Etapa.AMARILLO || etapaActual == Etapa.ROJO) {
            cambiosEnTransicion.add(cambio);
        } else {
            avancesManualesPendientes.add(cambio);
        }
        notifyAll();
        return cambio;
    }

    public int getCantidadFases() {
        return fases.size();
    }

    void establecerEvaluadorPresion(ToDoubleFunction<FaseSemaforo> evaluadorPresion) {
        this.evaluadorPresion = Objects.requireNonNull(evaluadorPresion);
    }

    @Override
    public void run() {
        int indiceFase = 0;
        long[] ultimaActivacionVerde = new long[fases.size()];
        Arrays.fill(ultimaActivacionVerde, ahoraNanos());
        try {
            if (controlDinamico) {
                indiceFase = elegirFaseConMayorPresion(0, -1);
            }
            while (!Thread.currentThread().isInterrupted() && !simulacionFinalizada()) {
                // Si se pausó entre fases, no se activa una nueva luz verde hasta reanudar.
                if (controlPausa != null && controlPausa.estaPausada()) {
                    synchronized (this) {
                        esperarComoMaximo(TimeUnit.MILLISECONDS.toNanos(INTERVALO_REVISION_PRESION_MS));
                    }
                    continue;
                }
                FaseSemaforo fase = fases.get(indiceFase);
                cambiarEstados(fase.direccionesAbiertas(), "VERDE", Etapa.VERDE);
                ultimaActivacionVerde[indiceFase] = ahoraNanos();
                completarCambiosEnTransicion(indiceFase);

                DecisionFase decision = controlDinamico
                        ? esperarSeleccionDinamica(indiceFase, ultimaActivacionVerde)
                        : esperarDuracionFija(indiceFase, fase.duracionSeg() * 1000L);
                if (decision == null) {
                    break;
                }
                indiceFase = iniciarDespeje(indiceFase, decision);
                if (!esperarDuracionProtegida(duracionAmarilloMs)) {
                    break;
                }

                cambiarEstados(Set.of(), "ROJO", Etapa.ROJO);
                if (!esperarDuracionProtegida(duracionRojoMs)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Al cerrar, deja todas las direcciones en rojo y resuelve solicitudes manuales pendientes.
            List<CompletableFuture<Integer>> solicitudesInterrumpidas = new ArrayList<>();
            synchronized (this) {
                solicitudesInterrumpidas.addAll(avancesManualesPendientes);
                solicitudesInterrumpidas.addAll(cambiosEnTransicion);
                avancesManualesPendientes.clear();
                cambiosEnTransicion.clear();
                cambiarEstados(Set.of(), "ROJO", Etapa.INACTIVO);
            }
            IllegalStateException detenido =
                    new IllegalStateException("El controlador del semáforo se detuvo.");
            solicitudesInterrumpidas.forEach(cambio -> cambio.completeExceptionally(detenido));
        }
    }

    private DecisionFase esperarDuracionFija(int indiceActual, long duracionMs) throws InterruptedException {
        // El límite usa tiempo simulado; una pausa no acorta la fase verde.
        long limite = ahoraNanos() + TimeUnit.MILLISECONDS.toNanos(duracionMs);
        synchronized (this) {
            while (!simulacionFinalizada()) {
                if (controlPausa != null && controlPausa.estaPausada()) {
                    TimeUnit.MILLISECONDS.timedWait(this, INTERVALO_REVISION_PRESION_MS);
                    continue;
                }
                if (!avancesManualesPendientes.isEmpty()) {
                    return new DecisionFase(
                            (indiceActual + 1) % fases.size(),
                            avancesManualesPendientes.remove());
                }
                long restante = limite - ahoraNanos();
                if (restante <= 0) {
                    return new DecisionFase((indiceActual + 1) % fases.size(), null);
                }
                TimeUnit.NANOSECONDS.timedWait(
                        this,
                        Math.min(restante, TimeUnit.MILLISECONDS.toNanos(INTERVALO_REVISION_PRESION_MS)));
            }
            return null;
        }
    }

    private DecisionFase esperarSeleccionDinamica(
            int indiceActual,
            long[] ultimaActivacionVerde) throws InterruptedException {
        // El controlador dinámico evalúa presión usando la misma referencia temporal pausada.
        long inicioVerde = ahoraNanos();
        long duracionVerdeMaximaNanos = TimeUnit.MILLISECONDS.toNanos(duracionVerdeMaximaMs);
        long duracionVerdeMinimaNanos = TimeUnit.MILLISECONDS.toNanos(duracionVerdeMinimaMs);

        while (true) {
            if (simulacionFinalizada()) {
                return null;
            }
            if (controlPausa != null && controlPausa.estaPausada()) {
                synchronized (this) {
                    esperarComoMaximo(TimeUnit.MILLISECONDS.toNanos(INTERVALO_REVISION_PRESION_MS));
                }
                continue;
            }

            long ahora = ahoraNanos();
            long transcurrido = ahora - inicioVerde;
            synchronized (this) {
                if (!avancesManualesPendientes.isEmpty()) {
                    return new DecisionFase(
                            (indiceActual + 1) % fases.size(),
                            avancesManualesPendientes.remove());
                }
                if (transcurrido < duracionVerdeMinimaNanos) {
                    esperarComoMaximo(duracionVerdeMinimaNanos - transcurrido);
                    continue;
                }
            }

            if (fases.size() > 1) {
                // Evita que una fase quede sin servicio indefinidamente.
                int faseAtrasada = elegirFaseMasAtrasada(
                        indiceActual,
                        ultimaActivacionVerde,
                        ahora,
                        duracionVerdeMaximaNanos);
                if (faseAtrasada >= 0) {
                    return new DecisionFase(faseAtrasada, null);
                }

                double[] presiones = calcularPresiones();
                int mejorFase = elegirFaseConMayorPresion(indiceActual, -1, presiones);
                if (mejorFase != indiceActual
                        && presiones[mejorFase] > presiones[indiceActual] + TOLERANCIA_PRESION) {
                    return new DecisionFase(mejorFase, null);
                }
                if (transcurrido >= duracionVerdeMaximaNanos) {
                    int siguienteMejor = elegirFaseConMayorPresion(
                            (indiceActual + 1) % fases.size(),
                            indiceActual,
                            presiones);
                    return new DecisionFase(siguienteMejor, null);
                }
            }

            synchronized (this) {
                if (!avancesManualesPendientes.isEmpty()) {
                    return new DecisionFase(
                            (indiceActual + 1) % fases.size(),
                            avancesManualesPendientes.remove());
                }
                long restanteMaximo = duracionVerdeMaximaNanos - (ahoraNanos() - inicioVerde);
                esperarComoMaximo(TimeUnit.MILLISECONDS.toNanos(INTERVALO_REVISION_PRESION_MS));
                if (restanteMaximo <= 0) {
                    continue;
                }
            }
        }
    }

    private void esperarComoMaximo(long nanosegundos) throws InterruptedException {
        if (nanosegundos > 0) {
            TimeUnit.NANOSECONDS.timedWait(
                    this,
                    Math.min(nanosegundos, TimeUnit.MILLISECONDS.toNanos(INTERVALO_REVISION_PRESION_MS)));
        }
    }

    private long ahoraNanos() {
        return controlPausa == null ? System.nanoTime() : controlPausa.ahoraNanos();
    }

    private boolean simulacionFinalizada() {
        return controlPausa != null && controlPausa.estaFinalizada();
    }

    private double[] calcularPresiones() {
        double[] presiones = new double[fases.size()];
        ToDoubleFunction<FaseSemaforo> evaluador = evaluadorPresion;
        for (int i = 0; i < fases.size(); i++) {
            double presion = evaluador.applyAsDouble(fases.get(i));
            presiones[i] = Double.isFinite(presion) ? presion : 0.0;
        }
        return presiones;
    }

    private int elegirFaseConMayorPresion(int inicioDesempate, int excluir) {
        return elegirFaseConMayorPresion(inicioDesempate, excluir, calcularPresiones());
    }

    private int elegirFaseConMayorPresion(int inicioDesempate, int excluir, double[] presiones) {
        int mejor = -1;
        double mayorPresion = Double.NEGATIVE_INFINITY;
        int inicio = Math.floorMod(inicioDesempate, fases.size());
        for (int desplazamiento = 0; desplazamiento < fases.size(); desplazamiento++) {
            int indice = (inicio + desplazamiento) % fases.size();
            if (indice == excluir) {
                continue;
            }
            if (mejor < 0 || presiones[indice] > mayorPresion + TOLERANCIA_PRESION) {
                mejor = indice;
                mayorPresion = presiones[indice];
            }
        }
        return mejor < 0 ? inicio : mejor;
    }

    private int elegirFaseMasAtrasada(
            int indiceActual,
            long[] ultimaActivacionVerde,
            long ahora,
            long esperaMaximaNanos) {
        int faseMasAtrasada = -1;
        long mayorEspera = esperaMaximaNanos;
        for (int desplazamiento = 1; desplazamiento < fases.size(); desplazamiento++) {
            int indice = (indiceActual + desplazamiento) % fases.size();
            long espera = ahora - ultimaActivacionVerde[indice];
            if (espera >= mayorEspera) {
                faseMasAtrasada = indice;
                mayorEspera = espera;
            }
        }
        return faseMasAtrasada;
    }

    /**
     * Empieza el despeje de forma atómica respecto a nuevos clics. Un clic que
     * llegue antes de amarillo todavía puede determinar la siguiente fase.
     */
    private int iniciarDespeje(int indiceActual, DecisionFase decision) {
        synchronized (this) {
            int destino = decision.indiceDestino();
            if (decision.cambioManual() != null) {
                cambiosEnTransicion.add(decision.cambioManual());
            } else if (!avancesManualesPendientes.isEmpty()) {
                destino = (indiceActual + 1) % fases.size();
                cambiosEnTransicion.add(avancesManualesPendientes.remove());
            }
            cambiarEstados(fases.get(indiceActual).direccionesAbiertas(), "AMARILLO", Etapa.AMARILLO);
            return destino;
        }
    }

    private void completarCambiosEnTransicion(int indiceFase) {
        List<CompletableFuture<Integer>> cambios;
        synchronized (this) {
            cambios = List.copyOf(cambiosEnTransicion);
            cambiosEnTransicion.clear();
        }
        cambios.forEach(cambio -> cambio.complete(indiceFase));
    }

    private boolean esperarDuracionProtegida(long duracionMs) throws InterruptedException {
        // Amarillo y todo-rojo también conservan su duración pendiente durante la pausa.
        long limite = ahoraNanos() + TimeUnit.MILLISECONDS.toNanos(duracionMs);
        synchronized (this) {
            long restante;
            while (!simulacionFinalizada() && (restante = limite - ahoraNanos()) > 0) {
                TimeUnit.NANOSECONDS.timedWait(
                        this,
                        Math.min(restante, TimeUnit.MILLISECONDS.toNanos(INTERVALO_REVISION_PRESION_MS)));
            }
            return !simulacionFinalizada();
        }
    }

    private synchronized void cambiarEstados(Set<String> direcciones, String estadoAbierto, Etapa etapa) {
        estadoNorte = "ROJO";
        estadoSur = "ROJO";
        estadoEste = "ROJO";
        estadoOeste = "ROJO";
        for (String direccion : direcciones) {
            switch (direccion) {
                case "NORTE" -> estadoNorte = estadoAbierto;
                case "SUR" -> estadoSur = estadoAbierto;
                case "ESTE" -> estadoEste = estadoAbierto;
                case "OESTE" -> estadoOeste = estadoAbierto;
                default -> throw new IllegalArgumentException("Dirección de semáforo desconocida: " + direccion);
            }
        }
        etapaActual = etapa;
    }

    private String combinarEstados(String primero, String segundo) {
        if (primero.equals(segundo)) {
            return primero;
        }
        if ("VERDE".equals(primero) || "VERDE".equals(segundo)) {
            return "VERDE";
        }
        if ("AMARILLO".equals(primero) || "AMARILLO".equals(segundo)) {
            return "AMARILLO";
        }
        return "ROJO";
    }
}
