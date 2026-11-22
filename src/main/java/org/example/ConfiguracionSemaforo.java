package org.example;

import java.util.List;

public record ConfiguracionSemaforo(
        List<FaseSemaforo> fases,
        int duracionAmarilloSeg,
        int duracionRojoSeg) {

    public ConfiguracionSemaforo {
        fases = List.copyOf(fases);
        if (fases.isEmpty()) {
            throw new IllegalArgumentException("Una configuración de semáforo debe tener al menos una fase.");
        }
        if (duracionAmarilloSeg <= 0 || duracionRojoSeg <= 0) {
            throw new IllegalArgumentException("Las duraciones forzadas de amarillo y rojo deben ser positivas.");
        }
    }
}
