package minipc;

import java.util.ArrayList;
import java.util.List;

/**
 * Se encarga de validar y procesar las lineas de un programa en lenguaje ensamblador.
 * Reconoce:
 *   - Operadores: LOAD, STORE, MOV, SUB, ADD
 *   - Registros : AX, BX, CX, DX
 *
 * Sintaxis esperada por linea:
 *   MOV   registro, valor   (ej: "MOV AX, 5"  o  "MOV BX, -8")
 *   LOAD  registro           (ej: "LOAD AX")
 *   STORE registro           (ej: "STORE AX")
 *   ADD   registro           (ej: "ADD BX")
 *   SUB   registro           (ej: "SUB AX")
 *
 * Las lineas vacias o que inician con ";" o "#" se consideran comentarios y se ignoran (no generan Instruccion).
 * @author Natalia Granados Rosales
 */
public class ProcesadorInstrucciones {

    /**
     * Procesa un programa completo (una linea de texto por instruccion).
     * Devuelve una Instruccion por cada linea con contenido (ignora comentarios/vacias).
     * El numero de linea reportado corresponde a la linea real dentro del archivo, para que los mensajes de error sean faciles de ubicar.
     */
    public List<Instruccion> procesarPrograma(List<String> lineas) {
        List<Instruccion> resultado = new ArrayList<>();
        int numeroLinea = 0;

        for (String linea : lineas) {
            numeroLinea++;
            String limpia = linea == null ? "" : linea.trim();

            if (limpia.isEmpty() || limpia.startsWith(";") || limpia.startsWith("#")) {
                continue; // linea vacia o comentario: se ignora
            }

            resultado.add(procesarLinea(limpia, numeroLinea));
        }
        return resultado;
    }

    /**
     * Valida y procesa una linea de ensamblador.
     * Primero se valida la ESTRUCTURA (operador + operandos bien separados) y luego
     * el CONTENIDO (que el operador exista y que sus operandos sean validos).
     */
    public Instruccion procesarLinea(String linea, int numeroLinea) {
        String texto = linea == null ? "" : linea.trim();

        if (texto.isEmpty()) {
            return Instruccion.crearInvalida(numeroLinea, linea, "Linea vacia o mal formada.");
        }

        // 1) Separar el operador (primer token) del resto de la linea
        String[] partes = texto.split("\\s+", 2);
        String textoOperador = partes[0];
        String textoOperandos = partes.length > 1 ? partes[1].trim() : "";

        // La linea empieza con coma o el operador viene pegado a una coma (ej: ",MOV AX" o "MOV,AX")
        if (textoOperador.contains(",")) {
            return Instruccion.crearInvalida(numeroLinea, linea, "Coma mal ubicada junto al operador \"" + textoOperador + "\". El operador se separa de los operandos con un espacio. Ej: \"MOV AX, 5\".");
        }

        Operador operador = Operador.fromTexto(textoOperador);
        if (operador == null) {
            return Instruccion.crearInvalida(numeroLinea, linea, "Operador \"" + textoOperador + "\" no reconocido. Use LOAD, STORE, MOV, SUB o ADD.");
        }

        // 2) Validar la estructura de la lista de operandos y separarlos
        List<String> operandos = new ArrayList<>();
        String errorEstructura = separarOperandos(textoOperandos, operandos);
        if (errorEstructura != null) {
            return Instruccion.crearInvalida(numeroLinea, linea, errorEstructura);
        }

        // 3) Validar cantidad y tipo de operandos segun el operador
        if (operador.requiereValorInmediato()) {
            return procesarConValorInmediato(linea, numeroLinea, operador, operandos);
        } else {
            return procesarSoloRegistro(linea, numeroLinea, operador, operandos);
        }
    }

    /**
     * Valida la estructura de la lista de operandos y, si es correcta, los agrega a la lista "destino".
     * Devuelve null si la estructura es valida, o el mensaje de error si no lo es.
     */
    private String separarOperandos(String textoOperandos, List<String> destino) {
        if (textoOperandos.isEmpty()) {
            return null; // instruccion sin operandos (la cantidad se valida despues)
        }

        // Comas consecutivas: ",," o ", ," (con o sin espacios entre ellas)
        if (textoOperandos.matches(".*,\\s*,.*")) {
            return "Comas consecutivas no permitidas. Los operandos se separan con una sola coma. Ej: \"MOV AX, 5\".";
        }

        if (textoOperandos.startsWith(",")) {
            return "Coma antes del primer operando. El operador se separa de los operandos con un espacio. Ej: \"MOV AX, 5\".";
        }

        if (textoOperandos.endsWith(",")) {
            return "Coma al final de la instruccion: falta un operando despues de la coma o la coma sobra.";
        }

        // Separa por una sola coma (con espacios opcionales alrededor)
        String[] piezas = textoOperandos.split("\\s*,\\s*");
        for (String pieza : piezas) {
            // Si un operando contiene espacios, faltaba una coma (ej: "MOV AX 5")
            if (pieza.matches(".*\\s+.*")) {
                return "Falta una coma entre los operandos \"" + pieza.replaceAll("\\s+", "\" y \"") + "\".";
            }
            destino.add(pieza);
        }
        return null;
    }

    /** Procesa instrucciones tipo "MOV registro, valor". */
    private Instruccion procesarConValorInmediato(String linea, int numeroLinea, Operador operador, List<String> operandos) {
        if (operandos.size() != 2) {
            return Instruccion.crearInvalida(numeroLinea, linea, "La instruccion " + operador + " requiere 2 operandos (registro y valor), pero se encontraron " + operandos.size() + ". Ej: \"MOV AX, 5\".");
        }

        Registro registro = Registro.fromTexto(operandos.get(0));
        if (registro == null) {
            return Instruccion.crearInvalida(numeroLinea, linea, "Registro \"" + operandos.get(0) + "\" no reconocido. Use AX, BX, CX o DX.");
        }

        int valor;
        try {
            valor = Integer.parseInt(operandos.get(1));
        } catch (NumberFormatException ex) {
            return Instruccion.crearInvalida(numeroLinea, linea, "Valor \"" + operandos.get(1) + "\" no es un numero entero valido.");
        }

        return Instruccion.crearValida(numeroLinea, linea, operador, registro, valor);
    }

    /** Procesa instrucciones tipo "LOAD <registro>", "STORE <registro>", "ADD <registro>", "SUB <registro>". */
    private Instruccion procesarSoloRegistro(String linea, int numeroLinea, Operador operador, List<String> operandos) {
        if (operandos.size() != 1) {
            return Instruccion.crearInvalida(numeroLinea, linea, "La instruccion " + operador + " requiere 1 operando (un registro), pero se encontraron " + operandos.size() + ". Ej: \"" + operador + " AX\".");
        }

        Registro registro = Registro.fromTexto(operandos.get(0));
        if (registro == null) {
            return Instruccion.crearInvalida(numeroLinea, linea, "Registro \"" + operandos.get(0) + "\" no reconocido. Use AX, BX, CX o DX.");
        }

        return Instruccion.crearValida(numeroLinea, linea, operador, registro, null);
    }

    /** Indica si todas las instrucciones procesadas son validas. */
    public boolean todasValidas(List<Instruccion> instrucciones) {
        for (Instruccion instruccion : instrucciones) {
            if (!instruccion.isValida()) {
                return false;
            }
        }
        return true;
    }
}