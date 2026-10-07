package minipc.ensamblador;

import minipc.ensamblador.Registro;

/**
 * Tipos de operando que puede recibir una instruccion del mini ensamblador.
 *
 * Cada tipo sabe reconocer si un texto le corresponde ({@link #acepta(String)}) y como escribirlo
 * de forma uniforme ({@link #normalizar(String)}), para que en memoria siempre quede igual.
 *
 *   REGISTRO       : AX, BX, CX, DX, AH, AL                      (ej: "ax" -> "AX")
 *   NUMERO         : entero decimal con signo opcional, o hexadecimal con sufijo H
 *                    que empieza con un digito                    (ej: "5", "-8", "3CH", "0FFH")
 *   DESPLAZAMIENTO : entero con signo OBLIGATORIO y distinto de 0 (ej: "+3", "-2")
 *   INTERRUPCION   : 20H, 10H, 09H o 21H                          (ej: "20h" -> "20H")
 * @author Natalia Granados Rosales
 */
public enum TipoOperando {

    REGISTRO("registro (" + Registro.listaParaMensajes() + ")"),
    NUMERO("número"),
    DESPLAZAMIENTO("desplazamiento con signo (ej: +3 o -2)"),
    INTERRUPCION("código de interrupción (" + Interrupcion.listaParaMensajes() + ")");

    private static final String PATRON_DECIMAL = "[+-]?\\d+";
    private static final String PATRON_HEXADECIMAL = "\\d[0-9A-F]*H";
    private static final String PATRON_DESPLAZAMIENTO = "[+-]\\d+";

    private final String descripcion;

    TipoOperando(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Descripcion legible del tipo, usada en los mensajes de error. */
    public String getDescripcion() {
        return descripcion;
    }

    /** Indica si el texto es un operando valido de este tipo. */
    public boolean acepta(String texto) {
        if (texto == null) {
            return false;
        }
        String t = texto.trim().toUpperCase();
        switch (this) {
            case REGISTRO:
                return Registro.fromTexto(t) != null;
            case NUMERO:
                return convertirNumero(t) != null;
            case DESPLAZAMIENTO:
                if (!t.matches(PATRON_DESPLAZAMIENTO)) {
                    return false;
                }
                Integer valor = convertirEntero(t);
                return valor != null && valor != 0;
            case INTERRUPCION:
                return Interrupcion.fromTexto(t) != null;
            default:
                return false;
        }
    }

    /**
     * Devuelve el operando escrito de forma uniforme (debe haberse verificado antes con acepta()).
     * Los numeros hexadecimales se conservan en hexadecimal ("3ch" -> "3CH") para que la instruccion
     * en memoria se lea igual que en el archivo.
     */
    public String normalizar(String texto) {
        String t = texto.trim().toUpperCase();
        switch (this) {
            case REGISTRO:
                return Registro.fromTexto(t).name();
            case NUMERO:
                return t.matches(PATRON_HEXADECIMAL) ? t : String.valueOf(convertirEntero(t));
            case DESPLAZAMIENTO:
                int desplazamiento = convertirEntero(t);
                return (desplazamiento > 0 ? "+" : "") + desplazamiento;
            case INTERRUPCION:
                return Interrupcion.fromTexto(t).getCodigo();
            default:
                return t;
        }
    }

    // ------------------------------------------------------------------
    // Conversion de numeros
    // ------------------------------------------------------------------

    /**
     * Convierte un operando numerico (decimal o hexadecimal con sufijo H) a entero.
     * @return el valor, o null si el texto no es un numero valido o no cabe en un entero.
     */
    public static Integer convertirNumero(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim().toUpperCase();
        if (t.matches(PATRON_DECIMAL)) {
            return convertirEntero(t);
        }
        if (t.matches(PATRON_HEXADECIMAL)) {
            try {
                return Integer.parseInt(t.substring(0, t.length() - 1), 16);
            } catch (NumberFormatException ex) {
                return null; // demasiado grande
            }
        }
        return null;
    }

    /** Convierte un entero decimal (con signo opcional). Devuelve null si no cabe en un int. */
    private static Integer convertirEntero(String texto) {
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Da una pista concreta cuando un texto NO es aceptado por este tipo, para que el mensaje
     * de error diga que corregir. Devuelve "" si no hay una pista especifica.
     */
    public String pistaDeError(String texto) {
        String t = texto.trim().toUpperCase();
        switch (this) {
            case REGISTRO:
                if (Registro.esRegistroInterno(t)) {
                    return " " + t + " es un registro interno de la CPU y no puede usarse como operando.";
                }
                return "";
            case NUMERO:
                if (t.matches("[A-F][0-9A-F]*H") && Registro.fromTexto(t) == null) {
                    return " Los números hexadecimales deben empezar con un dígito (ej: 0" + t + ").";
                }
                if (Registro.fromTexto(t) != null) {
                    return " Aquí se esperaba un valor numérico, no un registro.";
                }
                if (t.matches(PATRON_DECIMAL) || t.matches(PATRON_HEXADECIMAL)) {
                    return " El número está fuera del rango permitido.";
                }
                return "";
            case DESPLAZAMIENTO:
                if (t.matches("\\d+")) {
                    return " El desplazamiento debe llevar signo: use +" + t + " para avanzar o -" + t + " para retroceder.";
                }
                if (t.matches("[+-]0+")) {
                    return " El desplazamiento no puede ser 0 (el salto se repetiría infinitamente).";
                }
                if (t.matches(PATRON_DESPLAZAMIENTO)) {
                    return " El desplazamiento está fuera del rango permitido.";
                }
                return "";
            case INTERRUPCION:
                if (Interrupcion.fromTexto(t + "H") != null) {
                    return " Agregue el sufijo H: " + t + "H.";
                }
                return "";
            default:
                return "";
        }
    }
}