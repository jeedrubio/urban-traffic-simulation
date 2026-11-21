package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SimuladorMain extends Application {

    @Override
    public void start(Stage primaryStage) {
        int[][] diseñoCiudad = {

                //GLOSARIO
                //* 0 -> ZonaNoAccesible
                // * 1 -> CalleUnSentido: Flujo exclusivo hacia el NORTE (arriba).
                // * 2 -> CalleUnSentido: Flujo exclusivo hacia el SUR (abajo).
                // * 3 -> CalleUnSentido: Flujo exclusivo hacia el ESTE (derecha).
                // * 4 -> CalleUnSentido: Flujo exclusivo hacia el OESTE (izquierda).
                // * 5 -> CalleDobleSentido: Eje HORIZONTAL (permite tráfico Este <-> Oeste).
                // * 6 -> CalleDobleSentido: Eje VERTICAL (permite tráfico Norte <-> Sur).
                // * 7 -> Interseccion (Semáforo)
                // * 8 -> PuntoGeneracion: Destinos tanto como puntos de generación.

                 {0, 0, 0, 0, 8, 0, 0, 0, 0},
                 {0, 7, 3, 3, 7, 3, 3, 7, 0},
                 {0, 1, 0, 0, 6, 0, 0, 2, 0},
                 {0, 1, 0, 0, 6, 0, 0, 2, 0},
                 {8, 7, 5, 5, 7, 5, 5, 7, 8},
                 {0, 1, 0, 0, 6, 0, 0, 2, 0},
                 {0, 1, 0, 0, 6, 0, 0, 2, 0},
                 {0, 7, 4, 4, 7, 4, 4, 7, 0},
                 {0, 0, 0, 0, 8, 0, 0, 0, 0}
        };

        Mapa miCiudad = new Mapa(diseñoCiudad);

        ComparadorRendimiento comparador = new ComparadorRendimiento(miCiudad, 500);
        comparador.ejecutarPrueba();

        List<Vehiculo> vehiculos = new CopyOnWriteArrayList<>();

        PanelCiudadFX panelCiudad = new PanelCiudadFX(miCiudad, vehiculos);
        BorderPane root = new BorderPane(panelCiudad);

        Scene scene = new Scene(root);
        primaryStage.setTitle("Simulador de Tráfico JavaFX - Maven");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> System.exit(0));
        primaryStage.show();

        GeneradorTrafico generador = new GeneradorTrafico(miCiudad, vehiculos);
        Thread hiloGenerador = new Thread(generador);
        hiloGenerador.setDaemon(true);
        hiloGenerador.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}