package org.example;

import java.util.Set;

public record FaseSemaforo(Set<String> direccionesAbiertas, int duracionSeg) {
    public FaseSemaforo {
        direccionesAbiertas = Set.copyOf(direccionesAbiertas);
        if (direccionesAbiertas.isEmpty()) {
            throw new IllegalArgumentException("Una fase debe abrir al menos una dirección.");
        }
        if (duracionSeg <= 0) {
            throw new IllegalArgumentException("La duración de una fase debe ser mayor que cero.");
        }
    }
}
