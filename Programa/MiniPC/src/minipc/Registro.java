package minipc;

/**
 * Representa los registros de proposito general de la Mini PC: AX, BX, CX y DX.
 * @author Natalia Granados Rosales
 */
public enum Registro {

    AX,
    BX,
    CX,
    DX;

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
}