package org.example;

import java.util.List;
import java.util.Set;

public class Semaforo implements Runnable {
    private String estadoNorte = "ROJO";
    private String estadoSur = "ROJO";
    private String estadoEste = "ROJO";
    private String estadoOeste = "ROJO";

    private final List<FaseSemaforo> fases;
    private final long duracionAmarilloMs;
    private final long duracionRojoMs;

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
        if (fases == null || fases.isEmpty()) {
            throw new IllegalArgumentException("El semáforo debe tener al menos una fase.");
        }
        if (amarilloSeg <= 0 || rojoSeg <= 0) {
            throw new IllegalArgumentException("Las duraciones de amarillo y rojo deben ser mayores que cero.");
        }
        this.fases = List.copyOf(fases);
        this.duracionAmarilloMs = amarilloSeg * 1000L;
        this.duracionRojoMs = rojoSeg * 1000L;
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

    @Override
    public void run() {
        try {
            int indiceFase = 0;
            while (!Thread.currentThread().isInterrupted()) {
                FaseSemaforo fase = fases.get(indiceFase);
                cambiarEstados(fase.direccionesAbiertas(), "VERDE");
                Thread.sleep(fase.duracionSeg() * 1000L);

                cambiarEstados(fase.direccionesAbiertas(), "AMARILLO");
                Thread.sleep(duracionAmarilloMs);

                cambiarEstados(Set.of(), "ROJO");
                Thread.sleep(duracionRojoMs);
                indiceFase = (indiceFase + 1) % fases.size();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void cambiarEstados(Set<String> direcciones, String estadoAbierto) {
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
