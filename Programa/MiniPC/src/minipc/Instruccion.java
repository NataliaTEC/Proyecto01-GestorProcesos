package minipc;

/**
 * Representa el resultado del analisis de una linea del archivo .asm
 * Esta clase no valida ni procesa nada por si misma, solo guarda el resultado (valido o invalido) que produce {@link ProcesadorInstrucciones}.
 * @author Natalia Granados Rosales
 */
public class Instruccion {

    private final int numeroLinea;
    private final String lineaOriginal;

    private final Operador operador;
    private final Registro registro;
    private final Integer valor; // null si el operador no usa valor inmediato

    private final boolean valida;
    private final String mensajeError;

    // posicion de memoria donde quedara cargada (la asigna el cargador)
    private int posicionMemoria = -1;

    private Instruccion(int numeroLinea, String lineaOriginal, Operador operador, Registro registro, Integer valor, boolean valida, String mensajeError) {
        this.numeroLinea = numeroLinea;
        this.lineaOriginal = lineaOriginal;
        this.operador = operador;
        this.registro = registro;
        this.valor = valor;
        this.valida = valida;
        this.mensajeError = mensajeError;
    }

    /** Crea una instruccion correctamente analizada. */
    public static Instruccion crearValida(int numeroLinea, String lineaOriginal, Operador operador, Registro registro, Integer valor) {
        return new Instruccion(numeroLinea, lineaOriginal, operador, registro, valor, true, null);
    }

    /** Crea una instruccion invalida, guardando el motivo del error. */
    public static Instruccion crearInvalida(int numeroLinea, String lineaOriginal, String mensajeError) {
        return new Instruccion(numeroLinea, lineaOriginal, null, null, null, false, mensajeError);
    }

    public int getNumeroLinea() {
        return numeroLinea;
    }

    public String getLineaOriginal() {
        return lineaOriginal;
    }

    public Operador getOperador() {
        return operador;
    }

    public Registro getRegistro() {
        return registro;
    }

    public Integer getValor() {
        return valor;
    }

    public boolean isValida() {
        return valida;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public int getPosicionMemoria() {
        return posicionMemoria;
    }

    public void setPosicionMemoria(int posicionMemoria) {
        this.posicionMemoria = posicionMemoria;
    }

    /**
     * Devuelve la instruccion escrita en un formato uniforme (mayusculas, una coma y un espacio entre operandos),
     * por ejemplo "mov  ax ,5" se normaliza a "MOV AX, 5". Es el texto que se guarda en memoria.
     */
    public String getTextoNormalizado() {
        if (!valida) {
            return "";
        }
        StringBuilder texto = new StringBuilder(operador.name());
        if (registro != null) {
            texto.append(' ').append(registro.name());
        }
        if (valor != null) {
            texto.append(", ").append(valor);
        }
        return texto.toString();
    }

    @Override
    public String toString() {
        if (!valida) {
            return String.format("Linea %d [INVALIDA] \"%s\" -> %s", numeroLinea, lineaOriginal, mensajeError);
        }
        return String.format("Linea %d \"%s\" (pos:%d)", numeroLinea, getTextoNormalizado(), posicionMemoria);
    }
}