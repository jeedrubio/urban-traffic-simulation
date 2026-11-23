package org.example;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Semaforo implements Runnable {
    private enum Etapa {
        INACTIVO, VERDE, AMARILLO, ROJO
    }

    private String estadoNorte = "ROJO";
    private String estadoSur = "ROJO";
    private String estadoEste = "ROJO";
    private String estadoOeste = "ROJO";

    private final List<FaseSemaforo> fases;
    private final long duracionAmarilloMs;
    private final long duracionRojoMs;
    private final Queue<CompletableFuture<Integer>> avancesManualesPendientes = new ArrayDeque<>();
    private final List<CompletableFuture<Integer>> cambiosEnTransicion = new ArrayList<>();
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
        this(
                List.of(
                        new FaseSemaforo(Set.of("NORTE", "SUR"), configuracion.getDuracionVerdeSeg()),
                        new FaseSemaforo(Set.of("ESTE", "OESTE"), configuracion.getDuracionVerdeSeg())
                ),
                configuracion.getDuracionAmarilloSeg(),
                configuracion.getDuracionRojoSeg());
    }

    public Semaforo(ConfiguracionSemaforo configuracion) {
        this(
                configuracion.fases(),
                configuracion.duracionAmarilloSeg(),
                configuracion.duracionRojoSeg());
    }

    public Semaforo(List<FaseSemaforo> fases, int amarilloSeg, int rojoSeg) {
        this(fases, amarilloSeg * 1000L, rojoSeg * 1000L);
    }

    Semaforo(List<FaseSemaforo> fases, long duracionAmarilloMs, long duracionRojoMs) {
        if (fases == null || fases.isEmpty()) {
            throw new IllegalArgumentException("El semáforo debe tener al menos una fase.");
        }
        if (duracionAmarilloMs <= 0 || duracionRojoMs <= 0) {
            throw new IllegalArgumentException("Las duraciones de amarillo y rojo deben ser mayores que cero.");
        }
        this.fases = List.copyOf(fases);
        this.duracionAmarilloMs = duracionAmarilloMs;
        this.duracionRojoMs = duracionRojoMs;
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

    @Override
    public void run() {
        int indiceFase = 0;
        CompletableFuture<Integer> cambioDuranteVerde = null;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                FaseSemaforo fase = fases.get(indiceFase);
                cambiarEstados(fase.direccionesAbiertas(), "VERDE", Etapa.VERDE);
                completarCambiosEnTransicion(indiceFase);

                cambioDuranteVerde = esperarDuracionVerde(fase.duracionSeg() * 1000L);
                if (cambioDuranteVerde != null) {
                    synchronized (this) {
                        cambiosEnTransicion.add(cambioDuranteVerde);
                    }
                    cambioDuranteVerde = null;
                }

                cambiarEstados(fase.direccionesAbiertas(), "AMARILLO", Etapa.AMARILLO);
                esperarDuracionProtegida(duracionAmarilloMs);

                cambiarEstados(Set.of(), "ROJO", Etapa.ROJO);
                esperarDuracionProtegida(duracionRojoMs);
                indiceFase = (indiceFase + 1) % fases.size();
            }
        } catch (InterruptedException e) {
            List<CompletableFuture<Integer>> solicitudesInterrumpidas = new ArrayList<>();
            synchronized (this) {
                if (cambioDuranteVerde != null) {
                    solicitudesInterrumpidas.add(cambioDuranteVerde);
                }
                solicitudesInterrumpidas.addAll(avancesManualesPendientes);
                solicitudesInterrumpidas.addAll(cambiosEnTransicion);
                avancesManualesPendientes.clear();
                cambiosEnTransicion.clear();
                cambiarEstados(Set.of(), "ROJO", Etapa.INACTIVO);
            }
            IllegalStateException detenido =
                    new IllegalStateException("El controlador del semáforo se detuvo.", e);
            solicitudesInterrumpidas.forEach(cambio -> cambio.completeExceptionally(detenido));
            Thread.currentThread().interrupt();
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

    private CompletableFuture<Integer> esperarDuracionVerde(long duracionMs) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(duracionMs);
        synchronized (this) {
            while (avancesManualesPendientes.isEmpty()) {
                long restante = limite - System.nanoTime();
                if (restante <= 0) {
                    return null;
                }
                TimeUnit.NANOSECONDS.timedWait(this, restante);
            }
            return avancesManualesPendientes.remove();
        }
    }

    private void esperarDuracionProtegida(long duracionMs) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(duracionMs);
        synchronized (this) {
            long restante;
            while ((restante = limite - System.nanoTime()) > 0) {
                TimeUnit.NANOSECONDS.timedWait(this, restante);
            }
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
