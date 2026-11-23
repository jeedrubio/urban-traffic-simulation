package org.example;

import java.util.List;

public record ConfiguracionSemaforo(
        List<FaseSemaforo> fases,
        int duracionAmarilloSeg,
        int duracionRojoSeg,
        int duracionVerdeMinimaSeg,
        int duracionVerdeMaximaSeg) {

    public static final int DURACION_VERDE_MINIMA_POR_DEFECTO_SEG = 2;
    public static final int DURACION_VERDE_MAXIMA_POR_DEFECTO_SEG = 30;

    public ConfiguracionSemaforo(List<FaseSemaforo> fases, int duracionAmarilloSeg, int duracionRojoSeg) {
        this(
                fases,
                duracionAmarilloSeg,
                duracionRojoSeg,
                DURACION_VERDE_MINIMA_POR_DEFECTO_SEG,
                DURACION_VERDE_MAXIMA_POR_DEFECTO_SEG);
    }

    public ConfiguracionSemaforo {
        fases = List.copyOf(fases);
        if (fases.isEmpty()) {
            throw new IllegalArgumentException("Una configuración de semáforo debe tener al menos una fase.");
        }
        if (duracionAmarilloSeg <= 0 || duracionRojoSeg <= 0) {
            throw new IllegalArgumentException("Las duraciones forzadas de amarillo y rojo deben ser positivas.");
        }
        boolean tieneFasesDinamicas = fases.stream().anyMatch(FaseSemaforo::duracionDinamica);
        boolean tieneFasesFijas = fases.stream().anyMatch(fase -> !fase.duracionDinamica());
        if (tieneFasesDinamicas && tieneFasesFijas) {
            throw new IllegalArgumentException(
                    "Todas las fases de una configuración deben ser fijas o todas dinámicas.");
        }
        if (duracionVerdeMinimaSeg <= 0 || duracionVerdeMaximaSeg < duracionVerdeMinimaSeg) {
            throw new IllegalArgumentException(
                    "La duración verde máxima debe ser igual o mayor que la duración verde mínima, y ambas positivas.");
        }
    }

    public boolean esDinamica() {
        return fases.get(0).duracionDinamica();
    }
}
