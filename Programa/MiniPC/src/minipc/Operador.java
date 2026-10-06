package minipc;

/**
 * Representa los operadores (mnemonicos) validos del lenguaje ensamblador de la Mini PC.
 *
 * Operadores soportados:
 *   LOAD, STORE, MOV, SUB, ADD
 * @author Natalia Granados Rosales
 */
public enum Operador {

    LOAD(false),
    STORE(false),
    MOV(true),
    SUB(false),
    ADD(false);

    private final boolean requiereValorInmediato;

    Operador(boolean requiereValorInmediato) {
        this.requiereValorInmediato = requiereValorInmediato;
    }

    /** Indica si el operador necesita un valor numerico inmediato ademas del registro. */
    public boolean requiereValorInmediato() {
        return requiereValorInmediato;
    }

    /** Traduce un texto (mnemonico) a su Operador correspondiente. Acepta mayusculas/minusculas. Devuelve null si no es reconocido. */
    public static Operador fromTexto(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return Operador.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}