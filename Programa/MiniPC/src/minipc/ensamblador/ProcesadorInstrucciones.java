package minipc.ensamblador;

import minipc.ensamblador.Instruccion;
import minipc.ensamblador.Interrupcion;
import minipc.ensamblador.TipoOperando;
import minipc.ensamblador.Registro;
import minipc.ensamblador.Operador;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida y procesa los programas escritos en el mini ensamblador.
 *
 * El analisis se hace en tres niveles:
 *
 *   1) Estructura de la linea (igual para todas las instrucciones):
 *      - El operador se separa del primer operando con espacio(s), nunca con coma.
 *      - Los operandos se separan con UNA sola coma (se permiten espacios alrededor).
 *      - No se permiten comas consecutivas, ni al inicio o al final de los operandos.
 *      - Un comentario puede ir al final de la linea despues de ";" (ej: "INC AX ; contador").
 *
 *   2) Operandos segun el operador: la cantidad y el tipo de cada operando deben coincidir con
 *      alguna de las formas definidas en {@link Operador} (ej: MOV registro, registro | MOV registro, numero).
 *      Los tipos de operando estan en {@link TipoOperando}.
 *
 *   3) Programa completo (ver {@link #analizarPrograma(List)}):
 *      - El archivo debe tener al menos una instruccion.
 *      - Los saltos (JMP, JE, JNE) no pueden salirse del programa (desbordamiento).
 *      - Entre todas las instrucciones PARAM no puede haber mas de 3 parametros.
 *      - Advertencias (no impiden cargar): no hay INT 20H, PARAM despues de otras instrucciones.
 *
 * Las lineas vacias o que inician con ";" o "#" se consideran comentarios y se ignoran (no generan Instruccion).
 * @author Natalia Granados Rosales
 */
public class ProcesadorInstrucciones {

    /** Maximo de parametros de entrada que se pueden declarar con PARAM en un programa. */
    public static final int MAXIMO_PARAMETROS = 3;

    // ==================================================================
    // Nivel 3: programa completo
    // ==================================================================

    /**
     * Analiza un programa completo: valida cada linea y luego las reglas que dependen de todo el
     * programa (saltos, PARAM, INT 20H).
     * @param lineas lineas del archivo .asm, en orden.
     */
    public ResultadoAnalisis analizarPrograma(List<String> lineas) {
        List<Instruccion> instrucciones = procesarLineas(lineas);
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        if (instrucciones.isEmpty()) {
            errores.add("El archivo no contiene instrucciones (solo líneas vacías o comentarios).");
            return new ResultadoAnalisis(instrucciones, errores, advertencias);
        }

        validarSaltos(instrucciones);
        validarParametros(instrucciones, errores, advertencias);
        validarFinDePrograma(instrucciones, advertencias);

        return new ResultadoAnalisis(instrucciones, errores, advertencias);
    }

    /**
     * Procesa un programa completo y devuelve sus instrucciones (incluye la validacion de saltos).
     * Para obtener tambien los errores generales y las advertencias use {@link #analizarPrograma(List)}.
     */
    public List<Instruccion> procesarPrograma(List<String> lineas) {
        return analizarPrograma(lineas).getInstrucciones();
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

    /** Valida cada linea con contenido; el numero de linea es el real del archivo. */
    private List<Instruccion> procesarLineas(List<String> lineas) {
        List<Instruccion> resultado = new ArrayList<>();
        int numeroLinea = 0;
        for (String linea : lineas) {
            numeroLinea++;
            String limpia = quitarComentario(linea);
            if (limpia.isEmpty() || limpia.startsWith("#")) {
                continue; // linea vacia o comentario: se ignora
            }
            resultado.add(procesarLinea(linea, numeroLinea));
        }
        return resultado;
    }

    /**
     * Un salto no puede llevar a una instruccion fuera del programa (desbordamiento).
     * Si ocurre, la instruccion del salto se marca como invalida.
     */
    private void validarSaltos(List<Instruccion> instrucciones) {
        int total = instrucciones.size();
        for (int i = 0; i < total; i++) {
            Instruccion instruccion = instrucciones.get(i);
            if (!instruccion.isValida() || !instruccion.getOperador().esSalto()) {
                continue;
            }
            int desplazamiento = instruccion.getDesplazamiento();
            long destino = (long) i + desplazamiento; // long: evita desbordar el int con valores enormes
            if (destino < 0 || destino >= total) {
                String hacia = destino < 0
                        ? "antes de la primera instrucción"
                        : "a la instrucción " + (destino + 1) + ", pero el programa solo tiene " + total;
                instrucciones.set(i, instruccion.comoInvalida("Desbordamiento: " + instruccion.getTextoNormalizado()
                        + " está en la instrucción " + (i + 1) + " y saltaría " + hacia + "."));
            }
        }
    }

    /** Maximo 3 parametros en total; PARAM normalmente va al inicio del programa. */
    private void validarParametros(List<Instruccion> instrucciones, List<String> errores, List<String> advertencias) {
        int totalParametros = 0;
        boolean hayInstruccionesAntes = false;
        for (Instruccion instruccion : instrucciones) {
            if (!instruccion.isValida()) {
                hayInstruccionesAntes = true;
                continue;
            }
            if (instruccion.getOperador() == Operador.PARAM) {
                totalParametros += instruccion.getCantidadOperandos();
                if (hayInstruccionesAntes) {
                    advertencias.add("Línea " + instruccion.getNumeroLinea()
                            + ": PARAM aparece después de otras instrucciones; los parámetros de entrada "
                            + "normalmente se declaran al inicio del programa.");
                }
            } else {
                hayInstruccionesAntes = true;
            }
        }
        if (totalParametros > MAXIMO_PARAMETROS) {
            errores.add("El programa declara " + totalParametros + " parámetros con PARAM; el máximo es "
                    + MAXIMO_PARAMETROS + ".");
        }
    }

    /** Sin INT 20H el programa termina al pasar su ultima instruccion; se avisa, pero se permite. */
    private void validarFinDePrograma(List<Instruccion> instrucciones, List<String> advertencias) {
        for (Instruccion instruccion : instrucciones) {
            if (instruccion.isValida() && instruccion.getOperador() == Operador.INT
                    && instruccion.getInterrupcion() == Interrupcion.FIN_PROGRAMA) {
                return;
            }
        }
        advertencias.add("El programa no tiene INT 20H: terminará al ejecutar su última instrucción.");
    }

    // ==================================================================
    // Niveles 1 y 2: una linea
    // ==================================================================

    /**
     * Valida y procesa una linea de ensamblador.
     * Primero se valida la ESTRUCTURA (operador + operandos bien separados) y luego
     * los OPERANDOS segun las formas validas del operador.
     */
    public Instruccion procesarLinea(String linea, int numeroLinea) {
        String texto = quitarComentario(linea);

        if (texto.isEmpty()) {
            return Instruccion.crearInvalida(numeroLinea, linea, "Línea vacía o mal formada.");
        }

        // 1) Separar el operador (primer token) del resto de la linea
        String[] partes = texto.split("\\s+", 2);
        String textoOperador = partes[0];
        String textoOperandos = partes.length > 1 ? partes[1].trim() : "";

        // La linea empieza con un registro: falta el operador (ej: "AX, 5")
        String operadorSinComas = textoOperador.replace(",", "");
        if (Registro.fromTexto(operadorSinComas) != null || Registro.esRegistroInterno(operadorSinComas)) {
            return Instruccion.crearInvalida(numeroLinea, linea, mensajeOperadorDesconocido(operadorSinComas));
        }

        // La linea empieza con coma o el operador viene pegado a una coma (ej: ",MOV AX" o "MOV,AX")
        if (textoOperador.contains(",")) {
            return Instruccion.crearInvalida(numeroLinea, linea, "Coma mal ubicada junto al operador \"" + textoOperador + "\". El operador se separa de los operandos con un espacio. Ej: \"MOV AX, 5\".");
        }

        Operador operador = Operador.fromTexto(textoOperador);
        if (operador == null) {
            return Instruccion.crearInvalida(numeroLinea, linea, mensajeOperadorDesconocido(textoOperador));
        }

        // 2) Validar la estructura de la lista de operandos y separarlos
        List<String> operandos = new ArrayList<>();
        String errorEstructura = separarOperandos(textoOperandos, operandos);
        if (errorEstructura != null) {
            return Instruccion.crearInvalida(numeroLinea, linea, errorEstructura);
        }

        // 3) Validar cantidad y tipo de operandos segun las formas del operador
        return validarOperandos(linea, numeroLinea, operador, operandos);
    }

    /** Quita espacios y el comentario al final de la linea (todo lo que sigue a ";"). */
    private String quitarComentario(String linea) {
        if (linea == null) {
            return "";
        }
        int inicioComentario = linea.indexOf(';');
        String sinComentario = inicioComentario >= 0 ? linea.substring(0, inicioComentario) : linea;
        return sinComentario.trim();
    }

    private String mensajeOperadorDesconocido(String textoOperador) {
        String t = textoOperador.toUpperCase();
        if (Registro.fromTexto(t) != null || Registro.esRegistroInterno(t)) {
            return "Falta el operador: la línea empieza con el registro \"" + textoOperador
                    + "\". Ej: \"MOV " + t + ", 5\".";
        }
        return "Operador \"" + textoOperador + "\" no reconocido. Operadores válidos: "
                + Operador.listaParaMensajes() + ".";
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
            return "Coma al final de la instrucción: falta un operando después de la coma o la coma sobra.";
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

    /**
     * Valida los operandos contra las formas del operador y, si coinciden con alguna,
     * crea la Instruccion con los operandos normalizados.
     *
     * Para que el mensaje de error sea lo mas util posible:
     *   - Si la cantidad no coincide con ninguna forma, se indica cuantos acepta y su sintaxis.
     *   - Si la cantidad coincide, se indica el PRIMER operando que no encaja y que tipos se esperaban
     *     en esa posicion (ej: en MOV el segundo operando puede ser registro o numero).
     */
    private Instruccion validarOperandos(String linea, int numeroLinea, Operador operador, List<String> operandos) {
        int cantidad = operandos.size();

        // a) Cantidad de operandos
        if (!operador.aceptaCantidad(cantidad)) {
            return Instruccion.crearInvalida(numeroLinea, linea, "La instrucción " + operador + " acepta "
                    + operador.cantidadesAceptadas() + (operador.cantidadesAceptadas().equals("1") ? " operando" : " operandos")
                    + ", pero " + (cantidad == 1 ? "se encontró 1" : "se encontraron " + cantidad)
                    + ". Sintaxis: " + operador.sintaxis() + ".");
        }

        // b) Formas con esa cantidad: se busca la primera que acepte todos los operandos
        List<List<TipoOperando>> candidatas = new ArrayList<>();
        for (List<TipoOperando> forma : operador.getFormas()) {
            if (forma.size() == cantidad) {
                candidatas.add(forma);
                if (coincide(forma, operandos)) {
                    return Instruccion.crearValida(numeroLinea, linea, operador, normalizar(forma, operandos));
                }
            }
        }

        // c) Ninguna forma coincide: se reporta el primer operando que no encaja en ninguna
        for (int i = 0; i < cantidad; i++) {
            List<TipoOperando> esperados = new ArrayList<>();
            boolean encaja = false;
            for (List<TipoOperando> forma : candidatas) {
                TipoOperando tipo = forma.get(i);
                if (!esperados.contains(tipo)) {
                    esperados.add(tipo);
                }
                encaja |= tipo.acepta(operandos.get(i));
            }
            if (!encaja) {
                return Instruccion.crearInvalida(numeroLinea, linea, mensajeOperandoInvalido(operador, i, cantidad, operandos.get(i), esperados));
            }
        }

        // Cada operando encaja por separado pero no en la misma forma (no ocurre con las formas actuales)
        return Instruccion.crearInvalida(numeroLinea, linea, "Combinación de operandos no válida para "
                + operador + ". Sintaxis: " + operador.sintaxis() + ".");
    }

    private boolean coincide(List<TipoOperando> forma, List<String> operandos) {
        for (int i = 0; i < forma.size(); i++) {
            if (!forma.get(i).acepta(operandos.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<String> normalizar(List<TipoOperando> forma, List<String> operandos) {
        List<String> normalizados = new ArrayList<>();
        for (int i = 0; i < forma.size(); i++) {
            normalizados.add(forma.get(i).normalizar(operandos.get(i)));
        }
        return normalizados;
    }

    /** Ej: "El operando 2 de MOV ("EX") debe ser un registro (AX, ...) o un número." + pista. */
    private String mensajeOperandoInvalido(Operador operador, int indice, int cantidad, String operando, List<TipoOperando> esperados) {
        StringBuilder mensaje = new StringBuilder();
        mensaje.append(cantidad == 1 ? "El operando de " + operador : "El operando " + (indice + 1) + " de " + operador);
        mensaje.append(" (\"").append(operando).append("\") debe ser ");
        for (int i = 0; i < esperados.size(); i++) {
            if (i > 0) {
                mensaje.append(" o ");
            }
            mensaje.append("un ").append(esperados.get(i).getDescripcion());
        }
        mensaje.append(".");
        for (TipoOperando tipo : esperados) {
            String pista = tipo.pistaDeError(operando);
            if (!pista.isEmpty()) {
                mensaje.append(pista);
                break;
            }
        }
        return mensaje.toString();
    }
}