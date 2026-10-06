package minipc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado del analisis de una linea del archivo .asm.
 *
 * Guarda el operador y la LISTA de operandos ya normalizados (en mayusculas y con formato uniforme),
 * por lo que sirve para cualquier instruccion: "SWAP AX, BX", "PARAM 1, 2, 3", "JMP -2", "INC".
 *
 * Esta clase no valida nada por si misma: solo guarda lo que produce {@link ProcesadorInstrucciones}.
 * Como el procesador ya verifico que los operandos coinciden con alguna forma del operador, los
 * metodos de interpretacion (getRegistro, getNumero, getDesplazamiento, getInterrupcion) pueden
 * usarse con seguridad segun el operador.
 * @author Natalia Granados Rosales
 */
public class Instruccion {

    private final int numeroLinea;
    private final String lineaOriginal;

    private final Operador operador;
    private final List<String> operandos;

    private final boolean valida;
    private final String mensajeError;

    // posicion de memoria donde quedara cargada (la asigna el cargador)
    private int posicionMemoria = -1;

    private Instruccion(int numeroLinea, String lineaOriginal, Operador operador, List<String> operandos,
                        boolean valida, String mensajeError) {
        this.numeroLinea = numeroLinea;
        this.lineaOriginal = lineaOriginal;
        this.operador = operador;
        this.operandos = Collections.unmodifiableList(new ArrayList<>(operandos));
        this.valida = valida;
        this.mensajeError = mensajeError;
    }

    /** Crea una instruccion correctamente analizada (los operandos ya deben venir normalizados). */
    public static Instruccion crearValida(int numeroLinea, String lineaOriginal, Operador operador, List<String> operandos) {
        return new Instruccion(numeroLinea, lineaOriginal, operador, operandos, true, null);
    }

    /** Crea una instruccion invalida, guardando el motivo del error. */
    public static Instruccion crearInvalida(int numeroLinea, String lineaOriginal, String mensajeError) {
        return new Instruccion(numeroLinea, lineaOriginal, null, Collections.emptyList(), false, mensajeError);
    }

    /** Copia esta instruccion marcandola como invalida (se usa en validaciones de todo el programa, ej: saltos). */
    public Instruccion comoInvalida(String mensajeError) {
        return crearInvalida(numeroLinea, lineaOriginal, mensajeError);
    }

    // ------------------------------------------------------------------
    // Interpretacion de operandos
    // ------------------------------------------------------------------

    public int getCantidadOperandos() {
        return operandos.size();
    }

    /** Operando en la posicion indicada (0 = primero), tal como quedo normalizado. */
    public String getOperando(int indice) {
        return operandos.get(indice);
    }

    /** Indica si el operando en esa posicion es un registro (ej: para distinguir MOV AX, BX de MOV AX, 5). */
    public boolean esRegistro(int indice) {
        return indice < operandos.size() && Registro.fromTexto(operandos.get(indice)) != null;
    }

    /** Interpreta el operando como registro. */
    public Registro getRegistro(int indice) {
        Registro registro = Registro.fromTexto(operandos.get(indice));
        if (registro == null) {
            throw new IllegalStateException("El operando " + (indice + 1) + " de " + operador + " no es un registro: " + operandos.get(indice));
        }
        return registro;
    }

    /** Interpreta el operando como numero (decimal o hexadecimal con sufijo H). */
    public int getNumero(int indice) {
        Integer valor = TipoOperando.convertirNumero(operandos.get(indice));
        if (valor == null) {
            throw new IllegalStateException("El operando " + (indice + 1) + " de " + operador + " no es un número: " + operandos.get(indice));
        }
        return valor;
    }

    /** Desplazamiento de un salto (JMP, JE, JNE): positivo avanza, negativo retrocede. */
    public int getDesplazamiento() {
        if (operador == null || !operador.esSalto()) {
            throw new IllegalStateException("La instrucción " + operador + " no es un salto.");
        }
        return Integer.parseInt(operandos.get(0));
    }

    /** Codigo de interrupcion de una instruccion INT. */
    public Interrupcion getInterrupcion() {
        if (operador != Operador.INT) {
            throw new IllegalStateException("La instrucción " + operador + " no es una interrupción.");
        }
        return Interrupcion.fromTexto(operandos.get(0));
    }

    /** Valores de PARAM (de 1 a 3 numeros), en el orden en que se escribieron. */
    public List<Integer> getParametros() {
        if (operador != Operador.PARAM) {
            throw new IllegalStateException("La instrucción " + operador + " no es PARAM.");
        }
        List<Integer> valores = new ArrayList<>();
        for (int i = 0; i < operandos.size(); i++) {
            valores.add(getNumero(i));
        }
        return valores;
    }

    /**
     * Segundos de CPU que tarda esta instruccion. Para INT depende de la interrupcion; en INT 09H
     * devuelve {@link Interrupcion#PESO_VARIABLE} porque depende de cuando responda el usuario.
     */
    public int getPeso() {
        if (!valida) {
            return 0;
        }
        return operador == Operador.INT ? getInterrupcion().getPeso() : operador.getPeso();
    }

    // ------------------------------------------------------------------
    // Getters generales
    // ------------------------------------------------------------------

    public int getNumeroLinea() {
        return numeroLinea;
    }

    public String getLineaOriginal() {
        return lineaOriginal;
    }

    public Operador getOperador() {
        return operador;
    }

    public List<String> getOperandos() {
        return operandos;
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
     * Instruccion escrita en formato uniforme: operador, un espacio, y los operandos separados por ", ".
     * Ej: "mov  ax ,5" -> "MOV AX, 5";  "jmp 3" no es valido, "jmp +3" -> "JMP +3". Es el texto que se guarda en memoria.
     */
    public String getTextoNormalizado() {
        if (!valida) {
            return "";
        }
        return operandos.isEmpty() ? operador.name() : operador.name() + " " + String.join(", ", operandos);
    }

    @Override
    public String toString() {
        if (!valida) {
            return String.format("Linea %d [INVALIDA] \"%s\" -> %s", numeroLinea, lineaOriginal, mensajeError);
        }
        return String.format("Linea %d \"%s\" (pos:%d)", numeroLinea, getTextoNormalizado(), posicionMemoria);
    }
}