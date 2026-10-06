package minipc;

/**
 * Registros de proposito general que pueden usarse como operandos en el mini ensamblador.
 *   - AX, BX, CX, DX : registros generales.
 *   - AH, AL         : registros usados por INT 21H (AH = funcion de archivo, AL = contenido leido/escrito).
 *
 * En esta Mini PC AH y AL se manejan como registros independientes de AX.
 *
 * Los registros internos de la CPU (AC, PC, IR) NO son operandos del lenguaje: AC se usa de forma
 * implicita (LOAD, STORE, ADD, SUB, INC, DEC) y PC/IR los maneja la CPU.
 * @author Natalia Granados Rosales
 */
public enum Registro {

    AX,
    BX,
    CX,
    DX,
    AH,
    AL;

    /** Registros internos de la CPU, que no se permiten como operandos. */
    private static final String[] REGISTROS_INTERNOS = {"AC", "PC", "IR"};

    /** Traduce un texto a su Registro correspondiente. Acepta mayusculas/minusculas. Devuelve null si no es reconocido. */
    public static Registro fromTexto(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return Registro.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Indica si el texto corresponde a un registro interno de la CPU (AC, PC o IR). */
    public static boolean esRegistroInterno(String texto) {
        if (texto == null) {
            return false;
        }
        String t = texto.trim().toUpperCase();
        for (String interno : REGISTROS_INTERNOS) {
            if (interno.equals(t)) {
                return true;
            }
        }
        return false;
    }

    /** Lista legible de los registros validos para mensajes: "AX, BX, CX, DX, AH o AL". */
    public static String listaParaMensajes() {
        Registro[] todos = values();
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < todos.length; i++) {
            if (i > 0) {
                texto.append(i == todos.length - 1 ? " o " : ", ");
            }
            texto.append(todos[i].name());
        }
        return texto.toString();
    }
}