package org.example;

import java.util.Set;

public record FaseSemaforo(Set<String> direccionesAbiertas, int duracionSeg, boolean duracionDinamica) {
    public FaseSemaforo(Set<String> direccionesAbiertas, int duracionSeg) {
        this(direccionesAbiertas, duracionSeg, false);
    }

    public static FaseSemaforo conDuracionDinamica(Set<String> direccionesAbiertas) {
        return new FaseSemaforo(direccionesAbiertas, 0, true);
    }

    public FaseSemaforo {
        direccionesAbiertas = Set.copyOf(direccionesAbiertas);
        if (direccionesAbiertas.isEmpty()) {
            throw new IllegalArgumentException("Una fase debe abrir al menos una dirección.");
        }
        if (duracionDinamica && duracionSeg != 0) {
            throw new IllegalArgumentException("Una fase dinámica no puede tener una duración fija.");
        }
        if (!duracionDinamica && duracionSeg <= 0) {
            throw new IllegalArgumentException("La duración de una fase debe ser mayor que cero.");
        }
    }
}
