package minipc.gui;

import minipc.so.BCP;
import minipc.hardware.CPU;
import minipc.so.CargadorMemoria;
import minipc.so.ColaListos;
import minipc.config.Configuracion;
import minipc.hardware.Disco;
import minipc.hardware.EntradaIndice;
import minipc.so.EstadoProceso;
import minipc.so.GestorArchivo;
import minipc.so.GestorMemoria;
import minipc.ensamblador.Instruccion;
import minipc.hardware.Memoria;
import minipc.ensamblador.ProcesadorInstrucciones;
import minipc.ensamblador.Registro;
import minipc.so.ResultadoCargaArchivo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ventana principal (100% gráfica) de la Mini PC.
 *
 * Flujo de uso:
 *   1) El usuario pulsa "Cargar archivos". La primera vez (o despues de "Limpiar") se abre la
 *      ventana de configuracion de memoria; las siguientes veces se va directo a elegir archivos.
 *   2) Se eligen uno o varios .asm. Cada uno se valida por separado: los validos se guardan en el
 *      Disco (y se registran en su indice); los invalidos se rechazan con su lista de errores.
 *   3) Cada programa valido se copia DEL DISCO a la Memoria si hay una ranura de BCP libre (maximo 5
 *      procesos activos) y un hueco contiguo donde quepa (primer ajuste). Su BCP se escribe en la ranura
 *      y el proceso entra al final de la cola de listos, que es una lista enlazada dentro de la memoria.
 *      Los que no caben quedan en el disco; el planificador (Fase 5) los admitira despues.
 *   4) "Paso a paso" ejecuta una instrucción por clic; "Ejecutar" corre el proceso actual completo.
 *      Mientras no exista el planificador, al terminar un proceso se libera su memoria y su ranura,
 *      y pasa a la CPU el primero de la cola de listos (seleccion provisional en orden de llegada).
 *   5) "Limpiar" reinicia todo para cargar nuevos programas.
 */
public class VentanaMiniPC extends JFrame {

    /** Color de fondo para las posiciones de memoria que ocupan los BCP (ranuras ocupadas del Kernel). */
    private static final Color COLOR_FILA_BCP = new Color(0xFF, 0xF3, 0xDC);

    /** Color de fondo para el encabezado del SO (primeras posiciones del Kernel). */
    private static final Color COLOR_FILA_ENCABEZADO = new Color(0xE8, 0xF5, 0xE9);

    /** CPU a la que se asignan los procesos (la Mini PC tiene un solo procesador). */
    private static final String NOMBRE_CPU = "CPU1";

    /** Colores de las zonas del disco. */
    private static final Color COLOR_DISCO_INDICE = new Color(0xE3, 0xF2, 0xFD);
    private static final Color COLOR_DISCO_VIRTUAL = new Color(0xF1, 0xE8, 0xFB);

    // ---- Backend ----
    private final GestorArchivo gestorArchivo = new GestorArchivo();
    private final ProcesadorInstrucciones procesador = new ProcesadorInstrucciones();
    private final CargadorMemoria cargadorMemoria = new CargadorMemoria();

    /** Instrucciones del proceso que tiene la CPU (las muestra la tabla "Instrucciones"). */
    private List<Instruccion> instrucciones;
    private Memoria memoria;
    private Disco disco;
    private CPU cpu;

    /** Administra las ranuras de BCP del Kernel y el area de usuario (primer ajuste). */
    private GestorMemoria gestorMemoria;

    /** Cola de listos FCFS, enlazada dentro de la memoria por el campo "enlace" de cada BCP. */
    private ColaListos colaListos;

    /** Proceso que tiene la CPU (el que se ejecuta con "Paso a paso" / "Ejecutar"), o null. */
    private BCP bcp;

    /** Procesos activos (con BCP en el Kernel y programa en memoria), por PID. */
    private final Map<Integer, BCP> procesosActivos = new LinkedHashMap<>();

    /** Instrucciones de cada proceso activo, por PID (para mostrarlas cuando pase a la CPU). */
    private final Map<Integer, List<Instruccion>> instruccionesPorProceso = new HashMap<>();

    /** Proximo PID a asignar (1, 2, 3...). Se reinicia con "Limpiar". */
    private int siguientePid = 1;

    /** Configuracion de memoria/disco vigente (leida de config.properties o por defecto). */
    private Configuracion configuracion = Configuracion.cargar();
    private Timer temporizadorEjecucion;

    // ---- Componentes ----
    private JButton botonEjecutar;
    private JButton botonPasoAPaso;
    private JButton botonLimpiar;
    private JButton botonCargarArchivo;

    private JLabel etiquetaMemoriaTotal;
    private JLabel etiquetaSO;
    private JLabel etiquetaUsuario;
    private JLabel etiquetaDisco;

    private DefaultTableModel modeloInstrucciones;
    private DefaultTableModel modeloMemoria;
    private DefaultTableModel modeloDisco;
    private JTable tablaInstrucciones;
    private JTable tablaMemoria;
    private JTable tablaDisco;
    private JLabel leyendaDisco;

    // Etiquetas del panel "BCP actual"
    private JLabel valorId;
    private JLabel valorPrograma;
    private JLabel valorEstado;
    private JLabel valorPrioridad;
    private JLabel valorPosicionBcp;
    private JLabel valorInicioMemoria;
    private JLabel valorFinMemoria;
    private JLabel valorPc;
    private JLabel valorIr;
    private JLabel valorAc;
    private JLabel valorAx;
    private JLabel valorBx;
    private JLabel valorCx;
    private JLabel valorDx;
    private JLabel valorInstrucciones;
    private JLabel valorEnlace;

    public VentanaMiniPC() {
        super("Gestor de Procesos - Proyecto 1");
        disco = crearDiscoVacio();
        construirInterfaz();
        cargarTablaDisco();
        actualizarEstadoBotones();
    }

    // ==================================================================
    // Construcción de la interfaz
    // ==================================================================

    private void construirInterfaz() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(EstiloUI.FONDO);
        setLayout(new BorderLayout(0, 0));

        add(construirBarraSuperior(), BorderLayout.NORTH);
        add(construirContenidoCentral(), BorderLayout.CENTER);

        setMinimumSize(new Dimension(1240, 640));
        setSize(1420, 720);
        setLocationRelativeTo(null);
    }

    /** Fila de botones + resumen de la configuracion de memoria, en la parte superior. */
    private JPanel construirBarraSuperior() {
        JPanel contenedor = new JPanel();
        contenedor.setLayout(new javax.swing.BoxLayout(contenedor, javax.swing.BoxLayout.Y_AXIS));
        contenedor.setBackground(EstiloUI.FONDO);
        contenedor.setBorder(BorderFactory.createEmptyBorder(16, 20, 10, 20));

        // Fila 1: acciones de ejecución (izquierda) y "Cargar archivo" (derecha)
        JPanel filaSuperior = new JPanel(new BorderLayout());
        filaSuperior.setBackground(EstiloUI.FONDO);

        JPanel filaAcciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filaAcciones.setBackground(EstiloUI.FONDO);

        botonEjecutar = EstiloUI.crearBotonPrimario("Ejecutar", EstiloUI.PRIMARIO, EstiloUI.PRIMARIO_HOVER);
        botonPasoAPaso = EstiloUI.crearBotonSecundario("Paso a paso");
        botonLimpiar = EstiloUI.crearBotonPrimario("Limpiar", EstiloUI.PELIGRO, EstiloUI.PELIGRO_HOVER);

        botonEjecutar.addActionListener(e -> ejecutarProgramaCompleto());
        botonPasoAPaso.addActionListener(e -> ejecutarUnPaso());
        botonLimpiar.addActionListener(e -> limpiarTodo());

        filaAcciones.add(botonEjecutar);
        filaAcciones.add(botonPasoAPaso);
        filaAcciones.add(botonLimpiar);

        JPanel filaCarga = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filaCarga.setBackground(EstiloUI.FONDO);
        botonCargarArchivo = EstiloUI.crearBotonSecundario("Cargar archivos (.asm)");
        botonCargarArchivo.addActionListener(e -> cargarArchivos());
        filaCarga.add(botonCargarArchivo);

        filaSuperior.add(filaAcciones, BorderLayout.WEST);
        filaSuperior.add(filaCarga, BorderLayout.EAST);

        // Fila 2: resumen de la configuracion de memoria vigente (se cambia desde la ventana de configuracion)
        JPanel filaMemoria = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filaMemoria.setBackground(EstiloUI.FONDO);

        etiquetaMemoriaTotal = EstiloUI.crearEtiquetaSecundaria("");
        etiquetaMemoriaTotal.setIcon(EstiloUI.iconoMemoria(EstiloUI.TEXTO_SECUNDARIO));
        etiquetaMemoriaTotal.setIconTextGap(6);

        etiquetaSO = EstiloUI.crearEtiquetaSecundaria("");
        etiquetaSO.setIcon(EstiloUI.iconoSistema(EstiloUI.TEXTO_SECUNDARIO));
        etiquetaSO.setIconTextGap(6);

        etiquetaUsuario = EstiloUI.crearEtiquetaSecundaria("");
        etiquetaUsuario.setIcon(EstiloUI.iconoUsuario(EstiloUI.TEXTO_SECUNDARIO));
        etiquetaUsuario.setIconTextGap(6);

        etiquetaDisco = EstiloUI.crearEtiquetaSecundaria("");

        actualizarEtiquetaDivisionMemoria();

        filaMemoria.add(etiquetaMemoriaTotal);
        filaMemoria.add(Box.createHorizontalStrut(18));
        filaMemoria.add(etiquetaSO);
        filaMemoria.add(Box.createHorizontalStrut(14));
        filaMemoria.add(etiquetaUsuario);
        filaMemoria.add(Box.createHorizontalStrut(18));
        filaMemoria.add(etiquetaDisco);

        contenedor.add(filaSuperior);
        contenedor.add(filaMemoria);

        return contenedor;
    }

    /** Zona central: BCP actual | tabla de instrucciones | tabla de memoria | tabla de disco. */
    private JPanel construirContenidoCentral() {
        JPanel contenedor = new JPanel(new GridBagLayout());
        contenedor.setBackground(EstiloUI.FONDO);
        contenedor.setBorder(BorderFactory.createEmptyBorder(6, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 14);
        gbc.gridy = 0;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 0; // ancho fijo: el espacio sobrante se reparte entre las tablas
        contenedor.add(construirPanelBcp(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.28;
        contenedor.add(construirPanelInstrucciones(), gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.36;
        contenedor.add(construirPanelMemoria(), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.36;
        gbc.insets = new Insets(0, 0, 0, 0);
        contenedor.add(construirPanelDisco(), gbc);

        return contenedor;
    }

    private JPanel construirPanelInstrucciones() {
        modeloInstrucciones = new DefaultTableModel(new Object[]{"Posición", "Instrucción"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaInstrucciones = new JTable(modeloInstrucciones);
        EstiloUI.aplicarEstiloTabla(tablaInstrucciones);
        tablaInstrucciones.getColumnModel().getColumn(0).setMaxWidth(90);

        return envolverEnTarjeta("Instrucciones", tablaInstrucciones);
    }

    private JPanel construirPanelMemoria() {
        modeloMemoria = new DefaultTableModel(new Object[]{"Posición", "Valor en memoria"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaMemoria = new JTable(modeloMemoria);
        EstiloUI.aplicarEstiloTabla(tablaMemoria);
        tablaMemoria.getColumnModel().getColumn(0).setMaxWidth(90);
        aplicarRendererMemoria();

        return envolverEnTarjeta("Memoria", tablaMemoria);
    }

    /**
     * Renderer de la tabla de memoria: pinta de un color distinto las posiciones del Kernel
     * donde esta guardado el BCP, para distinguirlas del codigo del programa.
     */
    private void aplicarRendererMemoria() {
        javax.swing.table.DefaultTableCellRenderer renderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    if (memoria != null && memoria.esEncabezado(row)) {
                        c.setBackground(COLOR_FILA_ENCABEZADO);
                    } else if (esFilaDeUnBcp(row)) {
                        c.setBackground(COLOR_FILA_BCP);
                    } else {
                        c.setBackground(row % 2 == 0 ? EstiloUI.PANEL : EstiloUI.FONDO);
                    }
                }
                setToolTipText(descripcionZonaMemoria(row));
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                return c;
            }
        };
        for (int i = 0; i < tablaMemoria.getColumnCount(); i++) {
            tablaMemoria.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    /** Indica si una posicion de memoria pertenece a una ranura de BCP ocupada. */
    private boolean esFilaDeUnBcp(int posicion) {
        if (gestorMemoria == null) {
            return false;
        }
        int ranura = gestorMemoria.ranuraDePosicion(posicion);
        return ranura >= 0 && gestorMemoria.ranuraOcupada(ranura);
    }

    /** Tooltip de una fila de memoria: encabezado, ranura de BCP (y de que proceso) o programa de usuario. */
    private String descripcionZonaMemoria(int posicion) {
        if (memoria == null || gestorMemoria == null || !memoria.esPosicionValida(posicion)) {
            return null;
        }
        if (memoria.esEncabezado(posicion)) {
            return "Encabezado del SO";
        }
        if (memoria.esEspacioKernel(posicion)) {
            int ranura = gestorMemoria.ranuraDePosicion(posicion);
            if (ranura < 0) {
                return "Kernel (sin uso)";
            }
            BCP dueno = buscarProcesoPorRanura(gestorMemoria.getDireccionRanura(ranura));
            return "Ranura " + (ranura + 1) + " de BCP" + (dueno == null ? " (libre)" : " → PID " + dueno.getPid() + " (" + dueno.getNombrePrograma() + ")");
        }
        for (BCP proceso : procesosActivos.values()) {
            if (posicion >= proceso.getLimiteInferior() && posicion <= proceso.getLimiteSuperior()) {
                return "PID " + proceso.getPid() + " (" + proceso.getNombrePrograma() + "), instrucción " + (posicion - proceso.getBase() + 1) + " de " + proceso.getAlcance();
            }
        }
        return "Usuario (libre)";
    }

    private BCP buscarProcesoPorRanura(int direccion) {
        for (BCP proceso : procesosActivos.values()) {
            if (proceso.getPosicionBCP() == direccion) {
                return proceso;
            }
        }
        return null;
    }

    /** Tabla del disco: posicion y valor, con un color por zona (indice, archivos, memoria virtual). */
    private JPanel construirPanelDisco() {
        modeloDisco = new DefaultTableModel(new Object[]{"Posición", "Valor en disco"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaDisco = new JTable(modeloDisco);
        EstiloUI.aplicarEstiloTabla(tablaDisco);
        tablaDisco.getColumnModel().getColumn(0).setMaxWidth(90);
        aplicarRendererDisco();

        JPanel tarjeta = envolverEnTarjeta("Disco", tablaDisco);

        leyendaDisco = EstiloUI.crearEtiquetaSecundaria("");
        leyendaDisco.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        tarjeta.add(leyendaDisco, BorderLayout.SOUTH);
        return tarjeta;
    }

    private void aplicarRendererDisco() {
        javax.swing.table.DefaultTableCellRenderer renderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    if (disco != null && disco.esIndice(row)) {
                        c.setBackground(COLOR_DISCO_INDICE);
                    } else if (disco != null && disco.esMemoriaVirtual(row)) {
                        c.setBackground(COLOR_DISCO_VIRTUAL);
                    } else {
                        c.setBackground(row % 2 == 0 ? EstiloUI.PANEL : EstiloUI.FONDO);
                    }
                }
                setToolTipText(descripcionZonaDisco(row));
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                return c;
            }
        };
        for (int i = 0; i < tablaDisco.getColumnCount(); i++) {
            tablaDisco.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    /** Texto del tooltip de una fila del disco: zona y, si aplica, a que archivo pertenece. */
    private String descripcionZonaDisco(int posicion) {
        if (disco == null) {
            return null;
        }
        if (disco.esIndice(posicion)) {
            EntradaIndice entrada = EntradaIndice.desdeTexto(posicion, disco.leer(posicion));
            return entrada == null ? "Índice de archivos (entrada libre)" : "Índice: " + entrada.getNombre() + " → posiciones " + entrada.getInicio() + "-" + entrada.getFin();
        }
        if (disco.esMemoriaVirtual(posicion)) {
            return "Memoria virtual";
        }
        for (EntradaIndice entrada : disco.listarArchivos()) {
            if (posicion >= entrada.getInicio() && posicion <= entrada.getFin()) {
                return entrada.getNombre() + " (línea " + (posicion - entrada.getInicio() + 1) + " de " + entrada.getTamano() + ")";
            }
        }
        return "Zona de archivos (libre)";
    }

    /** Panel derecho tipo "ficha" con los campos del BCP actual. */
    private JPanel construirPanelBcp() {
        JPanel tarjeta = crearTarjetaBase();
        tarjeta.setLayout(new BorderLayout());

        JLabel titulo = EstiloUI.crearTitulo("BCP actual");
        titulo.setBorder(BorderFactory.createEmptyBorder(4, 4, 14, 4));
        tarjeta.add(titulo, BorderLayout.NORTH);

        JPanel campos = new JPanel(new GridBagLayout());
        campos.setBackground(EstiloUI.PANEL);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.insets = new Insets(5, 4, 5, 4);
        int fila = 0;

        valorId = new JLabel();
        valorPrograma = new JLabel();
        valorEstado = new JLabel();
        valorPrioridad = new JLabel();
        valorPosicionBcp = new JLabel();
        valorInicioMemoria = new JLabel();
        valorFinMemoria = new JLabel();
        valorPc = new JLabel();
        valorIr = new JLabel();
        valorAc = new JLabel();
        valorAx = new JLabel();
        valorBx = new JLabel();
        valorCx = new JLabel();
        valorDx = new JLabel();
        valorInstrucciones = new JLabel();
        valorEnlace = new JLabel();

        fila = agregarCampoBcp(campos, gbc, fila, "ID", valorId);
        fila = agregarCampoBcp(campos, gbc, fila, "Programa", valorPrograma);
        fila = agregarCampoBcp(campos, gbc, fila, "Estado", valorEstado);
        fila = agregarCampoBcp(campos, gbc, fila, "Prioridad", valorPrioridad);
        fila = agregarCampoBcp(campos, gbc, fila, "Posición BCP", valorPosicionBcp);
        fila = agregarCampoBcp(campos, gbc, fila, "Inicio memoria", valorInicioMemoria);
        fila = agregarCampoBcp(campos, gbc, fila, "Fin memoria", valorFinMemoria);
        fila = agregarCampoBcp(campos, gbc, fila, "Enlace siguiente", valorEnlace);
        fila = agregarSeparadorBcp(campos, gbc, fila);
        fila = agregarCampoBcp(campos, gbc, fila, "PC", valorPc);
        fila = agregarCampoBcp(campos, gbc, fila, "IR", valorIr);
        fila = agregarCampoBcp(campos, gbc, fila, "AC", valorAc);
        fila = agregarSeparadorBcp(campos, gbc, fila);
        fila = agregarCampoBcp(campos, gbc, fila, "AX", valorAx);
        fila = agregarCampoBcp(campos, gbc, fila, "BX", valorBx);
        fila = agregarCampoBcp(campos, gbc, fila, "CX", valorCx);
        fila = agregarCampoBcp(campos, gbc, fila, "DX", valorDx);
        fila = agregarSeparadorBcp(campos, gbc, fila);
        agregarCampoBcp(campos, gbc, fila, "Instrucciones", valorInstrucciones);

        // relleno inferior para que los campos queden pegados arriba
        gbc.gridy = fila + 10;
        gbc.weighty = 1;
        campos.add(javax.swing.Box.createVerticalGlue(), gbc);

        JScrollPane scroll = new JScrollPane(campos);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        tarjeta.add(scroll, BorderLayout.CENTER);

        // ancho fijo minimo para que las tablas no aplasten el panel
        tarjeta.setMinimumSize(new Dimension(250, 100));
        tarjeta.setPreferredSize(new Dimension(250, 100));

        limpiarPanelBcp();
        return tarjeta;
    }

    private int agregarCampoBcp(JPanel contenedor, GridBagConstraints gbc, int fila, String etiqueta, JLabel valor) {
        JPanel filaPanel = new JPanel(new BorderLayout());
        filaPanel.setBackground(EstiloUI.PANEL);

        JLabel nombreCampo = EstiloUI.crearEtiquetaSecundaria(etiqueta);
        valor.setFont(EstiloUI.FUENTE_MONO_BOLD);
        valor.setForeground(EstiloUI.TEXTO);
        valor.setHorizontalAlignment(SwingConstants.RIGHT);

        filaPanel.add(nombreCampo, BorderLayout.WEST);
        filaPanel.add(valor, BorderLayout.EAST);

        gbc.gridy = fila;
        contenedor.add(filaPanel, gbc);
        return fila + 1;
    }

    private int agregarSeparadorBcp(JPanel contenedor, GridBagConstraints gbc, int fila) {
        JPanel linea = new JPanel();
        linea.setBackground(EstiloUI.BORDE);
        linea.setPreferredSize(new Dimension(10, 1));
        gbc.gridy = fila;
        gbc.insets = new Insets(8, 4, 8, 4);
        contenedor.add(linea, gbc);
        gbc.insets = new Insets(5, 4, 5, 4);
        return fila + 1;
    }

    /** Envuelve un componente (tabla) en una "tarjeta" blanca con título, estilo tarjeta moderna. */
    private JPanel envolverEnTarjeta(String titulo, Component contenido) {
        JPanel tarjeta = crearTarjetaBase();
        tarjeta.setLayout(new BorderLayout());

        JLabel etiquetaTitulo = EstiloUI.crearTitulo(titulo);
        etiquetaTitulo.setBorder(BorderFactory.createEmptyBorder(4, 4, 10, 4));
        tarjeta.add(etiquetaTitulo, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(contenido);
        scroll.setBorder(BorderFactory.createLineBorder(EstiloUI.BORDE));
        tarjeta.add(scroll, BorderLayout.CENTER);

        return tarjeta;
    }

    private JPanel crearTarjetaBase() {
        JPanel tarjeta = new JPanel();
        tarjeta.setBackground(EstiloUI.PANEL);
        tarjeta.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(EstiloUI.BORDE), BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        return tarjeta;
    }

    // ==================================================================
    // Acciones
    // ==================================================================

    /**
     * Carga uno o varios archivos .asm:
     *   1) Ventana de configuracion: solo si no hay nada cargado (primera vez o despues de "Limpiar").
     *   2) Seleccion multiple de archivos.
     *   3) Cada archivo se valida por separado; los validos se guardan en el disco.
     *   4) Cada programa valido se copia DEL DISCO a la memoria (si hay ranura de BCP y espacio)
     *      y entra a la cola de listos.
     *   5) Resumen: cuales se cargaron (y donde) y cuales se rechazaron (con sus errores).
     */
    private void cargarArchivos() {
        // 1) Configuracion de memoria: solo la primera vez (o despues de "Limpiar").
        //    Si ya hay programas cargados, la configuracion no se puede cambiar, asi que
        //    no tiene sentido mostrar la ventana: se pasa directo a elegir archivos.
        //    La memoria se crea una sola vez, justo despues de aceptar la configuracion.
        boolean sinNadaCargado = memoria == null;
        if (sinNadaCargado) {
            Configuracion elegida = new DialogoConfiguracion(this, configuracion, true).mostrar();
            if (elegida == null) {
                return; // el usuario cancelo la configuracion
            }
            configuracion = elegida;
            disco = crearDiscoVacio(); // el disco toma el tamano configurado
            inicializarMemoriaPrincipal();
            cargarTablaDisco();
            actualizarEtiquetaDivisionMemoria();
        }

        // 2) Seleccion de archivos
        List<File> archivos = gestorArchivo.seleccionarArchivos(this);
        if (archivos.isEmpty()) {
            return; // el usuario cancelo
        }

        // 3) Validacion y escritura en el disco (cada archivo por separado)
        List<ResultadoCargaArchivo> resultados = gestorArchivo.cargarEnDisco(archivos, disco);
        cargarTablaDisco();

        // 4) Todos los programas validos se copian del disco a la memoria (si hay ranura y espacio).
        //    Los que no caben quedan en el disco: el planificador los admitira en la Fase 5.
        List<String> mensajesMemoria = new ArrayList<>();
        for (ResultadoCargaArchivo resultado : resultados) {
            if (resultado.isCargado()) {
                mensajesMemoria.add(cargarProgramaEnMemoria(resultado.getNombreArchivo()));
            }
        }

        // Si la CPU esta libre, pasa a ella el primero de la cola de listos.
        if (bcp == null) {
            asignarCpuAlSiguiente();
        }
        cargarTablaMemoria();
        actualizarPanelBcp();
        resaltarFilaActual();

        // 5) Resumen para el usuario
        mostrarResumenDeCarga(resultados, mensajesMemoria);
        actualizarEstadoBotones();
    }

    /**
     * Crea la memoria principal, la CPU, el gestor de memoria y la cola de listos con la configuracion
     * vigente. Se llama una sola vez, al aceptar la configuracion (y de nuevo despues de "Limpiar").
     */
    private void inicializarMemoriaPrincipal() {
        memoria = new Memoria(configuracion.getMemoriaPrincipal());
        cpu = new CPU(memoria);
        gestorMemoria = new GestorMemoria(memoria); // escribe el encabezado inicial del SO
        colaListos = new ColaListos(memoria);
        procesosActivos.clear();
        instruccionesPorProceso.clear();
        siguientePid = 1;
        bcp = null;
        instrucciones = null;
        modeloInstrucciones.setRowCount(0);
        cargarTablaMemoria();
    }

    /**
     * Copia un programa del disco a la memoria principal:
     *   1) Pide una ranura de BCP libre al GestorMemoria (maximo 5 procesos activos).
     *   2) Pide un hueco contiguo en el area de usuario (primer ajuste).
     *   3) El CargadorMemoria copia las instrucciones desde la base y escribe el BCP en la ranura.
     *   4) El proceso entra al final de la cola de listos (lista enlazada en memoria).
     * Si algo falla, se devuelve lo que se habia reservado.
     * @return texto para el resumen (exito o motivo por el que el programa sigue solo en el disco).
     */
    private String cargarProgramaEnMemoria(String nombre) {
        List<Instruccion> nuevasInstrucciones;
        try {
            nuevasInstrucciones = gestorArchivo.leerProgramaDelDisco(nombre, disco);
        } catch (RuntimeException ex) {
            return "\"" + nombre + "\" quedó en el disco, pero no se pudo leer: " + ex.getMessage();
        }

        int posicionBCP = gestorMemoria.asignarRanuraBCP();
        if (posicionBCP == Memoria.SIN_DIRECCION) {
            return "\"" + nombre + "\" quedó en el disco en espera: ya hay " + gestorMemoria.getCantidadRanuras()
                    + " procesos activos.";
        }

        int tamano = nuevasInstrucciones.size();
        int base = gestorMemoria.asignar(tamano);
        if (base == Memoria.SIN_DIRECCION) {
            gestorMemoria.liberarRanuraBCP(posicionBCP);
            return "\"" + nombre + "\" quedó en el disco en espera: necesita " + tamano
                    + " posiciones seguidas y el hueco libre más grande es de " + gestorMemoria.getMayorHueco() + ".";
        }

        try {
            BCP nuevoBcp = cargadorMemoria.cargar(nuevasInstrucciones, memoria, base, posicionBCP, siguientePid, nombre);
            siguientePid++;
            colaListos.encolar(nuevoBcp); // queda LISTO, al final de la cola
            procesosActivos.put(nuevoBcp.getPid(), nuevoBcp);
            instruccionesPorProceso.put(nuevoBcp.getPid(), nuevasInstrucciones);
            return "\"" + nombre + "\" → memoria " + nuevoBcp.getLimiteInferior() + "-" + nuevoBcp.getLimiteSuperior()
                    + ", BCP en " + posicionBCP + "-" + (posicionBCP + BCP.TAMANO_EN_MEMORIA - 1)
                    + " (PID " + nuevoBcp.getPid() + "), en la cola de listos.";
        } catch (RuntimeException ex) {
            gestorMemoria.liberar(base, tamano);
            gestorMemoria.liberarRanuraBCP(posicionBCP);
            return "\"" + nombre + "\" quedó en el disco, pero no se pudo cargar en memoria: " + ex.getMessage();
        }
    }

    /**
     * SELECCION PROVISIONAL (hasta la Fase 5): si la CPU esta libre, saca el primero de la cola de
     * listos y se lo asigna. Actualiza el campo 3 del encabezado (BCP en ejecucion).
     * En la Fase 5 esto lo haran el Planificador (seleccionar) y el Despachador (cambio de contexto).
     */
    private void asignarCpuAlSiguiente() {
        bcp = colaListos.desencolar();
        if (bcp == null) {
            instrucciones = null;
            modeloInstrucciones.setRowCount(0);
            memoria.escribirCampoEncabezado(Memoria.ENCABEZADO_BCP_EN_EJECUCION, Memoria.SIN_DIRECCION);
            return;
        }
        bcp.setEstado(EstadoProceso.EJECUTANDO);
        bcp.setCpuAsignada(NOMBRE_CPU);
        bcp.registrarInicio(LocalTime.now());
        memoria.guardarBCP(bcp);
        memoria.escribirCampoEncabezado(Memoria.ENCABEZADO_BCP_EN_EJECUCION, bcp.getPosicionBCP());

        instrucciones = instruccionesPorProceso.get(bcp.getPid());
        cargarTablaInstrucciones();
    }

    /**
     * LIBERACION PROVISIONAL (hasta INT 20H en la Fase 7): cuando el proceso actual termina, se libera
     * su espacio de usuario y su ranura de BCP, y la CPU pasa al siguiente de la cola de listos.
     */
    private void finalizarProcesoActual() {
        if (bcp == null) {
            return;
        }
        gestorMemoria.liberar(bcp.getBase(), bcp.getAlcance());
        gestorMemoria.liberarRanuraBCP(bcp.getPosicionBCP());
        procesosActivos.remove(bcp.getPid());
        instruccionesPorProceso.remove(bcp.getPid());
        bcp = null;

        asignarCpuAlSiguiente();
        cargarTablaMemoria();
        actualizarPanelBcp();
        resaltarFilaActual();
    }

    /** Mensaje de fin de un proceso; indica si otro proceso paso a la CPU. */
    private void avisarFinDeProceso(BCP terminado) {
        String texto = "Programa \"" + terminado.getNombrePrograma() + "\" (PID " + terminado.getPid() + ") finalizado.\nAC final = " + terminado.getAc();
        finalizarProcesoActual();
        if (bcp != null) {
            texto += "\n\nPasa a la CPU: \"" + bcp.getNombrePrograma() + "\" (PID " + bcp.getPid() + ").";
        }
        JOptionPane.showMessageDialog(this, texto, "Ejecución completa", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Muestra que archivos se cargaron (y en que posiciones del disco) y cuales se rechazaron. */
    private void mostrarResumenDeCarga(List<ResultadoCargaArchivo> resultados, List<String> mensajesMemoria) {
        List<String> cargados = new ArrayList<>();
        List<String> rechazados = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        for (ResultadoCargaArchivo resultado : resultados) {
            if (resultado.isCargado()) {
                EntradaIndice entrada = resultado.getEntrada();
                cargados.add(resultado.getNombreArchivo() + " → disco, posiciones " + entrada.getInicio() + "-" + entrada.getFin() + " (índice en la posición " + entrada.getPosicionIndice() + ")");
                advertencias.addAll(resultado.getAdvertencias());
            } else {
                rechazados.add(resultado.getNombreArchivo() + " (" + resultado.getErrores().size() + " error(es)):");
                for (String error : resultado.getErrores()) {
                    rechazados.add("    – " + error);
                }
            }
        }

        StringBuilder texto = new StringBuilder();
        texto.append("Archivos cargados en el disco: ").append(cargados.size()).append(" de ").append(resultados.size()).append("\n");
        for (String linea : cargados) {
            texto.append("  ✔ ").append(linea).append("\n");
        }
        if (!rechazados.isEmpty()) {
            texto.append("\nArchivos rechazados:\n");
            for (String linea : rechazados) {
                texto.append(linea.startsWith("    ") ? linea : "  ✘ " + linea).append("\n");
            }
        }
        if (!advertencias.isEmpty()) {
            texto.append("\nAdvertencias:\n");
            for (String advertencia : advertencias) {
                texto.append("  • ").append(advertencia).append("\n");
            }
        }
        if (!mensajesMemoria.isEmpty()) {
            texto.append("\nMemoria principal:\n");
            for (String mensaje : mensajesMemoria) {
                texto.append("  • ").append(mensaje).append("\n");
            }
            texto.append("\nProcesos activos: ").append(gestorMemoria.getCantidadProcesosActivos()).append(" de ")
                    .append(gestorMemoria.getCantidadRanuras()).append(" · en cola de listos: ").append(colaListos.getCantidad());
            if (bcp != null) {
                texto.append(" · en CPU: PID ").append(bcp.getPid());
            }
            texto.append("\n");
        }

        int tipo = rechazados.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : (cargados.isEmpty() ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE);
        JOptionPane.showMessageDialog(this, crearAreaDeMensaje(texto.toString().trim()), "Resultado de la carga", tipo);
    }

    /** Texto con ajuste de linea y barra de desplazamiento, para mensajes largos. */
    private JScrollPane crearAreaDeMensaje(String texto) {
        javax.swing.JTextArea area = new javax.swing.JTextArea(texto);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(EstiloUI.FUENTE_BASE);
        area.setBackground(EstiloUI.PANEL);
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, Math.min(380, 70 + 22 * texto.split("\n").length)));
        scroll.setBorder(BorderFactory.createLineBorder(EstiloUI.BORDE));
        return scroll;
    }

    private void ejecutarUnPaso() {
        if (bcp == null || cpu == null) {
            return;
        }
        if (!bcp.tieneInstruccionesPendientes()) {
            JOptionPane.showMessageDialog(this, "El programa ya finalizó.", "Fin de la ejecución", JOptionPane.INFORMATION_MESSAGE);
            actualizarEstadoBotones();
            return;
        }

        try {
            cpu.ejecutarUnPaso(bcp);
            actualizarPanelBcp();
            actualizarKernelEnTablaMemoria();
            resaltarFilaActual();
        } catch (RuntimeException ex) {
            mostrarErrorDeEjecucion(ex);
        }

        if (!bcp.tieneInstruccionesPendientes()) {
            avisarFinDeProceso(bcp);
        }
        actualizarEstadoBotones();
    }

    private void ejecutarProgramaCompleto() {
        if (bcp == null || cpu == null) {
            return;
        }
        if (!bcp.tieneInstruccionesPendientes()) {
            JOptionPane.showMessageDialog(this, "El programa ya finalizó.", "Fin de la ejecución", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        fijarBotonesDurante(false); // deshabilita botones mientras corre la animación

        temporizadorEjecucion = new Timer(450, evento -> {
            if (bcp.tieneInstruccionesPendientes()) {
                try {
                    cpu.ejecutarUnPaso(bcp);
                    actualizarPanelBcp();
                    actualizarKernelEnTablaMemoria();
                    resaltarFilaActual();
                } catch (RuntimeException ex) {
                    ((Timer) evento.getSource()).stop();
                    fijarBotonesDurante(true);
                    actualizarEstadoBotones();
                    mostrarErrorDeEjecucion(ex);
                    return;
                }
            } else {
                ((Timer) evento.getSource()).stop();
                actualizarKernelEnTablaMemoria();
                avisarFinDeProceso(bcp);
                fijarBotonesDurante(true);
                actualizarEstadoBotones();
            }
        });
        temporizadorEjecucion.start();
    }

    private void mostrarErrorDeEjecucion(RuntimeException ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error de ejecución", JOptionPane.ERROR_MESSAGE);
    }

    private void limpiarTodo() {
        if (temporizadorEjecucion != null && temporizadorEjecucion.isRunning()) {
            temporizadorEjecucion.stop();
        }

        instrucciones = null;
        memoria = null;
        bcp = null;
        cpu = null;
        gestorMemoria = null;
        colaListos = null;
        procesosActivos.clear();
        instruccionesPorProceso.clear();
        siguientePid = 1;
        disco = crearDiscoVacio();

        modeloInstrucciones.setRowCount(0);
        modeloMemoria.setRowCount(0);
        cargarTablaDisco();
        limpiarPanelBcp();

        actualizarEtiquetaDivisionMemoria();
        actualizarEstadoBotones();
    }

    // ==================================================================
    // Actualización de la interfaz
    // ==================================================================

    private void cargarTablaInstrucciones() {
        modeloInstrucciones.setRowCount(0);
        if (instrucciones == null) {
            return;
        }
        for (Instruccion instruccion : instrucciones) {
            modeloInstrucciones.addRow(new Object[]{instruccion.getPosicionMemoria(), instruccion.getTextoNormalizado()});
        }
    }

    private void cargarTablaMemoria() {
        modeloMemoria.setRowCount(0);
        if (memoria == null) {
            return;
        }
        // se muestra exactamente lo que quedo guardado en cada posicion de la memoria
        for (int posicion = 0; posicion < memoria.getTamanoTotal(); posicion++) {
            modeloMemoria.addRow(new Object[]{posicion, memoria.leer(posicion)});
        }
        actualizarKernelEnTablaMemoria();
    }

    /** Disco vacio con el tamano de la configuracion vigente. */
    private Disco crearDiscoVacio() {
        return new Disco(configuracion.getMemoriaSecundaria(), configuracion.getTamanoIndiceDisco(),
                configuracion.getMemoriaVirtual());
    }

    /** Vuelve a llenar la tabla del disco con lo que hay en cada posicion, y actualiza la leyenda de zonas. */
    private void cargarTablaDisco() {
        modeloDisco.setRowCount(0);
        for (int posicion = 0; posicion < disco.getTamanoTotal(); posicion++) {
            modeloDisco.addRow(new Object[]{posicion, disco.leer(posicion)});
        }
        leyendaDisco.setText("<html>" + cuadroColor(COLOR_DISCO_INDICE) + " Índice &nbsp;&nbsp;" + cuadroColor(EstiloUI.FONDO) + " Archivos &nbsp;&nbsp;" + cuadroColor(COLOR_DISCO_VIRTUAL) + " Memoria virtual<br>"
                + disco.getCantidadArchivos() + " de " + disco.getTamanoIndice() + " archivos &nbsp;·&nbsp; " + disco.getEspacioLibreArchivos() + " posiciones libres</html>");
        leyendaDisco.setToolTipText("Índice 0-" + (disco.getTamanoIndice() - 1) + ", archivos " + disco.getInicioArchivos() + "-" + disco.getFinArchivos() + ", memoria virtual " + disco.getInicioMemoriaVirtual() + "-" + (disco.getTamanoTotal() - 1));
    }

    /** Cuadrito de color para la leyenda (HTML). */
    private String cuadroColor(Color color) {
        return String.format("<span style='background-color:#%02x%02x%02x;'>&nbsp;&nbsp;&nbsp;&nbsp;</span>",
                color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Refresca todas las filas del Kernel: el encabezado del SO y las ranuras de BCP.
     * Los valores se LEEN DE LA MEMORIA (no de los objetos BCP), para mostrar lo que realmente
     * quedo almacenado: asi se ve la lista enlazada (encabezado -> enlace -> enlace -> -1).
     */
    private void actualizarKernelEnTablaMemoria() {
        if (memoria == null || gestorMemoria == null || modeloMemoria.getRowCount() != memoria.getTamanoTotal()) {
            return;
        }
        for (int posicion = memoria.getInicioKernel(); posicion <= memoria.getFinKernel(); posicion++) {
            String valorEnMemoria = memoria.leer(posicion);
            String texto = valorEnMemoria;
            if (memoria.esEncabezado(posicion)) {
                texto = Memoria.describirEncabezado(posicion - memoria.getInicioKernel(), valorEnMemoria);
            } else {
                int ranura = gestorMemoria.ranuraDePosicion(posicion);
                if (ranura >= 0 && gestorMemoria.ranuraOcupada(ranura)) {
                    int campo = posicion - gestorMemoria.getDireccionRanura(ranura);
                    texto = BCP.describirCampo(campo, valorEnMemoria);
                }
            }
            modeloMemoria.setValueAt(texto, posicion, 1);
        }
    }

    private void actualizarPanelBcp() {
        if (bcp == null) {
            limpiarPanelBcp();
            return;
        }
        valorId.setText(String.valueOf(bcp.getPid()));
        valorPrograma.setText(bcp.getNombrePrograma());
        valorEstado.setText(bcp.getEstado().toString());
        valorEstado.setForeground(colorParaEstado(bcp.getEstado()));
        valorPrioridad.setText(String.valueOf(bcp.getPrioridad()));
        // rango de posiciones del Kernel que ocupa el BCP (ej: "0 - 12")
        valorPosicionBcp.setText(bcp.getPosicionBCP() + " - " + (bcp.getPosicionBCP() + BCP.TAMANO_EN_MEMORIA - 1));
        valorInicioMemoria.setText(String.valueOf(bcp.getLimiteInferior()));
        valorFinMemoria.setText(String.valueOf(bcp.getLimiteSuperior()));
        valorPc.setText(String.valueOf(bcp.getPc()));
        valorIr.setText(bcp.getIr().isEmpty() ? "-" : bcp.getIr());
        valorAc.setText(String.valueOf(bcp.getAc()));
        valorAx.setText(String.valueOf(bcp.obtenerValorRegistro(Registro.AX)));
        valorBx.setText(String.valueOf(bcp.obtenerValorRegistro(Registro.BX)));
        valorCx.setText(String.valueOf(bcp.obtenerValorRegistro(Registro.CX)));
        valorDx.setText(String.valueOf(bcp.obtenerValorRegistro(Registro.DX)));
        valorInstrucciones.setText(String.valueOf(bcp.getInstruccionesEjecutadas()));
        valorEnlace.setText(String.valueOf(bcp.getEnlaceSiguiente()));
    }

    private void limpiarPanelBcp() {
        JLabel[] etiquetas = {valorId, valorPrograma, valorEnlace, valorEstado, valorPrioridad, valorPosicionBcp, valorInicioMemoria,
                valorFinMemoria, valorPc, valorIr, valorAc, valorAx, valorBx, valorCx, valorDx, valorInstrucciones};
        for (JLabel etiqueta : etiquetas) {
            etiqueta.setText("-");
            etiqueta.setForeground(EstiloUI.TEXTO);
        }
    }

    private Color colorParaEstado(EstadoProceso estado) {
        switch (estado) {
            case EJECUTANDO:
                return EstiloUI.ESTADO_EJECUTANDO;
            case TERMINADO:
                return EstiloUI.ESTADO_TERMINADO;
            default:
                return EstiloUI.ESTADO_LISTO;
        }
    }

    /** Resalta, en ambas tablas, la fila correspondiente a la próxima instrucción a ejecutar (según el PC). */
    private void resaltarFilaActual() {
        if (bcp == null || instrucciones == null) {
            return;
        }
        int pcActual = bcp.getPc();

        int indiceInstruccion = -1;
        for (int i = 0; i < instrucciones.size(); i++) {
            if (instrucciones.get(i).getPosicionMemoria() == pcActual) {
                indiceInstruccion = i;
                break;
            }
        }

        if (indiceInstruccion >= 0) {
            tablaInstrucciones.setRowSelectionInterval(indiceInstruccion, indiceInstruccion);
            desplazarHastaFila(tablaInstrucciones, indiceInstruccion);
        } else {
            tablaInstrucciones.clearSelection();
        }

        if (pcActual >= 0 && pcActual < modeloMemoria.getRowCount()) {
            tablaMemoria.setRowSelectionInterval(pcActual, pcActual);
            desplazarHastaFila(tablaMemoria, pcActual);
        } else {
            tablaMemoria.clearSelection();
        }
    }

    private void desplazarHastaFila(JTable tabla, int fila) {
        tabla.scrollRectToVisible(tabla.getCellRect(fila, 0, true));
    }

    /** Muestra en la barra superior la distribucion de la configuracion vigente. */
    private void actualizarEtiquetaDivisionMemoria() {
        etiquetaMemoriaTotal.setText("Memoria: " + configuracion.getMemoriaPrincipal());
        etiquetaSO.setText("SO: " + configuracion.getTamanoAreaSO() + " (" + Memoria.PORCENTAJE_KERNEL + "%)");
        etiquetaUsuario.setText("Usuario: " + configuracion.getTamanoAreaUsuario() + " (" + (100 - Memoria.PORCENTAJE_KERNEL) + "%)");
        etiquetaDisco.setText("Disco: " + configuracion.getMemoriaSecundaria()+ "  (índice: " + configuracion.getTamanoIndiceDisco() + ", memoria virtual fija: " + configuracion.getMemoriaVirtual() + ")");
    }

    private void actualizarEstadoBotones() {
        boolean hayPrograma = bcp != null;
        boolean puedeAvanzar = hayPrograma && bcp.tieneInstruccionesPendientes();

        botonEjecutar.setEnabled(puedeAvanzar);
        botonPasoAPaso.setEnabled(puedeAvanzar);
        botonLimpiar.setEnabled(hayPrograma || (disco != null && !disco.estaVacio()));
        botonCargarArchivo.setEnabled(true);
    }

    /** Deshabilita/habilita los botones mientras corre la animación de "Ejecutar". */
    private void fijarBotonesDurante(boolean habilitado) {
        botonPasoAPaso.setEnabled(habilitado && bcp != null && bcp.tieneInstruccionesPendientes());
        botonEjecutar.setEnabled(habilitado && bcp != null && bcp.tieneInstruccionesPendientes());
        botonLimpiar.setEnabled(habilitado);
        botonCargarArchivo.setEnabled(habilitado);
    }
}