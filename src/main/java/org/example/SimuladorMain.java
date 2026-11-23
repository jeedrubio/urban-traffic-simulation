package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimuladorMain extends Application {

    // El modo manual conserva el mapa integrado que ya utilizaba la aplicación.
    private final int[][] diseñoCiudad = {
            // 0 -> ZonaNoAccesible
            // 1 -> CalleUnSentido: NORTE
            // 2 -> CalleUnSentido: SUR
            // 3 -> CalleUnSentido: ESTE
            // 4 -> CalleUnSentido: OESTE
            // 5 -> CalleDobleSentido: HORIZONTAL
            // 6 -> CalleDobleSentido: VERTICAL
            // 7 -> Interseccion (Semáforo)
            // 8 -> Spawn, 9 -> Destino, 10 -> Spawn y destino
            {0, 0, 0, 0, 10, 0, 0, 0, 0},
            {0, 7, 3, 3, 7, 3, 3, 7, 0},
            {0, 1, 0, 0, 6, 0, 0, 2, 0},
            {0, 1, 0, 0, 6, 0, 0, 2, 0},
            {10, 7, 5, 5, 7, 5, 5, 7, 10},
            {0, 1, 0, 0, 6, 0, 0, 2, 0},
            {0, 1, 0, 0, 6, 0, 0, 2, 0},
            {0, 7, 4, 4, 7, 4, 4, 7, 0},
            {0, 0, 0, 0, 10, 0, 0, 0, 0}
    };

    private final CargadorConfiguracionXml cargadorXml = new CargadorConfiguracionXml();
    private DefinicionSimulacion definicionXml;
    private Path rutaXml;
    private boolean iniciarEnModoXml;
    private String errorCargaInicial;

    @Override
    public void start(Stage primaryStage) {
        List<String> argumentos = getParameters().getRaw();
        if (!argumentos.isEmpty()) {
            iniciarEnModoXml = true;
            if (argumentos.size() > 1) {
                errorCargaInicial = "Indica como máximo un archivo XML como argumento.";
            } else {
                cargarXmlInicial(argumentos.get(0));
            }
        }
        mostrarMenuConfiguracion(primaryStage);
    }

    private void cargarXmlInicial(String ruta) {
        try {
            rutaXml = Path.of(ruta).toAbsolutePath().normalize();
            definicionXml = cargadorXml.cargar(rutaXml);
        } catch (IOException | IllegalArgumentException ex) {
            definicionXml = null;
            errorCargaInicial = "No se pudo cargar el archivo XML: " + ex.getMessage();
        }
    }

    private void mostrarMenuConfiguracion(Stage stage) {
        VBox contenedor = new VBox(14);
        contenedor.setPadding(new Insets(22));
        contenedor.setStyle("-fx-background-color: #232730;");

        Label titulo = new Label("Configuración de Simulación de Tráfico");
        titulo.setFont(Font.font("System", FontWeight.BOLD, 18));
        titulo.setTextFill(Color.WHITE);

        RadioButton rbManual = new RadioButton("Configurar manualmente");
        RadioButton rbArchivoXml = new RadioButton("Cargar configuración XML");
        rbManual.setTextFill(Color.WHITE);
        rbArchivoXml.setTextFill(Color.WHITE);
        ToggleGroup grupoFuente = new ToggleGroup();
        rbManual.setToggleGroup(grupoFuente);
        rbArchivoXml.setToggleGroup(grupoFuente);
        rbManual.setSelected(!iniciarEnModoXml);
        rbArchivoXml.setSelected(iniciarEnModoXml);
        HBox opcionesFuente = new HBox(16, rbManual, rbArchivoXml);

        Button btnSeleccionarArchivo = new Button("Elegir archivo XML…");
        Label lblArchivo = new Label();
        lblArchivo.setTextFill(Color.LIGHTGRAY);
        lblArchivo.setWrapText(true);
        HBox selectorArchivo = new HBox(10, btnSeleccionarArchivo, lblArchivo);
        selectorArchivo.setAlignment(Pos.CENTER_LEFT);


        TextField txtVehiculos = new TextField();
        txtVehiculos.setPromptText("Número de vehículos");

        TextField txtVerde = new TextField();
        txtVerde.setPromptText("Verde");

        TextField txtAmarillo = new TextField();
        txtAmarillo.setPromptText("Amarillo");

        TextField txtRojo = new TextField();
        txtRojo.setPromptText("Rojo");

        HBox boxSemaforos = new HBox(10, txtVerde, txtAmarillo, txtRojo);

        Label lblError = new Label();
        lblError.setTextFill(Color.SALMON);
        lblError.setWrapText(true);

        Button btnIniciar = new Button("Iniciar Simulación");
        btnIniciar.setStyle(
                "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18;");
        HBox boxBotones = new HBox(12, btnIniciar);
        boxBotones.setAlignment(Pos.CENTER_RIGHT);

        Runnable actualizarModo = () -> {
            boolean modoXml = rbArchivoXml.isSelected();
            int[][] mapaActivo = modoXml
                    ? (definicionXml == null ? null : definicionXml.getDiseñoCiudad())
                    : diseñoCiudad;

            boolean tieneParOrigenDestino = mapaActivo != null
                    && tieneParSpawnDestinoDistinto(mapaActivo);

            txtVehiculos.setDisable(modoXml);
            txtVerde.setDisable(modoXml);
            txtAmarillo.setDisable(modoXml);
            txtRojo.setDisable(modoXml);

            if (modoXml) {
                btnSeleccionarArchivo.setDisable(false);
                lblArchivo.setText(rutaXml == null
                        ? "Selecciona un archivo XML para cargar el mapa y la configuración."
                        : "Archivo: " + rutaXml);
                if (definicionXml != null) {
                    ConfiguracionSimulacion configXml = definicionXml.getConfiguracion();
                    int[][] mapaXml = definicionXml.getDiseñoCiudad();
                    String idsConfiguracion = String.join(", ",
                            configXml.getConfiguracionesSemaforo().keySet().stream().sorted().toList());
                }
            } else {
                lblArchivo.setText("Modo manual: se utilizará el mapa integrado en el simulador.");
                btnSeleccionarArchivo.setDisable(true);
            }

            btnIniciar.setDisable(!tieneParOrigenDestino || (modoXml && definicionXml == null));
        };

        rbManual.setOnAction(e -> {
            lblError.setText("");
            actualizarModo.run();
        });
        rbArchivoXml.setOnAction(e -> {
            lblError.setText("");
            actualizarModo.run();
        });

        btnSeleccionarArchivo.setOnAction(e -> {
            FileChooser selector = new FileChooser();
            selector.setTitle("Seleccionar configuración XML");
            selector.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Archivos XML", "*.xml"));
            if (rutaXml != null && rutaXml.getParent() != null && rutaXml.getParent().toFile().isDirectory()) {
                selector.setInitialDirectory(rutaXml.getParent().toFile());
            }
            File archivo = selector.showOpenDialog(stage);
            if (archivo == null) {
                return;
            }
            try {
                Path nuevaRuta = archivo.toPath().toAbsolutePath().normalize();
                DefinicionSimulacion nuevaDefinicion = cargadorXml.cargar(nuevaRuta);
                rutaXml = nuevaRuta;
                definicionXml = nuevaDefinicion;
                iniciarEnModoXml = true;
                rbArchivoXml.setSelected(true);
                lblError.setText("");
                actualizarModo.run();
            } catch (IOException | IllegalArgumentException ex) {
                lblError.setText("No se pudo cargar el archivo XML: " + ex.getMessage());
            }
        });

        actualizarModo.run();
        if (errorCargaInicial != null) {
            lblError.setText(errorCargaInicial);
        }

        btnIniciar.setOnAction(e -> {
            lblError.setText("");
            boolean modoXml = rbArchivoXml.isSelected();
            if (modoXml && definicionXml == null) {
                lblError.setText("Selecciona un archivo XML válido o cambia al modo manual.");
                return;
            }
            int[][] mapaActivo = modoXml ? definicionXml.getDiseñoCiudad() : diseñoCiudad;
            if (!tieneParSpawnDestinoDistinto(mapaActivo)) {
                lblError.setText("Se requiere un spawn y un destino en puntos distintos para iniciar.");
                return;
            }

            ConfiguracionSimulacion config = modoXml
                    ? definicionXml.getConfiguracion()
                    : new ConfiguracionSimulacion();

            try {
                if (!modoXml && !txtVehiculos.getText().trim().isEmpty()) {
                    int cantidad = Integer.parseInt(txtVehiculos.getText().trim());
                    if (cantidad < 20 || cantidad > 200) {
                        lblError.setText("La cantidad de vehículos debe ser un entero entre 20 y 200.");
                        return;
                    }
                    config.setCantidadVehiculos(cantidad);
                }

                if (!modoXml && !txtVerde.getText().trim().isEmpty()) {
                    int verde = Integer.parseInt(txtVerde.getText().trim());
                    if (verde <= 0) {
                        lblError.setText("La duración de la luz VERDE debe ser mayor a 0 segundos.");
                        return;
                    }
                    config.setDuracionVerdeSeg(verde);
                }

                if (!modoXml && !txtAmarillo.getText().trim().isEmpty()) {
                    int amarillo = Integer.parseInt(txtAmarillo.getText().trim());
                    if (amarillo <= 0) {
                        lblError.setText("La duración de la luz AMARILLO debe ser mayor a 0 segundos.");
                        return;
                    }
                    config.setDuracionAmarilloSeg(amarillo);
                }

                if (!modoXml && !txtRojo.getText().trim().isEmpty()) {
                    int rojo = Integer.parseInt(txtRojo.getText().trim());
                    if (rojo <= 0) {
                        lblError.setText("La duración de la luz ROJO debe ser mayor a 0 segundos.");
                        return;
                    }
                    config.setDuracionRojoSeg(rojo);
                }

                iniciarSimulacion(stage, config, mapaActivo, modoXml ? definicionXml : null);
            } catch (NumberFormatException ex) {
                lblError.setText("Ingresa únicamente números enteros válidos en los campos numéricos.");
            }
        });

        contenedor.getChildren().addAll(
                titulo,
                opcionesFuente,
                selectorArchivo,
                crearEtiqueta("Cantidad de vehículos (20 - 200):"),
                txtVehiculos,
                crearEtiqueta("Duración de luces del semáforo (segundos):"),
                boxSemaforos,
                lblError,
                boxBotones
        );

        ScrollPane desplazamiento = new ScrollPane(contenedor);
        desplazamiento.setFitToWidth(true);
        desplazamiento.setStyle("-fx-background: #232730; -fx-background-color: #232730;");
        Scene menuScene = new Scene(desplazamiento, 540, 420);
        stage.setTitle("Menú Inicial - Simulador de Tráfico");
        stage.setScene(menuScene);
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();
    }

    private void iniciarSimulacion(
            Stage stage,
            ConfiguracionSimulacion config,
            int[][] diseño,
            DefinicionSimulacion definicionXml) {
        ControlPausaSimulacion controlPausa = new ControlPausaSimulacion();
        // Reiniciar el gestor ANTES de crear el mapa para que registre todas las intersecciones
        GestorMetricas gestor = GestorMetricas.getInstancia();
        gestor.reiniciar(config.getCantidadVehiculos());

        Mapa miCiudad = definicionXml == null
                ? new Mapa(diseño, config, controlPausa)
                : new Mapa(definicionXml, controlPausa);

        boolean hayRutaPosible = miCiudad.getPuntosSpawn().stream().anyMatch(origen ->
                miCiudad.getPuntosDestino().stream().anyMatch(destino ->
                        origen != destino && miCiudad.calcularRutaDijkstra(origen, destino) != null));
        if (!hayRutaPosible) {
            controlPausa.finalizar();
            miCiudad.detenerSemaforos();
            Alert alert = new Alert(
                    Alert.AlertType.ERROR,
                    "Se requiere al menos una ruta alcanzable entre un spawn y un destino distintos.");
            alert.showAndWait();
            return;
        }

        ComparadorRendimiento comparador = new ComparadorRendimiento(miCiudad, 500);
        comparador.ejecutarPrueba();

        List<Vehiculo> vehiculos = new CopyOnWriteArrayList<>();

        GestorVehiculos gestorVehiculos =
                new GestorVehiculos(miCiudad, vehiculos, gestor, controlPausa);
        PanelCiudadFX panelCiudad = new PanelCiudadFX(miCiudad, vehiculos);
        ScrollPane vistaMapa = new ScrollPane(panelCiudad);
        vistaMapa.setPannable(true);
        BorderPane root = new BorderPane(vistaMapa);

        // Panel lateral de monitoreo de métricas en tiempo real
        VBox panelMetricas = new VBox(12);
        panelMetricas.setPadding(new Insets(16));
        panelMetricas.setPrefWidth(250);
        panelMetricas.setStyle("-fx-background-color: #1e222a; -fx-border-color: #3a3f4b; -fx-border-width: 0 0 0 1;");

        Label lblTituloMetricas = new Label("Métricas en Vivo");
        lblTituloMetricas.setFont(Font.font("System", FontWeight.BOLD, 16));
        lblTituloMetricas.setTextFill(Color.WHITE);

        Label lblLlegados = crearDatoPanel("Llegados: 0 / " + config.getCantidadVehiculos());
        Label lblPrimero = crearDatoPanel("Primero: Esperando...");
        Label lblPromedio = crearDatoPanel("Tiempo prom.: 0.00 s");
        Label lblEspera = crearDatoPanel("Espera semáf.: 0.0%");
        Label lblCongestion = crearDatoPanel("Congestión máx.: 0");
        Label lblEstado = crearDatoPanel("Estado: Simulando...");
        lblEstado.setTextFill(Color.LIGHTGREEN);

        Button btnPausa = new Button("Pausar simulación");
        btnPausa.setMaxWidth(Double.MAX_VALUE);
        btnPausa.setStyle(
                "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");

        Button btnFinalizar = new Button("Finalizar y volver al menú");
        btnFinalizar.setMaxWidth(Double.MAX_VALUE);
        btnFinalizar.setStyle(
                "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");

        Button btnVerTabla = new Button("Ver Tabla de Métricas");
        btnVerTabla.setMaxWidth(Double.MAX_VALUE);
        btnVerTabla.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");
        btnVerTabla.setOnAction(e -> VentanaMetricasFX.mostrar(gestor));

        Button btnAutoAleatorio = new Button("Agregar auto aleatorio");
        btnAutoAleatorio.setMaxWidth(Double.MAX_VALUE);
        btnAutoAleatorio.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");

        Button btnElegirRuta = new Button("Elegir ruta en el mapa");
        btnElegirRuta.setMaxWidth(Double.MAX_VALUE);
        btnElegirRuta.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");

        Label lblInstruccionRuta = crearDatoPanel("Los autos añadidos se suman a los configurados.");
        lblInstruccionRuta.setTextFill(Color.LIGHTBLUE);

        Label lblFeedbackFase = crearDatoPanel("Haz click sobre un cruce para cambiar su fase.");
        lblFeedbackFase.setTextFill(Color.LIGHTGREEN);

        boolean[] seleccionRutaActiva = {false};
        PuntoGeneracion[] origenSeleccionado = {null};
        List<PuntoGeneracion> destinosSeleccionables = new ArrayList<>();
        Runnable cancelarSeleccionRuta = () -> {
            seleccionRutaActiva[0] = false;
            origenSeleccionado[0] = null;
            destinosSeleccionables.clear();
            panelCiudad.limpiarSeleccionSpawns();
            btnElegirRuta.setText("Elegir ruta en el mapa");
        };

        btnAutoAleatorio.setOnAction(e -> {
            if (gestor.estaFinalizada()) {
                lblInstruccionRuta.setText("La simulación ya finalizó.");
                return;
            }
            if (seleccionRutaActiva[0]) {
                cancelarSeleccionRuta.run();
            }
            lblInstruccionRuta.setText("Buscando una ruta aleatoria...");
            observarCreacionVehiculo(
                    gestorVehiculos.crearVehiculoAleatorio(true),
                    lblInstruccionRuta,
                    gestor,
                    btnAutoAleatorio,
                    btnElegirRuta,
                    cancelarSeleccionRuta);
        });

        btnElegirRuta.setOnAction(e -> {
            if (gestor.estaFinalizada()) {
                lblInstruccionRuta.setText("La simulación ya finalizó.");
                return;
            }
            if (seleccionRutaActiva[0]) {
                cancelarSeleccionRuta.run();
                lblInstruccionRuta.setText("Selección cancelada.");
                return;
            }
            seleccionRutaActiva[0] = true;
            origenSeleccionado[0] = null;
            destinosSeleccionables.clear();
            panelCiudad.mostrarSeleccionExtremosRuta(miCiudad.getPuntosSpawn(), List.of(), null);
            btnElegirRuta.setText("Cancelar selección");
            lblInstruccionRuta.setText("Haz clic en un spawn que será el origen.");
        });

        panelCiudad.setAlHacerClicEnPuntoRuta(punto -> {
            if (!seleccionRutaActiva[0] || gestor.estaFinalizada()) {
                return;
            }
            if (origenSeleccionado[0] == null) {
                if (!punto.esSpawn()) {
                    lblInstruccionRuta.setText("Ese punto no genera vehículos. Elige un spawn como origen.");
                    return;
                }
                origenSeleccionado[0] = punto;
                panelCiudad.mostrarSeleccionExtremosRuta(List.of(), List.of(), punto);
                lblInstruccionRuta.setText("Buscando destinos alcanzables desde " + coordenada(punto) + "...");
                gestorVehiculos.obtenerDestinosAlcanzables(punto).whenComplete((alcanzables, error) ->
                        Platform.runLater(() -> {
                            if (!seleccionRutaActiva[0] || origenSeleccionado[0] != punto) {
                                return;
                            }
                            if (error != null || alcanzables.isEmpty()) {
                                origenSeleccionado[0] = null;
                                panelCiudad.mostrarSeleccionExtremosRuta(
                                        miCiudad.getPuntosSpawn(), List.of(), null);
                                lblInstruccionRuta.setText(
                                        "Ese origen no tiene destinos alcanzables. Elige otro spawn.");
                                return;
                            }
                            destinosSeleccionables.clear();
                            destinosSeleccionables.addAll(alcanzables);
                            panelCiudad.mostrarSeleccionExtremosRuta(
                                    List.of(), destinosSeleccionables, punto);
                            lblInstruccionRuta.setText("Origen " + coordenada(punto)
                                    + ". Haz clic en un destino resaltado.");
                        }));
                return;
            }

            if (destinosSeleccionables.isEmpty()) {
                lblInstruccionRuta.setText("Calculando destinos; espera un momento...");
                return;
            }

            if (!destinosSeleccionables.contains(punto)) {
                lblInstruccionRuta.setText("Elige uno de los destinos resaltados.");
                return;
            }
            PuntoGeneracion origen = origenSeleccionado[0];
            cancelarSeleccionRuta.run();
            lblInstruccionRuta.setText("Creando auto para la ruta " + coordenada(origen)
                    + " → " + coordenada(punto) + "...");
            observarCreacionVehiculo(
                    gestorVehiculos.crearVehiculoEnRuta(origen, punto, true),
                    lblInstruccionRuta,
                    gestor,
                    btnAutoAleatorio,
                    btnElegirRuta,
                    cancelarSeleccionRuta);
        });

        panelCiudad.setAlHacerClicEnInterseccion(interseccion -> {
            if (gestor.estaFinalizada()) {
                return;
            }
            if (interseccion.getCantidadFases() < 2) {
                lblFeedbackFase.setText("La intersección " + interseccion.getCoordenadaTexto()
                        + " solo tiene una fase configurada.");
                return;
            }

            lblFeedbackFase.setText("Cambio solicitado en " + interseccion.getCoordenadaTexto()
                    + "; se completarán las fases amarilla y roja.");
            interseccion.solicitarCambioFaseManualmente().whenComplete((indiceFase, error) ->
                    Platform.runLater(() -> {
                        if (error != null) {
                            lblFeedbackFase.setText("No se pudo cambiar la fase: " + error.getMessage());
                        } else if (indiceFase != null && indiceFase >= 0) {
                            lblFeedbackFase.setText("Intersección " + interseccion.getCoordenadaTexto()
                                    + ": fase " + (indiceFase + 1)
                                    + " activa; continúa el control configurado.");
                        } else {
                            lblFeedbackFase.setText("La intersección no tiene una fase alternativa.");
                        }
                    }));
        });

        panelMetricas.getChildren().addAll(
                lblTituloMetricas,
                lblEstado,
                lblLlegados,
                lblPrimero,
                lblPromedio,
                lblEspera,
                lblCongestion,
                btnVerTabla,
                btnPausa,
                btnFinalizar,
                new Separator(),
                btnAutoAleatorio,
                btnElegirRuta,
                lblInstruccionRuta,
                lblFeedbackFase
        );
        root.setRight(panelMetricas);

        Runnable refrescarPanelLateral = () -> {
            lblLlegados.setText("Llegados: " + gestor.getTotalLlegados() + " / " + gestor.getTotalEsperados());
            lblPromedio.setText(String.format(Locale.US, "Tiempo prom.: %.2f s", gestor.getTiempoPromedioViajeSeg()));
            lblEspera.setText(String.format(Locale.US, "Espera semáf.: %.1f%%", gestor.getPorcentajePromedioEspera()));

            GestorMetricas.RegistroVehiculo primero = gestor.getPrimeroEnLlegar();
            if (primero != null) {
                lblPrimero.setText(String.format(Locale.US, "Primero: %s (%.2f s)", primero.getId(), primero.getTiempoTotalSeg()));
            }

            Interseccion maxInter = gestor.getInterseccionMasCongestionada();
            if (maxInter != null && maxInter.getMaxCongestion() > 0) {
                lblCongestion.setText("Congestión máx.: " + maxInter.getMaxCongestion() + " en " + maxInter.getCoordenadaTexto());
            }
        };

        Timeline actualizadorMetricas = new Timeline(new KeyFrame(Duration.millis(500), e -> refrescarPanelLateral.run()));
        actualizadorMetricas.setCycleCount(Timeline.INDEFINITE);
        actualizadorMetricas.play();

        Thread[] hiloGenerador = new Thread[1];
        AtomicBoolean detencionIniciada = new AtomicBoolean();
        Runnable detenerEjecucion = () -> {
            if (!detencionIniciada.compareAndSet(false, true)) {
                return;
            }
            controlPausa.finalizar();
            if (hiloGenerador[0] != null) {
                hiloGenerador[0].interrupt();
            }
            gestorVehiculos.detener();
            miCiudad.detenerSemaforos();
            panelCiudad.detenerAnimacion();
            actualizadorMetricas.stop();

            Thread limpieza = new Thread(() -> {
                try {
                    gestorVehiculos.esperarDetencion();
                    miCiudad.esperarDetencionSemaforos();
                    if (hiloGenerador[0] != null) {
                        hiloGenerador[0].join();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "LimpiezaSimulacion");
            limpieza.setDaemon(true);
            limpieza.start();
        };

        btnPausa.setOnAction(e -> {
            if (controlPausa.estaFinalizada()) {
                return;
            }
            if (controlPausa.estaPausada()) {
                controlPausa.reanudar();
                btnPausa.setText("Pausar simulación");
                btnPausa.setStyle(
                        "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");
                lblEstado.setText("Estado: Simulando...");
                lblEstado.setTextFill(Color.LIGHTGREEN);
            } else {
                controlPausa.pausar();
                btnPausa.setText("Reanudar simulación");
                btnPausa.setStyle(
                        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8;");
                lblEstado.setText("Estado: Pausada");
                lblEstado.setTextFill(Color.GOLD);
            }
        });

        btnFinalizar.setOnAction(e -> {
            cancelarSeleccionRuta.run();
            controlPausa.finalizar();
            gestor.finalizarSinReporte();
            detenerEjecucion.run();
            mostrarMenuConfiguracion(stage);
        });

        gestor.setAlFinalizarSimulacion(() -> Platform.runLater(() -> {
            if (detencionIniciada.get()) {
                return;
            }
            detenerEjecucion.run();
            cancelarSeleccionRuta.run();
            refrescarPanelLateral.run();
            lblEstado.setText("Estado: Finalizada");
            lblEstado.setTextFill(Color.GOLD);
            btnPausa.setDisable(true);
            btnAutoAleatorio.setDisable(true);
            btnElegirRuta.setDisable(true);
            lblInstruccionRuta.setText("La simulación finalizó; ya no se pueden agregar autos.");
            actualizadorMetricas.stop();
            VentanaMetricasFX.mostrar(gestor);
        }));

        Scene scene = new Scene(root);
        stage.setTitle("Simulador de Tráfico - (" + config.getCantidadVehiculos() + " vehículos)");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();

        GeneradorTrafico generador = new GeneradorTrafico(gestorVehiculos, config, controlPausa);
        hiloGenerador[0] = new Thread(generador, "GeneradorTrafico");
        hiloGenerador[0].setDaemon(true);
        hiloGenerador[0].start();
    }

    private void observarCreacionVehiculo(
            CompletableFuture<Boolean> creacion,
            Label lblInstruccion,
            GestorMetricas gestor,
            Button btnAutoAleatorio,
            Button btnElegirRuta,
            Runnable cancelarSeleccion) {
        creacion.whenComplete((creado, error) -> Platform.runLater(() -> {
            if (error != null) {
                lblInstruccion.setText("No se pudo crear el auto: " + error.getMessage());
            } else if (Boolean.TRUE.equals(creado)) {
                lblInstruccion.setText("Auto agregado a la simulación.");
            } else if (gestor.estaFinalizada()) {
                lblInstruccion.setText("La simulación ya finalizó; no se agregó el auto.");
            } else {
                lblInstruccion.setText("No se encontró una ruta válida para crear el auto.");
            }

            if (gestor.estaFinalizada()) {
                cancelarSeleccion.run();
                btnAutoAleatorio.setDisable(true);
                btnElegirRuta.setDisable(true);
                lblInstruccion.setText("La simulación finalizó; ya no se pueden agregar autos.");
            }
        }));
    }

    private Label crearDatoPanel(String texto) {
        Label lbl = new Label(texto);
        lbl.setTextFill(Color.LIGHTGRAY);
        lbl.setFont(Font.font("System", 13));
        lbl.setWrapText(true);
        return lbl;
    }

    private boolean tieneParSpawnDestinoDistinto(int[][] matriz) {
        for (int filaOrigen = 0; filaOrigen < matriz.length; filaOrigen++) {
            for (int columnaOrigen = 0; columnaOrigen < matriz[filaOrigen].length; columnaOrigen++) {
                int tipoOrigen = matriz[filaOrigen][columnaOrigen];
                if (tipoOrigen != 8 && tipoOrigen != 10) {
                    continue;
                }
                for (int filaDestino = 0; filaDestino < matriz.length; filaDestino++) {
                    for (int columnaDestino = 0; columnaDestino < matriz[filaDestino].length; columnaDestino++) {
                        int tipoDestino = matriz[filaDestino][columnaDestino];
                        if ((tipoDestino == 9 || tipoDestino == 10)
                                && (filaOrigen != filaDestino || columnaOrigen != columnaDestino)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private String coordenada(PuntoGeneracion spawn) {
        return "(" + spawn.x + ", " + spawn.y + ")";
    }

    private Label crearEtiqueta(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.setTextFill(Color.WHITE);
        etiqueta.setFont(Font.font("System", FontWeight.BOLD, 12));
        return etiqueta;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
