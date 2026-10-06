package minipc;

import java.util.Arrays;

/**
 * Representa la memoria principal de la Mini PC.
 *
 * Cada posicion guarda un valor en forma de texto:
 *   - En el espacio de Usuario: una instruccion completa del programa (ej: "MOV AX, 5").
 *   - En el espacio de Kernel : los campos del BCP, uno por posicion (ej: "130" para el PC).
 * Una posicion vacia guarda la cadena "" (cadena vacia).
 *
 * La memoria se divide en dos espacios:
 *   - Espacio de KERNEL  : primer 25% de las posiciones.
 *   - Espacio de USUARIO : el 75% restante.
 *
 * El tamaño total es configurable, con un minimo de 128 posiciones.
 * @author Natalia Granados Rosales
 */
public class Memoria {

    public static final int TAMANO_MINIMO = 128;

    public static final double PROPORCION_KERNEL = 0.25;

    private final int tamanoTotal;
    private final int tamanoKernel;
    private final int tamanoUsuario;

    private final int inicioKernel;
    private final int finKernel;
    private final int inicioUsuario;
    private final int finUsuario;

    private final String[] datos;

    /**
     * Crea la memoria con el tamaño indicado, dividiendo automaticamente 25% para Kernel y 75% para Usuario.
     * @param tamanoTotal cantidad total de posiciones (minimo 128).
     * @throws IllegalArgumentException si el tamaño es menor al minimo permitido.
     */
    public Memoria(int tamanoTotal) {
        if (tamanoTotal < TAMANO_MINIMO) {
            throw new IllegalArgumentException("El tamaño de memoria (" + tamanoTotal + ") es menor al mínimo requerido (" + TAMANO_MINIMO + ").");
        }

        this.tamanoTotal = tamanoTotal;
        this.tamanoKernel = (int) Math.round(tamanoTotal * PROPORCION_KERNEL);
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
    // BCP en el espacio de Kernel
    // ------------------------------------------------------------------

    /**
     * Escribe todos los campos del BCP en el espacio de Kernel, empezando en la posicion
     * indicada por bcp.getPosicionBCP() (por defecto la posicion 0).
     * Se llama al cargar el proceso y cada vez que la CPU guarda el contexto.
     * @throws SecurityException si el BCP no cabe completo dentro del Kernel.
     */
    public void guardarBCP(BCP bcp) {
        int inicio = bcp.getPosicionBCP();
        int fin = inicio + BCP.TAMANO_EN_MEMORIA - 1;
        if (!esEspacioKernel(inicio) || !esEspacioKernel(fin)) {
            throw new SecurityException("El BCP (posiciones " + inicio + "-" + fin + ") no cabe en el espacio de Kernel (" + inicioKernel + "-" + finKernel + ").");
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