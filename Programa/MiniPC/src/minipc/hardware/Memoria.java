package minipc.hardware;

import minipc.so.BCP;
import minipc.ensamblador.Instruccion;

import java.util.Arrays;

/**
 * Representa la memoria principal de la Mini PC.
 *
 * Cada posicion guarda un valor en forma de texto:
 *   - En el espacio de Usuario: una instruccion completa del programa (ej: "MOV AX, 5").
 *   - En el espacio de Kernel : el encabezado del SO y los campos de los BCP (ej: "78" para el PC).
 * Una posicion vacia guarda la cadena "" (cadena vacia).
 *
 * La memoria se divide en dos espacios:
 *   - Espacio de KERNEL : el primer 25% de las posiciones.
 *   - Espacio de USUARIO: el 75% restante.
 *
 * Distribucion del Kernel (ejemplo con 256 posiciones, Kernel 0-63):
 *   0        Direccion del primer BCP de la cola de listos (-1 si esta vacia)
 *   1        Direccion del ultimo BCP de la cola de listos (-1 si esta vacia)
 *   2        Cantidad de procesos activos
 *   3        Direccion del BCP en ejecucion (-1 si la CPU esta libre)
 *   4 - 63   Ranuras de los 5 BCP (12 posiciones cada una); las administra GestorMemoria.
 *
 * El tamano total viene de {@link minipc.config.Configuracion} (no queda en el codigo).
 * @author Natalia Granados Rosales
 */
public class Memoria {

    /** Porcentaje de la memoria reservado para el Kernel (SO). El resto es para Usuario. */
    public static final int PORCENTAJE_KERNEL = 25;

    // ------------------------------------------------------------------
    // Encabezado del SO (primeras posiciones del Kernel)
    // ------------------------------------------------------------------
    public static final int ENCABEZADO_PRIMER_LISTO = 0;
    public static final int ENCABEZADO_ULTIMO_LISTO = 1;
    public static final int ENCABEZADO_CANTIDAD_PROCESOS = 2;
    public static final int ENCABEZADO_BCP_EN_EJECUCION = 3;

    /** Cantidad de campos del encabezado (debe coincidir con Configuracion.TAMANO_ENCABEZADO_SO). */
    public static final int CAMPOS_ENCABEZADO = 4;

    /** Valor que se guarda en el encabezado cuando una direccion no apunta a ningun BCP. */
    public static final int SIN_DIRECCION = -1;

    /** Nombre de cada campo del encabezado, en el orden en que se guardan. */
    public static final String[] NOMBRES_ENCABEZADO = {
        "Primer listo", "Último listo", "Procesos activos", "BCP en ejecución"
    };

    private final int tamanoTotal;
    private final int tamanoKernel;
    private final int tamanoUsuario;

    private final int inicioKernel;
    private final int finKernel;
    private final int inicioUsuario;
    private final int finUsuario;

    private final String[] datos;

    /**
     * Calcula cuantas posiciones le corresponden al Kernel (25% del total, redondeado hacia abajo).
     */
    public static int calcularTamanoKernel(int tamanoTotal) {
        return tamanoTotal * PORCENTAJE_KERNEL / 100;
    }

    /**
     * Crea la memoria con el tamano indicado, dividiendo automaticamente 25% para Kernel y 75% para Usuario.
     * El tamano ya debe venir validado por la configuracion; aqui solo se verifica que sea coherente.
     * @param tamanoTotal cantidad total de posiciones.
     * @throws IllegalArgumentException si el tamano no permite tener ambos espacios.
     */
    public Memoria(int tamanoTotal) {
        int tamanoKernel = calcularTamanoKernel(tamanoTotal);
        if (tamanoKernel <= CAMPOS_ENCABEZADO || tamanoKernel >= tamanoTotal) {
            throw new IllegalArgumentException("El tamaño de memoria (" + tamanoTotal + ") es demasiado pequeño para dividirse entre Kernel y Usuario.");
        }

        this.tamanoTotal = tamanoTotal;
        this.tamanoKernel = tamanoKernel;
        this.tamanoUsuario = tamanoTotal - tamanoKernel;

        this.inicioKernel = 0;
        this.finKernel = tamanoKernel - 1;
        this.inicioUsuario = tamanoKernel;
        this.finUsuario = tamanoTotal - 1;

        this.datos = new String[tamanoTotal];
        limpiar();
    }

    // ------------------------------------------------------------------
    // Lectura / escritura generica
    // ------------------------------------------------------------------

    /** Escribe un valor (texto) en la posicion indicada. Un valor null se guarda como posicion vacia. */
    public void escribir(int posicion, String valor) {
        validarPosicion(posicion);
        datos[posicion] = valor == null ? "" : valor;
    }

    /** Lee el valor almacenado en la posicion indicada ("" si esta vacia). */
    public String leer(int posicion) {
        validarPosicion(posicion);
        return datos[posicion];
    }

    /** Indica si la posicion no tiene ningun valor guardado. */
    public boolean estaVacia(int posicion) {
        return leer(posicion).isEmpty();
    }

    // ------------------------------------------------------------------
    // Lectura / escritura restringida por espacio
    // ------------------------------------------------------------------

    /** Escribe un valor unicamente si la posicion pertenece al espacio de Usuario. */
    public void escribirEnUsuario(int posicion, String valor) {
        if (!esEspacioUsuario(posicion)) {
            throw new SecurityException("La posición " + posicion + " no pertenece al espacio de Usuario (" + inicioUsuario + "-" + finUsuario + ").");
        }
        escribir(posicion, valor);
    }

    /** Escribe un valor unicamente si la posicion pertenece al espacio de Kernel. */
    public void escribirEnKernel(int posicion, String valor) {
        if (!esEspacioKernel(posicion)) {
            throw new SecurityException("La posición " + posicion + " no pertenece al espacio de Kernel (" + inicioKernel + "-" + finKernel + ").");
        }
        escribir(posicion, valor);
    }

    /**
     * Deja vacias las posiciones desde "inicio" hasta "fin" (inclusive).
     * Se usa al liberar el espacio de un programa o la ranura de un BCP.
     */
    public void limpiarRango(int inicio, int fin) {
        validarPosicion(inicio);
        validarPosicion(fin);
        for (int posicion = inicio; posicion <= fin; posicion++) {
            datos[posicion] = "";
        }
    }

    // ------------------------------------------------------------------
    // Conveniencia: escribir una Instruccion ya validada
    // ------------------------------------------------------------------

    /**
     * Escribe una instruccion ya validada (como texto normalizado, ej: "MOV AX, 5") en la posicion indicada
     * dentro del espacio de Usuario, y actualiza la posicion de memoria guardada en la propia Instruccion.
     * @return la siguiente posicion libre (posicion + 1).
     */
    public int escribirInstruccion(int posicion, Instruccion instruccion) {
        if (!instruccion.isValida()) {
            throw new IllegalArgumentException("No se puede cargar en memoria una instrucción inválida: " + instruccion.getMensajeError());
        }

        escribirEnUsuario(posicion, instruccion.getTextoNormalizado());

        instruccion.setPosicionMemoria(posicion);
        return posicion + 1;
    }

    // ------------------------------------------------------------------
    // Encabezado del SO en el espacio de Kernel
    // ------------------------------------------------------------------

    /**
     * Escribe los 4 campos del encabezado del SO (posiciones 0 a 3 del Kernel).
     * @param primerListo       direccion del primer BCP de la cola de listos, o -1.
     * @param ultimoListo       direccion del ultimo BCP de la cola de listos, o -1.
     * @param cantidadProcesos  cantidad de procesos activos (con BCP en el Kernel).
     * @param bcpEnEjecucion    direccion del BCP que tiene la CPU, o -1.
     */
    public void escribirEncabezado(int primerListo, int ultimoListo, int cantidadProcesos, int bcpEnEjecucion) {
        escribirCampoEncabezado(ENCABEZADO_PRIMER_LISTO, primerListo);
        escribirCampoEncabezado(ENCABEZADO_ULTIMO_LISTO, ultimoListo);
        escribirCampoEncabezado(ENCABEZADO_CANTIDAD_PROCESOS, cantidadProcesos);
        escribirCampoEncabezado(ENCABEZADO_BCP_EN_EJECUCION, bcpEnEjecucion);
    }

    /** Deja el encabezado en su estado inicial: cola vacia, 0 procesos y CPU libre. */
    public void inicializarEncabezado() {
        escribirEncabezado(SIN_DIRECCION, SIN_DIRECCION, 0, SIN_DIRECCION);
    }

    /**
     * Escribe un solo campo del encabezado. Cada parte del SO actualiza solo lo que le corresponde:
     * la cola de listos (campos 0 y 1), el gestor de memoria (campo 2) y el despachador (campo 3).
     * @param campo  ENCABEZADO_PRIMER_LISTO, ENCABEZADO_ULTIMO_LISTO, ENCABEZADO_CANTIDAD_PROCESOS o ENCABEZADO_BCP_EN_EJECUCION.
     */
    public void escribirCampoEncabezado(int campo, int valor) {
        validarCampoEncabezado(campo);
        escribirEnKernel(inicioKernel + campo, String.valueOf(valor));
    }

    /** Lee un campo del encabezado como numero (-1 si la posicion esta vacia o no es numerica). */
    public int leerCampoEncabezado(int campo) {
        validarCampoEncabezado(campo);
        return leerEntero(inicioKernel + campo);
    }

    /** Indica si la posicion pertenece al encabezado del SO. */
    public boolean esEncabezado(int posicion) {
        return posicion >= inicioKernel && posicion < inicioKernel + CAMPOS_ENCABEZADO;
    }

    /** Texto legible de un campo del encabezado, para la interfaz (ej: "SO.Primer listo = 4"). */
    public static String describirEncabezado(int campo, String valorEnMemoria) {
        return "SO." + NOMBRES_ENCABEZADO[campo] + " = " + valorEnMemoria;
    }

    /** Lee una posicion como numero entero; devuelve -1 si esta vacia o no es un numero. */
    public int leerEntero(int posicion) {
        String valor = leer(posicion).trim();
        try {
            return valor.isEmpty() ? SIN_DIRECCION : Integer.parseInt(valor);
        } catch (NumberFormatException ex) {
            return SIN_DIRECCION;
        }
    }

    // ------------------------------------------------------------------
    // BCP en el espacio de Kernel
    // ------------------------------------------------------------------

    /**
     * Escribe todos los campos del BCP en el espacio de Kernel, empezando en la posicion
     * indicada por bcp.getPosicionBCP() (la direccion de su ranura).
     * Se llama al cargar el proceso, cada vez que cambia su enlace y cada vez que la CPU guarda el contexto.
     * @throws SecurityException si el BCP no cabe completo dentro del Kernel o pisaria el encabezado.
     */
    public void guardarBCP(BCP bcp) {
        int inicio = bcp.getPosicionBCP();
        int fin = inicio + BCP.TAMANO_EN_MEMORIA - 1;
        if (esEncabezado(inicio) || !esEspacioKernel(inicio) || !esEspacioKernel(fin)) {
            throw new SecurityException("El BCP (posiciones " + inicio + "-" + fin + ") no cabe en las ranuras del Kernel ("
                    + (inicioKernel + CAMPOS_ENCABEZADO) + "-" + finKernel + ").");
        }

        String[] valores = bcp.aValoresDeMemoria();
        for (int i = 0; i < valores.length; i++) {
            escribirEnKernel(inicio + i, valores[i]);
        }
    }

    // ------------------------------------------------------------------
    // Consultas de espacio
    // ------------------------------------------------------------------

    public boolean esPosicionValida(int posicion) {
        return posicion >= 0 && posicion < tamanoTotal;
    }

    public boolean esEspacioKernel(int posicion) {
        return posicion >= inicioKernel && posicion <= finKernel;
    }

    public boolean esEspacioUsuario(int posicion) {
        return posicion >= inicioUsuario && posicion <= finUsuario;
    }

    /** Deja todas las posiciones de la memoria vacias. */
    public final void limpiar() {
        Arrays.fill(datos, "");
    }

    // ------------------------------------------------------------------
    // Validaciones internas
    // ------------------------------------------------------------------

    private void validarPosicion(int posicion) {
        if (!esPosicionValida(posicion)) {
            throw new IndexOutOfBoundsException("Posición " + posicion + " fuera de rango (0-" + (tamanoTotal - 1) + ").");
        }
    }

    private void validarCampoEncabezado(int campo) {
        if (campo < 0 || campo >= CAMPOS_ENCABEZADO) {
            throw new IllegalArgumentException("Campo de encabezado inválido: " + campo + " (debe ser 0-" + (CAMPOS_ENCABEZADO - 1) + ").");
        }
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public int getTamanoTotal() {
        return tamanoTotal;
    }

    public int getTamanoKernel() {
        return tamanoKernel;
    }

    public int getTamanoUsuario() {
        return tamanoUsuario;
    }

    public int getInicioKernel() {
        return inicioKernel;
    }

    public int getFinKernel() {
        return finKernel;
    }

    public int getInicioUsuario() {
        return inicioUsuario;
    }

    public int getFinUsuario() {
        return finUsuario;
    }
}